package com.ssafy.docq_backend.domain.minigame.service;

import com.ssafy.docq_backend.domain.game.service.GameService;
import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.minigame.dto.BuffType;
import com.ssafy.docq_backend.domain.minigame.dto.MiniGameDto;
import com.ssafy.docq_backend.domain.room.entity.Room;
import com.ssafy.docq_backend.domain.room.entity.RoomStatus;
import com.ssafy.docq_backend.domain.room.repository.RoomRepository;
import com.ssafy.docq_backend.domain.player.entity.Player;
import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ssafy.docq_backend.domain.game.entity.PlayerState;
import com.ssafy.docq_backend.domain.minigame.dto.MiniGameType;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MiniGameService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Lazy
    private final GameService gameService;

    public void startMiniGame(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        log.info("Starting MiniGame for Room {}", roomId);

        // 1. 상태 설정
        room.setStatus(RoomStatus.MINIGAME);

        // 2. 이전 미니게임 데이터 초기화
        if (room.getMiniGameScores() == null) {
            room.setMiniGameScores(new HashMap<>());
        }
        room.getMiniGameScores().clear();
        roomRepository.save(room);

        // 3. 미니게임 종류 랜덤 선택 (중복 방지)
        MiniGameType[] allGames = MiniGameType.values();
        List<MiniGameType> candidates = Arrays.stream(allGames)
                .filter(type -> !room.getPlayedMiniGames().contains(type.getCode()))
                .collect(Collectors.toList());

        // 모든 게임을 다 했다면 리셋 후 전체 후보로 다시 설정
        if (candidates.isEmpty()) {
            room.getPlayedMiniGames().clear();
            candidates = Arrays.asList(allGames);
        }

        MiniGameType selectedGame = candidates.get(new Random().nextInt(candidates.size()));

        // 선택된 게임 기록
        room.getPlayedMiniGames().add(selectedGame.getCode());
        roomRepository.save(room); // 상태 저장

        // 4. 시작 메시지 브로드캐스트
        MiniGameDto.StartResponse response = MiniGameDto.StartResponse.builder()
                .type("START")
                .gameType(selectedGame.getCode())
                .timeLimit(selectedGame.getTimeLimit())
                .description(selectedGame.getDescription())
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/minigame", response);
    }

    public void submitResult(MiniGameDto.SubmissionRequest request) {
        String roomId = request.getRoomId();
        String playerId = request.getPlayerId();
        int score = request.getResultValue();

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        if (room.getStatus() != RoomStatus.MINIGAME) {
            log.warn("Ignored minigame result from {} in room {} (Status: {})", playerId, roomId, room.getStatus());
            return;
        }

        // 점수 저장
        if (room.getMiniGameScores() == null) {
            room.setMiniGameScores(new HashMap<>());
        }
        room.getMiniGameScores().put(playerId, score);
        roomRepository.save(room);

        log.info("Stored MiniGame Score: Player {} = {}", playerId, score);

        // 모든 플레이어 제출 여부 확인
        if (room.getMiniGameScores().size() >= room.getPlayers().size()) {
            endMiniGame(roomId);
        }
    }

    public void endMiniGame(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        if (room.getStatus() != RoomStatus.MINIGAME) {
            return;
        }

        log.info("Ending MiniGame for Room {}", roomId);

        // 1. 순위 계산
        List<MiniGameDto.RankInfo> rankings = calculateRankings(room);

        String winnerPlayerId = null;
        String rewardBuff = "NONE";
        String resultType = "RESULT";

        // 2. 승자 결정 및 보상 처리
        // 무승부 체크 (1, 2등 동점 시)
        if (rankings.size() >= 2 && rankings.get(0).getValue() == rankings.get(1).getValue()) {
            resultType = "DRAW";
            log.info("MiniGame Draw in Room {} - No rewards distributed", roomId);
        } else if (!rankings.isEmpty()) {
            // 승자 존재
            MiniGameDto.RankInfo winner = rankings.get(0);
            winnerPlayerId = winner.getPlayerId();

            // 랜덤 버프 선택 (Enum 활용)
            BuffType[] augments = BuffType.values();
            BuffType selectedBuff = augments[new Random().nextInt(augments.length)];
            rewardBuff = selectedBuff.getCode();

            // 인터랙션이 필요한 아이템인지 확인
            if (selectedBuff == BuffType.BANANA ||
                    selectedBuff == BuffType.SWAP_POSITION ||
                    selectedBuff == BuffType.QUIZ_INTERFERENCE) {
                requiresInteraction = true;
            } else if (selectedBuff == BuffType.PLUS_THREE) {
                // ACK 기반 처리: 버프 룰렛 완료 후 이동 적용
                requiresInteraction = true;
                room.setPendingPlusThreePlayerId(winnerPlayerId);
            }
            // BOOM은 아무 효과 없음
        }

        roomRepository.save(room);

        // 3. 결과 브로드캐스트
        MiniGameDto.ResultResponse response = MiniGameDto.ResultResponse.builder()
                .type(resultType)
                .gameType("MINIGAME_END") // 종료 시에는 구체적인 타입보다는 종료되었음을 알림
                .winnerPlayerId(winnerPlayerId)
                .rewardBuff(rewardBuff)
                .rankings(rankings)
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/minigame", response);

        // 4. 메인 게임 재개 (인터랙션이 필요없는 경우에만 자동 재개)
        if (!requiresInteraction) {
            gameService.scheduleResumeAfterMiniGame(roomId);
        } else {
            log.info("Waiting for item interaction from player {}", winnerPlayerId);
            // 클라이언트에게 인터랙션 요청 메시지 전송 (옵션)
            // 이미 RESULT 메시지에 rewardBuff가 포함되어 있으므로 클라이언트가 이를 보고 판단 가능
        }
    }

    public void useItem(MiniGameDto.ItemUsageRequest request) {
        String roomId = request.getRoomId();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        // 유효성 검증 (미니게임 상태인지, 요청자가 승자인지 등은 생략하거나 추가 가능)

        BuffType itemType;
        try {
            itemType = BuffType.valueOf(request.getItemType());
        } catch (IllegalArgumentException e) {
            log.error("Invalid item type: {}", request.getItemType());
            return;
        }

        log.info("Player {} using item {}", request.getPlayerId(), itemType);

        switch (itemType) {
            case BANANA:
                if (request.getTargetPosition() != null) {
                    room.getMapEvents().put(request.getTargetPosition(), "BANANA");
                }
                break;
            case SWAP_POSITION:
                if (request.getTargetPlayerId() != null) {
                    String p1 = request.getPlayerId();
                    String p2 = request.getTargetPlayerId();
                    if (room.getPlayerStates().containsKey(p1) && room.getPlayerStates().containsKey(p2)) {
                        int pos1 = room.getPlayerStates().get(p1).getPosition();
                        int pos2 = room.getPlayerStates().get(p2).getPosition();
                        room.getPlayerStates().get(p1).setPosition(pos2);
                        room.getPlayerStates().get(p2).setPosition(pos1);
                        // 위치 변경 알림 필요 (GameState Broadcast)
                    }
                }
                break;
            case QUIZ_INTERFERENCE:
                if (request.getTargetPlayerId() != null) {
                    if (room.getPlayerStates().containsKey(request.getTargetPlayerId())) {
                        // 디버프 저장 (PlayerState에 디버프 필드가 없으므로 hasBuff를 임시 활용하거나 별도 관리 필요)
                        // 현재 hasBuff는 String이므로 여기에 "DEBUFF_MIRROR" 저장 가능 (기존 버프 덮어씀 주의)
                        room.getPlayerStates().get(request.getTargetPlayerId()).setHasBuff("DEBUFF_MIRROR");
                    }
                }
                break;
            default:
                break; // 다른 아이템은 즉시 적용되었거나 별도 처리 없음
        }

        roomRepository.save(room);

        // 아이템 사용 완료 브로드캐스트
        Player player = playerRepository.findById(request.getPlayerId()).orElse(null);
        String nickname = player != null ? player.getNickname() : "알 수 없음";

        Map<String, Object> itemUsedNotice = new HashMap<>();
        itemUsedNotice.put("type", "ITEM_USED");
        itemUsedNotice.put("playerId", request.getPlayerId());
        itemUsedNotice.put("playerName", nickname);
        itemUsedNotice.put("itemType", request.getItemType());
        itemUsedNotice.put("targetPlayerId", request.getTargetPlayerId());
        itemUsedNotice.put("targetPosition", request.getTargetPosition());

        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/notice", itemUsedNotice);

        gameService.scheduleResumeAfterMiniGame(roomId);
    }

    private List<MiniGameDto.RankInfo> calculateRankings(Room room) {
        return room.getMiniGameScores().entrySet().stream()
                .map(entry -> {
                    String uid = entry.getKey();
                    String nickname = "Unknown";
                    Player player = playerRepository.findById(uid).orElse(null);
                    if (player != null)
                        nickname = player.getNickname();

                    return MiniGameDto.RankInfo.builder()
                            .playerId(uid)
                            .nickname(nickname)
                            .value(entry.getValue())
                            .build();
                })
                .sorted((a, b) -> b.getValue() - a.getValue()) // 점수 내림차순 정렬
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    // 순위 설정
                    for (int i = 0; i < list.size(); i++) {
                        // 순위 정보 재생성
                        MiniGameDto.RankInfo original = list.get(i);
                        list.set(i, MiniGameDto.RankInfo.builder()
                                .playerId(original.getPlayerId())
                                .nickname(original.getNickname())
                                .value(original.getValue())
                                .rank(i + 1)
                                .build());
                    }
                    return list;
                }));
    }

    public void applyPlusThreeMove(Room room, String playerId) {
        if (room == null || playerId == null) {
            return;
        }
        if (room.getPlayerStates() == null || !room.getPlayerStates().containsKey(playerId)) {
            return;
        }

        PlayerState ps = room.getPlayerStates().get(playerId);
        int before = ps.getPosition();
        int after = before + 3;
        int maxPosition = 30;
        if (after >= maxPosition) {
            after = maxPosition;
        }

        ps.setPosition(after);

        GameDto.MoveAction action = GameDto.MoveAction.builder()
                .type("BUFF")
                .buffType("PLUS_THREE")
                .before(before)
                .after(after)
                .build();

        GameDto.MoveInfo moveInfo = GameDto.MoveInfo.builder()
                .playerId(playerId)
                .startPosition(before)
                .endPosition(after)
                .moveProcess(Collections.singletonList(action))
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/move", moveInfo);
        roomRepository.save(room);
        gameService.broadcastGameState(room);
    }
}

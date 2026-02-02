package com.ssafy.docq_backend.domain.minigame.service;

import com.ssafy.docq_backend.domain.game.service.GameService;
import com.ssafy.docq_backend.domain.minigame.dto.BuffType;
import com.ssafy.docq_backend.domain.minigame.dto.MiniGameDto;
import com.ssafy.docq_backend.domain.room.entity.Room;
import com.ssafy.docq_backend.domain.room.entity.RoomStatus;
import com.ssafy.docq_backend.domain.room.repository.RoomRepository;
import com.ssafy.docq_backend.domain.guest.repository.GuestRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final GuestRepository guestRepository;
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

        // 2. 보상 적용
        if (!rankings.isEmpty()) {
            MiniGameDto.RankInfo winner = rankings.get(0);
            winnerPlayerId = winner.getPlayerId();

            // 랜덤 버프 선택 (Enum 활용)
            BuffType[] augments = BuffType.values();
            BuffType selectedBuff = augments[new Random().nextInt(augments.length)];
            rewardBuff = selectedBuff.getCode();

            // Apply to PlayerState
            if (room.getPlayerStates().containsKey(winnerPlayerId)) {
                room.getPlayerStates().get(winnerPlayerId).setHasBuff(rewardBuff);
            }
        }

        roomRepository.save(room);

        // 3. 결과 브로드캐스트
        MiniGameDto.ResultResponse response = MiniGameDto.ResultResponse.builder()
                .type("RESULT")
                .gameType("MINIGAME_END") // 종료 시에는 구체적인 타입보다는 종료되었음을 알림 (Room에 저장을 안하므로)
                .winnerPlayerId(winnerPlayerId)
                .rewardBuff(rewardBuff)
                .rankings(rankings)
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/minigame", response);

        // 4. 메인 게임 재개 (ACK 기반 진행 + 폴백 예약)
        gameService.scheduleResumeAfterMiniGame(roomId);
    }

    private List<MiniGameDto.RankInfo> calculateRankings(Room room) {
        return room.getMiniGameScores().entrySet().stream()
                .map(entry -> {
                    String uid = entry.getKey();
                    String nickname = "Unknown";
                    var guest = guestRepository.findById(uid).orElse(null);
                    if (guest != null)
                        nickname = guest.getNickname();

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
}

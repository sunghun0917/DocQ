package com.ssafy.docq_backend.domain.game.service;

import com.ssafy.docq_backend.domain.game.entity.PlayerState;
import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.room.entity.Room;
import com.ssafy.docq_backend.domain.room.entity.RoomStatus;
import com.ssafy.docq_backend.domain.room.repository.RoomRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.ssafy.docq_backend.domain.game.dto.QuizDto;
import com.ssafy.docq_backend.domain.game.entity.Quiz;
import com.ssafy.docq_backend.global.client.AiClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiClient aiClient;

    public void generateQuizFromPdf(String roomId, MultipartFile pdfFile) {
        log.info("Generating Quiz for Room {}: {}", roomId, pdfFile.getOriginalFilename());

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        // AI Client 호출
        List<QuizDto.Response> quizResponses = aiClient.generateQuiz(pdfFile);
        log.info("Received {} quizzes from AI: {}", quizResponses.size(), quizResponses);

        // Quiz 엔티티 로 변환 및 저장
        List<Quiz> quizzes = quizResponses.stream()
                .map(dto -> Quiz.builder()
                        .quizId(UUID.randomUUID().toString())
                        .question(dto.getQuestion())
                        .options(dto.getOptions())
                        .answerIndex(dto.getAnswerIndex())
                        .answerText(dto.getAnswerText())
                        .build())
                .collect(Collectors.toList());

        room.setQuizList(quizzes);

        // 완료 알림 브로드캐스트
        Map<String, Object> message = new HashMap<>();
        message.put("type", "QUIZ_GENERATED");
        message.put("count", quizzes.size());

        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", message);
    }

    public List<QuizDto.Response> getQuizzes(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        if (room.getQuizList() == null) {
            return Collections.emptyList();
        }

        return room.getQuizList().stream()
                .map(q -> QuizDto.Response.builder()
                        .quizId(q.getQuizId())
                        .question(q.getQuestion())
                        .options(q.getOptions())
                        .answerIndex(q.getAnswerIndex())
                        .answerText(q.getAnswerText())
                        .build())
                .collect(Collectors.toList());
    }

    public void joinGame(GameDto.JoinRequest request) {
        log.info("Join Request: roomId={}, userId={}, nickname={}", request.getRoomId(), request.getUserId(),
                request.getNickname());

        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

            if (!room.getPlayers().contains(request.getUserId())) {
                if (room.getCurrentCount() >= room.getCapacity()) {
                    sendError(request.getUserId(), ErrorCode.ROOM_FULL);
                    return;
                }
                if (room.getStatus() == RoomStatus.PLAYING) {
                    sendError(request.getUserId(), ErrorCode.GAME_ALREADY_STARTED);
                    return;
                }
                room.addPlayer(request.getUserId());
            }
            broadcastGameState(room);
        } catch (DocqException e) {
            sendError(request.getUserId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in joinGame", e);
            sendError(request.getUserId(), ErrorCode.SERVER_ERROR);
        }
    }

    public void readyGame(GameDto.ReadyRequest request) {
        log.info("Ready Request: roomId={}, userId={}, isReady={}", request.getRoomId(), request.getUserId(),
                request.isReady());

        try {
            log.info("Processing Ready Request: {}", request);
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            if (!room.getPlayers().contains(request.getUserId())) {
                log.warn("User {} not found in room {} players: {}", request.getUserId(), request.getRoomId(),
                        room.getPlayers());
                sendError(request.getUserId(), ErrorCode.USER_NOT_FOUND);
                return;
            }
            log.info("Setting ready state for user {} to {}", request.getUserId(), request.isReady());
            room.setReady(request.getUserId(), request.isReady());

            log.info("Broadcasting game state after ready update");
            broadcastGameState(room);
        } catch (DocqException e) {
            log.error("DocqException in readyGame: {}", e.getMessage());
            sendError(request.getUserId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in readyGame", e);
            sendError(request.getUserId(), ErrorCode.SERVER_ERROR);
        }
    }

    public void startGame(String roomId, String userId) {
        log.info("Start Request: roomId={}, userId={}", roomId, userId);
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            // 권한 체크: 방장만 시작 가능
            if (!userId.equals(room.getHostId())) {
                sendError(userId, ErrorCode.ACCESS_DENIED);
                return;
            }

            // 인원 체크
            if (room.getPlayers().size() < 2) {
                sendError(userId, ErrorCode.NOT_ENOUGH_PLAYERS);
                return;
            }

            // 모든 유저 Ready 체크
            for (String pid : room.getPlayers()) {
                if (!room.isReady(pid)) {
                    log.warn("Cannot start game. Player {} is not ready.", pid);
                    sendError(userId, ErrorCode.NOT_ALL_READY);
                    return;
                }
            }

            // 카드 뽑기 (순서 정하기)
            determineTurnOrder(room);

            // 게임 상태 초기화
            initGameData(room);

            // 상태 변경
            room.setStatus(RoomStatus.STARTING);

            // 브로드캐스트
            broadcastGameState(room);

        } catch (DocqException e) {
            sendError(userId, e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in startGame", e);
            sendError(userId, ErrorCode.SERVER_ERROR);
        }
    }

    private void initGameData(Room room) {
        room.setRound(1);
        // 첫 번째 턴 유저
        if (!room.getTurnOrder().isEmpty()) {
            room.setCurrentTurnUserId(room.getTurnOrder().get(0));
        }

        // 플레이어별 상태 초기화
        for (String pid : room.getPlayers()) {
            PlayerState ps = PlayerState.builder()
                    .userId(pid)
                    .score(0)
                    .position(0)
                    .hasBuff(null)
                    .build();
            room.getPlayerStates().put(pid, ps);
        }
    }

    private void determineTurnOrder(Room room) {
        List<String> players = new ArrayList<>(room.getPlayers());
        Map<String, Integer> cardResults = new HashMap<>();
        Random random = new Random();

        // 1~100 랜덤 값 부여 (중복 방지 로직은 간단히 생략하거나 재시도)
        Set<Integer> usedValues = new HashSet<>();
        for (String pid : players) {
            int val;
            do {
                val = random.nextInt(100) + 1;
            } while (usedValues.contains(val));
            usedValues.add(val);
            cardResults.put(pid, val);
        }

        // 값 기준 내림차순 정렬
        players.sort((p1, p2) -> cardResults.get(p2) - cardResults.get(p1));

        room.setTurnOrder(players);
        room.setCardDrawResult(cardResults);

        log.info("Turn Order determined: {}", players);
    }

    private void broadcastGameState(Room room) {
        List<GameDto.PlayerInfo> playerInfos = room.getPlayers().stream()
                .map(userId -> {
                    User u = userRepository.findById(userId).orElse(null);
                    if (u == null)
                        return null;

                    // 게임 상태 조회 (없으면 null)
                    PlayerState ps = room.getPlayerStates().get(userId);
                    Integer score = ps != null ? ps.getScore() : null;
                    Integer position = ps != null ? ps.getPosition() : null;
                    String buff = ps != null ? ps.getHasBuff() : null;

                    return GameDto.PlayerInfo.builder()
                            .userId(u.getId())
                            .nickname(u.getNickname())
                            .isHost(u.getId().equals(room.getHostId()))
                            .connected(true)
                            .isReady(room.isReady(u.getId()))
                            .score(score)
                            .position(position)
                            .hasBuff(buff)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Integer round = room.getStatus() == RoomStatus.WAITING ? null : room.getRound();
        String currentTurnUserId = room.getStatus() == RoomStatus.WAITING ? null : room.getCurrentTurnUserId();
        List<String> turnOrder = room.getStatus() == RoomStatus.WAITING ? null : room.getTurnOrder();
        Map<String, Integer> cardDrawResult = room.getStatus() == RoomStatus.WAITING ? null : room.getCardDrawResult();

        GameDto.GameStateResponse response = GameDto.GameStateResponse.builder()
                .state(room.getStatus())
                .round(round)
                .currentTurnUserId(currentTurnUserId)
                .players(playerInfos)
                .turnOrder(turnOrder)
                .cardDrawResult(cardDrawResult)
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/state", response);
    }

    private void sendError(String userId, ErrorCode errorCode) {
        GameDto.ErrorResponse errorResponse = GameDto.ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        messagingTemplate.convertAndSend("/queue/errors/" + userId, errorResponse);
        log.info("Sent error to user {}: {}", userId, errorCode.getMessage());
    }
}

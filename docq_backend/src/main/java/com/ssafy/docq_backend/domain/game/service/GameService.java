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
import com.ssafy.docq_backend.domain.minigame.service.MiniGameService;
import com.ssafy.docq_backend.global.client.AiClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.ssafy.docq_backend.domain.game.event.AckStore;
import com.ssafy.docq_backend.domain.game.event.ApplyMovePayload;
import com.ssafy.docq_backend.domain.game.event.GameEventStore;
import com.ssafy.docq_backend.domain.game.event.GameEventType;
import com.ssafy.docq_backend.domain.game.event.MoveActionPayload;
import com.ssafy.docq_backend.domain.game.event.MovePlanPayload;
import com.ssafy.docq_backend.domain.game.event.NextTurnPayload;
import com.ssafy.docq_backend.domain.game.event.RoundResultPayload;
import com.ssafy.docq_backend.domain.game.event.ResumeAfterMiniGamePayload;
import com.ssafy.docq_backend.global.websocket.SessionRegistry;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiClient aiClient;
    private final ObjectProvider<MiniGameService> miniGameServiceProvider;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final GameEventStore gameEventStore;
    private final AckStore ackStore;
    private final SessionRegistry sessionRegistry;
    private static final int START_POSITION = 1;
    private static final int TARGET_POSITION = 30;
    private static final int DEFAULT_QUIZ_COUNT = 30;

    private static final long TURN_PREP_DELAY_MS = 1500;
    private static final long INTRO_CAMERA_DURATION_MS = 10000;
    private static final long ACK_FALLBACK_TIMEOUT_MS = 3000;
    private static final long MINIGAME_RESUME_FALLBACK_MS = 8000;
    private static final long MOVE_FALLBACK_STEP_MS = 350;
    private static final long MOVE_FALLBACK_BUFFER_MS = 500;
    private static final long MOVE_FALLBACK_MAX_MS = 12000;

    public void generateQuizFromPdf(String roomId, MultipartFile pdfFile) {
        log.info("Generating Quiz for Room {}: {}", roomId, pdfFile.getOriginalFilename());

        // [ADD] 퀴즈 생성 시작 알림 (프론트엔드 대기 화면 전환용)
        Map<String, Object> startMsg = new HashMap<>();
        startMsg.put("type", "QUIZ_GENERATING");
        startMsg.put("fileName", pdfFile.getOriginalFilename());
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", startMsg);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        // AI Client 호출
        List<QuizDto.Response> quizResponses = aiClient.generateQuiz(pdfFile);
        log.info("Received {} quizzes from AI: {}", quizResponses.size(), quizResponses);

        // Quiz 엔티티 로 변환 및 저장
        // Quiz 엔티티 로 변환 및 저장
        List<Quiz> quizzes = new ArrayList<>();
        for (int i = 0; i < quizResponses.size(); i++) {
            QuizDto.Response dto = quizResponses.get(i);
            quizzes.add(Quiz.builder()
                    .quizId((long) (i + 1)) // 1-based sequential ID
                    .question(dto.getQuestion())
                    .options(dto.getOptions())
                    .answerIndex(dto.getAnswerIndex())
                    .answerText(dto.getAnswerText())
                    .build());
        }

        room.setQuizList(quizzes);

        // 완료 알림 브로드캐스트
        Map<String, Object> message = new HashMap<>();
        message.put("type", "QUIZ_GENERATED");
        message.put("count", quizzes.size());

        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", message);
    }

    public int setDefaultQuizzes(String roomId, String type) {
        // [ADD] 퀴즈 생성 시작 알림
        Map<String, Object> startMsg = new HashMap<>();
        startMsg.put("type", "QUIZ_GENERATING");
        startMsg.put("quizType", type);
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", startMsg);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        List<QuizDto.Response> loadedQuizzes = new ArrayList<>();

        if ("common".equals(type) || "mixed".equals(type)) {
            loadedQuizzes.addAll(loadQuizzesFromJson("common.json"));
        }
        if ("nonsense".equals(type) || "mixed".equals(type)) {
            loadedQuizzes.addAll(loadQuizzesFromJson("nonsense.json"));
        }

        if (loadedQuizzes.isEmpty()) {
            throw new RuntimeException("No quizzes loaded for type: " + type);
        }

        // Shuffle and pick DEFAULT_QUIZ_COUNT
        Collections.shuffle(loadedQuizzes);
        List<QuizDto.Response> selectedDtos = loadedQuizzes.stream()
                .limit(DEFAULT_QUIZ_COUNT)
                .collect(Collectors.toList());

        // Convert to Entity
        List<Quiz> quizzes = new ArrayList<>();
        for (int i = 0; i < selectedDtos.size(); i++) {
            QuizDto.Response dto = selectedDtos.get(i);
            quizzes.add(Quiz.builder()
                    .quizId((long) (i + 1))
                    .question(dto.getQuestion())
                    .options(dto.getOptions())
                    .answerIndex(dto.getAnswerIndex())
                    .answerText(dto.getAnswerText())
                    .build());
        }

        room.setQuizList(quizzes);
        roomRepository.save(room); // Ensure room is saved if using persistent repo, otherwise in-memory map
                                   // handles it

        // Broadcast update
        Map<String, Object> message = new HashMap<>();
        message.put("type", "QUIZ_GENERATED");
        message.put("count", quizzes.size());
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", message);

        return quizzes.size();
    }

    public void resetQuiz(String roomId) {
        // [MOD] 퀴즈 리셋 알림 -> "대기 중" 상태로 전환
        Map<String, Object> resetMsg = new HashMap<>();
        resetMsg.put("type", "QUIZ_RESET");
        messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", resetMsg);
    }

    private List<QuizDto.Response> loadQuizzesFromJson(String filename) {
        try (java.io.InputStream inputStream = getClass().getResourceAsStream("/quiz/" + filename)) {
            if (inputStream == null) {
                log.error("Quiz file not found: {}", filename);
                return Collections.emptyList();
            }
            return objectMapper.readValue(inputStream,
                    new com.fasterxml.jackson.core.type.TypeReference<List<QuizDto.Response>>() {
                    });
        } catch (java.io.IOException e) {
            log.error("Failed to load quizzes from {}", filename, e);
            throw new RuntimeException("Failed to load quiz file", e);
        }
    }

    public List<QuizDto.Response> getQuizzes(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

        if (room.getQuizList() == null || room.getQuizList().isEmpty()) {
            room.setQuizList(buildDefaultQuizzes(DEFAULT_QUIZ_COUNT));
            roomRepository.save(room);
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
                if (room.getStatus() != RoomStatus.WAITING) {
                    sendError(request.getUserId(), ErrorCode.GAME_ALREADY_STARTED);
                    return;
                }
                room.addPlayer(request.getUserId());
                roomRepository.save(room); // Persistence Fix
            }
            broadcastGameState(room);
        } catch (DocqException e) {
            sendError(request.getUserId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in joinGame", e);
            sendError(request.getUserId(), ErrorCode.SERVER_ERROR);
        }
    }

    public void leaveGame(GameDto.LeaveRequest request) {
        log.info("Leave Request: roomId={}, userId={}", request.getRoomId(), request.getUserId());

        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElse(null);
            if (room == null) {
                return;
            }

            String userId = request.getUserId();
            boolean wasHost = userId.equals(room.getHostId());
            boolean removed = false;

            if (room.getPlayers().contains(userId)) {
                int removedIndex = room.getTurnOrder().indexOf(userId);
                room.removePlayer(userId);
                removed = true;
                room.getPlayerAvatars().remove(userId);
                room.getPlayerStates().remove(userId);
                room.getCardDrawResult().remove(userId);
                room.getQuizAnswers().remove(userId);
                room.getTurnOrder().remove(userId);

                if (userId.equals(room.getCurrentTurnUserId())) {
                    if (!room.getTurnOrder().isEmpty()) {
                        int nextIndex = removedIndex >= room.getTurnOrder().size() ? 0 : Math.max(removedIndex, 0);
                        room.setCurrentTurnUserId(room.getTurnOrder().get(nextIndex));
                    } else {
                        room.setCurrentTurnUserId(null);
                    }
                }
            }

            if (room.getPlayers().size() == 1
                    && room.getStatus() != RoomStatus.WAITING
                    && room.getStatus() != RoomStatus.FINISHED) {
                gameEventStore.clearRoomEvents(room.getId());
                ackStore.clearRoomData(room.getId());
                room.setStatus(RoomStatus.FINISHED);
                roomRepository.save(room);
                broadcastGameState(room);
                messagingTemplate.convertAndSend(
                        "/topic/game/" + room.getId() + "/finish",
                        buildFinishResponse(room));
                return;
            }

            if (wasHost || room.getPlayers().isEmpty()) {
                closeRoom(room.getId(), wasHost ? "HOST_LEFT" : "EMPTY");
                return;
            }

            if (removed) {
                handlePlayerExitAcks(room, userId);
            }

            roomRepository.save(room);
            broadcastGameState(room);
        } catch (Exception e) {
            log.error("Error in leaveGame", e);
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
            roomRepository.save(room); // Persistence Fix

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

            // 중복 시작 방지: 이미 진행 중이면 무시
            if (room.getStatus() != RoomStatus.WAITING) {
                log.warn("Start ignored. Room already started: {}", room.getStatus());
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

            if (room.getQuizList() == null || room.getQuizList().isEmpty()) {
                room.setQuizList(buildDefaultQuizzes(DEFAULT_QUIZ_COUNT));
            }

            // 상태 변경
            room.setStatus(RoomStatus.STARTING);
            roomRepository.save(room); // Persistence Fix

            // 브로드캐스트
            broadcastGameState(room);

            // 카메라 인트로 이후 퀴즈 시작
            // gameEventStore.schedule(room.getId(), GameEventType.START_QUIZ, null,
            // INTRO_CAMERA_DURATION_MS);

        } catch (DocqException e) {
            sendError(userId, e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in startGame", e);
            sendError(userId, ErrorCode.SERVER_ERROR);
        }
    }

    public void endGame(GameDto.EndGameRequest request) {
        log.info("End Game Request: roomId={}, userId={}", request.getRoomId(), request.getUserId());
        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElse(null);
            if (room == null)
                return;

            if (!request.getUserId().equals(room.getHostId())) {
                sendError(request.getUserId(), ErrorCode.ACCESS_DENIED);
                return;
            }

            closeRoom(room.getId(), "HOST_END");
        } catch (Exception e) {
            log.error("Error in endGame", e);
        }
    }

    public void restartGame(GameDto.RestartRequest request) {
        log.info("Restart Request: roomId={}, userId={}", request.getRoomId(), request.getUserId());
        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElse(null);
            if (room == null)
                return;

            if (!request.getUserId().equals(room.getHostId())) {
                sendError(request.getUserId(), ErrorCode.ACCESS_DENIED);
                return;
            }

            gameEventStore.clearRoomEvents(room.getId());
            ackStore.clearRoomData(room.getId());

            resetGameState(room);
            room.setStatus(RoomStatus.WAITING);
            roomRepository.save(room);
            broadcastGameState(room);
        } catch (Exception e) {
            log.error("Error in restartGame", e);
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
                    .position(START_POSITION)
                    .hasBuff(null)
                    .build();
            room.getPlayerStates().put(pid, ps);
        }

        // 맵 특수 효과 배치 (Random 5 tiles with "TILE_X2")
        room.getMapEvents().clear();
        Random random = new Random();
        Set<Integer> eventTiles = new HashSet<>();
        int eventMinPosition = START_POSITION + 1;
        int eventMaxPosition = TARGET_POSITION - 1;

        while (eventTiles.size() < 5) {
            int pos = random.nextInt(eventMaxPosition - eventMinPosition + 1) + eventMinPosition;
            if (eventTiles.add(pos)) {
                room.getMapEvents().put(pos, "TILE_X2");
            }
        }
        log.info("Map Events Initialized: {}", room.getMapEvents());
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
                    Integer avatarId = room.getPlayerAvatars().get(userId);

                    return GameDto.PlayerInfo.builder()
                            .userId(u.getId())
                            .nickname(u.getNickname())
                            .isHost(u.getId().equals(room.getHostId()))
                            .connected(true)
                            .isReady(room.isReady(u.getId()))
                            .score(score)
                            .position(position)
                            .hasBuff(buff)
                            .avatarId(avatarId)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Integer round = room.getStatus() == RoomStatus.WAITING ? null : room.getRound();
        String currentTurnUserId = room.getStatus() == RoomStatus.WAITING ? null : room.getCurrentTurnUserId();
        List<String> turnOrder = room.getStatus() == RoomStatus.WAITING ? null : room.getTurnOrder();
        Map<String, Integer> cardDrawResult = room.getStatus() == RoomStatus.WAITING ? null : room.getCardDrawResult();
        Map<Integer, String> mapEvents = room.getStatus() == RoomStatus.WAITING ? null : room.getMapEvents();

        GameDto.GameStateResponse response = GameDto.GameStateResponse.builder()
                .state(room.getStatus())
                .round(round)
                .currentTurnUserId(currentTurnUserId)
                .players(playerInfos)
                .turnOrder(turnOrder)
                .cardDrawResult(cardDrawResult)
                .mapEvents(mapEvents)
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/state", response);
    }

    private void closeRoom(String roomId, String reason) {
        try {
            GameDto.RoomClosedResponse response = GameDto.RoomClosedResponse.builder()
                    .roomId(roomId)
                    .reason(reason)
                    .build();
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/closed", response);
        } catch (Exception e) {
            log.error("Error sending room closed message", e);
        } finally {
            roomRepository.deleteById(roomId);
        }
    }

    private void resetGameState(Room room) {
        room.setRound(1);
        room.setCurrentTurnUserId(null);
        room.setCurrentQuizId(null);
        room.setQuizStartTimeMs(null);
        room.getQuizAnswers().clear();
        room.getTurnOrder().clear();
        room.getCardDrawResult().clear();
        room.getMapEvents().clear();
        room.getReadyPlayers().clear();
        room.getPlayerStates().clear();

        for (String pid : room.getPlayers()) {
            PlayerState ps = PlayerState.builder()
                    .userId(pid)
                    .score(0)
                    .position(START_POSITION)
                    .hasBuff(null)
                    .build();
            room.getPlayerStates().put(pid, ps);
        }
    }

    private void sendError(String userId, ErrorCode errorCode) {
        GameDto.ErrorResponse errorResponse = GameDto.ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        messagingTemplate.convertAndSend("/queue/errors/" + userId, errorResponse);
        log.info("Sent error to user {}: {}", userId, errorCode.getMessage());
    }

    public void handleAck(GameDto.AckRequest request) {
        try {
            if (request == null || request.getAckType() == null) {
                return;
            }
            Room room = roomRepository.findById(request.getRoomId()).orElse(null);
            if (room == null || request.getUserId() == null) {
                return;
            }
            if (!room.getPlayers().contains(request.getUserId())) {
                return;
            }

            AckResolution resolution = resolveAck(room, request.getAckType(), resolveAckKeyId(room, request),
                    request.getRound());
            if (resolution == null || resolution.keyId == null) {
                return;
            }

            long count = ackStore.addAckAndCount(room.getId(), resolution.ackType, resolution.keyId, request.getUserId());
            int expected = resolveExpectedAckCount(room);
            if (count < expected) {
                return;
            }
            advanceAfterAck(room, resolution, request.getRound());
        } catch (Exception e) {
            log.error("Error handling ack", e);
        }
    }

    private void handlePlayerExitAcks(Room room, String userId) {
        int expected = resolveExpectedAckCount(room);
        for (AckStore.AckKeyInfo info : ackStore.removeUserFromRoomAcks(room.getId(), userId)) {
            if (info.getCount() < expected) {
                continue;
            }
            AckResolution resolution = resolveAck(room, info.getAckType(), info.getKeyId(), room.getRound());
            if (resolution == null || resolution.keyId == null) {
                continue;
            }
            advanceAfterAck(room, resolution, room.getRound());
        }
    }

    private AckResolution resolveAck(Room room, GameDto.AckType ackType, String keyId, Integer roundOverride) {
        if (ackType == GameDto.AckType.MINIGAME_COMPLETE || ackType == GameDto.AckType.BUFF_ROULETTE_COMPLETE) {
            if (room.getStatus() != RoomStatus.MINIGAME) {
                return null;
            }
            String resolvedKey = keyId != null
                    ? keyId
                    : String.valueOf(roundOverride != null ? roundOverride : room.getRound());
            return new AckResolution(GameDto.AckType.MINIGAME_COMPLETE, ackType, resolvedKey);
        }
        String resolvedKey = keyId != null ? keyId : String.valueOf(room.getRound());
        return new AckResolution(ackType, ackType, resolvedKey);
    }

    private int resolveExpectedAckCount(Room room) {
        int connected = sessionRegistry.getConnectedCount(room.getId());
        if (connected > 0) {
            return connected;
        }
        return room.getPlayers().size();
    }

    private void advanceAfterAck(Room room, AckResolution resolution, Integer roundOverride) {
        if (!ackStore.tryMarkAckComplete(room.getId(), resolution.ackType, resolution.keyId)) {
            return;
        }

        switch (resolution.ackType) {
            case CAMERA_END -> {
                gameEventStore.schedule(room.getId(), GameEventType.START_QUIZ, null, 0);
            }
            case RESULT_DISPLAYED -> {
                ApplyMovePayload payload = ackStore.loadPayload(room.getId(), GameDto.AckType.RESULT_DISPLAYED,
                        resolution.keyId,
                        ApplyMovePayload.class);
                if (payload != null) {
                    gameEventStore.schedule(room.getId(), GameEventType.APPLY_MOVE, payload, 0);
                }
            }
            case MOVE_COMPLETE -> {
                NextTurnPayload payload = ackStore.loadPayload(room.getId(), GameDto.AckType.MOVE_COMPLETE, resolution.keyId,
                        NextTurnPayload.class);
                if (payload != null) {
                    gameEventStore.schedule(room.getId(), GameEventType.NEXT_TURN, payload, 0);
                }
            }
            case MINIGAME_COMPLETE -> {
                ResumeAfterMiniGamePayload payload = ResumeAfterMiniGamePayload.builder()
                        .round(roundOverride != null ? roundOverride : room.getRound())
                        .build();
                gameEventStore.schedule(room.getId(), GameEventType.RESUME_AFTER_MINIGAME, payload, 0);
            }
            case BUFF_ROULETTE_COMPLETE -> {
                // handled via MINIGAME_COMPLETE normalization
            }
            case ROULETTE_COMPLETE -> {
                ApplyMovePayload payload = ackStore.loadPayload(room.getId(), GameDto.AckType.ROULETTE_COMPLETE,
                        resolution.keyId,
                        ApplyMovePayload.class);
                if (payload != null) {
                    gameEventStore.schedule(room.getId(), GameEventType.APPLY_MOVE, payload, 0);
                }
            }
        }

        ackStore.clearAck(room.getId(), resolution.ackType, resolution.keyId);
        if (resolution.originalAckType == GameDto.AckType.BUFF_ROULETTE_COMPLETE) {
            ackStore.clearAck(room.getId(), GameDto.AckType.BUFF_ROULETTE_COMPLETE, resolution.keyId);
        }
    }

    private static class AckResolution {
        private final GameDto.AckType ackType;
        private final GameDto.AckType originalAckType;
        private final String keyId;

        private AckResolution(GameDto.AckType ackType, GameDto.AckType originalAckType, String keyId) {
            this.ackType = ackType;
            this.originalAckType = originalAckType;
            this.keyId = keyId;
        }
    }

    private String resolveAckKeyId(Room room, GameDto.AckRequest request) {
        if (request.getQuizId() != null) {
            return String.valueOf(request.getQuizId());
        }
        if (request.getRound() != null) {
            return String.valueOf(request.getRound());
        }
        if (room.getCurrentQuizId() != null) {
            return String.valueOf(room.getCurrentQuizId());
        }
        return String.valueOf(room.getRound());
    }

    public void selectAvatar(GameDto.AvatarSelectRequest request) {
        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            // Update Avatar
            room.getPlayerAvatars().put(request.getUserId(), request.getAvatarId());
            log.info("User {} selected avatar {}", request.getUserId(), request.getAvatarId());

            // Broadcast Update (Lightweight)
            GameDto.AvatarUpdateResponse response = GameDto.AvatarUpdateResponse.builder()
                    .userId(request.getUserId())
                    .avatarId(request.getAvatarId())
                    .build();

            messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/avatar", response);

        } catch (Exception e) {
            log.error("Error selecting avatar", e);
        }
    }

    // --- Quiz Logic ---

    public void startQuiz(String roomId) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            if (room.getStatus() == RoomStatus.WAITING || room.getStatus() == RoomStatus.FINISHED) {
                log.info("Skipping quiz start for room {}. Status: {}", roomId, room.getStatus());
                return;
            }

            // 1. Validate Quiz List
            if (room.getQuizList() == null || room.getQuizList().isEmpty()) {
                log.warn("No quizzes found in room {}", roomId);
                return;
            }

            // 2. Select Quiz (Rotate per turn to avoid repeating per round)
            int turnIndex = 0;
            if (room.getTurnOrder() != null && !room.getTurnOrder().isEmpty() && room.getCurrentTurnUserId() != null) {
                turnIndex = Math.max(0, room.getTurnOrder().indexOf(room.getCurrentTurnUserId()));
            }
            int totalTurnIndex = (room.getRound() - 1) * Math.max(1, room.getTurnOrder().size()) + turnIndex;
            int quizIndex = totalTurnIndex % room.getQuizList().size();
            Quiz quizEntity = room.getQuizList().get(quizIndex);

            Long quizId = quizEntity.getQuizId();

            long quizStartTimeMs = System.currentTimeMillis();
            OffsetDateTime quizStartTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(quizStartTimeMs),
                    ZoneOffset.UTC);
            log.info("Quiz start time (UTC): {}, server zone: {}, server now: {}",
                    quizStartTime, ZoneId.systemDefault(), Instant.now());

            GameDto.QuizMessage quiz = GameDto.QuizMessage.builder()
                    .quizId(quizId)
                    .question(quizEntity.getQuestion())
                    .options(quizEntity.getOptions())
                    .timeLimit(7000L)
                    .quizStartTime(quizStartTime)
                    .quizStartTimeMs(quizStartTimeMs)
                    .build();

            // Set Room State
            room.setCurrentQuizId(quizId);
            room.getQuizAnswers().clear();
            room.setQuizStartTimeMs(quizStartTimeMs);
            room.setStatus(RoomStatus.QUIZ_TIME);
            roomRepository.save(room);

            log.info("Starting Quiz {} for Room {}", quizId, roomId);

            // Broadcast State before Quiz
            broadcastGameState(room);

            // Broadcast Quiz
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", quiz);

            // Schedule Quiz End
            gameEventStore.schedule(roomId, GameEventType.PROCESS_ROUND_RESULT,
                    RoundResultPayload.builder().quizId(quizId).build(),
                    quiz.getTimeLimit());

        } catch (Exception e) {
            log.error("Error starting quiz", e);
        }
    }

    public void submitAnswer(GameDto.SubmitAnswer request) {
        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            // Validate Quiz ID
            if (room.getCurrentQuizId() == null || !room.getCurrentQuizId().equals(request.getQuizId())) {
                log.warn("Invalid quiz submission: {} vs current {}", request.getQuizId(), room.getCurrentQuizId());
                return;
            }

            // Validate Duplicates
            if (room.getQuizAnswers().containsKey(request.getUserId())) {
                sendError(request.getUserId(), ErrorCode.ALREADY_SUBMITTED);
                return;
            }
            // Save Answer with Timestamp
            request.setSubmitTime(LocalDateTime.now());
            room.getQuizAnswers().put(request.getUserId(), request);
            roomRepository.save(room); // Persistence Fix
            log.info("User {} submitted answer: {} at {}", request.getUserId(), request.getAnswer(),
                    request.getSubmitTime());

            // Check if all players submitted
            // User Request update: "Wait for time limit even if all submitted"
            // So we do NOT call processRoundResult here anymore.
            // Just log.
            if (room.getQuizAnswers().size() >= room.getPlayers().size()) {
                log.info("All users submitted for room {}. Waiting for timer.", room.getId());
            }

        } catch (Exception e) {
            log.error("Error submitting answer", e);
        }
    }

    public void processRoundResult(String roomId, Long quizId) {
        try {
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null)
                return;

            if (room.getStatus() != RoomStatus.QUIZ_TIME)
                return;
            if (!quizId.equals(room.getCurrentQuizId()))
                return;

            String turnUser = room.getCurrentTurnUserId();

            Quiz quizEntity = room.getQuizList().stream()
                    .filter(q -> q.getQuizId().equals(quizId))
                    .findFirst()
                    .orElse(null);
            if (quizEntity == null)
                return;

            String correctAnswer = quizEntity.getAnswerText();

            // 정답자 점수(맞춘 퀴즈 수) 누적
            room.getQuizAnswers().forEach((userId, submit) -> {
                if (submit != null && correctAnswer.equals(submit.getAnswer())) {
                    PlayerState ps = room.getPlayerStates().get(userId);
                    if (ps != null) {
                        ps.setScore(ps.getScore() + 1);
                    }
                }
            });

            GameDto.SubmitAnswer turnUserSubmit = room.getQuizAnswers().get(turnUser);
            boolean isTurnUserCorrect = turnUserSubmit != null && correctAnswer.equals(turnUserSubmit.getAnswer());

            String winnerUserId = null;
            String rouletteType = "NONE";
            int moveValue = 0;

            if (isTurnUserCorrect) {
                winnerUserId = turnUser;
                rouletteType = "MOVE";
                moveValue = new Random().nextInt(4) + 1;
            } else {
                Optional<String> fastestGhost = room.getQuizAnswers().entrySet().stream()
                        .filter(e -> !e.getKey().equals(turnUser))
                        .filter(e -> correctAnswer.equals(e.getValue().getAnswer()))
                        .sorted(Comparator.comparing(e -> e.getValue().getSubmitTime()))
                        .map(Map.Entry::getKey)
                        .findFirst();

                if (fastestGhost.isPresent()) {
                    winnerUserId = fastestGhost.get();
                    rouletteType = "MOVE";
                    int[] range = { -2, -1, 1, 2 };
                    moveValue = range[new Random().nextInt(range.length)];
                }
            }

            MovePlanPayload movePlan = null;
            boolean finishPlanned = false;
            List<GameDto.MoveAction> actions = null;

            if (winnerUserId != null && moveValue != 0) {
                PlayerState winnerState = room.getPlayerStates().get(winnerUserId);
                int currentPos = winnerState.getPosition();
                int nextPos = currentPos + moveValue;

                if (nextPos < START_POSITION)
                    nextPos = START_POSITION;
                if (nextPos >= TARGET_POSITION) {
                    nextPos = TARGET_POSITION;
                    finishPlanned = true;
                }

                actions = new ArrayList<>();

                actions.add(GameDto.MoveAction.builder()
                        .type("ROULETTE")
                        .value(moveValue)
                        .before(currentPos)
                        .after(nextPos)
                        .build());

                boolean consumeBuff = winnerState.getHasBuff() != null;
                if ("DOUBLE_SCORE".equals(winnerState.getHasBuff())) {
                    int before = nextPos;
                    nextPos += moveValue;

                    // Boundary Check
                    if (nextPos < START_POSITION)
                        nextPos = START_POSITION;
                    if (nextPos >= TARGET_POSITION) {
                        nextPos = TARGET_POSITION;
                        finishPlanned = true;
                    }

                    actions.add(GameDto.MoveAction.builder()
                            .type("BUFF")
                            .buffType("DOUBLE_SCORE")
                            .before(before)
                            .after(nextPos)
                            .build());
                } else if ("PLUS_THREE".equals(winnerState.getHasBuff())) {
                    int before = nextPos;
                    nextPos += 3;

                    // Boundary Check
                    if (nextPos >= TARGET_POSITION) {
                        nextPos = TARGET_POSITION;
                        finishPlanned = true;
                    }

                    actions.add(GameDto.MoveAction.builder()
                            .type("BUFF")
                            .buffType("PLUS_THREE")
                            .before(before)
                            .after(nextPos)
                            .build());
                }

                if (room.getMapEvents().containsKey(nextPos)) {
                    Set<Integer> visitedTiles = new HashSet<>();
                    while (room.getMapEvents().containsKey(nextPos) && !visitedTiles.contains(nextPos)) {
                        visitedTiles.add(nextPos);
                        String eventType = room.getMapEvents().get(nextPos);
                        int before = nextPos;

                        if ("TILE_X2".equals(eventType)) {
                            nextPos += moveValue;
                            // Boundary Check
                            if (nextPos < START_POSITION)
                                nextPos = START_POSITION;
                            if (nextPos >= TARGET_POSITION) {
                                nextPos = TARGET_POSITION;
                                finishPlanned = true;
                            }
                        }

                        actions.add(GameDto.MoveAction.builder()
                                .type("TILE_EFFECT")
                                .effectType(eventType)
                                .before(before)
                                .after(nextPos)
                                .build());
                    }
                }

                for (String pid : room.getPlayers()) {
                    if (pid.equals(winnerUserId))
                        continue;

                    PlayerState other = room.getPlayerStates().get(pid);
                    if (other != null && other.getPosition() == nextPos && nextPos != START_POSITION) {
                        actions.add(GameDto.MoveAction.builder()
                                .type("CATCH")
                                .targetUserId(pid)
                                .before(other.getPosition())
                                .after(START_POSITION)
                                .build());
                    }
                }

                List<MoveActionPayload> actionPayloads = toActionPayloads(actions);
                movePlan = MovePlanPayload.builder()
                        .winnerUserId(winnerUserId)
                        .startPosition(currentPos)
                        .endPosition(nextPos)
                        .consumeDoubleMove(consumeBuff)
                        .actions(actionPayloads)
                        .build();

            }

            Integer rouletteValue = null;
            if (actions != null) {
                for (GameDto.MoveAction action : actions) {
                    if ("ROULETTE".equals(action.getType())) {
                        rouletteValue = action.getValue();
                        break;
                    }
                }
            }

            GameDto.RoundResult result = GameDto.RoundResult.builder()
                    .quizId(room.getCurrentQuizId())
                    .correctAnswer(correctAnswer)
                    .isTurnUserCorrect(isTurnUserCorrect)
                    .winnerUserId(winnerUserId)
                    .rouletteType(rouletteType)
                    .rouletteValue(rouletteValue)
                    .build();

            room.setStatus(RoomStatus.JUDGMENT);
            roomRepository.save(room);
            broadcastGameState(room);
            messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/result", result);

            String quizKeyId = String.valueOf(quizId);

            if (movePlan != null) {
                boolean hasRouletteAction = rouletteValue != null;
                ApplyMovePayload payload = ApplyMovePayload.builder()
                        .quizId(quizId)
                        .movePlan(movePlan)
                        .finishPlanned(finishPlanned)
                        .build();
                GameDto.AckType applyMoveAckType = hasRouletteAction
                        ? GameDto.AckType.ROULETTE_COMPLETE
                        : GameDto.AckType.RESULT_DISPLAYED;
                ackStore.savePayload(roomId, applyMoveAckType, quizKeyId, payload);
                if (!hasRouletteAction) {
                    gameEventStore.schedule(roomId, GameEventType.APPLY_MOVE, payload, ACK_FALLBACK_TIMEOUT_MS);
                }
            }

            NextTurnPayload nextTurnPayload = NextTurnPayload.builder()
                    .quizId(quizId)
                    .turnUserId(turnUser)
                    .finishPlanned(finishPlanned)
                    .build();
            if (!finishPlanned) {
                ackStore.savePayload(roomId, GameDto.AckType.MOVE_COMPLETE, quizKeyId, nextTurnPayload);
            }
            if (movePlan == null && !finishPlanned) {
                gameEventStore.schedule(roomId, GameEventType.NEXT_TURN, nextTurnPayload, ACK_FALLBACK_TIMEOUT_MS);
            }

        } catch (Exception e) {
            log.error("Error in processRoundResult", e);
        }
    }

    public void applyMove(String roomId, ApplyMovePayload payload) {
        try {
            if (payload == null || payload.getQuizId() == null || payload.getMovePlan() == null) {
                return;
            }
            String quizKeyId = String.valueOf(payload.getQuizId());
            if (!ackStore.tryMarkExecution(roomId, GameEventType.APPLY_MOVE.name(), quizKeyId)) {
                return;
            }
            Room latest = roomRepository.findById(roomId).orElse(null);
            if (latest == null || !payload.getQuizId().equals(latest.getCurrentQuizId())) {
                return;
            }

            MovePlanPayload plannedMove = payload.getMovePlan();
            PlayerState winnerState = latest.getPlayerStates().get(plannedMove.getWinnerUserId());
            if (winnerState == null) {
                return;
            }

            if (plannedMove.isConsumeDoubleMove()) {
                winnerState.setHasBuff(null);
            }

            winnerState.setPosition(plannedMove.getEndPosition());

            if (plannedMove.getActions() != null) {
                for (MoveActionPayload action : plannedMove.getActions()) {
                    if ("CATCH".equals(action.getType())) {
                        PlayerState other = latest.getPlayerStates().get(action.getTargetUserId());
                        if (other != null) {
                            other.setPosition(action.getAfter());
                        }
                    }
                }
            }

            List<GameDto.MoveAction> moveActions = toMoveActions(plannedMove.getActions());
            GameDto.MoveInfo moveInfo = GameDto.MoveInfo.builder()
                    .userId(plannedMove.getWinnerUserId())
                    .startPosition(plannedMove.getStartPosition())
                    .endPosition(plannedMove.getEndPosition())
                    .moveProcess(moveActions)
                    .build();

            if (payload.isFinishPlanned()) {
                latest.setStatus(RoomStatus.FINISHED);
                roomRepository.save(latest);
                messagingTemplate.convertAndSend("/topic/game/" + latest.getId() + "/move", moveInfo);
                messagingTemplate.convertAndSend(
                        "/topic/game/" + latest.getId() + "/finish",
                        buildFinishResponse(latest));
                broadcastGameState(latest);
                return;
            }

            latest.setStatus(RoomStatus.MOVE);
            roomRepository.save(latest);
            messagingTemplate.convertAndSend("/topic/game/" + latest.getId() + "/move", moveInfo);
            broadcastGameState(latest);

            if (!payload.isFinishPlanned()) {
                NextTurnPayload nextTurnPayload = ackStore.loadPayload(
                        roomId, GameDto.AckType.MOVE_COMPLETE, quizKeyId, NextTurnPayload.class);
                if (nextTurnPayload != null) {
                    long moveFallbackMs = estimateMoveFallbackMs(plannedMove);
                    long nextTurnFallbackMs = ACK_FALLBACK_TIMEOUT_MS + moveFallbackMs;
                    gameEventStore.schedule(roomId, GameEventType.NEXT_TURN, nextTurnPayload, nextTurnFallbackMs);
                }
            }
        } catch (Exception e) {
            log.error("Error applying move", e);
        }
    }

    public void prepareNextTurn(String roomId, NextTurnPayload payload) {
        try {
            if (payload == null || payload.isFinishPlanned()) {
                return;
            }
            String quizKeyId = payload.getQuizId() == null ? "unknown" : String.valueOf(payload.getQuizId());
            if (!ackStore.tryMarkExecution(roomId, GameEventType.NEXT_TURN.name(), quizKeyId)) {
                return;
            }
            Room latest = roomRepository.findById(roomId).orElse(null);
            if (latest == null || payload.getQuizId() == null
                    || !payload.getQuizId().equals(latest.getCurrentQuizId())) {
                return;
            }

            List<String> order = latest.getTurnOrder();
            int idx = order.indexOf(payload.getTurnUserId()) + 1;

            if (idx >= order.size()) {
                if (latest.getRound() % 2 != 0) {
                    miniGameServiceProvider.getObject().startMiniGame(roomId);
                    return;
                }
                idx = 0;
                latest.setRound(latest.getRound() + 1);
            }

            latest.setCurrentTurnUserId(order.get(idx));
            latest.setStatus(RoomStatus.STARTING);
            roomRepository.save(latest);
            broadcastGameState(latest);

            gameEventStore.schedule(roomId, GameEventType.START_QUIZ, null, TURN_PREP_DELAY_MS);
        } catch (Exception e) {
            log.error("Error preparing next turn", e);
        }
    }

    private List<MoveActionPayload> toActionPayloads(List<GameDto.MoveAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }
        return actions.stream()
                .map(action -> MoveActionPayload.builder()
                        .type(action.getType())
                        .value(action.getValue())
                        .buffType(action.getBuffType())
                        .effectType(action.getEffectType())
                        .before(action.getBefore())
                        .after(action.getAfter())
                        .targetUserId(action.getTargetUserId())
                        .build())
                .collect(Collectors.toList());
    }

    private List<GameDto.MoveAction> toMoveActions(List<MoveActionPayload> actions) {
        if (actions == null || actions.isEmpty()) {
            return Collections.emptyList();
        }
        return actions.stream()
                .map(action -> GameDto.MoveAction.builder()
                        .type(action.getType())
                        .value(action.getValue() == null ? 0 : action.getValue())
                        .buffType(action.getBuffType())
                        .effectType(action.getEffectType())
                        .before(action.getBefore())
                        .after(action.getAfter())
                        .targetUserId(action.getTargetUserId())
                        .build())
                .collect(Collectors.toList());
    }

    private long estimateMoveFallbackMs(MovePlanPayload movePlan) {
        if (movePlan == null) {
            return ACK_FALLBACK_TIMEOUT_MS;
        }
        int distance = Math.abs(movePlan.getEndPosition() - movePlan.getStartPosition());
        long estimate = (long) distance * MOVE_FALLBACK_STEP_MS + MOVE_FALLBACK_BUFFER_MS;
        long bounded = Math.min(Math.max(estimate, ACK_FALLBACK_TIMEOUT_MS), MOVE_FALLBACK_MAX_MS);
        return bounded;
    }

    public void resumeAfterMiniGame(String roomId, ResumeAfterMiniGamePayload payload) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            log.info("Resuming Game after MiniGame for Room {}", roomId);

            Integer expectedRound = payload != null ? payload.getRound() : room.getRound();
            String roundKeyId = expectedRound == null ? String.valueOf(room.getRound()) : String.valueOf(expectedRound);
            if (!ackStore.tryMarkExecution(roomId, GameEventType.RESUME_AFTER_MINIGAME.name(), roundKeyId)) {
                return;
            }
            if (expectedRound != null && !expectedRound.equals(room.getRound())) {
                return;
            }

            if (!room.getTurnOrder().isEmpty()) {
                room.setRound(room.getRound() + 1);
                room.setCurrentTurnUserId(room.getTurnOrder().get(0));
            }

            room.setStatus(RoomStatus.QUIZ_TIME);

            roomRepository.save(room);

            broadcastGameState(room);

            gameEventStore.schedule(roomId, GameEventType.START_QUIZ, null, TURN_PREP_DELAY_MS);

        } catch (Exception e) {
            log.error("Error resuming game after mini-game", e);
        }
    }

    public void scheduleResumeAfterMiniGame(String roomId) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));
            String roundKeyId = String.valueOf(room.getRound());
            ResumeAfterMiniGamePayload payload = ResumeAfterMiniGamePayload.builder()
                    .round(room.getRound())
                    .build();
            gameEventStore.schedule(roomId, GameEventType.RESUME_AFTER_MINIGAME, payload, MINIGAME_RESUME_FALLBACK_MS);
            ackStore.clearAck(roomId, GameDto.AckType.MINIGAME_COMPLETE, roundKeyId);
            ackStore.clearAck(roomId, GameDto.AckType.BUFF_ROULETTE_COMPLETE, roundKeyId);
        } catch (Exception e) {
            log.error("Error scheduling resume after mini-game", e);
        }
    }

    private GameDto.FinishResponse buildFinishResponse(Room room) {
        List<GameDto.FinishRank> rankings = buildFinishRankings(room);
        String winnerUserId = rankings.isEmpty() ? null : rankings.get(0).getUserId();

        return GameDto.FinishResponse.builder()
                .type("FINISH")
                .winnerUserId(winnerUserId)
                .rankings(rankings)
                .build();
    }

    private List<GameDto.FinishRank> buildFinishRankings(Room room) {
        return room.getPlayerStates().values().stream()
                .map(state -> {
                    User user = userRepository.findById(state.getUserId()).orElse(null);
                    String nickname = user != null ? user.getNickname() : "Unknown";
                    int position = state.getPosition();
                    int remaining = Math.max(TARGET_POSITION - position, 0);
                    int score = state.getScore();
                    return new Object[] { state.getUserId(), nickname, position, remaining, score };
                })
                .sorted((a, b) -> {
                    int remainingA = (int) a[3];
                    int remainingB = (int) b[3];
                    if (remainingA != remainingB) {
                        return Integer.compare(remainingA, remainingB);
                    }
                    int scoreA = (int) a[4];
                    int scoreB = (int) b[4];
                    return Integer.compare(scoreB, scoreA);
                })
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    List<GameDto.FinishRank> ranks = new ArrayList<>();
                    for (int i = 0; i < list.size(); i++) {
                        Object[] item = list.get(i);
                        ranks.add(GameDto.FinishRank.builder()
                                .userId((String) item[0])
                                .nickname((String) item[1])
                                .position((int) item[2])
                                .remaining((int) item[3])
                                .score((int) item[4])
                                .rank(i + 1)
                                .build());
                    }
                    return ranks;
                }));
    }

    private List<Quiz> buildDefaultQuizzes(int count) {
        List<Quiz> quizzes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int a = i;
            int b = i + 1;
            int correct = a + b;

            List<String> options = new ArrayList<>();
            options.add(String.valueOf(correct));
            options.add(String.valueOf(correct + 1));
            options.add(String.valueOf(correct + 2));
            options.add(String.valueOf(correct + 3));

            quizzes.add(Quiz.builder()
                    .quizId((long) i)
                    .question("Sample Q" + i + ": " + a + " + " + b + " = ?")
                    .options(options)
                    .answerIndex(0)
                    .answerText(options.get(0))
                    .build());
        }
        return quizzes;
    }
}

package com.ssafy.docq_backend.domain.game.service;

import com.ssafy.docq_backend.domain.game.entity.PlayerState;
import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.room.entity.Room;
import com.ssafy.docq_backend.domain.room.entity.RoomStatus;
import com.ssafy.docq_backend.domain.room.repository.RoomRepository;
import com.ssafy.docq_backend.domain.player.entity.Player;
import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
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
    private final PlayerRepository playerRepository;
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

    private static final long TURN_PREP_DELAY_MS = 500;
    private static final long INTRO_CAMERA_DURATION_MS = 10000;
    private static final long ACK_FALLBACK_TIMEOUT_MS = 3000;
    private static final long MINIGAME_RESUME_FALLBACK_MS = 8000;
    private static final long ROULETTE_FALLBACK_MS = 7000;
    private static final long MOVE_FALLBACK_STEP_MS = 350;
    private static final long MOVE_FALLBACK_BUFFER_MS = 500;
    private static final long MOVE_FALLBACK_MAX_MS = 12000;
    private static final long QUIZ_TIME_LIMIT_MS = 7000;

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
        if ("movie".equals(type) || "mixed".equals(type)) {
            loadedQuizzes.addAll(loadQuizzesFromJson("movie.json"));
        }
        if ("sports".equals(type) || "mixed".equals(type)) {
            loadedQuizzes.addAll(loadQuizzesFromJson("sports.json"));
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
        log.info("Join Request: roomId={}, userId={}, nickname={}", request.getRoomId(), request.getPlayerId(),
                request.getNickname());

        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            // playerId는 웹소켓 연결 시 세션에서 이미 검증됨 (별도 검증 불필요)

            if (!room.getPlayers().contains(request.getPlayerId())) {
                if (room.getCurrentCount() >= room.getCapacity()) {
                    sendError(request.getPlayerId(), ErrorCode.ROOM_FULL);
                    return;
                }
                if (room.getStatus() != RoomStatus.WAITING) {
                    sendError(request.getPlayerId(), ErrorCode.GAME_ALREADY_STARTED);
                    return;
                }
                room.addPlayer(request.getPlayerId());
                roomRepository.save(room); // Persistence Fix
            }
            broadcastGameState(room);
        } catch (DocqException e) {
            sendError(request.getPlayerId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in joinGame", e);
            sendError(request.getPlayerId(), ErrorCode.SERVER_ERROR);
        }
    }

    public void leaveGame(GameDto.LeaveRequest request) {
        log.info("Leave Request: roomId={}, userId={}", request.getRoomId(), request.getPlayerId());

        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElse(null);
            if (room == null) {
                return;
            }

            String userId = request.getPlayerId();
            boolean wasHost = userId.equals(room.getHostPlayerId());
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

                if (userId.equals(room.getCurrentTurnPlayerId())) {
                    if (!room.getTurnOrder().isEmpty()) {
                        int nextIndex = removedIndex >= room.getTurnOrder().size() ? 0 : Math.max(removedIndex, 0);
                        room.setCurrentTurnPlayerId(room.getTurnOrder().get(nextIndex));
                    } else {
                        room.setCurrentTurnPlayerId(null);
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
                scheduleRouletteFallbackIfNeeded(room);
            }

            roomRepository.save(room);
            broadcastGameState(room);
        } catch (Exception e) {
            log.error("Error in leaveGame", e);
        }
    }

    public void readyGame(GameDto.ReadyRequest request) {
        log.info("Ready Request: roomId={}, userId={}, isReady={}", request.getRoomId(), request.getPlayerId(),
                request.isReady());

        try {
            log.info("Processing Ready Request: {}", request);
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            if (!room.getPlayers().contains(request.getPlayerId())) {
                log.warn("User {} not found in room {} players: {}", request.getPlayerId(), request.getRoomId(),
                        room.getPlayers());
                sendError(request.getPlayerId(), ErrorCode.USER_NOT_FOUND);
                return;
            }
            log.info("Setting ready state for user {} to {}", request.getPlayerId(), request.isReady());

            room.setReady(request.getPlayerId(), request.isReady());
            roomRepository.save(room); // Persistence Fix

            log.info("Broadcasting game state after ready update");
            broadcastGameState(room);
        } catch (DocqException e) {
            log.error("DocqException in readyGame: {}", e.getMessage());
            sendError(request.getPlayerId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in readyGame", e);
            sendError(request.getPlayerId(), ErrorCode.SERVER_ERROR);
        }
    }

    public void startGame(String roomId, String userId) {
        log.info("Start Request: roomId={}, userId={}", roomId, userId);
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            // 권한 체크: 방장만 시작 가능
            if (!userId.equals(room.getHostPlayerId())) {
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
        log.info("End Game Request: roomId={}, userId={}", request.getRoomId(), request.getPlayerId());
        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElse(null);
            if (room == null)
                return;

            if (!request.getPlayerId().equals(room.getHostPlayerId())) {
                sendError(request.getPlayerId(), ErrorCode.ACCESS_DENIED);
                return;
            }

            closeRoom(room.getId(), "HOST_END");
        } catch (Exception e) {
            log.error("Error in endGame", e);
        }
    }

    public void restartGame(GameDto.RestartRequest request) {
        log.info("Restart Request: roomId={}, userId={}", request.getRoomId(), request.getPlayerId());
        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElse(null);
            if (room == null)
                return;

            if (!request.getPlayerId().equals(room.getHostPlayerId())) {
                sendError(request.getPlayerId(), ErrorCode.ACCESS_DENIED);
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
            room.setCurrentTurnPlayerId(room.getTurnOrder().get(0));
        }

        // 플레이어별 상태 초기화
        for (String pid : room.getPlayers()) {
            PlayerState ps = PlayerState.builder()
                    .playerId(pid)
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
                    Player p = playerRepository.findById(userId).orElse(null);
                    if (p == null)
                        return null;

                    // 게임 상태 조회 (없으면 null)
                    PlayerState ps = room.getPlayerStates().get(userId);
                    Integer score = ps != null ? ps.getScore() : null;
                    Integer position = ps != null ? ps.getPosition() : null;

                    String buff = ps != null ? ps.getHasBuff() : null;
                    Integer avatarId = room.getPlayerAvatars().get(userId);

                    return GameDto.PlayerInfo.builder()
                            .playerId(p.getPlayerId())
                            .nickname(p.getNickname())
                            .isHost(p.getPlayerId().equals(room.getHostPlayerId()))
                            .connected(true)
                            .isReady(room.isReady(p.getPlayerId()))
                            .score(score)
                            .position(position)
                            .hasBuff(buff)
                            .avatarId(avatarId)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Integer round = room.getStatus() == RoomStatus.WAITING ? null : room.getRound();
        String currentTurnUserId = room.getStatus() == RoomStatus.WAITING ? null : room.getCurrentTurnPlayerId();
        List<String> turnOrder = room.getStatus() == RoomStatus.WAITING ? null : room.getTurnOrder();
        Map<String, Integer> cardDrawResult = room.getStatus() == RoomStatus.WAITING ? null : room.getCardDrawResult();
        Map<Integer, String> mapEvents = room.getStatus() == RoomStatus.WAITING ? null : room.getMapEvents();

        GameDto.GameStateResponse response = GameDto.GameStateResponse.builder()
                .state(room.getStatus())
                .round(round)
                .currentTurnPlayerId(currentTurnUserId)
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
        room.setCurrentTurnPlayerId(null);
        room.setCurrentQuizId(null);
        room.setQuizStartTimeMs(null);
        room.setGhostOptionsRevealed(false);
        room.setGhostPhaseEndTimeMs(null);
        room.setGhostPhaseTimerScheduled(false);
        room.getQuizAnswers().clear();
        room.getTurnOrder().clear();
        room.getCardDrawResult().clear();
        room.getMapEvents().clear();
        room.getReadyPlayers().clear();
        room.getPlayerStates().clear();

        for (String pid : room.getPlayers()) {
            PlayerState ps = PlayerState.builder()
                    .playerId(pid)
                    .score(0)
                    .position(START_POSITION)
                    .hasBuff(null)
                    .build();
            room.getPlayerStates().put(pid, ps);
        }
        room.setQuizCursor(0);
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
            if (room == null || request.getPlayerId() == null) {
                return;
            }
            if (!room.getPlayers().contains(request.getPlayerId())) {
                return;
            }

            AckResolution resolution = resolveAck(room, request.getAckType(), resolveAckKeyId(room, request),
                    request.getRound());
            if (resolution == null || resolution.keyId == null) {
                return;
            }

            long count = ackStore.addAckAndCount(room.getId(), resolution.ackType, resolution.keyId,
                    request.getPlayerId());
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
        for (AckStore.AckKeyInfo info : ackStore.removePlayerFromRoomAcks(room.getId(), userId)) {
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

    private void scheduleRouletteFallbackIfNeeded(Room room) {
        if (room == null || room.getCurrentQuizId() == null) {
            return;
        }
        if (room.getStatus() != RoomStatus.JUDGMENT) {
            return;
        }
        String keyId = String.valueOf(room.getCurrentQuizId());
        ApplyMovePayload payload = ackStore.loadPayload(room.getId(), GameDto.AckType.ROULETTE_COMPLETE, keyId,
                ApplyMovePayload.class);
        if (payload == null) {
            return;
        }
        gameEventStore.schedule(room.getId(), GameEventType.APPLY_MOVE, payload, ROULETTE_FALLBACK_MS);
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
                NextTurnPayload payload = ackStore.loadPayload(room.getId(), GameDto.AckType.MOVE_COMPLETE,
                        resolution.keyId,
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
            room.getPlayerAvatars().put(request.getPlayerId(), request.getAvatarId());
            log.info("User {} selected avatar {}", request.getPlayerId(), request.getAvatarId());

            // Broadcast Update (Lightweight)
            GameDto.AvatarUpdateResponse response = GameDto.AvatarUpdateResponse.builder()
                    .playerId(request.getPlayerId())
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

            // 2. Select Quiz (Use persistent cursor)
            int quizIndex = room.getQuizCursor() % room.getQuizList().size();
            Quiz quizEntity = room.getQuizList().get(quizIndex);

            // Advance cursor
            room.setQuizCursor(room.getQuizCursor() + 1);

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
                    .timeLimit(QUIZ_TIME_LIMIT_MS)
                    .quizStartTime(quizStartTime)
                    .quizStartTimeMs(quizStartTimeMs)
                    .build();
            GameDto.QuizMessage quizForGhosts = GameDto.QuizMessage.builder()
                    .quizId(quizId)
                    .question(quizEntity.getQuestion())
                    .timeLimit(QUIZ_TIME_LIMIT_MS)
                    .quizStartTime(quizStartTime)
                    .quizStartTimeMs(quizStartTimeMs)
                    .build();

            // Set Room State
            room.setCurrentQuizId(quizId);
            room.getQuizAnswers().clear();
            room.setQuizStartTimeMs(quizStartTimeMs);
            room.setGhostOptionsRevealed(false);
            room.setGhostPhaseEndTimeMs(null);
            room.setGhostPhaseTimerScheduled(false);
            room.setStatus(RoomStatus.QUIZ_TIME);
            roomRepository.save(room);

            log.info("Starting Quiz {} for Room {}", quizId, roomId);

            // Broadcast State before Quiz
            broadcastGameState(room);

            // Broadcast Quiz
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", quizForGhosts);
            if (room.getCurrentTurnPlayerId() != null) {
                // 퀴즈 방해(DEBUFF_MIRROR) 체크
                String turnPlayerId = room.getCurrentTurnPlayerId();
                PlayerState turnPlayerState = room.getPlayerStates().get(turnPlayerId);
                String quizStyle = null;

                if (turnPlayerState != null && "DEBUFF_MIRROR".equals(turnPlayerState.getHasBuff())) {
                    quizStyle = "MIRROR";
                    turnPlayerState.setHasBuff(null); // 1회용 디버프 해제
                    roomRepository.save(room);
                }

                GameDto.QuizMessage finalQuiz = GameDto.QuizMessage.builder()
                        .quizId(quizId)
                        .question(quizEntity.getQuestion())
                        .options(quizEntity.getOptions())
                        .timeLimit(QUIZ_TIME_LIMIT_MS)
                        .quizStartTime(quizStartTime)
                        .quizStartTimeMs(quizStartTimeMs)
                        .quizStyle(quizStyle)
                        .build();

                messagingTemplate.convertAndSend(
                        "/queue/game/" + room.getCurrentTurnPlayerId() + "/quiz",
                        finalQuiz);
            }

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
            if (room.getQuizAnswers().containsKey(request.getPlayerId())) {
                sendError(request.getPlayerId(), ErrorCode.ALREADY_SUBMITTED);
                return;
            }

            String turnUserId = room.getCurrentTurnPlayerId();
            if (turnUserId != null
                    && !turnUserId.equals(request.getPlayerId())
                    && !room.isGhostOptionsRevealed()) {
                sendError(request.getPlayerId(), ErrorCode.GHOST_OPTIONS_HIDDEN);
                return;
            }
            if (turnUserId != null
                    && turnUserId.equals(request.getPlayerId())
                    && room.isGhostOptionsRevealed()) {
                sendError(request.getPlayerId(), ErrorCode.TURN_SUBMISSION_CLOSED);
                return;
            }
            // Save Answer with Timestamp
            request.setSubmitTime(LocalDateTime.now());
            room.getQuizAnswers().put(request.getPlayerId(), request);
            roomRepository.save(room); // Persistence Fix
            log.info("User {} submitted answer: {} at {}", request.getPlayerId(), request.getAnswer(),
                    request.getSubmitTime());

            if (turnUserId != null
                    && turnUserId.equals(request.getPlayerId())
                    && !room.isGhostOptionsRevealed()) {
                Quiz quizEntity = room.getQuizList().stream()
                        .filter(q -> q.getQuizId().equals(room.getCurrentQuizId()))
                        .findFirst()
                        .orElse(null);
                if (quizEntity != null) {
                    boolean isCorrect = quizEntity.getAnswerText().equals(request.getAnswer());
                    if (isCorrect) {
                        // 턴 플레이어 정답 → 즉시 판정
                        processRoundResult(room.getId(), room.getCurrentQuizId());
                    } else {
                        // 턴 플레이어 오답 → 화면에 "틀렸습니다" 전송 후 고스트 페이즈
                        GameDto.AnswerFeedbackMessage feedback = GameDto.AnswerFeedbackMessage.builder()
                                .correct(false)
                                .build();
                        messagingTemplate.convertAndSend("/queue/game/answer-feedback/" + request.getPlayerId(),
                                feedback);
                        room.setGhostOptionsRevealed(true);
                        long ghostEndAt = System.currentTimeMillis() + QUIZ_TIME_LIMIT_MS;
                        room.setGhostPhaseEndTimeMs(ghostEndAt);
                        room.setGhostPhaseTimerScheduled(true);
                        roomRepository.save(room);
                        GameDto.QuizOptionsMessage optionsMessage = GameDto.QuizOptionsMessage.builder()
                                .quizId(room.getCurrentQuizId())
                                .options(quizEntity.getOptions())
                                .extraTimeMs(QUIZ_TIME_LIMIT_MS)
                                .build();
                        messagingTemplate.convertAndSend(
                                "/topic/game/" + room.getId() + "/quiz/options",
                                optionsMessage);
                        gameEventStore.schedule(room.getId(), GameEventType.PROCESS_ROUND_RESULT,
                                RoundResultPayload.builder().quizId(room.getCurrentQuizId()).build(),
                                QUIZ_TIME_LIMIT_MS);
                    }
                }
            }

            if (turnUserId != null
                    && !turnUserId.equals(request.getPlayerId())
                    && room.isGhostOptionsRevealed()
                    && areAllGhostsSubmitted(room, turnUserId)) {
                processRoundResult(room.getId(), room.getCurrentQuizId());
            }

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

            String turnUser = room.getCurrentTurnPlayerId();
            long nowMs = System.currentTimeMillis();
            Long ghostEndAt = room.getGhostPhaseEndTimeMs();
            boolean allGhostsSubmitted = turnUser != null && areAllGhostsSubmitted(room, turnUser);
            if (room.isGhostOptionsRevealed() && ghostEndAt != null && nowMs < ghostEndAt && !allGhostsSubmitted) {
                if (!room.isGhostPhaseTimerScheduled()) {
                    long remaining = ghostEndAt - nowMs;
                    room.setGhostPhaseTimerScheduled(true);
                    roomRepository.save(room);
                    gameEventStore.schedule(roomId, GameEventType.PROCESS_ROUND_RESULT,
                            RoundResultPayload.builder().quizId(quizId).build(),
                            remaining);
                }
                return;
            }

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

            if (!room.isGhostOptionsRevealed() && !isTurnUserCorrect) {
                room.setGhostOptionsRevealed(true);
                long ghostPhaseEnd = System.currentTimeMillis() + QUIZ_TIME_LIMIT_MS;
                room.setGhostPhaseEndTimeMs(ghostPhaseEnd);
                room.setGhostPhaseTimerScheduled(true);
                roomRepository.save(room);
                GameDto.QuizOptionsMessage optionsMessage = GameDto.QuizOptionsMessage.builder()
                        .quizId(room.getCurrentQuizId())
                        .options(quizEntity.getOptions())
                        .extraTimeMs(QUIZ_TIME_LIMIT_MS)
                        .build();
                messagingTemplate.convertAndSend(
                        "/topic/game/" + room.getId() + "/quiz/options",
                        optionsMessage);
                gameEventStore.schedule(roomId, GameEventType.PROCESS_ROUND_RESULT,
                        RoundResultPayload.builder().quizId(quizId).build(),
                        QUIZ_TIME_LIMIT_MS);
                return;
            }

            String winnerUserId = null;
            String rouletteType = "NONE";
            int moveValue = 0;

            if (isTurnUserCorrect) {
                winnerUserId = turnUser;
                rouletteType = "MOVE";
                int[] turnPlayerRange = { -1, 1, 2, 3, 4, 5, 6, 7 };
                moveValue = turnPlayerRange[new Random().nextInt(turnPlayerRange.length)];
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
                    int[] ghostRange = { -1, 1, 2, 3 };
                    moveValue = ghostRange[new Random().nextInt(ghostRange.length)];
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
                            actions.add(GameDto.MoveAction.builder()
                                    .type("TILE_EFFECT")
                                    .effectType(eventType)
                                    .before(before)
                                    .after(nextPos)
                                    .build());
                        } else if ("BANANA".equals(eventType)) {
                            // 바나나 밟음: 뒤로 3칸
                            nextPos -= 3;
                            if (nextPos < START_POSITION)
                                nextPos = START_POSITION;

                            actions.add(GameDto.MoveAction.builder()
                                    .type("TILE_EFFECT")
                                    .effectType("BANANA_SLIP")
                                    .before(before)
                                    .after(nextPos)
                                    .build());

                            // 밟은 바나나 제거 (1회용)
                            room.getMapEvents().remove(before);
                        }
                    }
                }

                for (String pid : room.getPlayers()) {
                    if (pid.equals(winnerUserId))
                        continue;

                    PlayerState other = room.getPlayerStates().get(pid);
                    if (other != null && other.getPosition() == nextPos && nextPos != START_POSITION) {
                        actions.add(GameDto.MoveAction.builder()
                                .type("CATCH")
                                .targetPlayerId(pid)
                                .before(other.getPosition())
                                .after(START_POSITION)
                                .build());
                    }
                }

                List<MoveActionPayload> actionPayloads = toActionPayloads(actions);
                movePlan = MovePlanPayload.builder()
                        .winnerPlayerId(winnerUserId)
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
                    .winnerPlayerId(winnerUserId)
                    .rouletteType(rouletteType)
                    .rouletteValue(rouletteValue)
                    .build();

            room.setStatus(RoomStatus.JUDGMENT);
            room.setGhostPhaseEndTimeMs(null);
            room.setGhostPhaseTimerScheduled(false);
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
                    .turnPlayerId(turnUser)
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

    private boolean areAllGhostsSubmitted(Room room, String turnUserId) {
        if (room == null || room.getPlayers() == null || room.getPlayers().isEmpty()) {
            return false;
        }
        return room.getPlayers().stream()
                .filter(userId -> !userId.equals(turnUserId))
                .allMatch(userId -> room.getQuizAnswers().containsKey(userId));
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
            PlayerState winnerState = latest.getPlayerStates().get(plannedMove.getWinnerPlayerId());
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
                        PlayerState other = latest.getPlayerStates().get(action.getTargetPlayerId());
                        if (other != null) {
                            other.setPosition(action.getAfter());
                        }
                    }
                }
            }

            List<GameDto.MoveAction> moveActions = toMoveActions(plannedMove.getActions());
            GameDto.MoveInfo moveInfo = GameDto.MoveInfo.builder()
                    .playerId(plannedMove.getWinnerPlayerId())
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
            int idx = order.indexOf(payload.getTurnPlayerId()) + 1;

            if (idx >= order.size()) {
                if (latest.getRound() % 2 != 0) {
                    miniGameServiceProvider.getObject().startMiniGame(roomId);
                    return;
                }
                idx = 0;
                latest.setRound(latest.getRound() + 1);
            }

            latest.setCurrentTurnPlayerId(order.get(idx));
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
                        .targetPlayerId(action.getTargetPlayerId())
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
                        .targetPlayerId(action.getTargetPlayerId())
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
                room.setCurrentTurnPlayerId(room.getTurnOrder().get(0));
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
        String winnerUserId = rankings.isEmpty() ? null : rankings.get(0).getPlayerId();

        return GameDto.FinishResponse.builder()
                .type("FINISH")
                .winnerPlayerId(winnerUserId)
                .rankings(rankings)
                .build();
    }

    private List<GameDto.FinishRank> buildFinishRankings(Room room) {
        return room.getPlayerStates().values().stream()
                .map(state -> {
                    Player player = playerRepository.findById(state.getPlayerId()).orElse(null);
                    String nickname = player != null ? player.getNickname() : "Unknown";
                    int position = state.getPosition();
                    int remaining = Math.max(TARGET_POSITION - position, 0);
                    int score = state.getScore();
                    return new Object[] { state.getPlayerId(), nickname, position, remaining, score };
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
                                .playerId((String) item[0])
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

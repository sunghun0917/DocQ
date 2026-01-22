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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AiClient aiClient;
    private static final int DEFAULT_QUIZ_COUNT = 30;

    public void generateQuizFromPdf(String roomId, MultipartFile pdfFile) {
        log.info("Generating Quiz for Room {}: {}", roomId, pdfFile.getOriginalFilename());

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

    // Simple Scheduler for Quiz Timer
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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

            // 바로 퀴즈 시작 (또는 일정 딜레이 후 시작하도록 스케줄링 가능)
            // 여기서는 간단히 바로 첫 퀴즈 출제
            startQuiz(room.getId());

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

        // 맵 특수 효과 배치 (Random 5 tiles with "TILE_X2")
        room.getMapEvents().clear();
        Random random = new Random();
        Set<Integer> eventTiles = new HashSet<>();
        while (eventTiles.size() < 5) {
            int pos = random.nextInt(29) + 1; // 1 ~ 29 (0번은 시작점이라 제외)
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

    private void sendError(String userId, ErrorCode errorCode) {
        GameDto.ErrorResponse errorResponse = GameDto.ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        messagingTemplate.convertAndSend("/queue/errors/" + userId, errorResponse);
        log.info("Sent error to user {}: {}", userId, errorCode.getMessage());
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

            // 1. Validate Quiz List
            if (room.getQuizList() == null || room.getQuizList().isEmpty()) {
                log.warn("No quizzes found in room {}", roomId);
                return;
            }

            // 2. Select Quiz (Round-Robin or fail if out of bounds)
            int quizIndex = (room.getRound() - 1) % room.getQuizList().size();
            Quiz quizEntity = room.getQuizList().get(quizIndex);

            Long quizId = quizEntity.getQuizId();

            GameDto.QuizMessage quiz = GameDto.QuizMessage.builder()
                    .quizId(quizId)
                    .question("Round " + room.getRound() + ": " + quizEntity.getQuestion())
                    .options(quizEntity.getOptions())
                    .timeLimit(5000L)
                    .build();

            // Set Room State
            room.setCurrentQuizId(quizId);
            room.getQuizAnswers().clear();
            room.setQuizStartTime(LocalDateTime.now());

            log.info("Starting Quiz {} for Room {}", quizId, roomId);

            // Broadcast Quiz
            messagingTemplate.convertAndSend("/topic/game/" + roomId + "/quiz", quiz);

            // Schedule Quiz End
            scheduler.schedule(() -> {
                processRoundResult(roomId, quizId);
            }, quiz.getTimeLimit(), TimeUnit.MILLISECONDS);

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

    void processRoundResult(String roomId, Long quizId) {
        try {
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null)
                return;

            // Validate if this is still the current quiz (avoid stale tasks)
            if (!quizId.equals(room.getCurrentQuizId())) {
                log.warn("Skipping stale quiz result processing. Current: {}, Task: {}", room.getCurrentQuizId(),
                        quizId);
                return;
            }

            log.info("Processing Round Result for Room {}, Quiz {}", roomId, quizId);

            String turnUser = room.getCurrentTurnUserId();
            // Fetch Correct Answer from Quiz Entity
            final String correctAnswer;
            if (room.getQuizList() != null && !room.getQuizList().isEmpty()) {
                // Find quiz by quizId to be safe, or use index if strict
                // Using ID matching is safer if logic changes
                Quiz quizEntity = room.getQuizList().stream()
                        .filter(q -> q.getQuizId().equals(quizId))
                        .findFirst()
                        .orElse(null);

                if (quizEntity == null) {
                    log.error("Quiz ID {} not found in room {}", quizId, roomId);
                    return;
                }
                correctAnswer = quizEntity.getAnswerText();
            } else {
                log.error("Quiz list is empty during result processing for room {}", roomId);
                return;
            }

            boolean isTurnUserCorrect = false;
            GameDto.SubmitAnswer turnUserSubmit = room.getQuizAnswers().get(turnUser);

            if (turnUserSubmit != null && correctAnswer.equals(turnUserSubmit.getAnswer())) {
                isTurnUserCorrect = true;
            }

            String winnerUserId = null;
            String rouletteType = "NONE"; // MOVE or BUFF or NONE
            int moveValue = 0;

            // 판정 로직
            if (isTurnUserCorrect) {
                // 턴 주인 정답 -> 턴 주인 승리, 이동 룰렛(1~4)
                winnerUserId = turnUser;
                rouletteType = "MOVE"; // Assuming MOVE is the standard roulette for correct turn
                moveValue = new Random().nextInt(4) + 1; // 1~4
            } else {
                // 턴 주인 오답 -> 고스트 중 가장 빨리 맞춘 사람
                // Find fastest correct ghost
                Optional<String> fastestGhost = room.getQuizAnswers().entrySet().stream()
                        .filter(entry -> !entry.getKey().equals(turnUser)) // Exclude turn user
                        .filter(entry -> correctAnswer.equals(entry.getValue().getAnswer())) // Correct answers only
                        .sorted(Comparator.comparing(entry -> entry.getValue().getSubmitTime())) // Sort by time
                                                                                                 // ascending
                                                                                                 // (fastest first)
                        .map(Map.Entry::getKey)
                        .findFirst();

                if (fastestGhost.isPresent()) {
                    winnerUserId = fastestGhost.get();
                    rouletteType = "MOVE";
                    // 절반 룰렛 (-2 ~ +2)
                    int[] range = { -2, -1, 1, 2 };
                    moveValue = range[new Random().nextInt(range.length)];
                } else {
                    // 아무도 못 맞춤 -> 아무 일도 안 일어남?
                    winnerUserId = null;
                    rouletteType = "NONE";
                }
            }

            // 결과 전송
            GameDto.RoundResult result = GameDto.RoundResult.builder()
                    .quizId(room.getCurrentQuizId())
                    .correctAnswer(correctAnswer)
                    .isTurnUserCorrect(isTurnUserCorrect)
                    .winnerUserId(winnerUserId)
                    .rouletteType(rouletteType)
                    .build();

            messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/result", result);

            // 이동 처리 및 전송 (if winner exists)
            if (winnerUserId != null && moveValue != 0) {
                PlayerState winnerState = room.getPlayerStates().get(winnerUserId);
                int currentPos = winnerState.getPosition();
                int nextPos = currentPos + moveValue;

                // 단순 맵 크기 제한 (e.g. 20칸)
                if (nextPos < 0)
                    nextPos = 0;
                // if (nextPos >= MAX) ...

                // Update State (Initial Move)
                // winnerState.setPosition(nextPos); // Defer update until end of chain

                // Create Move Info
                List<GameDto.MoveAction> actions = new ArrayList<>();

                // 1. Roulette Action
                actions.add(GameDto.MoveAction.builder()
                        .type("ROULETTE")
                        .value(moveValue)
                        .before(currentPos)
                        .after(nextPos)
                        .build());

                // 2. Buff Action (Example: DOUBLE_MOVE)
                // Assuming 'DOUBLE_MOVE' doubles the roulette value
                if ("DOUBLE_MOVE".equals(winnerState.getHasBuff())) {
                    int beforeBuffPos = nextPos;
                    nextPos += moveValue; // Add gain again (x2)

                    actions.add(GameDto.MoveAction.builder()
                            .type("BUFF")
                            .buffType("DOUBLE_MOVE")
                            .before(beforeBuffPos)
                            .after(nextPos)
                            .build());

                    // Consume Buff
                    winnerState.setHasBuff(null);
                    log.info("User {} consumed buff DOUBLE_MOVE", winnerUserId);
                }

                // 3. Tile Effect (Map Event)
                if (room.getMapEvents().containsKey(nextPos)) {
                    String eventType = room.getMapEvents().get(nextPos);

                    actions.add(GameDto.MoveAction.builder()
                            .type("TILE_EFFECT")
                            .effectType(eventType)
                            .before(nextPos)
                            .after(nextPos) // Position doesn't change for X2
                            .build());

                    if ("TILE_X2".equals(eventType)) {
                        log.info("User {} landed on {} and got effect {}", winnerUserId, nextPos, eventType);
                    }
                }

                // 4. Collision / Catching Logic
                // Check if any other player is at nextPos
                for (String pid : room.getPlayers()) {
                    if (pid.equals(winnerUserId))
                        continue; // Skip self

                    PlayerState otherPs = room.getPlayerStates().get(pid);
                    if (otherPs != null && otherPs.getPosition() == nextPos && nextPos != 0) { // Don't catch at
                                                                                               // start(0)
                        // Catch!
                        int caughtPos = 0;
                        int originalPos = otherPs.getPosition();
                        otherPs.setPosition(caughtPos); // Send back to 0

                        actions.add(GameDto.MoveAction.builder()
                                .type("CATCH")
                                .targetUserId(pid)
                                .before(originalPos)
                                .after(caughtPos)
                                .build());

                        log.info("User {} CAUGHT User {} at position {}", winnerUserId, pid, nextPos);
                    }
                }

                // Final Position Update
                winnerState.setPosition(nextPos);
                roomRepository.save(room); // Save state changes (position, buff consumption)

                GameDto.MoveInfo moveInfo = GameDto.MoveInfo.builder()
                        .userId(winnerUserId)
                        .startPosition(currentPos)
                        .endPosition(nextPos)
                        .moveProcess(actions)
                        .build();

                messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/move", moveInfo);

                // Broadcast full state update as well
                broadcastGameState(room);
            }

            // --- Prepare Next Turn / Round ---

            // 1. Find Current Turn Index
            List<String> turnOrder = room.getTurnOrder();
            int currentIndex = turnOrder.indexOf(turnUser);

            // 2. Calculate Next Index
            int nextIndex = currentIndex + 1;

            // 3. Check for Round Change
            if (nextIndex >= turnOrder.size()) {
                // Round Over -> Next Round, Reset to First Player
                nextIndex = 0;
                room.setRound(room.getRound() + 1);
                log.info("Room {} - Round {} Started", room.getId(), room.getRound());
            }

            // 4. Set Next Turn User
            String nextTurnUser = turnOrder.get(nextIndex);
            room.setCurrentTurnUserId(nextTurnUser);
            roomRepository.save(room);

            log.info("Pass Turn: {} -> {} (Round {})", turnUser, nextTurnUser, room.getRound());

            // 5. Broadcast Updated State (Next Turn Info)
            broadcastGameState(room);

            // 6. Schedule Next Quiz (Delay for Animation: e.g., 5 seconds)
            scheduler.schedule(() -> {
                startQuiz(roomId);
            }, 6000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("Error in processRoundResult", e);
        }
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

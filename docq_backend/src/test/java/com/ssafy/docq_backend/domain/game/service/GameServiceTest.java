package com.ssafy.docq_backend.domain.game.service;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.entity.PlayerState;
import com.ssafy.docq_backend.domain.room.entity.Room;
import com.ssafy.docq_backend.domain.room.entity.RoomStatus;
import com.ssafy.docq_backend.domain.room.repository.RoomRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @InjectMocks
    private GameService gameService;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("턴 주인이 틀리고, 고스트가 맞췄을 때 가장 빠른 고스트가 승리한다")
    void testQuizLogic_GhostWin() throws InterruptedException {
        // Given
        String roomId = "room-1";
        String hostId = "user-1";
        String ghost1 = "user-2";
        String ghost2 = "user-3";

        Room room = Room.builder()
                .id(roomId)
                .hostId(hostId)
                .currentCount(3)
                .status(RoomStatus.PLAYING)
                .round(1)
                .players(new HashSet<>(Arrays.asList(hostId, ghost1, ghost2)))
                .turnOrder(Arrays.asList(hostId, ghost1, ghost2))
                .currentTurnUserId(hostId) // Turn Owner: user-1
                .playerStates(new java.util.concurrent.ConcurrentHashMap<>())
                .build();

        // Init Player States
        room.getPlayerStates().put(hostId, PlayerState.builder().userId(hostId).position(0).score(0).build());
        room.getPlayerStates().put(ghost1, PlayerState.builder().userId(ghost1).position(0).score(0).build());
        room.getPlayerStates().put(ghost2, PlayerState.builder().userId(ghost2).position(0).score(0).build());

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        // 1. Start Quiz
        System.out.println("[TEST] Starting Quiz...");
        gameService.startQuiz(roomId);

        // Wait briefly for async scheduler (if needed, but startQuiz uses scheduler for
        // ENDING, not starting)
        Thread.sleep(100);

        ArgumentCaptor<GameDto.QuizMessage> quizCaptor = ArgumentCaptor.forClass(GameDto.QuizMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + roomId + "/quiz"), quizCaptor.capture());
        GameDto.QuizMessage sentQuiz = quizCaptor.getValue();
        Long quizId = sentQuiz.getQuizId();
        System.out.println("[TEST] Quiz Started: ID=" + quizId + ", Q=" + sentQuiz.getQuestion());

        // 2. Submit Answers
        // Correct Answer is "String" based on mock implementation

        // Turn User (Host) -> Wrong Answer
        System.out.println("[TEST] User-1 (Turn Owner) submitting WRONG answer...");
        gameService.submitAnswer(new GameDto.SubmitAnswer(roomId, hostId, quizId, "int", null));
        Thread.sleep(100);

        // Ghost 1 -> Correct Answer (Fastest)
        System.out.println("[TEST] User-2 (Ghost) submitting CORRECT answer...");
        GameDto.SubmitAnswer ans1 = new GameDto.SubmitAnswer(roomId, ghost1, quizId, "String", null);
        gameService.submitAnswer(ans1);
        Thread.sleep(500); // Simulate time gap

        // Ghost 2 -> Correct Answer (Slower)
        System.out.println("[TEST] User-3 (Ghost) submitting CORRECT answer...");
        GameDto.SubmitAnswer ans2 = new GameDto.SubmitAnswer(roomId, ghost2, quizId, "String", null);
        gameService.submitAnswer(ans2);

        // 3. Wait for Timer (Mock 5000ms in service, so we must wait)
        // Since we cannot easily inject specific scheduler into Service here without
        // refactor,
        // we will wait actually or verify logic by manually triggering if that method
        // was public/protected.
        // It is private. But we have a scheduler in the service.
        // The service uses Executors.newScheduledThreadPool(1).

        System.out.println("[TEST] Waiting for Quiz Timer (5s)...");
        // We wait a bit more than 5s to ensure task runs
        Thread.sleep(6000);

        // 4. Verify Result
        ArgumentCaptor<GameDto.RoundResult> resultCaptor = ArgumentCaptor.forClass(GameDto.RoundResult.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + roomId + "/result"), resultCaptor.capture());

        GameDto.RoundResult result = resultCaptor.getValue();
        System.out.println("[TEST] Result Received: Winner=" + result.getWinnerUserId() + ", IsTurnCorrect="
                + result.isTurnUserCorrect());

        assert result.isTurnUserCorrect() == false;
        assert result.getWinnerUserId().equals(ghost1); // ghost1 submitted before ghost2

        System.out.println("[TEST] Verification Successful: Fastest Ghost Won!");
    }
}

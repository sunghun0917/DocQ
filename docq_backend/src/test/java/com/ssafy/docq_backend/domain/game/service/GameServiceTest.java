package com.ssafy.docq_backend.domain.game.service;

import com.ssafy.docq_backend.domain.game.dto.GameDto;
import com.ssafy.docq_backend.domain.game.entity.PlayerState;
import com.ssafy.docq_backend.domain.room.entity.Room;
import com.ssafy.docq_backend.domain.room.entity.RoomStatus;
import com.ssafy.docq_backend.domain.room.repository.RoomRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    private Room room;
    private User host;
    private User player2;

    @BeforeEach
    void setUp() {
        host = User.builder().id("host-id").nickname("HostUser").build();
        player2 = User.builder().id("player2-id").nickname("Player2").build();

        room = Room.builder()
                .id("room-1")
                .hostId(host.getId())
                .title("Test Room")
                .capacity(4)
                .build();

        room.addPlayer(host.getId());
        room.addPlayer(player2.getId());
    }

    @Test
    @DisplayName("게임 시작 성공 테스트: 상태 변경, 순서 결정, 브로드캐스트 확인")
    void testStartGame_Success() {
        // Given
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(userRepository.findById("host-id")).thenReturn(Optional.of(host));
        when(userRepository.findById("player2-id")).thenReturn(Optional.of(player2));

        // When
        gameService.startGame("room-1", "host-id");

        // Then
        // 1. 상태 변경 확인
        assertEquals(RoomStatus.STARTING, room.getStatus(), "방 상태가 STARTING이어야 합니다.");
        assertEquals(1, room.getRound(), "라운드가 1이어야 합니다.");

        // 2. 턴 순서 및 카드 결과 확인
        assertNotNull(room.getTurnOrder(), "턴 순서가 생성되어야 합니다.");
        assertEquals(2, room.getTurnOrder().size(), "플레이어 수만큼 턴 순서가 있어야 합니다.");
        assertNotNull(room.getCardDrawResult(), "카드 뽑기 결과가 있어야 합니다.");
        assertEquals(2, room.getCardDrawResult().size());

        // 3. 플레이어 상태 초기화 확인
        assertEquals(2, room.getPlayerStates().size());
        PlayerState hostState = room.getPlayerStates().get("host-id");
        assertNotNull(hostState);
        assertEquals(0, hostState.getScore());
        assertEquals(0, hostState.getPosition());

        // 4. 브로드캐스트 호출 확인
        ArgumentCaptor<GameDto.GameStateResponse> captor = ArgumentCaptor.forClass(GameDto.GameStateResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/room-1/state"), captor.capture());

        GameDto.GameStateResponse response = captor.getValue();
        assertEquals(RoomStatus.STARTING, response.getState());
        assertEquals(1, response.getRound());
        assertNotNull(response.getTurnOrder());
        assertNotNull(response.getCardDrawResult());

        System.out.println("Turn Order: " + response.getTurnOrder());
        System.out.println("Card Draw Result: " + response.getCardDrawResult());
    }

    @Test
    @DisplayName("게임 시작 실패: 방장이 아닌 경우")
    void testStartGame_Fail_NotHost() {
        // Given
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        // When
        gameService.startGame("room-1", "player2-id"); // Not host

        // Then
        verify(messagingTemplate).convertAndSend(eq("/queue/errors/player2-id"), any(GameDto.ErrorResponse.class));
        assertEquals(RoomStatus.WAITING, room.getStatus(), "상태가 변경되지 않아야 합니다.");
    }

    @Test
    @DisplayName("게임 시작 실패: 인원 부족")
    void testStartGame_Fail_NotEnoughPlayers() {
        // Given
        room.removePlayer(player2.getId()); // 혼자 남음
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        // When
        gameService.startGame("room-1", "host-id");

        // Then
        verify(messagingTemplate).convertAndSend(eq("/queue/errors/host-id"), any(GameDto.ErrorResponse.class));
        assertEquals(RoomStatus.WAITING, room.getStatus());
    }
}

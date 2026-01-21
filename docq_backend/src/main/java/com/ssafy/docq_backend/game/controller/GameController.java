package com.ssafy.docq_backend.game.controller;

import com.ssafy.docq_backend.game.dto.GameDto;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import com.ssafy.docq_backend.room.domain.Room;
import com.ssafy.docq_backend.room.domain.RoomStatus;
import com.ssafy.docq_backend.room.repository.RoomRepository;
import com.ssafy.docq_backend.user.domain.User;
import com.ssafy.docq_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameController {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/game/join")
    public void joinGame(GameDto.JoinRequest request) {
        log.info("Join Request: roomId={}, userId={}, nickname={}", request.getRoomId(), request.getUserId(),
                request.getNickname());

        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            // 사용자 검증
            userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

            // 방 참여 로직 (이미 있으면 무시 or 업데이트)
            if (!room.getPlayers().contains(request.getUserId())) {
                // 인원 꽉 찼는지 다시 확인 (Socket 시점)
                if (room.getCurrentCount() >= room.getCapacity()) {
                    sendError(request.getUserId(), ErrorCode.ROOM_FULL);
                    return;
                }
                // 2. 이미 시작된 게임인지 확인
                if (room.getStatus() == RoomStatus.PLAYING) {
                    sendError(request.getUserId(), ErrorCode.GAME_ALREADY_STARTED);
                    return;
                }

                room.addPlayer(request.getUserId());
            }

            // 전체 브로드캐스팅
            broadcastGameState(room);

        } catch (DocqException e) {
            sendError(request.getUserId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in joinGame", e);
            sendError(request.getUserId(), ErrorCode.SERVER_ERROR);
        }
    }

    @MessageMapping("/game/ready")
    public void readyGame(GameDto.ReadyRequest request) {
        log.info("Ready Request: roomId={}, userId={}, isReady={}", request.getRoomId(), request.getUserId(),
                request.isReady());

        try {
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND));

            if (!room.getPlayers().contains(request.getUserId())) {
                // 방에 없는 유저가 준비 요청? -> 에러 처리 or 무시
                // 여기서는 에러 전송
                sendError(request.getUserId(), ErrorCode.USER_NOT_FOUND);
                return;
            }

            // Ready 상태 업데이트
            room.setReady(request.getUserId(), request.isReady());

            // 전체 브로드캐스팅
            broadcastGameState(room);

        } catch (DocqException e) {
            sendError(request.getUserId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("Unknown error in readyGame", e);
            sendError(request.getUserId(), ErrorCode.SERVER_ERROR);
        }
    }

    private void broadcastGameState(Room room) {
        List<GameDto.PlayerInfo> playerInfos = room.getPlayers().stream()
                .map(userId -> {
                    User u = userRepository.findById(userId).orElse(null);
                    if (u == null)
                        return null;
                    return GameDto.PlayerInfo.builder()
                            .userId(u.getId())
                            .nickname(u.getNickname())
                            .isHost(u.getId().equals(room.getHostId()))
                            .connected(true)
                            .isReady(room.isReady(u.getId()))
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        GameDto.GameStateResponse response = GameDto.GameStateResponse.builder()
                .state(room.getStatus())
                .players(playerInfos)
                .build();

        messagingTemplate.convertAndSend("/topic/game/" + room.getId() + "/state", response);
    }

    private void sendError(String userId, ErrorCode errorCode) {
        GameDto.ErrorResponse errorResponse = GameDto.ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        // 특정 유저에게 에러 메시지 전송 (클라이언트는 /queue/errors/{userId} 구독 필요)
        messagingTemplate.convertAndSend("/queue/errors/" + userId, errorResponse);
        log.info("Sent error to user {}: {}", userId, errorCode.getMessage());
    }
}

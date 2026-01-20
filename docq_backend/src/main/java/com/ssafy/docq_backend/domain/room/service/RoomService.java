package com.ssafy.docq_backend.domain.room.service;

import com.ssafy.docq_backend.domain.room.dto.RoomDto;
import com.ssafy.docq_backend.domain.room.dto.RoomDto.RoomJoinResponse;
import com.ssafy.docq_backend.domain.room.entity.Room;
import com.ssafy.docq_backend.domain.room.entity.RoomStatus;
import com.ssafy.docq_backend.domain.room.repository.RoomRepository;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RoomDto.RoomCreateResponse createRoom(RoomDto.RoomCreateRequest request) {
        // 1. Host 검증
        if (!userRepository.findById(request.getUserId()).isPresent()) {
            throw new DocqException(ErrorCode.USER_NOT_FOUND); // U001
        }

        // 2. 비공개 방일 경우 비밀번호 필수
        if (Boolean.TRUE.equals(request.getIsPrivate())
                && (request.getPassword() == null || request.getPassword().isBlank())) {
            throw new DocqException(ErrorCode.INVALID_INPUT_VALUE); // C002
        }

        String roomId = UUID.randomUUID().toString();
        String inviteCode = generateInviteCode();

        Room room = Room.builder()
                .id(roomId)
                .hostId(request.getUserId())
                .title(request.getTitle())
                .capacity(request.getCapacity())
                .isPrivate(Boolean.TRUE.equals(request.getIsPrivate()))
                .password(request.getPassword())
                .inviteCode(inviteCode)
                .build();

        roomRepository.save(room);

        return RoomDto.RoomCreateResponse.builder()
                .roomId(room.getId())
                .inviteCode(room.getInviteCode())
                .hostId(room.getHostId())
                .isPrivate(room.isPrivate())
                .build();
    }

    private String generateInviteCode() {
        // 간단한 4자리 영대문자+숫자 코드 생성 (중복 체크 포함)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        ThreadLocalRandom random = ThreadLocalRandom.current();

        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = sb.toString();

            if (!roomRepository.existsByInviteCode(code)) {
                return code;
            }
        }
    }

    public RoomDto.RoomResponse findRoomById(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND)); // R001

        return toRoomResponse(room);
    }

    public RoomDto.RoomResponse findRoomByInviteCode(String inviteCode) {
        Room room = roomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND)); // R001

        return toRoomResponse(room);
    }

    private RoomDto.RoomResponse toRoomResponse(Room room) {
        return RoomDto.RoomResponse.builder()
                .roomId(room.getId())
                .title(room.getTitle())
                .isPrivate(room.isPrivate())
                .status(room.getStatus().name())
                .currentCount(room.getCurrentCount())
                .capacity(room.getCapacity())
                .build();
    }

    public RoomJoinResponse verifyJoin(String roomId, String password) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new DocqException(ErrorCode.ROOM_NOT_FOUND)); // R001

        // 1. 방 인원 초과 확인
        if (room.getCurrentCount() >= room.getCapacity()) {
            throw new DocqException(ErrorCode.ROOM_FULL); // R002
        }

        // 2. 이미 시작된 게임 확인
        if (room.getStatus() == RoomStatus.PLAYING) {
            throw new DocqException(ErrorCode.GAME_ALREADY_STARTED); // R003
        }

        // 3. 비밀번호 확인
        if (room.isPrivate()) {
            if (password == null || password.isBlank()) {
                throw new DocqException(ErrorCode.PASSWORD_REQUIRED); // R006
            }
            if (!room.getPassword().equals(password)) {
                throw new DocqException(ErrorCode.INVALID_PASSWORD); // R005
            }
        }

        return RoomJoinResponse.builder()
                .roomId(room.getId())
                .roomCode(room.getInviteCode())
                .status(room.getStatus().name())
                .build();
    }
}

package com.ssafy.docq_backend.domain.room.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ssafy.docq_backend.domain.room.dto.RoomDto;
import com.ssafy.docq_backend.domain.room.service.RoomService;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomDto.RoomCreateResponse> createRoom(
            @Valid @RequestBody RoomDto.RoomCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(request));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomDto.RoomResponse> getRoomById(@PathVariable String roomId) {
        return ResponseEntity.ok(roomService.findRoomById(roomId));
    }

    @GetMapping
    public ResponseEntity<RoomDto.RoomResponse> getRoomByInviteCode(@RequestParam String inviteCode) {
        return ResponseEntity.ok(roomService.findRoomByInviteCode(inviteCode));
    }

    @PostMapping("/join")
    public ResponseEntity<RoomDto.RoomJoinResponse> verifyJoin(@Valid @RequestBody RoomDto.RoomJoinRequest request) {
        return ResponseEntity.ok(roomService.verifyJoin(request.getRoomId(), request.getPassword()));
    }
}

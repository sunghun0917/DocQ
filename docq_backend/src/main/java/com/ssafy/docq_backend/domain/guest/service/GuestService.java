package com.ssafy.docq_backend.domain.guest.service;

import com.ssafy.docq_backend.domain.guest.dto.GuestDto;
import com.ssafy.docq_backend.domain.player.entity.Player;
import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import com.ssafy.docq_backend.global.session.SessionDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;

    public GuestDto.GuestLoginResponse login(GuestDto.GuestLoginRequest request, HttpSession session) {
        // 1. 현재 접속 중인 플레이어 닉네임 중복 체크
        if (playerRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }
        // 2. 가입된 회원 닉네임 중복 체크 (회원 사칭 방지)
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 게스트 플레이어 ID 생성 (중복 방지)
        String playerId;
        do {
            playerId = UUID.randomUUID().toString();
        } while (playerRepository.findById(playerId).isPresent());

        // Player 생성 및 저장
        Player player = Player.guest(playerId, request.getNickname());
        playerRepository.save(player);

        // 게스트 세션 1시간 만료 설정
        session.setMaxInactiveInterval(3600);

        // 세션에 게스트 정보 저장
        SessionDto sessionDto = SessionDto.forGuest(player.getPlayerId(), player.getNickname());
        session.setAttribute("user", sessionDto);

        return GuestDto.GuestLoginResponse.builder()
                .playerId(player.getPlayerId())
                .nickname(player.getNickname())
                .build();
    }

    public GuestDto.GuestNicknameUpdateResponse updateNickname(String playerId,
            GuestDto.GuestNicknameUpdateRequest request, HttpSession session) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        String nextNickname = request.getNickname();

        // 닉네임 변경 시 중복 체크 (자기 자신 제외)
        if (!player.getNickname().equals(nextNickname)) {
            if (playerRepository.existsByNickname(nextNickname)) {
                throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
            }
            if (userRepository.existsByNickname(nextNickname)) {
                throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
            }
        }

        player.setNickname(nextNickname);
        playerRepository.save(player); // In-Memory라서 put으로 덮어쓰기

        // 세션 업데이트
        SessionDto sessionDto = SessionDto.forGuest(playerId, nextNickname);
        session.setAttribute("user", sessionDto);

        return GuestDto.GuestNicknameUpdateResponse.builder()
                .playerId(player.getPlayerId())
                .nickname(nextNickname)
                .build();
    }
}

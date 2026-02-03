package com.ssafy.docq_backend.domain.auth.service;

import com.ssafy.docq_backend.domain.auth.dto.AuthDto;
import com.ssafy.docq_backend.domain.player.entity.Player;
import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import com.ssafy.docq_backend.global.session.SessionDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthDto.SignupResponse signup(AuthDto.SignupRequest request, HttpSession session) {
        // 중복 체크
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new DocqException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }
        // 현재 접속 중인 플레이어(게스트)와 닉네임 중복 체크
        if (playerRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        LocalDateTime now = LocalDateTime.now();

        // 사용자 생성 (BCrypt 암호화)
        User user = User.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .createdAt(now)
                .build();

        user = userRepository.save(user);

        // 회원 플레이어 생성 (playerId 생성 및 저장)
        String playerId = UUID.randomUUID().toString();
        Player player = Player.member(playerId, user.getNickname(), user.getId());
        playerRepository.save(player);

        // 세션 생성
        SessionDto sessionDto = SessionDto.forMember(player.getPlayerId(), user.getId(), user.getLoginId(),
                user.getNickname());
        session.setAttribute("user", sessionDto);

        return AuthDto.SignupResponse.builder()
                .id(user.getId())
                .loginId(user.getLoginId())
                .nickname(user.getNickname())
                .playerId(player.getPlayerId())
                .createdAt(now)
                .build();
    }

    public AuthDto.LoginResponse login(AuthDto.LoginRequest request, HttpSession session) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new DocqException(ErrorCode.INVALID_CREDENTIALS));

        // BCrypt 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new DocqException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 회원 플레이어 생성 (매 로그인마다 새 playerId 부여)
        String playerId = UUID.randomUUID().toString();
        Player player = Player.member(playerId, user.getNickname(), user.getId());

        playerRepository.save(player);

        // 세션 생성
        SessionDto sessionDto = SessionDto.forMember(player.getPlayerId(), user.getId(), user.getLoginId(),
                user.getNickname());
        session.setAttribute("user", sessionDto);

        return AuthDto.LoginResponse.builder()
                .id(user.getId())
                .loginId(user.getLoginId())
                .nickname(user.getNickname())
                .playerId(player.getPlayerId())
                .build();
    }
}

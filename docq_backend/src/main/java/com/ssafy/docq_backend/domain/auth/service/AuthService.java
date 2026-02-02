package com.ssafy.docq_backend.domain.auth.service;

import com.ssafy.docq_backend.domain.auth.dto.AuthDto;
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

        LocalDateTime now = LocalDateTime.now();

        // 사용자 생성 (BCrypt 암호화)
        User user = User.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .createdAt(now)
                .build();

        user = userRepository.save(user);

        // 세션 생성
        String playerId = UUID.randomUUID().toString();
        SessionDto sessionDto = SessionDto.forMember(playerId, user.getId(), user.getLoginId(), user.getNickname());
        session.setAttribute("user", sessionDto);

        return AuthDto.SignupResponse.builder()
                .id(user.getId())
                .loginId(user.getLoginId())
                .nickname(user.getNickname())
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

        // 세션 생성
        String playerId = UUID.randomUUID().toString();
        SessionDto sessionDto = SessionDto.forMember(playerId, user.getId(), user.getLoginId(), user.getNickname());
        session.setAttribute("user", sessionDto);

        return AuthDto.LoginResponse.builder()
                .id(user.getId())
                .loginId(user.getLoginId())
                .nickname(user.getNickname())
                .build();
    }
}

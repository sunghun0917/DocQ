package com.ssafy.docq_backend.global.security.provider;

import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import com.ssafy.docq_backend.global.security.CustomUserDetails;
import com.ssafy.docq_backend.global.security.token.GuestAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuestAuthenticationProvider implements AuthenticationProvider {

    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String nickname = (String) authentication.getPrincipal();

        // 1. 현재 접속 중인 플레이어 닉네임 중복 체크
        if (playerRepository.existsByNickname(nickname)) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }
        // 2. 가입된 회원 닉네임 중복 체크 (회원 사칭 방지)
        if (userRepository.existsByNickname(nickname)) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 인증 성공 -> UserDetails 생성 (playerId는 아직 없음, SuccessHandler에서 생성하거나 여기서 생성)
        // 여기서는 principal 생성을 위해 임시 null 처리하고 Handler에서 최종 생성하거나,
        // 로직 일관성을 위해 여기서 UUID를 생성해서 넘겨주는 방법도 있음.
        // Handler에서 통합 처리하기로 했으므로, 여기서는 닉네임 정보만 유효한 상태로 넘김.

        CustomUserDetails userDetails = CustomUserDetails.forGuest(null, nickname);

        return new GuestAuthenticationToken(userDetails, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return GuestAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

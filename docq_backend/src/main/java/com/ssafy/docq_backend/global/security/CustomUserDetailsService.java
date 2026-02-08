package com.ssafy.docq_backend.global.security;

import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with loginId: " + loginId));

        // 인증 단계에서는 아직 playerId가 없으므로 null로 설정
        // 실제 playerId는 로그인 성공 핸들러(SuccessHandler)에서 생성하여 주입하거나
        // 별도의 세션 관리 로직에서 처리됨
        return CustomUserDetails.forMember(user, null);
    }
}

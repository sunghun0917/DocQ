package com.ssafy.docq_backend.global.security.token;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class GuestAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal; // 인증 전: 닉네임(String), 인증 후: CustomUserDetails

    // 인증 전 생성자 (닉네임만 있음)
    public GuestAuthenticationToken(String nickname) {
        super(null);
        this.principal = nickname;
        setAuthenticated(false);
    }

    // 인증 후 생성자 (UserDetails, 권한 있음)
    public GuestAuthenticationToken(Object principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null; // 게스트는 비밀번호 없음
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }
}

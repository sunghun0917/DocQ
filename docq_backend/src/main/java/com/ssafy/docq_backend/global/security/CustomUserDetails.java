package com.ssafy.docq_backend.global.security;

import com.ssafy.docq_backend.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class CustomUserDetails implements UserDetails {

    private final User user; // 회원이면 not null, 게스트면 null
    @lombok.Setter
    private String playerId; // 게임용 ID (회원/게스트 공통)
    private final String nickname; // 닉네임 (회원/게스트 공통)
    private final boolean isGuest;

    // 회원용 생성자
    public CustomUserDetails(User user, String playerId) {
        this.user = user;
        this.playerId = playerId;
        this.nickname = user.getNickname();
        this.isGuest = false;
    }

    // 게스트용 생성자
    public CustomUserDetails(String playerId, String nickname) {
        this.user = null;
        this.playerId = playerId;
        this.nickname = nickname;
        this.isGuest = true;
    }

    public static CustomUserDetails forMember(User user, String playerId) {
        return new CustomUserDetails(user, playerId);
    }

    public static CustomUserDetails forGuest(String playerId, String nickname) {
        return new CustomUserDetails(playerId, nickname);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (isGuest) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_GUEST"));
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return isGuest ? null : user.getPassword();
    }

    @Override
    public String getUsername() {
        return isGuest ? nickname : user.getLoginId();
    }

    public Long getUserId() {
        return isGuest ? null : user.getId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

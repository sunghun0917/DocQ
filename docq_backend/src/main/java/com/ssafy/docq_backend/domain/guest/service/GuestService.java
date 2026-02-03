package com.ssafy.docq_backend.domain.guest.service;

import com.ssafy.docq_backend.domain.guest.dto.GuestDto;
import com.ssafy.docq_backend.domain.player.entity.Player;
import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
// import com.ssafy.docq_backend.global.session.SessionDto; // Removed
// import jakarta.servlet.http.HttpSession; // Removed
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.ssafy.docq_backend.global.security.CustomUserDetails;
import com.ssafy.docq_backend.global.security.token.GuestAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
// import java.util.UUID; // Removed unused

@Service
@RequiredArgsConstructor
public class GuestService {

    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;

    // NOTE: login 메소드는 Security Filter/Provider로 대체되었으므로 삭제 또는 미사용

    public GuestDto.GuestNicknameUpdateResponse updateNickname(String playerId,
            GuestDto.GuestNicknameUpdateRequest request) {
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

        // SecurityContext 업데이트 (Principal의 닉네임 동기화 필요)
        // 주의: 현재 SecurityContext의 Principal은 불변 객체일 수 있음.
        // 하지만 여기서는 업데이트된 정보를 반영하기 위해 Authentication을 재발급하는 로직을 Controller/Service 어디서든
        // 처리해야 함.
        // 간단한 방법: 여기서는 DB/저장소만 업데이트하고, 실제 Context 갱신은 호출측이나 별도 로직으로 위임?
        // 아니면 여기서 ContextHoler를 직접 호출.
        updateSecurityContext(player);

        return GuestDto.GuestNicknameUpdateResponse.builder()
                .playerId(player.getPlayerId())
                .nickname(nextNickname)
                .build();
    }

    private void updateSecurityContext(Player player) {
        // 새로운 UserDetails 생성
        CustomUserDetails newUserDetails = CustomUserDetails.forGuest(player.getPlayerId(), player.getNickname());

        // 새로운 Authentication 생성 (권한 유지)
        GuestAuthenticationToken newAuth = new GuestAuthenticationToken(
                newUserDetails,
                newUserDetails.getAuthorities());
        newAuth.setAuthenticated(true); // 이미 검증된 상태이므로 true

        // Context 설정
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }
}

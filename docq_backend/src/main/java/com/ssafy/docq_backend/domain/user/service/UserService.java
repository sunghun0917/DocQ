package com.ssafy.docq_backend.domain.user.service;

import com.ssafy.docq_backend.domain.player.repository.PlayerRepository;
import com.ssafy.docq_backend.domain.user.dto.UserDto;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import com.ssafy.docq_backend.global.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import com.ssafy.docq_backend.global.session.SessionDto; // Removed
// import jakarta.servlet.http.HttpSession; // Removed
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;

    public UserDto.LoginIdExistsResponse checkLoginIdExists(String loginId) {
        boolean exists = userRepository.existsByLoginId(loginId);
        return UserDto.LoginIdExistsResponse.builder()
                .available(!exists)
                .message(exists ? "이미 사용 중인 아이디입니다." : "사용 가능한 아이디입니다.")
                .build();
    }

    public UserDto.NicknameExistsResponse checkNicknameExists(String nickname) {
        boolean inUser = userRepository.existsByNickname(nickname);
        boolean inPlayer = playerRepository.existsByNickname(nickname);
        boolean exists = inUser || inPlayer;

        return UserDto.NicknameExistsResponse.builder()
                .exists(exists)
                .message(exists ? "이미 사용중인 닉네임입니다." : "사용 가능한 닉네임입니다.")
                .build();
    }

    public Object getMyInfo(CustomUserDetails userDetails) {
        if (userDetails.isGuest()) {
            return UserDto.GuestMeResponse.builder()
                    .playerId(userDetails.getPlayerId())
                    .nickname(userDetails.getNickname())
                    .isGuest(true)
                    .build();
        } else {
            return UserDto.MemberMeResponse.builder()
                    .id(userDetails.getUserId())
                    .playerId(userDetails.getPlayerId())
                    .loginId(userDetails.getUsername())
                    .nickname(userDetails.getNickname())
                    .isGuest(false)
                    .build();
        }
    }

    @Transactional
    public UserDto.NicknameUpdateResponse updateNickname(Long userId, UserDto.NicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        // 중복 체크
        if (!user.getNickname().equals(request.getNickname()) &&
                userRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        user.setNickname(request.getNickname());
        userRepository.save(user);

        // SecurityContext 업데이트
        // 현재 접속중인 사용자의 context를 업데이트하기 위해,
        // Authentication 객체(UsernamePasswordAuthenticationToken)를 재생성하거나 Principal을
        // 교체해야 함.
        // 하지만 여기서는 Service Layer이므로, PlayerId를 알기 어려울 수 있음 (Controller에서 넘겨받지 않음).
        // -> Controller에서 principal을 넘겨받아서 playerId를 추출하거나,
        // -> 여기서 SecurityContextHolder를 조회해서 현재 Authentication을 가져와서 수정.
        // 현재 스레드의 SecurityContext를 가져오는 것이 확실함.
        updateSecurityContext(user);

        return UserDto.NicknameUpdateResponse.builder()
                .nickname(request.getNickname())
                .build();
    }

    private void updateSecurityContext(User user) {
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();

        String playerId = null;
        if (currentAuth.getPrincipal() instanceof CustomUserDetails) {
            playerId = ((CustomUserDetails) currentAuth.getPrincipal()).getPlayerId();
        }

        CustomUserDetails newUserDetails = CustomUserDetails.forMember(user, playerId);

        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                newUserDetails,
                currentAuth.getCredentials(),
                newUserDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(newAuth);
    }
}

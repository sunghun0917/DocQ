package com.ssafy.docq_backend.domain.user.service;

import com.ssafy.docq_backend.domain.user.dto.UserDto;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import com.ssafy.docq_backend.global.session.SessionDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserDto.LoginIdExistsResponse checkLoginIdExists(String loginId) {
        boolean exists = userRepository.existsByLoginId(loginId);
        return UserDto.LoginIdExistsResponse.builder()
                .available(!exists)
                .message(exists ? "이미 사용 중인 아이디입니다." : "사용 가능한 아이디입니다.")
                .build();
    }

    public UserDto.NicknameExistsResponse checkNicknameExists(String nickname) {
        boolean exists = userRepository.existsByNickname(nickname);
        return UserDto.NicknameExistsResponse.builder()
                .exists(exists)
                .message(exists ? "이미 사용중인 닉네임입니다." : "사용 가능한 닉네임입니다.")
                .build();
    }

    public Object getMyInfo(SessionDto sessionDto) {
        if (sessionDto.isGuest()) {
            return UserDto.GuestMeResponse.builder()
                    .playerId(sessionDto.getPlayerId())
                    .nickname(sessionDto.getNickname())
                    .isGuest(true)
                    .build();
        } else {
            return UserDto.MemberMeResponse.builder()
                    .id(sessionDto.getId())
                    .playerId(sessionDto.getPlayerId())
                    .loginId(sessionDto.getLoginId())
                    .nickname(sessionDto.getNickname())
                    .isGuest(false)
                    .build();
        }
    }

    @Transactional
    public UserDto.NicknameUpdateResponse updateNickname(Long userId, UserDto.NicknameUpdateRequest request,
            HttpSession session) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        // 중복 체크
        if (!user.getNickname().equals(request.getNickname()) &&
                userRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        user.setNickname(request.getNickname());
        userRepository.save(user);

        // 세션 업데이트
        SessionDto currentSession = (SessionDto) session.getAttribute("user");
        SessionDto updatedSession = SessionDto.forMember(
                currentSession.getPlayerId(),
                userId,
                currentSession.getLoginId(),
                request.getNickname());
        session.setAttribute("user", updatedSession);

        return UserDto.NicknameUpdateResponse.builder()
                .nickname(request.getNickname())
                .build();
    }
}

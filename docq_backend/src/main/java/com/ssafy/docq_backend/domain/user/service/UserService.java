package com.ssafy.docq_backend.domain.user.service;

import com.ssafy.docq_backend.domain.user.dto.UserDto;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserDto.UserLoginResponse login(UserDto.UserLoginRequest request) {
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String userId;
        do {
            userId = UUID.randomUUID().toString();
        } while (userRepository.findById(userId).isPresent());

        User user = User.builder()
                .id(userId)
                .nickname(request.getNickname())
                .build();

        userRepository.save(user);

        return UserDto.UserLoginResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .build();
    }

    public UserDto.UserNicknameUpdateResponse updateNickname(String userId, UserDto.UserNicknameUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        String nextNickname = request.getNickname();
        if (!user.getNickname().equals(nextNickname) && userRepository.existsByNickname(nextNickname)) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        user.setNickname(nextNickname);
        userRepository.save(user);

        return UserDto.UserNicknameUpdateResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .build();
    }
}

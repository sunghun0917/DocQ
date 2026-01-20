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
}

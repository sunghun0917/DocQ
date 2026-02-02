package com.ssafy.docq_backend.domain.guest.service;

import com.ssafy.docq_backend.domain.guest.dto.GuestDto;
import com.ssafy.docq_backend.domain.guest.entity.Guest;
import com.ssafy.docq_backend.domain.guest.repository.GuestRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import com.ssafy.docq_backend.global.session.SessionDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;

    public GuestDto.GuestLoginResponse login(GuestDto.GuestLoginRequest request, HttpSession session) {
        if (guestRepository.existsByNickname(request.getNickname())) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        String playerId;
        do {
            playerId = UUID.randomUUID().toString();
        } while (guestRepository.findById(playerId).isPresent());

        Guest guest = Guest.builder()
                .id(playerId)
                .nickname(request.getNickname())
                .build();

        guestRepository.save(guest);

        // 게스트 세션 1시간 만료 설정
        session.setMaxInactiveInterval(3600); // 1시간 = 3600초

        // 세션에 게스트 정보 저장
        SessionDto sessionDto = SessionDto.forGuest(playerId, request.getNickname());
        session.setAttribute("user", sessionDto);

        return GuestDto.GuestLoginResponse.builder()
                .playerId(guest.getId())
                .nickname(guest.getNickname())
                .build();
    }

    public GuestDto.GuestNicknameUpdateResponse updateNickname(String playerId,
            GuestDto.GuestNicknameUpdateRequest request, HttpSession session) {
        Guest guest = guestRepository.findById(playerId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        String nextNickname = request.getNickname();
        if (!guest.getNickname().equals(nextNickname) && guestRepository.existsByNickname(nextNickname)) {
            throw new DocqException(ErrorCode.DUPLICATE_NICKNAME);
        }

        guest.setNickname(nextNickname);
        guestRepository.save(guest);

        // 세션 업데이트
        SessionDto sessionDto = SessionDto.forGuest(playerId, nextNickname);
        session.setAttribute("user", sessionDto);

        return GuestDto.GuestNicknameUpdateResponse.builder()
                .playerId(guest.getId())
                .nickname(guest.getNickname())
                .build();
    }
}

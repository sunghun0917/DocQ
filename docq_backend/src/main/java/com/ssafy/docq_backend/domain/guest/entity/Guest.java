package com.ssafy.docq_backend.domain.guest.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class Guest {
    private String id;
    private String nickname;
}

package com.ssafy.docq_backend.domain.game.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quiz {
    private Long quizId;
    private String question;
    private List<String> options;
    private int answerIndex;
    private String answerText;
    private String explanation;
}

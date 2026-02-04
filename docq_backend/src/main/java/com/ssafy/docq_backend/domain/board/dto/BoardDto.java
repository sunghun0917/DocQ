package com.ssafy.docq_backend.domain.board.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class BoardDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBoardRequest {
        @NotNull(message = "퀴즈 ID는 필수입니다.")
        private Long quizId;

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100)
        private String title;

        private String content;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBoardResponse {
        private Long boardId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LikeResponse {
        private Long boardId;
        private boolean liked;
        private long likeCount;
    }

    /** 게시판 목록 응답 (페이지네이션) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardListResponse {
        private int page;
        private int size;
        private long total;
        private java.util.List<BoardListItem> items;
    }

    /** 게시판 목록 한 건 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardListItem {
        private Long boardId;
        private String title;
        private String content;
        private AuthorInfo author;
        private Long quizId; // nullable (quiz가 없는 게시글도 있을 수 있음)
        private int questionCount; // 퀴즈 문제 개수
        private long likeCount;
        private long commentCount;
        private long storedCount; // 해당 퀴즈를 저장한 사용자 수
        private boolean likedByMe;
        private boolean storedByMe;
        private LocalDateTime createdAt;
    }

    /** 작성자 요약 (목록용) */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorInfo {
        private Long userId;
        private String nickname;
    }

    /** 게시글 상세 조회 응답 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardDetailResponse {
        private Long boardId;
        private String title;
        private String content;
        private AuthorInfo author;
        private QuizSummary quiz;
        private long likeCount;
        private long commentCount;
        private boolean likedByMe;
        private boolean storedByMe;
        private LocalDateTime createdAt;
    }

    /** 게시글 상세에서 사용하는 퀴즈 요약 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizSummary {
        private Long quizId;
        private int quizCount;
        private java.util.List<String> quizList; // question 문자열만
        private String quizTitle;
    }
}

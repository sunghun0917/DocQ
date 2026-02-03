package com.ssafy.docq_backend.domain.comment.controller;

import com.ssafy.docq_backend.domain.comment.dto.CommentDto;
import com.ssafy.docq_backend.domain.comment.service.CommentService;
import com.ssafy.docq_backend.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 댓글 API.
 * SecurityConfig: /boards/** 에 포함. 작성/삭제는 hasRole("USER"), 목록은 authenticated.
 * 사용자 식별: @AuthenticationPrincipal CustomUserDetails.
 */
@RestController
@RequestMapping("/boards/{boardId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 댓글 작성. 회원 전용.
     */
    @PostMapping
    public ResponseEntity<CommentDto.CreateCommentResponse> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long boardId,
            @Valid @RequestBody CommentDto.CreateCommentRequest request
    ) {
        Long userId = user != null ? user.getUserId() : null;
        CommentDto.CreateCommentResponse response = commentService.create(userId, boardId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 댓글 삭제. 댓글 작성자 전용.
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long boardId,
            @PathVariable Long commentId
    ) {
        Long userId = user != null ? user.getUserId() : null;
        commentService.delete(userId, boardId, commentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 댓글 목록 조회 (페이지네이션).
     * 권한: 게스트 / 회원
     */
    @GetMapping
    public ResponseEntity<CommentDto.CommentListResponse> getList(
            @PathVariable Long boardId,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        CommentDto.CommentListResponse response = commentService.getList(boardId, page, size);
        return ResponseEntity.ok(response);
    }
}

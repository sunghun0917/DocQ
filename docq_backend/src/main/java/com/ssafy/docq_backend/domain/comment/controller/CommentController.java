package com.ssafy.docq_backend.domain.comment.controller;

import com.ssafy.docq_backend.domain.comment.dto.CommentDto;
import com.ssafy.docq_backend.domain.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 댓글 API.
 * 현재는 Security 없이 X-User-Id 헤더로 사용자 식별(목업).
 */
@RestController
@RequestMapping("/boards/{boardId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 댓글 작성.
     * 회원 전용 (X-User-Id 헤더).
     */
    @PostMapping
    public ResponseEntity<CommentDto.CreateCommentResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long boardId,
            @Valid @RequestBody CommentDto.CreateCommentRequest request
    ) {
        CommentDto.CreateCommentResponse response = commentService.create(userId, boardId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 댓글 삭제.
     * 권한: 댓글 작성자 전용 (X-User-Id 헤더).
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long boardId,
            @PathVariable Long commentId
    ) {
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

package com.ssafy.docq_backend.domain.board.controller;

import com.ssafy.docq_backend.domain.board.dto.BoardDto;
import com.ssafy.docq_backend.domain.board.service.BoardService;
import com.ssafy.docq_backend.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 게시판 API.
 * 인증: SecurityConfig 기준 GET은 authenticated, POST/DELETE/likes는 hasRole("USER").
 * 사용자 식별: @AuthenticationPrincipal CustomUserDetails (회원이면 getUserId(), 게스트면 null).
 */
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 게시글 상세 조회.
     * 권한: 게스트 / 회원. 회원인 경우 likedByMe, storedByMe 계산.
     */
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDto.BoardDetailResponse> getBoard(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long boardId
    ) {
        Long userId = user != null ? user.getUserId() : null;
        BoardDto.BoardDetailResponse response = boardService.getBoard(userId, boardId);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시판 목록 조회 (페이지네이션).
     * 권한: 게스트 / 회원. 회원인 경우 likedByMe, storedByMe 계산.
     */
    @GetMapping
    public ResponseEntity<BoardDto.BoardListResponse> getList(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Long userId = user != null ? user.getUserId() : null;
        BoardDto.BoardListResponse response = boardService.getList(userId, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<BoardDto.CreateBoardResponse> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody BoardDto.CreateBoardRequest request) {
        Long userId = user != null ? user.getUserId() : null;
        BoardDto.CreateBoardResponse response = boardService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 게시글 삭제. 작성자만 삭제 가능 (principal의 userId와 게시글 user_id 일치).
     */
    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long boardId) {
        Long userId = user != null ? user.getUserId() : null;
        boardService.delete(userId, boardId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 게시글 좋아요. 회원 전용. 이미 좋아요한 경우에도 200 + 현재 상태 반환.
     */
    @PostMapping("/{boardId}/likes")
    public ResponseEntity<BoardDto.LikeResponse> like(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long boardId) {
        Long userId = user != null ? user.getUserId() : null;
        BoardDto.LikeResponse response = boardService.like(userId, boardId);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 좋아요 취소. 회원 전용. 이미 취소된 경우에도 200 + 현재 상태 반환.
     */
    @DeleteMapping("/{boardId}/likes")
    public ResponseEntity<BoardDto.LikeResponse> unlike(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long boardId) {
        Long userId = user != null ? user.getUserId() : null;
        BoardDto.LikeResponse response = boardService.unlike(userId, boardId);
        return ResponseEntity.ok(response);
    }
}

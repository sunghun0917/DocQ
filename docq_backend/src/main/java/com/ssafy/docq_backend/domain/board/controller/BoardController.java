package com.ssafy.docq_backend.domain.board.controller;

import com.ssafy.docq_backend.domain.board.dto.BoardDto;
import com.ssafy.docq_backend.domain.board.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 게시판 API.
 * userId가 있는 사용자만 접근 가능 (현재는 Security/Filter 없이 목업: X-User-Id 헤더로 전달).
 */
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /**
     * 게시글 상세 조회.
     * 권한: 게스트 / 회원
     * 회원인 경우 X-User-Id 헤더로 likedByMe, storedByMe 계산.
     */
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDto.BoardDetailResponse> getBoard(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long boardId
    ) {
        BoardDto.BoardDetailResponse response = boardService.getBoard(userId, boardId);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시판 목록 조회 (페이지네이션).
     * 권한: 게스트 / 회원
     * 회원인 경우 X-User-Id 헤더로 likedByMe, storedByMe 계산.
     * 최초 진입 시 파라미터 없이 호출하면 page=1, size=20 적용.
     */
    @GetMapping
    public ResponseEntity<BoardDto.BoardListResponse> getList(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        BoardDto.BoardListResponse response = boardService.getList(userId, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<BoardDto.CreateBoardResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody BoardDto.CreateBoardRequest request
    ) {
        BoardDto.CreateBoardResponse response = boardService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 게시글 삭제. 작성자만 삭제 가능 (X-User-Id가 해당 게시글의 user_id와 일치해야 함).
     */
    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long boardId
    ) {
        boardService.delete(userId, boardId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 게시글 좋아요. 회원 전용 (X-User-Id 헤더). 이미 좋아요한 경우에도 200 + 현재 상태 반환.
     */
    @PostMapping("/{boardId}/likes")
    public ResponseEntity<BoardDto.LikeResponse> like(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long boardId
    ) {
        BoardDto.LikeResponse response = boardService.like(userId, boardId);
        return ResponseEntity.ok(response);
    }

    /**
     * 게시글 좋아요 취소. 회원 전용 (X-User-Id 헤더). 이미 취소된 경우에도 200 + 현재 상태 반환.
     */
    @DeleteMapping("/{boardId}/likes")
    public ResponseEntity<BoardDto.LikeResponse> unlike(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long boardId
    ) {
        BoardDto.LikeResponse response = boardService.unlike(userId, boardId);
        return ResponseEntity.ok(response);
    }
}

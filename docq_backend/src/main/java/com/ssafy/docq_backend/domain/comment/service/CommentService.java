package com.ssafy.docq_backend.domain.comment.service;

import com.ssafy.docq_backend.domain.board.entity.Board;
import com.ssafy.docq_backend.domain.board.repository.BoardRepository;
import com.ssafy.docq_backend.domain.comment.dto.CommentDto;
import com.ssafy.docq_backend.domain.comment.entity.Comment;
import com.ssafy.docq_backend.domain.comment.repository.CommentRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    /**
     * 댓글 작성.
     */
    @Transactional
    public CommentDto.CreateCommentResponse create(Long userId, Long boardId, CommentDto.CreateCommentRequest request) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new DocqException(ErrorCode.BOARD_NOT_FOUND));

        Comment comment = new Comment();
        comment.setUser(user);
        comment.setBoard(board);
        comment.setContent(request.getContent());
        comment.setCreatedAt(Instant.now());

        Comment saved = commentRepository.save(comment);

        return CommentDto.CreateCommentResponse.builder()
                .commentId(saved.getId())
                .build();
    }

    /**
     * 댓글 삭제. 작성자만 삭제 가능.
     */
    @Transactional
    public void delete(Long userId, Long boardId, Long commentId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new DocqException(ErrorCode.COMMENT_NOT_FOUND));

        // 게시글 ID 확인
        if (!comment.getBoard().getId().equals(boardId)) {
            throw new DocqException(ErrorCode.COMMENT_NOT_FOUND);
        }

        // 작성자 확인
        if (!comment.getUser().getId().equals(userId)) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        commentRepository.delete(comment);
    }

    /**
     * 댓글 목록 조회 (페이지네이션).
     */
    @Transactional(readOnly = true)
    public CommentDto.CommentListResponse getList(Long boardId, int page, int size) {
        // 게시글 존재 확인
        if (!boardRepository.existsById(boardId)) {
            throw new DocqException(ErrorCode.BOARD_NOT_FOUND);
        }

        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Comment> commentPage = commentRepository.findByBoard_IdOrderByCreatedAtAsc(boardId, pageable);
        long total = commentPage.getTotalElements();

        List<CommentDto.CommentItem> items = commentPage.getContent().stream()
                .map(comment -> {
                    // Instant를 LocalDateTime으로 변환 (KST 기준)
                    LocalDateTime createdAt = LocalDateTime.ofInstant(
                            comment.getCreatedAt(),
                            ZoneId.of("Asia/Seoul")
                    );

                    return CommentDto.CommentItem.builder()
                            .commentId(comment.getId())
                            .author(CommentDto.AuthorInfo.builder()
                                    .userId(comment.getUser().getId())
                                    .nickname(comment.getUser().getNickname())
                                    .build())
                            .content(comment.getContent())
                            .createdAt(createdAt)
                            .build();
                })
                .collect(Collectors.toList());

        return CommentDto.CommentListResponse.builder()
                .page(safePage)
                .size(safeSize)
                .total(total)
                .items(items)
                .build();
    }
}

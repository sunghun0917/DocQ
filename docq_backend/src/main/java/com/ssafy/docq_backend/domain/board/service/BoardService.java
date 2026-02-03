package com.ssafy.docq_backend.domain.board.service;

import com.ssafy.docq_backend.domain.board.dto.BoardDto;
import com.ssafy.docq_backend.domain.board.entity.Board;
import com.ssafy.docq_backend.domain.board.repository.BoardRepository;
import com.ssafy.docq_backend.domain.comment.repository.CommentRepository;
import com.ssafy.docq_backend.domain.like.entity.Like;
import com.ssafy.docq_backend.domain.like.repository.LikeRepository;
import com.ssafy.docq_backend.domain.quiz.entity.Quiz;
import com.ssafy.docq_backend.domain.quiz.repository.QuizRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoardService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public BoardDto.CreateBoardResponse create(Long userId, BoardDto.CreateBoardRequest request) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        Quiz quiz = quizRepository.findById(request.getQuizId())
                .orElseThrow(() -> new DocqException(ErrorCode.QUIZ_NOT_FOUND));

        Board board = new Board();
        board.setUser(user);
        board.setQuiz(quiz);
        board.setTitle(request.getTitle());
        board.setContent(request.getContent() != null ? request.getContent() : "");
        board.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")));

        Board saved = boardRepository.save(board);

        return BoardDto.CreateBoardResponse.builder()
                .boardId(saved.getId())
                .build();
    }

    @Transactional
    public void delete(Long userId, Long boardId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new DocqException(ErrorCode.BOARD_NOT_FOUND));

        if (!board.getUser().getId().equals(userId)) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        boardRepository.delete(board);
    }

    @Transactional
    public BoardDto.LikeResponse like(Long userId, Long boardId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new DocqException(ErrorCode.BOARD_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        if (!likeRepository.existsByUser_IdAndBoard_Id(userId, boardId)) {
            Like like = new Like();
            like.setUser(user);
            like.setBoard(board);
            like.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            likeRepository.save(like);
        }

        long likeCount = likeRepository.countByBoard_Id(boardId);

        return BoardDto.LikeResponse.builder()
                .boardId(boardId)
                .liked(true)
                .likeCount(likeCount)
                .build();
    }

    @Transactional
    public BoardDto.LikeResponse unlike(Long userId, Long boardId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        boardRepository.findById(boardId)
                .orElseThrow(() -> new DocqException(ErrorCode.BOARD_NOT_FOUND));

        likeRepository.findByUser_IdAndBoard_Id(userId, boardId)
                .ifPresent(likeRepository::delete);

        long likeCount = likeRepository.countByBoard_Id(boardId);

        return BoardDto.LikeResponse.builder()
                .boardId(boardId)
                .liked(false)
                .likeCount(likeCount)
                .build();
    }

    @Transactional(readOnly = true)
    public BoardDto.BoardListResponse getList(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var boardPage = boardRepository.findAll(pageable);
        long total = boardPage.getTotalElements();

        List<BoardDto.BoardListItem> items = boardPage.getContent().stream()
                .map(board -> BoardDto.BoardListItem.builder()
                        .boardId(board.getId())
                        .title(board.getTitle())
                        .content(board.getContent())
                        .author(BoardDto.AuthorInfo.builder()
                                .userId(board.getUser().getId())
                                .nickname(board.getUser().getNickname())
                                .build())
                        .likeCount(likeRepository.countByBoard_Id(board.getId()))
                        .commentCount(commentRepository.countByBoard_Id(board.getId()))
                        .createdAt(board.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return BoardDto.BoardListResponse.builder()
                .page(safePage)
                .size(safeSize)
                .total(total)
                .items(items)
                .build();
    }
}

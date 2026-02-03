package com.ssafy.docq_backend.domain.board.service;

import com.ssafy.docq_backend.domain.board.dto.BoardDto;
import com.ssafy.docq_backend.domain.board.entity.Board;
import com.ssafy.docq_backend.domain.board.repository.BoardRepository;
import com.ssafy.docq_backend.domain.comment.repository.CommentRepository;
import com.ssafy.docq_backend.domain.like.entity.Like;
import com.ssafy.docq_backend.domain.like.repository.LikeRepository;
import com.ssafy.docq_backend.domain.quiz.entity.Quiz;
import com.ssafy.docq_backend.domain.quiz.repository.QuizRepository;
import com.ssafy.docq_backend.domain.storedquiz.repository.StoredQuizRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class BoardService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final StoredQuizRepository storedQuizRepository;

    @Transactional
    public BoardDto.CreateBoardResponse create(Long userId, BoardDto.CreateBoardRequest request) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        // 동일한 퀴즈로 이미 게시글이 존재하는지 검사
        if (boardRepository.existsByQuiz_Id(request.getQuizId())) {
            throw new DocqException(ErrorCode.DUPLICATE_QUIZ);
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
    public BoardDto.BoardDetailResponse getBoard(Long userId, Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new DocqException(ErrorCode.BOARD_NOT_FOUND));

        Quiz quiz = board.getQuiz();

        // quizList에서 question만 추출
        List<String> questions = List.of();
        int quizCount = 0;
        try {
            if (quiz != null && quiz.getQuizList() != null) {
                questions = quiz.getQuizList().stream()
                        .map(q -> (String) q.getOrDefault("question", ""))
                        .collect(Collectors.toList());
                quizCount = questions.size();
            }
        } catch (Exception e) {
            // 기존 DB에 quiz_list 포맷이 맞지 않는 경우 등 예외 발생 시 로그만 찍고 빈 리스트로 처리
            log.warn("Failed to parse quiz_list for quiz id={}", quiz != null ? quiz.getId() : null, e);
            questions = List.of();
            quizCount = 0;
        }

        long likeCount = likeRepository.countByBoard_Id(boardId);
        long commentCount = commentRepository.countByBoard_Id(boardId);

        boolean likedByMe = false;
        boolean storedByMe = false;

        if (userId != null) {
            likedByMe = likeRepository.existsByUser_IdAndBoard_Id(userId, boardId);
            if (quiz != null) {
                // 내가 게시판에서 저장한 퀴즈인지 여부 (stored_quizzes 존재 여부)
                storedByMe = storedQuizRepository.existsByUser_IdAndQuiz_Id(userId, quiz.getId());
            }
        }

        BoardDto.AuthorInfo authorInfo = BoardDto.AuthorInfo.builder()
                .userId(board.getUser().getId())
                .nickname(board.getUser().getNickname())
                .build();

        BoardDto.QuizSummary quizSummary = null;
        if (quiz != null) {
            quizSummary = BoardDto.QuizSummary.builder()
                    .quizId(quiz.getId())
                    .quizCount(quizCount)
                    .quizList(questions)
                    .build();
        }

        return BoardDto.BoardDetailResponse.builder()
                .boardId(board.getId())
                .title(board.getTitle())
                .content(board.getContent())
                .author(authorInfo)
                .quiz(quizSummary)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .likedByMe(likedByMe)
                .storedByMe(storedByMe)
                .createdAt(board.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public BoardDto.BoardListResponse getList(Long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var boardPage = boardRepository.findAll(pageable);
        long total = boardPage.getTotalElements();

        List<BoardDto.BoardListItem> items = boardPage.getContent().stream()
                .map(board -> {
                    long likeCount = likeRepository.countByBoard_Id(board.getId());
                    long commentCount = commentRepository.countByBoard_Id(board.getId());
                    boolean likedByMe = false;
                    boolean storedByMe = false;
                    long storedCount = 0;
                    int questionCount = 0;
                    Quiz quiz = board.getQuiz();
                    
                    // 퀴즈 문제 개수 계산
                    if (quiz != null && quiz.getQuizList() != null) {
                        try {
                            questionCount = quiz.getQuizList().size();
                        } catch (Exception e) {
                            // 기존 DB에 quiz_list 포맷이 맞지 않는 경우 등 예외 발생 시 0으로 처리
                            log.warn("Failed to get quiz count for quiz id={}", quiz.getId(), e);
                            questionCount = 0;
                        }
                    }
                    
                    if (userId != null) {
                        likedByMe = likeRepository.existsByUser_IdAndBoard_Id(userId, board.getId());
                        if (quiz != null) {
                            storedByMe = storedQuizRepository.existsByUser_IdAndQuiz_Id(userId, quiz.getId());
                            storedCount = storedQuizRepository.countByQuiz_Id(quiz.getId());
                        }
                    } else if (quiz != null) {
                        // 게스트도 저장 개수는 볼 수 있음
                        storedCount = storedQuizRepository.countByQuiz_Id(quiz.getId());
                    }
                    return BoardDto.BoardListItem.builder()
                            .boardId(board.getId())
                            .title(board.getTitle())
                            .content(board.getContent())
                            .author(BoardDto.AuthorInfo.builder()
                                    .userId(board.getUser().getId())
                                    .nickname(board.getUser().getNickname())
                                    .build())
                            .quizId(quiz != null ? quiz.getId() : null)
                            .questionCount(questionCount)
                            .likeCount(likeCount)
                            .commentCount(commentCount)
                            .storedCount(storedCount)
                            .likedByMe(likedByMe)
                            .storedByMe(storedByMe)
                            .createdAt(board.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return BoardDto.BoardListResponse.builder()
                .page(safePage)
                .size(safeSize)
                .total(total)
                .items(items)
                .build();
    }
}

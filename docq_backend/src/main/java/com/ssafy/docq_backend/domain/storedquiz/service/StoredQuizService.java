package com.ssafy.docq_backend.domain.storedquiz.service;

import com.ssafy.docq_backend.domain.quiz.dto.QuizApiDto;
import com.ssafy.docq_backend.domain.quiz.entity.Quiz;
import com.ssafy.docq_backend.domain.quiz.repository.QuizRepository;
import com.ssafy.docq_backend.domain.storedquiz.entity.StoredQuiz;
import com.ssafy.docq_backend.domain.storedquiz.repository.StoredQuizRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoredQuizService {

    private final StoredQuizRepository storedQuizRepository;
    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    /**
     * 퀴즈를 내 퀴즈에 저장.
     * 이미 저장되어 있으면 그대로 반환 (idempotent).
     */
    @Transactional
    public QuizApiDto.StoreQuizResponse store(Long userId, Long quizId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }
        if (quizId == null) {
            throw new DocqException(ErrorCode.QUIZ_NOT_FOUND);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new DocqException(ErrorCode.QUIZ_NOT_FOUND));

        // 이미 저장되어 있는지 확인
        boolean alreadyStored = storedQuizRepository.existsByUser_IdAndQuiz_Id(userId, quizId);
        if (alreadyStored) {
            return QuizApiDto.StoreQuizResponse.builder()
                    .quizId(quizId)
                    .stored(true)
                    .build();
        }

        // 저장
        StoredQuiz storedQuiz = new StoredQuiz();
        storedQuiz.setUser(user);
        storedQuiz.setQuiz(quiz);
        storedQuizRepository.save(storedQuiz);

        return QuizApiDto.StoreQuizResponse.builder()
                .quizId(quizId)
                .stored(true)
                .build();
    }

    /**
     * 퀴즈 저장 취소 (언저장).
     * 저장되어 있지 않으면 그대로 반환 (idempotent).
     */
    @Transactional
    public QuizApiDto.StoreQuizResponse unstore(Long userId, Long quizId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }
        if (quizId == null) {
            throw new DocqException(ErrorCode.QUIZ_NOT_FOUND);
        }

        // 사용자와 퀴즈 존재 여부 확인
        if (!userRepository.existsById(userId)) {
            throw new DocqException(ErrorCode.USER_NOT_FOUND);
        }
        if (!quizRepository.existsById(quizId)) {
            throw new DocqException(ErrorCode.QUIZ_NOT_FOUND);
        }

        // 저장되어 있는지 확인
        boolean exists = storedQuizRepository.existsByUser_IdAndQuiz_Id(userId, quizId);
        if (!exists) {
            return QuizApiDto.StoreQuizResponse.builder()
                    .quizId(quizId)
                    .stored(false)
                    .build();
        }

        // 삭제
        storedQuizRepository.findByUser_IdAndQuiz_Id(userId, quizId)
                .ifPresent(storedQuizRepository::delete);

        return QuizApiDto.StoreQuizResponse.builder()
                .quizId(quizId)
                .stored(false)
                .build();
    }

    /**
     * 내가 저장한 퀴즈 목록 조회.
     */
    @Transactional(readOnly = true)
    public QuizApiDto.MyQuizListResponse getStoredQuizzes(Long userId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        // 유저 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new DocqException(ErrorCode.USER_NOT_FOUND);
        }

        List<StoredQuiz> storedQuizzes = storedQuizRepository.findByUser_IdOrderByIdDesc(userId);

        List<QuizApiDto.MyQuizItem> items = storedQuizzes.stream()
                .map(stored -> {
                    Quiz quiz = stored.getQuiz();
                    int questionCount = 0;
                    try {
                        if (quiz.getQuizList() != null) {
                            questionCount = quiz.getQuizList().size();
                        }
                    } catch (Exception e) {
                        questionCount = 0;
                    }

                    return QuizApiDto.MyQuizItem.builder()
                            .quizId(quiz.getId())
                            .title(quiz.getQuizTitle() != null ? quiz.getQuizTitle() : "제목 없음")
                            .questionCount(questionCount)
                            .build();
                })
                .collect(Collectors.toList());

        return QuizApiDto.MyQuizListResponse.builder()
                .quizzes(items)
                .build();
    }
}

package com.ssafy.docq_backend.domain.quiz.service;

import com.ssafy.docq_backend.domain.quiz.dto.QuizApiDto;
import com.ssafy.docq_backend.domain.quiz.entity.Quiz;
import com.ssafy.docq_backend.domain.quiz.repository.QuizRepository;
import com.ssafy.docq_backend.domain.user.entity.User;
import com.ssafy.docq_backend.domain.user.repository.UserRepository;
import com.ssafy.docq_backend.global.exception.DocqException;
import com.ssafy.docq_backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserRepository userRepository;

    /**
     * PDF 파싱 결과(문제 리스트)를 받아 퀴즈 엔티티로 저장.
     */
    @Transactional
    public QuizApiDto.CreateQuizResponse create(Long userId, QuizApiDto.CreateQuizRequest request) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        List<Map<String, Object>> quizList = request.getQuizzes().stream()
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("question", item.getQuestion());
                    map.put("options", item.getOptions());
                    map.put("answerIndex", item.getAnswerIndex());
                    map.put("answerText", item.getAnswerText());
                    
                    return map;
                })
                .collect(Collectors.toList());

        Quiz quiz = new Quiz();
        quiz.setUser(user);
        quiz.setQuizList(quizList);
        // 타이틀 설정 (없으면 null)
        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            String title = request.getTitle().trim();
            // 최대 길이 제한 (45자)
            if (title.length() > 45) {
                title = title.substring(0, 45);
            }
            quiz.setQuizTitle(title);
        }

        Quiz saved = quizRepository.save(quiz);

        return QuizApiDto.CreateQuizResponse.builder()
                .quizId(saved.getId())
                .quizCount(quizList.size())
                .build();
    }

    /**
     * 내가 만든 퀴즈 목록 조회.
     */
    @Transactional(readOnly = true)
    public QuizApiDto.MyQuizListResponse getMyQuizzes(Long userId) {
        if (userId == null) {
            throw new DocqException(ErrorCode.ACCESS_DENIED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocqException(ErrorCode.USER_NOT_FOUND));

        List<Quiz> quizzes = quizRepository.findByUser_IdOrderByIdDesc(userId);

        List<QuizApiDto.MyQuizItem> items = quizzes.stream()
                .map(quiz -> {
                    int questionCount = 0;
                    try {
                        if (quiz.getQuizList() != null) {
                            questionCount = quiz.getQuizList().size();
                        }
                    } catch (Exception e) {
                        // 기존 DB에 quiz_list 포맷이 맞지 않는 경우 등 예외 발생 시 0으로 처리
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


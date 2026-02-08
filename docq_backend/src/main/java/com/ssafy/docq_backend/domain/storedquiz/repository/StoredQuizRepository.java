package com.ssafy.docq_backend.domain.storedquiz.repository;

import com.ssafy.docq_backend.domain.storedquiz.entity.StoredQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoredQuizRepository extends JpaRepository<StoredQuiz, Long> {

    boolean existsByUser_IdAndQuiz_Id(Long userId, Long quizId);

    long countByQuiz_Id(Long quizId);

    Optional<StoredQuiz> findByUser_IdAndQuiz_Id(Long userId, Long quizId);

    List<StoredQuiz> findByUser_IdOrderByIdDesc(Long userId);
}

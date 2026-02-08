package com.ssafy.docq_backend.domain.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.docq_backend.domain.quiz.entity.Quiz;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByUser_IdOrderByIdDesc(Long userId);
}

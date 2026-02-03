package com.ssafy.docq_backend.domain.quiz.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.docq_backend.domain.quiz.entity.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
}

package com.ssafy.docq_backend.domain.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.docq_backend.domain.board.entity.Board;

public interface BoardRepository extends JpaRepository<Board, Long> {

    boolean existsByQuiz_Id(Long quizId);
}

package com.ssafy.docq_backend.domain.like.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.docq_backend.domain.like.entity.Like;


import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUser_IdAndBoard_Id(Long userId, Long boardId);

    long countByBoard_Id(Long boardId);

    Optional<Like> findByUser_IdAndBoard_Id(Long userId, Long boardId);
}

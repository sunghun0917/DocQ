package com.ssafy.docq_backend.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.docq_backend.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}

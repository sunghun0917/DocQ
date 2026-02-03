package com.ssafy.docq_backend.domain.user.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

import com.ssafy.docq_backend.domain.storedquiz.entity.StoredQuiz;
import com.ssafy.docq_backend.domain.board.entity.Board;
import com.ssafy.docq_backend.domain.comment.entity.Comment;
import com.ssafy.docq_backend.domain.like.entity.Like;
import com.ssafy.docq_backend.domain.quiz.entity.Quiz;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 50)
    @NotNull
    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Size(max = 20)
    @NotNull
    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Size(max = 255)
    @NotNull
    @Column(name = "password", nullable = false)
    private String password;

    @OneToMany(mappedBy = "user")
    private Set<Board> boards = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<Comment> comments = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<Like> likes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<Quiz> quizzes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "user")
    private Set<StoredQuiz> storedQuizzes = new LinkedHashSet<>();


}
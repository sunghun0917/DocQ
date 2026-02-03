package com.ssafy.docq_backend.domain.quiz.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import com.ssafy.docq_backend.domain.user.entity.User;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.ssafy.docq_backend.domain.board.entity.Board;
import com.ssafy.docq_backend.domain.storedquiz.entity.StoredQuiz;

@Getter
@Setter
@Entity
@Table(name = "quizzes")
public class Quiz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz_list")
    private Map<String, Object> quizList;

    @OneToMany(mappedBy = "quiz")
    private Set<Board> boards = new LinkedHashSet<>();

    @OneToMany(mappedBy = "quiz")
    private Set<StoredQuiz> storedQuizzes = new LinkedHashSet<>();

}
package com.nonononoki.alovoa.entity;

import java.util.Date;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "question_id"})
    },
    indexes = {
        @Index(name = "idx_response_user", columnList = "user_id"),
        @Index(name = "idx_response_question", columnList = "question_id"),
        @Index(name = "idx_response_category", columnList = "category")
    }
)
public class UserAssessmentResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private AssessmentQuestion question;

    @Column
    private Integer numericResponse;

    @Column(columnDefinition = "text")
    private String textResponse;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AssessmentQuestion.QuestionCategory category;

    @Column(nullable = false)
    private Date answeredAt;

    @Column
    private Date updatedAt;

    @PrePersist
    protected void onCreate() {
        if (answeredAt == null) {
            answeredAt = new Date();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}

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

    // === OKCupid-Style Matching Fields ===

    /**
     * User's importance rating for this question.
     * Values: irrelevant, a_little, somewhat, very, mandatory
     * Default: somewhat (weight 10)
     */
    @Column(length = 20)
    private String importance;

    /**
     * JSON array of acceptable answers (OkCupid-style).
     * For Likert 1-5, might be: [1,2] meaning only "strongly disagree" or "disagree" acceptable
     * For multiple choice with options a-g, might be: ["a","b","c"]
     * If null, defaults to "same answer or within 1 point"
     */
    @Column(columnDefinition = "text")
    private String acceptableAnswers;

    /**
     * Optional explanation for why this answer matters to the user.
     * Shown to matches to provide context.
     */
    @Column(length = 500)
    private String explanation;

    /**
     * Whether this question should be shown publicly on profile.
     * Default: false (private, used only for matching)
     */
    @Column
    private Boolean publicVisible = false;

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

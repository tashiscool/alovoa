package com.nonononoki.alovoa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = {
    @Index(name = "idx_question_category", columnList = "category"),
    @Index(name = "idx_question_external_id", columnList = "externalId")
})
public class AssessmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionCategory category;

    @Column
    private String subcategory;

    @Column
    @Enumerated(EnumType.STRING)
    private ResponseScale responseScale;

    @Column
    private String domain;

    @Column
    private Integer facet;

    @Column
    private String keyed;

    @Column
    private String dimension;

    @Column
    private Integer redFlagValue;

    @Column
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column
    private Boolean inverse;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    public enum QuestionCategory {
        BIG_FIVE,
        ATTACHMENT,
        DEALBREAKER,
        VALUES,
        LIFESTYLE,
        RED_FLAG
    }

    public enum ResponseScale {
        LIKERT_5,
        AGREEMENT_5,
        BINARY,
        FREQUENCY_5,
        FREE_TEXT
    }

    public enum Severity {
        CRITICAL,
        HIGH,
        MODERATE,
        LOW
    }
}

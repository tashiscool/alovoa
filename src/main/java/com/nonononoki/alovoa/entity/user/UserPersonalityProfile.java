package com.nonononoki.alovoa.entity.user;

import java.util.Date;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class UserPersonalityProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne
    @EqualsAndHashCode.Exclude
    private User user;

    // Big Five (OCEAN) scores - 0 to 100
    private Double openness;
    private Double conscientiousness;
    private Double extraversion;
    private Double agreeableness;
    private Double neuroticism;

    // Attachment style
    @Enumerated(EnumType.STRING)
    private AttachmentStyle attachmentStyle;
    private Double attachmentConfidence;

    // Communication preferences - 0 to 100
    private Double communicationDirectness;
    private Double communicationEmotional;

    // Raw answers stored for re-analysis (JSON)
    @Lob
    @Column(columnDefinition = "mediumtext")
    private String valuesAnswers;

    // References to vector embeddings (stored in Pinecone/pgvector)
    @Column(length = 100)
    private String personalityEmbeddingId;

    @Column(length = 100)
    private String valuesEmbeddingId;

    @Column(length = 100)
    private String interestsEmbeddingId;

    private Date assessmentCompletedAt;

    @Column(nullable = false)
    private Date updatedAt = new Date();

    private Integer assessmentVersion = 1;

    public enum AttachmentStyle {
        SECURE,
        ANXIOUS,
        AVOIDANT,
        DISORGANIZED
    }

    public boolean isComplete() {
        return openness != null && conscientiousness != null &&
               extraversion != null && agreeableness != null &&
               neuroticism != null;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Date();
    }
}

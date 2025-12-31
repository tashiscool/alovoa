package com.nonononoki.alovoa.entity.user;

import java.util.Date;
import java.util.UUID;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class UserVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private UUID uuid;

    @JsonIgnore
    @ManyToOne
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoType videoType;

    @Column(length = 500)
    private String videoUrl;

    @Column(length = 500)
    private String thumbnailUrl;

    private Integer durationSeconds;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String transcript;

    @Lob
    @Column(columnDefinition = "mediumtext")
    private String sentimentScores;

    private Boolean isIntro = false;

    private Boolean isVerified = false;

    @Column(nullable = false)
    private Date createdAt = new Date();

    public enum VideoType {
        INTRO,
        DAY_IN_LIFE,
        HOBBY,
        RESPONSE,
        VERIFICATION
    }

    @PrePersist
    protected void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}

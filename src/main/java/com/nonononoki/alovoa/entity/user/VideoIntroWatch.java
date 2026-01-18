package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * Tracks when users watch video introductions.
 * Part of the video-first display system that shows video before photos.
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"viewer_id", "profile_owner_id"}),
       indexes = {
           @Index(name = "idx_video_watch_viewer", columnList = "viewer_id"),
           @Index(name = "idx_video_watch_owner", columnList = "profile_owner_id")
       })
public class VideoIntroWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viewer_id", nullable = false)
    @JsonIgnore
    private User viewer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_owner_id", nullable = false)
    @JsonIgnore
    private User profileOwner;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date watchedAt = new Date();

    /**
     * How long the viewer watched in seconds
     */
    private Integer watchDurationSeconds = 0;

    /**
     * Whether the viewer watched the full video
     */
    @Column(nullable = false)
    private Boolean completed = false;

    @PrePersist
    protected void onCreate() {
        if (watchedAt == null) {
            watchedAt = new Date();
        }
    }
}

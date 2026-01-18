package com.nonononoki.alovoa.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;

/**
 * Tracks individual profile edit events for frequency monitoring.
 * Part of AURA's Profile Coach anti-optimization system.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
    @Index(name = "idx_profile_edit_user", columnList = "user_id"),
    @Index(name = "idx_profile_edit_date", columnList = "user_id, edit_date"),
    @Index(name = "idx_profile_edit_type", columnList = "edit_type")
})
public class ProfileEditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "edit_type", nullable = false, length = 50)
    private EditType editType;

    @Column(name = "field_edited", length = 100)
    private String fieldEdited;

    @Column(name = "edit_date", nullable = false)
    private LocalDate editDate;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "edit_timestamp", nullable = false)
    private Date editTimestamp = new Date();

    @Column(name = "session_id", length = 100)
    private String sessionId;

    public enum EditType {
        PHOTO_ADD,
        PHOTO_DELETE,
        PHOTO_REORDER,
        PROFILE_PICTURE,
        BIO_UPDATE,
        PROMPT_ADD,
        PROMPT_UPDATE,
        PROMPT_DELETE,
        INTEREST_ADD,
        INTEREST_REMOVE,
        SETTINGS_CHANGE,
        VIDEO_INTRO,
        OTHER
    }

    @PrePersist
    protected void onCreate() {
        if (editDate == null) {
            editDate = LocalDate.now();
        }
        if (editTimestamp == null) {
            editTimestamp = new Date();
        }
    }
}

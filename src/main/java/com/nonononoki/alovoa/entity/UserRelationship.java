package com.nonononoki.alovoa.entity;

import com.nonononoki.alovoa.entity.user.UserMiscInfo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.UUID;

/**
 * Represents a linked relationship between two users.
 * Similar to Facebook's "In a relationship with..." feature.
 * Both users must agree to be linked.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user1_id", "user2_id"})
})
public class UserRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "BINARY(16)", unique = true)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipStatus status;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date confirmedAt;

    @Temporal(TemporalType.TIMESTAMP)
    private Date anniversaryDate;

    private Boolean isPublic;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        if (createdAt == null) createdAt = new Date();
        if (isPublic == null) isPublic = true;
    }

    /**
     * Types of relationships that can be displayed.
     */
    public enum RelationshipType {
        DATING("Dating"),
        IN_A_RELATIONSHIP("In a relationship"),
        ENGAGED("Engaged"),
        MARRIED("Married"),
        DOMESTIC_PARTNERSHIP("Domestic partnership"),
        CIVIL_UNION("Civil union"),
        ITS_COMPLICATED("It's complicated"),
        OPEN_RELATIONSHIP("In an open relationship");

        private final String displayName;

        RelationshipType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Status of the relationship link request.
     */
    public enum RelationshipStatus {
        PENDING,    // Request sent, waiting for other user to confirm
        CONFIRMED,  // Both users have agreed
        DECLINED,   // Other user declined the request
        ENDED       // Relationship was ended by one of the users
    }

    /**
     * Get the partner user for a given user.
     */
    public User getPartner(User user) {
        if (user.getId().equals(user1.getId())) {
            return user2;
        } else if (user.getId().equals(user2.getId())) {
            return user1;
        }
        return null;
    }

    /**
     * Check if a user is part of this relationship.
     */
    public boolean involvesUser(User user) {
        return user.getId().equals(user1.getId()) || user.getId().equals(user2.getId());
    }

    /**
     * Check if this user initiated the relationship request.
     */
    public boolean isInitiator(User user) {
        return user.getId().equals(user1.getId());
    }
}

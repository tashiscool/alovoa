package com.nonononoki.alovoa.entity.user;

import com.nonononoki.alovoa.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * Tracks when one user views another user's profile.
 * Similar to OKCupid's "Visitors" feature.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(indexes = {
    @Index(name = "idx_visit_visited", columnList = "visited_user_id"),
    @Index(name = "idx_visit_visitor", columnList = "visitor_id"),
    @Index(name = "idx_visit_date", columnList = "visitedAt")
})
public class UserProfileVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visited_user_id", nullable = false)
    private User visitedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visitor_id", nullable = false)
    private User visitor;

    @Temporal(TemporalType.TIMESTAMP)
    private Date visitedAt;

    // Count of visits (incremented on repeat visits)
    private int visitCount = 1;

    // Last visit timestamp (updated on repeat visits)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastVisitAt;

    public UserProfileVisit(User visitor, User visitedUser) {
        this.visitor = visitor;
        this.visitedUser = visitedUser;
        this.visitedAt = new Date();
        this.lastVisitAt = new Date();
    }

    public void recordVisit() {
        this.visitCount++;
        this.lastVisitAt = new Date();
    }
}

package com.nonononoki.alovoa.entity.user;

import java.util.Date;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nonononoki.alovoa.entity.User;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "matchDate"})
})
public class UserDailyMatchLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date matchDate;

    @Column(nullable = false)
    private Integer matchesShown = 0;

    @Column(nullable = false)
    private Integer matchLimit = 5;

    // JSON array of user IDs already shown today
    @Lob
    @Column(columnDefinition = "mediumtext")
    private String shownUserIds;

    public boolean hasReachedLimit() {
        return matchesShown >= matchLimit;
    }

    public int getRemainingMatches() {
        return Math.max(0, matchLimit - matchesShown);
    }
}

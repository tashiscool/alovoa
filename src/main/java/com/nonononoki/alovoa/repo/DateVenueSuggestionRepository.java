package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.Conversation;
import com.nonononoki.alovoa.entity.user.DateVenueSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

public interface DateVenueSuggestionRepository extends JpaRepository<DateVenueSuggestion, Long> {

    List<DateVenueSuggestion> findByConversationAndDismissedFalseOrderBySuggestedAtDesc(Conversation conversation);

    @Query("SELECT d FROM DateVenueSuggestion d WHERE d.userA = :user OR d.userB = :user ORDER BY d.suggestedAt DESC")
    List<DateVenueSuggestion> findByUserOrderBySuggestedAtDesc(User user);

    @Query("SELECT d FROM DateVenueSuggestion d WHERE (d.userA = :user OR d.userB = :user) AND d.dismissed = false AND d.accepted = false")
    List<DateVenueSuggestion> findActiveByUser(User user);

    boolean existsByUserAAndUserBAndVenueCategoryAndSuggestedAtAfter(
            User userA, User userB, String venueCategory, Date after);

    long countByAcceptedTrue();
}

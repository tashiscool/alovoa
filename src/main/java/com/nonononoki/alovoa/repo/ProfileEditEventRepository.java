package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.ProfileEditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProfileEditEventRepository extends JpaRepository<ProfileEditEvent, Long> {

    /**
     * Count edits for a user on a specific date
     */
    long countByUserAndEditDate(User user, LocalDate editDate);

    /**
     * Count edits for a user within a date range
     */
    long countByUserAndEditDateBetween(User user, LocalDate startDate, LocalDate endDate);

    /**
     * Get all edits for a user on a specific date
     */
    List<ProfileEditEvent> findByUserAndEditDateOrderByEditTimestampDesc(User user, LocalDate editDate);

    /**
     * Get recent edits for a user
     */
    List<ProfileEditEvent> findByUserAndEditDateAfterOrderByEditTimestampDesc(User user, LocalDate afterDate);

    /**
     * Count edits by type for a user on a date
     */
    long countByUserAndEditTypeAndEditDate(User user, ProfileEditEvent.EditType editType, LocalDate editDate);

    /**
     * Find users with high edit frequency today
     */
    @Query("SELECT DISTINCT e.user FROM ProfileEditEvent e " +
           "WHERE e.editDate = :today " +
           "GROUP BY e.user " +
           "HAVING COUNT(e) >= :threshold")
    List<User> findUsersWithHighEditFrequency(@Param("today") LocalDate today, @Param("threshold") long threshold);
}

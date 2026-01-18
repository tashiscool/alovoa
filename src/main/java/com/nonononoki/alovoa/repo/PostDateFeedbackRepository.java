package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.VideoDate;
import com.nonononoki.alovoa.entity.user.PostDateFeedback;
import com.nonononoki.alovoa.entity.user.RealWorldDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostDateFeedbackRepository extends JpaRepository<PostDateFeedback, Long> {

    Optional<PostDateFeedback> findByUuid(UUID uuid);

    List<PostDateFeedback> findByFromUserOrderBySubmittedAtDesc(User user);

    List<PostDateFeedback> findByAboutUserOrderBySubmittedAtDesc(User user);

    Optional<PostDateFeedback> findByVideoDateAndFromUser(VideoDate videoDate, User fromUser);

    Optional<PostDateFeedback> findByRealWorldDateAndFromUser(RealWorldDate realWorldDate, User fromUser);

    @Query("SELECT AVG(f.overallRating) FROM PostDateFeedback f WHERE f.aboutUser = :user")
    Double getAverageRatingForUser(User user);

    @Query("SELECT COUNT(f) FROM PostDateFeedback f WHERE f.aboutUser = :user AND f.feltSafe = false")
    long countSafetyConcernsAboutUser(User user);

    @Query("SELECT COUNT(f) FROM PostDateFeedback f WHERE f.aboutUser = :user AND f.wouldSeeAgain = true")
    long countPositiveOutcomesForUser(User user);

    long countBySubmittedAtBetween(Date start, Date end);

    @Query("SELECT COUNT(f) FROM PostDateFeedback f WHERE f.planningSecondDate = true AND f.submittedAt BETWEEN :start AND :end")
    long countSecondDatesPlannedBetween(Date start, Date end);
}

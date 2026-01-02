package com.nonononoki.alovoa.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.InferredDealbreaker;

@Repository
public interface InferredDealbreakerRepository extends JpaRepository<InferredDealbreaker, Long> {

    List<InferredDealbreaker> findByUser(User user);

    List<InferredDealbreaker> findByUserOrderByConfidenceDesc(User user);

    @Query("SELECT d FROM InferredDealbreaker d WHERE d.user = :user AND d.confirmed = true")
    List<InferredDealbreaker> findConfirmedByUser(@Param("user") User user);

    @Query("SELECT d FROM InferredDealbreaker d WHERE d.user = :user AND d.confirmed = false AND d.rejected = false")
    List<InferredDealbreaker> findUnreviewedByUser(@Param("user") User user);

    @Query("SELECT d FROM InferredDealbreaker d WHERE d.user = :user AND d.confidence >= :threshold ORDER BY d.confidence DESC")
    List<InferredDealbreaker> findHighConfidenceByUser(@Param("user") User user, @Param("threshold") Double threshold);

    @Query("SELECT COUNT(d) FROM InferredDealbreaker d WHERE d.user = :user AND d.confirmed = false AND d.rejected = false")
    Integer countUnreviewedByUser(@Param("user") User user);

    void deleteByUser(User user);
}

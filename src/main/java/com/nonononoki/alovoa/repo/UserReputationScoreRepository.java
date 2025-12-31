package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserReputationScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserReputationScoreRepository extends JpaRepository<UserReputationScore, Long> {
    Optional<UserReputationScore> findByUser(User user);
}

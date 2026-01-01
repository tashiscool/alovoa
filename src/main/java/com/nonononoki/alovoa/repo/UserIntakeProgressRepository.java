package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserIntakeProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIntakeProgressRepository extends JpaRepository<UserIntakeProgress, Long> {

    Optional<UserIntakeProgress> findByUser(User user);

    Optional<UserIntakeProgress> findByUserId(Long userId);

    boolean existsByUserAndIntakeCompleteTrue(User user);

    long countByIntakeCompleteTrue();
}

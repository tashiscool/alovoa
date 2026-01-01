package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserVideoIntroductionRepository extends JpaRepository<UserVideoIntroduction, Long> {

    Optional<UserVideoIntroduction> findByUser(User user);

    Optional<UserVideoIntroduction> findByUserId(Long userId);

    Optional<UserVideoIntroduction> findByUuid(UUID uuid);

    boolean existsByUser(User user);

    long countByStatus(UserVideoIntroduction.AnalysisStatus status);
}

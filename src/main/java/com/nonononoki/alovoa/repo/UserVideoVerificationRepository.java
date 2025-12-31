package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideoVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserVideoVerificationRepository extends JpaRepository<UserVideoVerification, Long> {
    UserVideoVerification findByUuid(UUID uuid);
    Optional<UserVideoVerification> findByUser(User user);
    Optional<UserVideoVerification> findBySessionId(String sessionId);
    List<UserVideoVerification> findByStatusAndCreatedAtBefore(
            UserVideoVerification.VerificationStatus status, Date createdBefore);
}

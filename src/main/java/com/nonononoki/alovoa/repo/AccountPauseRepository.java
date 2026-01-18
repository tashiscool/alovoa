package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.AccountPause;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountPauseRepository extends JpaRepository<AccountPause, Long> {

    Optional<AccountPause> findByUuid(UUID uuid);

    Optional<AccountPause> findByUser(User user);

    Optional<AccountPause> findByUserAndResumedAtIsNull(User user);

    boolean existsByUserAndResumedAtIsNull(User user);

    @Query("SELECT p FROM AccountPause p WHERE p.resumedAt IS NULL AND p.pauseUntil IS NOT NULL AND p.pauseUntil < :now")
    List<AccountPause> findExpiredPauses(Date now);

    long countByPauseTypeAndPausedAtBetween(AccountPause.PauseType type, Date start, Date end);
}

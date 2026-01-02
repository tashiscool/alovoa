package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {
    Optional<WaitlistEntry> findByEmail(String email);
    Optional<WaitlistEntry> findByUuid(UUID uuid);
    Optional<WaitlistEntry> findByInviteCode(String inviteCode);
    boolean existsByEmail(String email);
    long countByStatus(WaitlistEntry.Status status);
}

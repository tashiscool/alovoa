package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserPersonalityProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPersonalityProfileRepository extends JpaRepository<UserPersonalityProfile, Long> {
    Optional<UserPersonalityProfile> findByUser(User user);
}

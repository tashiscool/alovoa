package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserCalendarSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCalendarSettingsRepository extends JpaRepository<UserCalendarSettings, Long> {

    Optional<UserCalendarSettings> findByUser(User user);

    boolean existsByUser(User user);
}

package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserLocationPreferencesRepository extends JpaRepository<UserLocationPreferences, Long> {

    Optional<UserLocationPreferences> findByUser(User user);

    void deleteByUser(User user);
}

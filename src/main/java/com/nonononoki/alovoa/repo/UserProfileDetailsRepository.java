package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserProfileDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileDetailsRepository extends JpaRepository<UserProfileDetails, Long> {

    Optional<UserProfileDetails> findByUser(User user);

    boolean existsByUser(User user);
}

package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserDailyMatchLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface UserDailyMatchLimitRepository extends JpaRepository<UserDailyMatchLimit, Long> {
    Optional<UserDailyMatchLimit> findByUserAndMatchDate(User user, Date matchDate);
    List<UserDailyMatchLimit> findByMatchDateBefore(Date date);
}

package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserVideo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserVideoRepository extends JpaRepository<UserVideo, Long> {
    UserVideo findByUuid(UUID uuid);
    List<UserVideo> findByUser(User user);
    List<UserVideo> findByUserAndVideoType(User user, UserVideo.VideoType videoType);
    UserVideo findByUserAndIsIntroTrue(User user);
}

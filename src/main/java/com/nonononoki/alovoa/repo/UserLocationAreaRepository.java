package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserLocationArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserLocationAreaRepository extends JpaRepository<UserLocationArea, Long> {

    List<UserLocationArea> findByUserOrderByDisplayOrderAsc(User user);

    Optional<UserLocationArea> findByUserAndAreaType(User user, UserLocationArea.AreaType areaType);

    long countByUser(User user);

    void deleteByUser(User user);

    // Find users with overlapping areas (for matching)
    @Query("SELECT DISTINCT la.user FROM UserLocationArea la " +
           "WHERE la.city = :city AND la.state = :state " +
           "AND la.user.id != :excludeUserId")
    List<User> findUsersInArea(@Param("city") String city,
                               @Param("state") String state,
                               @Param("excludeUserId") Long excludeUserId);

    // Find users in neighborhood (more specific matching)
    @Query("SELECT DISTINCT la.user FROM UserLocationArea la " +
           "WHERE la.neighborhood = :neighborhood AND la.city = :city " +
           "AND la.user.id != :excludeUserId")
    List<User> findUsersInNeighborhood(@Param("neighborhood") String neighborhood,
                                       @Param("city") String city,
                                       @Param("excludeUserId") Long excludeUserId);

    // Find overlapping areas between two users
    @Query("SELECT la1.city FROM UserLocationArea la1, UserLocationArea la2 " +
           "WHERE la1.user.id = :userAId AND la2.user.id = :userBId " +
           "AND la1.city = la2.city AND la1.state = la2.state")
    List<String> findOverlappingCities(@Param("userAId") Long userAId,
                                       @Param("userBId") Long userBId);

    // Check if two users have any overlapping areas
    @Query("SELECT COUNT(la1) > 0 FROM UserLocationArea la1, UserLocationArea la2 " +
           "WHERE la1.user.id = :userAId AND la2.user.id = :userBId " +
           "AND la1.city = la2.city AND la1.state = la2.state")
    boolean hasOverlappingAreas(@Param("userAId") Long userAId, @Param("userBId") Long userBId);
}

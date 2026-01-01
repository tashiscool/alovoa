package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserTravelingMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface UserTravelingModeRepository extends JpaRepository<UserTravelingMode, Long> {

    Optional<UserTravelingMode> findByUser(User user);

    void deleteByUser(User user);

    // Find users currently traveling to a destination
    @Query("SELECT tm FROM UserTravelingMode tm " +
           "WHERE tm.active = true " +
           "AND tm.destinationCity = :city " +
           "AND tm.destinationState = :state " +
           "AND tm.arrivingDate <= :now " +
           "AND tm.leavingDate >= :now " +
           "AND tm.showMeThere = true")
    List<UserTravelingMode> findActiveTravelersToCity(@Param("city") String city,
                                                      @Param("state") String state,
                                                      @Param("now") Date now);

    // Find upcoming travelers (for "visiting soon" feature)
    @Query("SELECT tm FROM UserTravelingMode tm " +
           "WHERE tm.active = true " +
           "AND tm.destinationCity = :city " +
           "AND tm.destinationState = :state " +
           "AND tm.arrivingDate > :now " +
           "AND tm.showMeThere = true")
    List<UserTravelingMode> findUpcomingTravelersToCity(@Param("city") String city,
                                                        @Param("state") String state,
                                                        @Param("now") Date now);

    // Find expired trips for auto-disable
    @Query("SELECT tm FROM UserTravelingMode tm " +
           "WHERE tm.active = true " +
           "AND tm.autoDisable = true " +
           "AND tm.leavingDate < :now")
    List<UserTravelingMode> findExpiredTrips(@Param("now") Date now);
}

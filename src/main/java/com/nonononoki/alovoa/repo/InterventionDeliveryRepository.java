package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.InterventionDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterventionDeliveryRepository extends JpaRepository<InterventionDelivery, Long> {

    Optional<InterventionDelivery> findByUuid(UUID uuid);

    List<InterventionDelivery> findByUserOrderByDeliveredAtDesc(User user);

    List<InterventionDelivery> findByUserAndReadAtIsNullOrderByDeliveredAtDesc(User user);

    boolean existsByUserAndMessageTypeAndDeliveredAtAfter(
            User user, InterventionDelivery.MessageType messageType, Date after);

    long countByUserAndInterventionTier(User user, Integer tier);

    long countByInterventionTierAndDeliveredAtBetween(Integer tier, Date start, Date end);

    long countByResourcesClickedTrueAndDeliveredAtBetween(Date start, Date end);
}

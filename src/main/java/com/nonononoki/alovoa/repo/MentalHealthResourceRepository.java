package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.MentalHealthResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentalHealthResourceRepository extends JpaRepository<MentalHealthResource, Long> {

    List<MentalHealthResource> findByActiveTrueOrderByPriorityDesc();

    List<MentalHealthResource> findByCountryCodeAndActiveTrueOrderByPriorityDesc(String countryCode);

    List<MentalHealthResource> findByResourceTypeAndActiveTrueOrderByPriorityDesc(MentalHealthResource.ResourceType type);

    List<MentalHealthResource> findByCountryCodeOrCountryCodeIsNullAndActiveTrueOrderByPriorityDesc(String countryCode);

    List<MentalHealthResource> findByAvailable247TrueAndActiveTrueOrderByPriorityDesc();
}

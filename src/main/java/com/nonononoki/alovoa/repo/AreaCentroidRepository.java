package com.nonononoki.alovoa.repo;

import com.nonononoki.alovoa.entity.AreaCentroid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AreaCentroidRepository extends JpaRepository<AreaCentroid, Long> {

    // Find by city and state
    Optional<AreaCentroid> findByCityAndState(String city, String state);

    // Find by neighborhood, city, and state
    Optional<AreaCentroid> findByNeighborhoodAndCityAndState(String neighborhood, String city, String state);

    // Find all centroids in a metro area
    List<AreaCentroid> findByMetroArea(String metroArea);

    // Find all centroids in a state
    List<AreaCentroid> findByState(String state);

    // Find centroid, preferring neighborhood-level if available
    @Query("SELECT c FROM AreaCentroid c WHERE " +
           "(c.neighborhood = :neighborhood AND c.city = :city AND c.state = :state) OR " +
           "(c.neighborhood IS NULL AND c.city = :city AND c.state = :state) " +
           "ORDER BY c.neighborhood DESC NULLS LAST")
    List<AreaCentroid> findBestMatchCentroid(@Param("neighborhood") String neighborhood,
                                              @Param("city") String city,
                                              @Param("state") String state);

    // Find all neighborhoods in a city
    @Query("SELECT c FROM AreaCentroid c WHERE c.city = :city AND c.state = :state AND c.neighborhood IS NOT NULL")
    List<AreaCentroid> findNeighborhoodsInCity(@Param("city") String city, @Param("state") String state);
}

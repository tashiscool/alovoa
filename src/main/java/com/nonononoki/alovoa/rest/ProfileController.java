package com.nonononoki.alovoa.rest;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserProfileDetails;
import com.nonononoki.alovoa.entity.user.UserProfileVisit;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.service.AuthService;
import com.nonononoki.alovoa.service.ProfileDetailsService;
import com.nonononoki.alovoa.service.ProfileVisitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for profile features (OKCupid 2016 parity).
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private ProfileVisitService visitService;

    @Autowired
    private ProfileDetailsService detailsService;

    @Autowired
    private AuthService authService;

    // ============================================
    // Profile Visitors
    // ============================================

    /**
     * Get visitors to the current user's profile.
     */
    @GetMapping("/visitors")
    public ResponseEntity<Map<String, Object>> getVisitors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws AlovoaException {

        Page<UserProfileVisit> visitors = visitService.getMyVisitors(page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("visitors", visitors.getContent().stream()
                .map(this::mapVisitToDto)
                .collect(Collectors.toList()));
        response.put("totalCount", visitors.getTotalElements());
        response.put("totalPages", visitors.getTotalPages());
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    /**
     * Get recent visitors (last 7 days by default).
     */
    @GetMapping("/visitors/recent")
    public ResponseEntity<Map<String, Object>> getRecentVisitors(
            @RequestParam(defaultValue = "7") int days) throws AlovoaException {

        List<UserProfileVisit> visitors = visitService.getRecentVisitors(days);
        long totalCount = visitService.getTotalVisitorCount();
        long recentCount = visitService.getRecentVisitorCount(days);

        Map<String, Object> response = new HashMap<>();
        response.put("visitors", visitors.stream()
                .map(this::mapVisitToDto)
                .collect(Collectors.toList()));
        response.put("recentCount", recentCount);
        response.put("totalCount", totalCount);
        response.put("days", days);

        return ResponseEntity.ok(response);
    }

    /**
     * Get profiles the current user has visited.
     */
    @GetMapping("/visited")
    public ResponseEntity<Map<String, Object>> getVisitedProfiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws AlovoaException {

        Page<UserProfileVisit> visited = visitService.getMyVisitedProfiles(page, size);

        Map<String, Object> response = new HashMap<>();
        response.put("profiles", visited.getContent().stream()
                .map(this::mapVisitedProfileToDto)
                .collect(Collectors.toList()));
        response.put("totalCount", visited.getTotalElements());
        response.put("totalPages", visited.getTotalPages());
        response.put("currentPage", page);

        return ResponseEntity.ok(response);
    }

    // ============================================
    // Profile Details
    // ============================================

    /**
     * Get current user's profile details.
     */
    @GetMapping("/details")
    public ResponseEntity<Map<String, Object>> getMyDetails() throws AlovoaException {
        UserProfileDetails details = detailsService.getMyDetails();
        return ResponseEntity.ok(mapDetailsToDto(details));
    }

    /**
     * Update profile details.
     */
    @PostMapping("/details")
    public ResponseEntity<Map<String, Object>> updateDetails(@RequestBody Map<String, Object> request) throws AlovoaException {
        Integer heightCm = request.containsKey("heightCm") ?
                ((Number) request.get("heightCm")).intValue() : null;

        UserProfileDetails.BodyType bodyType = request.containsKey("bodyType") ?
                UserProfileDetails.BodyType.valueOf((String) request.get("bodyType")) : null;

        UserProfileDetails.Ethnicity ethnicity = request.containsKey("ethnicity") ?
                UserProfileDetails.Ethnicity.valueOf((String) request.get("ethnicity")) : null;

        UserProfileDetails.Diet diet = request.containsKey("diet") ?
                UserProfileDetails.Diet.valueOf((String) request.get("diet")) : null;

        UserProfileDetails.PetStatus pets = request.containsKey("pets") ?
                UserProfileDetails.PetStatus.valueOf((String) request.get("pets")) : null;

        String petDetails = (String) request.get("petDetails");

        UserProfileDetails.EducationLevel education = request.containsKey("education") ?
                UserProfileDetails.EducationLevel.valueOf((String) request.get("education")) : null;

        String occupation = (String) request.get("occupation");
        String employer = (String) request.get("employer");
        String languages = (String) request.get("languages");

        UserProfileDetails.ZodiacSign zodiacSign = request.containsKey("zodiacSign") && request.get("zodiacSign") != null ?
                UserProfileDetails.ZodiacSign.valueOf((String) request.get("zodiacSign")) : null;

        UserProfileDetails.IncomeLevel income = request.containsKey("income") && request.get("income") != null ?
                UserProfileDetails.IncomeLevel.valueOf((String) request.get("income")) : null;

        UserProfileDetails updated = detailsService.updateAllDetails(
                heightCm, bodyType, ethnicity, diet, pets, petDetails,
                education, occupation, employer, languages, zodiacSign);

        // Update income separately since it was added later
        if (income != null) {
            updated = detailsService.updateIncome(income);
        }

        return ResponseEntity.ok(mapDetailsToDto(updated));
    }

    /**
     * Update height in imperial (feet/inches).
     */
    @PostMapping("/details/height-imperial")
    public ResponseEntity<Map<String, Object>> updateHeightImperial(@RequestBody Map<String, Integer> request) throws AlovoaException {
        int feet = request.get("feet");
        int inches = request.get("inches");
        UserProfileDetails updated = detailsService.updateHeightImperial(feet, inches);
        return ResponseEntity.ok(mapDetailsToDto(updated));
    }

    /**
     * Get available options for profile detail fields.
     */
    @GetMapping("/details/options")
    public ResponseEntity<Map<String, Object>> getDetailOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("bodyTypes", UserProfileDetails.BodyType.values());
        options.put("ethnicities", UserProfileDetails.Ethnicity.values());
        options.put("diets", UserProfileDetails.Diet.values());
        options.put("petStatuses", UserProfileDetails.PetStatus.values());
        options.put("educationLevels", UserProfileDetails.EducationLevel.values());
        options.put("zodiacSigns", UserProfileDetails.ZodiacSign.values());
        options.put("incomeLevels", UserProfileDetails.IncomeLevel.values());
        options.put("responseRates", UserProfileDetails.ResponseRate.values());
        return ResponseEntity.ok(options);
    }

    // ============================================
    // Helper methods
    // ============================================

    private Map<String, Object> mapVisitToDto(UserProfileVisit visit) {
        Map<String, Object> dto = new HashMap<>();
        User visitor = visit.getVisitor();
        dto.put("visitorId", visitor.getUuid());
        dto.put("visitorName", visitor.getFirstName());
        dto.put("visitedAt", visit.getVisitedAt());
        dto.put("lastVisitAt", visit.getLastVisitAt());
        dto.put("visitCount", visit.getVisitCount());
        // Include profile picture if available
        if (visitor.getProfilePicture() != null) {
            dto.put("hasProfilePicture", true);
        }
        return dto;
    }

    private Map<String, Object> mapVisitedProfileToDto(UserProfileVisit visit) {
        Map<String, Object> dto = new HashMap<>();
        User visited = visit.getVisitedUser();
        dto.put("userId", visited.getUuid());
        dto.put("userName", visited.getFirstName());
        dto.put("visitedAt", visit.getVisitedAt());
        dto.put("lastVisitAt", visit.getLastVisitAt());
        dto.put("visitCount", visit.getVisitCount());
        if (visited.getProfilePicture() != null) {
            dto.put("hasProfilePicture", true);
        }
        return dto;
    }

    private Map<String, Object> mapDetailsToDto(UserProfileDetails details) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("heightCm", details.getHeightCm());
        dto.put("heightImperial", details.getHeightImperial());
        dto.put("bodyType", details.getBodyType());
        dto.put("ethnicity", details.getEthnicity());
        dto.put("diet", details.getDiet());
        dto.put("pets", details.getPets());
        dto.put("petDetails", details.getPetDetails());
        dto.put("education", details.getEducation());
        dto.put("occupation", details.getOccupation());
        dto.put("employer", details.getEmployer());
        dto.put("languages", details.getLanguages());
        dto.put("zodiacSign", details.getZodiacSign());
        dto.put("income", details.getIncome());
        dto.put("responseRate", details.getResponseRate());
        return dto;
    }
}

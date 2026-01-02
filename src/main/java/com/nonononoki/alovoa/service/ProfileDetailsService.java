package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.user.UserProfileDetails;
import com.nonononoki.alovoa.model.AlovoaException;
import com.nonononoki.alovoa.repo.UserProfileDetailsRepository;
import com.nonononoki.alovoa.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for managing extended profile details (OKCupid 2016 feature parity).
 */
@Service
public class ProfileDetailsService {

    @Autowired
    private UserProfileDetailsRepository detailsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    /**
     * Get profile details for the current user.
     */
    public UserProfileDetails getMyDetails() throws AlovoaException {
        User currentUser = authService.getCurrentUser(true);
        return getOrCreateDetails(currentUser);
    }

    /**
     * Get profile details for a specific user.
     */
    public Optional<UserProfileDetails> getDetailsForUser(User user) {
        return detailsRepository.findByUser(user);
    }

    /**
     * Get or create profile details for a user.
     */
    @Transactional
    public UserProfileDetails getOrCreateDetails(User user) {
        Optional<UserProfileDetails> existing = detailsRepository.findByUser(user);
        if (existing.isPresent()) {
            return existing.get();
        }

        UserProfileDetails details = new UserProfileDetails();
        details.setUser(user);
        return detailsRepository.save(details);
    }

    /**
     * Update height (in cm).
     */
    @Transactional
    public UserProfileDetails updateHeight(Integer heightCm) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setHeightCm(heightCm);
        return detailsRepository.save(details);
    }

    /**
     * Update height from feet/inches.
     */
    @Transactional
    public UserProfileDetails updateHeightImperial(int feet, int inches) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setHeightFromImperial(feet, inches);
        return detailsRepository.save(details);
    }

    /**
     * Update body type.
     */
    @Transactional
    public UserProfileDetails updateBodyType(UserProfileDetails.BodyType bodyType) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setBodyType(bodyType);
        return detailsRepository.save(details);
    }

    /**
     * Update ethnicity.
     */
    @Transactional
    public UserProfileDetails updateEthnicity(UserProfileDetails.Ethnicity ethnicity) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setEthnicity(ethnicity);
        return detailsRepository.save(details);
    }

    /**
     * Update diet.
     */
    @Transactional
    public UserProfileDetails updateDiet(UserProfileDetails.Diet diet) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setDiet(diet);
        return detailsRepository.save(details);
    }

    /**
     * Update pet status.
     */
    @Transactional
    public UserProfileDetails updatePets(UserProfileDetails.PetStatus pets, String petDetails) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setPets(pets);
        details.setPetDetails(petDetails);
        return detailsRepository.save(details);
    }

    /**
     * Update education level.
     */
    @Transactional
    public UserProfileDetails updateEducation(UserProfileDetails.EducationLevel education) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setEducation(education);
        return detailsRepository.save(details);
    }

    /**
     * Update occupation and employer.
     */
    @Transactional
    public UserProfileDetails updateOccupation(String occupation, String employer) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setOccupation(occupation);
        details.setEmployer(employer);
        return detailsRepository.save(details);
    }

    /**
     * Update languages (comma-separated).
     */
    @Transactional
    public UserProfileDetails updateLanguages(String languages) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setLanguages(languages);
        return detailsRepository.save(details);
    }

    /**
     * Update zodiac sign.
     */
    @Transactional
    public UserProfileDetails updateZodiacSign(UserProfileDetails.ZodiacSign zodiacSign) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setZodiacSign(zodiacSign);
        return detailsRepository.save(details);
    }

    /**
     * Bulk update all profile details at once.
     */
    @Transactional
    public UserProfileDetails updateAllDetails(
            Integer heightCm,
            UserProfileDetails.BodyType bodyType,
            UserProfileDetails.Ethnicity ethnicity,
            UserProfileDetails.Diet diet,
            UserProfileDetails.PetStatus pets,
            String petDetails,
            UserProfileDetails.EducationLevel education,
            String occupation,
            String employer,
            String languages,
            UserProfileDetails.ZodiacSign zodiacSign) throws AlovoaException {

        UserProfileDetails details = getMyDetails();
        if (heightCm != null) details.setHeightCm(heightCm);
        if (bodyType != null) details.setBodyType(bodyType);
        if (ethnicity != null) details.setEthnicity(ethnicity);
        if (diet != null) details.setDiet(diet);
        if (pets != null) details.setPets(pets);
        if (petDetails != null) details.setPetDetails(petDetails);
        if (education != null) details.setEducation(education);
        if (occupation != null) details.setOccupation(occupation);
        if (employer != null) details.setEmployer(employer);
        if (languages != null) details.setLanguages(languages);
        if (zodiacSign != null) details.setZodiacSign(zodiacSign);

        return detailsRepository.save(details);
    }

    /**
     * Update income level.
     */
    @Transactional
    public UserProfileDetails updateIncome(UserProfileDetails.IncomeLevel income) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setIncome(income);
        return detailsRepository.save(details);
    }

    /**
     * Get or create profile details for current user (convenience method for tests).
     */
    @Transactional
    public UserProfileDetails getOrCreateProfileDetails() throws AlovoaException {
        return getMyDetails();
    }

    /**
     * Update whether user has pets.
     */
    @Transactional
    public UserProfileDetails updateHasPets(boolean hasPets) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setPets(hasPets ? UserProfileDetails.PetStatus.HAS_PETS : UserProfileDetails.PetStatus.NO_PETS_LIKES_THEM);
        return detailsRepository.save(details);
    }

    /**
     * Update pet details description.
     */
    @Transactional
    public UserProfileDetails updatePetDetails(String petDetails) throws AlovoaException {
        UserProfileDetails details = getMyDetails();
        details.setPetDetails(petDetails);
        return detailsRepository.save(details);
    }

    /**
     * Calculate and update response rate based on messaging behavior.
     */
    @Transactional
    public void updateResponseRate(User user, double responsePercentage) {
        UserProfileDetails details = getOrCreateDetails(user);

        UserProfileDetails.ResponseRate rate;
        if (responsePercentage >= 80) {
            rate = UserProfileDetails.ResponseRate.REPLIES_OFTEN;
        } else if (responsePercentage >= 40) {
            rate = UserProfileDetails.ResponseRate.REPLIES_SELECTIVELY;
        } else {
            rate = UserProfileDetails.ResponseRate.REPLIES_VERY_SELECTIVELY;
        }

        details.setResponseRate(rate);
        detailsRepository.save(details);
    }

    /**
     * Get all available options for profile details dropdowns.
     */
    public ProfileDetailOptions getDetailOptions() {
        return ProfileDetailOptions.builder()
                .bodyTypes(UserProfileDetails.BodyType.values())
                .ethnicities(UserProfileDetails.Ethnicity.values())
                .diets(UserProfileDetails.Diet.values())
                .petStatuses(UserProfileDetails.PetStatus.values())
                .educationLevels(UserProfileDetails.EducationLevel.values())
                .zodiacSigns(UserProfileDetails.ZodiacSign.values())
                .incomeLevels(UserProfileDetails.IncomeLevel.values())
                .build();
    }

    /**
     * Bulk update all profile details from a DTO.
     */
    @Transactional
    public UserProfileDetails saveAllDetails(ProfileDetailsDto dto) throws AlovoaException {
        UserProfileDetails details = getMyDetails();

        if (dto.getHeightCm() != null) details.setHeightCm(dto.getHeightCm());
        if (dto.getBodyType() != null) {
            try { details.setBodyType(UserProfileDetails.BodyType.valueOf(dto.getBodyType())); } catch (Exception ignored) {}
        }
        if (dto.getEthnicity() != null) {
            try { details.setEthnicity(UserProfileDetails.Ethnicity.valueOf(dto.getEthnicity())); } catch (Exception ignored) {}
        }
        if (dto.getDiet() != null) {
            try { details.setDiet(UserProfileDetails.Diet.valueOf(dto.getDiet())); } catch (Exception ignored) {}
        }
        if (dto.getPets() != null) {
            try { details.setPets(UserProfileDetails.PetStatus.valueOf(dto.getPets())); } catch (Exception ignored) {}
        }
        if (dto.getEducation() != null) {
            try { details.setEducation(UserProfileDetails.EducationLevel.valueOf(dto.getEducation())); } catch (Exception ignored) {}
        }
        if (dto.getZodiacSign() != null) {
            try { details.setZodiacSign(UserProfileDetails.ZodiacSign.valueOf(dto.getZodiacSign())); } catch (Exception ignored) {}
        }
        if (dto.getIncome() != null) {
            try { details.setIncome(UserProfileDetails.IncomeLevel.valueOf(dto.getIncome())); } catch (Exception ignored) {}
        }
        if (dto.getOccupation() != null) details.setOccupation(dto.getOccupation());
        if (dto.getEmployer() != null) details.setEmployer(dto.getEmployer());
        if (dto.getLanguages() != null) details.setLanguages(dto.getLanguages());

        return detailsRepository.save(details);
    }

    @lombok.Builder
    @lombok.Getter
    public static class ProfileDetailOptions {
        private UserProfileDetails.BodyType[] bodyTypes;
        private UserProfileDetails.Ethnicity[] ethnicities;
        private UserProfileDetails.Diet[] diets;
        private UserProfileDetails.PetStatus[] petStatuses;
        private UserProfileDetails.EducationLevel[] educationLevels;
        private UserProfileDetails.ZodiacSign[] zodiacSigns;
        private UserProfileDetails.IncomeLevel[] incomeLevels;
    }

    @lombok.Data
    public static class ProfileDetailsDto {
        private Integer heightCm;
        private String bodyType;
        private String ethnicity;
        private String diet;
        private String pets;
        private String education;
        private String zodiacSign;
        private String income;
        private String occupation;
        private String employer;
        private String languages;
    }
}

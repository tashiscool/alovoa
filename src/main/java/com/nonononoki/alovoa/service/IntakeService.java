package com.nonononoki.alovoa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonononoki.alovoa.entity.AssessmentQuestion;
import com.nonononoki.alovoa.entity.User;
import com.nonononoki.alovoa.entity.UserAssessmentResponse;
import com.nonononoki.alovoa.entity.user.UserAudio;
import com.nonononoki.alovoa.entity.user.UserImage;
import com.nonononoki.alovoa.entity.user.UserIntakeProgress;
import com.nonononoki.alovoa.entity.user.UserVideoIntroduction;
import com.nonononoki.alovoa.model.AssessmentResponseDto;
import com.nonononoki.alovoa.model.IntakeProgressDto;
import com.nonononoki.alovoa.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IntakeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntakeService.class);

    // 10 categories for core questions
    private static final List<String> CORE_CATEGORIES = List.of(
            "dealbreakers_safety",
            "values_politics",
            "relationship_dynamics",
            "attachment_emotional",
            "lifestyle_compatibility",
            "family_future",
            "sex_intimacy",
            "personality_temperament",
            "hypotheticals_scenarios",
            "location_specific"
    );

    @Value("${app.aura.intake.min-pictures:1}")
    private int minPictures;

    @Value("${app.aura.intake.max-video-size-mb:50}")
    private int maxVideoSizeMb;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private VideoAnalysisService videoAnalysisService;

    @Autowired
    private UserIntakeProgressRepository intakeProgressRepo;

    @Autowired
    private UserVideoIntroductionRepository videoIntroRepo;

    @Autowired
    private AssessmentQuestionRepository questionRepo;

    @Autowired
    private UserAssessmentResponseRepository responseRepo;

    @Autowired
    private UserImageRepository imageRepo;

    @Autowired
    private UserAudioRepository audioRepo;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Get the current user's intake progress
     */
    @Transactional(readOnly = true)
    public IntakeProgressDto getIntakeProgress() throws Exception {
        User user = authService.getCurrentUser(true);
        UserIntakeProgress progress = getOrCreateProgress(user);

        // Count answered core questions
        int questionsAnswered = countCoreQuestionsAnswered(user);
        boolean questionsComplete = questionsAnswered >= CORE_CATEGORIES.size();

        // Check video
        Optional<UserVideoIntroduction> videoOpt = videoIntroRepo.findByUser(user);
        boolean videoComplete = videoOpt.isPresent();
        boolean videoAnalyzed = videoOpt.map(UserVideoIntroduction::isAnalysisComplete).orElse(false);

        // Check pictures
        int pictureCount = user.getImages() != null ? user.getImages().size() : 0;
        boolean picturesComplete = pictureCount >= minPictures || user.getProfilePicture() != null;

        // Check audio (optional)
        boolean audioComplete = user.getAudio() != null;

        // Update progress entity
        progress.setQuestionsComplete(questionsComplete);
        progress.setVideoIntroComplete(videoComplete);
        progress.setAudioIntroComplete(audioComplete);
        progress.setPicturesComplete(picturesComplete);
        progress.updateIntakeComplete();
        intakeProgressRepo.save(progress);

        return IntakeProgressDto.builder()
                .questionsComplete(questionsComplete)
                .questionsAnswered(questionsAnswered)
                .videoIntroComplete(videoComplete)
                .videoAnalyzed(videoAnalyzed)
                .audioIntroComplete(audioComplete)
                .picturesComplete(picturesComplete)
                .picturesCount(pictureCount)
                .intakeComplete(progress.getIntakeComplete())
                .completionPercentage(IntakeProgressDto.calculatePercentage(questionsComplete, videoComplete, picturesComplete))
                .startedAt(progress.getStartedAt())
                .completedAt(progress.getCompletedAt())
                .nextStep(IntakeProgressDto.determineNextStep(questionsComplete, videoComplete, picturesComplete))
                .build();
    }

    /**
     * Get the 10 core questions (1 from each category)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCoreQuestions() throws Exception {
        User user = authService.getCurrentUser(true);

        List<Map<String, Object>> coreQuestions = new ArrayList<>();

        for (String category : CORE_CATEGORIES) {
            // Find core question for this category (stored in subcategory field)
            Optional<AssessmentQuestion> coreQuestion = questionRepo.findBySubcategoryAndCoreQuestionTrue(category);

            if (coreQuestion.isEmpty()) {
                // Fallback: get first question in category
                List<AssessmentQuestion> categoryQuestions = questionRepo.findBySubcategory(category);
                if (!categoryQuestions.isEmpty()) {
                    coreQuestion = Optional.of(categoryQuestions.get(0));
                }
            }

            if (coreQuestion.isPresent()) {
                AssessmentQuestion q = coreQuestion.get();

                // Check if already answered
                Optional<UserAssessmentResponse> existingResponse =
                        responseRepo.findByUserAndQuestion(user, q);

                Map<String, Object> questionData = new LinkedHashMap<>();
                questionData.put("id", q.getId());
                questionData.put("externalId", q.getExternalId());
                questionData.put("category", q.getCategory());
                questionData.put("text", q.getText());
                questionData.put("responseScale", q.getResponseScale());
                questionData.put("options", parseOptions(q.getOptions()));
                questionData.put("answered", existingResponse.isPresent());
                questionData.put("currentAnswer", existingResponse.map(UserAssessmentResponse::getNumericResponse).orElse(null));

                coreQuestions.add(questionData);
            }
        }

        return coreQuestions;
    }

    /**
     * Submit answers to core questions
     */
    @Transactional
    public Map<String, Object> submitCoreAnswers(List<AssessmentResponseDto> responses) throws Exception {
        User user = authService.getCurrentUser(true);

        int saved = 0;
        for (AssessmentResponseDto dto : responses) {
            Optional<AssessmentQuestion> questionOpt = questionRepo.findByExternalId(dto.getQuestionId());

            if (questionOpt.isPresent()) {
                AssessmentQuestion question = questionOpt.get();

                // Create or update response
                UserAssessmentResponse response = responseRepo.findByUserAndQuestion(user, question)
                        .orElse(new UserAssessmentResponse());

                response.setUser(user);
                response.setQuestion(question);
                response.setCategory(question.getCategory());
                response.setNumericResponse(dto.getNumericResponse());
                response.setTextResponse(dto.getTextResponse());
                response.setUpdatedAt(new Date());

                if (response.getAnsweredAt() == null) {
                    response.setAnsweredAt(new Date());
                }

                responseRepo.save(response);
                saved++;
            }
        }

        // Update progress
        UserIntakeProgress progress = getOrCreateProgress(user);
        int answered = countCoreQuestionsAnswered(user);
        progress.setQuestionsComplete(answered >= CORE_CATEGORIES.size());
        progress.updateIntakeComplete();
        intakeProgressRepo.save(progress);

        return Map.of(
                "saved", saved,
                "totalAnswered", answered,
                "questionsComplete", progress.getQuestionsComplete()
        );
    }

    /**
     * Upload video introduction
     * @param skipAiAnalysis if true, skip AI analysis and allow manual entry
     */
    @Transactional
    public Map<String, Object> uploadVideoIntroduction(MultipartFile video, boolean skipAiAnalysis) throws Exception {
        User user = authService.getCurrentUser(true);

        // Validate video
        if (video.isEmpty()) {
            throw new IllegalArgumentException("Video file is required");
        }

        String contentType = video.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("Invalid video format");
        }

        long maxBytes = maxVideoSizeMb * 1024L * 1024L;
        if (video.getSize() > maxBytes) {
            throw new IllegalArgumentException("Video exceeds maximum size of " + maxVideoSizeMb + "MB");
        }

        // Create or update video introduction
        UserVideoIntroduction videoIntro = videoIntroRepo.findByUser(user)
                .orElse(new UserVideoIntroduction());

        videoIntro.setUser(user);
        videoIntro.setVideoData(video.getBytes());
        videoIntro.setMimeType(contentType);
        videoIntro.setUploadedAt(new Date());
        videoIntro.setManualEntry(skipAiAnalysis);

        if (skipAiAnalysis) {
            // Skip AI analysis - user will enter info manually
            videoIntro.setStatus(UserVideoIntroduction.AnalysisStatus.SKIPPED);
        } else {
            videoIntro.setStatus(UserVideoIntroduction.AnalysisStatus.PENDING);
        }

        videoIntroRepo.save(videoIntro);

        // Only trigger AI analysis if not skipped
        if (!skipAiAnalysis) {
            videoAnalysisService.analyzeVideoAsync(videoIntro);
        }

        // Update progress
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setVideoIntroComplete(true);
        progress.updateIntakeComplete();
        intakeProgressRepo.save(progress);

        String message = skipAiAnalysis
                ? "Video uploaded successfully. Please enter your profile information manually."
                : "Video uploaded successfully. AI analysis in progress.";

        return Map.of(
                "success", true,
                "videoId", videoIntro.getId(),
                "status", videoIntro.getStatus().name(),
                "manualEntry", skipAiAnalysis,
                "message", message
        );
    }

    /**
     * Upload video introduction with default AI analysis
     */
    @Transactional
    public Map<String, Object> uploadVideoIntroduction(MultipartFile video) throws Exception {
        return uploadVideoIntroduction(video, false);
    }

    /**
     * Submit manual profile information (worldview, background, life story)
     */
    @Transactional
    public Map<String, Object> submitManualProfileInfo(Map<String, String> profileInfo) throws Exception {
        User user = authService.getCurrentUser(true);

        Optional<UserVideoIntroduction> videoOpt = videoIntroRepo.findByUser(user);
        if (videoOpt.isEmpty()) {
            throw new IllegalArgumentException("Please upload a video first");
        }

        UserVideoIntroduction video = videoOpt.get();

        // Update profile fields
        if (profileInfo.containsKey("worldview")) {
            video.setWorldviewSummary(profileInfo.get("worldview"));
        }
        if (profileInfo.containsKey("background")) {
            video.setBackgroundSummary(profileInfo.get("background"));
        }
        if (profileInfo.containsKey("lifeStory")) {
            video.setLifeStorySummary(profileInfo.get("lifeStory"));
        }

        // Mark as manual entry
        video.setManualEntry(true);
        video.setAnalyzedAt(new Date());

        videoIntroRepo.save(video);

        return Map.of(
                "success", true,
                "message", "Profile information saved successfully",
                "hasWorldview", video.getWorldviewSummary() != null,
                "hasBackground", video.getBackgroundSummary() != null,
                "hasLifeStory", video.getLifeStorySummary() != null
        );
    }

    /**
     * Skip AI analysis for an already uploaded video
     */
    @Transactional
    public Map<String, Object> skipVideoAnalysis() throws Exception {
        User user = authService.getCurrentUser(true);

        Optional<UserVideoIntroduction> videoOpt = videoIntroRepo.findByUser(user);
        if (videoOpt.isEmpty()) {
            throw new IllegalArgumentException("No video found to skip analysis for");
        }

        UserVideoIntroduction video = videoOpt.get();
        video.setStatus(UserVideoIntroduction.AnalysisStatus.SKIPPED);
        video.setManualEntry(true);
        videoIntroRepo.save(video);

        return Map.of(
                "success", true,
                "message", "AI analysis skipped. Please enter your profile information manually."
        );
    }

    /**
     * Upload audio introduction (optional)
     */
    @Transactional
    public Map<String, Object> uploadAudioIntroduction(MultipartFile audio) throws Exception {
        User user = authService.getCurrentUser(true);

        if (audio.isEmpty()) {
            throw new IllegalArgumentException("Audio file is required");
        }

        String contentType = audio.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            throw new IllegalArgumentException("Invalid audio format");
        }

        // Use existing UserAudio entity pattern from UserService
        userService.updateAudio(user, audio.getBytes(), contentType);

        // Update progress
        UserIntakeProgress progress = getOrCreateProgress(user);
        progress.setAudioIntroComplete(true);
        intakeProgressRepo.save(progress);

        return Map.of(
                "success", true,
                "message", "Audio uploaded successfully"
        );
    }

    /**
     * Check if intake is complete for a user
     */
    @Transactional(readOnly = true)
    public boolean isIntakeComplete(User user) {
        return intakeProgressRepo.existsByUserAndIntakeCompleteTrue(user);
    }

    /**
     * Get or create intake progress for a user
     */
    private UserIntakeProgress getOrCreateProgress(User user) {
        return intakeProgressRepo.findByUser(user)
                .orElseGet(() -> {
                    UserIntakeProgress progress = new UserIntakeProgress();
                    progress.setUser(user);
                    progress.setStartedAt(new Date());
                    return intakeProgressRepo.save(progress);
                });
    }

    /**
     * Count how many core questions the user has answered
     */
    private int countCoreQuestionsAnswered(User user) {
        Set<String> answeredCategories = new HashSet<>();

        for (String category : CORE_CATEGORIES) {
            Optional<AssessmentQuestion> coreQuestion = questionRepo.findBySubcategoryAndCoreQuestionTrue(category);

            if (coreQuestion.isEmpty()) {
                List<AssessmentQuestion> categoryQuestions = questionRepo.findBySubcategory(category);
                if (!categoryQuestions.isEmpty()) {
                    coreQuestion = Optional.of(categoryQuestions.get(0));
                }
            }

            if (coreQuestion.isPresent()) {
                Optional<UserAssessmentResponse> response =
                        responseRepo.findByUserAndQuestion(user, coreQuestion.get());
                if (response.isPresent()) {
                    answeredCategories.add(category);
                }
            }
        }

        return answeredCategories.size();
    }

    /**
     * Parse question options from JSON string
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(optionsJson, List.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse options: {}", optionsJson, e);
            return Collections.emptyList();
        }
    }
}

package com.nonononoki.alovoa.service;

import com.nonononoki.alovoa.entity.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service providing encouraging messages, fun facts, and personalized stats
 * for the intake flow to make it feel warm, inviting, and low-stress.
 */
@Service
public class IntakeEncouragementService {

    private static final Random RANDOM = new Random();

    // Fun facts about relationships and dating
    private static final List<String> RELATIONSHIP_FACTS = List.of(
            "Couples who laugh together report higher relationship satisfaction.",
            "The average person will have 2-7 significant relationships before finding their long-term partner.",
            "73% of successful couples say friendship was the foundation of their relationship.",
            "People who share hobbies with their partner report 36% higher relationship satisfaction.",
            "The best predictor of relationship success? How you handle disagreements, not how often you agree.",
            "Couples who try new activities together release more bonding hormones.",
            "Most people can tell within 3 minutes if there's potential for a connection.",
            "Shared values matter more than shared interests for long-term compatibility.",
            "People who are authentically themselves on dates are 3x more likely to find a compatible match.",
            "The happiest couples maintain their individual identities while building a life together."
    );

    // Pop culture references to make it relatable
    private static final List<String> POP_CULTURE_FACTS = List.of(
            "73% of people watched Game of Thrones. Almost nobody liked the ending. Your opinions matter here too.",
            "Remember when everyone had a MySpace? Being authentic online has come a long way since then.",
            "If dating apps existed in the 90s, Ross and Rachel would've matched instantly. Then unmatched. Then matched again.",
            "The Office taught us that love can bloom in unexpected places - even a paper company in Scranton.",
            "Like Taylor Swift, everyone has a story. What's yours?",
            "Harry met Sally and proved that friendship can turn into something more. No rush here.",
            "Pride and Prejudice taught us that first impressions aren't everything. Neither are dating profiles.",
            "Even Jim took years to tell Pam how he felt. Good things take time."
    );

    // Hobby attractiveness insights from research
    private static final List<String> HOBBY_INSIGHTS = List.of(
            "Fun fact: Reading is rated the #1 most attractive hobby by 98% of people. What's on your bookshelf?",
            "Research shows cooking is attractive to 95% of potential partners. Gordon Ramsay would be proud.",
            "Playing a musical instrument? 95% of people find that attractive. Time to dust off that guitar!",
            "Gardening is rated attractive by 94% of people. Even a single houseplant counts!",
            "Hiking enthusiasts rejoice: 90% of people find outdoor hobbies attractive.",
            "Photography is rated attractive by 90% of people. Your Instagram game might actually matter.",
            "Swimming is the top-rated athletic hobby. Marco Polo skills optional.",
            "Traveling is attractive to 88% of people. Even armchair traveling counts!",
            "Woodworking and crafts are highly attractive - people appreciate those who can create things.",
            "Learning languages? 96% of people find that attractive. Bonjour, compatibility!"
    );

    // Encouraging messages for different intake steps
    private static final Map<String, List<String>> STEP_ENCOURAGEMENTS = Map.of(
            "questions", List.of(
                    "There are no wrong answers here - just your answers.",
                    "Be honest. The right person will appreciate the real you.",
                    "These questions help find people who actually get you.",
                    "No need to overthink it. Go with your gut.",
                    "Your quirks are features, not bugs.",
                    "The goal isn't to be perfect - it's to be you."
            ),
            "video", List.of(
                    "Just be yourself. Seriously. That's the whole point.",
                    "Imagine you're talking to a friend, not a camera.",
                    "Authenticity beats perfection every time.",
                    "Bad lighting won't hide a great personality.",
                    "Your future partner wants to meet YOU, not a polished version.",
                    "First takes are often the best. Don't overthink it."
            ),
            "profile", List.of(
                    "Your story is worth telling.",
                    "What makes you laugh? Start there.",
                    "The best profiles read like conversations, not resumes.",
                    "Your future partner is out there wondering about you too.",
                    "Be specific. 'I love music' is forgettable. 'I cry every time I hear Bohemian Rhapsody' is memorable.",
                    "Vulnerability is attractive. It's okay to be real."
            ),
            "photos", List.of(
                    "Show the real you - not just your best angle.",
                    "A genuine smile beats a perfect pose.",
                    "Include photos that show what you actually do.",
                    "That candid photo your friend took? Probably your best one.",
                    "Your photos should make someone want to hang out with you.",
                    "Pet photos are always a good idea. Always."
            )
    );

    // Completion celebrations
    private static final List<String> COMPLETION_MESSAGES = List.of(
            "You did it! Your future self (and future partner) will thank you.",
            "Profile complete! Now the fun begins.",
            "That wasn't so bad, was it? You're officially ready to meet your people.",
            "Welcome to the community! Authentic people like you make this place great.",
            "Done! Remember: the right person will appreciate everything you just shared.",
            "Profile finished! Time to find someone who gets your weird."
    );

    /**
     * Get personalized life statistics based on user's birthday.
     */
    public Map<String, Object> getLifeStats(User user) {
        Map<String, Object> stats = new LinkedHashMap<>();

        if (user.getDates() != null && user.getDates().getDateOfBirth() != null) {
            LocalDate birthDate = user.getDates().getDateOfBirth()
                    .toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate today = LocalDate.now();

            long daysAlive = ChronoUnit.DAYS.between(birthDate, today);
            long weeksAlive = ChronoUnit.WEEKS.between(birthDate, today);
            long monthsAlive = ChronoUnit.MONTHS.between(birthDate, today);
            int yearsAlive = Period.between(birthDate, today).getYears();

            stats.put("daysAlive", daysAlive);
            stats.put("weeksAlive", weeksAlive);
            stats.put("monthsAlive", monthsAlive);
            stats.put("yearsAlive", yearsAlive);

            // Fun derived stats
            stats.put("approximateHeartbeats", daysAlive * 100_000L);
            stats.put("approximateMealsEaten", daysAlive * 3);
            stats.put("approximateDreamsHad", daysAlive * 5); // avg 4-6 dreams per night
            stats.put("approximateSunrises", daysAlive);

            // Personalized message
            stats.put("personalMessage", String.format(
                    "You've experienced %,d sunrises on this beautiful planet. " +
                    "Someone out there would love to share the next ones with you.",
                    daysAlive
            ));
        }

        return stats;
    }

    /**
     * Get an encouraging message for a specific intake step.
     */
    public String getStepEncouragement(String step) {
        List<String> messages = STEP_ENCOURAGEMENTS.getOrDefault(step, STEP_ENCOURAGEMENTS.get("questions"));
        return messages.get(RANDOM.nextInt(messages.size()));
    }

    /**
     * Get a random relationship fact.
     */
    public String getRelationshipFact() {
        return RELATIONSHIP_FACTS.get(RANDOM.nextInt(RELATIONSHIP_FACTS.size()));
    }

    /**
     * Get a random pop culture reference.
     */
    public String getPopCultureFact() {
        return POP_CULTURE_FACTS.get(RANDOM.nextInt(POP_CULTURE_FACTS.size()));
    }

    /**
     * Get a random hobby insight.
     */
    public String getHobbyInsight() {
        return HOBBY_INSIGHTS.get(RANDOM.nextInt(HOBBY_INSIGHTS.size()));
    }

    /**
     * Get a completion celebration message.
     */
    public String getCompletionMessage() {
        return COMPLETION_MESSAGES.get(RANDOM.nextInt(COMPLETION_MESSAGES.size()));
    }

    /**
     * Get a bundle of encouraging content for the intake UI.
     */
    public Map<String, Object> getIntakeEncouragement(User user, String currentStep) {
        Map<String, Object> encouragement = new LinkedHashMap<>();

        // Life stats if available
        Map<String, Object> lifeStats = getLifeStats(user);
        if (!lifeStats.isEmpty()) {
            encouragement.put("lifeStats", lifeStats);
        }

        // Current step encouragement
        encouragement.put("stepEncouragement", getStepEncouragement(currentStep));

        // Random fun facts (pick 2-3)
        List<String> funFacts = new ArrayList<>();
        funFacts.add(getRelationshipFact());
        funFacts.add(getPopCultureFact());
        if (RANDOM.nextBoolean()) {
            funFacts.add(getHobbyInsight());
        }
        encouragement.put("funFacts", funFacts);

        // Hobby insight
        encouragement.put("hobbyInsight", getHobbyInsight());

        // Progress encouragement based on what's done
        encouragement.put("progressMessage", getProgressMessage(currentStep));

        return encouragement;
    }

    /**
     * Get progress-specific encouragement.
     */
    private String getProgressMessage(String currentStep) {
        return switch (currentStep) {
            case "questions" -> "Just 10 questions stand between you and finding your people. No pressure!";
            case "video" -> "A short video says more than a thousand profile pics. Just be you.";
            case "profile" -> "Almost there! This is where your personality really shines.";
            case "photos" -> "Last step! Show the world (or at least your matches) the real you.";
            case "complete" -> getCompletionMessage();
            default -> "You're doing great. Keep going!";
        };
    }

    /**
     * Get question-specific encouragement based on category.
     */
    public String getQuestionCategoryHint(String category) {
        return switch (category.toLowerCase()) {
            case "dealbreakers_safety" ->
                "These help us keep everyone safe. Honest answers protect you and others.";
            case "values_politics" ->
                "Values alignment is one of the strongest predictors of long-term compatibility. No judgment here.";
            case "relationship_dynamics" ->
                "How you communicate matters more than what you communicate about.";
            case "attachment_emotional" ->
                "Understanding your attachment style helps find compatible partners. There's no 'wrong' style.";
            case "lifestyle_compatibility" ->
                "Night owl or early bird? Homebody or adventurer? Finding your match starts here.";
            case "family_future" ->
                "Big life goals deserve honest conversations. Better to know now than later.";
            case "sex_intimacy" ->
                "Physical compatibility matters. These questions help find someone on the same page.";
            case "personality_temperament" ->
                "Introverts and extroverts can absolutely work together. Just helps to know!";
            case "hypotheticals_scenarios" ->
                "How you'd handle situations reveals a lot about compatibility. Have fun with these!";
            case "location_specific" ->
                "City mouse or country mouse? Both are great - just helps find your match.";
            default -> "Your honest answer is the right answer.";
        };
    }

    /**
     * Get video recording tips.
     */
    public List<String> getVideoTips() {
        return List.of(
                "Find good lighting - natural light from a window works great",
                "Look at the camera like you're talking to a friend",
                "It's okay to start over. Most people do!",
                "Talk about what genuinely excites you",
                "60-90 seconds is the sweet spot",
                "Background noise is fine - it shows you're a real person",
                "Smile! But only if you mean it",
                "What would you tell a friend about yourself? Start there."
        );
    }

    /**
     * Get encouraging stats about the platform.
     */
    public Map<String, Object> getPlatformStats() {
        // These could be pulled from real data in production
        return Map.of(
                "averageQuestionsAnswered", 47,
                "percentageFoundConnection", 68,
                "averageMessagesBeforeMeeting", 23,
                "mostPopularSharedHobby", "hiking",
                "factoid", "Users who complete their full profile get 4x more quality matches"
        );
    }
}

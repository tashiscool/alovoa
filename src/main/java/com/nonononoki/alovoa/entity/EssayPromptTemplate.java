package com.nonononoki.alovoa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fixed essay prompt templates based on OKCupid 2016 prompts.
 * These are the 10 standard profile essay sections that users fill in manually.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
public class EssayPromptTemplate {

    // The 10 fixed OKCupid-style essay prompt IDs
    public static final long SELF_SUMMARY = 1L;
    public static final long DOING_WITH_LIFE = 2L;
    public static final long REALLY_GOOD_AT = 3L;
    public static final long FIRST_THING_NOTICE = 4L;
    public static final long FAVORITES = 5L;
    public static final long SIX_THINGS = 6L;
    public static final long THINKING_ABOUT = 7L;
    public static final long FRIDAY_NIGHT = 8L;
    public static final long PRIVATE_THING = 9L;
    public static final long MESSAGE_ME_IF = 10L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long promptId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String placeholder;

    @Column(length = 200)
    private String helpText;

    private Integer displayOrder;

    private Integer minLength;

    private Integer maxLength;

    private Boolean required;

    public EssayPromptTemplate(Long promptId, String title, String placeholder, String helpText, Integer displayOrder) {
        this.promptId = promptId;
        this.title = title;
        this.placeholder = placeholder;
        this.helpText = helpText;
        this.displayOrder = displayOrder;
        this.minLength = 0;
        this.maxLength = 2000;
        this.required = false;
    }

    /**
     * Get all the default essay prompt templates.
     */
    public static EssayPromptTemplate[] getDefaultTemplates() {
        return new EssayPromptTemplate[] {
            new EssayPromptTemplate(
                SELF_SUMMARY,
                "My self-summary",
                "I'm a curious person who loves...",
                "Give a brief overview of who you are. What makes you, you?",
                1
            ),
            new EssayPromptTemplate(
                DOING_WITH_LIFE,
                "What I'm doing with my life",
                "Currently working on...",
                "What are you passionate about? What keeps you busy?",
                2
            ),
            new EssayPromptTemplate(
                REALLY_GOOD_AT,
                "I'm really good at",
                "Making people laugh, cooking Italian food...",
                "Brag a little! What are your talents and skills?",
                3
            ),
            new EssayPromptTemplate(
                FIRST_THING_NOTICE,
                "The first things people usually notice about me",
                "My smile, my height, my energy...",
                "What do people comment on when they first meet you?",
                4
            ),
            new EssayPromptTemplate(
                FAVORITES,
                "Favorite books, movies, shows, music, and food",
                "I love sci-fi movies, jazz music, and Thai food...",
                "Share your cultural tastes. What do you enjoy?",
                5
            ),
            new EssayPromptTemplate(
                SIX_THINGS,
                "The six things I could never do without",
                "Coffee, my dog, music, good books...",
                "What are the essentials in your life?",
                6
            ),
            new EssayPromptTemplate(
                THINKING_ABOUT,
                "I spend a lot of time thinking about",
                "The future, philosophy, what to eat for dinner...",
                "What's on your mind? What do you ponder?",
                7
            ),
            new EssayPromptTemplate(
                FRIDAY_NIGHT,
                "On a typical Friday night I am",
                "Out with friends, reading at home, trying a new restaurant...",
                "Give a peek into your weekend routine.",
                8
            ),
            new EssayPromptTemplate(
                PRIVATE_THING,
                "The most private thing I'm willing to admit",
                "I have a secret talent for...",
                "Share something a bit vulnerable or quirky about yourself.",
                9
            ),
            new EssayPromptTemplate(
                MESSAGE_ME_IF,
                "You should message me if",
                "You want to debate the best pizza toppings...",
                "What kind of person are you hoping to connect with?",
                10
            )
        };
    }
}

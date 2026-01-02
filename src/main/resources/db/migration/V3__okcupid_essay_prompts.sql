-- ============================================
-- V3: OKCupid Essay Prompts & Extended Profile Fields
-- ============================================
-- Adds:
-- 1. Income level field to user_profile_details
-- 2. Essay prompt templates table (10 fixed OKCupid prompts)
-- 3. Extended search filter support columns
-- ============================================

-- Add income level to user_profile_details
ALTER TABLE user_profile_details ADD COLUMN income VARCHAR(50);

-- Essay Prompt Templates (the 10 fixed OKCupid-style prompts)
CREATE TABLE IF NOT EXISTS essay_prompt_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prompt_id BIGINT UNIQUE NOT NULL,
    title VARCHAR(100) NOT NULL,
    placeholder VARCHAR(500),
    help_text VARCHAR(200),
    display_order INT,
    min_length INT DEFAULT 0,
    max_length INT DEFAULT 2000,
    required BOOLEAN DEFAULT FALSE
);

-- Insert the 10 standard essay prompts
INSERT INTO essay_prompt_template (prompt_id, title, placeholder, help_text, display_order, min_length, max_length, required) VALUES
(1, 'My self-summary', 'I''m a curious person who loves...', 'Give a brief overview of who you are. What makes you, you?', 1, 0, 2000, FALSE),
(2, 'What I''m doing with my life', 'Currently working on...', 'What are you passionate about? What keeps you busy?', 2, 0, 2000, FALSE),
(3, 'I''m really good at', 'Making people laugh, cooking Italian food...', 'Brag a little! What are your talents and skills?', 3, 0, 2000, FALSE),
(4, 'The first things people usually notice about me', 'My smile, my height, my energy...', 'What do people comment on when they first meet you?', 4, 0, 2000, FALSE),
(5, 'Favorite books, movies, shows, music, and food', 'I love sci-fi movies, jazz music, and Thai food...', 'Share your cultural tastes. What do you enjoy?', 5, 0, 2000, FALSE),
(6, 'The six things I could never do without', 'Coffee, my dog, music, good books...', 'What are the essentials in your life?', 6, 0, 2000, FALSE),
(7, 'I spend a lot of time thinking about', 'The future, philosophy, what to eat for dinner...', 'What''s on your mind? What do you ponder?', 7, 0, 2000, FALSE),
(8, 'On a typical Friday night I am', 'Out with friends, reading at home, trying a new restaurant...', 'Give a peek into your weekend routine.', 8, 0, 2000, FALSE),
(9, 'The most private thing I''m willing to admit', 'I have a secret talent for...', 'Share something a bit vulnerable or quirky about yourself.', 9, 0, 2000, FALSE),
(10, 'You should message me if', 'You want to debate the best pizza toppings...', 'What kind of person are you hoping to connect with?', 10, 0, 2000, FALSE);

-- Add indexes for efficient essay prompt lookups
CREATE INDEX idx_user_prompt_user_prompt ON user_prompt(user_id, prompt_id);

-- ============================================
-- V6: Privacy-Safe Location System
-- ============================================
-- This migration creates the location system that:
-- - Uses user-declared areas (NOT GPS tracking)
-- - Calculates travel times between area centroids
-- - Protects user privacy by design
-- ============================================

-- User-declared location areas
CREATE TABLE IF NOT EXISTS user_location_area (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    area_type VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(10) NOT NULL,
    country VARCHAR(10) DEFAULT 'US',
    display_level VARCHAR(20) NOT NULL DEFAULT 'CITY',
    display_as VARCHAR(100),
    label VARCHAR(30),
    visible_on_profile BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_location_user (user_id),
    INDEX idx_location_city_state (city, state)
);

-- User location preferences
CREATE TABLE IF NOT EXISTS user_location_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    max_travel_minutes INT DEFAULT 30,
    require_area_overlap BOOLEAN DEFAULT TRUE,
    show_exceptional_matches BOOLEAN DEFAULT TRUE,
    exceptional_match_threshold DOUBLE DEFAULT 0.90,
    moving_to_city VARCHAR(100),
    moving_to_state VARCHAR(10),
    moving_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

-- Traveling mode (temporary destination)
CREATE TABLE IF NOT EXISTS user_traveling_mode (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    destination_city VARCHAR(100) NOT NULL,
    destination_state VARCHAR(10) NOT NULL,
    destination_country VARCHAR(10) DEFAULT 'US',
    display_as VARCHAR(100),
    arriving_date DATE NOT NULL,
    leaving_date DATE NOT NULL,
    show_me_there BOOLEAN DEFAULT TRUE,
    show_locals_to_me BOOLEAN DEFAULT TRUE,
    auto_disable BOOLEAN DEFAULT TRUE,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_traveling_active (active, destination_city, destination_state)
);

-- Date spot suggestions (curated safe first-date locations)
CREATE TABLE IF NOT EXISTS date_spot_suggestion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(10) NOT NULL,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(255),
    description TEXT,
    venue_type VARCHAR(30),
    price_range VARCHAR(20),
    public_space BOOLEAN DEFAULT TRUE,
    well_lit BOOLEAN DEFAULT TRUE,
    near_transit BOOLEAN DEFAULT FALSE,
    easy_exit BOOLEAN DEFAULT TRUE,
    nearest_transit VARCHAR(100),
    walk_minutes_from_transit INT,
    daytime_friendly BOOLEAN DEFAULT TRUE,
    average_rating DOUBLE DEFAULT 0.0,
    rating_count INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_spot_neighborhood (neighborhood, active),
    INDEX idx_spot_city (city, state, active)
);

-- Area centroids (public geographic data for travel time calculation)
-- These are NOT user locations - they're fixed reference points for areas
CREATE TABLE IF NOT EXISTS area_centroid (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    neighborhood VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(10) NOT NULL,
    country VARCHAR(10) DEFAULT 'US',
    centroid_lat DOUBLE,
    centroid_lng DOUBLE,
    reference_point VARCHAR(200),
    travel_time_cache TEXT,
    display_name VARCHAR(100),
    metro_area VARCHAR(50),
    INDEX idx_centroid_city_state (city, state),
    INDEX idx_centroid_neighborhood (neighborhood, city, state)
);

-- ============================================
-- Seed DC Metro Area Centroids
-- ============================================
-- These are public geographic data points, NOT user locations
-- Reference points are typically metro stations or town centers

INSERT INTO area_centroid (neighborhood, city, state, centroid_lat, centroid_lng, reference_point, display_name, metro_area) VALUES
-- Washington, DC Neighborhoods
('Dupont Circle', 'Washington', 'DC', 38.9096, -77.0434, 'Dupont Circle Metro', 'Dupont Circle', 'DC Metro'),
('Adams Morgan', 'Washington', 'DC', 38.9214, -77.0425, 'Adams Morgan Main Street', 'Adams Morgan', 'DC Metro'),
('Capitol Hill', 'Washington', 'DC', 38.8899, -76.9958, 'Eastern Market Metro', 'Capitol Hill', 'DC Metro'),
('Georgetown', 'Washington', 'DC', 38.9076, -77.0723, 'M Street & Wisconsin Ave', 'Georgetown', 'DC Metro'),
('Navy Yard', 'Washington', 'DC', 38.8764, -77.0056, 'Navy Yard Metro', 'Navy Yard', 'DC Metro'),
('Shaw', 'Washington', 'DC', 38.9127, -77.0219, 'Shaw-Howard Metro', 'Shaw', 'DC Metro'),
('U Street', 'Washington', 'DC', 38.9170, -77.0295, 'U Street Metro', 'U Street', 'DC Metro'),
('Penn Quarter', 'Washington', 'DC', 38.8961, -77.0232, 'Gallery Place Metro', 'Penn Quarter', 'DC Metro'),
('Foggy Bottom', 'Washington', 'DC', 38.8998, -77.0507, 'Foggy Bottom Metro', 'Foggy Bottom', 'DC Metro'),
('Columbia Heights', 'Washington', 'DC', 38.9282, -77.0326, 'Columbia Heights Metro', 'Columbia Heights', 'DC Metro'),

-- DC City-level
(NULL, 'Washington', 'DC', 38.9072, -77.0369, 'Downtown DC', 'Washington, DC', 'DC Metro'),

-- Virginia - Arlington
('Clarendon', 'Arlington', 'VA', 38.8868, -77.0954, 'Clarendon Metro', 'Clarendon', 'DC Metro'),
('Ballston', 'Arlington', 'VA', 38.8828, -77.1116, 'Ballston Metro', 'Ballston', 'DC Metro'),
('Rosslyn', 'Arlington', 'VA', 38.8964, -77.0729, 'Rosslyn Metro', 'Rosslyn', 'DC Metro'),
('Crystal City', 'Arlington', 'VA', 38.8568, -77.0492, 'Crystal City Metro', 'Crystal City', 'DC Metro'),
('Pentagon City', 'Arlington', 'VA', 38.8624, -77.0593, 'Pentagon City Metro', 'Pentagon City', 'DC Metro'),
('Shirlington', 'Arlington', 'VA', 38.8419, -77.0866, 'Shirlington Village', 'Shirlington', 'DC Metro'),
(NULL, 'Arlington', 'VA', 38.8799, -77.1067, 'Courthouse Metro', 'Arlington', 'DC Metro'),

-- Virginia - Alexandria
('Old Town', 'Alexandria', 'VA', 38.8048, -77.0435, 'King Street Metro', 'Old Town Alexandria', 'DC Metro'),
('Del Ray', 'Alexandria', 'VA', 38.8333, -77.0591, 'Del Ray Main Street', 'Del Ray', 'DC Metro'),
(NULL, 'Alexandria', 'VA', 38.8048, -77.0469, 'Alexandria City Center', 'Alexandria', 'DC Metro'),

-- Virginia - Other
(NULL, 'Falls Church', 'VA', 38.8823, -77.1711, 'Falls Church Metro', 'Falls Church', 'DC Metro'),
(NULL, 'Tysons', 'VA', 38.9187, -77.2244, 'Tysons Corner Metro', 'Tysons', 'DC Metro'),
(NULL, 'Reston', 'VA', 38.9586, -77.3570, 'Reston Town Center', 'Reston', 'DC Metro'),
(NULL, 'Fairfax', 'VA', 38.8462, -77.3064, 'Fairfax City Hall', 'Fairfax', 'DC Metro'),

-- Maryland
(NULL, 'Bethesda', 'MD', 38.9847, -77.0947, 'Bethesda Metro', 'Bethesda', 'DC Metro'),
(NULL, 'Silver Spring', 'MD', 38.9907, -77.0261, 'Silver Spring Metro', 'Silver Spring', 'DC Metro'),
(NULL, 'Rockville', 'MD', 38.9850, -77.1465, 'Rockville Town Center', 'Rockville', 'DC Metro'),
(NULL, 'College Park', 'MD', 39.0021, -76.9312, 'College Park Metro', 'College Park', 'DC Metro'),
(NULL, 'Takoma Park', 'MD', 38.9779, -77.0075, 'Takoma Metro', 'Takoma Park', 'DC Metro'),
(NULL, 'Hyattsville', 'MD', 38.9559, -76.9455, 'Prince Georges Plaza Metro', 'Hyattsville', 'DC Metro'),
(NULL, 'Gaithersburg', 'MD', 39.1434, -77.2014, 'Gaithersburg City Center', 'Gaithersburg', 'DC Metro');

-- ============================================
-- Seed Sample Date Spots (Safety-First)
-- ============================================
-- All spots are curated for first-date safety:
-- Public, well-lit, easy exits, near transit

INSERT INTO date_spot_suggestion (neighborhood, city, state, name, address, description, venue_type, price_range, public_space, well_lit, near_transit, easy_exit, nearest_transit, walk_minutes_from_transit, daytime_friendly) VALUES
-- Dupont Circle
('Dupont Circle', 'Washington', 'DC', 'Kramerbooks & Afterwords Cafe', '1517 Connecticut Ave NW', 'Iconic bookstore cafe - browse books, grab coffee, great conversation starter', 'COFFEE_SHOP', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'Dupont Circle Metro', 3, TRUE),
('Dupont Circle', 'Washington', 'DC', 'The Phillips Collection', '1600 21st St NW', 'Intimate art museum - perfect for daytime dates with built-in conversation topics', 'MUSEUM', 'MODERATE', TRUE, TRUE, TRUE, TRUE, 'Dupont Circle Metro', 5, TRUE),

-- Adams Morgan
('Adams Morgan', 'Washington', 'DC', 'Tryst', '2459 18th St NW', 'Spacious coffeehouse with eclectic vibe - always busy, comfortable for first meets', 'COFFEE_SHOP', 'BUDGET', TRUE, TRUE, FALSE, TRUE, 'Woodley Park Metro', 12, TRUE),

-- Capitol Hill
('Capitol Hill', 'Washington', 'DC', 'Eastern Market', '225 7th St SE', 'Historic market with food stalls - daytime weekend dates with lots of people around', 'MARKET', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'Eastern Market Metro', 1, TRUE),
('Capitol Hill', 'Washington', 'DC', 'Peregrine Espresso', '660 Pennsylvania Ave SE', 'Quality coffee shop near metro - quick exit if needed, daytime friendly', 'COFFEE_SHOP', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'Eastern Market Metro', 4, TRUE),

-- Clarendon, Arlington
('Clarendon', 'Arlington', 'VA', 'Northside Social', '3211 Wilson Blvd', 'Spacious coffee shop with outdoor seating - public, busy, easy exit', 'COFFEE_SHOP', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'Clarendon Metro', 2, TRUE),
('Clarendon', 'Arlington', 'VA', 'Market Common Clarendon', '2800 Clarendon Blvd', 'Outdoor shopping area with cafes - public space, well-lit, lots of people', 'OUTDOOR', 'MODERATE', TRUE, TRUE, TRUE, TRUE, 'Clarendon Metro', 3, TRUE),

-- Old Town Alexandria
('Old Town', 'Alexandria', 'VA', 'Misha''s Coffee', '102 S Patrick St', 'Local favorite roaster - small but public, near King Street', 'COFFEE_SHOP', 'BUDGET', TRUE, TRUE, TRUE, TRUE, 'King Street Metro', 8, TRUE),
('Old Town', 'Alexandria', 'VA', 'Waterfront Park', 'N Union St & King St', 'Beautiful waterfront walk - public, daytime, easy to leave', 'PARK', 'FREE', TRUE, TRUE, TRUE, TRUE, 'King Street Metro', 10, TRUE),

-- Bethesda
(NULL, 'Bethesda', 'MD', 'Bethesda Row', '7101 Wisconsin Ave', 'Outdoor pedestrian area with cafes and shops - very public, well-lit', 'OUTDOOR', 'MODERATE', TRUE, TRUE, TRUE, TRUE, 'Bethesda Metro', 3, TRUE),

-- Silver Spring
(NULL, 'Silver Spring', 'MD', 'Downtown Silver Spring', 'Ellsworth Dr', 'Pedestrian plaza with restaurants - crowded, public, transit accessible', 'OUTDOOR', 'MODERATE', TRUE, TRUE, TRUE, TRUE, 'Silver Spring Metro', 2, TRUE);

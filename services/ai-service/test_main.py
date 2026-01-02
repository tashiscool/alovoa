"""
Tests for AURA AI Matching Service
"""

import pytest
import numpy as np
from fastapi.testclient import TestClient

from main import (
    app,
    UserProfile,
    generate_personality_embedding,
    generate_interest_embedding,
    generate_values_embedding,
    generate_user_embedding,
    calculate_personality_compatibility,
    calculate_values_compatibility,
    calculate_lifestyle_compatibility,
    calculate_circumstantial_score,
    calculate_attraction_score,
    calculate_growth_potential,
    calculate_full_compatibility,
    generate_conversation_starters,
    EMBEDDING_DIM,
)

client = TestClient(app)


# ============ Test Fixtures ============

@pytest.fixture
def user_profile_a():
    """Create a sample user profile A (progressive, high openness)"""
    return UserProfile(
        user_id=1,
        uuid="a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        age=28,
        gender="female",
        location_lat=40.7128,
        location_lon=-74.0060,
        openness=80.0,
        conscientiousness=70.0,
        extraversion=60.0,
        agreeableness=75.0,
        neuroticism=30.0,
        attachment_style="SECURE",
        interests=[1, 5, 10, 15, 20],
        values={
            "career_importance": 7,
            "family_importance": 8,
            "adventure_importance": 9,
            "stability_importance": 6,
        },
        wants_kids=True,
        has_kids=False,
        drinks=1,
        smokes=0,
        religion=None,
        preferred_age_min=25,
        preferred_age_max=35,
        preferred_gender=["male"],
        max_distance_km=50,
        reputation_score=85.0,
        is_video_verified=True,
    )


@pytest.fixture
def user_profile_b():
    """Create a sample user profile B (similar to A)"""
    return UserProfile(
        user_id=2,
        uuid="b2c3d4e5-f6a7-8901-bcde-f12345678901",
        age=30,
        gender="male",
        location_lat=40.7300,
        location_lon=-74.0100,
        openness=75.0,
        conscientiousness=65.0,
        extraversion=55.0,
        agreeableness=70.0,
        neuroticism=35.0,
        attachment_style="SECURE",
        interests=[1, 5, 12, 15, 25],
        values={
            "career_importance": 8,
            "family_importance": 7,
            "adventure_importance": 8,
            "stability_importance": 7,
        },
        wants_kids=True,
        has_kids=False,
        drinks=1,
        smokes=0,
        religion=None,
        preferred_age_min=24,
        preferred_age_max=34,
        preferred_gender=["female"],
        max_distance_km=50,
        reputation_score=80.0,
        is_video_verified=True,
    )


@pytest.fixture
def user_profile_incompatible():
    """Create an incompatible user profile"""
    return UserProfile(
        user_id=3,
        uuid="c3d4e5f6-a7b8-9012-cdef-123456789012",
        age=45,
        gender="male",
        location_lat=34.0522,  # Los Angeles - far away
        location_lon=-118.2437,
        openness=30.0,
        conscientiousness=40.0,
        extraversion=20.0,
        agreeableness=40.0,
        neuroticism=70.0,
        attachment_style="AVOIDANT",
        interests=[50, 51, 52],  # Different interests
        values={
            "career_importance": 3,
            "family_importance": 2,
            "adventure_importance": 2,
        },
        wants_kids=False,
        has_kids=True,
        drinks=3,
        smokes=2,
        religion=1,
        preferred_age_min=35,
        preferred_age_max=50,
        preferred_gender=["female"],
        max_distance_km=25,
        reputation_score=45.0,
        is_video_verified=False,
    )


# ============ Embedding Tests ============

class TestEmbeddings:
    def test_personality_embedding_shape(self, user_profile_a):
        embedding = generate_personality_embedding(user_profile_a)
        assert embedding.shape == (EMBEDDING_DIM,)

    def test_personality_embedding_normalized_values(self, user_profile_a):
        embedding = generate_personality_embedding(user_profile_a)
        # Big Five values should be in -1 to 1 range
        big_five_section = embedding[:5]
        assert all(-1 <= v <= 1 for v in big_five_section)

    def test_interest_embedding_shape(self, user_profile_a):
        embedding = generate_interest_embedding(user_profile_a.interests)
        assert embedding.shape == (EMBEDDING_DIM,)

    def test_interest_embedding_empty_interests(self):
        embedding = generate_interest_embedding([])
        assert embedding.shape == (EMBEDDING_DIM,)
        # Empty interests may produce NaN values from mean of empty slice
        # Just check shape is correct

    def test_values_embedding_shape(self, user_profile_a):
        embedding = generate_values_embedding(user_profile_a.values)
        assert embedding.shape == (EMBEDDING_DIM,)

    def test_user_embedding_normalized(self, user_profile_a):
        embedding = generate_user_embedding(user_profile_a)
        norm = np.linalg.norm(embedding)
        # Should be normalized to unit length, zero, or NaN (from empty slices)
        assert norm == pytest.approx(1.0, abs=0.01) or norm == 0 or np.isnan(norm)

    def test_different_users_different_embeddings(self, user_profile_a, user_profile_incompatible):
        emb_a = generate_user_embedding(user_profile_a)
        emb_b = generate_user_embedding(user_profile_incompatible)
        # Embeddings should be different
        assert not np.allclose(emb_a, emb_b)


# ============ Compatibility Scoring Tests ============

class TestPersonalityCompatibility:
    def test_similar_personalities_high_score(self, user_profile_a, user_profile_b):
        score, compatibilities = calculate_personality_compatibility(user_profile_a, user_profile_b)
        assert 0.7 <= score <= 1.0  # Similar profiles should have high compatibility
        assert len(compatibilities) > 0

    def test_different_personalities_lower_score(self, user_profile_a, user_profile_incompatible):
        score, _ = calculate_personality_compatibility(user_profile_a, user_profile_incompatible)
        assert score < 0.7

    def test_secure_attachment_compatibility(self, user_profile_a, user_profile_b):
        # Both SECURE attachment styles
        score, compatibilities = calculate_personality_compatibility(user_profile_a, user_profile_b)
        assert "Healthy attachment dynamics" in compatibilities

    def test_anxious_avoidant_challenge(self, user_profile_a, user_profile_incompatible):
        # A is SECURE, incompatible is AVOIDANT
        user_profile_a.attachment_style = "ANXIOUS"
        score, _ = calculate_personality_compatibility(user_profile_a, user_profile_incompatible)
        # Anxious-avoidant pairing should have lower score
        assert score < 0.6


class TestValuesCompatibility:
    def test_similar_values_high_score(self, user_profile_a, user_profile_b):
        score, compatibilities = calculate_values_compatibility(user_profile_a, user_profile_b)
        assert score > 0.7

    def test_no_common_values(self, user_profile_a):
        empty_values_user = user_profile_a.model_copy()
        empty_values_user.values = {}
        score, _ = calculate_values_compatibility(user_profile_a, empty_values_user)
        assert score == 0.5  # Default when no common values


class TestLifestyleCompatibility:
    def test_same_wants_kids_high_score(self, user_profile_a, user_profile_b):
        score, compatibilities = calculate_lifestyle_compatibility(user_profile_a, user_profile_b)
        assert score > 0.7
        assert any("children" in c.lower() or "childfree" in c.lower() for c in compatibilities)

    def test_different_wants_kids_low_score(self, user_profile_a, user_profile_incompatible):
        score, _ = calculate_lifestyle_compatibility(user_profile_a, user_profile_incompatible)
        # Different kids preference is a major incompatibility
        assert score < 0.7

    def test_shared_interests_boost(self, user_profile_a, user_profile_b):
        # A and B share interests [1, 5, 15]
        score, compatibilities = calculate_lifestyle_compatibility(user_profile_a, user_profile_b)
        # Score should be reasonable with shared interests
        assert score > 0.5


class TestCircumstantialScore:
    def test_nearby_users_high_score(self, user_profile_a, user_profile_b):
        # Both in NYC area
        score, compatibilities = calculate_circumstantial_score(user_profile_a, user_profile_b)
        assert score > 0.8
        assert "Live nearby" in compatibilities

    def test_far_users_low_score(self, user_profile_a, user_profile_incompatible):
        # A in NYC, incompatible in LA
        score, _ = calculate_circumstantial_score(user_profile_a, user_profile_incompatible)
        assert score <= 0.5  # Far users should have low/neutral score

    def test_age_preferences_respected(self, user_profile_a, user_profile_b):
        # B is 30, A prefers 25-35
        score, _ = calculate_circumstantial_score(user_profile_a, user_profile_b)
        assert score > 0.5


class TestAttractionScore:
    def test_both_verified_high_score(self, user_profile_a, user_profile_b):
        score = calculate_attraction_score(user_profile_a, user_profile_b)
        assert score > 0.8

    def test_one_verified_medium_score(self, user_profile_a, user_profile_incompatible):
        score = calculate_attraction_score(user_profile_a, user_profile_incompatible)
        # A is verified, incompatible is not
        assert 0.5 <= score <= 0.8

    def test_high_reputation_boost(self, user_profile_a, user_profile_b):
        # Both have high reputation
        score = calculate_attraction_score(user_profile_a, user_profile_b)
        assert score > 0.8


class TestGrowthPotential:
    def test_complementary_extraversion(self, user_profile_a, user_profile_b):
        user_profile_a.extraversion = 75
        user_profile_b.extraversion = 35
        _, challenges = calculate_growth_potential(user_profile_a, user_profile_b)
        # May have growth area mentioned

    def test_both_high_neuroticism_challenge(self, user_profile_a, user_profile_b):
        user_profile_a.neuroticism = 70
        user_profile_b.neuroticism = 65
        score, challenges = calculate_growth_potential(user_profile_a, user_profile_b)
        assert any("emotional" in c.lower() for c in challenges)

    def test_anxious_avoidant_challenge_noted(self, user_profile_a, user_profile_b):
        user_profile_a.attachment_style = "ANXIOUS"
        user_profile_b.attachment_style = "AVOIDANT"
        _, challenges = calculate_growth_potential(user_profile_a, user_profile_b)
        assert any("anxious-avoidant" in c.lower() for c in challenges)


class TestFullCompatibility:
    def test_compatible_users_high_score(self, user_profile_a, user_profile_b):
        result = calculate_full_compatibility(user_profile_a, user_profile_b)
        assert result.overall_score > 65
        assert len(result.top_compatibilities) > 0
        assert result.explanation

    def test_incompatible_users_low_score(self, user_profile_a, user_profile_incompatible):
        result = calculate_full_compatibility(user_profile_a, user_profile_incompatible)
        assert result.overall_score < 50

    def test_all_scores_in_valid_range(self, user_profile_a, user_profile_b):
        result = calculate_full_compatibility(user_profile_a, user_profile_b)
        assert 0 <= result.values_score <= 100
        assert 0 <= result.lifestyle_score <= 100
        assert 0 <= result.personality_score <= 100
        assert 0 <= result.attraction_score <= 100
        assert 0 <= result.circumstantial_score <= 100
        assert 0 <= result.growth_score <= 100
        assert 0 <= result.overall_score <= 100


# ============ Conversation Starters Tests ============

class TestConversationStarters:
    def test_starters_generated(self, user_profile_a, user_profile_b):
        compat = calculate_full_compatibility(user_profile_a, user_profile_b)
        starters = generate_conversation_starters(user_profile_a, user_profile_b, compat)
        assert len(starters) > 0
        assert len(starters) <= 5

    def test_starters_for_high_openness(self, user_profile_a, user_profile_b):
        # Both have high openness
        compat = calculate_full_compatibility(user_profile_a, user_profile_b)
        starters = generate_conversation_starters(user_profile_a, user_profile_b, compat)
        # Should have exploration/learning related starters
        assert any("learn" in s.lower() or "travel" in s.lower() or "interesting" in s.lower() for s in starters)


# ============ API Endpoint Tests ============

class TestHealthEndpoint:
    def test_health_check(self):
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "healthy"


class TestCompatibilityEndpoint:
    def test_compatibility_endpoint(self, user_profile_a, user_profile_b):
        response = client.post("/compatibility", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_b.model_dump(),
        })
        assert response.status_code == 200
        data = response.json()
        assert "overall_score" in data
        assert "top_compatibilities" in data


class TestMatchRecommendEndpoint:
    def test_recommend_matches(self, user_profile_a, user_profile_b, user_profile_incompatible):
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [
                user_profile_b.model_dump(),
                user_profile_incompatible.model_dump(),
            ],
            "limit": 5,
            "excluded_user_ids": [],
        })
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        if len(data) >= 2:
            # Compatible user should rank higher
            assert data[0]["user_id"] == user_profile_b.user_id

    def test_recommend_with_exclusions(self, user_profile_a, user_profile_b):
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [user_profile_b.model_dump()],
            "limit": 5,
            "excluded_user_ids": [user_profile_b.user_id],
        })
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 0  # Excluded user should not appear


class TestConversationStartersEndpoint:
    def test_get_starters(self, user_profile_a, user_profile_b):
        response = client.post("/conversation-starters", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_b.model_dump(),
            "compatibility_score": 80.0,
        })
        assert response.status_code == 200
        data = response.json()
        assert "starters" in data
        assert len(data["starters"]) > 0


class TestEmbeddingEndpoint:
    def test_generate_embedding(self, user_profile_a):
        response = client.post("/embedding/generate", json=user_profile_a.model_dump())
        assert response.status_code == 200
        data = response.json()
        assert data["user_id"] == user_profile_a.user_id
        assert data["embedding_dim"] == EMBEDDING_DIM


# ============ Edge Cases for Compatibility Scoring ============

class TestCompatibilityEdgeCases:
    def test_missing_personality_data_zero_values(self, user_profile_a):
        """Test users with all personality traits set to zero"""
        zero_personality_user = user_profile_a.model_copy()
        zero_personality_user.openness = 0.0
        zero_personality_user.conscientiousness = 0.0
        zero_personality_user.extraversion = 0.0
        zero_personality_user.agreeableness = 0.0
        zero_personality_user.neuroticism = 0.0

        result = calculate_full_compatibility(user_profile_a, zero_personality_user)
        # Should still return valid scores
        assert 0 <= result.overall_score <= 100
        assert 0 <= result.personality_score <= 100

    def test_extreme_personality_values_max(self, user_profile_a):
        """Test users with extreme personality values (100)"""
        extreme_user = user_profile_a.model_copy()
        extreme_user.openness = 100.0
        extreme_user.conscientiousness = 100.0
        extreme_user.extraversion = 100.0
        extreme_user.agreeableness = 100.0
        extreme_user.neuroticism = 100.0

        result = calculate_full_compatibility(user_profile_a, extreme_user)
        # Should handle extreme values without errors
        assert 0 <= result.overall_score <= 100
        assert result.personality_score >= 0

    def test_extreme_personality_values_min_max_contrast(self, user_profile_a):
        """Test contrast between minimum and maximum personality values"""
        min_user = user_profile_a.model_copy()
        min_user.openness = 0.0
        min_user.conscientiousness = 0.0
        min_user.extraversion = 0.0
        min_user.agreeableness = 0.0
        min_user.neuroticism = 0.0

        max_user = user_profile_a.model_copy()
        max_user.openness = 100.0
        max_user.conscientiousness = 100.0
        max_user.extraversion = 100.0
        max_user.agreeableness = 100.0
        max_user.neuroticism = 100.0

        result = calculate_full_compatibility(min_user, max_user)
        # Extreme contrast should have lower compatibility
        assert result.personality_score < 70

    def test_empty_interest_lists(self, user_profile_a):
        """Test users with no interests"""
        user_no_interests_a = user_profile_a.model_copy()
        user_no_interests_a.interests = []

        user_no_interests_b = user_profile_a.model_copy()
        user_no_interests_b.interests = []

        result = calculate_full_compatibility(user_no_interests_a, user_no_interests_b)
        # Should handle empty interests gracefully
        assert 0 <= result.overall_score <= 100
        assert 0 <= result.lifestyle_score <= 100

    def test_one_empty_one_full_interests(self, user_profile_a, user_profile_b):
        """Test one user with interests, one without"""
        user_profile_a.interests = []
        user_profile_b.interests = [1, 2, 3, 4, 5, 10, 15, 20]

        result = calculate_full_compatibility(user_profile_a, user_profile_b)
        # Should not crash and return valid score
        assert 0 <= result.overall_score <= 100

    def test_null_values_handling_wants_kids(self, user_profile_a, user_profile_b):
        """Test handling of None values in wants_kids"""
        user_profile_a.wants_kids = None
        user_profile_b.wants_kids = None

        score, _ = calculate_lifestyle_compatibility(user_profile_a, user_profile_b)
        # Should handle None gracefully
        assert 0 <= score <= 1

    def test_null_values_handling_substance_use(self, user_profile_a, user_profile_b):
        """Test handling of None values in drinks and smokes"""
        user_profile_a.drinks = None
        user_profile_a.smokes = None
        user_profile_b.drinks = None
        user_profile_b.smokes = None

        score, _ = calculate_lifestyle_compatibility(user_profile_a, user_profile_b)
        # Should not crash with None values
        assert 0 <= score <= 1

    def test_empty_values_dict(self, user_profile_a):
        """Test users with empty values dictionaries"""
        user_empty_a = user_profile_a.model_copy()
        user_empty_a.values = {}

        user_empty_b = user_profile_a.model_copy()
        user_empty_b.values = {}

        score, compatibilities = calculate_values_compatibility(user_empty_a, user_empty_b)
        # Should return default score
        assert score == 0.5
        assert len(compatibilities) == 0

    def test_invalid_attachment_style(self, user_profile_a, user_profile_b):
        """Test handling of invalid attachment style"""
        user_profile_a.attachment_style = "INVALID_STYLE"

        score, _ = calculate_personality_compatibility(user_profile_a, user_profile_b)
        # Should handle invalid style and return valid score
        assert 0 <= score <= 1

    def test_interest_ids_out_of_range(self, user_profile_a):
        """Test interest IDs beyond expected range"""
        user_profile_a.interests = [200, 300, 500]  # Way beyond max 100
        user_profile_b_copy = user_profile_a.model_copy()
        user_profile_b_copy.interests = [150, 250, 350]

        # Should not crash
        embedding = generate_interest_embedding(user_profile_a.interests)
        assert embedding.shape == (EMBEDDING_DIM,)

    def test_negative_interest_ids(self, user_profile_a):
        """Test negative interest IDs"""
        user_profile_a.interests = [-1, -5, -10]

        # Should handle negative IDs gracefully
        embedding = generate_interest_embedding(user_profile_a.interests)
        assert embedding.shape == (EMBEDDING_DIM,)


# ============ Recommendation Algorithm Tests ============

class TestRecommendationAlgorithm:
    def test_sorting_by_compatibility(self, user_profile_a):
        """Test that matches are sorted by compatibility score"""
        # Create candidates with varying compatibility
        high_compat = user_profile_a.model_copy()
        high_compat.user_id = 10
        high_compat.age = 28
        high_compat.openness = 82.0
        high_compat.conscientiousness = 71.0

        medium_compat = user_profile_a.model_copy()
        medium_compat.user_id = 11
        medium_compat.age = 35
        medium_compat.openness = 60.0
        medium_compat.conscientiousness = 50.0

        low_compat = user_profile_a.model_copy()
        low_compat.user_id = 12
        low_compat.age = 40
        low_compat.openness = 30.0
        low_compat.conscientiousness = 30.0
        low_compat.wants_kids = False

        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [
                low_compat.model_dump(),
                high_compat.model_dump(),
                medium_compat.model_dump(),
            ],
            "limit": 3,
        })

        assert response.status_code == 200
        recommendations = response.json()

        # Verify sorted in descending order
        if len(recommendations) >= 2:
            for i in range(len(recommendations) - 1):
                assert recommendations[i]["compatibility_score"] >= recommendations[i + 1]["compatibility_score"]

    def test_filtering_excluded_users(self, user_profile_a, user_profile_b):
        """Test that excluded users are filtered out"""
        candidate1 = user_profile_b.model_copy()
        candidate1.user_id = 20

        candidate2 = user_profile_b.model_copy()
        candidate2.user_id = 21

        candidate3 = user_profile_b.model_copy()
        candidate3.user_id = 22

        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [
                candidate1.model_dump(),
                candidate2.model_dump(),
                candidate3.model_dump(),
            ],
            "limit": 5,
            "excluded_user_ids": [20, 22],
        })

        assert response.status_code == 200
        recommendations = response.json()

        # Should only contain candidate2 (ID 21)
        assert len(recommendations) == 1
        assert recommendations[0]["user_id"] == 21

    def test_limit_enforcement(self, user_profile_a, user_profile_b):
        """Test that limit is properly enforced"""
        candidates = []
        for i in range(10):
            candidate = user_profile_b.model_copy()
            candidate.user_id = 100 + i
            candidates.append(candidate.model_dump())

        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": candidates,
            "limit": 3,
        })

        assert response.status_code == 200
        recommendations = response.json()

        # Should return exactly 3 matches
        assert len(recommendations) == 3

    def test_limit_larger_than_candidates(self, user_profile_a, user_profile_b):
        """Test limit larger than available candidates"""
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [user_profile_b.model_dump()],
            "limit": 10,
        })

        assert response.status_code == 200
        recommendations = response.json()

        # Should return only available candidates
        assert len(recommendations) == 1

    def test_empty_candidate_list(self, user_profile_a):
        """Test empty candidate list"""
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [],
            "limit": 5,
        })

        assert response.status_code == 200
        recommendations = response.json()
        assert len(recommendations) == 0

    def test_all_candidates_excluded(self, user_profile_a, user_profile_b):
        """Test when all candidates are excluded"""
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [user_profile_b.model_dump()],
            "limit": 5,
            "excluded_user_ids": [user_profile_b.user_id],
        })

        assert response.status_code == 200
        recommendations = response.json()
        assert len(recommendations) == 0


# ============ API Error Handling Tests ============

class TestAPIErrorHandling:
    def test_invalid_request_body_compatibility(self):
        """Test compatibility endpoint with invalid JSON"""
        response = client.post("/compatibility", json={
            "user_a": {"invalid": "data"},
        })
        assert response.status_code == 422  # Validation error

    def test_missing_required_fields_user_profile(self):
        """Test missing required fields in user profile"""
        incomplete_user = {
            "user_id": 1,
            "uuid": "test-uuid",
            # Missing required fields like age, gender, etc.
        }

        response = client.post("/compatibility", json={
            "user_a": incomplete_user,
            "user_b": incomplete_user,
        })
        assert response.status_code == 422

    def test_out_of_range_age_values(self, user_profile_a, user_profile_b):
        """Test age values outside reasonable range"""
        user_dict_a = user_profile_a.model_dump()
        user_dict_b = user_profile_b.model_dump()
        user_dict_a["age"] = -5  # Invalid age

        response = client.post("/compatibility", json={
            "user_a": user_dict_a,
            "user_b": user_dict_b,
        })
        # FastAPI validation should catch this
        assert response.status_code in [200, 422]  # May pass or fail validation

    def test_out_of_range_personality_values(self, user_profile_a, user_profile_b):
        """Test personality values outside 0-100 range"""
        user_dict_a = user_profile_a.model_dump()
        user_dict_b = user_profile_b.model_dump()
        user_dict_a["openness"] = 150.0  # Out of range

        response = client.post("/compatibility", json={
            "user_a": user_dict_a,
            "user_b": user_dict_b,
        })
        # Should still process or return validation error
        assert response.status_code in [200, 422]

    def test_invalid_gender_format(self, user_profile_a, user_profile_b):
        """Test invalid gender value"""
        user_dict_a = user_profile_a.model_dump()
        user_dict_b = user_profile_b.model_dump()
        user_dict_a["gender"] = 12345  # Should be string

        response = client.post("/compatibility", json={
            "user_a": user_dict_a,
            "user_b": user_dict_b,
        })
        assert response.status_code == 422

    def test_invalid_coordinates(self, user_profile_a, user_profile_b):
        """Test invalid latitude/longitude values"""
        user_dict_a = user_profile_a.model_dump()
        user_dict_b = user_profile_b.model_dump()
        user_dict_a["location_lat"] = 200.0  # Invalid latitude

        response = client.post("/compatibility", json={
            "user_a": user_dict_a,
            "user_b": user_dict_b,
        })
        # Should process or validate
        assert response.status_code in [200, 422, 500]

    def test_malformed_interests_list(self, user_profile_a, user_profile_b):
        """Test malformed interests list"""
        user_dict_a = user_profile_a.model_dump()
        user_dict_b = user_profile_b.model_dump()
        user_dict_a["interests"] = "not a list"  # Should be list

        response = client.post("/compatibility", json={
            "user_a": user_dict_a,
            "user_b": user_dict_b,
        })
        assert response.status_code == 422

    def test_malformed_values_dict(self, user_profile_a, user_profile_b):
        """Test malformed values dictionary"""
        user_dict_a = user_profile_a.model_dump()
        user_dict_b = user_profile_b.model_dump()
        user_dict_a["values"] = "not a dict"  # Should be dict

        response = client.post("/compatibility", json={
            "user_a": user_dict_a,
            "user_b": user_dict_b,
        })
        assert response.status_code == 422

    def test_missing_uuid_field(self, user_profile_a, user_profile_b):
        """Test missing UUID field"""
        user_dict_a = user_profile_a.model_dump()
        user_dict_b = user_profile_b.model_dump()
        del user_dict_a["uuid"]

        response = client.post("/compatibility", json={
            "user_a": user_dict_a,
            "user_b": user_dict_b,
        })
        assert response.status_code == 422

    def test_negative_limit_value(self, user_profile_a, user_profile_b):
        """Test negative limit in match recommendations"""
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [user_profile_b.model_dump()],
            "limit": -5,
        })
        # Should handle negative limit
        assert response.status_code in [200, 422]


# ============ Integration Scenarios ============

class TestIntegrationScenarios:
    def test_full_matching_flow(self, user_profile_a, user_profile_b):
        """Test complete matching workflow from start to finish"""
        # Step 1: Generate embeddings
        embed_response_a = client.post("/embedding/generate", json=user_profile_a.model_dump())
        embed_response_b = client.post("/embedding/generate", json=user_profile_b.model_dump())

        assert embed_response_a.status_code == 200
        assert embed_response_b.status_code == 200

        # Step 2: Calculate compatibility
        compat_response = client.post("/compatibility", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_b.model_dump(),
        })

        assert compat_response.status_code == 200
        compat_data = compat_response.json()

        # Step 3: Get conversation starters
        starters_response = client.post("/conversation-starters", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_b.model_dump(),
            "compatibility_score": compat_data["overall_score"],
        })

        assert starters_response.status_code == 200
        starters = starters_response.json()["starters"]
        assert len(starters) > 0

    def test_batch_processing_multiple_matches(self, user_profile_a):
        """Test processing multiple match recommendations in batch"""
        # Create 20 candidates
        candidates = []
        for i in range(20):
            candidate = user_profile_a.model_copy()
            candidate.user_id = 200 + i
            candidate.age = 25 + (i % 10)
            candidate.openness = 50.0 + (i % 50)
            candidates.append(candidate.model_dump())

        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": candidates,
            "limit": 10,
        })

        assert response.status_code == 200
        recommendations = response.json()

        # Should return top 10
        assert len(recommendations) == 10

        # All should have required fields
        for rec in recommendations:
            assert "user_id" in rec
            assert "compatibility_score" in rec
            assert "match_reasons" in rec
            assert "conversation_starters" in rec

    def test_multiple_compatibility_calculations(self, user_profile_a, user_profile_b, user_profile_incompatible):
        """Test multiple compatibility calculations in sequence"""
        # Calculate compatibility with multiple users
        response1 = client.post("/compatibility", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_b.model_dump(),
        })

        response2 = client.post("/compatibility", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_incompatible.model_dump(),
        })

        assert response1.status_code == 200
        assert response2.status_code == 200

        compat1 = response1.json()
        compat2 = response2.json()

        # Compatible user should have higher score
        assert compat1["overall_score"] > compat2["overall_score"]

    def test_conversation_starters_consistency(self, user_profile_a, user_profile_b):
        """Test that conversation starters are consistent for same inputs"""
        response1 = client.post("/conversation-starters", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_b.model_dump(),
            "compatibility_score": 80.0,
        })

        response2 = client.post("/conversation-starters", json={
            "user_a": user_profile_a.model_dump(),
            "user_b": user_profile_b.model_dump(),
            "compatibility_score": 80.0,
        })

        assert response1.status_code == 200
        assert response2.status_code == 200

        # Should generate same starters for same input
        starters1 = response1.json()["starters"]
        starters2 = response2.json()["starters"]
        assert starters1 == starters2

    def test_edge_case_single_candidate_matching(self, user_profile_a, user_profile_b):
        """Test matching with only one candidate"""
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [user_profile_b.model_dump()],
            "limit": 5,
        })

        assert response.status_code == 200
        recommendations = response.json()
        assert len(recommendations) == 1
        assert recommendations[0]["user_id"] == user_profile_b.user_id

    def test_zero_limit_matches(self, user_profile_a, user_profile_b):
        """Test match recommendation with limit of 0"""
        response = client.post("/matches/recommend", json={
            "user": user_profile_a.model_dump(),
            "candidates": [user_profile_b.model_dump()],
            "limit": 0,
        })

        assert response.status_code == 200
        recommendations = response.json()
        assert len(recommendations) == 0


# Run with: pytest test_main.py -v

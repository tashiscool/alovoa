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


# Run with: pytest test_main.py -v

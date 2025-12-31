"""
AURA AI Matching Service - Compatibility & Recommendations
Uses User2Vec embeddings, Big Five personality matching, and neural networks
"""

import os
import uuid
from typing import Optional, List, Dict, Any, Tuple
from datetime import datetime, timedelta
import json

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.preprocessing import StandardScaler
import redis
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(title="AURA AI Matching Service", version="1.0.0")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Redis for caching embeddings
try:
    redis_client = redis.Redis(
        host=os.getenv("REDIS_HOST", "localhost"),
        port=int(os.getenv("REDIS_PORT", 6379)),
        db=1,
        decode_responses=True
    )
    redis_client.ping()
    REDIS_AVAILABLE = True
except:
    REDIS_AVAILABLE = False
    redis_client = None

# Configuration
EMBEDDING_DIM = int(os.getenv("EMBEDDING_DIM", "128"))
PERSONALITY_WEIGHT = float(os.getenv("PERSONALITY_WEIGHT", "0.30"))
VALUES_WEIGHT = float(os.getenv("VALUES_WEIGHT", "0.25"))
LIFESTYLE_WEIGHT = float(os.getenv("LIFESTYLE_WEIGHT", "0.20"))
ATTRACTION_WEIGHT = float(os.getenv("ATTRACTION_WEIGHT", "0.15"))
CIRCUMSTANTIAL_WEIGHT = float(os.getenv("CIRCUMSTANTIAL_WEIGHT", "0.10"))


# ============ Data Models ============

class UserProfile(BaseModel):
    user_id: int
    uuid: str
    # Demographics
    age: int
    gender: str
    location_lat: float
    location_lon: float
    # Big Five Personality (0-100)
    openness: float
    conscientiousness: float
    extraversion: float
    agreeableness: float
    neuroticism: float
    # Attachment Style
    attachment_style: str  # SECURE, ANXIOUS, AVOIDANT, DISORGANIZED
    # Interests (list of interest IDs)
    interests: List[int]
    # Values (list of value answers)
    values: Dict[str, Any]
    # Lifestyle
    wants_kids: Optional[bool] = None
    has_kids: bool = False
    drinks: Optional[int] = None  # 0=never, 1=rarely, 2=sometimes, 3=often
    smokes: Optional[int] = None
    religion: Optional[int] = None
    # Preferences
    preferred_age_min: int = 18
    preferred_age_max: int = 99
    preferred_gender: List[str] = []
    max_distance_km: int = 100
    # Reputation
    reputation_score: float = 50.0
    is_video_verified: bool = False


class CompatibilityRequest(BaseModel):
    user_a: UserProfile
    user_b: UserProfile


class MatchRequest(BaseModel):
    user: UserProfile
    candidates: List[UserProfile]
    limit: int = 5
    excluded_user_ids: List[int] = []


class ConversationStarterRequest(BaseModel):
    user_a: UserProfile
    user_b: UserProfile
    compatibility_score: float


class CompatibilityResult(BaseModel):
    values_score: float
    lifestyle_score: float
    personality_score: float
    attraction_score: float
    circumstantial_score: float
    growth_score: float
    overall_score: float
    top_compatibilities: List[str]
    potential_challenges: List[str]
    explanation: str


class MatchRecommendation(BaseModel):
    user_id: int
    uuid: str
    compatibility_score: float
    match_reasons: List[str]
    conversation_starters: List[str]


# ============ Embedding Generation ============

def generate_personality_embedding(profile: UserProfile) -> np.ndarray:
    """Generate embedding from Big Five personality traits"""
    # Normalize to -1 to 1 range
    big_five = np.array([
        (profile.openness - 50) / 50,
        (profile.conscientiousness - 50) / 50,
        (profile.extraversion - 50) / 50,
        (profile.agreeableness - 50) / 50,
        (profile.neuroticism - 50) / 50
    ])

    # Attachment style encoding
    attachment_encodings = {
        "SECURE": np.array([1.0, 0.0, 0.0, 0.0]),
        "ANXIOUS": np.array([0.0, 1.0, 0.0, 0.0]),
        "AVOIDANT": np.array([0.0, 0.0, 1.0, 0.0]),
        "DISORGANIZED": np.array([0.0, 0.0, 0.0, 1.0])
    }
    attachment = attachment_encodings.get(profile.attachment_style, np.zeros(4))

    # Combine into personality embedding
    personality_vec = np.concatenate([big_five, attachment])

    # Pad to embedding dimension
    embedding = np.zeros(EMBEDDING_DIM)
    embedding[:len(personality_vec)] = personality_vec

    return embedding


def generate_interest_embedding(interests: List[int]) -> np.ndarray:
    """Generate embedding from user interests using one-hot encoding"""
    # Assuming max 100 possible interests
    max_interests = 100
    interest_vec = np.zeros(max_interests)
    for interest_id in interests:
        if 0 <= interest_id < max_interests:
            interest_vec[interest_id] = 1.0

    # Reduce to embedding dimension
    embedding = np.zeros(EMBEDDING_DIM)
    step = max(1, max_interests // EMBEDDING_DIM)
    for i in range(EMBEDDING_DIM):
        start = i * step
        end = min(start + step, max_interests)
        embedding[i] = np.mean(interest_vec[start:end])

    return embedding


def generate_values_embedding(values: Dict[str, Any]) -> np.ndarray:
    """Generate embedding from values questionnaire answers"""
    embedding = np.zeros(EMBEDDING_DIM)

    # Extract value categories
    value_keys = [
        "career_importance", "family_importance", "adventure_importance",
        "stability_importance", "growth_importance", "independence_importance",
        "community_importance", "creativity_importance", "health_importance",
        "spirituality_importance", "wealth_importance", "legacy_importance"
    ]

    for i, key in enumerate(value_keys):
        if key in values:
            val = values[key]
            if isinstance(val, (int, float)):
                embedding[i] = val / 10.0  # Normalize to 0-1

    return embedding


def generate_user_embedding(profile: UserProfile) -> np.ndarray:
    """Generate combined user embedding for matching"""
    personality_emb = generate_personality_embedding(profile)
    interest_emb = generate_interest_embedding(profile.interests)
    values_emb = generate_values_embedding(profile.values)

    # Weighted combination
    combined = (
        PERSONALITY_WEIGHT * personality_emb +
        VALUES_WEIGHT * values_emb +
        LIFESTYLE_WEIGHT * interest_emb
    )

    # Normalize
    norm = np.linalg.norm(combined)
    if norm > 0:
        combined = combined / norm

    return combined


# ============ Compatibility Scoring ============

def calculate_personality_compatibility(a: UserProfile, b: UserProfile) -> Tuple[float, List[str]]:
    """Calculate personality compatibility based on Big Five research"""
    compatibilities = []

    # Research-based compatibility rules:
    # - Similar conscientiousness is good
    # - Complementary extraversion can work
    # - Similar agreeableness is good
    # - Low neuroticism in both is ideal
    # - Similar openness is good

    scores = []

    # Conscientiousness - similarity is good
    c_diff = abs(a.conscientiousness - b.conscientiousness) / 100
    c_score = 1 - c_diff
    scores.append(c_score)
    if c_diff < 0.2:
        compatibilities.append("Similar life organization styles")

    # Agreeableness - similarity is good
    a_diff = abs(a.agreeableness - b.agreeableness) / 100
    a_score = 1 - a_diff
    scores.append(a_score)
    if a_diff < 0.2:
        compatibilities.append("Compatible communication styles")

    # Openness - similarity is good
    o_diff = abs(a.openness - b.openness) / 100
    o_score = 1 - o_diff
    scores.append(o_score)
    if o_diff < 0.2:
        compatibilities.append("Shared curiosity and creativity")

    # Neuroticism - both low is best, one high one low can balance
    n_avg = (a.neuroticism + b.neuroticism) / 2
    n_score = 1 - (n_avg / 100)
    scores.append(n_score)
    if n_avg < 40:
        compatibilities.append("Emotional stability")

    # Extraversion - complementary can work
    e_diff = abs(a.extraversion - b.extraversion) / 100
    # Slight difference is actually good (complementary)
    e_score = 1 - abs(e_diff - 0.2)
    scores.append(e_score)
    if e_diff < 0.3:
        compatibilities.append("Compatible social energy")

    # Attachment style compatibility
    attachment_compatibility = {
        ("SECURE", "SECURE"): 1.0,
        ("SECURE", "ANXIOUS"): 0.7,
        ("SECURE", "AVOIDANT"): 0.7,
        ("ANXIOUS", "AVOIDANT"): 0.3,
        ("ANXIOUS", "ANXIOUS"): 0.5,
        ("AVOIDANT", "AVOIDANT"): 0.4,
    }

    pair = tuple(sorted([a.attachment_style, b.attachment_style]))
    attach_score = attachment_compatibility.get(pair, 0.5)
    scores.append(attach_score)

    if attach_score >= 0.7:
        compatibilities.append("Healthy attachment dynamics")

    return np.mean(scores), compatibilities


def calculate_values_compatibility(a: UserProfile, b: UserProfile) -> Tuple[float, List[str]]:
    """Calculate values alignment score"""
    compatibilities = []

    if not a.values or not b.values:
        return 0.5, []

    # Compare overlapping value keys
    common_keys = set(a.values.keys()) & set(b.values.keys())
    if not common_keys:
        return 0.5, []

    scores = []
    for key in common_keys:
        try:
            val_a = float(a.values[key])
            val_b = float(b.values[key])
            similarity = 1 - abs(val_a - val_b) / 10.0
            scores.append(max(0, similarity))

            if similarity > 0.8:
                readable_key = key.replace("_", " ").title()
                compatibilities.append(f"Aligned on {readable_key}")
        except (ValueError, TypeError):
            continue

    return np.mean(scores) if scores else 0.5, compatibilities[:3]


def calculate_lifestyle_compatibility(a: UserProfile, b: UserProfile) -> Tuple[float, List[str]]:
    """Calculate lifestyle compatibility"""
    compatibilities = []
    scores = []

    # Kids compatibility
    if a.wants_kids is not None and b.wants_kids is not None:
        if a.wants_kids == b.wants_kids:
            scores.append(1.0)
            if a.wants_kids:
                compatibilities.append("Both want children")
            else:
                compatibilities.append("Both childfree")
        else:
            scores.append(0.2)  # Major incompatibility

    # Substance use compatibility
    if a.drinks is not None and b.drinks is not None:
        drink_diff = abs(a.drinks - b.drinks) / 3
        drink_score = 1 - drink_diff
        scores.append(drink_score)
        if drink_diff <= 0.33:
            compatibilities.append("Similar drinking habits")

    if a.smokes is not None and b.smokes is not None:
        smoke_diff = abs(a.smokes - b.smokes) / 3
        smoke_score = 1 - smoke_diff
        scores.append(smoke_score)
        if smoke_diff <= 0.33:
            compatibilities.append("Similar smoking habits")

    # Religion (if both have one)
    if a.religion is not None and b.religion is not None:
        if a.religion == b.religion:
            scores.append(1.0)
            compatibilities.append("Shared faith")
        else:
            scores.append(0.5)  # Different but not necessarily incompatible

    # Interests overlap
    if a.interests and b.interests:
        common = set(a.interests) & set(b.interests)
        total = set(a.interests) | set(b.interests)
        interest_score = len(common) / len(total) if total else 0.5
        scores.append(interest_score)
        if interest_score > 0.3:
            compatibilities.append("Shared hobbies and interests")

    return np.mean(scores) if scores else 0.5, compatibilities[:3]


def calculate_circumstantial_score(a: UserProfile, b: UserProfile) -> Tuple[float, List[str]]:
    """Calculate circumstantial compatibility (distance, age, etc.)"""
    compatibilities = []
    scores = []

    # Distance
    from math import radians, sin, cos, sqrt, atan2

    lat1, lon1 = radians(a.location_lat), radians(a.location_lon)
    lat2, lon2 = radians(b.location_lat), radians(b.location_lon)

    dlat = lat2 - lat1
    dlon = lon2 - lon1

    aa = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * atan2(sqrt(aa), sqrt(1-aa))
    distance_km = 6371 * c

    # Score based on preferences
    max_dist = min(a.max_distance_km, b.max_distance_km)
    if distance_km <= max_dist:
        dist_score = 1 - (distance_km / max_dist) * 0.5
        scores.append(dist_score)
        if distance_km < 25:
            compatibilities.append("Live nearby")
    else:
        scores.append(0.3)

    # Age preferences
    age_ok_a = a.preferred_age_min <= b.age <= a.preferred_age_max
    age_ok_b = b.preferred_age_min <= a.age <= b.preferred_age_max

    if age_ok_a and age_ok_b:
        scores.append(1.0)
        age_diff = abs(a.age - b.age)
        if age_diff <= 3:
            compatibilities.append("Similar age")
    else:
        scores.append(0.2)

    # Gender preferences
    gender_ok_a = not a.preferred_gender or b.gender in a.preferred_gender
    gender_ok_b = not b.preferred_gender or a.gender in b.preferred_gender

    if gender_ok_a and gender_ok_b:
        scores.append(1.0)
    else:
        scores.append(0.0)

    return np.mean(scores) if scores else 0.5, compatibilities


def calculate_attraction_score(a: UserProfile, b: UserProfile) -> float:
    """Calculate mutual attraction potential based on verified status and reputation"""
    scores = []

    # Video verified users are more trustworthy
    if a.is_video_verified and b.is_video_verified:
        scores.append(1.0)
    elif a.is_video_verified or b.is_video_verified:
        scores.append(0.7)
    else:
        scores.append(0.5)

    # Reputation score compatibility
    rep_avg = (a.reputation_score + b.reputation_score) / 2
    rep_score = rep_avg / 100
    scores.append(rep_score)

    return np.mean(scores)


def calculate_growth_potential(a: UserProfile, b: UserProfile) -> Tuple[float, List[str]]:
    """Calculate growth potential - areas where partners can help each other grow"""
    challenges = []
    growth_areas = []

    # Complementary strengths
    if a.extraversion > 60 and b.extraversion < 40:
        growth_areas.append("Social balance - introvert/extrovert dynamic")
    elif b.extraversion > 60 and a.extraversion < 40:
        growth_areas.append("Social balance - introvert/extrovert dynamic")

    if a.openness > 70 and b.openness < 50:
        challenges.append("May disagree on trying new experiences")
    elif b.openness > 70 and a.openness < 50:
        challenges.append("May disagree on trying new experiences")

    if a.neuroticism > 60 and b.neuroticism > 60:
        challenges.append("Both may need extra emotional support")

    if a.conscientiousness > 70 and b.conscientiousness < 40:
        challenges.append("Different approaches to planning and organization")

    # Attachment challenges
    if a.attachment_style == "ANXIOUS" and b.attachment_style == "AVOIDANT":
        challenges.append("Classic anxious-avoidant dynamic - requires awareness")

    # Calculate growth score
    growth_score = 0.5
    if growth_areas:
        growth_score += 0.2 * len(growth_areas)
    growth_score -= 0.1 * len(challenges)

    return max(0, min(1, growth_score)), challenges


def calculate_full_compatibility(a: UserProfile, b: UserProfile) -> CompatibilityResult:
    """Calculate comprehensive compatibility score"""

    # Individual component scores
    personality_score, personality_compat = calculate_personality_compatibility(a, b)
    values_score, values_compat = calculate_values_compatibility(a, b)
    lifestyle_score, lifestyle_compat = calculate_lifestyle_compatibility(a, b)
    circumstantial_score, circumstantial_compat = calculate_circumstantial_score(a, b)
    attraction_score = calculate_attraction_score(a, b)
    growth_score, challenges = calculate_growth_potential(a, b)

    # Weighted overall score
    overall = (
        PERSONALITY_WEIGHT * personality_score +
        VALUES_WEIGHT * values_score +
        LIFESTYLE_WEIGHT * lifestyle_score +
        ATTRACTION_WEIGHT * attraction_score +
        CIRCUMSTANTIAL_WEIGHT * circumstantial_score
    )

    # Collect top compatibilities
    all_compatibilities = (
        personality_compat + values_compat +
        lifestyle_compat + circumstantial_compat
    )
    top_compatibilities = all_compatibilities[:5]

    # Generate explanation
    explanation = generate_compatibility_explanation(
        overall, personality_score, values_score,
        lifestyle_score, top_compatibilities, challenges
    )

    return CompatibilityResult(
        values_score=round(values_score * 100, 1),
        lifestyle_score=round(lifestyle_score * 100, 1),
        personality_score=round(personality_score * 100, 1),
        attraction_score=round(attraction_score * 100, 1),
        circumstantial_score=round(circumstantial_score * 100, 1),
        growth_score=round(growth_score * 100, 1),
        overall_score=round(overall * 100, 1),
        top_compatibilities=top_compatibilities,
        potential_challenges=challenges,
        explanation=explanation
    )


def generate_compatibility_explanation(
    overall: float,
    personality: float,
    values: float,
    lifestyle: float,
    compatibilities: List[str],
    challenges: List[str]
) -> str:
    """Generate human-readable compatibility explanation"""

    if overall >= 0.85:
        intro = "Exceptional compatibility! You share remarkable alignment across key areas."
    elif overall >= 0.75:
        intro = "Strong compatibility. You have solid foundation for a meaningful connection."
    elif overall >= 0.65:
        intro = "Good compatibility. You share several important qualities."
    elif overall >= 0.50:
        intro = "Moderate compatibility. You have some things in common, with room to grow."
    else:
        intro = "You have different perspectives which could be challenging but also enriching."

    details = []
    if personality >= 0.75:
        details.append("Your personalities complement each other well")
    if values >= 0.75:
        details.append("You share important life values")
    if lifestyle >= 0.75:
        details.append("Your lifestyles are well-aligned")

    if compatibilities:
        details.append(f"Key strengths: {', '.join(compatibilities[:3])}")

    if challenges:
        details.append(f"Areas for awareness: {', '.join(challenges[:2])}")

    return f"{intro} {'. '.join(details)}."


# ============ Conversation Starters ============

def generate_conversation_starters(a: UserProfile, b: UserProfile, compat: CompatibilityResult) -> List[str]:
    """Generate personalized conversation starters based on compatibility"""
    starters = []

    # Interest-based starters
    common_interests = set(a.interests) & set(b.interests)
    if common_interests:
        # Would need interest name mapping in production
        starters.append("I noticed we share some hobbies! What got you into them?")

    # Personality-based starters
    if a.openness > 70 and b.openness > 70:
        starters.append("What's the most interesting thing you've learned recently?")
        starters.append("If you could travel anywhere tomorrow, where would you go?")

    if a.extraversion > 60 and b.extraversion > 60:
        starters.append("What's your ideal weekend look like?")
    elif a.extraversion < 40 and b.extraversion < 40:
        starters.append("What's your favorite way to unwind after a long day?")

    if a.conscientiousness > 60 and b.conscientiousness > 60:
        starters.append("What's something you're working toward right now?")

    # Values-based starters
    if "career_importance" in a.values and "career_importance" in b.values:
        if a.values.get("career_importance", 0) > 7 and b.values.get("career_importance", 0) > 7:
            starters.append("What do you love most about what you do?")

    if "adventure_importance" in a.values and "adventure_importance" in b.values:
        if a.values.get("adventure_importance", 0) > 7 and b.values.get("adventure_importance", 0) > 7:
            starters.append("What's been your best adventure so far?")

    # General good starters
    default_starters = [
        "What's something that made you smile today?",
        "I'd love to hear the story behind your photos!",
        "What are you most passionate about right now?",
    ]

    # Fill up to 5 starters
    while len(starters) < 5 and default_starters:
        starters.append(default_starters.pop(0))

    return starters[:5]


# ============ API Endpoints ============

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "ai-matching-service"}


@app.post("/compatibility", response_model=CompatibilityResult)
async def calculate_compatibility(request: CompatibilityRequest):
    """Calculate detailed compatibility between two users"""
    try:
        result = calculate_full_compatibility(request.user_a, request.user_b)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/matches/recommend", response_model=List[MatchRecommendation])
async def recommend_matches(request: MatchRequest):
    """Recommend top matches from candidate pool"""
    try:
        user = request.user
        candidates = [c for c in request.candidates if c.user_id not in request.excluded_user_ids]

        if not candidates:
            return []

        # Calculate compatibility with each candidate
        scored_candidates = []
        for candidate in candidates:
            try:
                compat = calculate_full_compatibility(user, candidate)
                starters = generate_conversation_starters(user, candidate, compat)

                scored_candidates.append({
                    "candidate": candidate,
                    "compatibility": compat,
                    "starters": starters
                })
            except Exception:
                continue

        # Sort by overall score descending
        scored_candidates.sort(
            key=lambda x: x["compatibility"].overall_score,
            reverse=True
        )

        # Return top N
        recommendations = []
        for sc in scored_candidates[:request.limit]:
            recommendations.append(MatchRecommendation(
                user_id=sc["candidate"].user_id,
                uuid=sc["candidate"].uuid,
                compatibility_score=sc["compatibility"].overall_score,
                match_reasons=sc["compatibility"].top_compatibilities[:3],
                conversation_starters=sc["starters"]
            ))

        return recommendations

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/conversation-starters")
async def get_conversation_starters(request: ConversationStarterRequest):
    """Generate conversation starters for a match"""
    try:
        compat = calculate_full_compatibility(request.user_a, request.user_b)
        starters = generate_conversation_starters(request.user_a, request.user_b, compat)
        return {"starters": starters}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/embedding/generate")
async def generate_embedding(profile: UserProfile):
    """Generate and cache user embedding"""
    try:
        embedding = generate_user_embedding(profile)

        # Cache in Redis if available
        if REDIS_AVAILABLE:
            redis_client.setex(
                f"user_embedding:{profile.user_id}",
                3600 * 24,  # 24 hour cache
                json.dumps(embedding.tolist())
            )

        return {
            "user_id": profile.user_id,
            "embedding_dim": len(embedding),
            "cached": REDIS_AVAILABLE
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/embedding/{user_id}")
async def get_embedding(user_id: int):
    """Retrieve cached user embedding"""
    if not REDIS_AVAILABLE:
        raise HTTPException(status_code=503, detail="Redis not available")

    embedding_json = redis_client.get(f"user_embedding:{user_id}")
    if not embedding_json:
        raise HTTPException(status_code=404, detail="Embedding not found")

    embedding = json.loads(embedding_json)
    return {"user_id": user_id, "embedding": embedding}


@app.post("/similar-users")
async def find_similar_users(request: Dict[str, Any]):
    """Find users with similar embeddings using cosine similarity"""
    try:
        user_id = request.get("user_id")
        candidate_ids = request.get("candidate_ids", [])
        top_k = request.get("top_k", 10)

        if not REDIS_AVAILABLE:
            raise HTTPException(status_code=503, detail="Redis not available for similarity search")

        # Get user embedding
        user_emb_json = redis_client.get(f"user_embedding:{user_id}")
        if not user_emb_json:
            raise HTTPException(status_code=404, detail="User embedding not found")

        user_emb = np.array(json.loads(user_emb_json)).reshape(1, -1)

        # Get candidate embeddings
        similarities = []
        for cid in candidate_ids:
            cand_emb_json = redis_client.get(f"user_embedding:{cid}")
            if cand_emb_json:
                cand_emb = np.array(json.loads(cand_emb_json)).reshape(1, -1)
                sim = cosine_similarity(user_emb, cand_emb)[0][0]
                similarities.append({"user_id": cid, "similarity": float(sim)})

        # Sort and return top K
        similarities.sort(key=lambda x: x["similarity"], reverse=True)
        return {"similar_users": similarities[:top_k]}

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)

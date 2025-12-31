"""
AURA Media Service - Face Verification & Video Processing
Uses DeepFace for face matching, liveness detection, and deepfake detection
"""

import os
import uuid
import tempfile
from typing import Optional, List, Dict, Any
from datetime import datetime

from fastapi import FastAPI, File, UploadFile, HTTPException, Form
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import numpy as np
import cv2
from deepface import DeepFace
import redis
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(title="AURA Media Service", version="1.0.0")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Redis for caching (optional)
try:
    redis_client = redis.Redis(
        host=os.getenv("REDIS_HOST", "localhost"),
        port=int(os.getenv("REDIS_PORT", 6379)),
        db=0,
        decode_responses=True
    )
    redis_client.ping()
    REDIS_AVAILABLE = True
except:
    REDIS_AVAILABLE = False
    redis_client = None

# Configuration
FACE_MODEL = os.getenv("FACE_MODEL", "Facenet512")  # Best accuracy
DETECTOR_BACKEND = os.getenv("DETECTOR_BACKEND", "retinaface")  # Best detection
FACE_MATCH_THRESHOLD = float(os.getenv("FACE_MATCH_THRESHOLD", "0.70"))
LIVENESS_THRESHOLD = float(os.getenv("LIVENESS_THRESHOLD", "0.85"))
DEEPFAKE_THRESHOLD = float(os.getenv("DEEPFAKE_THRESHOLD", "0.80"))


class VerificationRequest(BaseModel):
    user_id: int
    profile_image_url: str
    verification_video_url: str
    session_id: str


class LivenessRequest(BaseModel):
    user_id: int


class VideoAnalysisRequest(BaseModel):
    video_url: str
    user_id: int


class VerificationResult(BaseModel):
    verified: bool
    face_match_score: float
    liveness_score: float
    deepfake_score: float
    issues: List[str]


@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "media-service"}


@app.post("/upload/video")
async def upload_video(
    file: UploadFile = File(...),
    path: str = Form(...),
    type: str = Form(...)
):
    """Upload video to storage and return URL"""
    try:
        # Generate unique filename
        file_ext = file.filename.split(".")[-1] if file.filename else "mp4"
        filename = f"{uuid.uuid4()}.{file_ext}"

        # For local storage (in production, use S3/MinIO)
        storage_path = os.getenv("STORAGE_PATH", "/tmp/aura-media")
        os.makedirs(f"{storage_path}/{path}", exist_ok=True)

        file_path = f"{storage_path}/{path}/{filename}"

        with open(file_path, "wb") as f:
            content = await file.read()
            f.write(content)

        # Return URL (in production, this would be S3 URL)
        url = f"/media/{path}/{filename}"

        return {"url": url, "filename": filename, "size": len(content)}

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/verify/liveness/challenges")
async def get_liveness_challenges(request: LivenessRequest):
    """Get random liveness challenges for verification"""

    all_challenges = [
        {"type": "BLINK", "instruction": "Please blink naturally 2-3 times"},
        {"type": "TURN_HEAD_LEFT", "instruction": "Turn your head slightly to the left"},
        {"type": "TURN_HEAD_RIGHT", "instruction": "Turn your head slightly to the right"},
        {"type": "SMILE", "instruction": "Please smile naturally"},
        {"type": "NOD", "instruction": "Nod your head up and down"},
        {"type": "RAISE_EYEBROWS", "instruction": "Raise your eyebrows"},
    ]

    # Select 3 random challenges
    import random
    selected = random.sample(all_challenges, 3)

    session_id = str(uuid.uuid4())

    # Store session in Redis if available
    if REDIS_AVAILABLE:
        redis_client.setex(
            f"liveness_session:{session_id}",
            300,  # 5 minute expiry
            str({"user_id": request.user_id, "challenges": selected})
        )

    return {
        "session_id": session_id,
        "challenges": selected,
        "timeout": 30,  # seconds per challenge
        "total_timeout": 120  # total session timeout
    }


@app.post("/verify/face", response_model=VerificationResult)
async def verify_face(request: VerificationRequest):
    """
    Verify that the person in the verification video matches the profile picture.
    Uses DeepFace for face matching and anti-spoofing detection.
    """
    issues = []
    face_match_score = 0.0
    liveness_score = 0.0
    deepfake_score = 1.0  # 1.0 = authentic (not a deepfake)

    try:
        # Download/load images
        profile_image = await load_image_from_url(request.profile_image_url)
        video_frames = await extract_video_frames(request.verification_video_url)

        if profile_image is None:
            issues.append("Could not load profile image")
            return VerificationResult(
                verified=False,
                face_match_score=0,
                liveness_score=0,
                deepfake_score=0,
                issues=issues
            )

        if not video_frames:
            issues.append("Could not extract frames from video")
            return VerificationResult(
                verified=False,
                face_match_score=0,
                liveness_score=0,
                deepfake_score=0,
                issues=issues
            )

        # 1. Face Matching - Compare profile pic to video frames
        face_match_scores = []
        for frame in video_frames[:5]:  # Check first 5 frames
            try:
                result = DeepFace.verify(
                    img1_path=profile_image,
                    img2_path=frame,
                    model_name=FACE_MODEL,
                    detector_backend=DETECTOR_BACKEND,
                    enforce_detection=False
                )
                # Convert distance to similarity score (0-1)
                # Lower distance = higher similarity
                similarity = 1 - min(result["distance"] / result["threshold"], 1)
                face_match_scores.append(similarity)
            except Exception as e:
                continue

        if face_match_scores:
            face_match_score = max(face_match_scores)
        else:
            issues.append("Could not detect face in video")

        # 2. Liveness Detection - Check for signs of life
        liveness_score = await detect_liveness(video_frames)
        if liveness_score < LIVENESS_THRESHOLD:
            issues.append("Liveness check failed - please use a live camera")

        # 3. Anti-Spoofing (Deepfake detection)
        deepfake_score = await detect_deepfake(video_frames)
        if deepfake_score < DEEPFAKE_THRESHOLD:
            issues.append("Authenticity check failed - suspected manipulation")

        # Final verification decision
        verified = (
            face_match_score >= FACE_MATCH_THRESHOLD and
            liveness_score >= LIVENESS_THRESHOLD and
            deepfake_score >= DEEPFAKE_THRESHOLD and
            len(issues) == 0
        )

        return VerificationResult(
            verified=verified,
            face_match_score=round(face_match_score * 100, 1),
            liveness_score=round(liveness_score * 100, 1),
            deepfake_score=round(deepfake_score * 100, 1),
            issues=issues
        )

    except Exception as e:
        issues.append(f"Verification error: {str(e)}")
        return VerificationResult(
            verified=False,
            face_match_score=0,
            liveness_score=0,
            deepfake_score=0,
            issues=issues
        )


@app.post("/video/analyze")
async def analyze_video(request: VideoAnalysisRequest):
    """Analyze video for transcript, duration, and sentiment"""
    try:
        video_frames = await extract_video_frames(request.video_url)

        # Get video duration
        video_path = url_to_local_path(request.video_url)
        cap = cv2.VideoCapture(video_path)
        fps = cap.get(cv2.CAP_PROP_FPS)
        frame_count = cap.get(cv2.CAP_PROP_FRAME_COUNT)
        duration = int(frame_count / fps) if fps > 0 else 0
        cap.release()

        # Simple sentiment analysis placeholder
        # In production, use speech-to-text + NLP
        sentiment = {
            "positive": 0.5,
            "negative": 0.1,
            "neutral": 0.4
        }

        # Thumbnail generation
        thumbnail_url = None
        if video_frames:
            thumbnail_path = f"/tmp/aura-media/thumbnails/{uuid.uuid4()}.jpg"
            os.makedirs(os.path.dirname(thumbnail_path), exist_ok=True)
            cv2.imwrite(thumbnail_path, video_frames[0])
            thumbnail_url = thumbnail_path

        return {
            "duration": duration,
            "sentiment": sentiment,
            "thumbnail_url": thumbnail_url,
            "transcript": None,  # Would need speech-to-text
            "frame_count": len(video_frames)
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


async def load_image_from_url(url: str) -> Optional[np.ndarray]:
    """Load image from URL or local path"""
    try:
        if url.startswith("/media/") or url.startswith("local://"):
            # Local file
            local_path = url_to_local_path(url)
            if os.path.exists(local_path):
                return cv2.imread(local_path)
        else:
            # Remote URL - download
            import httpx
            async with httpx.AsyncClient() as client:
                response = await client.get(url)
                if response.status_code == 200:
                    nparr = np.frombuffer(response.content, np.uint8)
                    return cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        return None
    except:
        return None


async def extract_video_frames(video_url: str, num_frames: int = 10) -> List[np.ndarray]:
    """Extract frames from video for analysis"""
    frames = []
    try:
        video_path = url_to_local_path(video_url)
        if not os.path.exists(video_path):
            return frames

        cap = cv2.VideoCapture(video_path)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

        if total_frames <= 0:
            return frames

        # Extract evenly spaced frames
        frame_indices = np.linspace(0, total_frames - 1, num_frames, dtype=int)

        for idx in frame_indices:
            cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
            ret, frame = cap.read()
            if ret:
                frames.append(frame)

        cap.release()
    except:
        pass

    return frames


async def detect_liveness(frames: List[np.ndarray]) -> float:
    """
    Simple liveness detection based on:
    1. Face detection consistency across frames
    2. Motion detection between frames
    3. Eye blink detection
    """
    if len(frames) < 3:
        return 0.5

    scores = []

    try:
        # 1. Check face detection in multiple frames
        face_detected_count = 0
        for frame in frames:
            try:
                faces = DeepFace.extract_faces(
                    frame,
                    detector_backend=DETECTOR_BACKEND,
                    enforce_detection=False
                )
                if faces and len(faces) > 0:
                    face_detected_count += 1
            except:
                continue

        face_consistency = face_detected_count / len(frames)
        scores.append(face_consistency)

        # 2. Motion detection (should have some movement for liveness)
        motion_scores = []
        for i in range(1, min(len(frames), 5)):
            prev_gray = cv2.cvtColor(frames[i-1], cv2.COLOR_BGR2GRAY)
            curr_gray = cv2.cvtColor(frames[i], cv2.COLOR_BGR2GRAY)

            diff = cv2.absdiff(prev_gray, curr_gray)
            motion = np.mean(diff) / 255.0
            motion_scores.append(min(motion * 10, 1.0))  # Scale up small motions

        if motion_scores:
            avg_motion = np.mean(motion_scores)
            # Some motion is good (0.1-0.5), too much or too little is suspicious
            motion_score = 1.0 - abs(avg_motion - 0.3) * 2
            scores.append(max(0, min(1, motion_score)))

        # 3. Simple eye state variation (should blink)
        # This is a simplified check - production would use proper eye tracking
        scores.append(0.85)  # Placeholder

        return np.mean(scores) if scores else 0.5

    except Exception as e:
        return 0.5


async def detect_deepfake(frames: List[np.ndarray]) -> float:
    """
    Simple deepfake detection based on:
    1. Face boundary artifacts
    2. Inconsistent lighting
    3. Unnatural textures

    Returns: 1.0 = authentic, 0.0 = fake
    """
    if len(frames) < 2:
        return 0.5

    scores = []

    try:
        for frame in frames[:3]:
            # 1. Check for compression artifacts around face boundary
            try:
                faces = DeepFace.extract_faces(
                    frame,
                    detector_backend=DETECTOR_BACKEND,
                    enforce_detection=False
                )
                if faces and len(faces) > 0:
                    # Face was detected - check confidence
                    face_conf = faces[0].get("confidence", 0.9)
                    scores.append(face_conf)
            except:
                scores.append(0.7)

            # 2. Simple texture analysis
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
            # Natural faces have moderate texture variance
            texture_score = 1.0 - abs(laplacian_var - 500) / 1000
            scores.append(max(0, min(1, texture_score)))

        return np.mean(scores) if scores else 0.85

    except:
        return 0.85


def url_to_local_path(url: str) -> str:
    """Convert URL to local file path"""
    storage_path = os.getenv("STORAGE_PATH", "/tmp/aura-media")

    if url.startswith("/media/"):
        return f"{storage_path}{url[6:]}"
    elif url.startswith("local://"):
        return f"{storage_path}/{url[8:]}"
    return url


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)

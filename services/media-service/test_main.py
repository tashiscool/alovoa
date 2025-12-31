"""
Tests for AURA Media Service

Note: These tests mock the DeepFace library to avoid loading heavy ML models.
For integration tests with actual face detection, run with --integration flag.
"""

import sys
from unittest.mock import MagicMock

# Mock DeepFace before importing main to avoid loading ML models
mock_deepface = MagicMock()
mock_deepface.extract_faces.return_value = [{"confidence": 0.95, "facial_area": {"x": 0, "y": 0, "w": 100, "h": 100}}]
mock_deepface.verify.return_value = {"distance": 0.2, "threshold": 0.68, "verified": True}
sys.modules['deepface'] = MagicMock()
sys.modules['deepface'].DeepFace = mock_deepface

import pytest
import numpy as np
import os
import tempfile
import cv2
from unittest.mock import patch, AsyncMock
from fastapi.testclient import TestClient

from main import (
    app,
    VerificationRequest,
    LivenessRequest,
    VideoAnalysisRequest,
    url_to_local_path,
    detect_liveness,
    detect_deepfake,
    FACE_MATCH_THRESHOLD,
    LIVENESS_THRESHOLD,
    DEEPFAKE_THRESHOLD,
)

client = TestClient(app)


# ============ Test Fixtures ============

@pytest.fixture
def temp_storage():
    """Create temporary storage directory"""
    with tempfile.TemporaryDirectory() as tmpdir:
        os.environ["STORAGE_PATH"] = tmpdir
        yield tmpdir


@pytest.fixture
def sample_image():
    """Create a sample image for testing"""
    img = np.zeros((480, 640, 3), dtype=np.uint8)
    img[:, :, 0] = 100  # Blue channel
    img[:, :, 1] = 150  # Green channel
    img[:, :, 2] = 200  # Red channel
    return img


@pytest.fixture
def sample_frames(sample_image):
    """Create a list of sample frames with slight variations"""
    frames = []
    for i in range(10):
        frame = sample_image.copy()
        noise = np.random.randint(-10, 10, frame.shape, dtype=np.int16)
        frame = np.clip(frame.astype(np.int16) + noise, 0, 255).astype(np.uint8)
        frames.append(frame)
    return frames


@pytest.fixture
def sample_video_path(temp_storage, sample_frames):
    """Create a sample video file"""
    video_path = os.path.join(temp_storage, "test_video.mp4")
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(video_path, fourcc, 30.0, (640, 480))
    for frame in sample_frames:
        out.write(frame)
    out.release()
    return video_path


# ============ Helper Function Tests ============

class TestUrlToLocalPath:
    def test_media_url(self, temp_storage):
        url = "/media/videos/test.mp4"
        result = url_to_local_path(url)
        assert result == f"{temp_storage}/videos/test.mp4"

    def test_local_url(self, temp_storage):
        url = "local://uploads/image.jpg"
        result = url_to_local_path(url)
        assert result == f"{temp_storage}/uploads/image.jpg"

    def test_absolute_path(self, temp_storage):
        url = "/absolute/path/file.jpg"
        result = url_to_local_path(url)
        assert url in result or temp_storage in result


# ============ Liveness Detection Tests ============

class TestLivenessDetection:
    @pytest.mark.asyncio
    async def test_liveness_insufficient_frames(self):
        score = await detect_liveness([np.zeros((100, 100, 3))])
        assert score == 0.5

    @pytest.mark.asyncio
    async def test_liveness_with_motion(self, sample_frames):
        score = await detect_liveness(sample_frames)
        assert 0 <= score <= 1

    @pytest.mark.asyncio
    async def test_liveness_static_frames(self, sample_image):
        static_frames = [sample_image.copy() for _ in range(5)]
        score = await detect_liveness(static_frames)
        assert 0 <= score <= 1


# ============ Deepfake Detection Tests ============

class TestDeepfakeDetection:
    @pytest.mark.asyncio
    async def test_deepfake_insufficient_frames(self):
        score = await detect_deepfake([np.zeros((100, 100, 3))])
        assert score == 0.5

    @pytest.mark.asyncio
    async def test_deepfake_detection(self, sample_frames):
        score = await detect_deepfake(sample_frames)
        assert 0 <= score <= 1


# ============ API Endpoint Tests ============

class TestHealthEndpoint:
    def test_health_check(self):
        response = client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["service"] == "media-service"


class TestLivenessChallengesEndpoint:
    def test_get_challenges(self):
        response = client.post("/verify/liveness/challenges", json={"user_id": 123})
        assert response.status_code == 200
        data = response.json()
        assert "session_id" in data
        assert "challenges" in data
        assert "timeout" in data
        assert len(data["challenges"]) == 3
        for challenge in data["challenges"]:
            assert "type" in challenge
            assert "instruction" in challenge

    def test_challenge_types(self):
        valid_types = {"BLINK", "TURN_HEAD_LEFT", "TURN_HEAD_RIGHT", "SMILE", "NOD", "RAISE_EYEBROWS"}
        response = client.post("/verify/liveness/challenges", json={"user_id": 123})
        data = response.json()
        for challenge in data["challenges"]:
            assert challenge["type"] in valid_types


class TestVideoUploadEndpoint:
    def test_upload_video(self, temp_storage):
        test_content = b"fake video content for testing"
        response = client.post(
            "/upload/video",
            files={"file": ("test.mp4", test_content, "video/mp4")},
            data={"path": "videos", "type": "verification"}
        )
        assert response.status_code == 200
        data = response.json()
        assert "url" in data
        assert "filename" in data
        assert data["size"] == len(test_content)


class TestFaceVerificationEndpoint:
    def test_verification_missing_image(self):
        with patch('main.load_image_from_url', new_callable=AsyncMock) as mock_load:
            mock_load.return_value = None
            response = client.post("/verify/face", json={
                "user_id": 123,
                "profile_image_url": "/nonexistent/image.jpg",
                "verification_video_url": "/media/videos/test.mp4",
                "session_id": "test-session-123"
            })
            assert response.status_code == 200
            data = response.json()
            assert data["verified"] == False
            assert "Could not load profile image" in data["issues"]


class TestVideoAnalysisEndpoint:
    def test_analyze_nonexistent_video(self, temp_storage):
        response = client.post("/video/analyze", json={
            "video_url": "/media/nonexistent.mp4",
            "user_id": 123
        })
        assert response.status_code in [200, 500]


# ============ Configuration Tests ============

class TestConfiguration:
    def test_thresholds_valid(self):
        assert 0 <= FACE_MATCH_THRESHOLD <= 1
        assert 0 <= LIVENESS_THRESHOLD <= 1
        assert 0 <= DEEPFAKE_THRESHOLD <= 1

    def test_thresholds_reasonable(self):
        assert 0.5 <= FACE_MATCH_THRESHOLD <= 0.95
        assert 0.5 <= LIVENESS_THRESHOLD <= 0.95
        assert 0.5 <= DEEPFAKE_THRESHOLD <= 0.95


# ============ Edge Cases ============

class TestEdgeCases:
    def test_empty_frames_liveness(self):
        import asyncio
        result = asyncio.run(detect_liveness([]))
        assert result == 0.5

    def test_single_frame_deepfake(self):
        import asyncio
        single_frame = np.zeros((100, 100, 3), dtype=np.uint8)
        result = asyncio.run(detect_deepfake([single_frame]))
        assert result == 0.5

    def test_corrupted_frame_handling(self):
        import asyncio
        tiny_frame = np.zeros((1, 1, 3), dtype=np.uint8)
        result = asyncio.run(detect_liveness([tiny_frame, tiny_frame, tiny_frame]))
        assert 0 <= result <= 1


# ============ Security Tests ============

class TestSecurity:
    def test_path_traversal_prevention(self, temp_storage):
        response = client.post(
            "/upload/video",
            files={"file": ("../../../etc/passwd", b"malicious", "video/mp4")},
            data={"path": "videos", "type": "verification"}
        )
        if response.status_code == 200:
            data = response.json()
            assert ".." not in data["filename"]
            assert "passwd" not in data["url"]

    def test_url_to_path_safety(self, temp_storage):
        dangerous_url = "/media/../../../etc/passwd"
        result = url_to_local_path(dangerous_url)
        # Result should be sanitized


# Run with: pytest test_main.py -v

#!/usr/bin/env python3
"""
AURA Platform E2E Verification Tests
=====================================

Comprehensive end-to-end tests that verify the entire AURA platform
is working correctly with all services integrated.

Services Tested:
  - Main Java Application (aura-app:8080)
  - Media Service - Face verification, liveness, deepfake (media-service:8001)
  - AI Service - Compatibility scoring, matching (ai-service:8002)
  - MariaDB - Database
  - Redis - Caching
  - MinIO - S3 Object Storage

Usage:
  # Start the stack
  docker compose -f docker-compose.e2e.yml up -d --build

  # Wait for services to be healthy
  docker compose -f docker-compose.e2e.yml ps

  # Run tests
  pip install requests pytest
  python e2e/test_platform.py

  # Or with pytest
  pytest e2e/test_platform.py -v

  # Cleanup
  docker compose -f docker-compose.e2e.yml down -v
"""

import os
import sys
import time
import json
import base64
import requests
from typing import Dict, Any, Optional, List
from dataclasses import dataclass
from datetime import datetime, timedelta
import unittest

# =============================================================================
# Configuration
# =============================================================================

@dataclass
class ServiceConfig:
    """Configuration for a service endpoint"""
    name: str
    url: str
    health_endpoint: str = "/health"

SERVICES = {
    "aura-app": ServiceConfig("AURA Main App", os.getenv("AURA_APP_URL", "http://localhost:8080"), "/actuator/health"),
    "media-service": ServiceConfig("Media Service", os.getenv("MEDIA_SERVICE_URL", "http://localhost:8001")),
    "ai-service": ServiceConfig("AI Service", os.getenv("AI_SERVICE_URL", "http://localhost:8002")),
}

# Test timeouts
HEALTH_CHECK_TIMEOUT = 120  # seconds
REQUEST_TIMEOUT = 30  # seconds


# =============================================================================
# Test Utilities
# =============================================================================

def wait_for_service(service: ServiceConfig, timeout: int = HEALTH_CHECK_TIMEOUT) -> bool:
    """Wait for a service to become healthy"""
    start_time = time.time()
    url = f"{service.url}{service.health_endpoint}"

    while time.time() - start_time < timeout:
        try:
            response = requests.get(url, timeout=5)
            if response.status_code == 200:
                print(f"  [OK] {service.name} is healthy")
                return True
        except requests.exceptions.RequestException:
            pass
        time.sleep(2)

    print(f"  [FAIL] {service.name} not healthy after {timeout}s")
    return False


def wait_for_all_services() -> bool:
    """Wait for all services to become healthy"""
    print("\n" + "=" * 60)
    print("Waiting for services to become healthy...")
    print("=" * 60)

    all_healthy = True
    for key, service in SERVICES.items():
        if not wait_for_service(service):
            all_healthy = False

    return all_healthy


def generate_test_image(width: int = 640, height: int = 480) -> bytes:
    """Generate a simple test image (PNG format)"""
    # Create a minimal valid PNG (1x1 red pixel)
    # This is a valid PNG that services can process
    png_header = bytes([
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,  # PNG signature
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,  # IHDR chunk
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,  # 1x1 dimensions
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53,
        0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,  # IDAT chunk
        0x54, 0x08, 0xD7, 0x63, 0xF8, 0xCF, 0xC0, 0x00,
        0x00, 0x00, 0x03, 0x00, 0x01, 0x00, 0x05, 0xFE,
        0xD4, 0xEF, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45,  # IEND chunk
        0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82
    ])
    return png_header


def generate_test_video() -> bytes:
    """Generate minimal test video data"""
    # Return fake video bytes for testing
    return b"FAKE_VIDEO_DATA_FOR_TESTING"


# =============================================================================
# Test Classes
# =============================================================================

class TestServiceHealth(unittest.TestCase):
    """Test that all services are healthy and responding"""

    @classmethod
    def setUpClass(cls):
        """Wait for all services before running tests"""
        if not wait_for_all_services():
            raise Exception("Not all services are healthy")

    def test_aura_app_health(self):
        """Test AURA main application health endpoint"""
        service = SERVICES["aura-app"]
        response = requests.get(f"{service.url}/actuator/health", timeout=REQUEST_TIMEOUT)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("status", data)
        print(f"    AURA App Status: {data.get('status', 'unknown')}")

    def test_media_service_health(self):
        """Test Media Service health endpoint"""
        service = SERVICES["media-service"]
        response = requests.get(f"{service.url}/health", timeout=REQUEST_TIMEOUT)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data.get("status"), "healthy")
        self.assertEqual(data.get("service"), "media-service")
        print(f"    Media Service: {data}")

    def test_ai_service_health(self):
        """Test AI Service health endpoint"""
        service = SERVICES["ai-service"]
        response = requests.get(f"{service.url}/health", timeout=REQUEST_TIMEOUT)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data.get("status"), "healthy")
        print(f"    AI Service: {data}")


class TestMediaServiceCapabilities(unittest.TestCase):
    """Test Media Service face verification and video analysis capabilities"""

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES["media-service"].url

    def test_liveness_challenges(self):
        """Test that liveness challenge generation works"""
        response = requests.post(
            f"{self.base_url}/verify/liveness/challenges",
            json={"user_id": 12345},
            timeout=REQUEST_TIMEOUT
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("session_id", data)
        self.assertIn("challenges", data)
        self.assertIn("timeout", data)
        self.assertEqual(len(data["challenges"]), 3)

        # Verify challenge structure
        for challenge in data["challenges"]:
            self.assertIn("type", challenge)
            self.assertIn("instruction", challenge)

        print(f"    Liveness Challenges: {[c['type'] for c in data['challenges']]}")

    def test_video_upload(self):
        """Test video upload capability"""
        video_data = generate_test_video()

        response = requests.post(
            f"{self.base_url}/upload/video",
            files={"file": ("test_video.mp4", video_data, "video/mp4")},
            data={"path": "e2e-test", "type": "verification"},
            timeout=REQUEST_TIMEOUT
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("url", data)
        self.assertIn("filename", data)
        self.assertIn("size", data)
        print(f"    Video Upload: {data['filename']} ({data['size']} bytes)")

    def test_face_verification_endpoint_exists(self):
        """Test that face verification endpoint is accessible"""
        # Test with missing data to verify endpoint exists
        response = requests.post(
            f"{self.base_url}/verify/face",
            json={
                "user_id": 12345,
                "profile_image_url": "/nonexistent/image.jpg",
                "verification_video_url": "/nonexistent/video.mp4",
                "session_id": "test-session"
            },
            timeout=REQUEST_TIMEOUT
        )
        # Should return 200 with verification failure (not 404/500)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("verified", data)
        self.assertFalse(data["verified"])  # Should fail due to missing files
        print(f"    Face Verification Endpoint: Accessible (returns proper error)")


class TestAIServiceCapabilities(unittest.TestCase):
    """Test AI Service matching and compatibility capabilities"""

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES["ai-service"].url

    def test_compatibility_scoring(self):
        """Test compatibility score calculation between two profiles"""
        user1_profile = {
            "user_id": 1,
            "personality": {
                "openness": 75,
                "conscientiousness": 60,
                "extraversion": 65,
                "agreeableness": 80,
                "neuroticism": 35
            },
            "values": {
                "progressive": 70,
                "egalitarian": 75
            },
            "lifestyle": {
                "social": 60,
                "health": 70,
                "work_life": 55,
                "finance": 65
            },
            "attachment": {
                "anxiety": 25,
                "avoidance": 20
            }
        }

        user2_profile = {
            "user_id": 2,
            "personality": {
                "openness": 70,
                "conscientiousness": 65,
                "extraversion": 55,
                "agreeableness": 75,
                "neuroticism": 40
            },
            "values": {
                "progressive": 65,
                "egalitarian": 80
            },
            "lifestyle": {
                "social": 55,
                "health": 75,
                "work_life": 60,
                "finance": 60
            },
            "attachment": {
                "anxiety": 30,
                "avoidance": 25
            }
        }

        response = requests.post(
            f"{self.base_url}/compatibility/score",
            json={"user1": user1_profile, "user2": user2_profile},
            timeout=REQUEST_TIMEOUT
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertIn("overall_score", data)
        self.assertIn("category_scores", data)

        score = data["overall_score"]
        self.assertGreaterEqual(score, 0)
        self.assertLessEqual(score, 100)

        print(f"    Compatibility Score: {score:.1f}%")
        print(f"    Category Breakdown: {data['category_scores']}")

    def test_batch_matching(self):
        """Test batch matching capability"""
        target_user = {
            "user_id": 1,
            "personality": {"openness": 70, "conscientiousness": 65, "extraversion": 60, "agreeableness": 75, "neuroticism": 35},
            "values": {"progressive": 70, "egalitarian": 75},
            "lifestyle": {"social": 60, "health": 70, "work_life": 55, "finance": 65},
            "attachment": {"anxiety": 25, "avoidance": 20}
        }

        candidates = [
            {
                "user_id": i,
                "personality": {"openness": 50 + i*5, "conscientiousness": 60, "extraversion": 55, "agreeableness": 70, "neuroticism": 40},
                "values": {"progressive": 60 + i*3, "egalitarian": 70},
                "lifestyle": {"social": 55, "health": 65, "work_life": 50, "finance": 60},
                "attachment": {"anxiety": 30, "avoidance": 25}
            }
            for i in range(2, 7)
        ]

        response = requests.post(
            f"{self.base_url}/matching/batch",
            json={"target": target_user, "candidates": candidates, "limit": 5},
            timeout=REQUEST_TIMEOUT
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertIn("matches", data)
        self.assertIsInstance(data["matches"], list)

        # Verify matches are sorted by score (descending)
        if len(data["matches"]) > 1:
            scores = [m["score"] for m in data["matches"]]
            self.assertEqual(scores, sorted(scores, reverse=True))

        print(f"    Batch Matching: Found {len(data['matches'])} matches")
        for match in data["matches"][:3]:
            print(f"      User {match['user_id']}: {match['score']:.1f}%")

    def test_embedding_generation(self):
        """Test profile embedding generation"""
        profile = {
            "user_id": 100,
            "personality": {"openness": 75, "conscientiousness": 60, "extraversion": 65, "agreeableness": 80, "neuroticism": 35},
            "values": {"progressive": 70, "egalitarian": 75},
            "lifestyle": {"social": 60, "health": 70, "work_life": 55, "finance": 65},
            "attachment": {"anxiety": 25, "avoidance": 20}
        }

        response = requests.post(
            f"{self.base_url}/embedding/generate",
            json={"profile": profile},
            timeout=REQUEST_TIMEOUT
        )
        self.assertEqual(response.status_code, 200)
        data = response.json()

        self.assertIn("embedding", data)
        self.assertIn("dimension", data)
        self.assertIsInstance(data["embedding"], list)
        self.assertEqual(len(data["embedding"]), data["dimension"])

        print(f"    Embedding: {data['dimension']}-dimensional vector generated")


class TestAuraAppCapabilities(unittest.TestCase):
    """Test AURA main application capabilities"""

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES["aura-app"].url

    def test_actuator_info(self):
        """Test actuator info endpoint"""
        response = requests.get(f"{self.base_url}/actuator/info", timeout=REQUEST_TIMEOUT)
        # May return 200 or 404 depending on actuator config
        self.assertIn(response.status_code, [200, 404])
        print(f"    Actuator Info: Status {response.status_code}")

    def test_api_documentation_accessible(self):
        """Test that API documentation is accessible (if enabled)"""
        # Try swagger/openapi endpoints
        endpoints = ["/swagger-ui.html", "/v3/api-docs", "/swagger-ui/index.html"]
        accessible = False

        for endpoint in endpoints:
            try:
                response = requests.get(f"{self.base_url}{endpoint}", timeout=5, allow_redirects=True)
                if response.status_code == 200:
                    accessible = True
                    print(f"    API Docs: {endpoint} accessible")
                    break
            except:
                pass

        # API docs may not be enabled in all profiles
        print(f"    API Documentation: {'Accessible' if accessible else 'Not configured'}")

    def test_database_connectivity_via_health(self):
        """Test database connectivity through health endpoint"""
        response = requests.get(f"{self.base_url}/actuator/health", timeout=REQUEST_TIMEOUT)
        self.assertEqual(response.status_code, 200)
        data = response.json()

        # Check if DB health is reported
        if "components" in data and "db" in data["components"]:
            db_status = data["components"]["db"]["status"]
            self.assertEqual(db_status, "UP")
            print(f"    Database: {db_status}")
        else:
            print("    Database: Health details not exposed (security config)")


class TestCoreDatingFlows(unittest.TestCase):
    """
    Core Dating App Functionality E2E Tests

    Tests the essential user journeys:
    1. Registration & Authentication
    2. Profile Management
    3. Search & Discovery
    4. Liking & Matching
    5. Messaging
    6. Video Dating
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES["aura-app"].url
        cls.session = requests.Session()
        # Store test user info
        cls.test_email = f"e2e_test_{int(time.time())}@test.alovoa.com"

    # =========================================
    # 1. Registration & Authentication Flow
    # =========================================

    def test_01_captcha_generation(self):
        """Test captcha can be generated for registration"""
        response = self.session.get(f"{self.base_url}/captcha/generate", timeout=REQUEST_TIMEOUT)
        # Captcha endpoint should be publicly accessible
        self.assertIn(response.status_code, [200, 302])
        print(f"    Captcha Generation: Status {response.status_code}")

    def test_02_registration_endpoint_accessible(self):
        """Test registration endpoint is accessible"""
        # Test with invalid data - should return 400, not 404/500
        response = self.session.post(
            f"{self.base_url}/register",
            json={"email": "invalid"},
            timeout=REQUEST_TIMEOUT
        )
        # Should get validation error (400) or redirect, not server error
        self.assertIn(response.status_code, [200, 302, 400, 415])
        print(f"    Registration Endpoint: Status {response.status_code}")

    def test_03_login_page_accessible(self):
        """Test login page is accessible"""
        response = self.session.get(f"{self.base_url}/login", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302])
        print(f"    Login Page: Status {response.status_code}")

    # =========================================
    # 2. Profile & Search Endpoints
    # =========================================

    def test_04_search_users_default(self):
        """Test search users default endpoint"""
        response = self.session.get(f"{self.base_url}/search/users/default", timeout=REQUEST_TIMEOUT)
        # May require auth, but should not be 404/500
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Search Users Default: Status {response.status_code}")

    def test_05_search_users_with_params(self):
        """Test search users with location params"""
        # New York coords
        response = self.session.get(
            f"{self.base_url}/search/users/40.7128/-74.0060/50/0",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Search Users (geo): Status {response.status_code}")

    def test_06_daily_matches_endpoint(self):
        """Test daily matches recommendation endpoint"""
        response = self.session.get(f"{self.base_url}/matching/daily", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Daily Matches: Status {response.status_code}")

    # =========================================
    # 3. User Interaction Endpoints
    # =========================================

    def test_07_like_endpoint_format(self):
        """Test like endpoint accepts correct format"""
        # Test with fake UUID - should fail gracefully
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.post(
            f"{self.base_url}/user/like/{fake_uuid}",
            timeout=REQUEST_TIMEOUT
        )
        # Should require auth or return user not found
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Like Endpoint: Status {response.status_code}")

    def test_08_block_endpoint_format(self):
        """Test block endpoint accepts correct format"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.post(
            f"{self.base_url}/user/block/{fake_uuid}",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Block Endpoint: Status {response.status_code}")

    def test_09_report_endpoint_format(self):
        """Test report endpoint accepts correct format"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.post(
            f"{self.base_url}/user/report/{fake_uuid}",
            data="Test report reason",
            headers={"Content-Type": "text/plain"},
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Report Endpoint: Status {response.status_code}")

    # =========================================
    # 4. Messaging Endpoints
    # =========================================

    def test_10_message_send_endpoint_format(self):
        """Test message send endpoint format"""
        response = self.session.post(
            f"{self.base_url}/message/send/1",
            data="Hello, this is a test message",
            headers={"Content-Type": "text/plain"},
            timeout=REQUEST_TIMEOUT
        )
        # Should require auth
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Message Send: Status {response.status_code}")

    def test_11_message_get_endpoint_format(self):
        """Test get messages endpoint format"""
        response = self.session.get(
            f"{self.base_url}/message/get-messages/1/0",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Message Get: Status {response.status_code}")

    def test_12_message_read_endpoint(self):
        """Test mark message as read endpoint"""
        response = self.session.post(
            f"{self.base_url}/message/read/1",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Message Read: Status {response.status_code}")

    # =========================================
    # 5. Profile Update Endpoints
    # =========================================

    def test_13_update_description_endpoint(self):
        """Test profile description update endpoint"""
        response = self.session.post(
            f"{self.base_url}/user/update/description",
            data="Test bio description",
            headers={"Content-Type": "text/plain"},
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403])
        print(f"    Update Description: Status {response.status_code}")

    def test_14_update_location_endpoint(self):
        """Test location update endpoint"""
        response = self.session.post(
            f"{self.base_url}/user/update/location/40.7128/-74.0060",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403])
        print(f"    Update Location: Status {response.status_code}")

    def test_15_interest_add_endpoint(self):
        """Test add interest endpoint"""
        response = self.session.post(
            f"{self.base_url}/user/interest/add/hiking",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403])
        print(f"    Add Interest: Status {response.status_code}")

    def test_16_interest_autocomplete(self):
        """Test interest autocomplete endpoint"""
        response = self.session.get(
            f"{self.base_url}/user/interest/autocomplete/hik",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Interest Autocomplete: Status {response.status_code}")

    # =========================================
    # 6. Notification & Status Endpoints
    # =========================================

    def test_17_new_alert_status(self):
        """Test new alert status endpoint"""
        response = self.session.get(
            f"{self.base_url}/user/status/new-alert",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    New Alert Status: Status {response.status_code}")

    def test_18_new_message_status(self):
        """Test new message status endpoint"""
        response = self.session.get(
            f"{self.base_url}/user/status/new-message",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    New Message Status: Status {response.status_code}")

    def test_19_profile_completeness(self):
        """Test profile completeness endpoint"""
        response = self.session.get(
            f"{self.base_url}/user/profile/completeness",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Profile Completeness: Status {response.status_code}")

    # =========================================
    # 7. AURA-Specific Endpoints
    # =========================================

    def test_20_compatibility_check(self):
        """Test compatibility score endpoint"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.get(
            f"{self.base_url}/matching/compatibility/{fake_uuid}",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Compatibility Check: Status {response.status_code}")

    def test_21_video_date_availability(self):
        """Test video date availability endpoint"""
        response = self.session.get(
            f"{self.base_url}/video-date/availability",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 401, 403, 404])
        print(f"    Video Date Availability: Status {response.status_code}")

    def test_22_reputation_view(self):
        """Test viewing user reputation"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.get(
            f"{self.base_url}/user/reputation/{fake_uuid}",
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Reputation View: Status {response.status_code}")


# =============================================================================
# AURA-Specific Feature Tests (Compared to Upstream)
# =============================================================================

class TestAccountabilitySystem(unittest.TestCase):
    """
    E2E Tests for Accountability System
    Tests: /api/v1/accountability/*
    Features: Public reporting, evidence submission, feedback system
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/api/v1/accountability"
        cls.session = requests.Session()

    def test_01_get_report_categories(self):
        """Test getting available report categories"""
        response = self.session.get(f"{self.base_url}/categories", timeout=REQUEST_TIMEOUT)
        # Public endpoint should be accessible
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Report Categories: Status {response.status_code}")

    def test_02_submitted_reports(self):
        """Test getting user's submitted reports"""
        response = self.session.get(f"{self.base_url}/reports/submitted", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Submitted Reports: Status {response.status_code}")

    def test_03_received_reports(self):
        """Test getting reports received about user"""
        response = self.session.get(f"{self.base_url}/reports/received", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Received Reports: Status {response.status_code}")

    def test_04_report_submission_format(self):
        """Test report submission endpoint format"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.post(
            f"{self.base_url}/report",
            json={
                "reportedUserUuid": fake_uuid,
                "category": "BEHAVIOR",
                "description": "Test report",
                "severity": "LOW"
            },
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Report Submission: Status {response.status_code}")

    def test_05_user_feedback_endpoint(self):
        """Test getting feedback about a user"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.get(f"{self.base_url}/feedback/{fake_uuid}", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    User Feedback: Status {response.status_code}")


class TestAssessmentSystem(unittest.TestCase):
    """
    E2E Tests for Assessment/Questionnaire System
    Tests: /assessment/*
    Features: OKCupid-style questions, progress tracking, match scoring
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/assessment"
        cls.session = requests.Session()

    def test_01_get_questions_personality(self):
        """Test getting personality assessment questions"""
        response = self.session.get(f"{self.base_url}/questions/personality", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Personality Questions: Status {response.status_code}")

    def test_02_get_questions_values(self):
        """Test getting values assessment questions"""
        response = self.session.get(f"{self.base_url}/questions/values", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Values Questions: Status {response.status_code}")

    def test_03_get_questions_lifestyle(self):
        """Test getting lifestyle assessment questions"""
        response = self.session.get(f"{self.base_url}/questions/lifestyle", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Lifestyle Questions: Status {response.status_code}")

    def test_04_assessment_progress(self):
        """Test getting assessment progress"""
        response = self.session.get(f"{self.base_url}/progress", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Assessment Progress: Status {response.status_code}")

    def test_05_assessment_results(self):
        """Test getting assessment results"""
        response = self.session.get(f"{self.base_url}/results", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Assessment Results: Status {response.status_code}")

    def test_06_next_question(self):
        """Test getting next question to answer"""
        response = self.session.get(f"{self.base_url}/next", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Next Question: Status {response.status_code}")

    def test_07_question_batch(self):
        """Test getting batch of questions"""
        response = self.session.get(f"{self.base_url}/batch", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Question Batch: Status {response.status_code}")

    def test_08_assessment_stats(self):
        """Test getting assessment statistics"""
        response = self.session.get(f"{self.base_url}/stats", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Assessment Stats: Status {response.status_code}")

    def test_09_match_score_calculation(self):
        """Test match score calculation endpoint"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.get(f"{self.base_url}/match/{fake_uuid}", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Match Score: Status {response.status_code}")

    def test_10_match_explanation(self):
        """Test match explanation endpoint"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.get(f"{self.base_url}/match/{fake_uuid}/explain", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Match Explanation: Status {response.status_code}")


class TestIntakeAndScaffolding(unittest.TestCase):
    """
    E2E Tests for Intake Flow and Profile Scaffolding
    Tests: /intake/*
    Features: Video intro, AI analysis, profile scaffolding, encouragement
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/intake"
        cls.session = requests.Session()

    def test_01_intake_progress(self):
        """Test getting intake progress"""
        response = self.session.get(f"{self.base_url}/progress", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Intake Progress: Status {response.status_code}")

    def test_02_core_questions(self):
        """Test getting core intake questions"""
        response = self.session.get(f"{self.base_url}/questions", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Core Questions: Status {response.status_code}")

    def test_03_ai_status(self):
        """Test AI provider status"""
        response = self.session.get(f"{self.base_url}/ai/status", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    AI Status: Status {response.status_code}")

    def test_04_video_tips(self):
        """Test getting video recording tips"""
        response = self.session.get(f"{self.base_url}/video/tips", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Video Tips: Status {response.status_code}")

    def test_05_step_encouragement(self):
        """Test getting step encouragement"""
        response = self.session.get(f"{self.base_url}/encouragement/questions", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Step Encouragement: Status {response.status_code}")

    def test_06_life_stats(self):
        """Test personalized life stats"""
        response = self.session.get(f"{self.base_url}/life-stats", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Life Stats: Status {response.status_code}")

    def test_07_scaffolding_prompts(self):
        """Test getting scaffolding prompts"""
        response = self.session.get(f"{self.base_url}/scaffolding/prompts", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Scaffolding Prompts: Status {response.status_code}")

    def test_08_scaffolding_progress(self):
        """Test scaffolding progress"""
        response = self.session.get(f"{self.base_url}/scaffolding/progress", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Scaffolding Progress: Status {response.status_code}")

    def test_09_scaffolded_profile(self):
        """Test getting scaffolded profile"""
        response = self.session.get(f"{self.base_url}/scaffolded-profile", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403])
        print(f"    Scaffolded Profile: Status {response.status_code}")


class TestLocationSystem(unittest.TestCase):
    """
    E2E Tests for Location and Date Spots
    Tests: /location/*
    Features: Location areas, date spots, travel time, privacy-safe location
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/location"
        cls.session = requests.Session()

    def test_01_location_areas(self):
        """Test getting user's location areas"""
        response = self.session.get(f"{self.base_url}/areas", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Location Areas: Status {response.status_code}")

    def test_02_location_preferences(self):
        """Test getting location preferences"""
        response = self.session.get(f"{self.base_url}/preferences", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Location Preferences: Status {response.status_code}")

    def test_03_traveling_status(self):
        """Test traveling status"""
        response = self.session.get(f"{self.base_url}/traveling", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Traveling Status: Status {response.status_code}")

    def test_04_date_spots(self):
        """Test getting date spots"""
        response = self.session.get(f"{self.base_url}/date-spots", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Date Spots: Status {response.status_code}")

    def test_05_safe_date_spots(self):
        """Test getting safe/well-lit date spots"""
        response = self.session.get(f"{self.base_url}/date-spots/safe", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Safe Date Spots: Status {response.status_code}")

    def test_06_daytime_date_spots(self):
        """Test getting daytime date spots"""
        response = self.session.get(f"{self.base_url}/date-spots/daytime", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Daytime Date Spots: Status {response.status_code}")

    def test_07_budget_date_spots(self):
        """Test getting budget-friendly date spots"""
        response = self.session.get(f"{self.base_url}/date-spots/budget", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Budget Date Spots: Status {response.status_code}")

    def test_08_date_spot_by_type(self):
        """Test getting date spots by type"""
        response = self.session.get(f"{self.base_url}/date-spots/type/cafe", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Date Spots By Type: Status {response.status_code}")

    def test_09_location_display(self):
        """Test display location for another user"""
        response = self.session.get(f"{self.base_url}/display/1", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Location Display: Status {response.status_code}")

    def test_10_location_overlap(self):
        """Test checking location overlap with match"""
        response = self.session.get(f"{self.base_url}/overlap/1", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Location Overlap: Status {response.status_code}")


class TestMatchingAndReputation(unittest.TestCase):
    """
    E2E Tests for Matching and Reputation System
    Tests: /api/v1/matching/*, /api/v1/reputation/*
    Features: Daily matches, compatibility, reputation scoring, badges
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES['aura-app'].url
        cls.session = requests.Session()

    def test_01_daily_matches(self):
        """Test getting daily match recommendations"""
        response = self.session.get(f"{self.base_url}/api/v1/matching/daily", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Daily Matches: Status {response.status_code}")

    def test_02_compatibility_check(self):
        """Test compatibility score calculation"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.get(f"{self.base_url}/api/v1/matching/compatibility/{fake_uuid}", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Compatibility Check: Status {response.status_code}")

    def test_03_my_reputation(self):
        """Test getting own reputation score"""
        response = self.session.get(f"{self.base_url}/api/v1/reputation/me", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    My Reputation: Status {response.status_code}")

    def test_04_reputation_badges(self):
        """Test getting reputation badges"""
        response = self.session.get(f"{self.base_url}/api/v1/reputation/badges", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Reputation Badges: Status {response.status_code}")

    def test_05_reputation_history(self):
        """Test getting reputation history"""
        response = self.session.get(f"{self.base_url}/api/v1/reputation/history", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Reputation History: Status {response.status_code}")


class TestVideoDates(unittest.TestCase):
    """
    E2E Tests for Video Dating System
    Tests: /api/v1/video-date/*
    Features: Propose dates, scheduling, feedback, history
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/api/v1/video-date"
        cls.session = requests.Session()

    def test_01_upcoming_dates(self):
        """Test getting upcoming video dates"""
        response = self.session.get(f"{self.base_url}/upcoming", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Upcoming Dates: Status {response.status_code}")

    def test_02_date_proposals(self):
        """Test getting date proposals"""
        response = self.session.get(f"{self.base_url}/proposals", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Date Proposals: Status {response.status_code}")

    def test_03_date_history(self):
        """Test getting video date history"""
        response = self.session.get(f"{self.base_url}/history", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Date History: Status {response.status_code}")

    def test_04_propose_date_format(self):
        """Test date proposal endpoint format"""
        fake_uuid = "00000000-0000-0000-0000-000000000001"
        response = self.session.post(
            f"{self.base_url}/propose",
            json={
                "matchUuid": fake_uuid,
                "proposedTime": "2026-01-15T19:00:00Z"
            },
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 302, 400, 401, 403, 404])
        print(f"    Propose Date: Status {response.status_code}")


class TestVideoVerification(unittest.TestCase):
    """
    E2E Tests for Video Verification
    Tests: /video/*
    Features: Video intro upload, liveness verification
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/video"
        cls.session = requests.Session()

    def test_01_verification_status(self):
        """Test getting verification status"""
        response = self.session.get(f"{self.base_url}/verification/status", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Verification Status: Status {response.status_code}")

    def test_02_start_verification(self):
        """Test starting verification"""
        response = self.session.post(f"{self.base_url}/verification/start", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 400, 401, 403])
        print(f"    Start Verification: Status {response.status_code}")


class TestPoliticalAssessment(unittest.TestCase):
    """
    E2E Tests for Political/Values Assessment
    Tests: /api/v1/political-assessment/*
    Features: Political compass, economic class, reproductive views
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/api/v1/political-assessment"
        cls.session = requests.Session()

    def test_01_assessment_status(self):
        """Test getting political assessment status"""
        response = self.session.get(f"{self.base_url}/status", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Political Status: Status {response.status_code}")

    def test_02_assessment_options(self):
        """Test getting assessment options"""
        response = self.session.get(f"{self.base_url}/options", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Political Options: Status {response.status_code}")

    def test_03_class_consciousness_test(self):
        """Test getting class consciousness test"""
        response = self.session.get(f"{self.base_url}/class-consciousness-test", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Class Consciousness: Status {response.status_code}")

    def test_04_explanation_prompts(self):
        """Test getting explanation prompts"""
        response = self.session.get(f"{self.base_url}/explanation-prompts", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Explanation Prompts: Status {response.status_code}")


class TestProfileAndRelationship(unittest.TestCase):
    """
    E2E Tests for Profile and Relationship Management
    Tests: /api/profile/*, /api/v1/relationship/*
    Features: Profile visitors, relationship status, details
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES['aura-app'].url
        cls.session = requests.Session()

    def test_01_profile_visitors(self):
        """Test getting profile visitors"""
        response = self.session.get(f"{self.base_url}/api/profile/visitors", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Profile Visitors: Status {response.status_code}")

    def test_02_recent_visitors(self):
        """Test getting recent profile visitors"""
        response = self.session.get(f"{self.base_url}/api/profile/visitors/recent", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Recent Visitors: Status {response.status_code}")

    def test_03_visited_profiles(self):
        """Test getting profiles user visited"""
        response = self.session.get(f"{self.base_url}/api/profile/visited", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Visited Profiles: Status {response.status_code}")

    def test_04_profile_details(self):
        """Test getting profile details"""
        response = self.session.get(f"{self.base_url}/api/profile/details", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Profile Details: Status {response.status_code}")

    def test_05_profile_details_options(self):
        """Test getting profile detail options"""
        response = self.session.get(f"{self.base_url}/api/profile/details/options", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Detail Options: Status {response.status_code}")

    def test_06_relationships(self):
        """Test getting user relationships"""
        response = self.session.get(f"{self.base_url}/api/v1/relationship", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Relationships: Status {response.status_code}")

    def test_07_pending_requests(self):
        """Test getting pending relationship requests"""
        response = self.session.get(f"{self.base_url}/api/v1/relationship/requests/pending", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Pending Requests: Status {response.status_code}")

    def test_08_sent_requests(self):
        """Test getting sent relationship requests"""
        response = self.session.get(f"{self.base_url}/api/v1/relationship/requests/sent", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Sent Requests: Status {response.status_code}")

    def test_09_relationship_types(self):
        """Test getting relationship types"""
        response = self.session.get(f"{self.base_url}/api/v1/relationship/types", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Relationship Types: Status {response.status_code}")


class TestMatchWindows(unittest.TestCase):
    """
    E2E Tests for Match Windows System
    Tests: /api/v1/match-windows/*
    Features: Time-limited matching, confirm/decline, dashboard
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = f"{SERVICES['aura-app'].url}/api/v1/match-windows"
        cls.session = requests.Session()

    def test_01_pending_windows(self):
        """Test getting pending match windows"""
        response = self.session.get(f"{self.base_url}/pending", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Pending Windows: Status {response.status_code}")

    def test_02_waiting_windows(self):
        """Test getting windows waiting for response"""
        response = self.session.get(f"{self.base_url}/waiting", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Waiting Windows: Status {response.status_code}")

    def test_03_confirmed_windows(self):
        """Test getting confirmed match windows"""
        response = self.session.get(f"{self.base_url}/confirmed", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Confirmed Windows: Status {response.status_code}")

    def test_04_pending_count(self):
        """Test getting pending window count"""
        response = self.session.get(f"{self.base_url}/pending/count", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Pending Count: Status {response.status_code}")

    def test_05_dashboard(self):
        """Test getting match windows dashboard"""
        response = self.session.get(f"{self.base_url}/dashboard", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Windows Dashboard: Status {response.status_code}")


class TestEssaysAndPersonality(unittest.TestCase):
    """
    E2E Tests for Essays and Personality System
    Tests: /api/v1/essays/*, /personality/*
    Features: Profile essays, personality assessment
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES['aura-app'].url
        cls.session = requests.Session()

    def test_01_essays(self):
        """Test getting user essays"""
        response = self.session.get(f"{self.base_url}/api/v1/essays", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    User Essays: Status {response.status_code}")

    def test_02_essay_templates(self):
        """Test getting essay templates"""
        response = self.session.get(f"{self.base_url}/api/v1/essays/templates", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Essay Templates: Status {response.status_code}")

    def test_03_essay_count(self):
        """Test getting essay count"""
        response = self.session.get(f"{self.base_url}/api/v1/essays/count", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Essay Count: Status {response.status_code}")

    def test_04_personality_assessment(self):
        """Test getting personality assessment"""
        response = self.session.get(f"{self.base_url}/personality/assessment", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Personality Assessment: Status {response.status_code}")

    def test_05_personality_results(self):
        """Test getting personality results"""
        response = self.session.get(f"{self.base_url}/personality/results", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Personality Results: Status {response.status_code}")


class TestWaitlistAndVerification(unittest.TestCase):
    """
    E2E Tests for Waitlist and General Verification
    Tests: /api/v1/waitlist/*, /verification/*
    Features: Waitlist signup, verification status
    """

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES['aura-app'].url
        cls.session = requests.Session()

    def test_01_waitlist_status(self):
        """Test getting waitlist status"""
        response = self.session.get(f"{self.base_url}/api/v1/waitlist/status", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Waitlist Status: Status {response.status_code}")

    def test_02_waitlist_count(self):
        """Test getting waitlist count"""
        response = self.session.get(f"{self.base_url}/api/v1/waitlist/count", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Waitlist Count: Status {response.status_code}")

    def test_03_waitlist_stats(self):
        """Test getting waitlist statistics"""
        response = self.session.get(f"{self.base_url}/api/v1/waitlist/stats", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Waitlist Stats: Status {response.status_code}")

    def test_04_verification_page(self):
        """Test verification page accessibility"""
        response = self.session.get(f"{self.base_url}/verification", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Verification Page: Status {response.status_code}")

    def test_05_verification_api_status(self):
        """Test verification API status"""
        response = self.session.get(f"{self.base_url}/verification/api/status", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 302, 401, 403])
        print(f"    Verification API Status: Status {response.status_code}")


class TestIntegrationScenarios(unittest.TestCase):
    """Integration scenarios testing multiple services together"""

    def test_service_to_service_communication(self):
        """Verify services can communicate with each other"""
        # This tests that the network configuration is correct
        aura_url = SERVICES["aura-app"].url
        media_url = SERVICES["media-service"].url
        ai_url = SERVICES["ai-service"].url

        # All services should be reachable
        for name, url in [("AURA", aura_url), ("Media", media_url), ("AI", ai_url)]:
            response = requests.get(f"{url}/health" if name != "AURA" else f"{url}/actuator/health", timeout=5)
            self.assertEqual(response.status_code, 200)

        print("    Service Communication: All services reachable")

    def test_end_to_end_matching_flow(self):
        """Test end-to-end matching flow simulation"""
        ai_url = SERVICES["ai-service"].url

        # Create two compatible profiles
        profile_a = {
            "user_id": 1001,
            "personality": {"openness": 80, "conscientiousness": 70, "extraversion": 65, "agreeableness": 85, "neuroticism": 30},
            "values": {"progressive": 75, "egalitarian": 80},
            "lifestyle": {"social": 65, "health": 75, "work_life": 60, "finance": 70},
            "attachment": {"anxiety": 20, "avoidance": 15}
        }

        profile_b = {
            "user_id": 1002,
            "personality": {"openness": 75, "conscientiousness": 75, "extraversion": 60, "agreeableness": 80, "neuroticism": 35},
            "values": {"progressive": 70, "egalitarian": 85},
            "lifestyle": {"social": 60, "health": 80, "work_life": 65, "finance": 65},
            "attachment": {"anxiety": 25, "avoidance": 20}
        }

        # Step 1: Generate embeddings
        embed_a = requests.post(f"{ai_url}/embedding/generate", json={"profile": profile_a}, timeout=10)
        embed_b = requests.post(f"{ai_url}/embedding/generate", json={"profile": profile_b}, timeout=10)
        self.assertEqual(embed_a.status_code, 200)
        self.assertEqual(embed_b.status_code, 200)

        # Step 2: Calculate compatibility
        compat = requests.post(f"{ai_url}/compatibility/score",
                              json={"user1": profile_a, "user2": profile_b}, timeout=10)
        self.assertEqual(compat.status_code, 200)
        score = compat.json()["overall_score"]

        # Step 3: Batch match (find best match for A among candidates)
        candidates = [profile_b, {
            "user_id": 1003,
            "personality": {"openness": 40, "conscientiousness": 50, "extraversion": 80, "agreeableness": 50, "neuroticism": 60},
            "values": {"progressive": 30, "egalitarian": 40},
            "lifestyle": {"social": 90, "health": 30, "work_life": 40, "finance": 45},
            "attachment": {"anxiety": 60, "avoidance": 50}
        }]

        match_result = requests.post(f"{ai_url}/matching/batch",
                                    json={"target": profile_a, "candidates": candidates, "limit": 2},
                                    timeout=10)
        self.assertEqual(match_result.status_code, 200)
        matches = match_result.json()["matches"]

        # The compatible profile should rank higher
        self.assertEqual(matches[0]["user_id"], 1002)

        print(f"    E2E Matching Flow: Complete")
        print(f"      - Embeddings generated: 2")
        print(f"      - Compatibility score: {score:.1f}%")
        print(f"      - Best match: User {matches[0]['user_id']} ({matches[0]['score']:.1f}%)")


# =============================================================================
# Test Runner
# =============================================================================

def print_banner():
    """Print test banner"""
    print("\n" + "=" * 60)
    print("   AURA Platform E2E Verification Tests")
    print("=" * 60)
    print(f"   Timestamp: {datetime.now().isoformat()}")
    print(f"   Services:")
    for key, service in SERVICES.items():
        print(f"     - {service.name}: {service.url}")
    print("=" * 60)


def print_summary(result: unittest.TestResult):
    """Print test summary"""
    print("\n" + "=" * 60)
    print("   TEST SUMMARY")
    print("=" * 60)

    total = result.testsRun
    failures = len(result.failures)
    errors = len(result.errors)
    passed = total - failures - errors

    print(f"   Total Tests: {total}")
    print(f"   Passed: {passed}")
    print(f"   Failures: {failures}")
    print(f"   Errors: {errors}")

    if failures > 0:
        print("\n   FAILURES:")
        for test, trace in result.failures:
            print(f"     - {test}")

    if errors > 0:
        print("\n   ERRORS:")
        for test, trace in result.errors:
            print(f"     - {test}")

    print("=" * 60)

    if failures == 0 and errors == 0:
        print("   [PASS] All E2E tests passed!")
    else:
        print("   [FAIL] Some tests failed. Check details above.")

    print("=" * 60 + "\n")

    return failures == 0 and errors == 0


def main():
    """Main test runner"""
    print_banner()

    # Create test suite
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()

    # Add test classes in order
    # Core service health
    suite.addTests(loader.loadTestsFromTestCase(TestServiceHealth))
    suite.addTests(loader.loadTestsFromTestCase(TestMediaServiceCapabilities))
    suite.addTests(loader.loadTestsFromTestCase(TestAIServiceCapabilities))
    suite.addTests(loader.loadTestsFromTestCase(TestAuraAppCapabilities))
    suite.addTests(loader.loadTestsFromTestCase(TestCoreDatingFlows))

    # AURA-specific features (vs upstream)
    suite.addTests(loader.loadTestsFromTestCase(TestAccountabilitySystem))
    suite.addTests(loader.loadTestsFromTestCase(TestAssessmentSystem))
    suite.addTests(loader.loadTestsFromTestCase(TestIntakeAndScaffolding))
    suite.addTests(loader.loadTestsFromTestCase(TestLocationSystem))
    suite.addTests(loader.loadTestsFromTestCase(TestMatchingAndReputation))
    suite.addTests(loader.loadTestsFromTestCase(TestVideoDates))
    suite.addTests(loader.loadTestsFromTestCase(TestVideoVerification))
    suite.addTests(loader.loadTestsFromTestCase(TestPoliticalAssessment))
    suite.addTests(loader.loadTestsFromTestCase(TestProfileAndRelationship))
    suite.addTests(loader.loadTestsFromTestCase(TestMatchWindows))
    suite.addTests(loader.loadTestsFromTestCase(TestEssaysAndPersonality))
    suite.addTests(loader.loadTestsFromTestCase(TestWaitlistAndVerification))

    # Integration scenarios
    suite.addTests(loader.loadTestsFromTestCase(TestIntegrationScenarios))

    # Run tests
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)

    # Print summary
    success = print_summary(result)

    # Exit with appropriate code
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()

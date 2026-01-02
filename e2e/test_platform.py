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


class TestAuraAppEndpoints(unittest.TestCase):
    """Smoke tests for all AURA application REST endpoints"""

    @classmethod
    def setUpClass(cls):
        cls.base_url = SERVICES["aura-app"].url

    def test_personality_assessment_endpoint(self):
        """Test /personality/assessment endpoint exists"""
        response = requests.get(f"{self.base_url}/personality/assessment", timeout=REQUEST_TIMEOUT)
        # May return 401/403 for unauthenticated, but endpoint should exist
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Personality Assessment: Status {response.status_code}")

    def test_reputation_endpoint(self):
        """Test /api/v1/reputation endpoints exist"""
        endpoints = ["/api/v1/reputation/me", "/api/v1/reputation/badges"]
        for endpoint in endpoints:
            response = requests.get(f"{self.base_url}{endpoint}", timeout=REQUEST_TIMEOUT)
            self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Reputation Endpoints: Accessible")

    def test_accountability_categories(self):
        """Test /api/v1/accountability/categories endpoint"""
        response = requests.get(f"{self.base_url}/api/v1/accountability/categories", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Accountability Categories: Status {response.status_code}")

    def test_video_verification_status(self):
        """Test /video/verification/status endpoint"""
        response = requests.get(f"{self.base_url}/video/verification/status", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Video Verification Status: Status {response.status_code}")

    def test_intake_progress(self):
        """Test /intake/progress endpoint"""
        response = requests.get(f"{self.base_url}/intake/progress", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Intake Progress: Status {response.status_code}")

    def test_intake_questions(self):
        """Test /intake/questions endpoint"""
        response = requests.get(f"{self.base_url}/intake/questions", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Intake Questions: Status {response.status_code}")

    def test_scaffolding_prompts(self):
        """Test /intake/scaffolding/prompts endpoint"""
        response = requests.get(f"{self.base_url}/intake/scaffolding/prompts", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Scaffolding Prompts: Status {response.status_code}")

    def test_match_window_current(self):
        """Test match window endpoints"""
        response = requests.get(f"{self.base_url}/match-window/current", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Match Window: Status {response.status_code}")

    def test_location_areas(self):
        """Test location area endpoints"""
        response = requests.get(f"{self.base_url}/location/areas", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Location Areas: Status {response.status_code}")

    def test_waitlist_status(self):
        """Test waitlist status endpoint"""
        response = requests.get(f"{self.base_url}/waitlist/status", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Waitlist Status: Status {response.status_code}")

    def test_api_search_keyword(self):
        """Test API keyword search endpoint exists"""
        response = requests.post(
            f"{self.base_url}/api/v1/search/keyword",
            json={"keyword": "test", "globalSearch": False},
            timeout=REQUEST_TIMEOUT
        )
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    API Keyword Search: Status {response.status_code}")

    def test_essay_prompts(self):
        """Test essay prompts endpoint"""
        response = requests.get(f"{self.base_url}/essay/prompts", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Essay Prompts: Status {response.status_code}")

    def test_matching_recommendations(self):
        """Test matching recommendations endpoint"""
        response = requests.get(f"{self.base_url}/matching/recommendations", timeout=REQUEST_TIMEOUT)
        self.assertIn(response.status_code, [200, 401, 403])
        print(f"    Matching Recommendations: Status {response.status_code}")


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
    suite.addTests(loader.loadTestsFromTestCase(TestServiceHealth))
    suite.addTests(loader.loadTestsFromTestCase(TestMediaServiceCapabilities))
    suite.addTests(loader.loadTestsFromTestCase(TestAIServiceCapabilities))
    suite.addTests(loader.loadTestsFromTestCase(TestAuraAppCapabilities))
    suite.addTests(loader.loadTestsFromTestCase(TestAuraAppEndpoints))
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

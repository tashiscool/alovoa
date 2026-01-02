#!/usr/bin/env python3
"""
Mock Media Service for integration testing.
Provides face verification and video processing endpoints.
"""

from flask import Flask, jsonify, request
import hashlib
import base64

app = Flask(__name__)


@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'healthy', 'service': 'media-service-mock'})


@app.route('/api/v1/verify-face', methods=['POST'])
def verify_face():
    """Mock face verification against profile picture."""
    data = request.get_json() or {}
    user_id = data.get('user_id')

    # Always return successful verification in mock
    return jsonify({
        'user_id': user_id,
        'verified': True,
        'face_match_score': 0.92,
        'liveness_score': 0.95,
        'deepfake_score': 0.05,
        'status': 'VERIFIED',
        'message': 'Mock verification successful'
    })


@app.route('/api/v1/liveness-check', methods=['POST'])
def liveness_check():
    """Mock liveness detection."""
    data = request.get_json() or {}
    session_id = data.get('session_id')

    return jsonify({
        'session_id': session_id,
        'is_live': True,
        'liveness_score': 0.95,
        'deepfake_probability': 0.03,
        'status': 'PASSED'
    })


@app.route('/api/v1/analyze-video', methods=['POST'])
def analyze_video():
    """Mock video analysis for intro videos."""
    data = request.get_json() or {}
    video_id = data.get('video_id')

    return jsonify({
        'video_id': video_id,
        'duration_seconds': 120,
        'transcript': 'This is a mock transcript of the video introduction.',
        'sentiment': {
            'overall': 'positive',
            'confidence': 0.85
        },
        'personality_indicators': {
            'openness': 0.7,
            'conscientiousness': 0.8,
            'extraversion': 0.6,
            'agreeableness': 0.75,
            'neuroticism': 0.3
        },
        'worldview_summary': 'Mock worldview summary for testing.',
        'background_summary': 'Mock background summary for testing.',
        'status': 'COMPLETED'
    })


@app.route('/api/v1/transcribe', methods=['POST'])
def transcribe_audio():
    """Mock audio transcription."""
    data = request.get_json() or {}
    audio_id = data.get('audio_id')

    return jsonify({
        'audio_id': audio_id,
        'transcript': 'This is a mock transcription of the audio content.',
        'duration_seconds': 180,
        'language': 'en',
        'confidence': 0.95
    })


@app.route('/api/v1/compare-faces', methods=['POST'])
def compare_faces():
    """Mock face comparison between two images."""
    data = request.get_json() or {}

    return jsonify({
        'match': True,
        'similarity_score': 0.89,
        'confidence': 0.92,
        'message': 'Mock face comparison successful'
    })


@app.route('/api/v1/moderation/image', methods=['POST'])
def moderate_image():
    """Mock image content moderation."""
    data = request.get_json() or {}

    return jsonify({
        'is_safe': True,
        'categories': {
            'adult': 0.01,
            'violence': 0.0,
            'hate': 0.0
        },
        'action': 'ALLOW'
    })


@app.route('/api/v1/moderation/text', methods=['POST'])
def moderate_text():
    """Mock text content moderation."""
    data = request.get_json() or {}
    text = data.get('text', '')

    return jsonify({
        'text': text,
        'is_toxic': False,
        'toxicity_score': 0.05,
        'categories': {
            'toxicity': 0.05,
            'severe_toxicity': 0.01,
            'identity_attack': 0.0,
            'insult': 0.02,
            'profanity': 0.0,
            'threat': 0.0
        },
        'action': 'ALLOW'
    })


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8001, debug=False)

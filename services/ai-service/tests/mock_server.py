#!/usr/bin/env python3
"""
Mock AI Service for integration testing.
Provides compatibility scoring and personality matching endpoints.
"""

from flask import Flask, jsonify, request
import random

app = Flask(__name__)


@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'healthy', 'service': 'ai-service-mock'})


@app.route('/api/v1/compatibility', methods=['POST'])
def calculate_compatibility():
    """Calculate mock compatibility scores between two users."""
    data = request.get_json() or {}

    # Generate deterministic mock scores based on user IDs
    user1_id = data.get('user1_id', 0)
    user2_id = data.get('user2_id', 0)
    seed = hash(f"{user1_id}-{user2_id}") % 100

    return jsonify({
        'user1_id': user1_id,
        'user2_id': user2_id,
        'scores': {
            'values': min(95, 50 + seed % 45),
            'lifestyle': min(95, 55 + (seed + 10) % 40),
            'personality': min(95, 60 + (seed + 20) % 35),
            'attraction': min(95, 45 + (seed + 30) % 50),
            'circumstantial': min(95, 50 + (seed + 40) % 45),
            'growth_potential': min(95, 55 + (seed + 50) % 40),
            'overall': min(95, 55 + seed % 40),
            'enemy_score': max(0, 10 + (seed % 20))
        },
        'top_compatibilities': [
            'Shared values on important life decisions',
            'Compatible communication styles',
            'Similar lifestyle preferences'
        ],
        'potential_challenges': [
            'Different approaches to conflict resolution'
        ],
        'explanation': 'Mock compatibility explanation for testing.'
    })


@app.route('/api/v1/embedding', methods=['POST'])
def generate_embedding():
    """Generate mock personality embedding."""
    data = request.get_json() or {}
    user_id = data.get('user_id', 0)

    # Generate deterministic mock embedding
    random.seed(user_id)
    embedding = [random.random() for _ in range(128)]

    return jsonify({
        'user_id': user_id,
        'embedding': embedding,
        'dimensions': 128
    })


@app.route('/api/v1/recommendations', methods=['POST'])
def get_recommendations():
    """Get mock match recommendations."""
    data = request.get_json() or {}
    user_id = data.get('user_id', 0)
    limit = data.get('limit', 5)

    # Generate mock recommendations
    recommendations = []
    for i in range(min(limit, 10)):
        recommendations.append({
            'user_id': 1000 + i,
            'compatibility_score': 95 - (i * 5),
            'reason': f'Mock recommendation {i + 1}'
        })

    return jsonify({
        'user_id': user_id,
        'recommendations': recommendations
    })


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8002, debug=False)

import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 Load Test Script for Milestone M12: Caching, Performance & Scalability (Target ~500 CCU)
// Simulates user workflow: /start -> /analyze -> poll /status -> /assessment
export const options = {
  stages: [
    { duration: '30s', target: 50 },   // Warm-up to 50 CCU
    { duration: '1m',  target: 100 },  // Ramp to 100 CCU
    { duration: '1m',  target: 250 },  // Ramp to 250 CCU
    { duration: '1m',  target: 500 },  // Peak at 500 CCU
    { duration: '30s', target: 0 },    // Scale down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<2000'], // 95% of requests must complete within 2s
    'http_req_failed': ['rate<0.01'],    // Error rate under 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Mock tiny 1x1 JPEG dummy image binary
const DUMMY_IMAGE = [0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00, 0xFF, 0xDB, 0x00, 0x43, 0x00, 0xFF, 0xD9];

export default function () {
  // Step 1: Start Analysis Session
  const startRes = http.post(`${BASE_URL}/api/analysis/start`, null, {
    headers: { 'Accept': 'application/json' },
  });

  const startOk = check(startRes, {
    'session created (201)': (r) => r.status === 201,
    'has sessionId': (r) => JSON.parse(r.body).sessionId !== undefined,
  });

  if (!startOk) {
    sleep(1);
    return;
  }

  const session = JSON.parse(startRes.body);
  const sessionId = session.sessionId;

  // Step 2: Trigger Analysis with mock images
  const analyzePayload = {
    ingredientImage: http.file(new Uint8Array(DUMMY_IMAGE).buffer, 'ingredients.jpg', 'image/jpeg'),
  };

  const analyzeRes = http.post(`${BASE_URL}/api/analysis/${sessionId}/analyze`, analyzePayload);
  check(analyzeRes, {
    'analyze accepted (202 or 200)': (r) => r.status === 202 || r.status === 200,
  });

  // Step 3: Poll status (up to 5 attempts)
  let completed = false;
  for (let i = 0; i < 5; i++) {
    sleep(1);
    const statusRes = http.get(`${BASE_URL}/api/analysis/${sessionId}/status`);
    if (statusRes.status === 200) {
      const statusData = JSON.parse(statusRes.body);
      if (statusData.status === 'COMPLETED') {
        completed = true;
        break;
      }
    }
  }

  // Step 4: Fetch final assessment if completed
  if (completed) {
    const assessRes = http.get(`${BASE_URL}/api/analysis/${sessionId}/assessment`);
    check(assessRes, {
      'assessment ok (200)': (r) => r.status === 200,
      'has overallScore': (r) => JSON.parse(r.body).overallScore !== undefined,
    });
  }

  sleep(1);
}

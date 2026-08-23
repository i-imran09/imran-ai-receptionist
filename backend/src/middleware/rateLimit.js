// Simple in-memory rate limiting
// In production, use Redis or similar

const requestCounts = new Map();
const RATE_LIMIT_WINDOW = 60 * 1000; // 1 minute
const MAX_REQUESTS_PER_WINDOW = 100;

export function rateLimitWebhook(req, res, next) {
  const clientId = req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown';
  const now = Date.now();

  if (!requestCounts.has(clientId)) {
    requestCounts.set(clientId, []);
  }

  const requests = requestCounts.get(clientId);

  // Remove old requests outside the window
  const recentRequests = requests.filter(time => now - time < RATE_LIMIT_WINDOW);

  if (recentRequests.length >= MAX_REQUESTS_PER_WINDOW) {
    console.warn(`⚠️ Rate limit exceeded for ${clientId}`);
    return res.status(429).json({ error: 'Too many requests' });
  }

  recentRequests.push(now);
  requestCounts.set(clientId, recentRequests);

  next();
}

// Simple development authentication middleware
// In production, implement proper token validation, OAuth, or mutual TLS

function authenticateRequest(req, res, next) {
  const authHeader = req.headers['authorization'];
  const androidSecret = process.env.ANDROID_SECRET_KEY;

  if (!androidSecret) {
    console.warn('[Auth] ANDROID_SECRET_KEY not configured, allowing request (DEVELOPMENT ONLY)');
    return next();
  }

  // Expected format: "Bearer {secret}"
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({
      error: 'Missing authorization token'
    });
  }

  if (token !== androidSecret) {
    return res.status(403).json({
      error: 'Invalid authorization token'
    });
  }

  next();
}

module.exports = {
  authenticateRequest
};

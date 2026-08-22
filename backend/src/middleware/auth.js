import crypto from 'crypto';

// Authenticate Android app requests
export function authenticateAndroid(req, res, next) {
  const authHeader = req.headers['authorization'];

  if (!authHeader) {
    return res.status(401).json({ error: 'Missing authorization header' });
  }

  // Expected format: Bearer <token>
  const parts = authHeader.split(' ');
  if (parts.length !== 2 || parts[0] !== 'Bearer') {
    return res.status(401).json({ error: 'Invalid authorization header format' });
  }

  const token = parts[1];

  // Verify token (simple HMAC verification)
  const expected = crypto
    .createHmac('sha256', process.env.APP_SHARED_SECRET)
    .update('android-app')
    .digest('hex');

  if (!crypto.timingSafeEqual(token, expected)) {
    return res.status(401).json({ error: 'Invalid token' });
  }

  console.log('✅ Android request authenticated');
  next();
}

// Verify Meta webhook signature
export function verifyMetaWebhook(req, res, next) {
  const signature = req.headers['x-hub-signature-256'];

  if (!signature) {
    console.warn('⚠️ Missing webhook signature');
    return res.status(403).json({ error: 'Missing signature' });
  }

  // Get raw body (must be configured in Express)
  let body = '';
  if (Buffer.isBuffer(req.body)) {
    body = req.body.toString('utf-8');
  } else {
    body = JSON.stringify(req.body);
  }

  // Calculate expected signature
  const expected = 'sha256=' + crypto
    .createHmac('sha256', process.env.META_VERIFY_TOKEN)
    .update(body)
    .digest('hex');

  // Verify signature (timing-safe comparison)
  try {
    if (!crypto.timingSafeEqual(signature, expected)) {
      console.error('❌ Invalid webhook signature');
      return res.status(403).json({ error: 'Invalid signature' });
    }
  } catch (err) {
    console.error('❌ Signature verification failed:', err.message);
    return res.status(403).json({ error: 'Signature verification failed' });
  }

  console.log('✅ Meta webhook signature verified');
  next();
}

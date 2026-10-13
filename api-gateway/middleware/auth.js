const jwt = require('jsonwebtoken');
const { sendError } = require('./errorHandler');
const { isTokenBlacklisted } = require('./redis');

const MIN_SECRET_BYTES = 32;
const JWT_SECRET = process.env.JWT_SECRET;

// X-03: no fallback secret. Refuse to start with a missing or weak signing key.
if (!JWT_SECRET || Buffer.byteLength(JWT_SECRET, 'utf8') < MIN_SECRET_BYTES) {
  console.error(
    `[FATAL] JWT_SECRET is missing or shorter than ${MIN_SECRET_BYTES} characters. ` +
      'Set a strong JWT_SECRET (same value as every backend service) and restart.'
  );
  process.exit(1);
}

/**
 * Verifies the Bearer JWT (same shared secret every Java service validates independently).
 * Only `type=access` tokens are accepted (AUTH-02) and revoked `jti`s are rejected (AUTH-03).
 */
async function verifyToken(req, res, next) {
  const header = req.headers.authorization;
  const token = header && header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return sendError(res, 401, 'Authentication required', req.requestId);
  }

  let payload;
  try {
    payload = jwt.verify(token, JWT_SECRET, { algorithms: ['HS256'] });
  } catch (err) {
    return sendError(res, 401, 'Invalid or expired token', req.requestId);
  }

  // Tokens without a jti can't be revoked, so they are rejected (same rule as the Java services).
  if (!payload || typeof payload !== 'object' || payload.type !== 'access' || !payload.jti) {
    return sendError(res, 401, 'Invalid or expired token', req.requestId);
  }

  if (await isTokenBlacklisted(payload.jti)) {
    return sendError(res, 401, 'Token đã bị thu hồi', req.requestId);
  }

  req.user = payload;
  return next();
}

/** Requires auth for every method except safe ones (GET/HEAD/OPTIONS) — browsable-but-admin-writable resources. */
function requireAuthForWrites(req, res, next) {
  if (req.method === 'GET' || req.method === 'HEAD' || req.method === 'OPTIONS') {
    return next();
  }
  return verifyToken(req, res, next);
}

module.exports = { verifyToken, requireAuthForWrites };

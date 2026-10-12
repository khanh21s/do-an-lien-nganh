const jwt = require('jsonwebtoken');
const { sendError } = require('./errorHandler');

const JWT_SECRET = process.env.JWT_SECRET || 'dev_super_secret_jwt_key_change_in_production';

/** Verifies the Bearer JWT (same shared secret every Java service validates independently). */
function verifyToken(req, res, next) {
  const header = req.headers.authorization;
  const token = header && header.startsWith('Bearer ') ? header.slice(7) : null;
  if (!token) {
    return sendError(res, 401, 'Authentication required', req.requestId);
  }
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    next();
  } catch (err) {
    return sendError(res, 401, 'Invalid or expired token', req.requestId);
  }
}

/** Requires auth for every method except GET (used for browsable-but-admin-writable resources). */
function requireAuthForWrites(req, res, next) {
  if (req.method === 'GET') {
    return next();
  }
  return verifyToken(req, res, next);
}

module.exports = { verifyToken, requireAuthForWrites };

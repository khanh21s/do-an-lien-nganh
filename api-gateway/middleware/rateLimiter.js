const rateLimit = require('express-rate-limit');
const { sendError } = require('./errorHandler');

const handler = (req, res) => sendError(res, 429, 'Too many requests', req.requestId);

/** 1000 requests/hour per IP, applied globally (matches the original spec). */
const ipLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 1000,
  standardHeaders: true,
  legacyHeaders: false,
  handler,
});

/** 5000 requests/hour per authenticated user, applied after verifyToken on protected routes. */
const userLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 5000,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => (req.user && req.user.sub) || req.ip,
  handler,
});

/**
 * P2-C: GET /vocabs/dictionary/{word} is public and may reach an external API on a cache miss,
 * so it gets its own tighter budget per IP (clicking words while reading stays far below it).
 */
const dictionaryLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => sendError(res, 429, 'Tra từ quá nhanh, vui lòng thử lại sau ít phút', req.requestId),
});

module.exports = { ipLimiter, userLimiter, dictionaryLimiter };

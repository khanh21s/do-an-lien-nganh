const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const { createProxyMiddleware } = require('http-proxy-middleware');

const { requestId, requestLogger } = require('./middleware/logger');
const { verifyToken, requireAuthForWrites } = require('./middleware/auth');
const { ipLimiter, userLimiter } = require('./middleware/rateLimiter');
const { notFoundHandler, errorHandler, sendError } = require('./middleware/errorHandler');

const PORT = process.env.PORT || 8000;
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:3000';

const SERVICES = {
  auth: process.env.AUTH_SERVICE_URL || 'http://localhost:8001',
  content: process.env.CONTENT_SERVICE_URL || 'http://localhost:8002',
  practice: process.env.PRACTICE_SERVICE_URL || 'http://localhost:8003',
  vocabulary: process.env.VOCABULARY_SERVICE_URL || 'http://localhost:8004',
  subscription: process.env.SUBSCRIPTION_SERVICE_URL || 'http://localhost:8005',
};

const app = express();
app.disable('x-powered-by');
app.set('trust proxy', 1);

app.use(requestId);
app.use(requestLogger);
app.use(helmet());
app.use(cors({ origin: FRONTEND_URL, credentials: true }));
app.use(ipLimiter);

// Deliberately no body-parser here: every route below is a proxy pass-through, and
// consuming the request stream (e.g. express.json()) before http-proxy-middleware pipes
// it upstream leaves the proxied request body empty and the upstream connection hanging.
// verifyToken only reads the Authorization header, never the body, so this is safe.

function proxyError(serviceName) {
  return (err, req, res) => {
    console.error(`[${req.requestId}] Proxy error reaching ${serviceName}:`, err.message);
    sendError(res, 502, `${serviceName} service is unavailable`, req.requestId);
  };
}

// Express strips the app.use() mount prefix from req.url before invoking a sub-middleware,
// so http-proxy-middleware would otherwise forward a truncated path. req.originalUrl is
// never touched by Express's mounting, so every proxy rewrite is anchored on that instead.
function proxyTo(target, { stripPrefix, serviceName } = {}) {
  return createProxyMiddleware({
    target,
    changeOrigin: true,
    pathRewrite: (_path, req) => {
      const full = req.originalUrl;
      return stripPrefix ? full.replace(stripPrefix, '') : full;
    },
    onProxyReq: (proxyReq, req) => {
      if (req.requestId) proxyReq.setHeader('X-Request-Id', req.requestId);
    },
    onError: proxyError(serviceName || target),
    logLevel: 'warn',
  });
}

app.get('/health', (req, res) => {
  res.json({ status: 'OK', timestamp: new Date().toISOString() });
});

// ==================== AUTH SERVICE ====================
// Public auth endpoints (no token yet at register/login time).
app.use(
  ['/auth/register', '/auth/login', '/auth/login-google', '/auth/login-apple', '/auth/refresh-token'],
  proxyTo(SERVICES.auth, { serviceName: 'auth' })
);
// Everything else under /auth (profile, change-password, account, logout) requires a token.
app.use('/auth', verifyToken, userLimiter, proxyTo(SERVICES.auth, { serviceName: 'auth' }));

// ==================== CONTENT SERVICE ====================
// content-service's own routes are bare /lessons, /sentences (no /content prefix),
// so the gateway strips /content before forwarding.
app.use(
  '/content',
  requireAuthForWrites,
  proxyTo(SERVICES.content, { serviceName: 'content', stripPrefix: /^\/content/ })
);

// ==================== PRACTICE SERVICE ====================
// Dictation/shadowing/stats always require an authenticated user.
app.use('/practice', verifyToken, userLimiter, proxyTo(SERVICES.practice, { serviceName: 'practice' }));

// ==================== VOCABULARY SERVICE ====================
// Deck browsing is public; creating/editing decks and cards requires a token
// (admin authorization itself is enforced inside vocabulary-service).
app.use('/vocabs/decks', requireAuthForWrites, proxyTo(SERVICES.vocabulary, { serviceName: 'vocabulary' }));
app.use('/vocabs/cards', verifyToken, proxyTo(SERVICES.vocabulary, { serviceName: 'vocabulary' }));
// Personal notebook/review endpoints always require a token.
app.use('/vocabs/user', verifyToken, userLimiter, proxyTo(SERVICES.vocabulary, { serviceName: 'vocabulary' }));

// ==================== SUBSCRIPTION SERVICE ====================
app.use(
  ['/subscriptions/plans', '/subscriptions/payment-callback'],
  proxyTo(SERVICES.subscription, { serviceName: 'subscription' })
);
app.use('/subscriptions', verifyToken, userLimiter, proxyTo(SERVICES.subscription, { serviceName: 'subscription' }));

app.use(notFoundHandler);
app.use(errorHandler);

app.listen(PORT, () => {
  console.log(`Parroto API Gateway listening on port ${PORT}`);
  console.log('Routing to:', SERVICES);
});

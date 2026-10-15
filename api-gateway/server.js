const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const { createProxyMiddleware } = require('http-proxy-middleware');

const { requestId, requestLogger } = require('./middleware/logger');
const { verifyToken, requireAuthForWrites } = require('./middleware/auth');
const { ipLimiter, userLimiter, dictionaryLimiter } = require('./middleware/rateLimiter');
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
// Audio (lesson/sentence) and images are loaded by the frontend origin through <audio>/<img>
// (no-cors requests). helmet's default `Cross-Origin-Resource-Policy: same-origin` makes the
// browser block them (ERR_BLOCKED_BY_RESPONSE.NotSameOrigin) because FE and gateway are on
// different origins, so allow cross-origin embedding; JSON reads stay protected by CORS.
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
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
// P2-C dictionary lookups are public (a Bearer, when sent, is forwarded so the user's own cards are
// included); writes do not exist there, so anything but GET is rejected by vocabulary-service.
app.use('/vocabs/dictionary', dictionaryLimiter, proxyTo(SERVICES.vocabulary, { serviceName: 'vocabulary' }));
// T15 "Dịch nhanh" game rounds (scored server-side) always require a token.
app.use('/vocabs/games', verifyToken, userLimiter, proxyTo(SERVICES.vocabulary, { serviceName: 'vocabulary' }));
// P2-B app-wide XP / diamonds / check-in / missions / XP leaderboard always require a token.
// (vocabulary-service's /internal/** is never routed: it is not under /vocabs.)
app.use('/vocabs/gamification', verifyToken, userLimiter, proxyTo(SERVICES.vocabulary, { serviceName: 'vocabulary' }));

// ==================== SUBSCRIPTION SERVICE ====================
// Public: plan listing, the VNPay browser return URL, and the VNPay server-to-server IPN (SUB-02),
// none of which carry a user token. subscription-service verifies the VNPay signature itself.
// Same for SePay (/subscriptions/sepay/checkout|return|ipn): the service re-reads every result
// from SePay's API and checks the IPN's X-Secret-Key header.
app.use(
  ['/subscriptions/plans', '/subscriptions/payment-callback', '/subscriptions/vnpay-ipn', '/subscriptions/sepay'],
  proxyTo(SERVICES.subscription, { serviceName: 'subscription' })
);
app.use('/subscriptions', verifyToken, userLimiter, proxyTo(SERVICES.subscription, { serviceName: 'subscription' }));

app.use(notFoundHandler);
app.use(errorHandler);

app.listen(PORT, () => {
  console.log(`Parroto API Gateway listening on port ${PORT}`);
  console.log('Routing to:', SERVICES);
});

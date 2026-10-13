const { createClient } = require('redis');

const REDIS_URL = process.env.REDIS_URL || 'redis://localhost:6379';
// Upper bound a request may wait on Redis (connect or command) before failing open.
const REDIS_WAIT_MS = 500;

let client = null;
let ready = null; // Promise resolving once the first connection is established

/** Resolves to `fallback` if `promise` hasn't settled within `ms` (never rejects). */
function within(promise, ms, fallback) {
  let timer;
  return Promise.race([
    promise.catch(() => fallback),
    new Promise((resolve) => {
      timer = setTimeout(() => resolve(fallback), ms);
    }),
  ]).finally(() => clearTimeout(timer));
}

/**
 * Lazily creates the shared node-redis client and starts connecting in the background.
 * node-redis keeps reconnecting on its own (and connect() does not reject while it retries),
 * so callers must never await it unbounded.
 */
function getClient() {
  if (!client) {
    client = createClient({
      url: REDIS_URL,
      // Don't queue commands while disconnected: fail fast instead and let callers fail open.
      disableOfflineQueue: true,
      socket: {
        connectTimeout: 2000,
        reconnectStrategy: (retries) => Math.min(retries * 200, 5000),
      },
    });
    client.on('error', (err) => {
      console.warn(`[redis] ${err.message}`);
    });
    ready = client.connect().catch((err) => {
      console.warn(`[redis] connect failed (${REDIS_URL}): ${err.message}`);
    });
  }
  return client;
}

/**
 * True when `token_blacklist:{jti}` exists (written by auth-service on logout / refresh rotation).
 * Fail-open: Redis unavailable, slow or erroring resolves to false with a warning
 * (deliberate demo trade-off, see docs/architecture.md §5.2).
 */
async function isTokenBlacklisted(jti) {
  if (!jti) return false;
  try {
    const redis = getClient();
    if (!redis.isReady) {
      await within(ready, REDIS_WAIT_MS, undefined);
    }
    if (!redis.isReady) {
      console.warn('[redis] unavailable, skipping token blacklist check (fail-open)');
      return false;
    }
    const exists = await within(redis.exists(`token_blacklist:${jti}`), REDIS_WAIT_MS, null);
    if (exists === null) {
      console.warn('[redis] blacklist check timed out or failed, failing open');
      return false;
    }
    return exists === 1;
  } catch (err) {
    console.warn(`[redis] blacklist check failed, failing open: ${err.message}`);
    return false;
  }
}

module.exports = { isTokenBlacklisted };

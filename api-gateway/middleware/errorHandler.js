/** Standardized error JSON shape shared with every backend service's GlobalExceptionHandler. */
function sendError(res, status, message, requestId) {
  res.status(status).json({
    error: message,
    status,
    timestamp: new Date().toISOString(),
    requestId: requestId || 'unknown',
  });
}

function notFoundHandler(req, res) {
  sendError(res, 404, 'Route not found', req.requestId);
}

function errorHandler(err, req, res, _next) {
  console.error(`[${req.requestId}] Unhandled error:`, err);
  sendError(res, 500, 'Internal gateway error', req.requestId);
}

module.exports = { sendError, notFoundHandler, errorHandler };

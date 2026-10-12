const morgan = require('morgan');
const { v4: uuidv4 } = require('uuid');

/** Attaches a correlation id to every request (reused from an upstream X-Request-Id if present). */
function requestId(req, res, next) {
  const id = req.headers['x-request-id'] || uuidv4();
  req.requestId = id;
  res.setHeader('X-Request-Id', id);
  next();
}

morgan.token('request-id', (req) => req.requestId);

const requestLogger = morgan(':date[iso] [:request-id] :method :url :status :res[content-length] - :response-time ms');

module.exports = { requestId, requestLogger };

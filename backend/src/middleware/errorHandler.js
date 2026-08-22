function errorHandler(err, req, res, next) {
  console.error(`[${new Date().toISOString()}] Error:`, {
    message: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method
  });

  // Never expose internal error details in production
  const isDevelopment = process.env.NODE_ENV === 'development';
  const errorResponse = {
    error: isDevelopment ? err.message : 'Internal server error',
    timestamp: new Date().toISOString()
  };

  if (isDevelopment) {
    errorResponse.stack = err.stack;
  }

  res.status(err.status || 500).json(errorResponse);
}

module.exports = errorHandler;

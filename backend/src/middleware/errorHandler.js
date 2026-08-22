export default function errorHandler(err, req, res, next) {
  console.error('❌ Error:', err.message);

  // Don't expose internal error details
  const statusCode = err.statusCode || 500;
  const message = statusCode === 500 ? 'Internal server error' : err.message;

  res.status(statusCode).json({
    error: message,
    timestamp: new Date().toISOString()
  });
}

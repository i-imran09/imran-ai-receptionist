const errorHandler = (err, req, res, next) => {
  console.error(`[ERROR] ${err.message}`);
  
  const isDev = process.env.NODE_ENV === 'development';
  const status = err.status || 500;
  const response = {
    error: isDev ? err.message : 'Internal server error',
    timestamp: new Date().toISOString()
  };

  if (isDev) {
    response.stack = err.stack;
  }

  res.status(status).json(response);
};

export default errorHandler;

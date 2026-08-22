export const validateCallFollowup = (req, res, next) => {
  const { callerNumber, currentStatus, callTimestamp } = req.body;
  const errors = [];

  if (!callerNumber || typeof callerNumber !== 'string') {
    errors.push('callerNumber is required and must be a string');
  }

  if (!['Work', 'Sleep', 'Outing'].includes(currentStatus)) {
    errors.push('currentStatus must be Work, Sleep, or Outing');
  }

  if (!callTimestamp || isNaN(Date.parse(callTimestamp))) {
    errors.push('callTimestamp must be a valid ISO 8601 date');
  }

  if (errors.length > 0) {
    return res.status(400).json({ error: 'Validation failed', details: errors });
  }

  next();
};

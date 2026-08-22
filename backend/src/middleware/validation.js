function validateCallFollowup(req, res, next) {
  const { callerNumber, currentStatus, callTimestamp } = req.body;
  const errors = [];

  // Validate callerNumber
  if (!callerNumber) {
    errors.push('callerNumber is required');
  } else if (typeof callerNumber !== 'string') {
    errors.push('callerNumber must be a string');
  } else if (!/^[0-9+\-\s()]*$/.test(callerNumber)) {
    errors.push('callerNumber contains invalid characters');
  }

  // Validate currentStatus
  if (!currentStatus) {
    errors.push('currentStatus is required');
  } else if (!['Work', 'Sleep', 'Outing'].includes(currentStatus)) {
    errors.push('currentStatus must be one of: Work, Sleep, Outing');
  }

  // Validate callTimestamp
  if (!callTimestamp) {
    errors.push('callTimestamp is required');
  } else if (isNaN(Date.parse(callTimestamp))) {
    errors.push('callTimestamp must be a valid ISO 8601 date string');
  }

  if (errors.length > 0) {
    return res.status(400).json({
      error: 'Validation failed',
      details: errors
    });
  }

  next();
}

module.exports = {
  validateCallFollowup
};

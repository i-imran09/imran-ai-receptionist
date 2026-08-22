export function validateCallFollowupRequest(req, res, next) {
  const { callerNumber, currentStatus } = req.body;

  // Validate caller number
  if (!callerNumber || typeof callerNumber !== 'string') {
    return res.status(400).json({
      error: 'Invalid request: callerNumber is required and must be a string'
    });
  }

  // Validate status
  const validStatuses = ['Work', 'Sleep', 'Outing'];
  if (!currentStatus || !validStatuses.includes(currentStatus)) {
    return res.status(400).json({
      error: `Invalid status. Must be one of: ${validStatuses.join(', ')}`
    });
  }

  // Validate phone number format (basic)
  if (!/^\d{10,15}$/.test(callerNumber.replace(/\D/g, ''))) {
    return res.status(400).json({
      error: 'Invalid phone number format'
    });
  }

  console.log('✅ Request validation passed');
  next();
}

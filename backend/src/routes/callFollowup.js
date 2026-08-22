import express from 'express';
import { handleCallFollowup } from '../controllers/callController.js';
import { authenticateAndroid } from '../middleware/auth.js';
import { validateCallFollowupRequest } from '../middleware/validation.js';

const router = express.Router();

// POST /call-followup
// Android app sends: { callerNumber, currentStatus }
router.post('/', authenticateAndroid, validateCallFollowupRequest, handleCallFollowup);

export default router;

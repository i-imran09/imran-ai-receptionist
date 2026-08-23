import express from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { validateCallFollowup } from '../middleware/validation.js';
import { processCallFollowup } from '../controllers/callFollowupController.js';

const router = express.Router();

router.post('/', authMiddleware, validateCallFollowup, processCallFollowup);

export default router;

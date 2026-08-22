import express from 'express';

const router = express.Router();

router.get('/', (req, res) => {
  res.status(200).json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    service: 'imran-ai-receptionist-backend',
    version: '1.0.0'
  });
});

export default router;

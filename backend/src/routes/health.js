import express from 'express';

const router = express.Router();

router.get('/', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    service: 'Imran AI Receptionist Backend',
    uptime: process.uptime()
  });
});

export default router;

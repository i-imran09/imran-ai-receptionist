export function validateCallFollowup(req,res,next) {
  const {callerNumber,currentStatus,eventId}=req.body || {};
  if (!callerNumber || !eventId || !["Work","Sleep","Outing"].includes(currentStatus))
    return res.status(400).json({error:"Invalid call follow-up payload"});
  next();
}

import express from "express";
import dotenv from "dotenv";
dotenv.config();

import healthRouter from "./routes/health.js";
import callFollowupRouter from "./routes/callFollowup.js";
import webhookRouter from "./routes/webhook.js";
import errorHandler from "./middleware/errorHandler.js";
import { validateEnvironment } from "./config/env.js";

validateEnvironment();
const app=express();

// Keep raw bytes for Meta X-Hub-Signature-256 verification.
app.use(express.json({
  verify:(req,res,buf)=>{ req.rawBody=Buffer.from(buf); }
}));
app.use(express.urlencoded({extended:true}));

app.use("/health",healthRouter);
app.use("/call-followup",callFollowupRouter);
app.use("/webhook",webhookRouter);
app.use(errorHandler);

const port=Number(process.env.PORT||3000);
app.listen(port,()=>console.log(`Imran AI Receptionist backend listening on ${port}`));

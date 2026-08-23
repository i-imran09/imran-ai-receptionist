import crypto from "crypto";
import { getConfig } from "../config/env.js";

export function authMiddleware(req, res, next) {
  const expected = getConfig().app.clientToken;
  const supplied = (req.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (!supplied || supplied.length !== expected.length) return res.status(401).json({error:"Unauthorized"});
  const ok = crypto.timingSafeEqual(Buffer.from(supplied), Buffer.from(expected));
  if (!ok) return res.status(401).json({error:"Unauthorized"});
  next();
}

export function verifyWebhookSignature(rawBody, signature) {
  if (!signature) return false;
  const secret = getConfig().meta.appSecret;
  const expected = "sha256=" + crypto.createHmac("sha256", secret).update(rawBody).digest("hex");
  if (signature.length !== expected.length) return false;
  return crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected));
}

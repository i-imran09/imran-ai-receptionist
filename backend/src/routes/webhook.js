import express from "express";
import { getConfig } from "../config/env.js";
import { verifyWebhookSignature } from "../middleware/auth.js";
import { getConversation, storeConversation } from "../storage/conversationStorage.js";
import { generateAIResponse } from "../services/groqService.js";
import { sendWhatsAppMessage } from "../services/whatsappService.js";
import { getSystemPrompt } from "../prompts/systemPrompts.js";

const router=express.Router();
const cfg=getConfig();

router.get("/",(req,res)=>{
  if(req.query["hub.mode"]==="subscribe" && req.query["hub.verify_token"]===cfg.meta.verifyToken)
    return res.status(200).send(req.query["hub.challenge"]);
  res.sendStatus(403);
});

router.post("/", async (req,res)=>{
  const raw=req.rawBody || Buffer.from(JSON.stringify(req.body));
  if(!verifyWebhookSignature(raw, req.headers["x-hub-signature-256"])) return res.sendStatus(403);
  res.sendStatus(200);

  try {
    for(const entry of req.body.entry||[]) for(const change of entry.changes||[]) {
      for(const m of change.value?.messages||[]) {
        if(m.type!=="text") continue;
        const phone=m.from, id=m.id;
        let c=await getConversation(phone) || {
          id:`conv_${phone}_${Date.now()}`,callerNumber:phone,currentStatus:"Work",
          messages:[],processedMessageIds:[]
        };
        if((c.processedMessageIds||[]).includes(id)) continue;
        c.processedMessageIds=[...(c.processedMessageIds||[]).slice(-99),id];
        c.messages.push({id,type:"incoming",text:m.text?.body||"",timestamp:Date.now()});

        const history=c.messages.slice(-8).map(x=>({
          role:x.type==="incoming"?"user":"assistant",content:x.text
        }));
        const prompt=getSystemPrompt(c.currentStatus,c.id);
        const reply=await generateAIResponse(prompt,m.text?.body||"",history.slice(0,-1));
        const sent=await sendWhatsAppMessage(phone,reply);
        c.messages.push({id:sent.messageId||`local_${Date.now()}`,type:"outgoing",text:reply,timestamp:Date.now()});
        await storeConversation(c);
      }
    }
  } catch(e){ console.error("[Webhook async]",e.message); }
});
export default router;

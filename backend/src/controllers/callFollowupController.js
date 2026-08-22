import { normalizePhoneNumber } from "../utils/phoneUtils.js";
import { sendWhatsAppTemplate } from "../services/whatsappService.js";
import { storeConversation, getConversation } from "../storage/conversationStorage.js";

export async function processCallFollowup(req,res,next) {
  try {
    const {callerNumber,currentStatus,eventId,callTimestamp} = req.body;
    const phone=normalizePhoneNumber(callerNumber);
    let c=await getConversation(phone);
    const now=new Date().toISOString();

    if (c?.processedEventIds?.includes(eventId))
      return res.json({success:true,conversationId:c.id,deduplicated:true});

    c = c || {id:`conv_${phone}_${Date.now()}`,callerNumber:phone,messages:[],repeatCount:0,processedEventIds:[]};
    c.currentStatus=currentStatus;
    c.lastCallTime=callTimestamp || Date.now();
    c.repeatCount=(c.repeatCount||0)+1;
    c.processedEventIds=[...(c.processedEventIds||[]).slice(-49), eventId];

    // Avoid template spam: one proactive template per 15 minutes.
    const cooldown=15*60*1000;
    const last=Number(c.lastTemplateAt||0);
    if (Date.now()-last >= cooldown) {
      const result=await sendWhatsAppTemplate(phone,currentStatus);
      c.lastTemplateAt=Date.now();
      c.templateMessageId=result.messageId;
    }
    await storeConversation(c);
    res.json({success:true,conversationId:c.id,messageId:c.templateMessageId||null});
  } catch(e){ next(e); }
}

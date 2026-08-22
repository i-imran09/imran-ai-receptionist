import axios from "axios";
import { getConfig } from "../config/env.js";
const cfg = getConfig();

const endpoint = () =>
  `https://graph.facebook.com/${cfg.meta.graphApiVersion}/${cfg.meta.phoneNumberId}/messages`;

async function post(payload) {
  const {data} = await axios.post(endpoint(), {messaging_product:"whatsapp", ...payload}, {
    headers: {Authorization:`Bearer ${cfg.meta.accessToken}`, "Content-Type":"application/json"},
    timeout: 15000
  });
  return data?.messages?.[0]?.id;
}

export async function sendWhatsAppTemplate(to, status) {
  const id = await post({
    to,
    type:"template",
    template:{
      name:cfg.meta.initialTemplate,
      language:{code:cfg.meta.templateLanguage},
      components:[{
        type:"body",
        parameters:[{type:"text", text:status}]
      }]
    }
  });
  return {success:Boolean(id), messageId:id};
}

export async function sendWhatsAppMessage(to, text) {
  const id = await post({to, type:"text", text:{body:String(text).slice(0,4096)}});
  return {success:Boolean(id), messageId:id};
}

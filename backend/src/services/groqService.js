import axios from "axios";
import { getConfig } from "../config/env.js";
const cfg = getConfig();

export async function generateAIResponse(systemPrompt, userMessage, history=[]) {
  try {
    const safeHistory = history.slice(-8);
    const {data} = await axios.post("https://api.groq.com/openai/v1/chat/completions", {
      model: cfg.groq.model,
      messages:[
        {role:"system",content:systemPrompt},
        ...safeHistory,
        {role:"user",content:userMessage}
      ],
      temperature:0.65,
      max_completion_tokens:300
    }, {
      headers:{Authorization:`Bearer ${cfg.groq.apiKey}`,"Content-Type":"application/json"},
      timeout:20000
    });
    return data?.choices?.[0]?.message?.content?.trim() ||
      "Konjam clear-aa sollunga, naan Imran-kaga details note pannikiren.";
  } catch (e) {
    console.error("[Groq]", e.response?.status || e.message);
    return "Sorry, ippo konjam technical issue irukku. Konjam later try pannunga.";
  }
}

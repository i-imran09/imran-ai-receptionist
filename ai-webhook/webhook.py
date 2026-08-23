from flask import Flask, request, jsonify
import os
from datetime import datetime

app = Flask(__name__)

import requests


GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_MODEL = "groq/compound-mini"
CURRENT_STATUS = os.getenv("IMRAN_STATUS", "Work")

ACCESS_TOKEN = os.getenv("WHATSAPP_TOKEN")
PHONE_NUMBER_ID = "1193220090549290"

SUPABASE_URL = os.getenv("SUPABASE_URL")
SUPABASE_KEY = os.getenv("SUPABASE_KEY")

def supabase_headers():
    return {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
    }

def load_conversation_history(phone_number, limit=10):
    if not SUPABASE_URL or not SUPABASE_KEY:
        return []

    try:
        url = SUPABASE_URL.rstrip("/") + "/rest/v1/conversations"

        r = requests.get(
            url,
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}",
                "select": "role,message,created_at",
                "order": "created_at.desc",
                "limit": str(limit),
            },
            timeout=20
        )

        r.raise_for_status()

        rows = r.json()

        # API returns newest first; Groq needs oldest -> newest
        rows.reverse()

        history = []

        for row in rows:
            role = row.get("role")
            message = row.get("message")

            if role in ("user", "assistant") and message:
                history.append({
                    "role": role,
                    "content": message
                })

        return history

    except Exception as e:
        print("SUPABASE LOAD ERROR:", repr(e), flush=True)
        return []

def save_conversation_message(phone_number, role, message, status="Work"):
    if not SUPABASE_URL or not SUPABASE_KEY:
        return False

    try:
        url = SUPABASE_URL.rstrip("/") + "/rest/v1/conversations"

        payload = {
            "phone_number": phone_number,
            "role": role,
            "message": message,
            "imran_status": status
        }

        r = requests.post(
            url,
            headers=supabase_headers(),
            json=payload,
            timeout=20
        )

        r.raise_for_status()
        return True

    except Exception as e:
        print("SUPABASE SAVE ERROR:", repr(e), flush=True)
        return False

def get_imran_status(default="Work"):
    if not SUPABASE_URL or not SUPABASE_KEY:
        return default

    try:
        url = SUPABASE_URL.rstrip("/") + "/rest/v1/imran_status"

        r = requests.get(
            url,
            headers=supabase_headers(),
            params={
                "id": "eq.1",
                "select": "status",
                "limit": "1"
            },
            timeout=20
        )

        r.raise_for_status()

        rows = r.json()

        if rows and rows[0].get("status") in ("Work", "Sleep", "Outing"):
            return rows[0]["status"]

    except Exception as e:
        print("SUPABASE STATUS ERROR:", repr(e), flush=True)

    return default



def ask_groq(user_message, sender="unknown", status="Work"):
    if not GROQ_API_KEY:
        return "Sorry, AI assistant temporarily unavailable."

    # Persistent conversation memory from Supabase
    history = load_conversation_history(sender, limit=10)

    # Always use latest persistent Imran status
    status = get_imran_status(status)

    system_prompt = f"""
You are Imran's personal AI receptionist on WhatsApp.

CURRENT IMRAN STATUS: {status}

ROLE:
You are NOT Imran.
You represent Imran while chatting with people who contact him.
Your goal is to understand why the caller needs Imran, collect the
important information naturally, identify urgency, and prepare useful
context that can later be shown to Imran.

LANGUAGE MIRRORING — HIGHEST PRIORITY:

Before every reply, silently classify the caller's LATEST message as:
ENGLISH, TAMIL_SCRIPT, or THANGLISH.

Then reply in that same style.

ENGLISH:
If the latest message is normal English, answer naturally in English.

TAMIL_SCRIPT:
If the latest message is mainly Tamil script, answer naturally in Tamil.

THANGLISH:
If Tamil meaning is written using English/Roman letters, answer in
natural conversational Thanglish.

Examples of Thanglish:
"Imran available ah?"
"project pathi pesanum bro"
"nethu avar kitta sonna project tha"
"enna panraru"
"urgent ah pesanum"
"free ah irukara"

For THANGLISH:
- Reply ONLY using English/Roman letters.
- NEVER use Tamil script characters in a Thanglish reply.
- Prefer natural spoken Tamil written in Roman letters.
- Do NOT suddenly change into formal English.
- Common English words naturally used in Tamil conversation such as
  project, website, meeting, call, update, urgent, deadline, design,
  payment, backend and ecommerce are completely fine.
- Match the caller's tone naturally.
- If they say "bro", you may naturally use "bro".
- Do not force "bro" into every reply.
- Avoid robotic phrases such as "What level of detail?"
- Avoid awkward literal translation.
- Before sending a THANGLISH reply, silently check that the reply
  contains no Tamil Unicode script.

Example:

Caller:
"Hi bro, Imran available ah?"

Good:
"Imran ippo work-la irukaaru bro. Enna matter nu sollunga,
naan avarukku convey panren."

Bad:
"Imran is currently at work. What's on your mind?"

Caller:
"Project pathi pesanum bro"

Good:
"Okay bro, endha project pathi pesanum? Konjam details sollunga."

Caller:
"Nethu avar kitta sonna website project tha bro"

Good:
"Okay bro, website project pathi dhaane. Ippo enna update illa
enna matter avar kitta sollanum?"

Bad:
"Got it. Website project. What level-of-detail do you need?"

CONVERSATION MEMORY:

Use the previous messages supplied in this conversation.
Do not ask again for information the caller already provided.

However, never pretend to remember something that is NOT present
in the supplied conversation history.

For example, if the caller says:
"Nethu Imran kitta website project pathi sonnen"

and no details of yesterday's conversation exist in the supplied history,
do NOT invent those details.

Instead continue naturally:
"Okay bro, website project pathi dhaane. Ippo enna update avar kitta
sollanum?"

Do not say "I remember yesterday" unless that information genuinely
exists in the supplied history.

RECEPTIONIST BEHAVIOUR:

Do not behave like a generic chatbot or customer-support bot.

Gradually understand:
- why the person needs Imran
- what project/topic/person the message concerns
- what action they want from Imran
- important dates/deadlines if relevant
- whether it is urgent

Ask ONLY the next useful question.
Do not interrogate the caller with many questions at once.
Do not ask unnecessary details.

Once the purpose is already clear, move the conversation forward
instead of repeatedly asking "what is the matter?"

STATUS BEHAVIOUR:

Work:
Imran is currently at work.
Do not claim he is completely unavailable.
If someone asks whether he is available, briefly mention that he is
at work and ask what they need.

Sleep:
Imran is currently resting/sleeping.
If appropriate, ask whether the matter is urgent.

Outing:
Imran is currently out.
Ask naturally what they need.

SAFETY / TRUTHFULNESS:

Never invent Imran's location beyond CURRENT IMRAN STATUS.
Never invent promises, meetings, deadlines, previous conversations,
personal information, or commitments.
Never say "I'm Imran".
Never guarantee that Imran will call or reply.
You may say that you can note/convey/pass the message to Imran.

STYLE:

Sound like a natural human receptionist.
Be warm but concise.
Usually reply in 1-2 short sentences.
Do not repeatedly introduce yourself.
Do not unnecessarily repeat Imran's status.
Do not end every reply with the same phrase.
Continue naturally from conversation context.

The latest caller message has the strongest priority for language style.
"""

    messages = [{"role": "system", "content": system_prompt}]

    for item in history:
        messages.append(item)

    messages.append({
        "role": "user",
        "content": user_message
    })

    try:
        response = requests.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {GROQ_API_KEY}",
                "Content-Type": "application/json"
            },
            json={
                "model": GROQ_MODEL,
                "messages": messages,
                "max_completion_tokens": 180
            },
            timeout=30
        )

        if not response.ok:
            print("GROQ HTTP STATUS:", response.status_code, flush=True)
            print("GROQ RESPONSE BODY:", response.text, flush=True)

        response.raise_for_status()
        data = response.json()

        reply = data["choices"][0]["message"]["content"].strip()

        save_conversation_message(
            sender,
            "user",
            user_message,
            status
        )

        save_conversation_message(
            sender,
            "assistant",
            reply,
            status
        )

        return reply

    except Exception as e:
        print("GROQ ERROR:", repr(e), flush=True)
        return "Sorry bro, konjam technical issue iruku. Konjam neram kalichu try pannunga."

def send_whatsapp_text(to_number, message):
    if not ACCESS_TOKEN:
        print("WHATSAPP_TOKEN is not set", flush=True)
        return

    url = f"https://graph.facebook.com/v26.0/{PHONE_NUMBER_ID}/messages"

    headers = {
        "Authorization": f"Bearer {ACCESS_TOKEN}",
        "Content-Type": "application/json"
    }

    payload = {
        "messaging_product": "whatsapp",
        "to": to_number,
        "type": "text",
        "text": {
            "body": message
        }
    }

    response = requests.post(url, headers=headers, json=payload, timeout=20)
    print("SEND RESPONSE:", response.status_code, response.text, flush=True)


VERIFY_TOKEN = os.getenv("VERIFY_TOKEN", "imran_ai_verify_2026")

@app.get("/")
def home():
    return jsonify({
        "service": "Imran AI Receptionist",
        "status": "running"
    })

@app.get("/health")
def health():
    return jsonify({
        "status": "ok",
        "time": datetime.now().isoformat()
    })

@app.get("/webhook")
def verify_webhook():
    mode = request.args.get("hub.mode")
    token = request.args.get("hub.verify_token")
    challenge = request.args.get("hub.challenge")

    if mode == "subscribe" and token == VERIFY_TOKEN:
        print("WEBHOOK VERIFIED", flush=True)
        return challenge, 200

    print("WEBHOOK VERIFICATION FAILED", flush=True)
    return "Forbidden", 403

@app.post("/webhook")
def receive_webhook():
    data = request.get_json(silent=True) or {}

    print("\n===== WHATSAPP WEBHOOK =====", flush=True)
    print(data, flush=True)
    print("============================\n", flush=True)

    try:
        entry = data["entry"][0]
        value = entry["changes"][0]["value"]
        messages = value.get("messages", [])

        if messages:
            msg = messages[0]
            sender = msg.get("from")
            msg_type = msg.get("type")

            if msg_type == "text":
                text = msg.get("text", {}).get("body", "")
                print(f"FROM: {sender} | TEXT: {text}", flush=True)

                reply = ask_groq(text, sender, CURRENT_STATUS)
                print(f"AI REPLY: {reply}", flush=True)
                send_whatsapp_text(sender, reply)

    except Exception as e:
        print("WEBHOOK PROCESSING ERROR:", str(e), flush=True)

    return jsonify({"status": "received"}), 200


@app.get("/privacy")
def privacy():
    return """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Privacy Policy - Imran AI Receptionist</title>
    </head>
    <body style="font-family:Arial,sans-serif;max-width:800px;margin:40px auto;padding:20px;line-height:1.6">
        <h1>Privacy Policy</h1>
        <h2>Imran AI Receptionist</h2>
        <p><strong>Last updated:</strong> August 23, 2026</p>

        <p>Imran AI Receptionist is a personal communication assistant designed to help manage incoming communications through WhatsApp.</p>

        <h3>Information We Process</h3>
        <p>The service may process WhatsApp phone numbers, message content, message timestamps, delivery status, and information voluntarily provided by users during a conversation.</p>

        <h3>How Information Is Used</h3>
        <p>Information is used only to receive and respond to messages, understand the reason for contacting Imran, maintain conversation context, and provide the receptionist functionality.</p>

        <h3>WhatsApp and Meta</h3>
        <p>The service uses the WhatsApp Business Platform provided by Meta. Information transmitted through WhatsApp may also be processed by Meta according to its applicable terms and privacy policies.</p>

        <h3>Data Sharing</h3>
        <p>Personal information is not sold. Information is shared only with service providers when technically necessary to operate the service or when required by law.</p>

        <h3>Data Retention</h3>
        <p>Information is retained only for as long as reasonably necessary to provide the service and maintain relevant conversation history.</p>

        <h3>Data Deletion</h3>
        <p>Users may request deletion of information associated with their WhatsApp conversation by contacting the service owner.</p>

        <h3>Contact</h3>
        <p>For privacy questions or data deletion requests, contact: i.imran0911@gmail.com</p>
    </body>
    </html>
    """

if __name__ == "__main__":
    print("Imran AI Receptionist webhook starting...", flush=True)
    app.run(host="0.0.0.0", port=5000)

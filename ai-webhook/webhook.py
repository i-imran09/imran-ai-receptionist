from flask import Flask, request, jsonify
import os
import re
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

        if rows and rows[0].get("status") in (
            "Work", "Sleep", "Outing", "Driving", "Meeting",
            "Eating", "Travel", "Exercise", "Personal Work",
            "Family Time", "Prayer", "Busy", "Free"
        ):
            return rows[0]["status"]

    except Exception as e:
        print("SUPABASE STATUS ERROR:", repr(e), flush=True)

    return default




def get_caller_name(phone_number):
    """Return saved caller name, or None if this caller is new."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        return None

    try:
        url = SUPABASE_URL.rstrip("/") + "/rest/v1/caller_profiles"

        r = requests.get(
            url,
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}",
                "select": "caller_name",
                "limit": "1"
            },
            timeout=20
        )

        r.raise_for_status()
        rows = r.json()

        if rows:
            name = rows[0].get("caller_name")
            if name and name.strip():
                return name.strip()

    except Exception as e:
        print("CALLER NAME LOAD ERROR:", repr(e), flush=True)

    return None


def save_caller_name(phone_number, caller_name):
    """Create/update caller profile with the caller's name."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        return False

    caller_name = (caller_name or "").strip()

    if not caller_name:
        return False

    try:
        url = SUPABASE_URL.rstrip("/") + "/rest/v1/caller_profiles"

        payload = {
            "phone_number": phone_number,
            "caller_name": caller_name,
            "updated_at": datetime.utcnow().isoformat()
        }

        headers = supabase_headers().copy()
        headers["Prefer"] = "resolution=merge-duplicates"

        r = requests.post(
            url,
            headers=headers,
            params={"on_conflict": "phone_number"},
            json=payload,
            timeout=20
        )

        r.raise_for_status()

        print(
            f"CALLER PROFILE SAVED: {phone_number} -> {caller_name}",
            flush=True
        )
        return True

    except Exception as e:
        print("CALLER NAME SAVE ERROR:", repr(e), flush=True)
        return False



def looks_like_name(text):
    """
    Conservative name detector.
    Accept short human-name-like replies and reject obvious conversation text.
    """
    if not text:
        return None

    raw = text.strip()

    # Remove common intro phrases
    cleaned = raw
    prefixes = [
        r"(?i)^my name is\s+",
        r"(?i)^i am\s+",
        r"(?i)^i'm\s+",
        r"(?i)^this is\s+",
        r"(?i)^naan\s+",
        r"(?i)^na\s+",
        r"(?i)^en peru\s+",
        r"(?i)^ennoda peru\s+",
        r"(?i)^name\s*[:\-]\s*",
    ]

    for pattern in prefixes:
        cleaned = re.sub(pattern, "", cleaned).strip()

    # Remove casual suffixes
    cleaned = re.sub(r"(?i)\s+(bro|boss|anna|machan|machi|sir)$", "", cleaned).strip()

    # Reject long sentences
    words = cleaned.split()
    if not (1 <= len(words) <= 4):
        return None

    # Reject likely topic/status words
    blocked = {
        "project", "website", "ecommerce", "payment", "meeting",
        "urgent", "work", "sleep", "outing", "driving", "travel",
        "busy", "free", "call", "update", "backend", "frontend",
        "design", "deadline", "matter", "help", "available"
    }

    lowered_words = {w.lower().strip(".,!?") for w in words}

    if lowered_words & blocked:
        return None

    # Allow letters, spaces, apostrophe, dot, hyphen only
    if not re.fullmatch(r"[A-Za-z][A-Za-z .'-]{0,60}", cleaned):
        return None

    # Title-case for clean storage
    return " ".join(part.capitalize() for part in cleaned.split())


def previous_ai_asked_name(phone_number):
    history = load_conversation_history(phone_number, limit=4)

    for item in reversed(history):
        if item.get("role") != "assistant":
            continue

        text = (item.get("content") or "").lower()

        name_markers = [
            "unga name",
            "your name",
            "may i know your name",
            "name enna",
            "peru enna",
            "உங்கள் பெயர்",
            "உங்க பெயர்"
        ]

        return any(marker in text for marker in name_markers)

    return False

def ask_groq(user_message, sender="unknown", status="Work"):
    if not GROQ_API_KEY:
        return "Sorry, AI assistant temporarily unavailable."

    # Persistent conversation memory from Supabase
    history = load_conversation_history(sender, limit=10)

    # Always use latest persistent Imran status
    status = get_imran_status(status)

    # Load persistent caller identity
    caller_name = get_caller_name(sender)

    caller_identity = (
        f"KNOWN CALLER NAME: {caller_name}"
        if caller_name
        else "KNOWN CALLER NAME: UNKNOWN"
    )

    system_prompt = f"""
You are Imran's personal AI receptionist on WhatsApp.

CURRENT IMRAN STATUS: {status}
{caller_identity}

CALLER IDENTITY RULES:

Caller identity is important and must be collected naturally.

If KNOWN CALLER NAME is UNKNOWN:
- Your first priority is to learn the caller's name.
- Ask their name naturally in the same language/style they are using.
- Do not continue into a long discussion without learning their name.
- You may briefly answer an immediate availability question, but also ask their name.
- Ask only for their name first; do not ask name, reason, deadline and urgency all at once.

Examples:

Thanglish:
"Imran ippo work-la irukaaru bro. Unga name enna nu sollunga?"

English:
"Imran is currently at work. May I know your name?"

Tamil:
Reply naturally in Tamil and ask their name.

If KNOWN CALLER NAME contains a real saved name:
- Never ask for their name again.
- Treat that name as the caller's identity.
- You may use their name naturally when useful.
- Do not repeat their name unnecessarily in every message.

Never guess a caller's name from a project name, company name,
topic, greeting, or ordinary conversation.

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

                caller_name = get_caller_name(sender)

                # If we previously asked for the caller's name,
                # try to extract and store it before generating the next reply.
                if not caller_name and previous_ai_asked_name(sender):
                    extracted_name = looks_like_name(text)

                    if extracted_name:
                        if save_caller_name(sender, extracted_name):
                            caller_name = extracted_name
                            print(
                                f"CALLER NAME CAPTURED: {sender} -> {caller_name}",
                                flush=True
                            )

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

# ============================================================
# ANDROID APP - CONVERSATION / DATABASE API
# ============================================================

def require_app_client():
    """Protect private Android management APIs."""
    expected = os.getenv("APP_CLIENT_TOKEN", "")
    supplied = request.headers.get("Authorization", "")

    if not expected:
        return False

    return supplied == f"Bearer {expected}"


@app.get("/api/conversations")
def api_conversations():
    if not require_app_client():
        return jsonify({"error": "Unauthorized"}), 401

    if not SUPABASE_URL or not SUPABASE_KEY:
        return jsonify({"error": "Database unavailable"}), 503

    try:
        # Load caller profiles
        profile_response = requests.get(
            SUPABASE_URL.rstrip("/") + "/rest/v1/caller_profiles",
            headers=supabase_headers(),
            params={
                "select": "phone_number,caller_name,created_at,updated_at",
                "order": "updated_at.desc"
            },
            timeout=20
        )
        profile_response.raise_for_status()
        profiles = profile_response.json()

        names = {
            row.get("phone_number"): row.get("caller_name")
            for row in profiles
        }

        # Load messages
        message_response = requests.get(
            SUPABASE_URL.rstrip("/") + "/rest/v1/conversations",
            headers=supabase_headers(),
            params={
                "select": "phone_number,role,message,imran_status,created_at",
                "order": "created_at.asc"
            },
            timeout=20
        )
        message_response.raise_for_status()
        messages = message_response.json()

        grouped = {}

        for msg in messages:
            phone = msg.get("phone_number")

            if not phone:
                continue

            if phone not in grouped:
                grouped[phone] = {
                    "phone_number": phone,
                    "caller_name": names.get(phone),
                    "message_count": 0,
                    "last_message": None,
                    "last_message_at": None,
                    "messages": []
                }

            item = {
                "role": msg.get("role"),
                "message": msg.get("message"),
                "imran_status": msg.get("imran_status"),
                "created_at": msg.get("created_at")
            }

            grouped[phone]["messages"].append(item)
            grouped[phone]["message_count"] += 1
            grouped[phone]["last_message"] = msg.get("message")
            grouped[phone]["last_message_at"] = msg.get("created_at")

        # Also show saved callers who currently have no messages
        for phone, caller_name in names.items():
            if phone not in grouped:
                grouped[phone] = {
                    "phone_number": phone,
                    "caller_name": caller_name,
                    "message_count": 0,
                    "last_message": None,
                    "last_message_at": None,
                    "messages": []
                }

        result = list(grouped.values())

        result.sort(
            key=lambda x: x.get("last_message_at") or "",
            reverse=True
        )

        return jsonify({
            "success": True,
            "caller_count": len(result),
            "conversations": result
        }), 200

    except Exception as e:
        print("CONVERSATION API ERROR:", repr(e), flush=True)
        return jsonify({
            "success": False,
            "error": "Unable to load conversations"
        }), 500


@app.get("/api/database/stats")
def api_database_stats():
    if not require_app_client():
        return jsonify({"error": "Unauthorized"}), 401

    if not SUPABASE_URL or not SUPABASE_KEY:
        return jsonify({"error": "Database unavailable"}), 503

    try:
        profiles_response = requests.get(
            SUPABASE_URL.rstrip("/") + "/rest/v1/caller_profiles",
            headers=supabase_headers(),
            params={
                "select": "phone_number,caller_name"
            },
            timeout=20
        )
        profiles_response.raise_for_status()
        profiles = profiles_response.json()

        conversations_response = requests.get(
            SUPABASE_URL.rstrip("/") + "/rest/v1/conversations",
            headers=supabase_headers(),
            params={
                "select": "phone_number,role,message,imran_status,created_at"
            },
            timeout=20
        )
        conversations_response.raise_for_status()
        conversations = conversations_response.json()

        # Approximate JSON payload size used by app records.
        # This is NOT the complete PostgreSQL project size/quota.
        import json

        profile_bytes = len(
            json.dumps(profiles, ensure_ascii=False).encode("utf-8")
        )

        conversation_bytes = len(
            json.dumps(conversations, ensure_ascii=False).encode("utf-8")
        )

        total_bytes = profile_bytes + conversation_bytes

        return jsonify({
            "success": True,
            "caller_profiles": len(profiles),
            "conversation_messages": len(conversations),
            "approx_data_bytes": total_bytes,
            "approx_data_kb": round(total_bytes / 1024, 2),
            "approx_data_mb": round(total_bytes / (1024 * 1024), 3)
        }), 200

    except Exception as e:
        print("DATABASE STATS ERROR:", repr(e), flush=True)
        return jsonify({
            "success": False,
            "error": "Unable to calculate database statistics"
        }), 500

# ============================================================
# ANDROID APP - DATABASE DELETE API
# ============================================================

@app.delete("/api/database/conversations/<phone_number>")
def api_delete_conversation(phone_number):
    if not require_app_client():
        return jsonify({"error": "Unauthorized"}), 401

    try:
        r = requests.delete(
            SUPABASE_URL.rstrip("/") + "/rest/v1/conversations",
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}"
            },
            timeout=20
        )

        r.raise_for_status()

        return jsonify({
            "success": True,
            "phone_number": phone_number,
            "deleted": "conversation"
        }), 200

    except Exception as e:
        print("DELETE CONVERSATION ERROR:", repr(e), flush=True)
        return jsonify({
            "success": False,
            "error": "Unable to delete conversation"
        }), 500


@app.delete("/api/database/caller/<phone_number>")
def api_delete_caller(phone_number):
    if not require_app_client():
        return jsonify({"error": "Unauthorized"}), 401

    try:
        # Delete conversation history first
        conversation_response = requests.delete(
            SUPABASE_URL.rstrip("/") + "/rest/v1/conversations",
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}"
            },
            timeout=20
        )

        conversation_response.raise_for_status()

        # Then delete saved caller profile
        profile_response = requests.delete(
            SUPABASE_URL.rstrip("/") + "/rest/v1/caller_profiles",
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}"
            },
            timeout=20
        )

        profile_response.raise_for_status()

        return jsonify({
            "success": True,
            "phone_number": phone_number,
            "deleted": "caller_and_conversation"
        }), 200

    except Exception as e:
        print("DELETE CALLER ERROR:", repr(e), flush=True)
        return jsonify({
            "success": False,
            "error": "Unable to delete caller data"
        }), 500


@app.delete("/api/database/conversations")
def api_clear_conversations():
    if not require_app_client():
        return jsonify({"error": "Unauthorized"}), 401

    try:
        r = requests.delete(
            SUPABASE_URL.rstrip("/") + "/rest/v1/conversations",
            headers=supabase_headers(),
            params={
                "phone_number": "not.is.null"
            },
            timeout=20
        )

        r.raise_for_status()

        return jsonify({
            "success": True,
            "deleted": "all_conversations"
        }), 200

    except Exception as e:
        print("CLEAR CONVERSATIONS ERROR:", repr(e), flush=True)
        return jsonify({
            "success": False,
            "error": "Unable to clear conversations"
        }), 500

@app.get("/api/conversations/<phone_number>")
def api_single_conversation(phone_number):
    if not require_app_client():
        return jsonify({"error": "Unauthorized"}), 401

    if not SUPABASE_URL or not SUPABASE_KEY:
        return jsonify({"error": "Database unavailable"}), 503

    try:
        profile_response = requests.get(
            SUPABASE_URL.rstrip("/") + "/rest/v1/caller_profiles",
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}",
                "select": "phone_number,caller_name",
                "limit": "1"
            },
            timeout=20
        )
        profile_response.raise_for_status()

        profiles = profile_response.json()
        caller_name = (
            profiles[0].get("caller_name")
            if profiles else None
        )

        message_response = requests.get(
            SUPABASE_URL.rstrip("/") + "/rest/v1/conversations",
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}",
                "select": "role,message,imran_status,created_at",
                "order": "created_at.asc"
            },
            timeout=20
        )
        message_response.raise_for_status()

        messages = message_response.json()

        return jsonify({
            "success": True,
            "phone_number": phone_number,
            "caller_name": caller_name,
            "message_count": len(messages),
            "messages": messages
        }), 200

    except Exception as e:
        print("SINGLE CONVERSATION ERROR:", repr(e), flush=True)
        return jsonify({
            "success": False,
            "error": "Unable to load conversation"
        }), 500

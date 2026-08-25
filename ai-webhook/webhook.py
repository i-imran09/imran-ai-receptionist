from flask import Flask, request, jsonify
import os
import re
from datetime import datetime
from zoneinfo import ZoneInfo

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




def get_caller_state(phone_number):
    """Load structured receptionist state for one caller."""

    default_state = {
        "language_preference": None,
        "caller_reason": None,
        "callback_requested": False,
        "callback_time": None,
        "emergency": False,
        "emergency_reason": None,
    }

    if not SUPABASE_URL or not SUPABASE_KEY:
        return default_state

    try:
        url = (
            SUPABASE_URL.rstrip("/")
            + "/rest/v1/caller_state"
        )

        r = requests.get(
            url,
            headers=supabase_headers(),
            params={
                "phone_number": f"eq.{phone_number}",
                "select": (
                    "language_preference,"
                    "caller_reason,"
                    "callback_requested,"
                    "callback_time,"
                    "emergency,"
                    "emergency_reason"
                ),
                "limit": "1",
            },
            timeout=20
        )

        r.raise_for_status()
        rows = r.json()

        if not rows:
            return default_state

        state = default_state.copy()
        state.update(rows[0])

        return state

    except Exception as e:
        print(
            "CALLER STATE LOAD ERROR:",
            repr(e),
            flush=True
        )
        return default_state


def save_caller_state(phone_number, **updates):
    """Create/update only validated caller-state fields."""

    allowed = {
        "language_preference",
        "caller_reason",
        "callback_requested",
        "callback_time",
        "emergency",
        "emergency_reason",
    }

    payload = {
        key: value
        for key, value in updates.items()
        if key in allowed
    }

    if not payload:
        return False

    if not SUPABASE_URL or not SUPABASE_KEY:
        return False

    payload["phone_number"] = phone_number
    payload["updated_at"] = datetime.utcnow().isoformat()

    try:
        url = (
            SUPABASE_URL.rstrip("/")
            + "/rest/v1/caller_state"
        )

        headers = supabase_headers().copy()
        headers["Prefer"] = "resolution=merge-duplicates"

        r = requests.post(
            url,
            headers=headers,
            params={
                "on_conflict": "phone_number"
            },
            json=payload,
            timeout=20
        )

        r.raise_for_status()

        print(
            "CALLER STATE SAVED:",
            phone_number,
            payload,
            flush=True
        )

        return True

    except Exception as e:
        print(
            "CALLER STATE SAVE ERROR:",
            repr(e),
            flush=True
        )
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


def analyze_caller_message(
    user_message,
    sender="unknown"
):
    """
    Extract structured receptionist state from the caller message.

    This function does NOT generate the conversational reply.
    It only extracts validated state such as:
    language, reason, callback request/time and emergencies.
    """

    if not GROQ_API_KEY:
        return {}

    existing_state = get_caller_state(sender)
    caller_name = get_caller_name(sender)

    history = load_conversation_history(
        sender,
        limit=20
    )

    now_ist = datetime.now(
        ZoneInfo("Asia/Kolkata")
    )

    history_text = []

    for item in history[-12:]:
        role = item.get("role", "")
        content = item.get("content", "")

        if content:
            history_text.append(
                f"{role}: {content}"
            )

    prompt = f"""
You are a strict information extraction engine for
Imran's personal AI receptionist.

CURRENT TIME:
{now_ist.isoformat()}

TIMEZONE:
Asia/Kolkata

KNOWN CALLER NAME:
{caller_name or "UNKNOWN"}

EXISTING STATE:
{json.dumps(existing_state, ensure_ascii=False)}

RECENT CONVERSATION:
{chr(10).join(history_text)}

LATEST CALLER MESSAGE:
{user_message}

Return ONLY valid JSON.

Required JSON shape:

{{
  "language_preference": null,
  "caller_reason": null,
  "callback_requested": null,
  "callback_time": null,
  "emergency": null,
  "emergency_reason": null
}}

RULES:

LANGUAGE:
- Allowed values:
  "THANGLISH", "ENGLISH", "TAMIL"
- If an earlier explicit language preference already exists,
  preserve it unless the caller explicitly asks to change.
- Messages such as "hmm", "...", "ok", emoji or "sari"
  must NOT change the established language.
- Use null if language cannot be determined safely.

CALLER REASON:
- Store a concise but specific reason.
- Capture what the caller needs from Imran.
- Do NOT store generic text such as:
  "wants to talk", "needs help", "calling Imran"
  when the conversation contains a more precise reason.
- If the reason is still unclear, return null.
- Never invent details.

CALLBACK REQUEST:
- true only when the caller actually asks/wants Imran
  to call or contact them back.
- false only when they clearly say they do NOT need a callback.
- Otherwise null.

CALLBACK TIME:
- Only return a time if the caller actually supplied
  enough scheduling information.
- Convert relative expressions using CURRENT TIME.
Examples:
  "10 mins" -> current time + 10 minutes
  "after one hour" -> current time + 1 hour
  "tomorrow 9 am" -> tomorrow at 09:00
- Return ISO-8601 including +05:30 offset.
- If ambiguous, return null.
- Never invent a callback time.

EMERGENCY:
- true ONLY for a clearly time-sensitive emergency:
  accident, urgent medical situation, immediate safety danger,
  serious family emergency, or similarly immediate danger.
- The word "urgent" alone is NOT automatically an emergency.
- false only when context clearly establishes it is not emergency.
- Otherwise null.

EMERGENCY REASON:
- Only provide when emergency=true.
- Use the caller's actual stated emergency reason.
- Never invent or exaggerate.

Do not include markdown.
Do not include explanations.
"""

    try:
        response = requests.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {GROQ_API_KEY}",
                "Content-Type": "application/json"
            },
            json={
                "model": GROQ_MODEL,
                "messages": [
                    {
                        "role": "system",
                        "content": (
                            "Return strict JSON only. "
                            "Never invent missing information."
                        )
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                "max_completion_tokens": 350,
                "temperature": 0
            },
            timeout=30
        )

        response.raise_for_status()

        raw = (
            response.json()["choices"][0]
            ["message"]["content"]
            .strip()
        )

        # Remove accidental markdown fences.
        raw = re.sub(
            r"^```(?:json)?\s*",
            "",
            raw,
            flags=re.IGNORECASE
        )

        raw = re.sub(
            r"\s*```$",
            "",
            raw
        ).strip()

        data = json.loads(raw)

        validated = {}

        # ----------------------------------------
        # LANGUAGE
        # ----------------------------------------

        language = data.get(
            "language_preference"
        )

        if language in (
            "THANGLISH",
            "ENGLISH",
            "TAMIL"
        ):
            validated[
                "language_preference"
            ] = language

        # ----------------------------------------
        # REASON
        # ----------------------------------------

        reason = data.get("caller_reason")

        if isinstance(reason, str):
            reason = reason.strip()

            if (
                5 <= len(reason) <= 500
            ):
                validated[
                    "caller_reason"
                ] = reason

        # ----------------------------------------
        # CALLBACK REQUEST
        # ----------------------------------------

        callback_requested = data.get(
            "callback_requested"
        )

        if isinstance(
            callback_requested,
            bool
        ):
            validated[
                "callback_requested"
            ] = callback_requested

        # ----------------------------------------
        # CALLBACK TIME
        # ----------------------------------------

        callback_time = data.get(
            "callback_time"
        )

        if isinstance(callback_time, str):
            callback_time = callback_time.strip()

            try:
                parsed = datetime.fromisoformat(
                    callback_time.replace(
                        "Z",
                        "+00:00"
                    )
                )

                # Reject timezone-less timestamps.
                if parsed.tzinfo is not None:

                    # Do not accept obviously old times.
                    if (
                        parsed.timestamp()
                        >= now_ist.timestamp() - 60
                    ):
                        validated[
                            "callback_time"
                        ] = parsed.isoformat()

            except Exception:
                pass

        # ----------------------------------------
        # EMERGENCY
        # ----------------------------------------

        emergency = data.get("emergency")

        if emergency is True:
            validated["emergency"] = True

            emergency_reason = data.get(
                "emergency_reason"
            )

            if isinstance(
                emergency_reason,
                str
            ):
                emergency_reason = (
                    emergency_reason.strip()
                )

                if (
                    5 <= len(
                        emergency_reason
                    ) <= 500
                ):
                    validated[
                        "emergency_reason"
                    ] = emergency_reason

        elif emergency is False:
            # Never automatically clear an existing
            # emergency from a casual later message.
            if not existing_state.get(
                "emergency"
            ):
                validated[
                    "emergency"
                ] = False

        print(
            "CALLER STRUCTURED ANALYSIS:",
            sender,
            validated,
            flush=True
        )

        return validated

    except Exception as e:
        print(
            "CALLER ANALYSIS ERROR:",
            repr(e),
            flush=True
        )

        return {}


def update_caller_state_from_message(
    user_message,
    sender="unknown"
):
    """
    Analyze one message and persist only validated fields.
    """

    extracted = analyze_caller_message(
        user_message,
        sender
    )

    if not extracted:
        return get_caller_state(sender)

    save_caller_state(
        sender,
        **extracted
    )

    return get_caller_state(sender)



def ask_groq(user_message, sender="unknown", status="Work"):
    if not GROQ_API_KEY:
        return "Sorry, AI assistant temporarily unavailable."

    # Persistent conversation memory from Supabase
    history = load_conversation_history(sender, limit=20)

    # Always use latest persistent Imran status
    status = get_imran_status(status)

    # Load persistent caller identity
    caller_name = get_caller_name(sender)

    # Load structured receptionist state
    caller_state = get_caller_state(sender)

    caller_identity = (
        f"KNOWN CALLER NAME: {caller_name}"
        if caller_name
        else "KNOWN CALLER NAME: UNKNOWN"
    )

    structured_context = f"""
STRUCTURED CALLER STATE:
Language preference: {caller_state.get("language_preference") or "UNKNOWN"}
Known reason: {caller_state.get("caller_reason") or "UNKNOWN"}
Callback requested: {caller_state.get("callback_requested")}
Callback time: {caller_state.get("callback_time") or "UNKNOWN"}
Emergency: {caller_state.get("emergency")}
Emergency reason: {caller_state.get("emergency_reason") or "UNKNOWN"}
"""

    system_prompt = f"""
You are Imran's personal AI receptionist on WhatsApp.

CURRENT IMRAN STATUS: {status}
{caller_identity}

{structured_context}

STRUCTURED STATE RULES:

- Treat STRUCTURED CALLER STATE as already-known verified context.
- Do not ask again for a reason if Known reason is already specific enough.
- Do not ask again whether callback is needed if Callback requested is already true.
- Do not ask again for callback time when Callback time is already known.
- If Emergency is true, keep the response concise and focused.
- Never invent new emergency details beyond Emergency reason.
- If Language preference is THANGLISH, continue in Thanglish even when the latest
  message is only "hmm", "ok", "...", emoji, or another short acknowledgement.
- If Language preference is ENGLISH or TAMIL, preserve that preference similarly.

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

CONVERSATION CONTINUITY — VERY IMPORTANT:

Treat this as one continuing receptionist conversation, not a series of
independent questions.

LANGUAGE LOCK:
- If the caller explicitly asked for Thanglish earlier in this conversation,
  continue in natural Thanglish until they explicitly ask to switch language.
- Do NOT switch to English just because the latest message is short such as:
  "hmm", "hm", "ok", "okay", "sari", "...", "👍", "yes", "no".
- Short acknowledgements inherit the established conversation language.
- If no language preference has been established, use the caller's latest
  meaningful message to determine the language.

NO REPETITION:
- Never repeat a question whose answer is already present in the conversation.
- Never repeatedly reconfirm a callback time that the caller already gave.
- Never keep repeating the same summary after "hmm", "okay", "sari", "...".
- If the caller sends only a low-information acknowledgement and the important
  details are already clear, answer briefly or naturally close the exchange.

REASON COLLECTION:
Your job is to understand the caller's ACTUAL reason accurately.
A useful reason should capture:
- what they need from Imran
- the topic/project/person involved
- the specific action they want from Imran

If the reason is still vague, ask ONE natural follow-up question.
Do not ask multiple questions in the same reply.

CALLBACK TIME:
If the caller says they want Imran to call them:
- Ask when they can be reached ONLY if a callback time is not already known.
- Accept natural answers such as:
  "10 mins", "after 1 hour", "today evening", "tomorrow 9 am",
  "night 8 mani", or an exact date/time.
- Once a callback time is given, do not ask for it again unless it is genuinely
  ambiguous.

IMPORTANT:
The reminder system is handled by the application, not by you.
Until the application explicitly confirms that a reminder was created,
NEVER say:
- "reminder set panniten"
- "Imran kandippa call pannuvaaru"
- "Imran 10 mins la call pannuvaaru"

Instead say naturally:
Thanglish:
"Sari, note panniten. Imran-ku convey panren."

English:
"Okay, I've noted that and I'll pass it to Imran."

EMERGENCY AWARENESS:
If the caller clearly describes an urgent situation such as an accident,
medical emergency, immediate safety issue, serious family emergency, or another
time-sensitive danger:
- Treat it as urgent.
- Do not invent urgency when the caller has not expressed it.
- Ask only the minimum useful clarification if absolutely needed.
- Do not make promises on Imran's behalf.

LOW-INFORMATION MESSAGE EXAMPLES:

Caller: "hmm"
If the reason/time is already clear:
Good: "Sari bro, note panniten."
Bad: Ask the same callback question again.

Caller: "..."
If nothing new was provided:
Good: Keep the reply very short or do not restart the conversation.
Bad: Repeat the entire summary.

Caller previously requested Thanglish, then says: "okay"
Good: "Sari bro."
Bad: "Okay. Is there anything else I can help you with?"

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
                "max_completion_tokens": 220,
                "temperature": 0.35
            },
            timeout=30
        )

        if not response.ok:
            print("GROQ HTTP STATUS:", response.status_code, flush=True)
            print("GROQ RESPONSE BODY:", response.text, flush=True)

        response.raise_for_status()
        data = response.json()

        reply = data["choices"][0]["message"]["content"].strip()

        reply = re.sub(
            r"<think>.*?</think>",
            "",
            reply,
            flags=re.IGNORECASE | re.DOTALL
        ).strip()

        if not reply:
            reply = (
                "Sari, note panniten. Imran-ku convey panren."
                if caller_name
                else "Unga name enna nu sollunga?"
            )

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


def send_whatsapp_template(
    to_number,
    template_name="imran_call_followup",
    language_code="en"
):
    if not ACCESS_TOKEN:
        print("WHATSAPP_TOKEN is not set", flush=True)
        return False, None

    url = (
        f"https://graph.facebook.com/v26.0/"
        f"{PHONE_NUMBER_ID}/messages"
    )

    headers = {
        "Authorization": f"Bearer {ACCESS_TOKEN}",
        "Content-Type": "application/json"
    }

    payload = {
        "messaging_product": "whatsapp",
        "to": to_number,
        "type": "template",
        "template": {
            "name": template_name,
            "language": {
                "code": language_code
            }
        }
    }

    try:
        response = requests.post(
            url,
            headers=headers,
            json=payload,
            timeout=20
        )

        print(
            "TEMPLATE SEND RESPONSE:",
            response.status_code,
            response.text,
            flush=True
        )

        if not response.ok:
            return False, None

        data = response.json()

        message_id = None

        messages = data.get("messages") or []

        if messages:
            message_id = messages[0].get("id")

        return True, message_id

    except Exception as e:
        print(
            "TEMPLATE SEND ERROR:",
            repr(e),
            flush=True
        )
        return False, None


def save_contact_display_name(
    phone_number,
    contact_display_name
):
    if (
        not SUPABASE_URL or
        not SUPABASE_KEY or
        not contact_display_name
    ):
        return False

    name = contact_display_name.strip()

    if not name:
        return False

    try:
        url = (
            SUPABASE_URL.rstrip("/") +
            "/rest/v1/caller_profiles"
        )

        headers = supabase_headers().copy()
        headers["Prefer"] = "resolution=merge-duplicates"

        payload = {
            "phone_number": phone_number,
            "contact_display_name": name,
            "updated_at": datetime.utcnow().isoformat()
        }

        r = requests.post(
            url,
            headers=headers,
            params={
                "on_conflict": "phone_number"
            },
            json=payload,
            timeout=20
        )

        r.raise_for_status()

        print(
            f"CONTACT DISPLAY NAME SAVED: "
            f"{phone_number} -> {name}",
            flush=True
        )

        return True

    except Exception as e:
        print(
            "CONTACT DISPLAY NAME SAVE ERROR:",
            repr(e),
            flush=True
        )
        return False


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

                # Extract and persist structured caller state first.
                # This lets the conversational AI immediately use
                # the latest reason / callback / emergency information.
                state = update_caller_state_from_message(
                    text,
                    sender
                )

                print(
                    "CURRENT CALLER STATE:",
                    sender,
                    state,
                    flush=True
                )

                reply = ask_groq(
                    text,
                    sender,
                    CURRENT_STATUS
                )

                print(
                    f"AI REPLY: {reply}",
                    flush=True
                )

                send_whatsapp_text(
                    sender,
                    reply
                )

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

@app.post("/call-followup")
def call_followup():

    if not require_app_client():
        return jsonify({
            "success": False,
            "error": "Unauthorized"
        }), 401

    data = request.get_json(silent=True) or {}

    caller_number = str(
        data.get("callerNumber") or ""
    ).strip()

    current_status = str(
        data.get("currentStatus") or "Work"
    ).strip()

    event_id = str(
        data.get("eventId") or ""
    ).strip()

    call_timestamp = data.get("callTimestamp")

    contact_display_name = (
        data.get("contactDisplayName")
    )

    call_result = str(
        data.get("callResult") or ""
    ).upper()

    try:
        sim_slot = int(
            data.get("simSlot", 0)
        )
    except Exception:
        sim_slot = 0

    # Backend safety check too.
    if sim_slot != 1:
        return jsonify({
            "success": False,
            "error": "SIM 1 only"
        }), 400

    if call_result not in (
        "MISSED",
        "REJECTED"
    ):
        return jsonify({
            "success": False,
            "error": "Only missed/rejected calls allowed"
        }), 400

    normalized = "".join(
        ch for ch in caller_number
        if ch.isdigit()
    )

    if len(normalized) < 10:
        return jsonify({
            "success": False,
            "error": "Invalid caller number"
        }), 400

    # Saved Android contact name is only a hint.
    # Never overwrite caller_name (actual/preferred name).
    if contact_display_name:
        save_contact_display_name(
            normalized,
            contact_display_name
        )

    sent, message_id = (
        send_whatsapp_template(
            normalized,
            template_name="imran_call_followup",
            language_code="en"
        )
    )

    print(
        "CALL FOLLOWUP:",
        {
            "number": normalized,
            "result": call_result,
            "sim": sim_slot,
            "contact": contact_display_name,
            "status": current_status,
            "event_id": event_id,
            "template_sent": sent
        },
        flush=True
    )

    if not sent:
        return jsonify({
            "success": False,
            "error": "Template send failed"
        }), 502

    return jsonify({
        "success": True,
        "conversationId": normalized,
        "messageId": message_id,
        "eventId": event_id,
        "callTimestamp": call_timestamp
    }), 200

@app.get("/api/caller-state")
def api_caller_state():
    if not require_app_client():
        return jsonify({
            "success": False,
            "error": "Unauthorized"
        }), 401

    phone_number = str(
        request.args.get("phone_number") or ""
    ).strip()

    if not phone_number:
        return jsonify({
            "success": False,
            "error": "phone_number is required"
        }), 400

    state = get_caller_state(phone_number)
    caller_name = get_caller_name(phone_number)

    return jsonify({
        "success": True,
        "phone_number": phone_number,
        "caller_name": caller_name,
        "language_preference":
            state.get("language_preference"),
        "caller_reason":
            state.get("caller_reason"),
        "callback_requested":
            bool(state.get("callback_requested")),
        "callback_time":
            state.get("callback_time"),
        "emergency":
            bool(state.get("emergency")),
        "emergency_reason":
            state.get("emergency_reason")
    }), 200


@app.get("/api/actionable-callers")
def api_actionable_callers():
    """
    Return callers that currently need Android attention:
    - scheduled callback
    - emergency
    """

    if not require_app_client():
        return jsonify({
            "success": False,
            "error": "Unauthorized"
        }), 401

    if not SUPABASE_URL or not SUPABASE_KEY:
        return jsonify({
            "success": False,
            "error": "Database unavailable"
        }), 503

    try:
        url = (
            SUPABASE_URL.rstrip("/")
            + "/rest/v1/caller_state"
        )

        r = requests.get(
            url,
            headers=supabase_headers(),
            params={
                "select": (
                    "phone_number,"
                    "language_preference,"
                    "caller_reason,"
                    "callback_requested,"
                    "callback_time,"
                    "emergency,"
                    "emergency_reason,"
                    "updated_at"
                ),
                "or": (
                    "(callback_requested.eq.true,"
                    "emergency.eq.true)"
                ),
                "order": "updated_at.desc"
            },
            timeout=20
        )

        r.raise_for_status()
        rows = r.json()

        result = []

        for row in rows:
            phone = row.get("phone_number")

            if not phone:
                continue

            result.append({
                **row,
                "caller_name": get_caller_name(phone)
            })

        return jsonify({
            "success": True,
            "count": len(result),
            "callers": result
        }), 200

    except Exception as e:
        print(
            "ACTIONABLE CALLERS API ERROR:",
            repr(e),
            flush=True
        )

        return jsonify({
            "success": False,
            "error": "Unable to load actionable callers"
        }), 500

from flask import Flask, request, jsonify
import os
import re
import json
import base64
from datetime import datetime, timezone

import firebase_admin
from firebase_admin import credentials, messaging
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

FIREBASE_SERVICE_ACCOUNT_B64 = os.getenv(
    "FIREBASE_SERVICE_ACCOUNT_B64",
    ""
)


def initialize_firebase_admin():
    """
    Initialize Firebase Admin from the Render Base64 environment secret.

    The service-account JSON is decoded only in memory.
    No private credential file is written to disk.
    """

    encoded = (
        FIREBASE_SERVICE_ACCOUNT_B64
        or ""
    ).strip()

    if not encoded:
        print(
            "FIREBASE ADMIN: service account env missing",
            flush=True
        )
        return False

    try:
        decoded = base64.b64decode(
            encoded,
            validate=True
        )

        service_account = json.loads(
            decoded.decode("utf-8")
        )

        if service_account.get("type") != "service_account":
            raise ValueError(
                "Invalid Firebase service account type"
            )

        if not firebase_admin._apps:
            credential = credentials.Certificate(
                service_account
            )

            firebase_admin.initialize_app(
                credential
            )

        print(
            "FIREBASE ADMIN INITIALIZED",
            flush=True
        )

        return True

    except Exception as exc:
        print(
            "FIREBASE ADMIN INIT ERROR:",
            type(exc).__name__,
            str(exc)[:200],
            flush=True
        )

        return False


FIREBASE_ADMIN_READY = initialize_firebase_admin()


def run_supabase_startup_diagnostic():
    """Temporary safe diagnostic for Render/Supabase auth."""
    print("===== SUPABASE STARTUP DIAGNOSTIC =====", flush=True)

    url = (SUPABASE_URL or "").rstrip("/")
    key = SUPABASE_KEY or ""

    print("SUPABASE URL LOADED:", bool(url), flush=True)
    print("SUPABASE KEY LOADED:", bool(key), flush=True)
    print(
        "SUPABASE KEY TYPE:",
        "sb_secret" if key.startswith("sb_secret_") else "other",
        flush=True
    )

    if not url or not key:
        print("DIAGNOSTIC STOP: missing environment value", flush=True)
        print("===== DIAGNOSTIC END =====", flush=True)
        return

    headers = {
        "apikey": key,
        "Content-Type": "application/json",
    }

    tests = [
        ("REST_ROOT", "/rest/v1/"),
        (
            "CALLER_STATE",
            "/rest/v1/caller_state"
            "?select=phone_number&limit=1"
        ),
        (
            "CONVERSATIONS",
            "/rest/v1/conversations"
            "?select=phone_number&limit=1"
        ),
    ]

    for name, path in tests:
        try:
            response = requests.get(
                url + path,
                headers=headers,
                timeout=15
            )

            print(
                f"SUPABASE TEST {name}: HTTP {response.status_code}",
                flush=True
            )

            if not response.ok:
                body = (response.text or "").replace("\n", " ")
                print(
                    f"SUPABASE TEST {name} ERROR BODY:",
                    body[:300],
                    flush=True
                )

        except Exception as exc:
            print(
                f"SUPABASE TEST {name} EXCEPTION:",
                type(exc).__name__,
                str(exc)[:200],
                flush=True
            )

    print("===== DIAGNOSTIC END =====", flush=True)


run_supabase_startup_diagnostic()


def supabase_headers():
    return {
        "apikey": SUPABASE_KEY,
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

        "caller_requested_time": None,
        "owner_decision": "NONE",
        "confirmed_callback_time": None,
        "callback_status": "NONE",
        "callback_attempt_result": None,

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
                    "caller_requested_time,"
                    "owner_decision,"
                    "confirmed_callback_time,"
                    "callback_status,"
                    "callback_attempt_result,"
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

        "caller_requested_time",
        "owner_decision",
        "confirmed_callback_time",
        "callback_status",
        "callback_attempt_result",

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
        limit=8
    )

    now_ist = datetime.now(
        ZoneInfo("Asia/Kolkata")
    )

    history_text = []

    for item in history[-6:]:
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
  "callback_action": "NONE",
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

CALLBACK ACTION:
- Allowed values:
  "NEW", "RESCHEDULE", "CANCEL", "FOLLOW_UP", "NONE".

- Determine the action from the ENTIRE recent conversation,
  existing callback state and latest caller message.

- NEW:
  The caller is creating a callback request and there is no
  existing active callback that they are merely modifying.

- RESCHEDULE:
  The caller is changing/correcting the time of an existing
  callback request.

  Examples when an existing callback exists:
  "8 mani better"
  "actually tomorrow morning"
  "konjam late ah pannunga"
  "7 illa 8 mani"

- CANCEL:
  The caller clearly wants an existing callback request stopped.

  Examples in appropriate context:
  "call venam"
  "callback venam"
  "cancel pannidunga"
  "problem solve aachu call panna vendaam"

  IMPORTANT:
  Do NOT classify a vague "venam", "no", "illa" or similar
  message as CANCEL unless conversation context clearly shows
  that the caller is referring to the callback.

- FOLLOW_UP:
  The caller is asking about the progress/status of an existing
  callback without requesting a new one or changing its time.

  Examples:
  "call pannalaya?"
  "enna aachu?"
  "confirm aacha?"
  "avar call pannara?"
  "callback update enna?"

- NONE:
  The latest message does not perform one of the callback actions.

- Never classify a normal topic change as a callback action.
- Never invent an action from a keyword alone.

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
        # CALLBACK ACTION
        # ----------------------------------------

        callback_action = data.get(
            "callback_action"
        )

        if isinstance(callback_action, str):
            callback_action = (
                callback_action
                .strip()
                .upper()
            )

            if callback_action in (
                "NEW",
                "RESCHEDULE",
                "CANCEL",
                "FOLLOW_UP"
            ):
                validated[
                    "callback_action"
                ] = callback_action

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



def get_active_device_tokens():
    """Return active Android FCM tokens from Supabase."""

    if not SUPABASE_URL or not SUPABASE_KEY:
        return []

    try:
        url = (
            SUPABASE_URL.rstrip("/")
            + "/rest/v1/device_tokens"
        )

        response = requests.get(
            url,
            headers=supabase_headers(),
            params={
                "active": "eq.true",
                "platform": "eq.android",
                "select": "token",
                "order": "updated_at.desc"
            },
            timeout=20
        )

        response.raise_for_status()

        tokens = []

        for row in response.json():
            token = str(
                row.get("token") or ""
            ).strip()

            if token and token not in tokens:
                tokens.append(token)

        return tokens

    except Exception as exc:
        print(
            "FCM TOKEN LOAD ERROR:",
            repr(exc),
            flush=True
        )

        return []


def deactivate_device_token(token):
    """Mark a bad FCM token inactive."""

    if (
        not token
        or not SUPABASE_URL
        or not SUPABASE_KEY
    ):
        return

    try:
        url = (
            SUPABASE_URL.rstrip("/")
            + "/rest/v1/device_tokens"
        )

        requests.patch(
            url,
            headers=supabase_headers(),
            params={
                "token": f"eq.{token}"
            },
            json={
                "active": False,
                "updated_at": datetime.now(
                    timezone.utc
                ).isoformat()
            },
            timeout=20
        )

    except Exception as exc:
        print(
            "FCM TOKEN DEACTIVATE ERROR:",
            repr(exc),
            flush=True
        )


def send_callback_approval_push(
    phone_number,
    caller_name,
    caller_reason,
    caller_requested_time
):
    """
    Send a data-only FCM message to registered Android devices.
    """

    if not FIREBASE_ADMIN_READY:
        print(
            "FCM PUSH SKIPPED: Firebase Admin not ready",
            flush=True
        )
        return False

    tokens = get_active_device_tokens()

    if not tokens:
        print(
            "FCM PUSH SKIPPED: no active device tokens",
            flush=True
        )
        return False

    data = {
        "type": "CALLBACK_APPROVAL",
        "phone_number": str(
            phone_number or ""
        ),
        "caller_name": str(
            caller_name or ""
        ),
        "caller_reason": str(
            caller_reason or ""
        ),
        "caller_requested_time": str(
            caller_requested_time or ""
        ),
    }

    sent = 0

    for token in tokens:
        try:
            message = messaging.Message(
                token=token,
                data=data,
                android=messaging.AndroidConfig(
                    priority="high"
                )
            )

            message_id = messaging.send(
                message
            )

            print(
                "FCM CALLBACK PUSH SENT:",
                message_id,
                flush=True
            )

            sent += 1

        except Exception as exc:
            error_name = type(exc).__name__

            print(
                "FCM CALLBACK PUSH ERROR:",
                error_name,
                str(exc)[:200],
                flush=True
            )

            if error_name in (
                "UnregisteredError",
                "SenderIdMismatchError"
            ):
                deactivate_device_token(
                    token
                )

    return sent > 0


def update_caller_state_from_message(
    user_message,
    sender="unknown"
):
    """
    Analyze one message and persist only validated fields.
    Skip low-information messages to save Groq tokens.
    """

    normalized = (
        (user_message or "")
        .strip()
        .lower()
    )

    low_information = {
        "",
        "hi",
        "hello",
        "hey",
        "hii",
        "hiii",
        "hm",
        "hmm",
        "mmm",
        "ok",
        "okay",
        "kk",
        "sari",
        "...",
        ".",
        "👍",
        "👍🏻",
        "👍🏼",
        "👍🏽",
        "👍🏾",
        "👍🏿"
    }

    if normalized in low_information:
        print(
            "CALLER ANALYSIS SKIPPED:",
            repr(user_message),
            flush=True
        )
        return get_caller_state(sender)

    previous_state = get_caller_state(sender)

    extracted = analyze_caller_message(
        user_message,
        sender
    )

    if not extracted:
        return previous_state

    # --------------------------------------------------------
    # CALLBACK STATE TRANSITION ENGINE
    # --------------------------------------------------------

    callback_requested = extracted.get(
        "callback_requested"
    )

    callback_time = extracted.get(
        "callback_time"
    )

    callback_action = extracted.pop(
        "callback_action",
        None
    )

    previous_callback_status = (
        previous_state.get("callback_status")
        or "NONE"
    )

    previous_requested_time = (
        previous_state.get("caller_requested_time")
    )

    active_callback = (
        previous_callback_status
        in (
            "WAITING_OWNER",
            "CONFIRMED"
        )
    )

    should_push_approval = False

    # NEW callback request.
    if (
        callback_action == "NEW"
        and
        callback_requested is True
        and
        callback_time
    ):
        extracted[
            "caller_requested_time"
        ] = callback_time

        extracted[
            "owner_decision"
        ] = "PENDING"

        extracted[
            "callback_status"
        ] = "WAITING_OWNER"

        extracted[
            "confirmed_callback_time"
        ] = None

        should_push_approval = (
            previous_callback_status
            != "WAITING_OWNER"
            or
            previous_requested_time
            != callback_time
        )

    # Caller changes an existing callback time.
    elif (
        callback_action == "RESCHEDULE"
        and
        callback_time
        and
        active_callback
    ):
        extracted[
            "callback_requested"
        ] = True

        extracted[
            "callback_time"
        ] = callback_time

        extracted[
            "caller_requested_time"
        ] = callback_time

        extracted[
            "owner_decision"
        ] = "PENDING"

        extracted[
            "callback_status"
        ] = "WAITING_OWNER"

        extracted[
            "confirmed_callback_time"
        ] = None

        should_push_approval = (
            previous_callback_status
            != "WAITING_OWNER"
            or
            previous_requested_time
            != callback_time
        )

    # Explicit cancellation of an active callback.
    elif (
        callback_action == "CANCEL"
        and
        active_callback
    ):
        extracted[
            "callback_requested"
        ] = False

        extracted[
            "owner_decision"
        ] = "CANCELLED_BY_CALLER"

        extracted[
            "callback_status"
        ] = "CANCELLED"

        extracted[
            "confirmed_callback_time"
        ] = None

        # Clear scheduling fields so stale times are not reused.
        extracted[
            "callback_time"
        ] = None

        extracted[
            "caller_requested_time"
        ] = None

    # FOLLOW_UP is conversational only.
    # It must not mutate callback scheduling state.
    elif callback_action == "FOLLOW_UP":
        for field in (
            "callback_requested",
            "callback_time"
        ):
            extracted.pop(
                field,
                None
            )

    # A callback action without enough safe information
    # must never destructively modify an existing callback.
    elif callback_action in (
        "NEW",
        "RESCHEDULE",
        "CANCEL"
    ):
        for field in (
            "callback_requested",
            "callback_time"
        ):
            extracted.pop(
                field,
                None
            )

    save_caller_state(
        sender,
        **extracted
    )

    updated_state = get_caller_state(sender)

    if (
        should_push_approval
        and
        updated_state.get("callback_status")
        == "WAITING_OWNER"
        and
        updated_state.get("caller_requested_time")
    ):
        try:
            send_callback_approval_push(
                phone_number=sender,
                caller_name=get_caller_name(sender),
                caller_reason=updated_state.get(
                    "caller_reason"
                ),
                caller_requested_time=updated_state.get(
                    "caller_requested_time"
                )
            )

        except Exception as push_error:
            print(
                "FCM CALLBACK PUSH NON-FATAL ERROR:",
                repr(push_error),
                flush=True
            )

    return updated_state



def ask_groq(user_message, sender="unknown", status="Work"):
    if not GROQ_API_KEY:
        return "Sorry, AI assistant temporarily unavailable."

    # Persistent conversation memory from Supabase
    history = load_conversation_history(sender, limit=8)

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

CALLBACK STATE:
Callback requested: {caller_state.get("callback_requested")}
Original callback time: {caller_state.get("callback_time") or "UNKNOWN"}
Caller requested time: {caller_state.get("caller_requested_time") or "UNKNOWN"}
Confirmed callback time: {caller_state.get("confirmed_callback_time") or "UNKNOWN"}
Callback status: {caller_state.get("callback_status") or "NONE"}
Owner decision: {caller_state.get("owner_decision") or "NONE"}

EMERGENCY STATE:
Emergency: {caller_state.get("emergency")}
Emergency reason: {caller_state.get("emergency_reason") or "UNKNOWN"}
"""

    system_prompt = f"""
You handle Imran's personal WhatsApp conversations when he is unavailable.

CURRENT IMRAN STATUS:
{status}

{caller_identity}

{structured_context}

CORE BEHAVIOUR

Think about the conversation before replying.

Your job is not to complete a questionnaire.
Your job is to naturally understand what this person wants from Imran,
collect only information that Imran would actually need,
and keep the conversation moving like a normal WhatsApp chat.

You are not Imran.
Never claim that you are Imran.
Never invent something Imran said, decided, promised or agreed to.

Do not behave like customer support.
Do not sound like a form, chatbot, IVR, ticket system or scripted receptionist.

CONVERSATION FIRST

Read the recent conversation as one continuous interaction.

Before every reply silently determine:

1. What did the caller just mean?
2. What useful information is already known?
3. What is still genuinely missing?
4. Is there already an active request or callback?
5. What would a sensible person reply at this exact point?

Then send only that reply.

Never expose this reasoning.

Do not blindly follow keywords.
Interpret the caller's intention using the whole conversation.

REAL WHATSAPP STYLE

Prefer short conversational messages.

Usually:
- one short sentence, or
- two short sentences when necessary.

Do not send a paragraph when a few words are enough.

Match the caller's tone and level of formality.

If they say:
"hmm"
"okay"
"seri"
"sari"
"kk"
"oh"
"..."

understand it in context.

Do not restart the conversation.
Do not introduce yourself again.
Do not repeat the previous explanation.

Avoid unnecessarily formal phrases such as:
"How may I assist you?"
"Please provide the required details."
"Your request has been recorded."
"Thank you for contacting us."

INFORMATION GATHERING

Collect information through conversation, not interrogation.

Useful information may include:
- who the person is,
- why they need Imran,
- what they need him to do,
- whether it is urgent,
- callback preference,
- suitable callback time,
- useful context Imran needs before responding.

But ask ONLY for information that is currently useful.

Ask ONE thing at a time.

Never ask something already available in:
- conversation history,
- caller identity,
- structured caller state.

If the caller voluntarily gives multiple useful details,
accept them and continue.
Do not ask those details again.

NAME

If the caller's real name is already known:
never ask their name again.

If unknown:
ask naturally when identification becomes useful.

Examples of natural style:
"Sure, unga name enna?"
"Okay, name sollunga."

Do not repeatedly ask for a name.

If they ask why:

briefly explain that you need it so Imran knows
whose message/request you are passing to him.

Do not send a long explanation about being an AI.

If they refuse or avoid giving their name:
continue the conversation without blocking them.

IDENTITY DISCLOSURE

Do not repeatedly announce that you are an AI.

If the caller directly asks who you are,
answer truthfully and briefly that you are
Imran's personal AI assistant/receptionist helping manage his messages.

Never pretend to literally be a human being.

The conversation should feel natural because of good context,
memory and wording — not because you falsely claim to be Imran
or deny being an AI.

REASON / INTENT

Try to understand the actual purpose of contacting Imran.

Bad:
"Why are you calling?"

Better depending on context:
"Okay, enna matter nu sollunga."
"Sure, enna vishayam?"
"Okay, Imran kitta enna sollanum?"

If they already explained the matter,
do not ask for it again.

If their explanation is incomplete,
ask only the most useful next question.

Do not force unnecessary detail.

CALLBACK LOGIC

Treat callback scheduling as a real ongoing state.

If Callback requested is true:
do not ask whether they want a callback again.

If Callback time is already known:
do not ask for another time unless the caller wants to change it
or the existing request can no longer be used.

If callback_status is WAITING_OWNER:
the requested callback is already waiting for Imran's approval.

If the caller asks again before that request is resolved,
do NOT create the impression that a second callback is needed.

Respond naturally based on context, for example:
"Already andha time note panniruken, Imran confirm pannadhum update panren."

Do not use that exact sentence every time.
Generate wording appropriate to the conversation.

If the caller explicitly changes the requested time,
treat it as an update to the existing request,
not an unrelated second callback.

If the caller cancels the callback,
acknowledge the cancellation naturally.

If they request a callback but no usable time exists,
ask for a suitable time.

Never claim a callback is confirmed
unless structured state says it is confirmed.

Never promise:
"Imran will definitely call."

If the callback is confirmed,
you may accurately tell the caller it is confirmed.

REAL-LIFE CONTINUITY

Think about whether an action is already pending before asking
the caller to perform or schedule it again.

Examples:

If someone already gave their name:
do not ask their name.

If someone already explained the issue:
continue from that issue.

If someone already requested a callback:
continue from that callback.

If they return later saying:
"call pannalaya?"

understand they are following up on the existing callback,
not making a brand-new request.

If they say:
"time change pannalama?"

understand they mean the existing callback time.

If they say:
"venam"

use the conversation to determine what they are cancelling/refusing.

If uncertain, ask one short clarification rather than assuming.

HUMAN-LIKE RESPONSE SELECTION

Do not mechanically acknowledge every message with:
"Sari"
"Okay"
"Noted"
"Convey panren."

Vary acknowledgements naturally or skip them when unnecessary.

A normal conversation can progress directly.

Example:

Caller:
"Imran irukana?"

Good:
"Work-la irukaaru. Enna matter nu sollunga?"

Caller:
"project pathi pesanum"

Good:
"Sure. Endha project?"

Caller:
"website project"

Good:
"Okay, website-la enna discuss pannanum?"

Caller:
"payment pathi"

Good:
"Got it. Payment-la enna issue nu konjam sollunga, avarukku clear-a convey panna useful-a irukkum."

This is an example of reasoning style,
not a script to copy.

Another example:

Caller:
"callback panna sollunga"

Known callback time = UNKNOWN.

Good:
"Sure, eppo call panna convenient?"

Caller:
"7 mani"

After state captures the requested time:

Good:
"Okay, 7 mani request anupiruken. Imran confirm pannadhum update panren."

If they immediately say:
"8 mani better"

Good:
"Okay, 8 mani-ku change pannalaam."

Do not act as if this is a brand-new callback conversation.

LANGUAGE

If structured state has a language preference,
preserve it.

THANGLISH:
Tamil conversation written naturally using Roman/English letters.

ENGLISH:
natural conversational English.

TAMIL:
natural Tamil script.

If language preference is unknown:
mirror the latest meaningful caller language.

Neutral greetings like "Hi", "Hello", "Hey":
default to natural Thanglish.

Short acknowledgements must not switch language.

For Thanglish, prefer normal spoken wording such as:
"enna matter"
"sollunga"
"seri"
"eppo"
"call panna"
"avar kitta"

Do not produce awkward transliteration or overly literary Tamil.

STATUS

Use Imran's current status only when relevant.

Do not insert the status into every response.

Examples:
If asked "Imran free ah?"
then CURRENT STATUS matters.

If discussing an already-known payment issue,
there may be no reason to mention his status again.

EMERGENCY

If structured state clearly identifies an emergency,
prioritize understanding the immediate need.

Be concise.

Do not exaggerate urgency.
Do not invent emergency details.

TRUST

Never invent:
- callback confirmation,
- availability,
- location,
- promises,
- decisions,
- messages from Imran,
- completed actions.

If something is pending, say it is pending naturally.

If you genuinely do not know something,
do not pretend to know.

FINAL RESPONSE RULE

Return ONLY the WhatsApp message that should be sent to the caller.

No analysis.
No labels.
No JSON.
No quotation marks around the response.
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
                # Structured caller state is an enhancement.
                # It must NEVER block the core WhatsApp AI conversation.
                try:
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

                except Exception as state_error:
                    state = {}

                    print(
                        "CALLER STATE NON-FATAL ERROR:",
                        repr(state_error),
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
                    "caller_requested_time,"
                    "owner_decision,"
                    "confirmed_callback_time,"
                    "callback_status,"
                    "callback_attempt_result,"
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


@app.post("/api/device-token")
def api_device_token():
    """
    Register or refresh an Android FCM device token.

    Protected by APP_CLIENT_TOKEN.
    The token is stored in Supabase device_tokens.
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

    data = request.get_json(silent=True) or {}

    token = str(
        data.get("token") or ""
    ).strip()

    platform = str(
        data.get("platform") or "android"
    ).strip().lower()

    if not token:
        return jsonify({
            "success": False,
            "error": "token is required"
        }), 400

    if platform != "android":
        return jsonify({
            "success": False,
            "error": "Unsupported platform"
        }), 400

    try:
        url = (
            SUPABASE_URL.rstrip("/")
            + "/rest/v1/device_tokens"
        )

        headers = supabase_headers().copy()

        # Supabase/PostgREST upsert by unique token.
        headers["Prefer"] = (
            "resolution=merge-duplicates,"
            "return=representation"
        )

        payload = {
            "token": token,
            "platform": platform,
            "active": True,
            "updated_at": datetime.now(
                timezone.utc
            ).isoformat()
        }

        response = requests.post(
            url,
            headers=headers,
            params={
                "on_conflict": "token"
            },
            json=payload,
            timeout=20
        )

        if response.status_code not in (
            200,
            201
        ):
            print(
                "FCM DEVICE TOKEN SAVE ERROR:",
                response.status_code,
                response.text,
                flush=True
            )

            return jsonify({
                "success": False,
                "error": "Unable to register device token"
            }), 502

        print(
            "FCM DEVICE TOKEN REGISTERED",
            flush=True
        )

        return jsonify({
            "success": True
        }), 200

    except Exception as e:
        print(
            "FCM DEVICE TOKEN ERROR:",
            repr(e),
            flush=True
        )

        return jsonify({
            "success": False,
            "error": "Device token registration failed"
        }), 500


@app.post("/api/callback-decision")
def api_callback_decision():
    if not require_app_client():
        return jsonify({
            "success": False,
            "error": "Unauthorized"
        }), 401

    data = request.get_json(silent=True) or {}

    phone_number = str(
        data.get("phone_number") or ""
    ).strip()

    decision = str(
        data.get("decision") or ""
    ).strip().upper()

    confirmed_time = (
        data.get("confirmed_callback_time")
    )

    if not phone_number:
        return jsonify({
            "success": False,
            "error": "phone_number is required"
        }), 400

    if decision not in (
        "ACCEPT",
        "REJECT",
        "RESCHEDULE"
    ):
        return jsonify({
            "success": False,
            "error": "Invalid decision"
        }), 400

    state = get_caller_state(phone_number)

    caller_requested_time = state.get(
        "caller_requested_time"
    )

    if decision == "ACCEPT":
        final_time = (
            confirmed_time
            or caller_requested_time
        )

        if not final_time:
            return jsonify({
                "success": False,
                "error": "No callback time available"
            }), 400

        saved = save_caller_state(
            phone_number,
            owner_decision="ACCEPTED",
            confirmed_callback_time=final_time,
            callback_status="CONFIRMED"
        )

        if not saved:
            return jsonify({
                "success": False,
                "error": "Unable to save decision"
            }), 500

        message = (
            "Sari, unga requested callback time Imran confirm pannirukaaru. "
            "Andha time-la unga kitta call panna try pannuvaaru."
        )

        send_whatsapp_text(
            phone_number,
            message
        )

        return jsonify({
            "success": True,
            "decision": "ACCEPTED",
            "confirmed_callback_time": final_time
        }), 200

    if decision == "REJECT":

        # Idempotency protection:
        # repeated Android taps / retries must never send
        # the rejection WhatsApp message more than once.
        if (
            state.get("owner_decision") == "REJECTED"
            and
            state.get("callback_status") == "CANCELLED"
        ):
            print(
                "CALLBACK REJECT ALREADY PROCESSED:",
                phone_number,
                flush=True
            )

            return jsonify({
                "success": True,
                "decision": "REJECTED",
                "already_processed": True
            }), 200

        saved = save_caller_state(
            phone_number,
            owner_decision="REJECTED",
            confirmed_callback_time=None,
            callback_status="CANCELLED"
        )

        if not saved:
            return jsonify({
                "success": False,
                "error": "Unable to save decision"
            }), 500

        message = (
            "Sorry, Imran-ku ippothaikku call panna suitable time illa. "
            "Unga message avar kitta note pannirukku."
        )

        send_whatsapp_text(
            phone_number,
            message
        )

        return jsonify({
            "success": True,
            "decision": "REJECTED"
        }), 200

    # RESCHEDULE
    if not confirmed_time:
        return jsonify({
            "success": False,
            "error": "confirmed_callback_time is required for reschedule"
        }), 400

    saved = save_caller_state(
        phone_number,
        owner_decision="RESCHEDULED",
        confirmed_callback_time=confirmed_time,
        callback_status="CONFIRMED"
    )

    if not saved:
        return jsonify({
            "success": False,
            "error": "Unable to save decision"
        }), 500

    message = (
        "Imran unga callback-ku vera time set pannirukaaru. "
        "Andha confirmed time-la unga kitta call panna try pannuvaaru."
    )

    send_whatsapp_text(
        phone_number,
        message
    )

    return jsonify({
        "success": True,
        "decision": "RESCHEDULED",
        "confirmed_callback_time": confirmed_time
    }), 200

# ============================================================
# LOCAL DEVELOPMENT STARTUP
# Keep this at the END of the file so every Flask route
# is registered before app.run() starts.
# ============================================================

if __name__ == "__main__":
    print(
        "Imran AI Receptionist webhook starting...",
        flush=True
    )
    app.run(
        host="0.0.0.0",
        port=5000
    )

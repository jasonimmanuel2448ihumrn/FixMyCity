#!/usr/bin/env python3
"""
FixMyCity - Local Image Storage Server
======================================
A zero-dependency HTTP server to store and serve report images locally on your laptop.

Endpoints:
- POST   /upload          : Upload image (multipart/form-data or raw image binary)
- GET    /uploads/<file>  : Retrieve and serve image file
- GET    /health          : Check server health and stats (JSON)
- GET    /                : Web dashboard to view and test uploaded images
- DELETE /uploads/<file>  : Delete an image file

Usage:
  python3 server.py [PORT]
  Default port: 8080
"""

import os
import sys
import json
import time
import uuid
import socket
import shutil
import re
import mimetypes
from pathlib import Path
from http.server import HTTPServer, BaseHTTPRequestHandler
import urllib.parse
import urllib.request

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "uploads"
UPLOAD_DIR.mkdir(exist_ok=True)
BACKEND_UPLOAD_DIR = BASE_DIR.parent / "backend" / "uploads"
BACKEND_DB_FILE = BASE_DIR.parent / "backend" / "data" / "database.json"


def get_lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def get_best_ollama_model():
    """Detects installed models in local Ollama daemon."""
    try:
        req = urllib.request.Request("http://localhost:11434/api/tags")
        with urllib.request.urlopen(req, timeout=1.2) as r:
            tags = json.loads(r.read().decode("utf-8")).get("models", [])
            names = [m.get("name", "") for m in tags]
            # Priority: fast small model first, then standard 7B/8B
            for pref in ["granite3.1-moe:1b", "llama3:latest", "mistral:latest", "granite"]:
                for name in names:
                    if pref in name:
                        return name
            if names:
                return names[0]
    except Exception:
        pass
    return "granite3.1-moe:1b"


def query_ollama(prompt, model=None):
    """Sends prompt to local Ollama instance on port 11434."""
    active_model = model or get_best_ollama_model()
    try:
        payload = json.dumps({
            "model": active_model,
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.35,
                "num_predict": 450
            }
        }).encode("utf-8")
        req = urllib.request.Request(
            "http://localhost:11434/api/generate",
            data=payload,
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data.get("response", "").strip(), active_model
    except Exception as e:
        return None, str(e)


def load_local_reports():
    """Reads reports from local database if available."""
    if BACKEND_DB_FILE.exists():
        try:
            with open(BACKEND_DB_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
                return data.get("reports", [])
        except Exception:
            pass
    return []


def detect_chat_action(query, response_text, reports):
    """Infers smart navigation action from user query and AI response."""
    query_lower = query.lower().strip()
    resp_lower = response_text.lower()

    # 1. Emergency / helpline
    if any(w in query_lower for w in ["1916", "emergency", "helpline", "disaster", "police", "fire", "ambulance"]):
        return "CALL_HELPLINE", "Call City Helpline 1916", "1916"

    # 2. View specific report ID (#R1, R2, etc.)
    match = re.search(r'#?(r\d+)', query_lower, re.IGNORECASE)
    if match:
        rep_id = match.group(1).lower()
        for r in reports:
            if r.get("id", "").lower() == rep_id:
                return "VIEW_REPORT_DETAIL", f"View Report #{rep_id.upper()}", r.get("id")

    # 3. View all reports or track
    if any(w in query_lower for w in ["track", "my reports", "my complaints", "status of my", "track report"]):
        return "VIEW_MY_REPORTS", "View My Reports", None

    # 4. Detect specific category ONLY if explicitly present in user query
    cat_keywords = [
        ("pothole", "Pothole"),
        ("garbage", "Garbage"),
        ("trash", "Garbage"),
        ("waste", "Garbage"),
        ("dump", "Garbage"),
        ("streetlight", "Streetlight"),
        ("street light", "Streetlight"),
        ("light", "Streetlight"),
        ("lamp", "Streetlight"),
        ("water leakage", "Water Leakage"),
        ("water leak", "Water Leakage"),
        ("pipe", "Water Leakage"),
        ("water", "Water Leakage"),
        ("drainage", "Drainage"),
        ("sewage", "Drainage"),
        ("gutter", "Drainage"),
        ("drain", "Drainage"),
        ("road damage", "Road Damage"),
        ("road", "Road Damage"),
        ("divider", "Road Damage"),
        ("public property", "Public Property"),
        ("park", "Public Property")
    ]

    matched_cat = None
    for kw, cat in cat_keywords:
        if kw in query_lower:
            matched_cat = cat
            break

    # If AI drafted a specific complaint category
    if "draft complaint" in resp_lower or "**category**:" in resp_lower:
        for kw, cat in cat_keywords:
            if f"category: {kw}" in resp_lower or f"category**: {kw}" in resp_lower or f"category**: {cat.lower()}" in resp_lower:
                return "REPORT_ISSUE", f"Submit {cat} Report", cat

    # If user wants to report an issue
    is_reporting = any(w in query_lower for w in ["report", "file", "submit", "lodge", "log", "complain", "register", "new issue"])
    if is_reporting:
        if matched_cat:
            return "REPORT_ISSUE", f"Report {matched_cat} Issue", matched_cat
        else:
            return "REPORT_ISSUE", "Report an Issue", None

    # Area summary query action
    areas = ["vashi", "belapur", "nerul", "koparkhairane", "kharghar", "airoli", "sanpada", "ghansoli", "seawoods", "panvel", "sector 17", "sector 10", "sector 5", "sector 20", "palm beach"]
    matched_area = next((a for a in areas if a in query_lower), None)
    if matched_area:
        return "REPORT_ISSUE", f"Report Issue in {matched_area.title()}", None

    if matched_cat:
        return "REPORT_ISSUE", f"Report {matched_cat} Issue", matched_cat

    # If response itself suggests reporting
    if any(w in resp_lower for w in ["report an issue", "file a report", "submit a report"]):
        return "REPORT_ISSUE", "Report an Issue", None

    return "NONE", None, None


def generate_fallback_ai_response(query, reports, user_id, user_name):
    """High-intelligence analytical engine for report summarization and civic advice when LLM is offline."""
    lower = query.lower().strip()

    is_summary_request = any(w in lower for w in [
        "summar", "overview", "breakdown", "stats", "statistics", "how many",
        "all reports", "list reports", "what are the reports", "what are the issues", "what issues", "what problems"
    ])

    # 1. Report Issue Intent (Guidance without dumping summaries)
    is_report_intent = any(w in lower for w in ["report", "file", "submit", "lodge", "log", "register", "new issue"]) and not is_summary_request
    if is_report_intent and not any(w in lower for w in ["draft", "write"]):
        matched_cat = None
        for kw, cat in [
            ("pothole", "Pothole"), ("garbage", "Garbage"), ("trash", "Garbage"), ("waste", "Garbage"),
            ("streetlight", "Streetlight"), ("light", "Streetlight"), ("lamp", "Streetlight"),
            ("water", "Water Leakage"), ("leak", "Water Leakage"), ("drain", "Drainage"),
            ("sewage", "Drainage"), ("road", "Road Damage"), ("divider", "Road Damage"),
            ("park", "Public Property"), ("property", "Public Property")
        ]:
            if kw in lower:
                matched_cat = cat
                break

        if matched_cat:
            return (
                f"I'm ready to help you report this **{matched_cat}** issue! 🚨\n\n"
                f"To submit it directly to municipal field engineers:\n"
                f"1. Tap the **Report {matched_cat} Issue** button below.\n"
                f"2. Take or upload a photo showing the issue clearly.\n"
                f"3. Confirm your GPS location and submit.\n\n"
                f"Once submitted, you will receive real-time updates as field teams inspect and fix it.",
                "REPORT_ISSUE",
                f"Report {matched_cat} Issue",
                matched_cat
            )
        else:
            return (
                f"I'm ready to help you log a civic issue! 🛠️\n\n"
                f"You can report any neighborhood problem in 3 simple steps:\n"
                f"1. Tap the **Report an Issue** button below.\n"
                f"2. Select the category (Pothole, Garbage, Streetlight, Water Leakage, Drainage, Road Damage, Public Property).\n"
                f"3. Attach a photo and confirm the location.\n\n"
                f"Your complaint will be immediately dispatched to the appropriate municipal department with real-time status tracking.",
                "REPORT_ISSUE",
                "Report an Issue",
                None
            )

    # 2. Area Summarization (ONLY when explicitly requested)
    areas = ["vashi", "belapur", "nerul", "koparkhairane", "kharghar", "airoli", "sanpada", "ghansoli", "seawoods", "panvel", "sector 17", "sector 10", "sector 5", "sector 20", "sector 3", "sector 9", "palm beach", "market"]
    matched_area = next((a for a in areas if a in lower), None)

    if is_summary_request and matched_area:
        area_reports = [r for r in reports if matched_area in (r.get("location", "") + " " + r.get("title", "")).lower()]
        area_title = matched_area.title()
        if not area_reports:
            return (
                f"📍 **Civic Summary for {area_title}**\n\n"
                f"Currently, there are **0 active complaints** logged specifically in {area_title}.\n\n"
                f"If you notice an infrastructure defect (such as a pothole, broken streetlight, or garbage dump), "
                f"you can be the first to report it to municipal authorities!",
                "REPORT_ISSUE",
                f"Report Issue in {area_title}",
                None
            )

        total = len(area_reports)
        resolved = sum(1 for r in area_reports if r.get("status") == "RESOLVED")
        in_prog = sum(1 for r in area_reports if r.get("status") in ["IN_PROGRESS", "ASSIGNED"])
        under_rev = sum(1 for r in area_reports if r.get("status") in ["UNDER_REVIEW", "SUBMITTED"])

        bullet_lines = []
        for r in area_reports[:5]:
            status_emoji = {"RESOLVED": "✅", "IN_PROGRESS": "🛠️", "ASSIGNED": "👷", "UNDER_REVIEW": "👀", "SUBMITTED": "⏳"}.get(r.get("status"), "📋")
            bullet_lines.append(f"• **#{r.get('id', '').upper()}** [{r.get('category')}]: \"{r.get('title')}\" ({status_emoji} {r.get('status', '').replace('_', ' ')})\n  _{r.get('location')}_")

        bullets_text = "\n".join(bullet_lines)
        res_rate = int((resolved / total) * 100) if total > 0 else 0

        text = (
            f"📊 **Live Grievance Summary for {area_title}**\n\n"
            f"Found **{total} civic reports** in this area:\n"
            f"• **Resolved**: {resolved} ({res_rate}% resolution rate)\n"
            f"• **In Progress / Assigned**: {in_prog}\n"
            f"• **Awaiting Review**: {under_rev}\n\n"
            f"**Recent Area Incidents:**\n"
            f"{bullets_text}\n\n"
            f"💡 _Tip: Municipal field crews are dispatched daily across {area_title}. You can track individual work orders or report a new defect below._"
        )
        return text, "REPORT_ISSUE", f"Report New Issue in {area_title}", None

    # 3. Category Summarization (ONLY when explicitly requested)
    categories = ["pothole", "garbage", "streetlight", "water leakage", "drainage", "road damage", "public property"]
    matched_cat = next((c for c in categories if c in lower), None)
    if is_summary_request and matched_cat:
        cat_reports = [r for r in reports if matched_cat in r.get("category", "").lower()]
        cat_name = matched_cat.title()
        total = len(cat_reports)
        resolved = sum(1 for r in cat_reports if r.get("status") == "RESOLVED")
        active = total - resolved

        items_summary = "\n".join([
            f"• **#{r.get('id', '').upper()}** in {r.get('location')} ({r.get('status', '').replace('_', ' ')})"
            for r in cat_reports[:4]
        ])

        text = (
            f"🚨 **{cat_name} Reports Overview**\n\n"
            f"Total logged across the city: **{total} reports**\n"
            f"• Active/Pending: **{active}**\n"
            f"• Successfully Resolved: **{resolved}**\n\n"
            f"**Locations:**\n{items_summary or 'No active records'}\n\n"
            f"⏱️ **Department SLA**: Resolution timeline is typically 24–72 hours depending on severity."
        )
        return text, "REPORT_ISSUE", f"Report {cat_name} Issue", cat_name

    # 3. Complaint Tracking
    if any(w in lower for w in ["track", "my report", "my complaint", "status"]):
        user_reports = [r for r in reports if r.get("userId") == user_id]
        if not user_reports:
            user_reports = reports[:3]
        if not user_reports:
            return "You have not submitted any complaints yet. Tap below to log your first civic report!", "REPORT_ISSUE", "Report First Issue", None

        resolved = sum(1 for r in user_reports if r.get("status") == "RESOLVED")
        pending = len(user_reports) - resolved
        summary_lines = []
        for r in user_reports[:3]:
            st = r.get("status", "SUBMITTED")
            st_emoji = "✅" if st == "RESOLVED" else "🛠️"
            summary_lines.append(f"• **#{r.get('id', '').upper()}** {r.get('title')} ({st_emoji} {st.replace('_', ' ')})")

        return (
            f"📋 **Your Civic Complaint Status**\n\n"
            f"You have **{len(user_reports)} complaints on file** ({resolved} Resolved, {pending} Ongoing):\n\n"
            f"{chr(10).join(summary_lines)}\n\n"
            f"Municipal officers update timelines in real-time as field teams work.",
            "VIEW_MY_REPORTS",
            "View My Reports",
            None
        )

    # 4. Complaint Drafting assistance
    if any(w in lower for w in ["draft", "write", "how to complain", "file"]):
        detected_cat = "Pothole"
        for c in ["garbage", "streetlight", "water leakage", "drainage", "road damage"]:
            if c in lower:
                detected_cat = c.title()
                break

        return (
            f"📝 **Draft Complaint Prepared for You**:\n\n"
            f"• **Category**: {detected_cat}\n"
            f"• **Title**: Severe {detected_cat} defect obstructing public safety\n"
            f"• **Description**: Noticeable infrastructure damage observed on the main roadway. Causing public inconvenience and safety hazards. Requires urgent municipal inspection and remediation.\n\n"
            f"Would you like to submit this report now?",
            "REPORT_ISSUE",
            f"Submit {detected_cat} Report",
            detected_cat
        )

    # 5. Helplines & Emergencies
    if any(w in lower for w in ["emergency", "helpline", "phone", "number", "contact", "call"]):
        return (
            "🏛️ **Official 24x7 Municipal Helplines**:\n\n"
            "• **City Corporation Central Helpline**: 1916\n"
            "• **Disaster Management Cell**: 1077\n"
            "• **Fire Emergency**: 101\n"
            "• **Ambulance**: 108\n"
            "• **Police Control**: 100\n\n"
            "Tap below to dial the Municipal Control Room directly.",
            "CALL_HELPLINE",
            "Call City Helpline 1916",
            "1916"
        )

    # 6. SLAs & Timelines
    if any(w in lower for w in ["sla", "time", "duration", "how long"]):
        return (
            "⏱️ **Municipal Service Level Agreements (SLAs)**:\n\n"
            "• **Water Main Bursts**: 6 – 12 hours (High Priority)\n"
            "• **Streetlight Outages**: 24 – 48 hours\n"
            "• **Garbage Accumulation**: 12 – 24 hours\n"
            "• **Pothole Repair**: 48 – 72 hours\n"
            "• **Drainage Clearance**: 24 – 48 hours\n\n"
            "You will receive live notification milestones as each stage progresses.",
            "REPORT_ISSUE",
            "Report an Issue",
            None
        )

    # Default fallback
    return (
        f"Hello {user_name}! 👋 I am **FixMyCity Civic AI Copilot**.\n\n"
        f"You can ask me to:\n"
        f"• **Summarize reports in an area** (e.g. _\"Summarize reports in Vashi\"_)\n"
        f"• **Check specific categories** (e.g. _\"What are active drainage issues?\"_)\n"
        f"• **Track your complaints** or check turnaround SLAs\n"
        f"• **Draft a formal complaint** for quick submission\n\n"
        f"How can I assist your neighborhood today?",
        "REPORT_ISSUE",
        "File a Report",
        None
    )


def process_ai_chat(req_data):
    """Orchestrates Ollama LLM RAG inference with fallback."""
    message = req_data.get("message", "").strip()
    user_id = req_data.get("userId", "")
    user_name = req_data.get("userName", "Citizen")
    reports = req_data.get("reports", []) or load_local_reports()
    requested_model = req_data.get("model")

    lower_msg = message.lower()
    is_summary_query = any(w in lower_msg for w in [
        "summar", "overview", "breakdown", "stats", "statistics", "how many",
        "all reports", "list reports", "what are the reports", "what are the issues", "what issues", "what problems"
    ])
    is_report_intent = any(w in lower_msg for w in ["report", "file", "submit", "lodge", "log", "register", "new issue"]) and not is_summary_query

    # 1. Try Ollama local model first
    if is_report_intent:
        rag_prompt = f"""You are FixMyCity Civic AI Copilot, a helpful municipal grievance assistant.

Citizen Name: {user_name}
Citizen Query: "{message}"

The citizen is asking to REPORT or FILE a civic issue in their neighborhood.
CRITICAL RULES:
- DO NOT summarize any reports from the database.
- DO NOT dump or list past complaints.
- Guide the citizen in a friendly, encouraging way on how to report:
  1. Tap the 'Report an Issue' button below to open the submission screen.
  2. Select the issue category (Pothole, Garbage, Streetlight, Water Leakage, Drainage, Road Damage, Public Property).
  3. Snap a clear photo of the problem and verify the GPS location.
  4. Submit to immediately alert municipal engineers.
- If they mentioned what specific problem they have, confirm and reassure them that city field crews will inspect it.
- FORMATTING RULE: Do NOT use ** to bold words. Do not use double asterisks. Use clean plain text with emojis and bullet points only.
"""
    elif is_summary_query:
        reports_context = []
        for idx, r in enumerate(reports[:30]):
            r_id = r.get("id", f"r{idx+1}").upper()
            title = r.get("title", "")
            cat = r.get("category", "")
            loc = r.get("location", "")
            status = r.get("status", "SUBMITTED")
            desc = (r.get("description", "") or "")[:120].replace("\n", " ")
            reports_context.append(f"- #{r_id} [{cat}] '{title}' at {loc} | Status: {status} | Details: {desc}")

        context_str = "\n".join(reports_context) if reports_context else "No active reports."

        rag_prompt = f"""You are FixMyCity Civic AI Copilot, an intelligent civic assistant for municipal grievance analytics.

--- LIVE GRIEVANCE DATABASE ({len(reports)} records) ---
{context_str}

Citizen Name: {user_name}
User Query: "{message}"

INSTRUCTIONS:
The user explicitly asked to summarize civic reports.
1. Provide a concise summary of the reports in the requested area or category.
2. State the total complaints found, with status breakdown (Resolved vs In Progress vs Under Review).
3. Highlight 2 to 4 notable incident IDs (e.g. #R1), location, and current progress.
4. Offer helpful civic advice or recommendations.
5. FORMATTING RULE: Do NOT use ** to bold words. Do not use double asterisks. Use clean plain text, emojis, and bullet points only. Never fabricate complaints that are not in the database.
"""
    else:
        reports_context = []
        for idx, r in enumerate(reports[:15]):
            r_id = r.get("id", f"r{idx+1}").upper()
            title = r.get("title", "")
            cat = r.get("category", "")
            loc = r.get("location", "")
            status = r.get("status", "SUBMITTED")
            reports_context.append(f"- #{r_id} [{cat}] '{title}' at {loc} ({status})")
        context_str = "\n".join(reports_context) if reports_context else "No active reports."

        rag_prompt = f"""You are FixMyCity Civic AI Copilot, an intelligent, empathetic civic assistant developed for municipal grievance resolution.

--- LIVE DATABASE REFERENCE ---
{context_str}

Citizen Name: {user_name}
User Query: "{message}"

INSTRUCTIONS:
1. Answer the user query directly and helpfully.
2. If drafting a complaint, provide a professional Title, Category, and Description.
3. If asking about SLAs or helplines, provide official municipal details (Central Helpline: 1916).
4. If asking to track a specific report, reference the record.
5. DO NOT provide an unprompted summary of the whole database.
6. FORMATTING RULE: Do NOT use ** to bold words. Do not use double asterisks. Use clean plain text, emojis, and bullet points only.
"""

    ollama_resp, used_model = query_ollama(rag_prompt, requested_model)
    if ollama_resp:
        clean_resp = ollama_resp.replace("**", "").replace("__", "").strip()
        action_type, action_label, action_payload = detect_chat_action(message, clean_resp, reports)
        return {
            "status": "success",
            "response": clean_resp,
            "source": "ollama",
            "model": used_model,
            "actionType": action_type,
            "actionLabel": action_label,
            "actionPayload": action_payload
        }

    # 2. Fallback to analytical engine
    fallback_text, action_type, action_label, action_payload = generate_fallback_ai_response(
        message, reports, user_id, user_name
    )
    clean_fallback = fallback_text.replace("**", "").replace("__", "").strip()
    return {
        "status": "success",
        "response": clean_fallback,
        "source": "local_engine",
        "model": "Civic Intelligence Engine",
        "actionType": action_type,
        "actionLabel": action_label,
        "actionPayload": action_payload
    }


def parse_multipart(body: bytes, boundary: bytes):
    """Parses multipart/form-data bytes and returns list of (headers, content)."""
    parts = body.split(b"--" + boundary)
    files = []
    for part in parts:
        if not part or part == b"--\r\n" or part == b"--":
            continue
        if b"\r\n\r\n" in part:
            header_part, content = part.split(b"\r\n\r\n", 1)
            if content.endswith(b"\r\n"):
                content = content[:-2]
            header_text = header_part.decode("latin1", errors="replace")
            files.append((header_text, content))
    return files


class ImageStorageHandler(BaseHTTPRequestHandler):

    def _set_headers(self, status=200, content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_OPTIONS(self):
        self._set_headers(200, "text/plain")

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        # 1. Health check
        if path in ("/health", "/api/health"):
            files = list(UPLOAD_DIR.glob("*"))
            data = {
                "status": "online",
                "message": "FixMyCity Local Image Server is running",
                "total_images": len(files),
                "upload_dir": str(UPLOAD_DIR)
            }
            self._set_headers(200, "application/json")
            self.wfile.write(json.dumps(data, indent=2).encode("utf-8"))
            return

        # 2. Serve uploaded image
        if path.startswith("/uploads/"):
            filename = urllib.parse.unquote(path[len("/uploads/"):].strip("/"))
            file_path = UPLOAD_DIR / filename
            if file_path.is_file() and file_path.resolve().is_relative_to(UPLOAD_DIR.resolve()):
                mime, _ = mimetypes.guess_type(str(file_path))
                mime = mime or "application/octet-stream"
                try:
                    with open(file_path, "rb") as f:
                        content = f.read()
                    self._set_headers(200, mime)
                    self.wfile.write(content)
                    return
                except Exception as e:
                    self._set_headers(500, "application/json")
                    self.wfile.write(json.dumps({"error": str(e)}).encode("utf-8"))
                    return
            else:
                self._set_headers(404, "application/json")
                self.wfile.write(json.dumps({"error": "Image not found"}).encode("utf-8"))
                return

        # 3. Web Dashboard UI
        if path in ("/", "/index.html"):
            self._serve_dashboard()
            return

        self._set_headers(404, "application/json")
        self.wfile.write(json.dumps({"error": "Endpoint not found"}).encode("utf-8"))

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path in ("/upload", "/api/upload"):
            content_type = self.headers.get("Content-Type", "")
            content_length = int(self.headers.get("Content-Length", 0))

            if content_length == 0:
                self._set_headers(400, "application/json")
                self.wfile.write(json.dumps({"error": "Empty payload"}).encode("utf-8"))
                return

            body = self.rfile.read(content_length)
            saved_filename = None

            # Handle multipart/form-data
            if "multipart/form-data" in content_type and "boundary=" in content_type:
                boundary_raw = content_type.split("boundary=")[1].strip().strip('"').strip("'")
                boundary = boundary_raw.encode("ascii")
                parts = parse_multipart(body, boundary)
                for header_text, content in parts:
                    if 'filename="' in header_text:
                        orig_name = header_text.split('filename="')[1].split('"')[0]
                        ext = Path(orig_name).suffix or ".jpg"
                        saved_filename = f"report_{int(time.time() * 1000)}_{uuid.uuid4().hex[:6]}{ext}"
                        out_path = UPLOAD_DIR / saved_filename
                        with open(out_path, "wb") as f:
                            f.write(content)
                        break

            # Handle raw binary image payload
            if not saved_filename:
                ext = ".jpg"
                if "png" in content_type:
                    ext = ".png"
                elif "webp" in content_type:
                    ext = ".webp"
                saved_filename = f"report_{int(time.time() * 1000)}_{uuid.uuid4().hex[:6]}{ext}"
                out_path = UPLOAD_DIR / saved_filename
                with open(out_path, "wb") as f:
                    f.write(body)

            # Mirror to backend uploads directory if present
            if BACKEND_UPLOAD_DIR.exists():
                try:
                    shutil.copy2(out_path, BACKEND_UPLOAD_DIR / saved_filename)
                except Exception:
                    pass

            host = self.headers.get("Host", f"localhost:{PORT}")
            lan_ip = get_lan_ip()
            image_url_direct = f"http://{host}/uploads/{saved_filename}"
            image_url_emulator = f"http://10.0.2.2:{PORT}/uploads/{saved_filename}"
            image_url_lan = f"http://{lan_ip}:{PORT}/uploads/{saved_filename}"

            response_data = {
                "status": "success",
                "filename": saved_filename,
                "imageUrl": image_url_direct,
                "emulatorUrl": image_url_emulator,
                "lanUrl": image_url_lan,
                "relativeUrl": f"/uploads/{saved_filename}",
                "sizeBytes": (UPLOAD_DIR / saved_filename).stat().st_size
            }

            self._set_headers(201, "application/json")
            self.wfile.write(json.dumps(response_data, indent=2).encode("utf-8"))
            return

        # ── AI Chatbot API Endpoint (Ollama RAG + Fallback) ───────────────────
        if path in ("/api/chat", "/chat"):
            content_length = int(self.headers.get("Content-Length", 0))
            if content_length == 0:
                self._set_headers(400, "application/json")
                self.wfile.write(json.dumps({"error": "Empty message payload"}).encode("utf-8"))
                return

            body = self.rfile.read(content_length).decode("utf-8", errors="replace")
            try:
                req_json = json.loads(body)
            except Exception as e:
                self._set_headers(400, "application/json")
                self.wfile.write(json.dumps({"error": f"Invalid JSON: {str(e)}"}).encode("utf-8"))
                return

            chat_result = process_ai_chat(req_json)
            self._set_headers(200, "application/json")
            self.wfile.write(json.dumps(chat_result, indent=2).encode("utf-8"))
            return

        self._set_headers(404, "application/json")
        self.wfile.write(json.dumps({"error": "Endpoint not found"}).encode("utf-8"))

    def do_DELETE(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path.startswith("/uploads/"):
            filename = urllib.parse.unquote(path[len("/uploads/"):].strip("/"))
            file_path = UPLOAD_DIR / filename
            if file_path.is_file() and file_path.resolve().is_relative_to(UPLOAD_DIR.resolve()):
                try:
                    file_path.unlink()
                    self._set_headers(200, "application/json")
                    self.wfile.write(json.dumps({"status": "deleted", "filename": filename}).encode("utf-8"))
                    return
                except Exception as e:
                    self._set_headers(500, "application/json")
                    self.wfile.write(json.dumps({"error": str(e)}).encode("utf-8"))
                    return

        self._set_headers(404, "application/json")
        self.wfile.write(json.dumps({"error": "File not found"}).encode("utf-8"))

    def _serve_dashboard(self):
        files = sorted(UPLOAD_DIR.glob("*"), key=lambda p: p.stat().st_mtime, reverse=True)

        cards_html = ""
        for f in files:
            file_url = f"/uploads/{f.name}"
            size_kb = f.stat().st_size / 1024
            mtime = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(f.stat().st_mtime))
            cards_html += f"""
            <div class="card">
                <img src="{file_url}" alt="{f.name}" loading="lazy"/>
                <div class="card-info">
                    <div class="card-title" title="{f.name}">{f.name}</div>
                    <div class="card-meta">{size_kb:.1f} KB &bull; {mtime}</div>
                    <a href="{file_url}" target="_blank" class="url-link">Open URL</a>
                </div>
            </div>
            """

        if not files:
            cards_html = "<div class='empty'>No images uploaded yet. Use the upload box below or the FixMyCity Android app.</div>"

        html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FixMyCity Local Image Storage</title>
    <style>
        * {{ box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }}
        body {{ background: #0f172a; color: #f8fafc; padding: 24px; }}
        .container {{ max-width: 1000px; margin: 0 auto; }}
        header {{ display: flex; justify-content: space-between; align-items: center; padding-bottom: 20px; border-bottom: 1px solid #334155; margin-bottom: 24px; }}
        h1 {{ font-size: 24px; font-weight: 700; color: #38bdf8; display: flex; align-items: center; gap: 10px; }}
        .badge {{ background: #0284c7; color: white; padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: 600; }}
        .guide-box {{ background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 16px 20px; margin-bottom: 24px; }}
        .guide-box h3 {{ color: #cbd5e1; font-size: 14px; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }}
        .guide-box code {{ background: #0f172a; color: #38bdf8; padding: 2px 6px; border-radius: 6px; font-size: 13px; }}
        .upload-section {{ background: #1e293b; border: 2px dashed #475569; border-radius: 12px; padding: 24px; text-align: center; margin-bottom: 24px; }}
        .upload-section input[type="file"] {{ display: none; }}
        .btn {{ background: #0284c7; color: white; border: none; padding: 10px 20px; border-radius: 8px; font-weight: 600; cursor: pointer; font-size: 14px; display: inline-block; transition: background 0.2s; }}
        .btn:hover {{ background: #0369a1; }}
        .gallery-title {{ font-size: 18px; font-weight: 600; margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center; }}
        .gallery {{ display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }}
        .card {{ background: #1e293b; border: 1px solid #334155; border-radius: 12px; overflow: hidden; display: flex; flex-direction: column; }}
        .card img {{ width: 100%; height: 160px; object-fit: cover; background: #0f172a; }}
        .card-info {{ padding: 12px; }}
        .card-title {{ font-size: 13px; font-weight: 600; color: #e2e8f0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }}
        .card-meta {{ font-size: 11px; color: #94a3b8; margin: 4px 0 8px 0; }}
        .url-link {{ font-size: 12px; color: #38bdf8; text-decoration: none; font-weight: 500; }}
        .url-link:hover {{ text-decoration: underline; }}
        .empty {{ text-align: center; color: #94a3b8; padding: 40px 0; grid-column: 1 / -1; }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>FixMyCity Local Image Server</h1>
            <span class="badge">Running on port {PORT}</span>
        </header>

        <div class="guide-box">
            <h3>Connection Guide for Android</h3>
            <p style="font-size: 13px; color: #94a3b8; line-height: 1.6;">
                &bull; <strong>Android Emulator:</strong> Upload to <code>http://10.0.2.2:{PORT}/upload</code><br/>
                &bull; <strong>Host Browser:</strong> Access dashboard and files at <code>http://localhost:{PORT}</code><br/>
                &bull; <strong>Physical Phone:</strong> Use your laptop's Wi-Fi LAN IP, e.g., <code>http://&lt;your-laptop-ip&gt;:{PORT}/upload</code>
            </p>
        </div>

        <div class="upload-section">
            <form id="uploadForm" enctype="multipart/form-data">
                <p style="margin-bottom: 12px; color: #cbd5e1; font-size: 14px;">Test upload an image from your laptop:</p>
                <label class="btn">
                    Choose Image to Upload
                    <input type="file" id="fileInput" name="file" accept="image/*" onchange="uploadFile()"/>
                </label>
                <span id="uploadStatus" style="margin-left: 12px; font-size: 13px; color: #38bdf8;"></span>
            </form>
        </div>

        <div class="gallery-title">
            <span>Stored Report Images ({len(files)})</span>
            <button class="btn" style="padding: 6px 14px; font-size: 12px;" onclick="location.reload()">Refresh</button>
        </div>

        <div class="gallery">
            {cards_html}
        </div>
    </div>

    <script>
        async function uploadFile() {{
            const input = document.getElementById('fileInput');
            const status = document.getElementById('uploadStatus');
            if (!input.files || input.files.length === 0) return;

            const file = input.files[0];
            const formData = new FormData();
            formData.append('image', file);

            status.textContent = 'Uploading ' + file.name + '...';
            try {{
                const res = await fetch('/upload', {{ method: 'POST', body: formData }});
                const data = await res.json();
                if (res.ok) {{
                    status.textContent = 'Uploaded successfully!';
                    setTimeout(() => location.reload(), 600);
                }} else {{
                    status.textContent = 'Error: ' + (data.error || 'Upload failed');
                }}
            }} catch (e) {{
                status.textContent = 'Upload failed: ' + e.message;
            }}
        }}
    </script>
</body>
</html>
"""
        self._set_headers(200, "text/html; charset=utf-8")
        self.wfile.write(html.encode("utf-8"))


def run_server(port=PORT):
    server_address = ("0.0.0.0", port)
    HTTPServer.allow_reuse_address = True
    httpd = HTTPServer(server_address, ImageStorageHandler)
    lan_ip = get_lan_ip()
    best_model = get_best_ollama_model()
    print("=" * 64)
    print(f"  FixMyCity Local Image Storage & Civic AI Server")
    print("=" * 64)
    print(f"  Web Dashboard   : http://localhost:{port}")
    print(f"  Upload API      : http://localhost:{port}/upload (POST)")
    print(f"  AI Chat Copilot : http://localhost:{port}/api/chat (POST)")
    print(f"  Active AI Model : Ollama ({best_model}) with RAG Summarizer")
    print(f"  Android Emulator: http://10.0.2.2:{port}")
    print(f"  Physical Phone  : http://{lan_ip}:{port}")
    print(f"  Storage Folder  : {UPLOAD_DIR}")
    print(f"  Stock Images    : 8 categories preloaded")
    print("=" * 64)
    print(f"Press Ctrl+C to stop the server.")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        httpd.server_close()


if __name__ == "__main__":
    run_server()

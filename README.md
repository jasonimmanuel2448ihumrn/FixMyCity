# 🏙️ FixMyCity

> **A Location-Aware Citizen Grievance Redressal and Municipal Infrastructure Management Platform**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%20(API%2026%2B)-green.svg?style=flat&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Cloud-Firebase%20Auth%20%7C%20Firestore-orange.svg?style=flat&logo=firebase)](https://firebase.google.com)
[![Ollama](https://img.shields.io/badge/AI-Ollama%20Local%20LLM%20RAG-black.svg?style=flat)](https://ollama.com)

---

## 📌 Overview

**FixMyCity** bridges the gap between citizens and municipal corporations to resolve civic infrastructure defects (potholes, garbage accumulation, water leakages, streetlight outages, drainage blockages, and road damage). 

Built natively with **Jetpack Compose** and **Kotlin Coroutines**, it provides a streamlined 3-step complaint reporting process, an interactive Web Mercator map picker, real-time 5-stage progress milestone tracking, role-based municipal administrative triage, and an intelligent **Civic AI Copilot** powered by local LLMs via Ollama.

---

## ✨ Key Features

- **📱 Declarative Native UI**: 100% Jetpack Compose with Material 3 design, custom design system, dynamic cards, and edge-to-edge support.
- **🗺️ Interactive Visual Map**: OpenStreetMap/Esri tile canvas using Web Mercator spherical projection mathematics, pan/drag gestures, coordinate jumping, and geocoding.
- **📍 Location & Reverse Geocoding**: Multi-provider location resolver (`GPS_PROVIDER`, `NETWORK_PROVIDER`, `PASSIVE_PROVIDER`) with reverse geocoding to human-readable street addresses.
- **📷 Evidence Capture**: Built-in camera integration via `FileProvider` and gallery image picker with smart category stock fallbacks.
- **🔄 5-Stage Grievance Lifecycle**: Tracks complaints across audited milestones: `SUBMITTED` ➔ `UNDER_REVIEW` ➔ `ASSIGNED` ➔ `IN_PROGRESS` ➔ `RESOLVED`.
- **🏛️ Municipal Admin Dashboard**: Web portal (port 8080) and in-app administrative dashboard for municipal officers to reassign priorities, update statuses, and log inspection timestamps.
- **🤖 Civic AI Copilot (RAG + Ollama)**: Local LLM-driven chatbot that ingests live civic records to summarize area issues, draft complaints, track turnaround SLAs, and trigger in-app deep actions (e.g. 1-tap complaint reporting or dialing municipal helpline `1916`).
- **🛡️ Offline-First Architecture**: Resilient repository pattern that gracefully falls back to local cache and embedded datasets during network outages.

---

## 🏗️ System Architecture

```
                    ┌────────────────────────────────────────┐
                    │      Citizen Android Application       │
                    │   (Jetpack Compose + MVVM + StateFlow) │
                    └───────────────────┬────────────────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 ▼                                             ▼
  ┌─────────────────────────────┐               ┌─────────────────────────────┐
  │   Google Firebase (Cloud)   │               │   Local Server Engine (:8080)│
  │ • Authentication (JWT)      │               │ • Python (server.py)        │
  │ • Cloud Firestore DB        │               │ • Node.js (server.js)       │
  │ • Security Rules            │               │ • Image Uploads & Admin Web │
  └─────────────────────────────┘               └──────────────┬──────────────┘
                                                               │
                                                               ▼
                                                ┌─────────────────────────────┐
                                                │      Ollama Local Daemon    │
                                                │ (Granite / LLaMA 3.1 / RAG) │
                                                └─────────────────────────────┘
```

---

## 📂 Project Structure

```
FixMyCity/
├── app/                        # Android Client Application
│   ├── src/main/java/com/fixmycity/app/
│   │   ├── data/               # Models, Repository, Mock Fallbacks
│   │   ├── domain/             # Domain logic & Impact Statistics
│   │   ├── ui/
│   │   │   ├── components/     # Reusable Compose Design System
│   │   │   ├── navigation/     # NavHost & Screen Routes
│   │   │   ├── screens/        # Auth, Home, Report, Detail, Admin, AI Copilot
│   │   │   └── theme/          # Typography, Color palettes, M3 Theme
│   │   └── utils/              # ServerConfig, LocationUtils, DateUtils
│   └── src/main/res/           # Vector drawables, stock assets, mipmaps
├── backend/                    # Node.js REST API & Municipal Web Portal
│   ├── public/                 # Web Admin Dashboard (HTML/CSS/JS)
│   ├── data/                   # Seed & database records
│   └── server.js               # Express API (Auth, Reports, Status, Uploads)
├── server/                     # Lightweight Python Local Server & AI Proxy
│   ├── server.py               # Image hosting + Ollama RAG endpoint
│   └── uploads/                # Local evidence storage & category assets
├── FixMyCity_Paper.tex         # IEEE Conference Research Paper source
└── start_server.sh             # Convenience launcher script
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (Ladybug / Iguana or later) with Android SDK 35 & JDK 17
- **Node.js** (v18+) or **Python** (3.10+)
- *(Optional)* [Ollama](https://ollama.com) running locally for AI Copilot (`ollama run granite3.1-moe:1b` or `llama3`)

### 1. Run the Local Backend / Image Server

You can launch either server on port `8080`:

**Option A — Python Server (Zero dependencies):**
```bash
./start_server.sh
# Server starts at http://localhost:8080
```

**Option B — Node.js Express Server:**
```bash
cd backend
npm install
npm start
# Web Admin Dashboard: http://localhost:8080
```

### 2. Configure Firebase

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com).
2. Enable **Email/Password Authentication** and **Cloud Firestore**.
3. Place your `google-services.json` inside the `app/` directory.
4. *(Optional for Node.js Admin)* Place your Firebase Admin private key at `backend/serviceAccountKey.json` (see `backend/serviceAccountKey.json.example`).

### 3. Build & Run the Android App

1. Open the project in Android Studio.
2. Let Gradle sync dependencies.
3. Run on an Android Emulator or physical device connected via USB/Wi-Fi:
   - **Emulator**: Automatically connects to `http://10.0.2.2:8080`.
   - **Physical Device**: Configure your computer's LAN IP in the in-app settings (or via `ServerConfig.kt`).

---

## 📜 Research Paper

This project is backed by an IEEE conference research paper:
> **"FixMyCity: A Location-Aware Citizen Grievance Redressal and Municipal Infrastructure Management Platform"**
> Available in LaTeX (`FixMyCity_Paper.tex`) and compiled PDF format (`FixMyCity_Paper.pdf`).

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).

# FixMyCity Local Backend

A lightweight REST API server and Admin Dashboard for the FixMyCity Android application.

## 🚀 Quick Start

### 1. Install Dependencies
```bash
cd backend
npm install
```

### 2. Start the Backend Server
```bash
npm start
```
The server will start on port `8080`:
- **Web Admin Dashboard**: http://localhost:8080
- **REST API Base URL**: http://localhost:8080/api

---

## 📱 Android App Connection

- **Android Emulator**: Uses `http://10.0.2.2:8080/api/` (configured as default in `NetworkConfig.kt`).
- **Physical Device (via Wi-Fi)**: Set your computer's local LAN IP in `NetworkConfig.kt` (e.g. `http://192.168.1.100:8080/api/`).
- **Graceful Fallback**: If the server is offline or unreachable, the Android app falls back to local cached seed data so the UI remains fully usable.

---

## 🛠️ API Endpoints

### 🔐 Authentication
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Login with `{ email, password }` |
| `POST` | `/api/auth/signup` | Register new user `{ name, email, phone, password }` |
| `GET` | `/api/auth/me` | Fetch logged-in user profile & stats |
| `PUT` | `/api/auth/profile` | Update profile information |
| `POST` | `/api/auth/forgot-password`| Send password reset instruction |

### 📋 Reports (Civic Issues)
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/reports` | Get reports (supports `?category=`, `?status=`, `?search=`, `?userId=`) |
| `GET` | `/api/reports/:id` | Get report by ID with full timeline steps |
| `POST` | `/api/reports` | Create new civic complaint |
| `PATCH` | `/api/reports/:id/status` | Update report status (`SUBMITTED`, `UNDER_REVIEW`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `REJECTED`) |
| `DELETE` | `/api/reports/:id` | Delete report |

### 🔔 Notifications & Stats
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/notifications` | Get notifications |
| `PATCH` | `/api/notifications/:id/read` | Mark single notification read |
| `POST` | `/api/notifications/read-all` | Mark all notifications read |
| `GET` | `/api/stats` | Get civic impact & resolution stats |
| `GET` | `/api/categories` | Get civic problem categories |
| `POST` | `/api/upload` | Upload report photo (`multipart/form-data`) |
| `POST` | `/api/reset` | Reset database to initial seed data |

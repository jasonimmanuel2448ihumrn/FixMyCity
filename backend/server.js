const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const multer = require('multer');

const app = express();
const PORT = process.env.PORT || 8080;

// Directories
const DATA_DIR = path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'database.json');
const SEED_FILE = path.join(DATA_DIR, 'seed.json');
const UPLOADS_DIR = path.join(__dirname, 'uploads');
const PUBLIC_DIR = path.join(__dirname, 'public');

if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
if (!fs.existsSync(UPLOADS_DIR)) fs.mkdirSync(UPLOADS_DIR, { recursive: true });
if (!fs.existsSync(PUBLIC_DIR)) fs.mkdirSync(PUBLIC_DIR, { recursive: true });

// Multer Storage Configuration
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, UPLOADS_DIR);
  },
  filename: function (req, file, cb) {
    const ext = path.extname(file.originalname) || '.jpg';
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'report-' + uniqueSuffix + ext);
  }
});
const upload = multer({ storage: storage, limits: { fileSize: 10 * 1024 * 1024 } });

// Database Helper
function loadDatabase() {
  try {
    if (!fs.existsSync(DB_FILE)) {
      if (fs.existsSync(SEED_FILE)) {
        const seedData = fs.readFileSync(SEED_FILE, 'utf8');
        fs.writeFileSync(DB_FILE, seedData, 'utf8');
        return JSON.parse(seedData);
      }
      const emptyDb = { users: [], reports: [], categories: [], notifications: [] };
      fs.writeFileSync(DB_FILE, JSON.stringify(emptyDb, null, 2), 'utf8');
      return emptyDb;
    }
    return JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
  } catch (err) {
    console.error('Error loading database:', err);
    return { users: [], reports: [], categories: [], notifications: [] };
  }
}

function saveDatabase(data) {
  try {
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2), 'utf8');
  } catch (err) {
    console.error('Error saving database:', err);
  }
}

// Middlewares
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use('/uploads', express.static(UPLOADS_DIR));
app.use(express.static(PUBLIC_DIR));

// Logging Middleware
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.originalUrl}`);
  next();
});

// ── Health Check ─────────────────────────────────────────────────────────────
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', service: 'FixMyCity Local Backend', version: '1.0.0', time: new Date().toISOString() });
});

// ── Auth Endpoints ───────────────────────────────────────────────────────────
app.post('/api/auth/login', (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'Email and password are required' });
  }
  const db = loadDatabase();
  let user = db.users.find(u => u.email.toLowerCase() === email.toLowerCase());
  if (!user) {
    // Auto-create demo user if not found for seamless local testing
    user = {
      id: 'user_' + Date.now(),
      name: email.split('@')[0].replace('.', ' '),
      email: email,
      phone: '+91 98765 00000',
      password: password,
      role: 'Citizen',
      avatarUrl: null,
      reportsSubmitted: 0,
      reportsResolved: 0,
      createdAt: new Date().toISOString()
    };
    db.users.push(user);
    saveDatabase(db);
  }

  // Update counts
  const userReports = db.reports.filter(r => r.userId === user.id);
  user.reportsSubmitted = userReports.length;
  user.reportsResolved = userReports.filter(r => r.status === 'RESOLVED').length;

  const { password: _, ...safeUser } = user;
  res.json({
    success: true,
    token: 'jwt_mock_' + user.id + '_' + Date.now(),
    user: safeUser
  });
});

app.post('/api/auth/signup', (req, res) => {
  const { name, email, phone, password } = req.body;
  if (!name || !email || !password) {
    return res.status(400).json({ error: 'Name, email, and password are required' });
  }
  const db = loadDatabase();
  const existing = db.users.find(u => u.email.toLowerCase() === email.toLowerCase());
  if (existing) {
    return res.status(409).json({ error: 'User with this email already exists' });
  }

  const newUser = {
    id: 'user_' + Date.now(),
    name: name.trim(),
    email: email.trim().toLowerCase(),
    phone: phone || '',
    password: password,
    role: 'Citizen',
    avatarUrl: null,
    reportsSubmitted: 0,
    reportsResolved: 0,
    createdAt: new Date().toISOString()
  };
  db.users.push(newUser);
  saveDatabase(db);

  const { password: _, ...safeUser } = newUser;
  res.status(201).json({
    success: true,
    token: 'jwt_mock_' + newUser.id + '_' + Date.now(),
    user: safeUser
  });
});

app.get('/api/auth/me', (req, res) => {
  const db = loadDatabase();
  const userId = req.query.userId || (db.users[0] && db.users[0].id);
  const user = db.users.find(u => u.id === userId) || db.users[0];
  if (!user) {
    return res.status(404).json({ error: 'User not found' });
  }
  const userReports = db.reports.filter(r => r.userId === user.id);
  user.reportsSubmitted = userReports.length;
  user.reportsResolved = userReports.filter(r => r.status === 'RESOLVED').length;

  const { password: _, ...safeUser } = user;
  res.json({ user: safeUser });
});

app.put('/api/auth/profile', (req, res) => {
  const { userId, name, phone, role, avatarUrl } = req.body;
  const db = loadDatabase();
  const userIndex = db.users.findIndex(u => u.id === (userId || 'user_1'));
  if (userIndex === -1) {
    return res.status(404).json({ error: 'User not found' });
  }
  if (name) db.users[userIndex].name = name;
  if (phone) db.users[userIndex].phone = phone;
  if (role) db.users[userIndex].role = role;
  if (avatarUrl !== undefined) db.users[userIndex].avatarUrl = avatarUrl;
  saveDatabase(db);

  const { password: _, ...safeUser } = db.users[userIndex];
  res.json({ success: true, user: safeUser });
});

app.post('/api/auth/forgot-password', (req, res) => {
  const { email } = req.body;
  if (!email) return res.status(400).json({ error: 'Email is required' });
  res.json({ success: true, message: 'Password reset link sent to ' + email });
});

app.get('/api/users', (req, res) => {
  const db = loadDatabase();
  const safeUsers = db.users.map(({ password: _, ...u }) => {
    const userReports = db.reports.filter(r => r.userId === u.id);
    return {
      ...u,
      reportsSubmitted: userReports.length,
      reportsResolved: userReports.filter(r => r.status === 'RESOLVED').length
    };
  });
  res.json(safeUsers);
});

// ── Reports Endpoints ────────────────────────────────────────────────────────
app.get('/api/reports', (req, res) => {
  const { category, status, search, userId } = req.query;
  const db = loadDatabase();
  let results = [...db.reports];

  if (userId) {
    results = results.filter(r => r.userId === userId);
  }
  if (category && category !== 'All') {
    results = results.filter(r => r.category.toLowerCase() === category.toLowerCase());
  }
  if (status && status !== 'All') {
    const normalizedStatus = status.toUpperCase().replace(' ', '_');
    results = results.filter(r => r.status === normalizedStatus);
  }
  if (search) {
    const q = search.toLowerCase();
    results = results.filter(r =>
      (r.title && r.title.toLowerCase().includes(q)) ||
      (r.description && r.description.toLowerCase().includes(q)) ||
      (r.category && r.category.toLowerCase().includes(q)) ||
      (r.location && r.location.toLowerCase().includes(q))
    );
  }

  // Sort newest first
  results.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
  res.json(results);
});

app.get('/api/reports/:id', (req, res) => {
  const db = loadDatabase();
  const report = db.reports.find(r => r.id === req.params.id);
  if (!report) {
    return res.status(404).json({ error: 'Report not found' });
  }
  res.json(report);
});

function formatCurrentDate() {
  const options = { month: 'short', day: 'numeric', year: 'numeric' };
  return new Date().toLocaleDateString('en-US', options);
}

app.post('/api/reports', (req, res) => {
  const { title, description, category, location, imageUrl, userId, userName } = req.body;
  if (!category || !description) {
    return res.status(400).json({ error: 'Category and description are required' });
  }

  const db = loadDatabase();
  const reportDate = formatCurrentDate();
  const newReportId = 'r' + (Date.now());
  const reportTitle = title || `${category} on ${location || 'Main Road'}`;

  let authorName = userName;
  if (!authorName) {
    const author = db.users.find(u => u.id === (userId || 'user_1'));
    authorName = author ? author.name : 'Guest';
  }
  if (!authorName || authorName.trim() === '' || userId === 'guest') {
    authorName = 'Guest';
  }

  const newReport = {
    id: newReportId,
    title: reportTitle,
    description: description.trim(),
    category: category,
    location: location || 'Vashi, Navi Mumbai',
    date: reportDate,
    status: 'SUBMITTED',
    imageUrl: imageUrl || null,
    userId: userId || (authorName === 'Guest' ? 'guest' : 'user_1'),
    userName: authorName,
    timelineSteps: [
      { status: 'SUBMITTED', label: 'Submitted', date: reportDate, isCompleted: true },
      { status: 'UNDER_REVIEW', label: 'Under Review', date: null, isCompleted: false },
      { status: 'ASSIGNED', label: 'Assigned', date: null, isCompleted: false },
      { status: 'IN_PROGRESS', label: 'In Progress', date: null, isCompleted: false },
      { status: 'RESOLVED', label: 'Resolved', date: null, isCompleted: false }
    ],
    createdAt: new Date().toISOString()
  };

  db.reports.unshift(newReport);

  // Auto-generate notification
  const newNotification = {
    id: 'n_' + Date.now(),
    title: 'Report Submitted',
    message: `Your report "${newReport.title}" has been received and submitted for review.`,
    time: 'Just now',
    isRead: false,
    type: 'STATUS_UPDATE',
    reportId: newReportId,
    userId: newReport.userId,
    createdAt: new Date().toISOString()
  };
  db.notifications.unshift(newNotification);

  // Update user stats
  const user = db.users.find(u => u.id === newReport.userId);
  if (user) {
    user.reportsSubmitted = (user.reportsSubmitted || 0) + 1;
  }

  saveDatabase(db);
  res.status(201).json({ success: true, report: newReport });
});

app.patch('/api/reports/:id/status', (req, res) => {
  const { status, note } = req.body;
  if (!status) {
    return res.status(400).json({ error: 'Status is required' });
  }

  const validStatuses = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'REJECTED'];
  const normalizedStatus = status.toUpperCase().replace(' ', '_');
  if (!validStatuses.includes(normalizedStatus)) {
    return res.status(400).json({ error: `Invalid status. Must be one of: ${validStatuses.join(', ')}` });
  }

  const db = loadDatabase();
  const reportIndex = db.reports.findIndex(r => r.id === req.params.id);
  if (reportIndex === -1) {
    return res.status(404).json({ error: 'Report not found' });
  }

  const report = db.reports[reportIndex];
  report.status = normalizedStatus;
  const currentDateStr = formatCurrentDate();

  // Update timeline
  const statusLabels = {
    'SUBMITTED': 'Submitted',
    'UNDER_REVIEW': 'Under Review',
    'ASSIGNED': 'Assigned',
    'IN_PROGRESS': 'In Progress',
    'RESOLVED': 'Resolved',
    'REJECTED': 'Rejected'
  };

  const statusOrder = ['SUBMITTED', 'UNDER_REVIEW', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED'];
  const targetIdx = statusOrder.indexOf(normalizedStatus);

  if (targetIdx !== -1) {
    report.timelineSteps = statusOrder.map((st, idx) => ({
      status: st,
      label: statusLabels[st],
      date: idx <= targetIdx ? (report.timelineSteps[idx]?.date || currentDateStr) : null,
      isCompleted: idx <= targetIdx
    }));
  }

  // Create notification for citizen
  const notifType = normalizedStatus === 'RESOLVED' ? 'RESOLVED' : 'STATUS_UPDATE';
  const newNotification = {
    id: 'n_' + Date.now(),
    title: normalizedStatus === 'RESOLVED' ? 'Issue Resolved!' : `Status Update: ${statusLabels[normalizedStatus]}`,
    message: note || `Your report "${report.title}" status is now ${statusLabels[normalizedStatus]}.`,
    time: 'Just now',
    isRead: false,
    type: notifType,
    reportId: report.id,
    userId: report.userId,
    createdAt: new Date().toISOString()
  };
  db.notifications.unshift(newNotification);

  // Update user stats if resolved
  const user = db.users.find(u => u.id === report.userId);
  if (user) {
    const userReports = db.reports.filter(r => r.userId === user.id);
    user.reportsResolved = userReports.filter(r => r.status === 'RESOLVED').length;
  }

  saveDatabase(db);
  res.json({ success: true, report: report });
});

app.delete('/api/reports/:id', (req, res) => {
  const db = loadDatabase();
  const initialLength = db.reports.length;
  db.reports = db.reports.filter(r => r.id !== req.params.id);
  if (db.reports.length === initialLength) {
    return res.status(404).json({ error: 'Report not found' });
  }
  saveDatabase(db);
  res.json({ success: true, message: 'Report deleted successfully' });
});

// ── Categories Endpoints ─────────────────────────────────────────────────────
app.get('/api/categories', (req, res) => {
  const db = loadDatabase();
  res.json(db.categories || []);
});

// ── Notifications Endpoints ──────────────────────────────────────────────────
app.get('/api/notifications', (req, res) => {
  const { userId } = req.query;
  const db = loadDatabase();
  let results = [...db.notifications];
  if (userId) {
    results = results.filter(n => n.userId === userId);
  }
  results.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0));
  res.json(results);
});

app.patch('/api/notifications/:id/read', (req, res) => {
  const db = loadDatabase();
  const notif = db.notifications.find(n => n.id === req.params.id);
  if (!notif) return res.status(404).json({ error: 'Notification not found' });
  notif.isRead = true;
  saveDatabase(db);
  res.json({ success: true, notification: notif });
});

app.post('/api/notifications/read-all', (req, res) => {
  const { userId } = req.body;
  const db = loadDatabase();
  db.notifications.forEach(n => {
    if (!userId || n.userId === userId) {
      n.isRead = true;
    }
  });
  saveDatabase(db);
  res.json({ success: true, message: 'All notifications marked as read' });
});

app.post('/api/notifications', (req, res) => {
  const { title, message, type, reportId, userId } = req.body;
  if (!title || !message) {
    return res.status(400).json({ error: 'Title and message are required' });
  }
  const db = loadDatabase();
  const newNotification = {
    id: 'n_' + Date.now(),
    title: title.trim(),
    message: message.trim(),
    time: 'Just now',
    isRead: false,
    type: type || 'GENERAL',
    reportId: reportId || null,
    userId: userId || 'user_1',
    createdAt: new Date().toISOString()
  };
  db.notifications.unshift(newNotification);
  saveDatabase(db);
  res.status(201).json({ success: true, notification: newNotification });
});

// ── Stats Endpoint ───────────────────────────────────────────────────────────
app.get('/api/stats', (req, res) => {
  const { userId } = req.query;
  const db = loadDatabase();
  const reports = userId ? db.reports.filter(r => r.userId === userId) : db.reports;

  const totalSubmitted = reports.length;
  const inProgress = reports.filter(r => r.status === 'IN_PROGRESS' || r.status === 'UNDER_REVIEW' || r.status === 'ASSIGNED').length;
  const resolved = reports.filter(r => r.status === 'RESOLVED').length;
  const rejected = reports.filter(r => r.status === 'REJECTED').length;

  res.json({
    reportsSubmitted: totalSubmitted,
    inProgress: inProgress,
    resolved: resolved,
    rejected: rejected,
    totalUsers: db.users.length,
    activeIssues: totalSubmitted - resolved - rejected
  });
});

// ── Image Upload Endpoints ───────────────────────────────────────────────────
function handleUploadedFile(req, res, filename) {
  const host = req.get('host');
  const protocol = req.protocol;
  const fileUrl = `${protocol}://${host}/uploads/${filename}`;
  const emulatorUrl = `http://10.0.2.2:8080/uploads/${filename}`;

  // Mirror to server/uploads if directory exists
  const serverUploadsDir = path.join(__dirname, '..', 'server', 'uploads');
  if (fs.existsSync(serverUploadsDir)) {
    try {
      fs.copyFileSync(path.join(UPLOADS_DIR, filename), path.join(serverUploadsDir, filename));
    } catch (e) {}
  }

  res.json({
    success: true,
    status: 'success',
    filename: filename,
    url: fileUrl,
    imageUrl: fileUrl,
    emulatorUrl: emulatorUrl,
    relativeUrl: `/uploads/${filename}`
  });
}

const handleUploadRoute = (req, res) => {
  if (req.file) {
    return handleUploadedFile(req, res, req.file.filename);
  }
  // If sent as raw binary body
  if (req.body && Buffer.isBuffer(req.body) && req.body.length > 0) {
    const filename = `report_${Date.now()}_${Math.round(Math.random() * 1e6)}.jpg`;
    fs.writeFileSync(path.join(UPLOADS_DIR, filename), req.body);
    return handleUploadedFile(req, res, filename);
  }
  return res.status(400).json({ error: 'No image file uploaded' });
};

app.post('/api/upload', express.raw({ type: ['image/*', 'application/octet-stream'], limit: '10mb' }), upload.single('image'), handleUploadRoute);
app.post('/upload', express.raw({ type: ['image/*', 'application/octet-stream'], limit: '10mb' }), upload.single('image'), handleUploadRoute);


// ── AI Chatbot Endpoint (Ollama RAG + Fallback) ──────────────────────────────
app.post(['/api/chat', '/chat'], async (req, res) => {
  const { message, reports: clientReports, userId, userName, model } = req.body || {};
  if (!message || typeof message !== 'string') {
    return res.status(400).json({ error: 'Message is required' });
  }

  const db = loadDatabase();
  const activeReports = (clientReports && clientReports.length > 0) ? clientReports : (db.reports || []);

  const lowerMsg = message.toLowerCase();
  const isSummaryQuery = ['summar', 'overview', 'breakdown', 'stats', 'statistics', 'how many', 'all reports', 'list reports', 'what are the reports', 'what are the issues', 'what issues', 'what problems'].some(w => lowerMsg.includes(w));
  const isReportIntent = ['report', 'file', 'submit', 'lodge', 'log', 'register', 'new issue'].some(w => lowerMsg.includes(w)) && !isSummaryQuery;

  // Determine category if mentioned in citizen's query
  const catKeywords = [
    ['pothole', 'Pothole'], ['garbage', 'Garbage'], ['trash', 'Garbage'], ['waste', 'Garbage'],
    ['streetlight', 'Streetlight'], ['light', 'Streetlight'], ['lamp', 'Streetlight'],
    ['water', 'Water Leakage'], ['leak', 'Water Leakage'], ['drain', 'Drainage'], ['sewage', 'Drainage'],
    ['road', 'Road Damage'], ['divider', 'Road Damage'], ['park', 'Public Property'], ['property', 'Public Property']
  ];
  let matchedCat = null;
  for (const [kw, cat] of catKeywords) {
    if (lowerMsg.includes(kw)) {
      matchedCat = cat;
      break;
    }
  }

  let ragPrompt = '';
  if (isReportIntent) {
    ragPrompt = `You are FixMyCity Civic AI Copilot, a helpful municipal grievance assistant.

Citizen Name: ${userName || 'Citizen'}
Citizen Query: "${message.trim()}"

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
- Keep the response concise, formatted with markdown and emojis.`;
  } else if (isSummaryQuery) {
    const areas = ['vashi', 'belapur', 'nerul', 'koparkhairane', 'kharghar', 'airoli', 'sanpada', 'ghansoli', 'seawoods', 'panvel', 'sector 17', 'sector 10', 'sector 5', 'sector 20', 'sector 3', 'sector 9', 'palm beach', 'market'];
    const matchedArea = areas.find(a => lowerMsg.includes(a));

    if (matchedArea) {
      const areaReports = activeReports.filter(r =>
        ((r.location || '') + ' ' + (r.title || '')).toLowerCase().includes(matchedArea)
      );
      const areaTitle = matchedArea.charAt(0).toUpperCase() + matchedArea.slice(1);
      const total = areaReports.length;
      const resolved = areaReports.filter(r => r.status === 'RESOLVED').length;
      const inProg = areaReports.filter(r => r.status === 'IN_PROGRESS' || r.status === 'ASSIGNED').length;
      const underRev = areaReports.filter(r => r.status === 'UNDER_REVIEW' || r.status === 'SUBMITTED').length;

      const reportsContext = areaReports.slice(0, 10).map((r, i) =>
        `- #${(r.id || 'r' + (i + 1)).toUpperCase()} [${r.category}] '${r.title}' at ${r.location} | Status: ${r.status} | Details: ${(r.description || '').slice(0, 100)}`
      ).join('\n');

      ragPrompt = `You are FixMyCity Civic AI Copilot, an intelligent civic assistant for municipal grievance analytics.

--- VERIFIED GRIEVANCE DATABASE FOR ${areaTitle.toUpperCase()} (${total} records) ---
EXACT VERIFIED STATISTICS:
- Total complaints in ${areaTitle}: ${total}
- Resolved: ${resolved}
- In Progress: ${inProg}
- Under Review: ${underRev}

Matching Complaints in ${areaTitle}:
${reportsContext || 'No records found.'}

Citizen Name: ${userName || 'Citizen'}
Query: "${message.trim()}"

INSTRUCTIONS:
1. Provide a concise summary of reports in ${areaTitle}.
2. State the EXACT verified total of ${total} complaints found in ${areaTitle} (${resolved} resolved, ${inProg} in progress, ${underRev} under review).
3. UNDER NO CIRCUMSTANCES state numbers greater than ${total} or numbers beyond 100. The true count in ${areaTitle} is strictly ${total}.
4. Mention the specific complaint IDs from the records above.
5. Format with bold markdown headers and clean bullet points.`;
    } else {
      const total = activeReports.length;
      const resolved = activeReports.filter(r => r.status === 'RESOLVED').length;
      const inProg = activeReports.filter(r => r.status === 'IN_PROGRESS' || r.status === 'ASSIGNED').length;
      const underRev = activeReports.filter(r => r.status === 'UNDER_REVIEW' || r.status === 'SUBMITTED').length;

      const reportsContext = activeReports.slice(0, 15).map((r, i) =>
        `- #${(r.id || 'r' + (i + 1)).toUpperCase()} [${r.category}] '${r.title}' at ${r.location} | Status: ${r.status} | Details: ${(r.description || '').slice(0, 100)}`
      ).join('\n');

      ragPrompt = `You are FixMyCity Civic AI Copilot, an intelligent civic assistant for municipal grievance analytics.

--- LIVE GRIEVANCE DATABASE (${total} records) ---
EXACT VERIFIED STATISTICS:
- Total complaints: ${total}
- Resolved: ${resolved}
- In Progress: ${inProg}
- Under Review: ${underRev}

Live Records:
${reportsContext || 'No active reports.'}

Citizen Name: ${userName || 'Citizen'}
Query: "${message.trim()}"

INSTRUCTIONS:
1. Provide a concise summary of the reports in the database.
2. State the EXACT total count of ${total} complaints found (Resolved: ${resolved}, In Progress: ${inProg}, Under Review: ${underRev}).
3. UNDER NO CIRCUMSTANCES state numbers greater than ${total} or fabricate numbers beyond 100.
4. Highlight 2 to 4 notable incident IDs, location, and current progress.
5. Format with bold markdown headers and clean bullet points.`;
    }
  } else {
    const reportsContext = activeReports.slice(0, 15).map((r, i) =>
      `- #${(r.id || 'r' + (i + 1)).toUpperCase()} [${r.category}] '${r.title}' at ${r.location} (${r.status})`
    ).join('\n');

    ragPrompt = `You are FixMyCity Civic AI Copilot, an intelligent, empathetic civic assistant developed for municipal grievance resolution.

--- LIVE DATABASE REFERENCE ---
${reportsContext || 'No active reports.'}

Citizen Name: ${userName || 'Citizen'}
Query: "${message.trim()}"

INSTRUCTIONS:
1. Answer the user query directly and helpfully.
2. If drafting a complaint, provide a professional Title, Category, and Description.
3. If asking about SLAs or helplines, provide official municipal details (Central Helpline: 1916).
4. If asking to track a specific report, reference the record.
5. DO NOT provide an unprompted summary of the whole database.
6. Use emojis, bold markdown headers, and clean bullet points.`;
  }

  // 1. Try Ollama local daemon
  try {
    const ollamaResp = await fetch('http://127.0.0.1:11434/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: model || 'granite3.1-moe:1b',
        prompt: ragPrompt,
        stream: false,
        options: { temperature: 0.25, num_predict: 350 }
      })
    });

    if (ollamaResp.ok) {
      const data = await ollamaResp.json();
      if (data && data.response) {
        let actionType = 'NONE';
        let actionLabel = null;
        let actionPayload = null;

        if (lowerMsg.includes('1916') || lowerMsg.includes('emergency') || lowerMsg.includes('helpline')) {
          actionType = 'CALL_HELPLINE';
          actionLabel = 'Call City Helpline 1916';
          actionPayload = '1916';
        } else if (lowerMsg.includes('track') || lowerMsg.includes('my report') || lowerMsg.includes('my complaint') || lowerMsg.includes('status')) {
          actionType = 'VIEW_MY_REPORTS';
          actionLabel = 'View My Reports';
          actionPayload = null;
        } else if (isReportIntent) {
          actionType = 'REPORT_ISSUE';
          actionLabel = matchedCat ? `Report ${matchedCat} Issue` : 'Report an Issue';
          actionPayload = matchedCat;
        } else if (matchedCat) {
          actionType = 'REPORT_ISSUE';
          actionLabel = `Report ${matchedCat} Issue`;
          actionPayload = matchedCat;
        } else if (data.response.toLowerCase().includes('report an issue')) {
          actionType = 'REPORT_ISSUE';
          actionLabel = 'Report an Issue';
          actionPayload = null;
        }

        const cleanResp = data.response.replace(/\*\*/g, '').replace(/__/g, '').trim();
        return res.json({
          status: 'success',
          response: cleanResp,
          source: 'ollama',
          model: data.model || model || 'granite3.1-moe:1b',
          actionType,
          actionLabel,
          actionPayload
        });
      }
    }
  } catch (err) {
    // Ollama not reachable, fall through to built-in analytics engine
  }

  // 2. Built-in analytical engine fallback
  if (isReportIntent) {
    const text = matchedCat
      ? `I'm ready to help you report this **${matchedCat}** issue! 🚨\n\nTo submit it directly to municipal field engineers:\n1. Tap the **Report ${matchedCat} Issue** button below.\n2. Take or upload a photo showing the issue clearly.\n3. Confirm your GPS location and submit.\n\nOnce submitted, you will receive real-time updates as field teams inspect and fix it.`
      : `I'm ready to help you log a civic issue! 🛠️\n\nYou can report any neighborhood problem in 3 simple steps:\n1. Tap the **Report an Issue** button below.\n2. Select the category (Pothole, Garbage, Streetlight, Water Leakage, Drainage, Road Damage, Public Property).\n3. Attach a photo and confirm the location.\n\nYour complaint will be immediately dispatched to the appropriate municipal department with real-time status tracking.`;

    return res.json({
      status: 'success',
      response: text.replace(/\*\*/g, '').replace(/__/g, ''),
      source: 'local_engine',
      model: 'Civic Intelligence Engine',
      actionType: 'REPORT_ISSUE',
      actionLabel: matchedCat ? `Report ${matchedCat} Issue` : 'Report an Issue',
      actionPayload: matchedCat
    });
  }

  const areas = ['vashi', 'belapur', 'nerul', 'koparkhairane', 'kharghar', 'sector 17', 'sector 10', 'sector 5', 'sector 20', 'palm beach'];
  const matchedArea = areas.find(a => lowerMsg.includes(a));

  if (isSummaryQuery && matchedArea) {
    const areaReports = activeReports.filter(r => (r.location + ' ' + r.title).toLowerCase().includes(matchedArea));
    const areaTitle = matchedArea.charAt(0).toUpperCase() + matchedArea.slice(1);
    const resolved = areaReports.filter(r => r.status === 'RESOLVED').length;
    const inProg = areaReports.filter(r => r.status === 'IN_PROGRESS' || r.status === 'ASSIGNED').length;

    const listLines = areaReports.slice(0, 4).map(r => `• #${r.id.toUpperCase()} [${r.category}]: ${r.title} (${r.status})`).join('\n');
    const text = `📊 Civic Summary for ${areaTitle}\n\nFound ${areaReports.length} complaints in ${areaTitle}:\n• Resolved: ${resolved}\n• Active/In Progress: ${inProg}\n\nNotable Reports:\n${listLines || 'No recent complaints.'}\n\n_Tip: Municipal teams inspect this area regularly._`;

    return res.json({
      status: 'success',
      response: text.replace(/\*\*/g, '').replace(/__/g, ''),
      source: 'local_engine',
      model: 'Civic Intelligence Engine',
      actionType: 'REPORT_ISSUE',
      actionLabel: `Report Issue in ${areaTitle}`,
      actionPayload: null
    });
  }

  return res.json({
    status: 'success',
    response: `Hello ${userName || 'Citizen'}! 👋 I am your FixMyCity Civic AI Copilot.\n\nI can:\n• Summarize complaints by area (e.g. "Summarize reports in Vashi")\n• Track specific issues and check turnaround SLAs\n• Draft formal reports for fast submission\n\nHow can I help you today?`.replace(/\*\*/g, ''),
    source: 'local_engine',
    model: 'Civic Intelligence Engine',
    actionType: 'REPORT_ISSUE',
    actionLabel: 'Report an Issue',
    actionPayload: null
  });
});

// ── Reset Database ───────────────────────────────────────────────────────────
app.post('/api/reset', (req, res) => {
  try {
    if (fs.existsSync(SEED_FILE)) {
      const seedData = fs.readFileSync(SEED_FILE, 'utf8');
      fs.writeFileSync(DB_FILE, seedData, 'utf8');
      res.json({ success: true, message: 'Database reset to seed data' });
    } else {
      res.status(500).json({ error: 'Seed file not found' });
    }
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Start Server
app.listen(PORT, '0.0.0.0', () => {
  console.log(`====================================================`);
  console.log(` FixMyCity Local Backend Running on Port ${PORT}`);
  console.log(` REST API Base:  http://localhost:${PORT}/api`);
  console.log(` Web Dashboard:  http://localhost:${PORT}`);
  console.log(` Android Emulator Endpoint: http://10.0.2.2:${PORT}/api`);
  console.log(`====================================================`);
});

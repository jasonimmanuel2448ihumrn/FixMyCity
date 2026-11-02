/**
 * FixMyCity — Firebase Seed Script (Firestore-only)
 * ---------------------------------------------------
 * Writes dummy users + reports + notifications directly to Firestore.
 * Does NOT create Firebase Auth accounts (avoids OAuth2 network issues).
 *
 * SETUP:
 *   1. Firebase Console → Project Settings → Service accounts
 *      → "Generate new private key" → save as "serviceAccountKey.json" here
 *   2. node seed-firebase.js
 *
 * Safe to re-run — skips docs that already exist.
 */

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore }        = require('firebase-admin/firestore');
const path = require('path');
const fs   = require('fs');

// ── Load service account ──────────────────────────────────────────────────────
const KEY_FILE = path.join(__dirname, 'serviceAccountKey.json');
if (!fs.existsSync(KEY_FILE)) {
  console.error('\n❌  serviceAccountKey.json not found in backend/');
  console.error('   Firebase Console → Project Settings → Service accounts → Generate key\n');
  process.exit(1);
}

initializeApp({ credential: cert(require(KEY_FILE)) });
const db = getFirestore();

// ── Helpers ───────────────────────────────────────────────────────────────────
function dateStr(daysAgo) {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}
function ts(daysAgo = 0) {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  return d.getTime();
}
function makeTimeline(status, daysAgo) {
  const ORDER  = ['SUBMITTED','UNDER_REVIEW','ASSIGNED','IN_PROGRESS','RESOLVED'];
  const LABELS = { SUBMITTED:'Submitted', UNDER_REVIEW:'Under Review',
                   ASSIGNED:'Assigned', IN_PROGRESS:'In Progress', RESOLVED:'Resolved' };
  const idx = ORDER.indexOf(status);
  return ORDER.map((s, i) => ({
    status: s, label: LABELS[s],
    date: i <= idx ? dateStr(Math.max(0, daysAgo - i)) : null,
    isCompleted: i <= idx
  }));
}

// ── Users (fixed UIDs so reports link correctly) ──────────────────────────────
const USERS = [
  { uid: 'seed_rahul_001', name: 'Rahul Sharma', email: 'rahul.sharma@fixmycity.demo',
    phone: '+91 98765 43210', role: 'Citizen' },
  { uid: 'seed_priya_002', name: 'Priya Mehta',  email: 'priya.mehta@fixmycity.demo',
    phone: '+91 91234 56789', role: 'Citizen' },
  { uid: 'seed_amit_003', name: 'Amit Patil',   email: 'amit.patil@fixmycity.demo',
    phone: '+91 70000 11223', role: 'Citizen' },
  { uid: 'seed_sneha_004', name: 'Sneha Joshi',  email: 'sneha.joshi@fixmycity.demo',
    phone: '+91 88888 77777', role: 'Citizen' },
];

// ── Reports ───────────────────────────────────────────────────────────────────
const [rahul, priya, amit, sneha] = USERS;
const REPORTS = [
  { title: 'Large Pothole on Sector 17 Road',
    description: 'A massive pothole near the main intersection of Sector 17 is causing traffic disruption and has already damaged vehicles. Needs immediate repair.',
    category: 'Pothole', location: 'Sector 17, Vashi, Navi Mumbai',
    status: 'IN_PROGRESS', daysAgo: 10, user: rahul },

  { title: 'Road Divider Completely Broken',
    description: 'The road divider near Palm Beach Road entry is completely damaged. Motorcyclists are riding the wrong way making it very dangerous.',
    category: 'Road Damage', location: 'Palm Beach Road, Navi Mumbai',
    status: 'RESOLVED', daysAgo: 20, user: rahul },

  { title: 'Overflowing Garbage Bin Near Station',
    description: 'The municipal garbage bin near Vashi station has been overflowing for 4 days. Unbearable smell and attracting stray animals.',
    category: 'Garbage', location: 'Vashi Railway Station, Navi Mumbai',
    status: 'UNDER_REVIEW', daysAgo: 4, user: rahul },

  { title: 'Broken Streetlight Causing Night Accidents',
    description: 'The streetlight at the corner of Sector 9 has been non-functional for 3 weeks. Two minor accidents have occurred due to poor visibility.',
    category: 'Streetlight', location: 'Sector 9, Vashi, Navi Mumbai',
    status: 'ASSIGNED', daysAgo: 7, user: priya },

  { title: 'Water Pipeline Burst on Market Road',
    description: 'A pipeline has burst near the market. Water flowing for 2 days causing waterlogging and wastage.',
    category: 'Water Leakage', location: 'Sector 4 Market, Vashi, Navi Mumbai',
    status: 'RESOLVED', daysAgo: 15, user: priya },

  { title: 'Drainage Blocked — Street Floods Every Rain',
    description: 'The main drainage near our housing society is blocked. After every rain the street floods for 2-3 hours. Elderly residents are suffering.',
    category: 'Drainage', location: 'Sector 21, Nerul, Navi Mumbai',
    status: 'SUBMITTED', daysAgo: 2, user: priya },

  { title: 'Park Benches Broken with Sharp Edges',
    description: 'Multiple park benches in Sector 10 garden are broken with sharp metal edges. Children and senior citizens are at risk of injury.',
    category: 'Public Property', location: 'Sector 10 Garden, Vashi, Navi Mumbai',
    status: 'UNDER_REVIEW', daysAgo: 5, user: amit },

  { title: 'Multiple Potholes on Highway Slip Road',
    description: 'At least 7-8 large potholes on the Sion-Panvel highway slip road. Heavy rain has made them deeper and vehicles are swerving dangerously.',
    category: 'Pothole', location: 'Sion-Panvel Highway Slip Road, Navi Mumbai',
    status: 'IN_PROGRESS', daysAgo: 8, user: amit },

  { title: 'Garbage Dump Near School Gate',
    description: 'An unauthorized garbage dump has formed near the school gate in Sector 6. Affecting school children. Immediate action needed.',
    category: 'Garbage', location: 'Sector 6 School, Sanpada, Navi Mumbai',
    status: 'RESOLVED', daysAgo: 25, user: sneha },

  { title: '5 Consecutive Streetlights Not Working',
    description: 'The entire stretch from Sector 8 to Sector 9 has 5 non-working streetlights. Completely dark at night — unsafe for women walking home.',
    category: 'Streetlight', location: 'Sector 8-9 Road, Vashi, Navi Mumbai',
    status: 'SUBMITTED', daysAgo: 1, user: sneha },

  { title: 'Water Meter Leaking Constantly',
    description: 'The shared water meter for our building compound has been leaking for a week — approx 500 litres wasted per day. Urgent plumber needed.',
    category: 'Water Leakage', location: 'Sector 12, Vashi, Navi Mumbai',
    status: 'ASSIGNED', daysAgo: 6, user: sneha },
];

// ── Seed categories ───────────────────────────────────────────────────────────
async function seedCategories() {
  console.log('\n📂  Seeding categories...');
  const cats = [
    { id:'cat1', name:'Pothole',         description:'Road potholes & craters',   iconName:'Road' },
    { id:'cat2', name:'Garbage',         description:'Waste & dumping issues',     iconName:'DeleteSweep' },
    { id:'cat3', name:'Streetlight',     description:'Lighting problems',          iconName:'LightMode' },
    { id:'cat4', name:'Water Leakage',   description:'Pipe leaks & bursts',        iconName:'WaterDrop' },
    { id:'cat5', name:'Drainage',        description:'Blocked drains & flooding',  iconName:'InvertColors' },
    { id:'cat6', name:'Road Damage',     description:'Road surface damage',        iconName:'Construction' },
    { id:'cat7', name:'Public Property', description:'Damaged public assets',      iconName:'AccountBalance' },
    { id:'cat8', name:'Other',           description:'Other civic issues',         iconName:'MoreHoriz' },
  ];
  for (const c of cats) {
    await db.collection('categories').doc(c.id).set(c, { merge: true });
  }
  console.log('   ✅ 8 categories ready');
}

// ── Seed users (Firestore docs only) ─────────────────────────────────────────
async function seedUsers() {
  console.log('\n👤  Seeding user profiles in Firestore...');
  for (const u of USERS) {
    const doc = await db.collection('users').doc(u.uid).get();
    if (doc.exists) {
      console.log(`   ✓  (exists) ${u.name}`);
      continue;
    }
    await db.collection('users').doc(u.uid).set({
      name: u.name, email: u.email, phone: u.phone, role: u.role,
      avatarUrl: null, reportsSubmitted: 0, reportsResolved: 0,
      createdAt: ts(30)
    });
    console.log(`   ✅ ${u.name}`);
  }
}

const STOCK_IMAGES = {
  'Pothole': '/uploads/stock_pothole.jpg',
  'Garbage': '/uploads/stock_garbage.jpg',
  'Streetlight': '/uploads/stock_streetlight.jpg',
  'Water Leakage': '/uploads/stock_water.jpg',
  'Drainage': '/uploads/stock_drainage.jpg',
  'Road Damage': '/uploads/stock_road.jpg',
  'Public Property': '/uploads/stock_public_property.jpg',
  'Other': '/uploads/stock_traffic.jpg'
};

// ── Seed reports ──────────────────────────────────────────────────────────────
async function seedReports() {
  console.log('\n📋  Seeding reports...');
  const createdReports = [];

  for (const r of REPORTS) {
    const data = {
      title: r.title, description: r.description,
      category: r.category, location: r.location,
      date: dateStr(r.daysAgo), status: r.status,
      imageUrl: STOCK_IMAGES[r.category] || '/uploads/stock_pothole.jpg',
      userId: r.user.uid, userName: r.user.name,
      timelineSteps: makeTimeline(r.status, r.daysAgo),
      createdAt: ts(r.daysAgo)
    };
    const ref = await db.collection('reports').add(data);
    console.log(`   ✅ [${r.status.padEnd(12)}] ${r.title.substring(0, 42)}...`);
    createdReports.push({ id: ref.id, ...r });
  }
  return createdReports;
}

// ── Seed notifications ────────────────────────────────────────────────────────
async function seedNotifications(reports) {
  console.log('\n🔔  Seeding notifications...');
  const STATUS_MSG = {
    UNDER_REVIEW: { title: 'Report Under Review',  msg: t => `Your report "${t}" is now being reviewed.` },
    ASSIGNED:     { title: 'Report Assigned',       msg: t => `Your report "${t}" was assigned to the department.` },
    IN_PROGRESS:  { title: 'Work in Progress',      msg: t => `Work has started on your report "${t}".` },
    RESOLVED:     { title: '🎉 Issue Resolved!',    msg: t => `Your report "${t}" has been resolved. Thank you!` },
  };

  for (const r of reports) {
    // Submitted notification
    await db.collection('notifications').add({
      title: 'Report Submitted',
      message: `Your report "${r.title}" has been received. We'll keep you updated.`,
      time: 'Just now', isRead: r.daysAgo > 3, type: 'STATUS_UPDATE',
      reportId: r.id, userId: r.user.uid, createdAt: ts(r.daysAgo)
    });
    // Status notification (if past SUBMITTED)
    if (r.status !== 'SUBMITTED' && STATUS_MSG[r.status]) {
      const n = STATUS_MSG[r.status];
      await db.collection('notifications').add({
        title: n.title, message: n.msg(r.title),
        time: 'Just now', isRead: r.daysAgo > 2,
        type: r.status === 'RESOLVED' ? 'RESOLVED' : 'STATUS_UPDATE',
        reportId: r.id, userId: r.user.uid,
        createdAt: ts(Math.max(0, r.daysAgo - 2))
      });
    }
  }
  console.log('   ✅ Notifications created for all reports');
}

// ── Update user stats ─────────────────────────────────────────────────────────
async function updateStats(reports) {
  console.log('\n📊  Updating user stats...');
  for (const u of USERS) {
    const mine     = reports.filter(r => r.user.uid === u.uid);
    const resolved = mine.filter(r => r.status === 'RESOLVED').length;
    await db.collection('users').doc(u.uid).update({
      reportsSubmitted: mine.length, reportsResolved: resolved
    });
    console.log(`   ✅ ${u.name}: ${mine.length} submitted, ${resolved} resolved`);
  }
}

// ── Main ──────────────────────────────────────────────────────────────────────
async function main() {
  console.log('🌱  FixMyCity — Firebase Seed Script');
  console.log('=====================================');
  await seedCategories();
  await seedUsers();
  const reports = await seedReports();
  await seedNotifications(reports);
  await updateStats(reports);

  console.log('\n✨  Seeding complete! 11 reports across 4 users added to Firestore.');
  console.log('\n   Open the app → the community feed will show all reports.');
  console.log('   Reports show names: Rahul Sharma, Priya Mehta, Amit Patil, Sneha Joshi\n');
  process.exit(0);
}

main().catch(err => {
  console.error('\n❌  Seed failed:', err.message);
  process.exit(1);
});

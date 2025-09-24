import http from 'k6/http';
import { check, sleep } from 'k6';

// ---------------------------
// Config (override via env)
// ---------------------------
const CMD = __ENV.CMD_URL || 'http://localhost:8080';   // command-service (writes)
const QRY = __ENV.QRY_URL || 'http://localhost:8081';   // query-service (reads)
const CURRENCY = __ENV.CURRENCY || 'USD';
const SEED_AMOUNT = Number(__ENV.SEED_AMOUNT || 5000);
const TRANSFER_AMOUNT = Number(__ENV.TRANSFER_AMOUNT || 1200);
const READ_RETRIES = Number(__ENV.READ_RETRIES || 120);   // 120 * 0.5s = 60s
const READ_WAIT_MS = Number(__ENV.READ_WAIT_MS || 500);

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '60s',
  setupTimeout: __ENV.SETUP_TIMEOUT || '120s',
  thresholds: {
    'http_req_failed{retry:false}': ['rate<0.01'],      // exclude retry GETs from failure rate
    'http_req_duration{expected_response:true}': ['p(95)<400'],
    checks: ['rate>0.99'],
  },
};

// Helper: JSON POST with tags
function jpost(url, body, tags) {
  return http.post(url, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    tags: Object.assign({ retry: 'false' }, tags),
  });
}

// Helper: extract account id from various response shapes
function extractId(res, fallback) {
  try {
    const obj = res.json();
    if (!obj) return fallback;
    if (obj.accountId) return obj.accountId;
    if (obj.id) return obj.id;
    if (obj.data) {
      if (obj.data.accountId) return obj.data.accountId;
      if (obj.data.id) return obj.data.id;
    }
  } catch (e) { /* ignore parse errors */ }
  return fallback; // fall back to the name we sent
}

// Helper: wait until read returns 200 (handles eventual consistency); tries two routes
function readAccount(id, attempts = READ_RETRIES, waitMs = READ_WAIT_MS) {
  let res;
  for (let i = 0; i < attempts; i++) {
    // primary route
    res = http.get(`${QRY}/api/accounts/${id}`, { tags: { endpoint: '/api/accounts/{id}', method: 'GET', retry: 'true' } });
    if (res.status === 200) return res;
    // alternate route some apps expose
    res = http.get(`${QRY}/api/accounts/${id}/balance`, { tags: { endpoint: '/api/accounts/{id}/balance', method: 'GET', retry: 'true' } });
    if (res.status === 200) return res;
    if (i % 20 === 0) {
      console.log(`read wait: id=${id} attempt=${i} status1=${res && res.status}`);
    }
    sleep(waitMs / 1000);
  }
  return res; // last response
}

// ---------------------------
// One-time setup: create a stable pair of accounts and wait for projection
// ---------------------------
export function setup() {
  const uid = `pool_${Date.now()}`;
  const nameA = `k6_${uid}_A`;
  const nameB = `k6_${uid}_B`;

  // Open accounts (idempotent via idem keys)
  const openA = jpost(`${CMD}/api/accounts`, { accountId: nameA, currency: CURRENCY, idempotencyKey: `open-${nameA}` }, { endpoint: '/api/accounts', method: 'POST' });
  const openB = jpost(`${CMD}/api/accounts`, { accountId: nameB, currency: CURRENCY, idempotencyKey: `open-${nameB}` }, { endpoint: '/api/accounts', method: 'POST' });
  check(openA, { 'open A 200 (setup)': (r) => r.status === 200 });
  check(openB, { 'open B 200 (setup)': (r) => r.status === 200 });

  console.log(`openA body: ${openA.body}`);
  console.log(`openB body: ${openB.body}`);
  console.log(`Using IDs (pre-extract): A=${nameA} B=${nameB}`);

  // Use whatever id the server actually returns (fallback to the requested name)
  const A = extractId(openA, nameA);
  const B = extractId(openB, nameB);

  // Seed A so transfers can succeed
  const seed = jpost(`${CMD}/api/accounts/${A}/credit`, { amountCents: SEED_AMOUNT, currency: CURRENCY, idempotencyKey: `seed-${A}` }, { endpoint: '/api/accounts/{id}/credit', method: 'POST' });
  check(seed, { 'seed credit 200 (setup)': (r) => r.status === 200 });

  // Wait until projection materializes both accounts on query-service
  const rA = readAccount(A);
  const rB = readAccount(B);
  check(rA, { 'read A 200 (setup)': (r) => r.status === 200 });
  check(rB, { 'read B 200 (setup)': (r) => r.status === 200 });

  return { A, B };
}

// ---------------------------
// Load: transfers between the stable pair, then reads
// ---------------------------
export default function (data) {
  const { A, B } = data;
  const txid = `tx_${__VU}_${__ITER}_${Date.now()}`; // unique but NOT used as a tag

  const tr = jpost(
    `${CMD}/api/transfers`,
    {
      transferId: txid,
      fromAccount: A,       // adjust to { from: A, to: B } if your API expects that
      toAccount: B,
      amountCents: TRANSFER_AMOUNT,
      currency: CURRENCY,
      idempotencyKey: txid,
    },
    { endpoint: '/api/transfers', method: 'POST' }
  );
  check(tr, { 'transfer 200': (r) => r.status === 200 });

  const rA = readAccount(A);
  const rB = readAccount(B);
  check(rA, { 'read A 200': (r) => r.status === 200 });
  check(rB, { 'read B 200': (r) => r.status === 200 });

  sleep(0.1);
}
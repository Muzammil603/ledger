// Simple fetch helpers for the Vite proxy
export async function jfetch(url, opts = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) },
    ...opts,
  });
  const text = await res.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }

  if (res.ok) return data;

  // Soft-handle common idempotency/conflict cases
  if (res.status === 409) {
    return { __httpStatus: 409, data };
  }

  const msg = (data && (data.message || data.error || data.detail)) || `HTTP ${res.status}`;
  const err = new Error(msg);
  err.status = res.status;
  err.body = data;
  throw err;
}

// Like jfetch but returns {__notFound:true} for 404 instead of throwing
export async function jfetchAllow404(url, opts = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) },
    ...opts,
  });
  if (res.status === 404) {
    return { __notFound: true };
  }
  const text = await res.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (res.ok) return data;

  const msg = (data && (data.message || data.error || data.detail)) || `HTTP ${res.status}`;
  const err = new Error(msg);
  err.status = res.status;
  err.body = data;
  throw err;
}

// COMMAND (write) side — proxied via /cmd
export const cmd = {
  openAccount: (accountId, currency) =>
    jfetch(`/cmd/api/accounts`, {
      method: 'POST',
      body: JSON.stringify({ accountId, currency }),
    }),

  credit: (accountId, amountCents, currency, idempotencyKey) =>
    jfetch(`/cmd/api/accounts/${encodeURIComponent(accountId)}/credit`, {
      method: 'POST',
      body: JSON.stringify({ amountCents, currency, idempotencyKey }),
    }),

  debit: (accountId, amountCents, currency, idempotencyKey) =>
    jfetch(`/cmd/api/accounts/${encodeURIComponent(accountId)}/debit`, {
      method: 'POST',
      body: JSON.stringify({ amountCents, currency, idempotencyKey }),
    }),

  transfer: (transferId, fromAccount, toAccount, amountCents, currency, idempotencyKey) =>
    jfetch(`/cmd/api/transfers`, {
      method: 'POST',
      body: JSON.stringify({ transferId, fromAccount, toAccount, amountCents, currency, idempotencyKey }),
    }),
};

// QUERY (read) side — proxied via /qry
export const qry = {
  getAccount: (accountId) => jfetch(`/qry/api/accounts/${encodeURIComponent(accountId)}`),
  getTransfer: (transferId) => jfetch(`/qry/api/transfers/${encodeURIComponent(transferId)}`),
  getTransferMaybe: (transferId) => jfetchAllow404(`/qry/api/transfers/${encodeURIComponent(transferId)}`),
  listAccounts: (q) => jfetchAllow404(`/qry/api/accounts${q ? `?q=${encodeURIComponent(q)}` : ""}`),
  listTransfers: (limit = 50) => jfetchAllow404(`/qry/api/transfers?limit=${limit}`),
  listTransfersByAccount: (accountId, limit = 50) =>
    jfetchAllow404(`/qry/api/transfers?account=${encodeURIComponent(accountId)}&limit=${limit}`),
  getAdminStats: () => jfetchAllow404(`/qry/api/admin/stats`),
};
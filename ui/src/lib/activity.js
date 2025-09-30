const KEY = "ledgerx.activity";

export function pushActivity(evt) {
  const a = JSON.parse(localStorage.getItem(KEY) || "[]");
  a.unshift({ ...evt, ts: new Date().toISOString() });
  localStorage.setItem(KEY, JSON.stringify(a.slice(0, 200)));
}

export function getActivity() {
  return JSON.parse(localStorage.getItem(KEY) || "[]");
}

export function clearActivity() {
  localStorage.removeItem(KEY);
}
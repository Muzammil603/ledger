import { useEffect, useMemo, useState } from "react";
import { cmd, qry } from "../lib/api";
import Badge from "./common/Badge";
import Spinner from "./common/Spinner";
import { getActivity, pushActivity } from "../lib/activity";
import { useToast } from "./common/ToastProvider";
import { downloadCSV } from "../lib/csv";

const TERMINAL = new Set(["COMPLETED","FAILED","COMPENSATED"]);
const sleep = (ms) => new Promise(r => setTimeout(r, ms));

// SSE helper with auto-retry (exponential backoff + jitter)
function openSSE(
  url,
  { onRow, onHeartbeat, onMessage, onOpen, onError, min = 500, max = 15000 } = {}
) {
  let es, closed = false, delay = min, timer;

  const connect = () => {
    if (closed) return;
    es = new EventSource(url);

    es.onopen = () => { delay = min; onOpen && onOpen(); };

    if (onMessage) es.onmessage = (e) => onMessage(e);
    es.addEventListener("row", (e) => onRow && onRow(e));
    es.addEventListener("heartbeat", (e) => onHeartbeat && onHeartbeat(e));

    es.onerror = () => {
      onError && onError(new Error("SSE disconnected"));
      try { es.close(); } catch {}
      if (closed) return;
      const jitter = Math.floor(Math.random() * 0.3 * delay);
      const wait = Math.min(max, delay + jitter);
      timer = setTimeout(connect, wait);
      delay = Math.min(max, Math.floor(delay * 1.7));
    };
  };

  connect();

  return {
    close() { closed = true; clearTimeout(timer); es && es.close(); },
  };
}

export default function TransfersPanel(){
  const toast = useToast();
  const [transferId, setTransferId] = useState("tx_demo");
  const [from, setFrom] = useState("acc_src");
  const [to, setTo]     = useState("acc_dst");
  const [amount, setAmount] = useState(1200);
  const [currency, setCurrency] = useState("USD");
  const [idem, setIdem] = useState("tx_demo");
  const [result, setResult] = useState(null);
  const [statusRow, setStatusRow] = useState(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  // list (optional)
  const [list, setList] = useState(null);
  const [loadingList, setLoadingList] = useState(false);

  // extras
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [live, setLive] = useState(false);
  const [liveCtl, setLiveCtl] = useState(null);

  function genKey(){ return `${transferId}-${Date.now()}`; }

  function upsertTransfer(prev, row){
    const arr = (prev && !prev.__notFound) ? [...prev] : [];
    const i = arr.findIndex(t => t.transfer_id === row.transfer_id);
    if (i >= 0) arr[i] = row; else arr.unshift(row);
    return arr.slice(0, 200);
  }

  async function waitForTerminalStatus(maxMs = 8000, baseMs = 250){
    let delay = baseMs;
    const start = Date.now();
    while (Date.now() - start < maxMs){
      const s = await qry.getTransferMaybe(transferId);
      if (s && !s.__notFound) {
        setStatusRow(s);
        if (TERMINAL.has(s.status)) { setErr(""); return true; }
      }
      await sleep(delay);
      delay = Math.min(Math.floor(delay * 1.7), 1200);
    }
    setErr("Still processing… click Refresh Status in a moment.");
    return false;
  }

  async function submit(){
    setBusy(true); setErr("");
    const key = (idem && idem.trim()) ? idem.trim() : genKey();
    if (!idem || idem.trim() === "") setIdem(key);

    try {
      const r = await cmd.transfer(transferId, from, to, Number(amount), currency, key);
      setResult(r);
      pushActivity({ type: "transfer", transferId, from, to, amountCents: Number(amount), currency, idem: key });
      toast.addToast(`Transfer ${transferId} started`, { type: "success" });
      const done = await waitForTerminalStatus();
      if (!done) toast.addToast("Still processing… click Refresh Status in a moment.", { type: "info" });
      else if (statusRow?.status) {
        toast.addToast(`Transfer ${transferId} → ${statusRow.status}`, { type: statusRow.status === "COMPLETED" ? "success" : "error" });
      }
      await loadList();
    } catch(e){
      const msg = e?.message || String(e);
      setErr(msg.includes("Idempotency key reuse")
        ? "That idempotency key was already used with different fields. Pick a New Key or keep the fields identical."
        : msg);
      toast.addToast(msg, { type: "error" });
    } finally {
      setBusy(false);
    }
  }

  async function refresh(){
    setErr("");
    try {
      const s = await qry.getTransfer(transferId);
      setStatusRow(s);
    } catch(e){
      setStatusRow(null);
      const msg = (e.message || String(e));
      setErr(msg.toLowerCase().includes("not found")
        ? "Transfer not found yet; the read model may still be catching up. Try again in a second."
        : msg);
    }
  }

  async function loadList(){
    setLoadingList(true);
    try {
      const data = await qry.listTransfers(50);
      setList(data && !data.__notFound ? data : []);
    } catch {
      setList([]);
    } finally {
      setLoadingList(false);
    }
  }

  useEffect(() => { loadList(); }, []);

  // Live SSE hookup with auto-retry
  useEffect(() => {
    if (!live) {
      if (liveCtl) { liveCtl.close(); setLiveCtl(null); }
      return;
    }
    const ctl = openSSE("/qry/api/stream/transfers", {
      onRow: (ev) => {
        try {
          const data = JSON.parse(ev.data);
          if (!data || !data.transfer_id) return;
          setList(prev => upsertTransfer(prev, data));
          setStatusRow(prev => (prev && prev.transfer_id === data.transfer_id) ? data : prev);
        } catch {}
      },
      onError: () => {
        toast.addToast("Live stream dropped. Reconnecting…", { type: "error" });
      },
    });
    setLiveCtl(ctl);
    return () => { ctl.close(); setLiveCtl(null); };
  }, [live, toast]);

  // Fallback: session history if backend list is unavailable
  const sessionTransfers = useMemo(() => {
    const acts = getActivity().filter(a => a.type === "transfer");
    const seen = new Set(); const items = [];
    for (const a of acts) {
      if (!seen.has(a.transferId)) { seen.add(a.transferId); items.push(a); }
      if (items.length >= 10) break;
    }
    return items;
  }, []);

  const rows = list && list.length > 0 ? list : null;
  const filteredRows = rows
    ? rows.filter(t => statusFilter === "ALL" ? true : t.status === statusFilter)
    : null;

  return (
    <div className="space-y-6">

      {/* Action card */}
      <div className="card space-y-4">
        <h2 className="text-xl font-semibold">Transfers</h2>

        <div className="grid grid-cols-1 md:grid-cols-6 gap-3">
          <div>
            <label className="text-sm">Transfer ID</label>
            <input className="input" value={transferId} onChange={e=>setTransferId(e.target.value)} />
          </div>
          <div>
            <label className="text-sm">From Account</label>
            <input className="input" value={from} onChange={e=>setFrom(e.target.value)} />
          </div>
          <div>
            <label className="text-sm">To Account</label>
            <input className="input" value={to} onChange={e=>setTo(e.target.value)} />
          </div>
          <div>
            <label className="text-sm">Amount (cents)</label>
            <input className="input" type="number" value={amount} onChange={e=>setAmount(e.target.value)} />
          </div>
          <div>
            <label className="text-sm">Currency</label>
            <input className="input" value={currency} onChange={e=>setCurrency(e.target.value)} />
          </div>
          <div>
            <label className="text-sm">Idempotency Key</label>
            <input className="input" value={idem} onChange={e=>setIdem(e.target.value)} placeholder="blank = auto" />
            <div className="flex gap-2 mt-1">
              <button type="button" className="btn" onClick={() => setIdem(genKey())}>New Key</button>
              <button type="button" className="btn" onClick={() => setIdem(transferId)}>Use Transfer ID</button>
            </div>
          </div>
        </div>

        <div className="flex gap-2 items-center">
          <button disabled={busy} onClick={submit} className="btn btn-primary">{busy ? "Starting…" : "Start Transfer"}</button>
          <button disabled={busy} onClick={refresh} className="btn">Refresh Status</button>
          <label className="text-sm flex items-center gap-2 ml-2">
            <input type="checkbox" checked={live} onChange={e=>setLive(e.target.checked)} />
            Live
          </label>
        </div>

        {err && <div className="text-red-600 text-sm">{err}</div>}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <div className="font-medium mb-1">Command Response</div>
            <pre className="bg-gray-50 p-3 rounded-xl text-xs overflow-auto">{result ? JSON.stringify(result, null, 2) : "—"}</pre>
          </div>
          <div>
            <div className="font-medium mb-1">Transfer Status (read model)</div>
            <pre className="bg-gray-50 p-3 rounded-xl text-xs overflow-auto">{statusRow ? JSON.stringify(statusRow, null, 2) : "—"}</pre>
          </div>
        </div>
      </div>

      {/* List card */}
      <div className="card space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold">Transfers (latest)</h3>
          <div className="flex items-center gap-2">
            <select className="input" value={statusFilter} onChange={e=>setStatusFilter(e.target.value)}>
              <option value="ALL">All</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
              <option value="COMPENSATED">Compensated</option>
              <option value="PENDING">Pending</option>
              <option value="PROCESSING">Processing</option>
            </select>
            <button className="btn" onClick={()=>{
              const data = filteredRows ?? sessionTransfers;
              if (!data || data.length === 0) return toast.addToast("No rows to export", { type: "info" });
              const cols = rows
                ? [
                    { key: "transfer_id", label: "Transfer" },
                    { key: "from_account", label: "From" },
                    { key: "to_account", label: "To" },
                    { key: "amount_cents", label: "Amount (cents)" },
                    { key: "currency", label: "Currency" },
                    { key: "status", label: "Status" },
                    { key: "updated_at", label: "Updated" },
                  ]
                : [
                    { key: "transferId", label: "Transfer" },
                    { key: "from", label: "From" },
                    { key: "to", label: "To" },
                    { key: "amountCents", label: "Amount (cents)" },
                    { key: "currency", label: "Currency" },
                    { key: "ts", label: "When" },
                  ];
              downloadCSV("transfers.csv", data, cols);
              toast.addToast("Exported transfers.csv", { type: "success" });
            }}>Export CSV</button>
            <button className="btn" onClick={loadList} disabled={loadingList}>
              {loadingList ? <>Refreshing…</> : <>Refresh</>}
            </button>
          </div>
        </div>

        {loadingList && <div className="text-sm text-gray-500"><Spinner /> Loading…</div>}

        {rows === null && (
          <div className="text-sm text-gray-500">
            Your backend may not expose <code>/qry/api/transfers</code> yet. Showing session history instead.
          </div>
        )}

        {filteredRows && filteredRows.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500">
                  <th className="py-2">Transfer</th>
                  <th>From</th><th>To</th><th>Amount</th><th>Status</th><th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {filteredRows.map((t) => (
                  <tr key={t.transfer_id} className="border-t">
                    <td className="py-2">{t.transfer_id}</td>
                    <td>{t.from_account}</td>
                    <td>{t.to_account}</td>
                    <td>{t.amount_cents} {t.currency}</td>
                    <td>
                      <Badge tone={
                        t.status === "COMPLETED" ? "green" :
                        t.status === "FAILED" ? "red" :
                        t.status === "COMPENSATED" ? "blue" : "yellow"
                      }>
                        {t.status}
                      </Badge>
                    </td>
                    <td className="text-gray-500">{t.updated_at}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {rows === null && sessionTransfers.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500">
                  <th className="py-2">Transfer</th>
                  <th>From</th><th>To</th><th>Amount</th><th>When</th>
                </tr>
              </thead>
              <tbody>
                {sessionTransfers.map((s) => (
                  <tr key={s.ts + s.transferId} className="border-t">
                    <td className="py-2">{s.transferId}</td>
                    <td>{s.from}</td>
                    <td>{s.to}</td>
                    <td>{s.amountCents} {s.currency}</td>
                    <td className="text-gray-500">{new Date(s.ts).toLocaleTimeString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {rows === null && sessionTransfers.length === 0 && (
          <div className="text-sm text-gray-500">
            No transfers in this session yet. Create one above.
          </div>
        )}
      </div>
    </div>
  );
}
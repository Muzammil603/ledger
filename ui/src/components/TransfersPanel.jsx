import { useState } from "react";
import { cmd, qry } from "../lib/api";

export default function TransfersPanel(){
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

  async function submit(){
    setBusy(true); setErr("");
    try {
      const r = await cmd.transfer(transferId, from, to, Number(amount), currency, idem);
      setResult(r);
      await refresh();
    } catch(e){ setErr(e.message); }
    finally { setBusy(false); }
  }

  async function refresh(){
    setErr("");
    try {
      const s = await qry.getTransfer(transferId);
      setStatusRow(s);
    } catch(e){ setStatusRow(null); setErr(e.message); }
  }

  return (
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
          <input className="input" value={idem} onChange={e=>setIdem(e.target.value)} />
        </div>
      </div>

      <div className="flex gap-2">
        <button disabled={busy} onClick={submit} className="btn btn-primary">Start Transfer</button>
        <button disabled={busy} onClick={refresh} className="btn">Refresh Status</button>
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
  );
}
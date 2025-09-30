import { useState } from "react";
import { cmd, qry } from "../lib/api";

export default function AccountsPanel(){
  const [accountId, setAccountId] = useState("acc_demo");
  const [currency, setCurrency]   = useState("USD");
  const [amount, setAmount]       = useState(500);
  const [idem, setIdem]           = useState("idem-1");
  const [acct, setAcct]           = useState(null);
  const [busy, setBusy]           = useState(false);
  const [err, setErr]             = useState("");

  async function open() {
  setBusy(true); setErr("");
  try {
    const r = await cmd.openAccount(accountId, currency);
    if (r && r.__httpStatus === 409) {
      setErr("Account already exists — showing current state.");
    }
    await refresh();
  } catch (e) {
    setErr(e.message);
  } finally {
    setBusy(false);
  }
}

  async function credit() {
    setBusy(true); setErr("");
    try {
      await cmd.credit(accountId, Number(amount), currency, idem || `k-${Date.now()}`);
      await refresh();
    } catch(e){ setErr(e.message); }
    finally { setBusy(false); }
  }

  async function debit() {
    setBusy(true); setErr("");
    try {
      await cmd.debit(accountId, Number(amount), currency, idem || `k-${Date.now()}`);
      await refresh();
    } catch(e){ setErr(e.message); }
    finally { setBusy(false); }
  }

  async function refresh() {
    setErr("");
    try {
      const data = await qry.getAccount(accountId);
      setAcct(data);
    } catch(e){ setAcct(null); setErr(e.message); }
  }

  return (
    <div className="card space-y-4">
      <h2 className="text-xl font-semibold">Accounts</h2>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
        <div>
          <label className="text-sm">Account ID</label>
          <input className="input" value={accountId} onChange={e=>setAccountId(e.target.value)} />
        </div>
        <div>
          <label className="text-sm">Currency</label>
          <input className="input" value={currency} onChange={e=>setCurrency(e.target.value)} />
        </div>
        <div>
          <label className="text-sm">Amount (cents)</label>
          <input className="input" type="number" value={amount} onChange={e=>setAmount(e.target.value)} />
        </div>
        <div>
          <label className="text-sm">Idempotency Key</label>
          <input className="input" value={idem} onChange={e=>setIdem(e.target.value)} placeholder="auto if blank" />
        </div>
      </div>

      <div className="flex gap-2">
        <button disabled={busy} onClick={open}   className="btn btn-primary">Open</button>
        <button disabled={busy} onClick={credit} className="btn">Credit</button>
        <button disabled={busy} onClick={debit}  className="btn">Debit</button>
        <button disabled={busy} onClick={refresh} className="btn">Refresh</button>
      </div>

      {err && <div className="text-red-600 text-sm">{err}</div>}

      <div className="text-sm">
        {acct ? (
          <div className="mt-2">
            <div><span className="font-medium">Account:</span> {acct.account_id}</div>
            <div><span className="font-medium">Currency:</span> {acct.currency}</div>
            <div><span className="font-medium">Balance (cents):</span> {acct.balance_cents}</div>
            <div><span className="font-medium">Updated:</span> {acct.updated_at}</div>
          </div>
        ) : (
          <div className="text-gray-500">No account loaded yet.</div>
        )}
      </div>
    </div>
  );
}
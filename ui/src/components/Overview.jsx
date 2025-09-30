import { useEffect, useState } from "react";
import { qry } from "../lib/api";
import Spinner from "./common/Spinner";
import Badge from "./common/Badge";

export default function Overview(){
  const [loading, setLoading] = useState(false);
  const [accounts, setAccounts] = useState(null);
  const [transfers, setTransfers] = useState(null);
  const [auto, setAuto] = useState(false);
  const [ms, setMs] = useState(5000);
  const [stats, setStats] = useState(null);

  async function load(){
    setLoading(true);
    try {
      const [a, t, s] = await Promise.all([
        qry.listAccounts().catch(()=>({__notFound:true})),
        qry.listTransfers(20).catch(()=>({__notFound:true})),
        qry.getAdminStats().catch(()=>({__notFound:true})),
     ]);
      setAccounts(a.__notFound ? null : a);
      setTransfers(t.__notFound ? null : t);
      setStats(s.__notFound ? null : s);
    } finally {
      setLoading(false);
    }
  }

  useEffect(()=>{ load(); }, []);

  // Auto-refresh interval
  useEffect(() => {
    if (!auto) return;
    const id = setInterval(load, ms);
    return () => clearInterval(id);
  }, [auto, ms]);

  const totalBalance = accounts ? accounts.reduce((s,a)=>s + (a.balance_cents||0), 0) : null;

  return (
    <div className="space-y-6">
      <div className="card">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-xl font-semibold">Overview</h2>
          <div className="flex items-center gap-2">
            <label className="text-sm flex items-center gap-2">
              <input type="checkbox" checked={auto} onChange={e=>setAuto(e.target.checked)} />
              Auto-refresh
            </label>
            <select className="input w-28" value={ms} onChange={e=>setMs(Number(e.target.value))} disabled={!auto}>
              <option value={3000}>3s</option>
              <option value={5000}>5s</option>
              <option value={10000}>10s</option>
            </select>
            <button className="btn" onClick={load} disabled={loading}>
              {loading ? "Refreshing…" : "Refresh"}
            </button>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="font-medium mb-2">Projection Health</div>
        {!stats ? (
          <div className="text-sm text-gray-500"><Spinner /> Loading…</div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-sm">
            <div>
              <span className="text-gray-500">Accounts:</span>{" "}
              <span className="font-medium">{stats.accounts_count}</span>
            </div>
            <div>
              <span className="text-gray-500">Transfers:</span>{" "}
              <span className="font-medium">{stats.transfers_count}</span>
            </div>
            <div>
              <span className="text-gray-500">Last Account Update:</span>{" "}
              <span className="font-medium">{stats.last_account_update ?? "—"}</span>
            </div>
            <div>
              <span className="text-gray-500">Last Transfer Update:</span>{" "}
              <span className="font-medium">{stats.last_transfer_update ?? "—"}</span>
            </div>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="card">
          <div className="text-sm text-gray-500">Accounts</div>
          <div className="text-2xl font-bold">{accounts ? accounts.length : "—"}</div>
        </div>
        <div className="card">
          <div className="text-sm text-gray-500">Total Balance (cents)</div>
          <div className="text-2xl font-bold">{totalBalance ?? "—"}</div>
        </div>
        <div className="card">
          <div className="text-sm text-gray-500">Recent Transfers</div>
          <div className="text-2xl font-bold">{transfers ? transfers.length : "—"}</div>
        </div>
      </div>

      <div className="card space-y-3">
        <div className="font-medium">Latest Transfers</div>
        {!transfers && <div className="text-sm text-gray-500"><Spinner /> Waiting for `/qry/api/transfers`…</div>}
        {transfers && transfers.length === 0 && <div className="text-sm text-gray-500">No transfers yet.</div>}
        {transfers && transfers.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500">
                  <th className="py-2">Transfer</th><th>From</th><th>To</th><th>Amount</th><th>Status</th><th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {transfers.map(t=>(
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
                      }>{t.status}</Badge>
                    </td>
                    <td className="text-gray-500">{t.updated_at}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
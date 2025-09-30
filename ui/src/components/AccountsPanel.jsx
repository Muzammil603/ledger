import { useEffect, useMemo, useState } from "react";
import { cmd, qry } from "../lib/api";
import Badge from "./common/Badge";
import Spinner from "./common/Spinner";
import { pushActivity } from "../lib/activity";
import { useToast } from "./common/ToastProvider";
import { downloadCSV } from "../lib/csv";
import AccountDrilldown from "./AccountDrilldown";

export default function AccountsPanel(){
  // form state
  const toast = useToast();
  const [accountId, setAccountId] = useState("acc_demo");
  const [currency, setCurrency]   = useState("USD");
  const [amount, setAmount]       = useState(500);
  const [idem, setIdem]           = useState("idem-1");
  const [drillId, setDrillId] = useState(null);

  // data
  const [acct, setAcct]           = useState(null);
  const [busy, setBusy]           = useState(false);
  const [err, setErr]             = useState("");

  // list (optional if backend supports it)
  const [list, setList]           = useState(null);
  const [loadingList, setLoadingList] = useState(false);
  const [search, setSearch]       = useState("");

  async function refresh() {
    setErr("");
    try {
      const data = await qry.getAccount(accountId);
      setAcct(data);
    } catch(e){ setAcct(null); setErr(e.message); }
  }

  async function open() {
    setBusy(true); setErr("");
    try {
      await cmd.openAccount(accountId, currency);
      pushActivity({ type: "openAccount", accountId, currency });
      toast.addToast(`Account ${accountId} opened (${currency})`, { type: "success" });
      await refresh();
      await loadList();
    } catch(e){ setErr(e.message); toast.addToast(e.message || "Open failed", { type: "error" }); }
    finally { setBusy(false); }
  }

  async function credit() {
    setBusy(true); setErr("");
    try {
      await cmd.credit(accountId, Number(amount), currency, idem || `k-${Date.now()}`);
      pushActivity({ type: "credit", accountId, amountCents: Number(amount), currency, idem });
      toast.addToast(`Credited ${amount} ${currency} to ${accountId}`, { type: "success" });
      await refresh();
      await loadList();
    } catch(e){ setErr(e.message); toast.addToast(e.message || "Credit failed", { type: "error" }); }
    finally { setBusy(false); }
  }

  async function debit() {
    setBusy(true); setErr("");
    try {
      await cmd.debit(accountId, Number(amount), currency, idem || `k-${Date.now()}`);
      pushActivity({ type: "debit", accountId, amountCents: Number(amount), currency, idem });
      toast.addToast(`Debited ${amount} ${currency} from ${accountId}`, { type: "success" });
      await refresh();
      await loadList();
    } catch(e){ setErr(e.message); toast.addToast(e.message || "Debit failed", { type: "error" }); }
    finally { setBusy(false); }
  }

  async function loadList(){
    setLoadingList(true);
    try {
      const data = await qry.listAccounts(search);
      // If backend doesn’t support list yet, data will be {__notFound:true}
      setList(data && !data.__notFound ? data : []);
    } catch {
      setList([]); // silent; show note below
    } finally {
      setLoadingList(false);
    }
  }

  useEffect(() => { loadList(); /* attempt once on mount */ }, []);
  useEffect(() => { const t = setTimeout(loadList, 300); return () => clearTimeout(t); }, [search]);

  const filtered = useMemo(() => list || [], [list]);

  return (
    <div className="space-y-6">

      {/* Actions card */}
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
            <input className="input" value={idem} onChange={e=>setIdem(e.target.value)} placeholder="idempotency key" />
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
          <div className="font-medium mb-1">Selected Account</div>
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
      {drillId && <AccountDrilldown accountId={drillId} onClose={() => setDrillId(null)} />}

      {/* List card */}
      <div className="card space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold">Accounts (latest)</h3>
          <div className="flex items-center gap-2">
            <input className="input w-48" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search id…" />
            <button
              className="btn"
              onClick={()=>{
                if (!list || list.length === 0) { toast.addToast("No rows to export", { type: "info" }); return; }
                downloadCSV("accounts.csv", list, [
                  { key: "account_id", label: "Account" },
                  { key: "currency", label: "Currency" },
                  { key: "balance_cents", label: "Balance (cents)" },
                  { key: "updated_at", label: "Updated" },
                ]);
                toast.addToast("Exported accounts.csv", { type: "success" });
              }}
            >
              Export CSV
            </button>
            <button className="btn" onClick={loadList} disabled={loadingList}>
              {loadingList ? <>Refreshing…</> : <>Refresh</>}
            </button>
          </div>
        </div>

        {loadingList && <div className="text-sm text-gray-500"><Spinner /> Loading…</div>}

        {list && list.length === 0 && (
          <div className="text-sm text-gray-500">
            Your backend may not expose <code>/qry/api/accounts</code> yet. The actions above still work; history
            will appear once the endpoint is implemented.
          </div>
        )}

        {filtered && filtered.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500">
                  <th className="py-2">Account</th>
                  <th>Currency</th>
                  <th>Balance (cents)</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((a) => (
                  <tr
                    key={a.account_id}
                    className="border-t hover:bg-gray-50 cursor-pointer"
                    onClick={() => setDrillId(a.account_id)}
                  >
                    <td className="py-2">{a.account_id}</td>
                    <td>{a.currency}</td>
                    <td>{a.balance_cents}</td>
                    <td className="text-gray-500">{a.updated_at}</td>
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
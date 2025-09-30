import { useEffect, useState } from "react";
import { qry } from "../lib/api";
import Spinner from "./common/Spinner";
import Badge from "./common/Badge";

export default function AccountDrilldown({ accountId, onClose }){
  const [rows, setRows] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    (async () => {
      setLoading(true);
      try {
        const data = await qry.listTransfersByAccount(accountId, 50);
        if (isMounted) setRows(data && !data.__notFound ? data : []);
      } finally {
        if (isMounted) setLoading(false);
      }
    })();
    return () => { isMounted = false; };
  }, [accountId]);

  return (
    <div className="fixed inset-0 bg-black/20 backdrop-blur-sm flex justify-end z-40">
      <div className="h-full w-full max-w-xl bg-white shadow-xl p-4 overflow-y-auto">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-lg font-semibold">Activity for {accountId}</h3>
          <button className="btn" onClick={onClose}>Close</button>
        </div>

        {loading && <div className="text-sm text-gray-500"><Spinner /> Loading…</div>}
        {!loading && rows && rows.length === 0 && (
          <div className="text-sm text-gray-500">No recent transfers for this account.</div>
        )}

        {!loading && rows && rows.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500">
                  <th className="py-2">Transfer</th>
                  <th>Dir</th>
                  <th>Counterparty</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(t => {
                  const outgoing = t.from_account === accountId;
                  const cp = outgoing ? t.to_account : t.from_account;
                  return (
                    <tr key={t.transfer_id} className="border-t">
                      <td className="py-2">{t.transfer_id}</td>
                      <td>{outgoing ? "→ Out" : "← In"}</td>
                      <td>{cp}</td>
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
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
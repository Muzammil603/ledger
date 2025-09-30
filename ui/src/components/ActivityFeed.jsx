import { useEffect, useState } from "react";
import { clearActivity, getActivity } from "../lib/activity";
import Badge from "./common/Badge";

function Row({ a }) {
  return (
    <tr className="border-t">
      <td className="py-2 text-gray-500">{new Date(a.ts).toLocaleString()}</td>
      <td>
        <Badge tone={a.type === "transfer" ? "blue" : a.type === "credit" ? "green" : a.type === "debit" ? "yellow" : "gray"}>
          {a.type}
        </Badge>
      </td>
      <td className="text-xs">
        <pre className="bg-gray-50 p-2 rounded-md overflow-auto">{JSON.stringify(a, null, 2)}</pre>
      </td>
    </tr>
  );
}

export default function ActivityFeed(){
  const [items, setItems] = useState([]);

  function load(){ setItems(getActivity()); }

  useEffect(() => { load(); }, []);

  return (
    <div className="card space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">Activity (this browser)</h2>
        <div className="flex gap-2">
          <button className="btn" onClick={load}>Refresh</button>
          <button className="btn" onClick={() => { clearActivity(); load(); }}>Clear</button>
        </div>
      </div>

      {items.length === 0 ? (
        <div className="text-sm text-gray-500">Nothing yet. As you open accounts, credit/debit, and transfer funds, entries will appear here.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500">
                <th className="py-2 w-48">When</th>
                <th className="w-32">Type</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {items.map((a, i) => <Row key={a.ts + i} a={a} />)}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
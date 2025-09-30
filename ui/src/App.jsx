import { useState } from "react";
import Overview from "./components/Overview";
import AccountsPanel from "./components/AccountsPanel";
import TransfersPanel from "./components/TransfersPanel";
import ActivityFeed from "./components/ActivityFeed";
import ToastProvider from "./components/common/ToastProvider";

const TABS = [
  { key: "overview",  label: "Overview",  comp: <Overview /> },
  { key: "accounts",  label: "Accounts",  comp: <AccountsPanel /> },
  { key: "transfers", label: "Transfers", comp: <TransfersPanel /> },
  { key: "activity",  label: "Activity",  comp: <ActivityFeed /> },
];

export default function App() {
  const [tab, setTab] = useState("overview");

  return (
    <ToastProvider>
      <div className="min-h-screen bg-white text-gray-900">
        <header className="border-b bg-white/60 backdrop-blur">
          <div className="max-w-6xl mx-auto px-4 py-4 flex items-center justify-between">
            <h1 className="text-2xl font-bold">LedgerX Dashboard</h1>
            <nav className="flex gap-2">
              {TABS.map(t => (
                <button
                  key={t.key}
                  onClick={()=>setTab(t.key)}
                  className={`px-3 py-1.5 rounded-xl text-sm border ${tab===t.key ? "bg-black text-white" : "hover:bg-gray-50"}`}
                >
                  {t.label}
                </button>
              ))}
            </nav>
          </div>
        </header>

        <main className="max-w-6xl mx-auto px-4 py-6">
          {TABS.find(t=>t.key===tab)?.comp}
        </main>
      </div>
    </ToastProvider>
  );
}
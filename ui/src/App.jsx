import AccountsPanel from "./components/AccountsPanel";
import TransfersPanel from "./components/TransfersPanel";

export default function App() {
  return (
    <div className="min-h-screen bg-white text-gray-900">
      <header className="border-b">
        <div className="max-w-5xl mx-auto px-4 py-4 flex items-center justify-between">
          <h1 className="text-2xl font-bold">LedgerX Dashboard</h1>
          <div className="text-sm text-gray-600">local dev • Vite + Tailwind</div>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-6 space-y-6">
        <AccountsPanel />
        <TransfersPanel />
      </main>
    </div>
  );
}

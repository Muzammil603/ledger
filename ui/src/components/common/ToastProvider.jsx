import { createContext, useCallback, useContext, useMemo, useState } from "react";

const ToastCtx = createContext(null);
export function useToast(){ return useContext(ToastCtx); }

export default function ToastProvider({ children }){
  const [toasts, setToasts] = useState([]);

  const remove = useCallback((id) => {
    setToasts(t => t.filter(x => x.id !== id));
  }, []);

  const addToast = useCallback((msg, { type="info", ttl=3500 } = {}) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setToasts(t => [{ id, msg, type }, ...t]);
    if (ttl > 0) setTimeout(() => remove(id), ttl);
  }, [remove]);

  const value = useMemo(()=>({ addToast, remove }), [addToast, remove]);

  const tone = (type) => (
    type === "success" ? "bg-green-50 border-green-300 text-green-900" :
    type === "error"   ? "bg-red-50 border-red-300 text-red-900" :
                         "bg-gray-50 border-gray-300 text-gray-900"
  );

  return (
    <ToastCtx.Provider value={value}>
      {children}
      <div className="fixed z-50 top-4 right-4 space-y-2 w-[min(92vw,360px)]">
        {toasts.map(t => (
          <div key={t.id}
               className={`border rounded-xl px-3 py-2 shadow ${tone(t.type)} flex items-start gap-2`}>
            <div className="text-sm leading-snug">{t.msg}</div>
            <button
              className="ml-auto text-xs underline opacity-70 hover:opacity-100"
              onClick={() => remove(t.id)}
            >
              Dismiss
            </button>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}
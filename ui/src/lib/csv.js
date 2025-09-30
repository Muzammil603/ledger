function csvEscape(v){
  if (v == null) return "";
  const s = String(v);
  if (/[",\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

export function downloadCSV(filename, rows, columns){
  // columns: [{ key: "account_id", label: "Account" }, ...]
  const header = columns.map(c => csvEscape(c.label ?? c.key)).join(",");
  const lines = rows.map(r => columns.map(c => csvEscape(r[c.key])).join(","));
  const csv = [header, ...lines].join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(()=>URL.revokeObjectURL(a.href), 0);
}
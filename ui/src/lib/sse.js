export function openSSE(url, { onMessage, onOpen, onError, min = 500, max = 15000 } = {}) {
  let es, closed = false, delay = min, timer;

  const connect = () => {
    if (closed) return;
    es = new EventSource(url);
    es.onopen = () => { delay = min; onOpen && onOpen(); };
    es.onmessage = (e) => onMessage && onMessage(e);
    es.onerror = () => {
      onError && onError(new Error("SSE disconnected"));
      es.close();
      if (closed) return;
      const jitter = Math.floor(Math.random() * 0.3 * delay);
      const wait = Math.min(max, delay + jitter);
      timer = setTimeout(connect, wait);
      delay = Math.min(max, Math.floor(delay * 1.7));
    };
  };

  connect();

  return {
    close() { closed = true; clearTimeout(timer); es && es.close(); },
  };
}
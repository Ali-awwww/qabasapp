import { useEffect, useRef, useState } from "react";
import { Send } from "lucide-react";
import { ASSISTANT_FALLBACK, ASSISTANT_GREETING, ASSISTANT_KB } from "../data/demo";
import { DemoBadge } from "../components/ui";

interface Msg {
  from: "user" | "bot";
  text: string;
}

const SUGGESTIONS = ["مواقيت الصلاة", "اتجاه القبلة", "أذكار الصباح", "ما حكم الزكاة؟", "أريد سورة الفاتحة"];

function answer(q: string): string {
  for (const item of ASSISTANT_KB) {
    if (item.keys.some((k) => q.includes(k))) return item.answer;
  }
  return ASSISTANT_FALLBACK;
}

export default function Assistant() {
  const [msgs, setMsgs] = useState<Msg[]>([{ from: "bot", text: ASSISTANT_GREETING }]);
  const [input, setInput] = useState("");
  const [typing, setTyping] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [msgs, typing]);

  const send = (text: string) => {
    const q = text.trim();
    if (!q || typing) return;
    setMsgs((m) => [...m, { from: "user", text: q }]);
    setInput("");
    setTyping(true);
    setTimeout(() => {
      setMsgs((m) => [...m, { from: "bot", text: answer(q) }]);
      setTyping(false);
    }, 700);
  };

  return (
    <div className="view chat">
      <div className="center">
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", color: "var(--text)", margin: "4px 0 0" }}>
          المساعد الذكي
        </h1>
        <p className="muted" style={{ margin: "4px 0 0" }}>إجابات محلية من قاعدة معرفة تجريبية <DemoBadge /></p>
      </div>

      <div className="chat-list" aria-live="polite">
        {msgs.map((m, i) => (
          <div key={i} className={`bubble ${m.from === "user" ? "me" : "bot"}`}>{m.text}</div>
        ))}
        {typing && (
          <div className="bubble bot typing">
            <span />
            <span />
            <span />
          </div>
        )}
        <div ref={endRef} />
      </div>

      <div className="chips">
        {SUGGESTIONS.map((s) => (
          <button key={s} className="chip" onClick={() => send(s)} disabled={typing}>{s}</button>
        ))}
      </div>

      <form
        className="chat-input"
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
      >
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="اكتب سؤالك…"
          aria-label="اكتب سؤالك"
          disabled={typing}
        />
        <button type="submit" className="send" aria-label="إرسال" disabled={typing || !input.trim()}>
          <Send size={20} />
        </button>
      </form>
      <p className="muted center" style={{ margin: "4px 0 0" }}>
        المساعد في هذه النسخة يجيب من قاعدة معرفة محلية فقط — النموذج الذكي الحي يأتي بعد ربط الخلفية.
      </p>
    </div>
  );
}

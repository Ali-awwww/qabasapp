import { useEffect, useMemo, useState } from "react";
import { BookOpen, Compass, Library, Sparkles, Settings } from "lucide-react";
import { DEMO_CITY, DEMO_HIJRI, HADITHS, PRAYER_TIMES_DEMO } from "../data/demo";
import type { Go } from "../types";
import { DemoBadge, SectionTitle, Chevron } from "../components/ui";

function toMinutes(t: string) {
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
}

function nextPrayer(now: Date) {
  const mins = now.getHours() * 60 + now.getMinutes();
  const prayers = PRAYER_TIMES_DEMO.filter((p) => p.name !== "الشروق");
  for (const p of prayers) {
    if (toMinutes(p.time) > mins) return { name: p.name, time: p.time, tomorrow: false };
  }
  return { name: "الفجر", time: PRAYER_TIMES_DEMO[0].time, tomorrow: true };
}

function countdown(target: string, tomorrow: boolean) {
  const now = new Date();
  const [h, m] = target.split(":").map(Number);
  const t = new Date(now);
  t.setHours(h, m, 0, 0);
  if (tomorrow) t.setDate(t.getDate() + 1);
  const diff = Math.max(0, t.getTime() - now.getTime());
  const hh = Math.floor(diff / 3600000);
  const mm = Math.floor((diff % 3600000) / 60000);
  const ss = Math.floor((diff % 60000) / 1000);
  return `${String(hh).padStart(2, "0")}:${String(mm).padStart(2, "0")}:${String(ss).padStart(2, "0")}`;
}

function greeting() {
  const h = new Date().getHours();
  if (h < 5) return "ليلة مباركة";
  if (h < 12) return "صباح الخير";
  if (h < 17) return "نهارك مبارك";
  return "مساء الخير";
}

export default function Home({ go, onSettings }: { go: Go; onSettings: () => void }) {
  const [, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((x) => x + 1), 1000);
    return () => clearInterval(id);
  }, []);
  const now = new Date();
  const next = nextPrayer(now);
  const hadith = useMemo(() => {
    const day = Math.floor(now.getTime() / 86400000);
    return HADITHS[day % HADITHS.length];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="view">
      <div>
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", lineHeight: 1.2, color: "var(--text)", margin: "4px 4px 0" }}>
          {greeting()}، {DEMO_CITY}
        </h1>
        <p className="muted" style={{ margin: "4px 4px 0" }}>
          {DEMO_HIJRI} <DemoBadge />
        </p>
      </div>

      <div className="next-prayer">
        <div className="label">الصلاة القادمة {next.tomorrow ? "(غدًا)" : ""}</div>
        <div className="name">{next.name}</div>
        <div className="count" dir="ltr">{countdown(next.time, next.tomorrow)}</div>
        <div className="meta">وقت {next.name} {next.time} — مواقيت تجريبية</div>
      </div>

      <div>
        <SectionTitle title="مواقيت اليوم" extra={<DemoBadge />} />
        <div className="card" style={{ marginTop: 8 }}>
          {PRAYER_TIMES_DEMO.map((p) => (
            <div key={p.name} className={`prayer-row ${p.name === next.name && !next.tomorrow ? "next" : ""}`}>
              {p.name === next.name && !next.tomorrow && <span className="dot" />}
              <span className="nm">{p.name}</span>
              <span className="tm" dir="ltr">{p.time}</span>
            </div>
          ))}
        </div>
      </div>

      <div>
        <SectionTitle title="حديث اليوم" />
        <div className="card glow" style={{ marginTop: 8 }}>
          <p style={{ fontSize: 18, fontWeight: 600, color: "var(--text)", lineHeight: 1.9, margin: 0 }}>
            «{hadith.text}»
          </p>
          <p className="muted" style={{ marginTop: 8 }}>{hadith.source}</p>
        </div>
      </div>

      <div>
        <SectionTitle title="وصول سريع" />
        <div className="grid2" style={{ marginTop: 8 }}>
          <button className="quick" onClick={() => go({ tab: "quran" })}>
            <span className="ic"><BookOpen size={22} /></span>
            القرآن الكريم
          </button>
          <button className="quick" onClick={() => go({ tab: "worship", view: "adhkar" })}>
            <span className="ic"><Sparkles size={22} /></span>
            الأذكار
          </button>
          <button className="quick" onClick={() => go({ tab: "worship", view: "qibla" })}>
            <span className="ic"><Compass size={22} /></span>
            اتجاه القبلة
          </button>
          <button className="quick" onClick={() => go({ tab: "library" })}>
            <span className="ic"><Library size={22} /></span>
            المكتبة
          </button>
        </div>
      </div>

      <div>
        <SectionTitle title="التطبيق" />
        <div className="card" style={{ marginTop: 8 }}>
          <button className="row" onClick={onSettings}>
            <span className="ic blue"><Settings size={22} /></span>
            <span className="tx"><b>الإعدادات</b><small>المدينة، التنبيهات، حول التطبيق</small></span>
            <Chevron />
          </button>
        </div>
        <p className="muted center mt8">مساعد المسلم — نسخة الويب التجريبية ٠.١.٠</p>
      </div>
    </div>
  );
}

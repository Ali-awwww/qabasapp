import { useEffect, useState } from "react";
import { BookOpen, Check, Compass, MoonStar, RotateCcw, Sun } from "lucide-react";
import {
  ADHKAR_AFTER_PRAYER, ADHKAR_EVENING, ADHKAR_MORNING, DEMO_CITY, GUIDES, HADITHS,
  PRAYER_TIMES_DEMO, QIBLA_DEGREE_DEMO, type Dhikr,
} from "../data/demo";
import type { Go, Route } from "../types";
import { Chevron, DemoBadge, EmptyState, SectionTitle } from "../components/ui";

type AdhkarCat = "morning" | "evening" | "after";
const CATS: { id: AdhkarCat; label: string }[] = [
  { id: "morning", label: "أذكار الصباح" },
  { id: "evening", label: "أذكار المساء" },
  { id: "after", label: "بعد الصلاة" },
];

export default function Worship({ route, go, toast }: { route: Route; go: Go; toast: (t: string) => void }) {
  if (route.view === "qibla") return <Qibla go={go} toast={toast} />;
  if (route.view === "adhkar") return <Adhkar go={go} />;
  if (route.view === "hadith") return <HadithList go={go} />;
  return <Overview go={go} />;
}

function Overview({ go }: { go: Go }) {
  return (
    <div className="view">
      <div>
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", lineHeight: 1.2, color: "var(--text)", margin: "4px 4px 0" }}>
          العبادات
        </h1>
        <p className="muted" style={{ margin: "4px 4px 0" }}>مواقيت {DEMO_CITY} <DemoBadge /></p>
      </div>

      <div className="card">
        {PRAYER_TIMES_DEMO.map((p) => (
          <div key={p.name} className="prayer-row">
            <span className="nm">{p.name}</span>
            <span className="tm" dir="ltr">{p.time}</span>
          </div>
        ))}
      </div>

      <div className="card">
        <button className="row" onClick={() => go({ tab: "worship", view: "qibla" })}>
          <span className="ic"><Compass size={22} /></span>
          <span className="tx"><b>اتجاه القبلة</b><small>بوصلة حية من جهازك، أو الاتجاه التجريبي</small></span>
          <Chevron />
        </button>
        <button className="row" onClick={() => go({ tab: "worship", view: "adhkar" })}>
          <span className="ic green"><MoonStar size={22} /></span>
          <span className="tx"><b>الأذكار</b><small>الصباح والمساء وبعد الصلاة — مع عدّاد</small></span>
          <Chevron />
        </button>
        <button className="row" onClick={() => go({ tab: "worship", view: "hadith" })}>
          <span className="ic amber"><BookOpen size={22} /></span>
          <span className="tx"><b>الأحاديث</b><small>أحاديث قصيرة صحيحة للقراءة اليومية</small></span>
          <Chevron />
        </button>
      </div>

      <SectionTitle title="أدلة العبادات" />
      {GUIDES.map((g) => (
        <div className="card" key={g.title}>
          <b style={{ fontSize: 18, color: "var(--text)" }}>{g.title}</b>
          <p style={{ margin: "6px 0 0", fontSize: 16, lineHeight: 1.9 }}>{g.text}</p>
        </div>
      ))}
    </div>
  );
}

function Qibla({ go, toast }: { go: Go; toast: (t: string) => void }) {
  const [heading, setHeading] = useState(QIBLA_DEGREE_DEMO);
  const [live, setLive] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!live) return;
    const onMove = (e: DeviceOrientationEvent) => {
      const h = (e as unknown as { webkitCompassHeading?: number }).webkitCompassHeading;
      if (typeof h === "number") setHeading(Math.round(h));
      else if (typeof e.alpha === "number") setHeading(Math.round(360 - e.alpha));
    };
    window.addEventListener("deviceorientation", onMove);
    return () => window.removeEventListener("deviceorientation", onMove);
  }, [live]);

  const enable = async () => {
    setLoading(true);
    try {
      const DOE = DeviceOrientationEvent as unknown as { requestPermission?: () => Promise<string> };
      if (typeof DOE?.requestPermission === "function") {
        const res = await DOE.requestPermission();
        if (res !== "granted") throw new Error("denied");
      }
      setLive(true);
      toast("البوصلة الحية مفعّلة");
    } catch {
      toast("تعذّر الوصول لحساس الجهاز — الاتجاه التجريبي معروض");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="view">
      <button className="btn btn-ghost" style={{ alignSelf: "flex-start" }} onClick={() => go({ tab: "worship" })}>
        → العودة للعبادات
      </button>
      <div className="center">
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", color: "var(--text)", margin: "4px 0" }}>اتجاه القبلة</h1>
        <p className="muted" style={{ margin: 0 }}>من {DEMO_CITY} — {live ? "بوصلة حية" : "اتجاه تجريبي"} <DemoBadge /></p>
      </div>
      <div className="compass" style={{ transform: `rotate(${QIBLA_DEGREE_DEMO - heading}deg)` }}>
        <div className="needle" />
        <div className="center" style={{ transform: `rotate(${heading - QIBLA_DEGREE_DEMO}deg)` }}>القبلة</div>
        <div className="deg" style={{ transform: `rotate(${heading - QIBLA_DEGREE_DEMO}deg)` }} dir="ltr">{heading}°</div>
      </div>
      <p className="center muted" style={{ margin: 0 }}>
        {live ? "أدر جهازك حتى يشير السهم نحو الكعبة" : `الاتجاه التجريبي ${QIBLA_DEGREE_DEMO}° نحو الجنوب الشرقي`}
      </p>
      {!live && (
        <button className="btn btn-primary btn-block" onClick={enable} disabled={loading}>
          {loading ? "جارٍ التفعيل…" : "تفعيل البوصلة الحية"}
        </button>
      )}
      {live && (
        <button className="btn btn-ghost btn-block" onClick={() => setLive(false)}>إيقاف البوصلة الحية</button>
      )}
    </div>
  );
}

function DhikrCard({ d }: { d: Dhikr }) {
  const [count, setCount] = useState(0);
  const done = count >= d.count;
  return (
    <div className="card" style={done ? { borderColor: "rgba(52,211,153,.45)" } : undefined}>
      {d.note && <span className="badge-demo" style={{ marginBottom: 8 }}>{d.note}</span>}
      <p style={{ fontSize: 18, fontWeight: 600, color: "var(--text)", lineHeight: 2, margin: 0 }}>{d.text}</p>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 14 }}>
        <button
          className="counter-ring"
          style={{ width: 84, height: 84, margin: 0 }}
          onClick={() => setCount((c) => Math.min(d.count, c + 1))}
          aria-label={`عدّ — ${count} من ${d.count}`}
        >
          {done ? <Check size={32} color="var(--green)" /> : <b style={{ fontSize: 26 }}>{d.count - count}</b>}
          <span>{done ? "أتممت" : "متبقٍ"}</span>
        </button>
        <div style={{ flex: 1 }}>
          <b style={{ color: "var(--text)" }}>{count} / {d.count}</b>
          <p className="muted" style={{ margin: "2px 0 0" }}>المس الدائرة للعدّ</p>
          {count > 0 && (
            <button
              className="btn btn-ghost"
              style={{ minHeight: 40, padding: "0 14px", marginTop: 8, fontSize: 15 }}
              onClick={() => setCount(0)}
            >
              <RotateCcw size={16} /> إعادة
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function Adhkar({ go }: { go: Go }) {
  const [cat, setCat] = useState<AdhkarCat>("morning");
  const list = cat === "morning" ? ADHKAR_MORNING : cat === "evening" ? ADHKAR_EVENING : ADHKAR_AFTER_PRAYER;
  return (
    <div className="view">
      <button className="btn btn-ghost" style={{ alignSelf: "flex-start" }} onClick={() => go({ tab: "worship" })}>
        → العودة للعبادات
      </button>
      <div>
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", lineHeight: 1.2, color: "var(--text)", margin: "4px 4px 0" }}>
          الأذكار
        </h1>
        <p className="muted" style={{ margin: "4px 4px 0" }}>المس الدائرة لعدّ كل ذكر <DemoBadge /></p>
      </div>
      <div className="chips" role="tablist" aria-label="أقسام الأذكار">
        {CATS.map((c) => (
          <button key={c.id} className="chip" role="tab" aria-pressed={cat === c.id} onClick={() => setCat(c.id)}>
            {c.id === "morning" ? <Sun size={16} /> : <MoonStar size={16} />}
            {c.label}
          </button>
        ))}
      </div>
      {list.map((d, i) => <DhikrCard key={`${cat}-${i}`} d={d} />)}
    </div>
  );
}

function HadithList({ go }: { go: Go }) {
  return (
    <div className="view">
      <button className="btn btn-ghost" style={{ alignSelf: "flex-start" }} onClick={() => go({ tab: "worship" })}>
        → العودة للعبادات
      </button>
      <div>
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", lineHeight: 1.2, color: "var(--text)", margin: "4px 4px 0" }}>
          الأحاديث
        </h1>
        <p className="muted" style={{ margin: "4px 4px 0" }}>أحاديث قصيرة صحيحة <DemoBadge /></p>
      </div>
      {HADITHS.length === 0 ? (
        <EmptyState icon={BookOpen} title="لا أحاديث بعد" text="ستظهر الأحاديث هنا بعد ربط قاعدة البيانات." />
      ) : (
        HADITHS.map((h, i) => (
          <div className="card" key={i}>
            <p style={{ fontSize: 18, fontWeight: 600, color: "var(--text)", lineHeight: 1.9, margin: 0 }}>«{h.text}»</p>
            <p className="muted" style={{ marginTop: 8 }}>{h.source}</p>
          </div>
        ))
      )}
    </div>
  );
}

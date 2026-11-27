import { useState } from "react";
import { Trash2 } from "lucide-react";
import { DEMO_CITY } from "../data/demo";
import { DemoBadge, Toggle } from "../components/ui";

const CITIES = ["دمشق", "حلب", "حمص", "حماة", "اللاذقية", "إدلب", "درعا", "دير الزور"];

function stored(key: string, fallback: string) {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

export default function Settings({ toast }: { toast: (t: string) => void }) {
  const [city, setCity] = useState(() => stored("ma-city", DEMO_CITY));
  const [adhan, setAdhan] = useState(() => stored("ma-adhan", "1") === "1");
  const [reminder, setReminder] = useState(() => stored("ma-reminder", "1") === "1");

  const changeCity = (c: string) => {
    setCity(c);
    try {
      localStorage.setItem("ma-city", c);
    } catch {
      /* ignore */
    }
    toast(`المدينة: ${c} — المواقيت تجريبية وتُحدَّث بعد ربط الخلفية`);
  };

  const resetProgress = () => {
    try {
      Object.keys(localStorage)
        .filter((k) => k.startsWith("ma-read-"))
        .forEach((k) => localStorage.removeItem(k));
    } catch {
      /* ignore */
    }
    toast("تم مسح تقدّم القراءة");
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div>
        <label htmlFor="city" style={{ display: "block", fontWeight: 700, marginBottom: 8, color: "var(--text)" }}>
          المدينة
        </label>
        <select
          id="city"
          value={city}
          onChange={(e) => changeCity(e.target.value)}
          style={{
            width: "100%",
            minHeight: 48,
            background: "var(--surface)",
            color: "var(--text)",
            border: "1px solid var(--border)",
            borderRadius: 12,
            padding: "0 12px",
            fontSize: 16,
          }}
        >
          {CITIES.map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <p className="muted" style={{ margin: "6px 0 0", fontSize: 14 }}>
          المواقيت المعروضة تجريبية لدمشق حاليًا، وتُحدَّث حسب المدينة بعد ربط الخلفية.
        </p>
      </div>

      <div className="row" style={{ border: 0, padding: 0 }}>
        <span className="tx"><b>تنبيه الأذان</b><small>تنبيه قبل كل صلاة</small></span>
        <Toggle checked={adhan} onChange={(v) => { setAdhan(v); localStorage.setItem("ma-adhan", v ? "1" : "0"); }} label="تنبيه الأذان" />
      </div>
      <div className="row" style={{ border: 0, padding: 0 }}>
        <span className="tx"><b>تذكير الأذكار</b><small>تذكير صباحي ومسائي</small></span>
        <Toggle checked={reminder} onChange={(v) => { setReminder(v); localStorage.setItem("ma-reminder", v ? "1" : "0"); }} label="تذكير الأذكار" />
      </div>

      <div className="card">
        <b style={{ color: "var(--text)" }}>حول التطبيق</b>
        <p className="muted" style={{ margin: "6px 0 0", lineHeight: 1.8, fontSize: 15 }}>
          مساعد المسلم — نسخة الويب التجريبية ٠.١.٠<br />
          إعادة تصميم كاملة بأسلوب HIG + سايبر. كل البيانات المعروضة تجريبية ومحلية
          لأن الخلفية السابقة غير متاحة حاليًا؛ تُستبدل بالبيانات الحية عند ربط مشروع جديد.
        </p>
        <p style={{ margin: "8px 0 0" }}><DemoBadge /></p>
      </div>

      <button
        className="btn btn-block"
        style={{ borderColor: "rgba(239,68,68,.5)", color: "#f87171", background: "transparent", minHeight: 48 }}
        onClick={resetProgress}
      >
        <Trash2 size={18} /> مسح تقدّم القراءة
      </button>
    </div>
  );
}

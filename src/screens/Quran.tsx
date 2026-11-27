import { useMemo, useState } from "react";
import { BookOpen, Check, Minus, Plus, SearchX } from "lucide-react";
import { SURAHS, type Surah } from "../data/demo";
import type { Go, Route } from "../types";
import { Chevron, DemoBadge, EmptyState, SearchInput, SectionTitle } from "../components/ui";

function readKey(n: number) {
  return `ma-read-${n}`;
}

export default function Quran({ route, go, toast }: { route: Route; go: Go; toast: (t: string) => void }) {
  const [q, setQ] = useState("");
  const [fontSize, setFontSize] = useState(26);
  const [done, setDone] = useState(false);

  const surah = useMemo(
    () => (route.view === "reader" ? SURAHS.find((s) => s.n === Number(route.param)) : undefined),
    [route]
  );

  const list = useMemo(() => {
    const query = q.trim();
    if (!query) return SURAHS;
    return SURAHS.filter((s) => s.name.includes(query) || String(s.n) === query);
  }, [q]);

  if (surah) return <Reader surah={surah} fontSize={fontSize} setFontSize={setFontSize} done={done} setDone={setDone} go={go} toast={toast} />;

  return (
    <div className="view">
      <div>
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", lineHeight: 1.2, color: "var(--text)", margin: "4px 4px 0" }}>
          القرآن الكريم
        </h1>
        <p className="muted" style={{ margin: "4px 4px 0" }}>فهرس السور الـ ١١٤ <DemoBadge /></p>
      </div>

      <SearchInput value={q} onChange={setQ} placeholder="ابحث عن سورة بالاسم أو الرقم…" />

      {list.length === 0 ? (
        <EmptyState
          icon={SearchX}
          title={`لا توجد سورة باسم «${q.trim()}»`}
          text="تحقق من الإملاء، أو جرّب رقم السورة، أو امسح البحث لعرض الفهرس كاملًا."
          action={
            <button className="btn btn-ghost" onClick={() => setQ("")}>مسح البحث</button>
          }
        />
      ) : (
        <div className="card">
          {list.map((s) => {
            const isRead = typeof localStorage !== "undefined" && localStorage.getItem(readKey(s.n)) === "1";
            return (
              <button key={s.n} className="row" onClick={() => { setDone(false); go({ tab: "quran", view: "reader", param: s.n }); }}>
                <span className="ic" style={{ fontWeight: 800, fontSize: 17 }}>{s.n}</span>
                <span className="tx">
                  <b>سورة {s.name} {isRead ? "✓" : ""}</b>
                  <small>{s.ayahs} آية • {s.type}{s.text ? " • النص كامل" : ""}</small>
                </span>
                <Chevron />
              </button>
            );
          })}
        </div>
      )}
      <p className="muted center">النص الكامل متاح الآن للسور القصيرة؛ بقية السور تُعرض بعد ربط قاعدة البيانات.</p>
    </div>
  );
}

function Reader({
  surah, fontSize, setFontSize, done, setDone, go, toast,
}: {
  surah: Surah;
  fontSize: number;
  setFontSize: (n: number) => void;
  done: boolean;
  setDone: (b: boolean) => void;
  go: Go;
  toast: (t: string) => void;
}) {
  const markRead = () => {
    localStorage.setItem(readKey(surah.n), "1");
    setDone(true);
    toast("أحسنت، سُجّلت قراءتك");
  };
  return (
    <div className="view">
      <button className="btn btn-ghost" style={{ alignSelf: "flex-start" }} onClick={() => go({ tab: "quran" })}>
        → العودة للفهرس
      </button>
      <div className="card glow center">
        <p className="muted" style={{ margin: 0 }}>سورة</p>
        <h1 style={{ fontSize: 32, fontWeight: 800, letterSpacing: "-0.02em", color: "var(--text)", margin: "4px 0" }}>
          {surah.name}
        </h1>
        <p className="muted" style={{ margin: 0 }}>{surah.ayahs} آية • {surah.type} <DemoBadge /></p>
        <div style={{ display: "flex", gap: 8, justifyContent: "center", marginTop: 12 }}>
          <button className="icon-btn" aria-label="تصغير الخط" onClick={() => setFontSize(Math.max(20, fontSize - 2))} disabled={fontSize <= 20}>
            <Minus size={20} />
          </button>
          <button className="icon-btn" aria-label="تكبير الخط" onClick={() => setFontSize(Math.min(34, fontSize + 2))} disabled={fontSize >= 34}>
            <Plus size={20} />
          </button>
        </div>
      </div>

      {surah.text ? (
        <div className="card">
          <div className="quran-text" style={{ fontSize }}>
            {surah.text.map((ayah, i) => (
              <span key={i}>
                {ayah}
                <span className="ayah-num">{i + 1}</span>{" "}
              </span>
            ))}
          </div>
          <div style={{ marginTop: 16 }}>
            {done || localStorage.getItem(readKey(surah.n)) === "1" ? (
              <div className="center" style={{ color: "var(--green)", fontWeight: 700, display: "flex", gap: 6, alignItems: "center", justifyContent: "center" }}>
                <Check size={20} /> أتممت قراءة هذه السورة
              </div>
            ) : (
              <button className="btn btn-primary btn-block" onClick={markRead}>أتممت القراءة</button>
            )}
          </div>
        </div>
      ) : (
        <>
          <EmptyState
            icon={BookOpen}
            title="النص الكامل لهذه السورة غير مضمّن بعد"
            text={`سورة ${surah.name} (${surah.ayahs} آية، ${surah.type}) ستُعرض كاملة بعد ربط قاعدة بيانات القرآن في النسخة الحية.`}
          />
          <button className="btn btn-ghost btn-block" onClick={() => go({ tab: "quran" })}>اختر سورة قصيرة متاحة</button>
        </>
      )}
      <SectionTitle title="عن هذه السورة" />
      <div className="card">
        <p style={{ margin: 0, fontSize: 16, lineHeight: 1.9 }}>
          سورة {surah.name} ترتيبها {surah.n} في المصحف، وعدد آياتها {surah.ayahs} آية، وهي سورة {surah.type}.
          {surah.text ? " نصها كامل في الأعلى." : ""}
        </p>
      </div>
    </div>
  );
}

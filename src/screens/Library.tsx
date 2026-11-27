import { useState } from "react";
import { BookOpen, Headphones, Play, User } from "lucide-react";
import { AUDIO_LIBRARY, SCHOLARS, STORIES, type Scholar, type Story } from "../data/demo";
import { DemoBadge, SectionTitle, Sheet } from "../components/ui";

export default function Library() {
  const [scholar, setScholar] = useState<Scholar | null>(null);
  const [story, setStory] = useState<Story | null>(null);

  return (
    <div className="view">
      <div>
        <h1 style={{ fontSize: 28, fontWeight: 800, letterSpacing: "-0.02em", lineHeight: 1.2, color: "var(--text)", margin: "4px 4px 0" }}>
          المكتبة
        </h1>
        <p className="muted" style={{ margin: "4px 4px 0" }}>علماء وقصص وتلاوات <DemoBadge /></p>
      </div>

      <SectionTitle title="محاضرات العلماء" />
      <div className="card">
        {SCHOLARS.map((s) => (
          <button key={s.name} className="row" onClick={() => setScholar(s)}>
            <span className="ic"><User size={22} /></span>
            <span className="tx">
              <b>{s.name}</b>
              <small>{s.era} • {s.topic}</small>
            </span>
            <span className="badge-demo">{s.lectures} محاضرة</span>
          </button>
        ))}
      </div>

      <SectionTitle title="قصص الأنبياء" />
      {STORIES.map((s) => (
        <button key={s.title} className="card" style={{ textAlign: "right", width: "100%" }} onClick={() => setStory(s)}>
          <b style={{ fontSize: 18, color: "var(--text)" }}>{s.title}</b>
          <p className="muted" style={{ margin: "6px 0 0", lineHeight: 1.8 }}>{s.summary}</p>
        </button>
      ))}

      <SectionTitle title="مكتبة الصوت" extra={<DemoBadge />} />
      <div className="card">
        {AUDIO_LIBRARY.map((a) => (
          <div key={a.title} className="row" style={{ cursor: "default" }}>
            <button className="icon-btn" disabled aria-label="التشغيل غير متاح في النسخة التجريبية" title="التشغيل غير متاح في النسخة التجريبية">
              <Play size={20} />
            </button>
            <span className="tx">
              <b>{a.title}</b>
              <small>{a.reciter}</small>
            </span>
            <span className="muted" style={{ fontSize: 14 }} dir="ltr">{a.duration}</span>
          </div>
        ))}
      </div>
      <p className="muted center">
        <Headphones size={16} style={{ verticalAlign: "middle" }} /> التشغيل الصوتي غير مفعّل في هذه النسخة التجريبية — يأتي مع ربط مكتبة الصوت.
      </p>

      {scholar && (
        <Sheet title={scholar.name} onClose={() => setScholar(null)}>
          <p style={{ fontSize: 16, lineHeight: 1.9, color: "var(--text)" }}>
            <b>العصر:</b> {scholar.era}<br />
            <b>التخصص:</b> {scholar.topic}<br />
            <b>المحاضرات:</b> {scholar.lectures} محاضرة (تجريبي)
          </p>
          <p className="muted" style={{ fontSize: 15, lineHeight: 1.8 }}>
            قائمة المحاضرات الكاملة وروابط الاستماع تأتي بعد ربط قاعدة البيانات.
          </p>
          <button className="btn btn-primary btn-block" onClick={() => setScholar(null)}>تم</button>
        </Sheet>
      )}

      {story && (
        <Sheet title={story.title} onClose={() => setStory(null)}>
          <p style={{ fontSize: 17, lineHeight: 2, color: "var(--text)" }}>{story.summary}</p>
          <p className="muted" style={{ fontSize: 15, lineHeight: 1.8 }}>
            <BookOpen size={16} style={{ verticalAlign: "middle" }} /> <b>الدروس:</b> {story.lessons}
          </p>
          <p className="muted" style={{ fontSize: 15, lineHeight: 1.8 }}>القصة الكاملة تأتي مع ربط قاعدة البيانات.</p>
          <button className="btn btn-primary btn-block" onClick={() => setStory(null)}>تم</button>
        </Sheet>
      )}
    </div>
  );
}

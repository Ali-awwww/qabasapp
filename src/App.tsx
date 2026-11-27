import { useCallback, useEffect, useState } from "react";
import { BookOpen, Home as HomeIcon, Library, MoonStar, Sparkles, X } from "lucide-react";
import Home from "./screens/Home";
import Quran from "./screens/Quran";
import Worship from "./screens/Worship";
import LibraryScreen from "./screens/Library";
import Assistant from "./screens/Assistant";
import Settings from "./screens/Settings";
import { Sheet, Toast } from "./components/ui";
import type { Route, TabId } from "./types";

const TABS: { id: TabId; label: string; icon: typeof HomeIcon }[] = [
  { id: "home", label: "الرئيسية", icon: HomeIcon },
  { id: "quran", label: "القرآن", icon: BookOpen },
  { id: "worship", label: "العبادات", icon: MoonStar },
  { id: "library", label: "المكتبة", icon: Library },
];

export default function App() {
  const [route, setRoute] = useState<Route>({ tab: "home" });
  const [assistantOpen, setAssistantOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [toast, setToast] = useState("");

  const showToast = useCallback((t: string) => {
    setToast(t);
  }, []);

  useEffect(() => {
    if (!toast) return;
    const id = setTimeout(() => setToast(""), 2600);
    return () => clearTimeout(id);
  }, [toast]);

  const go = (r: Route) => {
    setRoute(r);
    setAssistantOpen(false);
    window.scrollTo({ top: 0 });
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="logo">
            <MoonStar size={22} />
          </span>
          <b>مساعد المسلم</b>
        </div>
        <span className="badge-demo">نسخة الويب التجريبية</span>
      </header>

      <main className="content">
        {assistantOpen ? (
          <Assistant />
        ) : route.tab === "home" ? (
          <Home go={go} onSettings={() => setSettingsOpen(true)} />
        ) : route.tab === "quran" ? (
          <Quran route={route} go={go} toast={showToast} />
        ) : route.tab === "worship" ? (
          <Worship route={route} go={go} toast={showToast} />
        ) : (
          <LibraryScreen />
        )}
      </main>

      <button
        className="fab"
        aria-label={assistantOpen ? "إغلاق المساعد" : "فتح المساعد الذكي"}
        onClick={() => setAssistantOpen((v) => !v)}
      >
        {assistantOpen ? <X size={26} /> : <Sparkles size={26} />}
      </button>

      <nav className="bottombar" aria-label="التنقل الرئيسي">
        {TABS.map((t) => {
          const Icon = t.icon;
          const active = !assistantOpen && route.tab === t.id;
          return (
            <button
              key={t.id}
              className="tab"
              aria-current={active ? "page" : undefined}
              onClick={() => go({ tab: t.id })}
            >
              <Icon size={24} />
              <span>{t.label}</span>
            </button>
          );
        })}
      </nav>

      {settingsOpen && (
        <Sheet title="الإعدادات" onClose={() => setSettingsOpen(false)}>
          <Settings toast={showToast} />
        </Sheet>
      )}

      {toast && <Toast text={toast} />}
    </div>
  );
}

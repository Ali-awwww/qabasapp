export type TabId = "home" | "quran" | "worship" | "library";

export interface Route {
  tab: TabId;
  /** شاشة فرعية داخل التبويب: reader | adhkar | qibla | hadith | scholar | story | audio | guides */
  view?: string;
  param?: string | number;
}

export type Go = (r: Route) => void;

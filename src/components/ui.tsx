import type { ReactNode } from "react";
import { ChevronLeft, Search, X, type LucideIcon } from "lucide-react";

export function DemoBadge() {
  return <span className="badge-demo">تجريبي</span>;
}

export function SectionTitle({ title, extra }: { title: string; extra?: ReactNode }) {
  return (
    <div className="section-title">
      <h2>{title}</h2>
      {extra}
    </div>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  text,
  action,
}: {
  icon: LucideIcon;
  title: string;
  text: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <div className="ic">
        <Icon size={32} strokeWidth={1.5} />
      </div>
      <b>{title}</b>
      <p>{text}</p>
      {action}
    </div>
  );
}

export function Skeleton({ h = 20, w = "100%" }: { h?: number; w?: string }) {
  return <div className="skeleton" style={{ minHeight: h, width: w }} />;
}

export function Toast({ text }: { text: string }) {
  return <div className="toast" role="status">{text}</div>;
}

export function Sheet({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <>
      <div className="sheet-backdrop" onClick={onClose} />
      <div className="sheet" role="dialog" aria-label={title}>
        <div className="grab" />
        <div className="section-title" style={{ marginTop: 0 }}>
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="إغلاق" style={{ minWidth: 44, minHeight: 44 }}>
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      className="toggle"
      onClick={() => onChange(!checked)}
    />
  );
}

export function SearchInput({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
}) {
  return (
    <label
      style={{
        display: "flex",
        alignItems: "center",
        gap: 10,
        background: "var(--surface)",
        border: "1px solid var(--border)",
        borderRadius: 14,
        padding: "0 14px",
        minHeight: 48,
      }}
    >
      <Search size={20} color="var(--text-sub)" />
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        style={{
          flex: 1,
          background: "transparent",
          border: 0,
          outline: "none",
          color: "var(--text)",
          fontSize: 16,
          minHeight: 44,
        }}
      />
    </label>
  );
}

export function Chevron() {
  return <ChevronLeft size={20} className="chev" />;
}

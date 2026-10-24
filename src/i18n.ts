// UI text comes only from i18n/*.json (same files tools/i18n.py turns into Android strings).
// Missing Tamil keys fall back to English; nothing is translated in code.
import en from '../i18n/ui.en.json';
import fixed from '../i18n/ui.fixed.json';
import ta from '../i18n/ui.ta.json';
import type { Lang } from './native';

export type Key = keyof typeof en | keyof typeof fixed;

const tables: Record<Lang, Record<string, string>> = { en: { ...en, ...fixed }, ta: { ...en, ...ta, ...fixed } };

export const translator = (lang: Lang) => (key: Key, args?: Record<string, string | number>) =>
  (tables[lang][key] ?? key).replace(/\{(\w+)\}/g, (m, name) => (args && name in args ? String(args[name]) : m));

export type T = ReturnType<typeof translator>;

/** ₹ with Indian digit grouping (₹1,20,000), Western digits in both languages. */
export function rupees(n: number) {
  const s = Math.round(n).toString();
  const head = s.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',');
  return '₹' + (head ? head + ',' + s.slice(-3) : s);
}

export const grams = (n: number) => (Math.round(n * 10) / 10).toString();

/** Energy per 100 g from the Atwater factors (protein 4, fat 9, carbohydrate 4 kcal/g). */
export const kcal = (n: { protein: number; fat: number; carbohydrate?: number | null }) => Math.round(n.protein * 4 + n.fat * 9 + (n.carbohydrate ?? 0) * 4);

export const percent = (c: number) => Math.round(c * 100);

const locale = (lang: Lang) => (lang === 'ta' ? 'ta-IN' : 'en-IN');

export const formatTime = (ms: number, lang: Lang) =>
  new Date(ms).toLocaleTimeString(locale(lang), { hour: 'numeric', minute: '2-digit' });

export function formatDay(ms: number, lang: Lang, t: T) {
  const day = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diff = Math.round((day(new Date()) - day(new Date(ms))) / 86400000);
  if (diff === 0) return t('history_today');
  if (diff === 1) return t('history_yesterday');
  return new Date(ms).toLocaleDateString(locale(lang), { day: 'numeric', month: 'short', year: 'numeric' });
}

// Coded species values → UI strings. Unknown codes show raw, so a data typo is visible but harmless.
export const codeLabel = (t: T, prefix: 'habitat' | 'feeding' | 'vertical' | 'iucn', code: string) => {
  const s = t(`${prefix}_${code}` as Key);
  return s === `${prefix}_${code}` ? code : s;
};

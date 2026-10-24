// Light: warm cream and ink-black pills (Cal AI). Dark: deep night blues with soft glow (Pillowtalk).
// Brand teal and marigold come from docs/PLAN.md §7.

const light = {
  bg: '#F6F4EF',
  surface: '#FFFFFF',
  surfaceAlt: '#EFECE4',
  ink: '#111111',
  muted: '#5C5A55',
  line: '#E6E1D6',
  primary: '#111111',
  onPrimary: '#FFFFFF',
  accent: '#0E6E7E',
  accentSoft: '#DCEFF0',
  marigold: '#F2A541',
  success: '#2E9E5B',
  successSoft: '#DFF3E6',
  warn: '#B86E00',
  warnSoft: '#FCEFD9',
  danger: '#C8423B',
  // Nutrient colours (rings, tiles, stacked bar)
  energy: '#F26B3A',
  protein: '#E25D6B',
  fat: '#3E86F5',
  carbs: '#E3A21A',
  moisture: '#7FB8D6',
  ash: '#A79E8B',
  shadow: '0px 6px 24px rgba(17, 17, 17, 0.07)',
  scrim: 'rgba(0,0,0,0.38)',
};

export type Colors = typeof light;

const dark: Colors = {
  bg: '#0B1020',
  surface: '#151B2E',
  surfaceAlt: '#1D2540',
  ink: '#F2F3F8',
  muted: '#A6ACC0',
  line: '#27304D',
  primary: '#F2F3F8',
  onPrimary: '#0B1020',
  accent: '#62CBD8',
  accentSoft: '#163542',
  marigold: '#F6B860',
  success: '#5BD08C',
  successSoft: '#163A2A',
  warn: '#F6B860',
  warnSoft: '#3A2D16',
  danger: '#FF7D74',
  energy: '#FF8A5C',
  protein: '#FF7A88',
  fat: '#78A9FF',
  carbs: '#F4C04E',
  moisture: '#8FC6E3',
  ash: '#B9B2A2',
  shadow: '0px 8px 28px rgba(0, 0, 0, 0.35)',
  scrim: 'rgba(0,0,0,0.45)',
};

export const palettes = { light, dark };

// Official IUCN Red List colours (background, text).
export const iucnColors: Record<string, [string, string]> = {
  ne: ['#FFFFFF', '#111111'],
  dd: ['#D1D1C6', '#111111'],
  lc: ['#60C659', '#0B2A09'],
  nt: ['#CCE226', '#1F2600'],
  vu: ['#F9E814', '#2B2700'],
  en: ['#FC7F3F', '#2D1000'],
  cr: ['#D81E05', '#FFFFFF'],
};

export const font = 'HindMadurai';
export const scientificFont = 'NotoSerifItalic';

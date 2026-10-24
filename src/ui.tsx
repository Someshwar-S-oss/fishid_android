// Shared state, navigation types and the small set of components every screen is built from.
import React, { createContext, useContext, useEffect, useRef } from 'react';
import {
  ActivityIndicator, Animated, Image, Pressable, PressableProps, StyleProp, StyleSheet, Text, TextProps, TextStyle, View, ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Path } from 'react-native-svg';
import type { Fish, Lang, Scan, Versions } from './native';
import type { T } from './i18n';
import { Colors, font, iucnColors, scientificFont } from './theme';
import { codeLabel } from './i18n';

// ---------- App state & navigation ----------

export type Route =
  | { name: 'tabs' }
  | { name: 'scanner' }
  | { name: 'result'; scanId: string }
  | { name: 'compare'; scanId: string }
  | { name: 'nomatch'; scanId: string }
  | { name: 'profile'; id: string; scanId?: string }
  | { name: 'report'; scanId: string }
  | { name: 'settings' };

export type Tab = 'home' | 'guide' | 'history';

export type ThemePref = 'system' | 'light' | 'dark';

export type AppState = {
  c: Colors;
  dark: boolean;
  lang: Lang;
  t: T;
  fish: Fish[];
  fishById: Record<string, Fish>;
  history: Scan[];
  versions: Versions | null;
  prefs: Record<string, string>;
  setPref: (key: string, value: string) => void;
  setLang: (lang: Lang) => void;
  setTheme: (theme: ThemePref) => void;
  reloadHistory: () => void;
  push: (r: Route) => void;
  pop: () => void;
  /** Back to the tabs, optionally switching tab. */
  home: (tab?: Tab) => void;
  tab: Tab;
};

export const AppContext = createContext<AppState>(null as unknown as AppState);
export const useApp = () => useContext(AppContext);

/** Where a past scan opens: its fish, the compare screen, or the no-match tips. */
export const routeForScan = (s: Scan): Route =>
  s.species_id ? { name: 'profile', id: s.species_id, scanId: s.id } : s.outcome === 'UNSURE' ? { name: 'compare', scanId: s.id } : { name: 'nomatch', scanId: s.id };

// ---------- Text ----------

const type = {
  hero: { fontSize: 44, lineHeight: 52, fontWeight: '700' },
  display: { fontSize: 28, lineHeight: 38, fontWeight: '700' },
  headline: { fontSize: 22, lineHeight: 32, fontWeight: '700' },
  title: { fontSize: 18, lineHeight: 28, fontWeight: '600' },
  body: { fontSize: 16, lineHeight: 26, fontWeight: '400' },
  label: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  caption: { fontSize: 13, lineHeight: 20, fontWeight: '400' },
} satisfies Record<string, TextStyle>;

export type Variant = keyof typeof type;

export function Txt({ v = 'body', color, muted, style, ...rest }: TextProps & { v?: Variant; color?: string; muted?: boolean }) {
  const { c } = useApp();
  return <Text {...rest} style={[{ fontFamily: font, color: color ?? (muted ? c.muted : c.ink) }, type[v], style]} />;
}

export function Scientific({ children, style, color }: { children: string; style?: StyleProp<TextStyle>; color?: string }) {
  const { c } = useApp();
  return <Text style={[{ fontFamily: scientificFont, fontSize: 15, lineHeight: 22, color: color ?? c.muted }, style]}>{children}</Text>;
}

// ---------- Icons (paths ported from res/drawable, 24px stroke icons) ----------

const icons = {
  back: 'M15,18l-6,-6 6,-6',
  camera: 'M4,8h3l2,-3h6l2,3h3v11H4z M8.5,13a3.5,3.5 0 1,0 7.0,0a3.5,3.5 0 1,0 -7.0,0',
  check: 'M5,12l5,5L20,7',
  chevronDown: 'M6,9l6,6 6,-6',
  chevronRight: 'M9,18l6,-6 -6,-6',
  close: 'M6,6l12,12M18,6L6,18',
  depth: 'M4,4h16M12,4v16M8,16l4,4 4,-4',
  fish: 'M3,12c2.2,-3.6 5.2,-5.4 9,-5.4 2.7,0 4.5,1.8 5.8,3.6L21,8v8l-3.2,-2.2c-1.3,1.8 -3.1,3.6 -5.8,3.6 -3.8,0 -6.8,-1.8 -9,-5.4z',
  flag: 'M5,21V4M5,4h11l-2,4 2,4H5',
  flash: 'M13,2L4,14h7l-1,8 9,-12h-7z',
  gallery: 'M5,4h14a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-12a2,2 0 0 1 2,-2z M7,10a2,2 0 1,0 4,0a2,2 0 1,0 -4,0 M21,16l-5,-5 -9,9',
  globe: 'M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0 M3,12h18M12,3c2.5,3 2.5,15 0,18M12,3c-2.5,3 -2.5,15 0,18',
  history: 'M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0 M12,7v5l3,2',
  info: 'M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0 M12,11v5M12,8h0.01',
  layers: 'M12,3l9,5 -9,5 -9,-5z M3,13l9,5 9,-5',
  pin: 'M12,22s7,-6.2 7,-12a7,7 0 0 0 -14,0c0,5.8 7,12 7,12z M9.5,10a2.5,2.5 0 1,0 5.0,0a2.5,2.5 0 1,0 -5.0,0',
  price: 'M3,12V4a1,1 0 0 1 1,-1h8l9,9 -9,9z M6.0,7.5a1.5,1.5 0 1,0 3.0,0a1.5,1.5 0 1,0 -3.0,0',
  scan: 'M4,8V5a1,1 0 0 1 1,-1h3M16,4h3a1,1 0 0 1 1,1v3M20,16v3a1,1 0 0 1 -1,1h-3M8,20H5a1,1 0 0 1 -1,-1v-3 M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0',
  search: 'M4,11a7,7 0 1,0 14,0a7,7 0 1,0 -14,0 M20,20l-3.5,-3.5',
  settings:
    'M12.22,2h-0.44a2,2 0 0,0 -2,2v0.18a2,2 0 0,1 -1,1.73l-0.43,0.25a2,2 0 0,1 -2,0l-0.15,-0.08a2,2 0 0,0 -2.73,0.73l-0.22,0.38a2,2 0 0,0 0.73,2.73l0.15,0.1a2,2 0 0,1 1,1.72v0.51a2,2 0 0,1 -1,1.74l-0.15,0.09a2,2 0 0,0 -0.73,2.73l0.22,0.38a2,2 0 0,0 2.73,0.73l0.15,-0.08a2,2 0 0,1 2,0l0.43,0.25a2,2 0 0,1 1,1.73V20a2,2 0 0,0 2,2h0.44a2,2 0 0,0 2,-2v-0.18a2,2 0 0,1 1,-1.73l0.43,-0.25a2,2 0 0,1 2,0l0.15,0.08a2,2 0 0,0 2.73,-0.73l0.22,-0.39a2,2 0 0,0 -0.73,-2.73l-0.15,-0.08a2,2 0 0,1 -1,-1.74v-0.5a2,2 0 0,1 1,-1.74l0.15,-0.09a2,2 0 0,0 0.73,-2.73l-0.22,-0.38a2,2 0 0,0 -2.73,-0.73l-0.15,0.08a2,2 0 0,1 -2,0l-0.43,-0.25a2,2 0 0,1 -1,-1.73V4a2,2 0 0,0 -2,-2z M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0',
  share: 'M15,5a3,3 0 1,0 6,0a3,3 0 1,0 -6,0 M3,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0 M15,19a3,3 0 1,0 6,0a3,3 0 1,0 -6,0 M8.6,13.5l6.8,4M15.4,6.5l-6.8,4',
  shield: 'M12,3l8,3v6c0,5 -3.5,8 -8,9 -4.5,-1 -8,-4 -8,-9V6z',
  sun: 'M8,12a4,4 0 1,0 8,0a4,4 0 1,0 -8,0 M12,2v2M12,20v2M2,12h2M20,12h2M4.9,4.9l1.4,1.4M17.7,17.7l1.4,1.4M4.9,19.1l1.4,-1.4M17.7,6.3l1.4,-1.4',
  trash: 'M4,7h16M9,7V4h6v3M6,7l1,13h10l1,-13',
  waves: 'M2,9c2.5,-2 4.5,-2 7,0s4.5,2 7,0 4,-2 6,0M2,15c2.5,-2 4.5,-2 7,0s4.5,2 7,0 4,-2 6,0',
  // New for this UI
  home: 'M4,11l8,-7 8,7v9a1,1 0 0 1 -1,1h-4v-6h-6v6H5a1,1 0 0 1 -1,-1z',
  book: 'M5,4h10a3,3 0 0 1 3,3v13H8a3,3 0 0 1 -3,-3z M5,17a3,3 0 0 1 3,-3h10',
  flame: 'M12,22c4,0 7,-2.8 7,-6.8 0,-4.2 -3.5,-6.7 -4.5,-11.2 -2.3,1.6 -3.5,4 -3.5,6.5 -1.2,-0.7 -2,-2 -2.2,-3.5C6.6,9.3 5,12 5,15.2 5,19.2 8,22 12,22z',
  drop: 'M12,3c3.5,4.5 6,8 6,11a6,6 0 0 1 -12,0c0,-3 2.5,-6.5 6,-11z',
  leaf: 'M5,19C5,10 10,5 20,4c-1,10 -6,15 -15,15z M5,19l7,-7',
  bolt: 'M7,20l3,-7H5l7,-9 -2,7h5z',
  sparkle: 'M12,3l1.8,5.2L19,10l-5.2,1.8L12,17l-1.8,-5.2L5,10l5.2,-1.8z M19,16l0.8,2.2L22,19l-2.2,0.8L19,22l-0.8,-2.2L16,19l2.2,-0.8z',
  plus: 'M12,5v14M5,12h14',
  moon: 'M20,14.5A8,8 0 0 1 9.5,4 8,8 0 1 0 20,14.5z',
};

export type IconName = keyof typeof icons;

export function Icon({ name, size = 24, color, stroke = 2, fill }: { name: IconName; size?: number; color?: string; stroke?: number; fill?: string }) {
  const { c } = useApp();
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      <Path d={icons[name]} stroke={color ?? c.ink} strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" fill={fill ?? 'none'} />
    </Svg>
  );
}

// ---------- Pressables ----------

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/** Pressable that sinks slightly under the finger. */
export function Tap({ style, children, scale = 0.97, ...rest }: PressableProps & { style?: StyleProp<ViewStyle>; scale?: number; children?: React.ReactNode }) {
  const s = useRef(new Animated.Value(1)).current;
  const to = (v: number) => Animated.spring(s, { toValue: v, useNativeDriver: true, speed: 40, bounciness: 6 }).start();
  return (
    <AnimatedPressable
      {...rest}
      onPressIn={e => { to(scale); rest.onPressIn?.(e); }}
      onPressOut={e => { to(1); rest.onPressOut?.(e); }}
      style={[style, { transform: [{ scale: s }] }]}>
      {children}
    </AnimatedPressable>
  );
}

type BtnProps = { label: string; onPress?: () => void; icon?: IconName; kind?: 'primary' | 'outline' | 'ghost' | 'soft'; disabled?: boolean; loading?: boolean; style?: StyleProp<ViewStyle>; small?: boolean };

export function Btn({ label, onPress, icon, kind = 'primary', disabled, loading, style, small }: BtnProps) {
  const { c } = useApp();
  const bg = kind === 'primary' ? c.primary : kind === 'soft' ? c.surfaceAlt : 'transparent';
  const fg = kind === 'primary' ? c.onPrimary : c.ink;
  return (
    <Tap
      onPress={onPress}
      disabled={disabled || loading}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={[
        {
          minHeight: small ? 44 : 56, paddingHorizontal: small ? 16 : 22, borderRadius: 999, backgroundColor: bg,
          flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, opacity: disabled ? 0.4 : 1,
          borderWidth: kind === 'outline' ? 1.5 : 0, borderColor: c.line,
        },
        style,
      ]}>
      {loading ? <ActivityIndicator color={fg} /> : icon ? <Icon name={icon} size={20} color={fg} /> : null}
      <Txt v={small ? 'label' : 'title'} color={fg} style={{ fontWeight: '600', textAlign: 'center', flexShrink: 1 }}>{label}</Txt>
    </Tap>
  );
}

/** Round icon button, e.g. back/share over photos (translucent) or in headers (surface). */
export function CircleBtn({ icon, onPress, label, onPhoto, size = 44 }: { icon: IconName; onPress: () => void; label: string; onPhoto?: boolean; size?: number }) {
  const { c } = useApp();
  return (
    <Tap
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={6}
      style={{
        width: size, height: size, borderRadius: size / 2, alignItems: 'center', justifyContent: 'center',
        backgroundColor: onPhoto ? 'rgba(0,0,0,0.35)' : c.surface, boxShadow: onPhoto ? undefined : c.shadow,
      }}>
      <Icon name={icon} size={22} color={onPhoto ? '#fff' : c.ink} />
    </Tap>
  );
}

// ---------- Surfaces ----------

export function Card({ style, children, onPress }: { style?: StyleProp<ViewStyle>; children: React.ReactNode; onPress?: () => void }) {
  const { c } = useApp();
  const s: StyleProp<ViewStyle> = [{ backgroundColor: c.surface, borderRadius: 24, padding: 18, boxShadow: c.shadow }, style];
  return onPress ? <Tap onPress={onPress} style={s}>{children}</Tap> : <View style={s}>{children}</View>;
}

export function Chip({ label, bg, fg, icon, style }: { label: string; bg?: string; fg?: string; icon?: IconName; style?: StyleProp<ViewStyle> }) {
  const { c } = useApp();
  return (
    <View style={[{ flexDirection: 'row', alignItems: 'center', gap: 6, alignSelf: 'flex-start', backgroundColor: bg ?? c.surfaceAlt, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 4 }, style]}>
      {icon && <Icon name={icon} size={14} color={fg ?? c.ink} stroke={2.4} />}
      <Txt v="label" color={fg ?? c.ink} style={{ fontWeight: '600' }}>{label}</Txt>
    </View>
  );
}

export function IucnBadge({ code }: { code: string }) {
  const { t, c } = useApp();
  const colors = iucnColors[code];
  if (!colors) return null;
  return <Chip label={`${code.toUpperCase()} · ${codeLabel(t, 'iucn', code)}`} bg={colors[0]} fg={colors[1]} style={code === 'ne' ? { borderWidth: 1, borderColor: c.line } : undefined} />;
}

/** Nutrient-style tile: tinted icon square, label, big value (Cal AI's Calories / Carbs tiles). */
export function StatTile({ icon, tint, label, value, style }: { icon: IconName; tint: string; label: string; value: string; style?: StyleProp<ViewStyle> }) {
  const { c } = useApp();
  return (
    <View style={[{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: c.surface, borderRadius: 20, padding: 14, boxShadow: c.shadow }, style]}>
      <View style={{ width: 42, height: 42, borderRadius: 14, backgroundColor: tint + '22', alignItems: 'center', justifyContent: 'center' }}>
        <Icon name={icon} size={22} color={tint} />
      </View>
      <View style={{ flexShrink: 1 }}>
        <Txt v="caption" muted numberOfLines={2}>{label}</Txt>
        <Txt v="title" style={{ fontWeight: '700' }}>{value}</Txt>
      </View>
    </View>
  );
}

/** The app icon's mark (res/drawable/ic_launcher_*): marigold scan corners around a white fish on brand teal. */
export function Logo({ size = 40 }: { size?: number }) {
  return (
    <View style={{ width: size, height: size, borderRadius: size * 0.3, backgroundColor: '#0E6E7E', alignItems: 'center', justifyContent: 'center' }}>
      <Svg width={size * 0.78} height={size * 0.78} viewBox="2 2 20 20">
        <Path d="M4,8V5a1,1 0 0 1 1,-1h3M16,4h3a1,1 0 0 1 1,1v3M20,16v3a1,1 0 0 1 -1,1h-3M8,20H5a1,1 0 0 1 -1,-1v-3" stroke="#F2A541" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
        <Path d="M6,12c1.6,-2.4 3.6,-3.6 6,-3.6 1.8,0 3,1.2 3.9,2.4L18,9.5v5l-2.1,-1.3c-0.9,1.2 -2.1,2.4 -3.9,2.4 -2.4,0 -4.4,-1.2 -6,-3.6z" stroke="#FFFFFF" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </Svg>
    </View>
  );
}

/** Horizontal bar that fills in when shown (Cal AI's health score). [value] is 0..1. */
export function Bar({ value, color, height = 8 }: { value: number; color: string; height?: number }) {
  const { c } = useApp();
  const p = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(p, { toValue: value, duration: 800, useNativeDriver: false }).start();
  }, [p, value]);
  return (
    <View style={{ height, borderRadius: height, backgroundColor: c.surfaceAlt, overflow: 'hidden' }}>
      <Animated.View style={{ height, borderRadius: height, backgroundColor: color, width: p.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] }) }} />
    </View>
  );
}

// ---------- Images ----------

export function FishImage({ uri, style, radius = 20 }: { uri?: string | null; style?: StyleProp<ViewStyle>; radius?: number }) {
  const { c } = useApp();
  return (
    <View style={[{ backgroundColor: c.surfaceAlt, borderRadius: radius, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' }, style]}>
      <Icon name="fish" size={34} color={c.muted} />
      {uri ? <Image source={{ uri }} style={StyleSheet.absoluteFill} resizeMode="cover" /> : null}
    </View>
  );
}

// ---------- Layout ----------

/** Plain screen with a large title (and optional back / right action), content scrolls below. */
export function Header({ title, onBack, right, subtitle }: { title: string; onBack?: () => void; right?: React.ReactNode; subtitle?: string }) {
  const { t } = useApp();
  const insets = useSafeAreaInsets();
  return (
    <View style={{ paddingTop: insets.top + 8, paddingHorizontal: 20, paddingBottom: 8 }}>
      {onBack && (
        <View style={{ marginBottom: 12, alignSelf: 'flex-start' }}>
          <CircleBtn icon="back" label={t('action_back')} onPress={onBack} />
        </View>
      )}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Txt v="display" style={{ flex: 1 }} numberOfLines={2} adjustsFontSizeToFit minimumFontScale={0.7} accessibilityRole="header">{title}</Txt>
        {right}
      </View>
      {subtitle ? <Txt muted>{subtitle}</Txt> : null}
    </View>
  );
}

export function SectionTitle({ children, right }: { children: string; right?: React.ReactNode }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 28, marginBottom: 12 }}>
      <Txt v="headline" style={{ flex: 1 }} accessibilityRole="header">{children}</Txt>
      {right}
    </View>
  );
}

/** A / அ segmented switch. Labels are each language's own script, never translated. */
export function LanguageSwitch({ onDark }: { onDark?: boolean }) {
  const { c, lang, setLang, t } = useApp();
  const opt = (l: 'en' | 'ta', glyph: string, name: string) => {
    const on = lang === l;
    return (
      <Pressable
        key={l}
        onPress={() => !on && setLang(l)}
        accessibilityRole="radio"
        accessibilityState={{ selected: on }}
        accessibilityLabel={name}
        style={{ minWidth: 40, height: 36, borderRadius: 18, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 12, backgroundColor: on ? (onDark ? '#fff' : c.primary) : 'transparent' }}>
        <Text style={{ fontFamily: font, fontWeight: '700', fontSize: 15, color: on ? (onDark ? '#111' : c.onPrimary) : onDark ? '#fff' : c.ink }}>{glyph}</Text>
      </Pressable>
    );
  };
  return (
    <View style={{ flexDirection: 'row', padding: 3, borderRadius: 999, backgroundColor: onDark ? 'rgba(0,0,0,0.35)' : c.surface, boxShadow: onDark ? undefined : c.shadow }}>
      {opt('en', 'A', t('language_english'))}
      {opt('ta', 'அ', t('language_tamil'))}
    </View>
  );
}

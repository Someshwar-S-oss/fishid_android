import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Animated, Appearance, AppState as RNAppState, BackHandler, Dimensions, Easing, StatusBar, StyleSheet, useColorScheme, View } from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import { FishID, Fish, Lang, onScanResult, Scan, Versions } from './native';
import { translator } from './i18n';
import { palettes } from './theme';
import { AppContext, AppState, Icon, IconName, Route, Tab, Tap, ThemePref, Txt, useApp } from './ui';
import Onboarding from './screens/Onboarding';
import Home from './screens/Home';
import Guide from './screens/Guide';
import History from './screens/History';
import Scanner from './screens/Scanner';
import { Compare, NoMatch, Result } from './screens/Result';
import Profile from './screens/Profile';
import Report from './screens/Report';
import Settings from './screens/Settings';

export default function App() {
  return (
    <SafeAreaProvider>
      <Root />
    </SafeAreaProvider>
  );
}

type Entry = { key: number; route: Route };

function Root() {
  const scheme = useColorScheme();
  const [prefs, setPrefs] = useState<Record<string, string> | null>(null);
  const [lang, setLangState] = useState<Lang>('en');
  const [fish, setFish] = useState<Fish[]>([]);
  const [history, setHistory] = useState<Scan[]>([]);
  const [versions, setVersions] = useState<Versions | null>(null);
  const [stack, setStack] = useState<Entry[]>([]);
  const [tab, setTab] = useState<Tab>('home');
  const nextKey = useRef(1);

  const dark = scheme === 'dark';
  const c = palettes[dark ? 'dark' : 'light'];

  const syncLanguage = useCallback(() => { FishID.language().then(setLangState); }, []);
  useEffect(() => {
    FishID.prefs().then(p => {
      // Light unless the user picked otherwise (the Kotlin app stored LIGHT/DARK/SYSTEM in capitals).
      const theme = (p.theme ?? 'light').toLowerCase();
      Appearance.setColorScheme(theme === 'dark' ? 'dark' : theme === 'system' ? 'auto' : 'light');
      setPrefs({ ...p, theme });
    });
    syncLanguage();
    FishID.versions().then(setVersions).catch(() => {});
    // The language can also change from the phone's per-app language settings.
    const sub = RNAppState.addEventListener('change', s => s === 'active' && syncLanguage());
    return () => sub.remove();
  }, [syncLanguage]);
  useEffect(() => { FishID.catalog(lang).then(cat => setFish(cat.entries)); }, [lang]);

  const reloadHistory = useCallback(() => { FishID.history().then(setHistory); }, []);
  useEffect(reloadHistory, [reloadHistory]);

  const push = useCallback((route: Route) => setStack(s => [...s, { key: nextKey.current++, route }]), []);
  const pop = useCallback(() => setStack(s => s.slice(0, -1)), []);
  const home = useCallback((t?: Tab) => { setStack([]); if (t) setTab(t); }, []);

  // Every finished scan (camera or gallery) lands here, already saved to history.
  useEffect(() => onScanResult(async scan => {
    setHistory(await FishID.history()); // the result screens read the scan from history
    const route: Route = scan.outcome === 'MATCH' ? { name: 'result', scanId: scan.id } : scan.outcome === 'UNSURE' ? { name: 'compare', scanId: scan.id } : { name: 'nomatch', scanId: scan.id };
    setStack(s => [...s, { key: nextKey.current++, route }]);
  }), []);

  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (stack.length === 0) {
        if (tab === 'home') return false;
        setTab('home');
        return true;
      }
      pop();
      return true;
    });
    return () => sub.remove();
  }, [stack.length, tab, pop]);

  const state = useMemo<AppState | null>(() => prefs && {
    c, dark, lang, t: translator(lang), fish, history, versions, prefs, tab,
    fishById: Object.fromEntries(fish.map(f => [f.id, f])),
    setPref: (key, value) => { FishID.setPref(key, value); setPrefs(p => ({ ...p, [key]: value })); },
    setLang: l => { FishID.setLanguage(l); setLangState(l); },
    setTheme: (th: ThemePref) => {
      FishID.setPref('theme', th);
      setPrefs(p => ({ ...p, theme: th }));
      Appearance.setColorScheme(th === 'system' ? 'auto' : th);
    },
    reloadHistory, push, pop, home,
  }, [c, dark, lang, fish, history, versions, prefs, tab, reloadHistory, push, pop, home]);

  if (!state) return <View style={{ flex: 1, backgroundColor: c.bg }} />;

  return (
    <AppContext.Provider value={state}>
      <StatusBar barStyle={dark ? 'light-content' : 'dark-content'} />
      <View style={{ flex: 1, backgroundColor: c.bg }}>
        {prefs?.onboarded !== 'true' ? (
          <Onboarding onDone={() => state.setPref('onboarded', 'true')} />
        ) : (
          <>
            <Tabs tab={tab} setTab={setTab} />
            {stack.map((e, i) => (
              <StackScreen key={e.key} top={i === stack.length - 1} modal={e.route.name === 'scanner'}>
                {screenFor(e.route)}
              </StackScreen>
            ))}
          </>
        )}
      </View>
    </AppContext.Provider>
  );
}

function screenFor(r: Route) {
  switch (r.name) {
    case 'scanner': return <Scanner />;
    case 'result': return <Result scanId={r.scanId} />;
    case 'compare': return <Compare scanId={r.scanId} />;
    case 'nomatch': return <NoMatch scanId={r.scanId} />;
    case 'profile': return <Profile id={r.id} scanId={r.scanId} />;
    case 'report': return <Report scanId={r.scanId} />;
    case 'settings': return <Settings />;
    default: return null;
  }
}

/** Slides in from the right (or up, for the camera) when pushed. Screens below stay mounted, so going back keeps their state. */
function StackScreen({ children, top, modal }: { children: React.ReactNode; top: boolean; modal?: boolean }) {
  const { c } = useApp();
  const p = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(p, { toValue: 1, duration: 280, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
  }, [p]);
  const { width, height } = Dimensions.get('window');
  const transform = modal
    ? [{ translateY: p.interpolate({ inputRange: [0, 1], outputRange: [height * 0.25, 0] }) }]
    : [{ translateX: p.interpolate({ inputRange: [0, 1], outputRange: [width * 0.3, 0] }) }];
  return (
    <Animated.View
      importantForAccessibility={top ? 'auto' : 'no-hide-descendants'}
      style={[StyleSheet.absoluteFill, { backgroundColor: c.bg, opacity: p, transform }]}>
      {children}
    </Animated.View>
  );
}

function Tabs({ tab, setTab }: { tab: Tab; setTab: (t: Tab) => void }) {
  const { c, t, push } = useApp();
  const insets = useSafeAreaInsets();
  const items: [Tab, IconName, string][] = [
    ['home', 'home', t('nav_home')],
    ['guide', 'book', t('nav_fish')],
    ['history', 'history', t('nav_history')],
  ];
  return (
    <View style={{ flex: 1 }}>
      <View style={{ flex: 1 }}>
        {tab === 'home' ? <Home /> : tab === 'guide' ? <Guide /> : <History />}
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', backgroundColor: c.surface, paddingBottom: insets.bottom, paddingLeft: 8, paddingRight: 92, borderTopLeftRadius: 28, borderTopRightRadius: 28, boxShadow: c.shadow }}>
        {items.map(([key, icon, label]) => {
          const on = tab === key;
          return (
            <Tap key={key} onPress={() => setTab(key)} accessibilityRole="tab" accessibilityState={{ selected: on }} style={{ flex: 1, alignItems: 'center', paddingVertical: 10, gap: 2 }}>
              <Icon name={icon} size={24} color={on ? c.ink : c.muted} stroke={on ? 2.4 : 2} />
              <Txt v="caption" color={on ? c.ink : c.muted} style={{ fontWeight: on ? '700' : '500' }} numberOfLines={1}>{label}</Txt>
            </Tap>
          );
        })}
      </View>
      {/* The scan button floats over the bar's right end, like Cal AI's "+". */}
      <Tap
        onPress={() => push({ name: 'scanner' })}
        accessibilityRole="button"
        accessibilityLabel={t('nav_scan')}
        scale={0.92}
        style={{ position: 'absolute', right: 18, bottom: insets.bottom + 14, width: 68, height: 68, borderRadius: 34, backgroundColor: c.primary, alignItems: 'center', justifyContent: 'center', boxShadow: '0px 10px 24px rgba(0,0,0,0.28)' }}>
        <Icon name="scan" size={30} color={c.onPrimary} />
      </Tap>
    </View>
  );
}

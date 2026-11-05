// First-run tips (after Duolingo's): the screen dims, one control shows through a spotlight, and a speech bubble
// says what it does. Tap anywhere for the next tip. Each tip shows once; Settings can bring them back.
import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Animated, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Key } from './i18n';
import { Txt, useApp } from './ui';
import { Pop } from './motion';

export type TipId = 'search' | 'language' | 'scan' | 'frame';

const text: Record<TipId, Key> = {
  search: 'tip_search',
  language: 'tip_language',
  scan: 'tip_scan',
  frame: 'tip_frame',
};

type Rect = { x: number; y: number; w: number; h: number };
type Shown = { id: TipId; rect: Rect; round: number };

type ViewRef = React.ComponentRef<typeof View>;
const targets = new Map<TipId, { ref: React.RefObject<ViewRef | null>; round: number }>();

/**
 * Marks a view as the target of a tip. Put the returned ref on a View with collapsable={false}
 * (Android drops plain wrapper views otherwise, and they can't be measured). [round] is the spotlight's corner radius;
 * leave it out for a pill / circle around the target.
 */
export function useTipTarget(id: TipId, round?: number) {
  const ref = useRef<ViewRef>(null);
  useEffect(() => {
    const entry = { ref, round: round ?? -1 };
    targets.set(id, entry);
    return () => { if (targets.get(id) === entry) targets.delete(id); };
  }, [id, round]);
  return ref;
}

const Coach = createContext<(ids: TipId[]) => void>(() => {});

/** Shows the given tips that haven't been seen yet, in order. Call once the screen has settled. */
export const useTips = () => useContext(Coach);

export function CoachProvider({ children }: { children: React.ReactNode }) {
  const { prefs, setPref } = useApp();
  const [queue, setQueue] = useState<Shown[]>([]);
  const seen = useRef(new Set<string>());
  seen.current = new Set((prefs.tips_seen ?? '').split(',').filter(Boolean));
  const busy = useRef(false);

  const start = useCallback(async (ids: TipId[]) => {
    if (busy.current) return;
    const todo = ids.filter(id => !seen.current.has(id) && targets.has(id));
    if (!todo.length) return;
    busy.current = true;
    const measured = await Promise.all(todo.map(id => new Promise<Shown | null>(resolve => {
      const tgt = targets.get(id);
      if (!tgt?.ref.current) return resolve(null);
      tgt.ref.current.measureInWindow((x, y, w, h) => resolve(w > 0 ? { id, rect: { x, y, w, h }, round: tgt.round } : null));
    })));
    const shown = measured.filter((s): s is Shown => !!s);
    if (shown.length) setQueue(shown);
    else busy.current = false;
  }, []);

  const next = () => {
    const [current, ...rest] = queue;
    seen.current.add(current.id);
    setPref('tips_seen', [...seen.current].join(','));
    setQueue(rest);
    if (!rest.length) busy.current = false;
  };

  return (
    <Coach.Provider value={start}>
      {children}
      {queue.length > 0 && <Spotlight key={queue[0].id} tip={queue[0]} onNext={next} />}
    </Coach.Provider>
  );
}

const PAD = 10; // spotlight margin around the target
const SCRIM = 'rgba(0, 0, 0, 0.6)';

function Spotlight({ tip, onNext }: { tip: Shown; onNext: () => void }) {
  const { t } = useApp();
  const { width, height } = useWindowDimensions();
  const fade = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(fade, { toValue: 1, duration: 220, useNativeDriver: true }).start();
  }, [fade]);
  // Taps in the first half-second don't count: a tap still landing from the screen below, or a quick double tap,
  // would otherwise dismiss a tip before it could be read (and mark it seen).
  const shownAt = useRef(Date.now()).current;
  const press = () => { if (Date.now() - shownAt > 500) onNext(); };

  const { x, y, w, h } = tip.rect;
  const hole = { x: x - PAD, y: y - PAD, w: w + PAD * 2, h: h + PAD * 2 };
  const radius = tip.round >= 0 ? tip.round + PAD : Math.min(hole.w, hole.h) / 2;
  // The scrim is one huge border around the hole: its inside edge is the spotlight's outline.
  const B = Math.max(width, height) * 2;

  // Bubble below the target when it sits in the top half, above it otherwise; kept on screen, arrow under the target.
  const below = y + h / 2 < height / 2;
  const bubbleW = Math.min(300, width - 32);
  const left = Math.max(16, Math.min(width - bubbleW - 16, x + w / 2 - bubbleW / 2));
  const arrowX = Math.max(20, Math.min(bubbleW - 20, x + w / 2 - left));
  const gap = 14;

  return (
    <Animated.View style={[StyleSheet.absoluteFill, { opacity: fade }]}>
      <Pressable style={StyleSheet.absoluteFill} onPress={press} accessibilityRole="button" accessibilityLabel={t(text[tip.id])} accessibilityHint={t('tip_continue')}>
        <View pointerEvents="none" style={{
          position: 'absolute', left: hole.x - B, top: hole.y - B, width: hole.w + B * 2, height: hole.h + B * 2,
          borderWidth: B, borderRadius: B + radius, borderColor: SCRIM,
        }} />
        <View pointerEvents="none" style={{ position: 'absolute', left: hole.x, top: hole.y, width: hole.w, height: hole.h, borderRadius: radius, borderWidth: 2, borderColor: 'rgba(255,255,255,0.9)' }} />

        <Pop delay={120} style={{ position: 'absolute', left, width: bubbleW, ...(below ? { top: hole.y + hole.h + gap } : { bottom: height - hole.y + gap }) }}>
          <View style={{ position: 'absolute', left: arrowX - 8, width: 16, height: 16, backgroundColor: '#fff', transform: [{ rotate: '45deg' }], ...(below ? { top: -7 } : { bottom: -7 }) }} />
          <View style={{ backgroundColor: '#fff', borderRadius: 20, paddingHorizontal: 20, paddingVertical: 16, boxShadow: '0px 8px 24px rgba(0,0,0,0.25)' }}>
            <Txt v="title" color="#111" style={{ textAlign: 'center', fontWeight: '600' }}>{t(text[tip.id])}</Txt>
            <Txt v="caption" color="#6B6B6B" style={{ textAlign: 'center', marginTop: 4 }}>{t('tip_continue')}</Txt>
          </View>
        </Pop>
      </Pressable>
    </Animated.View>
  );
}

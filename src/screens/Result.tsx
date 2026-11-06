import React, { useContext, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Animated, Dimensions, Easing, Image, PanResponder, ScrollView, Share, StatusBar, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FishID, fileUri, Scan } from '../native';
import { percent, rupees } from '../i18n';
import { Bar, Btn, Card, Chip, CircleBtn, FishImage, Icon, IconName, Scientific, Tap, Txt, useApp } from '../ui';
import { Awake, CountUp, Pop, Reveal, stagger } from '../motion';

const useScan = (scanId: string): Scan | undefined => useApp().history.find(s => s.id === scanId);

/** Leaves a result screen: back to a still-open scanner (re-armed), else wherever we came from. */
function useLeave() {
  const { pop } = useApp();
  return () => { FishID.scanAgain(); pop(); };
}

/**
 * Matched: the user's photo, dimmed, with a white sheet sliding up over it. Match level as a small spaced label,
 * the fish's name large, then the price per kg with a ruler showing where it sits among the guide's prices.
 */
export function Result({ scanId }: { scanId: string }) {
  const { c, t, lang, fish, fishById, versions, push, home } = useApp();
  const insets = useSafeAreaInsets();
  const leave = useLeave();
  const scan = useScan(scanId);
  const f = scan?.species_id ? fishById[scan.species_id] : undefined;

  const rise = useRef(new Animated.Value(0)).current;
  const [sheetH, setSheetH] = useState(0);
  const [rootH, setRootH] = useState(Dimensions.get('window').height);
  const [img, setImg] = useState<{ w: number; h: number } | null>(null);
  useEffect(() => {
    if (scan) Image.getSize(fileUri(scan.photo), (w, h) => setImg({ w, h }), () => setImg(null));
  }, [scan?.photo]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    Animated.spring(rise, { toValue: 1, speed: 12, bounciness: 4, useNativeDriver: true }).start();
  }, [rise]);

  // Swiping up morphs this screen into the species page (Profile.tsx): the sheet rises to where the page's sheet
  // starts and turns its colour, the photo fills the page's header band. The page then fades in over an identical frame.
  // [drag] is the sheet's offset; [morph] (0..1) is derived from it, so the finger scrubs the morph.
  const heroH = Dimensions.get('window').width * 0.92; // Profile's header photo height
  const openAt = Math.min(0, heroH - 32 - (rootH - sheetH)); // sheet offset where its top meets Profile's sheet
  const drag = useRef(new Animated.Value(0)).current;
  const morph = drag.interpolate({ inputRange: [Math.min(openAt, -1), 0], outputRange: [1, 0], extrapolate: 'clamp' });
  const fadeOut = morph.interpolate({ inputRange: [0, 1], outputRange: [1, 0] });
  const settle = (toValue: number, then?: () => void, duration = 220) =>
    Animated.timing(drag, { toValue, duration, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start(then);
  // The page is pushed right away (hidden) so it builds while the morph runs on the native thread; it fades in as the morph lands.
  const openDetails = () => {
    if (!f) return;
    const ms = 240;
    settle(openAt, undefined, ms);
    push({ name: 'profile', id: f.id, scanId, morph: Date.now() + ms });
  };
  // Back from the species page: the sheet is in place again before the first frame shows.
  const awake = useContext(Awake);
  useLayoutEffect(() => { if (awake) drag.setValue(0); }, [awake, drag]);
  const pan = useMemo(() => PanResponder.create({
    // Vertical moves only, so taps on the buttons inside still work.
    onMoveShouldSetPanResponder: (_, g) => Math.abs(g.dy) > 8 && Math.abs(g.dy) > Math.abs(g.dx),
    onPanResponderMove: (_, g) => drag.setValue(Math.max(g.dy, openAt - 40)), // a little give past fully open
    onPanResponderRelease: (_, g) => {
      const far = Math.max(sheetH, 300) / 3;
      if (g.dy < Math.max(openAt / 3, -far) || g.vy < -0.8) openDetails();
      else if (g.dy > far || g.vy > 0.8) settle(Math.max(sheetH, 600), leave);
      else Animated.spring(drag, { toValue: 0, bounciness: 6, useNativeDriver: true }).start();
    },
    onPanResponderTerminate: () => settle(0),
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }), [drag, sheetH, openAt, f?.id, scanId]);

  if (!scan || !f) return null;

  // One photo that moves and scales from "whole, above the sheet" to "covering the species page's header band",
  // so the morph never shows two copies. Laid out at its end size; the transform shrinks it back to the start.
  let photoMorph = null;
  if (img && sheetH > 0) {
    const W = Dimensions.get('window').width;
    const boxH = rootH - sheetH + 32 - insets.top;
    const s = Math.min(W / img.w, boxH / img.h); // contain, above the sheet
    const e = Math.max(W / img.w, heroH / img.h); // cover, Profile's header band
    const end = { w: img.w * e, h: img.h * e };
    const endX = (W - end.w) / 2, endY = (heroH - end.h) / 2;
    const startCx = W / 2, startCy = insets.top + boxH / 2;
    const to = (from: number) => morph.interpolate({ inputRange: [0, 1], outputRange: [from, 0] });
    photoMorph = {
      position: 'absolute' as const, left: endX, top: endY, width: end.w, height: end.h,
      transform: [
        { translateX: to(startCx - (endX + end.w / 2)) },
        { translateY: to(startCy - (endY + end.h / 2)) },
        { scale: morph.interpolate({ inputRange: [0, 1], outputRange: [s / e, 1] }) },
      ],
    };
  }

  const conf = scan.candidates.find(x => x.species_id === f.id)?.confidence ?? 0;
  const high = conf >= f.threshold + (versions?.high_margin ?? 0.1);
  const price = f.price_inr_per_kg;
  const prices = fish.flatMap(x => (x.price_inr_per_kg != null ? [x.price_inr_per_kg] : []));
  const low = Math.min(...prices);
  const top = Math.max(...prices);
  const place = price == null ? null : top > low ? (price - low) / (top - low) : 0.5;
  const share = () => Share.share({ message: t('profile_share_text', { name: f.primary_name, scientific_name: f.scientific_name }) });

  return (
    <View style={{ flex: 1, backgroundColor: '#000' }} onLayout={e => setRootH(e.nativeEvent.layout.height)}>
      <StatusBar barStyle="light-content" />
      {/* The user's photo shown whole above the sheet (cover on a tall screen zooms in a lot),
          with a blurred copy filling the rest of the screen. */}
      <Image source={{ uri: fileUri(scan.photo) }} blurRadius={25} resizeMode="cover" style={StyleSheet.absoluteFill} />
      {photoMorph ? (
        <Animated.Image source={{ uri: fileUri(scan.photo) }} style={photoMorph} />
      ) : sheetH > 0 && (
        <Image source={{ uri: fileUri(scan.photo) }} resizeMode="contain"
          style={{ position: 'absolute', left: 0, right: 0, top: insets.top, bottom: sheetH - 32 }} />
      )}
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: 'rgba(0,0,0,0.38)', opacity: fadeOut }]} />

      <Animated.View style={{ position: 'absolute', top: insets.top + 8, left: 16, opacity: fadeOut }}>
        <CircleBtn icon="close" onPhoto label={t('action_done')} onPress={() => home()} />
      </Animated.View>

      <Animated.View
        {...pan.panHandlers}
        onLayout={e => setSheetH(e.nativeEvent.layout.height)}
        style={{
          position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: c.surface,
          borderTopLeftRadius: 32, borderTopRightRadius: 32, paddingHorizontal: 24, paddingBottom: insets.bottom + 16,
          transform: [{ translateY: Animated.add(rise.interpolate({ inputRange: [0, 1], outputRange: [500, 0] }), drag) }],
        }}>
        {/* Fills below the sheet while it's dragged up, so the photo never shows underneath. */}
        <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, top: '100%', height: 3000, backgroundColor: c.surface }} />
        <View style={{ alignSelf: 'center', width: 40, height: 5, borderRadius: 3, backgroundColor: c.line, marginTop: 10 }} />
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 8, marginHorizontal: -8 }}>
          <IconBtn icon="share" label={t('action_share')} onPress={share} />
          <IconBtn icon="flag" label={t('profile_report_link')} onPress={() => push({ name: 'report', scanId })} />
        </View>

        <Reveal delay={150} style={{ alignItems: 'center' }}>
          {/* Spaced capitals only in English: letter spacing pulls Tamil's joined letters apart. */}
          <Txt v="label" color={high ? c.success : c.warn}
            style={{ fontWeight: '700', textAlign: 'center', ...(lang === 'en' && { letterSpacing: 3, textTransform: 'uppercase' }) }}>
            {t('result_match_percent', { label: t(high ? 'result_match_high' : 'result_match_likely'), percent: percent(conf) })}
          </Txt>
          <Txt v="hero" style={{ fontSize: 36, lineHeight: 44, textAlign: 'center', marginTop: 2 }} accessibilityRole="header">{f.primary_name}</Txt>
        </Reveal>
        <Reveal delay={230} style={{ alignItems: 'center', marginTop: 10 }}>
          {f.secondary_name ? <Txt v="title" muted style={{ textAlign: 'center', fontWeight: '500' }}>{f.secondary_name}</Txt> : null}
          <Scientific style={{ textAlign: 'center' }}>{f.scientific_name}</Scientific>
        </Reveal>

        <Reveal delay={320} style={{ alignItems: 'center', marginTop: 22 }}>
          {price != null ? (
            <>
              <CountUp value={price} format={rupees} v="hero" style={{ fontSize: 52, lineHeight: 60 }} />
              <Txt v="label" style={{ fontWeight: '700' }}>{t('price_unit_per_kg')}</Txt>
            </>
          ) : (
            <Txt v="title" muted>{t('price_unavailable')}</Txt>
          )}
        </Reveal>
        {place != null && prices.length > 1 && <PriceRuler place={place} />}
        <Txt v="caption" muted style={{ textAlign: 'center', marginTop: 8 }}>{t('price_note')}</Txt>

        {/* Side by side, as in the reference, at fixed 2:3 widths in every language; long Tamil labels shrink or wrap inside them. */}
        <View style={{ flexDirection: 'row', gap: 12, marginTop: 22 }}>
          <Btn label={t('result_scan_again')} kind="outline" onPress={leave} style={{ flex: 2, paddingHorizontal: 12 }} />
          <Btn label={t('result_view_details')} onPress={openDetails} style={{ flex: 3, paddingHorizontal: 12 }} />
        </View>
        {/* Morph: the sheet turns into the species page's (blank) sheet; its text arrives with the page. */}
        <Animated.View pointerEvents="none" style={{
          position: 'absolute', left: 0, right: 0, top: 0, height: 3000, opacity: morph,
          backgroundColor: c.bg, borderTopLeftRadius: 32, borderTopRightRadius: 32,
        }} />
      </Animated.View>
    </View>
  );
}

/** Bare icon button for the sheet's top corners. */
function IconBtn({ icon, label, onPress }: { icon: IconName; label: string; onPress: () => void }) {
  const { c } = useApp();
  return (
    <Tap onPress={onPress} accessibilityRole="button" accessibilityLabel={label} hitSlop={8} style={{ padding: 8 }}>
      <Icon name={icon} size={24} color={c.ink} />
    </Tap>
  );
}

const TICKS = 29;

/** Ticks from the guide's cheapest fish to its priciest; a dark marker slides to where this fish's price sits. */
function PriceRuler({ place }: { place: number }) {
  const { c, t } = useApp();
  const [w, setW] = useState(0);
  const p = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (w) Animated.timing(p, { toValue: place, duration: 900, delay: 350, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
  }, [p, place, w]);
  return (
    <View style={{ marginTop: 14 }} accessible accessibilityLabel={`${t('price_scale_low')} – ${t('price_scale_high')}`}>
      <View onLayout={e => setW(e.nativeEvent.layout.width)} style={{ height: 36, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        {Array.from({ length: TICKS }, (_, i) => <View key={i} style={{ width: 1.5, height: 16, backgroundColor: c.muted, opacity: 0.45 }} />)}
        {w > 0 && (
          <Animated.View style={{
            position: 'absolute', left: 0, width: 3, height: 36, borderRadius: 2, backgroundColor: c.ink,
            transform: [{ translateX: p.interpolate({ inputRange: [0, 1], outputRange: [-0.75, w - 2.25] }) }],
          }} />
        )}
      </View>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 2 }}>
        <Txt v="caption" muted>{t('price_scale_low')}</Txt>
        <Txt v="caption" muted>{t('price_scale_high')}</Txt>
      </View>
    </View>
  );
}

/** Low confidence: up to three candidates to compare by eye (field marks "used to visually confirm a match"). */
export function Compare({ scanId }: { scanId: string }) {
  const { c, t, fishById, push, pop, reloadHistory } = useApp();
  const insets = useSafeAreaInsets();
  const leave = useLeave();
  const scan = useScan(scanId);
  if (!scan) return null;
  const candidates = scan.candidates.flatMap(x => (fishById[x.species_id] ? [{ f: fishById[x.species_id], conf: x.confidence }] : []));

  const choose = async (id: string) => {
    await FishID.pickCandidate(scanId, id);
    reloadHistory();
    pop();
    push({ name: 'profile', id, scanId });
  };

  return (
    <ScrollView contentContainerStyle={{ paddingTop: insets.top + 8, paddingHorizontal: 20, paddingBottom: insets.bottom + 24 }}>
      <CircleBtn icon="back" label={t('action_back')} onPress={leave} />
      <View style={{ flexDirection: 'row', gap: 16, alignItems: 'center', marginTop: 16 }}>
        <FishImage uri={fileUri(scan.photo)} style={{ width: 96, height: 96 }} radius={24} />
        <View style={{ flex: 1 }}>
          <Chip label={t('label_your_photo')} />
          <Txt v="headline" style={{ marginTop: 6 }}>{t('low_confidence_title')}</Txt>
        </View>
      </View>
      <Txt muted style={{ marginTop: 10 }}>{t('low_confidence_body')}</Txt>

      <View style={{ gap: 14, marginTop: 18 }}>
        {candidates.map(({ f, conf }, i) => (
          <Reveal key={f.id} delay={120 + stagger(i, 90)}>
          <Card style={{ padding: 14 }}>
            <View style={{ flexDirection: 'row', gap: 14 }}>
              <FishImage uri={f.image_uris[0]} style={{ width: 110, height: 110 }} radius={18} />
              <View style={{ flex: 1, gap: 2 }}>
                <Txt v="title" numberOfLines={2}>{f.primary_name}</Txt>
                {f.secondary_name ? <Txt v="caption" muted numberOfLines={1}>{f.secondary_name}</Txt> : null}
                <Scientific style={{ fontSize: 13 }}>{f.scientific_name}</Scientific>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 6 }}>
                  <View style={{ flex: 1 }}><Bar value={conf} color={c.marigold} height={6} /></View>
                  <Txt v="label" style={{ fontWeight: '700' }}>{`${percent(conf)}%`}</Txt>
                </View>
              </View>
            </View>
            {f.field_marks.slice(0, 3).map(m => (
              <View key={m} style={{ flexDirection: 'row', gap: 8, marginTop: 8 }}>
                <Icon name="check" size={18} color={c.accent} stroke={2.6} />
                <Txt v="label" style={{ flex: 1 }}>{m}</Txt>
              </View>
            ))}
            <Btn label={t('low_confidence_pick')} small onPress={() => choose(f.id)} style={{ marginTop: 14 }} />
          </Card>
          </Reveal>
        ))}
      </View>
      <Btn label={t('low_confidence_none')} kind="outline" onPress={() => { pop(); push({ name: 'report', scanId }); }} style={{ marginTop: 18 }} />
    </ScrollView>
  );
}

/** Nothing recognised: retake tips, try again, or report. */
export function NoMatch({ scanId }: { scanId: string }) {
  const { c, t, push, pop } = useApp();
  const insets = useSafeAreaInsets();
  const leave = useLeave();
  const scan = useScan(scanId);
  const tips = ['no_match_tip_whole', 'no_match_tip_side', 'no_match_tip_light', 'no_match_tip_background'] as const;
  return (
    <ScrollView contentContainerStyle={{ paddingTop: insets.top + 8, paddingHorizontal: 20, paddingBottom: insets.bottom + 24 }}>
      <CircleBtn icon="back" label={t('action_back')} onPress={leave} />
      <View style={{ alignItems: 'center', marginTop: 12 }}>
        <View>
          <FishImage uri={scan ? fileUri(scan.photo) : null} style={{ width: 180, height: 180 }} radius={36} />
          <Pop delay={250} style={{ position: 'absolute', right: -8, bottom: -8, width: 52, height: 52, borderRadius: 26, backgroundColor: c.warnSoft, borderWidth: 4, borderColor: c.bg, alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="info" size={24} color={c.warn} />
          </Pop>
        </View>
        <Txt v="display" style={{ textAlign: 'center', marginTop: 20 }}>{t('no_match_title')}</Txt>
      </View>
      <Card style={{ marginTop: 20, gap: 14 }}>
        {tips.map((k, i) => (
          <Reveal key={k} delay={200 + i * 70} from={10} style={{ flexDirection: 'row', gap: 12, alignItems: 'center' }}>
            <View style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="check" size={18} color={c.accent} stroke={2.6} />
            </View>
            <Txt style={{ flex: 1 }}>{t(k)}</Txt>
          </Reveal>
        ))}
      </Card>
      <Btn label={t('action_retry')} icon="scan" onPress={leave} style={{ marginTop: 20 }} />
      {scan && <Btn label={t('profile_report_link')} kind="ghost" icon="flag" onPress={() => { pop(); push({ name: 'report', scanId }); }} />}
    </ScrollView>
  );
}

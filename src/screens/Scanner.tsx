import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Animated, AppState, Easing, Linking, PermissionsAndroid, StatusBar, StyleSheet, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CameraView, FishID, useScanState } from '../native';
import { Btn, CircleBtn, Icon, IconName, Tap, Txt, useApp } from '../ui';

const CAMERA = PermissionsAndroid.PERMISSIONS.CAMERA;
/** The frame is the model's crop: FRAME_FRACTION of the screen width (ml/ScanLogic.kt) at BRACKET_ASPECT 4:3 (ScanEngine.kt). */
const FRAME_FRACTION = 0.84;
const FRAME_ASPECT = 4 / 3;
/** Share of the screen height the frame sits above centre, in the open space between top bar and controls (FRAME_SHIFT in ScanLogic.kt). */
const FRAME_SHIFT = 0.06;

export default function Scanner() {
  const { pop, prefs, setPref } = useApp();
  const [granted, setGranted] = useState<boolean | null>(null);
  const [blocked, setBlocked] = useState(false);

  useEffect(() => {
    const check = () => PermissionsAndroid.check(CAMERA).then(setGranted);
    check();
    const sub = AppState.addEventListener('change', s => s === 'active' && check()); // back from system settings
    return () => sub.remove();
  }, []);

  const ask = async () => {
    const r = await PermissionsAndroid.request(CAMERA);
    setGranted(r === 'granted');
    setBlocked(r === 'never_ask_again'); // the system dialog won't show again: offer settings instead
  };

  // Results (camera or gallery) are routed by App; returning here re-arms the scanner.
  useEffect(() => () => FishID.setTorch(false), []);

  return (
    <View style={{ flex: 1, backgroundColor: '#000' }}>
      <StatusBar barStyle="light-content" />
      {granted === null ? null : granted ? (
        <Viewfinder onBack={pop} showTapNotice={prefs.tap_notice_shown !== 'true'} onTapNoticeSeen={() => setPref('tap_notice_shown', 'true')} />
      ) : (
        <Permission blocked={blocked} onAllow={blocked ? () => Linking.openSettings() : ask} onBack={pop} />
      )}
    </View>
  );
}

function Viewfinder({ onBack, showTapNotice, onTapNoticeSeen }: { onBack: () => void; showTapNotice: boolean; onTapNoticeSeen: () => void }) {
  const { t } = useApp();
  const insets = useSafeAreaInsets();
  const [state, cameraError] = useScanState();
  const [torch, setTorch] = useState(false);
  const [picking, setPicking] = useState(false);

  const live = state?.live ?? true;
  const holding = state?.holding ?? false;
  const identifying = (state?.identifying ?? false) || picking;
  const mode: FrameMode = cameraError ? 'tap' : identifying ? 'busy' : holding ? 'hold' : live ? 'search' : 'tap';
  const hint = cameraError ? t('scan_camera_error') : identifying ? t('scan_identifying') : !live ? t('scan_hint_tap') : holding ? t('scan_hint_hold_still') : t('scan_hint_frame');

  const pick = async () => {
    setPicking(true);
    await FishID.pickPhoto().catch(() => false);
    setPicking(false);
  };

  return (
    <View style={{ flex: 1 }}>
      <CameraView style={StyleSheet.absoluteFill} />
      <ScanFrame mode={mode} hint={hint} />

      {/* Top: back · title. The live/tap mode lives in the tray, where long Tamil labels have room. */}
      <View style={{ position: 'absolute', top: insets.top + 8, left: 16, right: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        {/* Title centred on the screen, not in the space left between the unequal side controls. */}
        <View style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center' }]} pointerEvents="none">
          <Txt v="title" color="#fff">{t('scanner_title')}</Txt>
        </View>
        <CircleBtn icon="back" onPhoto label={t('action_back')} onPress={onBack} />
      </View>

      <View style={{ position: 'absolute', left: 0, right: 0, bottom: insets.bottom + 20, alignItems: 'center', gap: 18, paddingHorizontal: 20 }}>
        {state?.switchedToTap && showTapNotice && (
          <Tap onPress={() => { onTapNoticeSeen(); FishID.dismissTapNotice(); }} style={[glass, { borderRadius: 20, padding: 14, flexDirection: 'row', gap: 10, alignItems: 'center' }]}>
            <Icon name="info" size={20} color="#fff" />
            <Txt v="label" color="#fff" style={{ flex: 1 }}>{t('scan_switched_to_tap')}</Txt>
            <Icon name="close" size={18} color="#fff" />
          </Tap>
        )}

        {/* Tray (Cal AI's "Scan food" strip): scan mode, gallery, flash */}
        <View style={[glass, { flexDirection: 'row', alignItems: 'center', padding: 5, gap: 4, maxWidth: '100%' }]}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: '#fff', borderRadius: 999, paddingHorizontal: 16, minHeight: 42, flexShrink: 1 }}>
            <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: live ? LOCK_GREEN : '#111' }} />
            <Txt v="label" color="#111" numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7} style={{ fontWeight: '700', flexShrink: 1 }}>{t(live ? 'scan_mode_live' : 'scan_mode_tap')}</Txt>
          </View>
          <TrayBtn icon="gallery" label={t('scan_gallery_description')} onPress={pick} disabled={identifying} />
          {state?.hasFlash && (
            <TrayBtn icon="flash" active={torch} label={t(torch ? 'scan_flash_off_description' : 'scan_flash_on_description')} onPress={() => { FishID.setTorch(!torch); setTorch(!torch); }} />
          )}
        </View>

        <Tap
          onPress={() => FishID.shutter()}
          disabled={identifying}
          scale={0.9}
          accessibilityRole="button"
          accessibilityLabel={t('scan_shutter_description')}
          style={{ width: 84, height: 84, borderRadius: 42, borderWidth: 4, borderColor: 'rgba(255,255,255,0.9)', alignItems: 'center', justifyContent: 'center' }}>
          <View style={{ width: 66, height: 66, borderRadius: 33, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' }}>
            {identifying ? <ActivityIndicator color="#111" /> : <Icon name="scan" size={28} color="#111" />}
          </View>
        </Tap>
      </View>
    </View>
  );
}

const LOCK_GREEN = '#3DDC84';
const BEAM = '#8EEBF5';
const RADIUS = 26;
const CORNER = 44;
/** Frosted-glass surface for controls over the camera. */
const glass = { backgroundColor: 'rgba(20,20,24,0.42)', borderRadius: 999, borderWidth: 1, borderColor: 'rgba(255,255,255,0.18)' } as const;

function TrayBtn({ icon, label, onPress, active, disabled }: { icon: IconName; label: string; onPress: () => void; active?: boolean; disabled?: boolean }) {
  return (
    <Tap onPress={onPress} disabled={disabled} accessibilityRole="button" accessibilityLabel={label}
      style={{ width: 42, height: 42, borderRadius: 21, alignItems: 'center', justifyContent: 'center', backgroundColor: active ? '#fff' : 'transparent', opacity: disabled ? 0.5 : 1 }}>
      <Icon name={icon} size={20} color={active ? '#111' : '#fff'} />
    </Tap>
  );
}

type FrameMode = 'search' | 'hold' | 'tap' | 'busy';

/**
 * Exactly the area the model sees (ScanEngine crops to it). Soft scrim outside, thin rounded corners that breathe
 * while searching, a light beam sweeping through, and a green lock with a check when the fish holds still.
 */
function ScanFrame({ mode, hint }: { mode: FrameMode; hint: string }) {
  const { width, height } = useWindowDimensions();
  const w = width * FRAME_FRACTION;
  const h = w / FRAME_ASPECT;
  const scrim = 2000; // border thick enough to cover the screen around the cut-out
  const color = mode === 'hold' ? LOCK_GREEN : '#FFFFFF';

  const sweep = useRef(new Animated.Value(0)).current;
  const breathe = useRef(new Animated.Value(0)).current;
  const lock = useRef(new Animated.Value(0)).current;
  const pulse = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    if (mode !== 'search') return;
    sweep.setValue(0);
    const loops = [
      Animated.loop(Animated.sequence([
        Animated.timing(sweep, { toValue: 1, duration: 2400, easing: Easing.inOut(Easing.cubic), useNativeDriver: true }),
        Animated.delay(250),
      ])),
      Animated.loop(Animated.sequence([
        Animated.timing(breathe, { toValue: 1, duration: 1300, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
        Animated.timing(breathe, { toValue: 0, duration: 1300, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
      ])),
    ];
    loops.forEach(l => l.start());
    return () => { loops.forEach(l => l.stop()); breathe.setValue(0); };
  }, [mode, sweep, breathe]);
  useEffect(() => {
    Animated.spring(lock, { toValue: mode === 'hold' ? 1 : 0, useNativeDriver: true, speed: 16, bounciness: 12 }).start();
  }, [mode, lock]);
  useEffect(() => {
    if (mode !== 'busy') return pulse.setValue(1);
    const loop = Animated.loop(Animated.sequence([
      Animated.timing(pulse, { toValue: 0.4, duration: 500, useNativeDriver: true }),
      Animated.timing(pulse, { toValue: 1, duration: 500, useNativeDriver: true }),
    ]));
    loop.start();
    return () => loop.stop();
  }, [mode, pulse]);

  // Each corner drifts out a few px and back while searching, and snaps inward on lock.
  const drift = (sign: number) => Animated.add(
    breathe.interpolate({ inputRange: [0, 1], outputRange: [0, sign * 5] }),
    lock.interpolate({ inputRange: [0, 1], outputRange: [0, -sign * 8] }),
  );
  const radiusKey = { tl: 'borderTopLeftRadius', tr: 'borderTopRightRadius', bl: 'borderBottomLeftRadius', br: 'borderBottomRightRadius' } as const;
  const corner = (k: 'tl' | 'tr' | 'bl' | 'br') => {
    const top = k[0] === 't';
    const left = k[1] === 'l';
    return (
      <Animated.View
        key={k}
        style={{
          position: 'absolute', width: CORNER, height: CORNER, borderColor: color, [radiusKey[k]]: RADIUS,
          top: top ? 0 : undefined, bottom: top ? undefined : 0, left: left ? 0 : undefined, right: left ? undefined : 0,
          borderTopWidth: top ? 4 : 0, borderBottomWidth: top ? 0 : 4, borderLeftWidth: left ? 4 : 0, borderRightWidth: left ? 0 : 4,
          transform: [{ translateX: drift(left ? -1 : 1) }, { translateY: drift(top ? -1 : 1) }],
        }}
      />
    );
  };

  return (
    <View style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center', transform: [{ translateY: -height * FRAME_SHIFT }] }]} pointerEvents="none">
      <View style={{ position: 'absolute', width: w + scrim * 2, height: h + scrim * 2, borderWidth: scrim, borderRadius: scrim + RADIUS, borderColor: 'rgba(0,0,0,0.38)' }} />

      {/* Status pill sits just below the frame, where the eye already is. */}
      <View style={[glass, { position: 'absolute', top: '50%', marginTop: h / 2 + 20, borderRadius: 20, flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 14, paddingVertical: 8, minHeight: 38, maxWidth: w }]}>
        {mode === 'busy' ? <ActivityIndicator size="small" color="#fff" /> : <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: mode === 'hold' ? LOCK_GREEN : mode === 'search' ? BEAM : '#fff' }} />}
        <Txt v="label" color="#fff" numberOfLines={2} style={{ flexShrink: 1, fontWeight: '600', textAlign: 'center' }}>{hint}</Txt>
      </View>

      <Animated.View style={{ width: w, height: h, opacity: pulse }}>
        <View style={[StyleSheet.absoluteFill, { borderRadius: RADIUS, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' }]}>
          {/* Guide: a faint sideways fish shows how to frame it. */}
          {mode === 'search' && <View style={{ opacity: 0.16 }}><Icon name="fish" size={Math.round(w * 0.4)} color="#FFFFFF" stroke={1} /></View>}
          <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: LOCK_GREEN, opacity: lock.interpolate({ inputRange: [0, 1], outputRange: [0, 0.14] }) }]} />
          {mode === 'search' && (
            <Animated.View
              style={{
                position: 'absolute', left: 0, right: 0, top: 0, height: 90,
                opacity: sweep.interpolate({ inputRange: [0, 0.1, 0.85, 1], outputRange: [0, 1, 1, 0] }),
                transform: [{ translateY: sweep.interpolate({ inputRange: [0, 1], outputRange: [-88, h - 2] }) }],
              }}>
              <View style={{ flex: 1, backgroundImage: 'linear-gradient(to bottom, rgba(142,235,245,0), rgba(142,235,245,0.22))' }} />
              <View style={{ height: 2, backgroundImage: 'linear-gradient(to right, rgba(142,235,245,0), #E8FDFF, rgba(142,235,245,0))', boxShadow: `0px 0px 12px 2px ${BEAM}` }} />
            </Animated.View>
          )}
        </View>
        {(['tl', 'tr', 'bl', 'br'] as const).map(corner)}
        {/* Lock badge pops onto the top-right corner, leaving the fish visible. */}
        <Animated.View style={{ position: 'absolute', top: -14, right: -14, width: 40, height: 40, borderRadius: 20, backgroundColor: LOCK_GREEN, alignItems: 'center', justifyContent: 'center', opacity: lock, transform: [{ scale: lock }] }}>
          <Icon name="check" size={22} color="#0B2A14" stroke={3} />
        </Animated.View>
      </Animated.View>
    </View>
  );
}

/** Explains why before asking; after a permanent denial, sends the user to settings. Gallery always works. */
function Permission({ blocked, onAllow, onBack }: { blocked: boolean; onAllow: () => void; onBack: () => void }) {
  const { c, t, dark } = useApp();
  const insets = useSafeAreaInsets();
  const [picking, setPicking] = useState(false);
  return (
    <View style={{ flex: 1, backgroundColor: c.bg, paddingTop: insets.top + 8, paddingBottom: insets.bottom + 16, paddingHorizontal: 24 }}>
      <StatusBar barStyle={dark ? 'light-content' : 'dark-content'} />
      <CircleBtn icon="back" label={t('action_back')} onPress={onBack} />
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: 120, height: 120, borderRadius: 60, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center', marginBottom: 28 }}>
          <Icon name="camera" size={52} color={c.accent} />
        </View>
        <Txt v="display" style={{ textAlign: 'center' }}>{t('permission_camera_title')}</Txt>
        <Txt v="title" muted style={{ textAlign: 'center', marginTop: 10, fontWeight: '400' }}>{t('permission_camera_body')}</Txt>
      </View>
      <View style={{ gap: 10 }}>
        <Btn label={t(blocked ? 'permission_open_settings' : 'permission_allow')} icon="camera" onPress={onAllow} />
        <Btn
          label={t('permission_use_gallery')}
          icon="gallery"
          kind="outline"
          loading={picking}
          onPress={async () => { setPicking(true); await FishID.pickPhoto().catch(() => false); setPicking(false); }}
        />
      </View>
    </View>
  );
}

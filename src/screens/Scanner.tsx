import React, { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Animated, AppState, Easing, Linking, PermissionsAndroid, StatusBar, StyleSheet, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CameraView, FishID, useScanState } from '../native';
import { Btn, CircleBtn, Icon, IconName, Tap, Txt, useApp } from '../ui';
import { useTips, useTipTarget } from '../coach';

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

/** Minimal viewfinder (after Cal AI, pared down): close, four corners, one line of guidance, then flash · shutter · gallery. */
function Viewfinder({ onBack, showTapNotice, onTapNoticeSeen }: { onBack: () => void; showTapNotice: boolean; onTapNoticeSeen: () => void }) {
  const { t } = useApp();
  const insets = useSafeAreaInsets();
  const [state, cameraError] = useScanState();
  const [torch, setTorch] = useState(false);
  const [picking, setPicking] = useState(false);
  const flash = useRef(new Animated.Value(0)).current;
  const shoot = () => {
    // A quick white blink, like a camera shutter, so a tap visibly "took" the photo.
    flash.setValue(0.55);
    Animated.timing(flash, { toValue: 0, duration: 260, easing: Easing.out(Easing.quad), useNativeDriver: true }).start();
    FishID.shutter();
  };

  const live = state?.live ?? true;
  const holding = state?.holding ?? false;
  const identifying = (state?.identifying ?? false) || picking;
  const mode: FrameMode = cameraError ? 'tap' : identifying ? 'busy' : holding ? 'hold' : live ? 'search' : 'tap';
  // The hint also says when to press the shutter (tap mode), so no separate live/tap indicator is needed.
  const hint = cameraError ? t('scan_camera_error') : identifying ? t('scan_identifying') : !live ? t('scan_hint_tap') : holding ? t('scan_hint_hold_still') : t('scan_hint_frame');

  // First time in the scanner: point at the frame once the screen has slid up.
  const tips = useTips();
  useEffect(() => {
    const id = setTimeout(() => tips(['frame']), 800);
    return () => clearTimeout(id);
  }, [tips]);

  const pick = async () => {
    setPicking(true);
    await FishID.pickPhoto().catch(() => false);
    setPicking(false);
  };

  return (
    <View style={{ flex: 1 }}>
      <CameraView style={StyleSheet.absoluteFill} />
      <ScanFrame mode={mode} hint={hint} />
      <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: '#fff', opacity: flash }]} />

      <View style={{ position: 'absolute', top: insets.top + 8, left: 16 }}>
        <RoundBtn icon="close" label={t('action_back')} onPress={onBack} />
      </View>

      <View style={{ position: 'absolute', left: 0, right: 0, bottom: insets.bottom + 28, gap: 18, paddingHorizontal: 20 }}>
        {state?.switchedToTap && showTapNotice && (
          <Tap onPress={() => { onTapNoticeSeen(); FishID.dismissTapNotice(); }} style={{ backgroundColor: DARK, borderRadius: 18, padding: 14, flexDirection: 'row', gap: 10, alignItems: 'center' }}>
            <Icon name="info" size={20} color="#fff" />
            <Txt v="label" color="#fff" style={{ flex: 1 }}>{t('scan_switched_to_tap')}</Txt>
            <Icon name="close" size={18} color="#fff" />
          </Tap>
        )}
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 18 }}>
          {state?.hasFlash ? (
            <RoundBtn icon="flash" active={torch} label={t(torch ? 'scan_flash_off_description' : 'scan_flash_on_description')} onPress={() => { FishID.setTorch(!torch); setTorch(!torch); }} />
          ) : <View style={{ width: 52 }} />}
          <Tap
            onPress={shoot}
            disabled={identifying}
            scale={0.9}
            accessibilityRole="button"
            accessibilityLabel={t('scan_shutter_description')}
            style={{ width: 80, height: 80, borderRadius: 40, borderWidth: 3, borderColor: '#fff', alignItems: 'center', justifyContent: 'center' }}>
            <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: '#fff', alignItems: 'center', justifyContent: 'center' }}>
              {identifying && <ActivityIndicator color="#111" />}
            </View>
          </Tap>
          <RoundBtn icon="gallery" label={t('scan_gallery_description')} onPress={pick} disabled={identifying} />
        </View>
      </View>
    </View>
  );
}

const LOCK_GREEN = '#3DDC84';
const RADIUS = 26;
const CORNER = 44;
/** Cal AI's dark round controls over the camera. */
const DARK = 'rgba(28,28,30,0.72)';

function RoundBtn({ icon, label, onPress, active, disabled }: { icon: IconName; label: string; onPress: () => void; active?: boolean; disabled?: boolean }) {
  return (
    <Tap onPress={onPress} disabled={disabled} accessibilityRole="button" accessibilityLabel={label} hitSlop={6}
      style={{ width: 52, height: 52, borderRadius: 26, alignItems: 'center', justifyContent: 'center', backgroundColor: active ? '#fff' : DARK, opacity: disabled ? 0.5 : 1 }}>
      <Icon name={icon} size={22} color={active ? '#111' : '#fff'} />
    </Tap>
  );
}

type FrameMode = 'search' | 'hold' | 'tap' | 'busy';

/**
 * Exactly the area the model sees (ScanEngine crops to it), marked by four thin corners and nothing else.
 * They turn green and close in a little when the fish holds still, and pulse while identifying.
 */
function ScanFrame({ mode, hint }: { mode: FrameMode; hint: string }) {
  const frameTip = useTipTarget('frame', RADIUS);
  const { width, height } = useWindowDimensions();
  const w = width * FRAME_FRACTION;
  const h = w / FRAME_ASPECT;
  const color = mode === 'hold' ? LOCK_GREEN : '#FFFFFF';

  const lock = useRef(new Animated.Value(0)).current;
  const pulse = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    Animated.spring(lock, { toValue: mode === 'hold' ? 1 : 0, useNativeDriver: true, speed: 16, bounciness: 8 }).start();
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

  const inset = (sign: number) => lock.interpolate({ inputRange: [0, 1], outputRange: [0, -sign * 6] });
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
          borderTopWidth: top ? 3 : 0, borderBottomWidth: top ? 0 : 3, borderLeftWidth: left ? 3 : 0, borderRightWidth: left ? 0 : 3,
          transform: [{ translateX: inset(left ? -1 : 1) }, { translateY: inset(top ? -1 : 1) }],
        }}
      />
    );
  };

  return (
    <View style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center', transform: [{ translateY: -height * FRAME_SHIFT }] }]} pointerEvents="none">
      <View ref={frameTip} collapsable={false} pointerEvents="none" style={{ position: 'absolute', width: w, height: h }} />
      <Animated.View style={{ width: w, height: h, opacity: pulse }}>
        {(['tl', 'tr', 'bl', 'br'] as const).map(corner)}
      </Animated.View>
      {/* One line of guidance just below the frame, where the eye already is. */}
      {/* A fixed width (not maxWidth): an absolutely placed text sized to its content measured one line and cut the second in Tamil. */}
      <Txt v="label" color="#fff" numberOfLines={3}
        style={{ position: 'absolute', top: '50%', marginTop: h / 2 + 22, width: w, textAlign: 'center', fontWeight: '600', textShadowColor: 'rgba(0,0,0,0.6)', textShadowRadius: 6 }}>
        {hint}
      </Txt>
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

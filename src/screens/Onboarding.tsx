import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing, PermissionsAndroid, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Btn, Card, Icon, IconName, Tap, Txt, useApp } from '../ui';

// S1: language → Scan / Identify / Learn → camera permission (explained before the system dialog).
export default function Onboarding({ onDone }: { onDone: () => void }) {
  const { c, t, lang, setLang } = useApp();
  const insets = useSafeAreaInsets();
  const [step, setStep] = useState(0);
  const slides: [IconName, string, string, string][] = [
    ['scan', t('onboarding_scan_title'), t('onboarding_scan_body'), c.marigold],
    ['sparkle', t('onboarding_identify_title'), t('onboarding_identify_body'), c.accent],
    ['book', t('onboarding_learn_title'), t('onboarding_learn_body'), c.protein],
  ];
  const last = slides.length + 1; // language, 3 slides, camera

  const fade = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    fade.setValue(0);
    Animated.timing(fade, { toValue: 1, duration: 320, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
  }, [fade, step]);

  const next = () => (step < last ? setStep(step + 1) : onDone());
  const askCamera = async () => {
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CAMERA).catch(() => {});
    onDone();
  };

  let body: React.ReactNode;
  if (step === 0) {
    body = (
      <>
        <Art icon="globe" tint={c.accent} />
        <Txt v="display" style={{ textAlign: 'center' }}>{t('onboarding_language_title')}</Txt>
        <View style={{ gap: 12, marginTop: 28, alignSelf: 'stretch' }}>
          {(['en', 'ta'] as const).map(l => {
            const on = lang === l;
            return (
              <Card key={l} onPress={() => setLang(l)} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 20, borderWidth: 2, borderColor: on ? c.ink : 'transparent' }}>
                <Txt v="headline" style={{ flex: 1 }}>{l === 'en' ? t('language_english') : t('language_tamil')}</Txt>
                <View style={{ width: 28, height: 28, borderRadius: 14, backgroundColor: on ? c.primary : c.surfaceAlt, alignItems: 'center', justifyContent: 'center' }}>
                  {on && <Icon name="check" size={18} color={c.onPrimary} stroke={3} />}
                </View>
              </Card>
            );
          })}
        </View>
        <Txt muted style={{ textAlign: 'center', marginTop: 16 }}>{t('onboarding_language_hint')}</Txt>
      </>
    );
  } else if (step <= slides.length) {
    const [icon, title, text, tint] = slides[step - 1];
    body = (
      <>
        <Art icon={icon} tint={tint} />
        <Txt v="hero" style={{ textAlign: 'center' }}>{title}</Txt>
        <Txt v="title" muted style={{ textAlign: 'center', marginTop: 12, fontWeight: '400' }}>{text}</Txt>
      </>
    );
  } else {
    body = (
      <>
        <Art icon="camera" tint={c.success} />
        <Txt v="display" style={{ textAlign: 'center' }}>{t('permission_camera_title')}</Txt>
        <Txt v="title" muted style={{ textAlign: 'center', marginTop: 12, fontWeight: '400' }}>{t('permission_camera_body')}</Txt>
      </>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: c.bg, paddingTop: insets.top + 12, paddingBottom: insets.bottom + 16, paddingHorizontal: 24 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', height: 44 }}>
        <View style={{ flex: 1, flexDirection: 'row', gap: 6 }}>
          {Array.from({ length: last + 1 }, (_, i) => (
            <View key={i} style={{ height: 6, borderRadius: 3, width: i === step ? 26 : 6, backgroundColor: i <= step ? c.ink : c.line }} />
          ))}
        </View>
        {step > 0 && step < last && (
          <Tap onPress={() => setStep(last)} accessibilityRole="button" style={{ padding: 10 }}>
            <Txt v="label" muted>{t('onboarding_skip')}</Txt>
          </Tap>
        )}
      </View>
      <Animated.View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', opacity: fade, transform: [{ translateY: fade.interpolate({ inputRange: [0, 1], outputRange: [24, 0] }) }] }}>
        {body}
      </Animated.View>
      {step === last ? (
        <View style={{ gap: 8 }}>
          <Btn label={t('permission_allow')} icon="camera" onPress={askCamera} />
          <Btn label={t('onboarding_start')} kind="ghost" onPress={onDone} />
        </View>
      ) : (
        <Btn label={t('onboarding_next')} onPress={next} />
      )}
    </View>
  );
}

/** Soft glowing disc with a big icon (Pillowtalk-style illustration). */
function Art({ icon, tint }: { icon: IconName; tint: string }) {
  const pulse = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const loop = Animated.loop(Animated.sequence([
      Animated.timing(pulse, { toValue: 1, duration: 1800, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
      Animated.timing(pulse, { toValue: 0, duration: 1800, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
    ]));
    loop.start();
    return () => loop.stop();
  }, [pulse]);
  return (
    <View style={{ width: 220, height: 220, alignItems: 'center', justifyContent: 'center', marginBottom: 36 }}>
      <Animated.View style={{ position: 'absolute', width: 220, height: 220, borderRadius: 110, backgroundColor: tint + '1F', transform: [{ scale: pulse.interpolate({ inputRange: [0, 1], outputRange: [0.92, 1.04] }) }] }} />
      <View style={{ width: 150, height: 150, borderRadius: 75, backgroundColor: tint + '33', alignItems: 'center', justifyContent: 'center' }}>
        <View style={{ width: 96, height: 96, borderRadius: 48, backgroundColor: tint, alignItems: 'center', justifyContent: 'center' }}>
          <Icon name={icon} size={48} color="#fff" stroke={2.2} />
        </View>
      </View>
    </View>
  );
}

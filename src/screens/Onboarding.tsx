import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing, PermissionsAndroid, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Btn, Icon, Logo, Tap, Txt, useApp } from '../ui';
import { Pop } from '../motion';
import { font } from '../theme';

// Two screens, then learn by doing (the first-run tips on Home take over):
// 1. welcome + language, 2. camera permission, explained before the system dialog.
export default function Onboarding({ onDone }: { onDone: () => void }) {
  const { c, t } = useApp();
  const insets = useSafeAreaInsets();
  const [step, setStep] = useState(0);

  const fade = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    fade.setValue(0);
    Animated.timing(fade, { toValue: 1, duration: 320, easing: Easing.out(Easing.cubic), useNativeDriver: true }).start();
  }, [fade, step]);

  const askCamera = async () => {
    await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.CAMERA).catch(() => {});
    onDone();
  };

  return (
    <View style={{ flex: 1, backgroundColor: c.bg, paddingTop: insets.top + 12, paddingBottom: insets.bottom + 16, paddingHorizontal: 24 }}>
      <View style={{ flexDirection: 'row', gap: 6, height: 44, alignItems: 'center' }}>
        {[0, 1].map(i => <View key={i} style={{ height: 6, borderRadius: 3, width: i === step ? 26 : 6, backgroundColor: i <= step ? c.ink : c.line }} />)}
      </View>

      <Animated.View key={step} style={{ flex: 1, justifyContent: 'center', opacity: fade, transform: [{ translateY: fade.interpolate({ inputRange: [0, 1], outputRange: [24, 0] }) }] }}>
        {step === 0 ? <Welcome /> : (
          <View style={{ alignItems: 'center' }}>
            <Pop style={{ width: 132, height: 132, borderRadius: 66, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center', marginBottom: 32 }}>
              <Icon name="camera" size={56} color={c.accent} />
            </Pop>
            <Txt v="display" style={{ textAlign: 'center' }}>{t('permission_camera_title')}</Txt>
            <Txt v="title" muted style={{ textAlign: 'center', marginTop: 12, fontWeight: '400' }}>{t('permission_camera_body')}</Txt>
          </View>
        )}
      </Animated.View>

      {step === 0 ? (
        <Btn label={t('onboarding_next')} onPress={() => setStep(1)} />
      ) : (
        <View style={{ gap: 8 }}>
          <Btn label={t('permission_allow')} icon="camera" onPress={askCamera} />
          <Btn label={t('onboarding_start')} kind="ghost" onPress={onDone} />
        </View>
      )}
    </View>
  );
}

/** The angelfish, a one-line welcome, and the language choice: each language named in its own script. */
function Welcome() {
  const { c, t, lang, setLang } = useApp();
  const option = (l: 'en' | 'ta', glyph: string, name: string) => {
    const on = lang === l;
    return (
      <Tap key={l} onPress={() => setLang(l)} accessibilityRole="radio" accessibilityState={{ selected: on }} accessibilityLabel={name}
        style={{ flex: 1, alignItems: 'center', gap: 6, paddingVertical: 18, borderRadius: 22, backgroundColor: c.surface, borderWidth: 2, borderColor: on ? c.ink : 'transparent', boxShadow: c.shadow }}>
        <Text style={{ fontFamily: font, fontSize: 30, lineHeight: 40, fontWeight: '700', color: on ? c.ink : c.muted }}>{glyph}</Text>
        <Txt v="label" style={{ fontWeight: on ? '700' : '500' }}>{name}</Txt>
      </Tap>
    );
  };
  return (
    <View style={{ alignItems: 'center' }}>
      <Pop><Logo size={112} /></Pop>
      <Txt v="display" style={{ textAlign: 'center', marginTop: 28 }} accessibilityRole="header">{t('onboarding_welcome_title')}</Txt>
      <Txt muted style={{ textAlign: 'center', marginTop: 6 }}>{t('about_body')}</Txt>

      <Txt v="title" style={{ marginTop: 40, alignSelf: 'stretch' }}>{t('onboarding_language_title')}</Txt>
      <View style={{ flexDirection: 'row', gap: 12, marginTop: 12, alignSelf: 'stretch' }}>
        {option('en', 'A', t('language_english'))}
        {option('ta', 'அ', t('language_tamil'))}
      </View>
      <Txt v="caption" muted style={{ textAlign: 'center', marginTop: 12 }}>{t('onboarding_language_hint')}</Txt>
    </View>
  );
}

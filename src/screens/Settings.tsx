import React from 'react';
import { ScrollView, View } from 'react-native';
import { Key } from '../i18n';
import { Card, Header, Icon, IconName, LanguageSwitch, Tap, ThemePref, Txt, useApp } from '../ui';

// S8: grouped settings cards. Language and appearance apply instantly; scan mode applies on the next scan.
export default function Settings() {
  const { c, t, pop, prefs, setPref, setTheme, versions } = useApp();
  const theme = (prefs.theme as ThemePref) || 'light';
  const scanMode = prefs.scan_mode || 'AUTO';

  return (
    <ScrollView contentContainerStyle={{ paddingBottom: 48 }}>
      <Header title={t('settings_title')} onBack={pop} />
      <View style={{ paddingHorizontal: 20, gap: 14 }}>
        <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
          <Badge icon="globe" tint={c.accent} />
          <Txt v="title" style={{ flex: 1 }}>{t('settings_language')}</Txt>
          <LanguageSwitch />
        </Card>

        <Group icon="moon" tint={c.fat} title={t('settings_theme')}>
          {([['system', 'theme_system', 'theme_system_description'], ['light', 'theme_light'], ['dark', 'theme_dark']] as [ThemePref, Key, Key?][]).map(([k, label, desc]) => (
            <Option key={k} label={t(label)} description={desc && t(desc)} selected={theme === k} onPress={() => setTheme(k)} />
          ))}
        </Group>

        <Group icon="scan" tint={c.marigold} title={t('settings_scan_mode')}>
          {([['AUTO', 'scan_mode_auto', 'scan_mode_auto_description'], ['LIVE', 'scan_mode_live'], ['TAP', 'scan_mode_tap']] as [string, Key, Key?][]).map(([k, label, desc]) => (
            <Option key={k} label={t(label)} description={desc && t(desc)} selected={scanMode === k} onPress={() => setPref('scan_mode', k)} />
          ))}
        </Group>

        <Group icon="layers" tint={c.success} title={t('settings_updates')}>
          {versions && <Txt muted style={{ paddingVertical: 4 }}>{t('settings_data_version', { version: versions.data_version })}</Txt>}
          {versions?.model_version != null && <Txt muted style={{ paddingVertical: 4 }}>{t('settings_model_version', { version: versions.model_version })}</Txt>}
        </Group>

        <Group icon="shield" tint={c.protein} title={t('settings_privacy')}>
          <Txt muted>{t('privacy_body')}</Txt>
        </Group>

        <Group icon="info" tint={c.carbs} title={t('settings_about')}>
          <Txt muted>{t('about_body')}</Txt>
          {versions && <Txt v="caption" muted style={{ marginTop: 8 }}>{`${t('app_name')} ${versions.app_version}`}</Txt>}
        </Group>
      </View>
    </ScrollView>
  );
}

function Badge({ icon, tint }: { icon: IconName; tint: string }) {
  return (
    <View style={{ width: 40, height: 40, borderRadius: 14, backgroundColor: tint + '22', alignItems: 'center', justifyContent: 'center' }}>
      <Icon name={icon} size={20} color={tint} />
    </View>
  );
}

function Group({ icon, tint, title, children }: { icon: IconName; tint: string; title: string; children: React.ReactNode }) {
  return (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14, marginBottom: 8 }}>
        <Badge icon={icon} tint={tint} />
        <Txt v="title" style={{ flex: 1 }}>{title}</Txt>
      </View>
      {children}
    </Card>
  );
}

function Option({ label, description, selected, onPress }: { label: string; description?: string; selected: boolean; onPress: () => void }) {
  const { c } = useApp();
  return (
    <Tap onPress={onPress} accessibilityRole="radio" accessibilityState={{ selected }} scale={0.99} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10 }}>
      <View style={{ flex: 1 }}>
        <Txt style={{ fontWeight: selected ? '700' : '400' }}>{label}</Txt>
        {description ? <Txt v="caption" muted>{description}</Txt> : null}
      </View>
      <View style={{ width: 26, height: 26, borderRadius: 13, borderWidth: 2, borderColor: selected ? c.ink : c.line, backgroundColor: selected ? c.primary : 'transparent', alignItems: 'center', justifyContent: 'center' }}>
        {selected && <Icon name="check" size={16} color={c.onPrimary} stroke={3} />}
      </View>
    </Tap>
  );
}

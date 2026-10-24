import React from 'react';
import { Alert, SectionList, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FishID, Scan } from '../native';
import { formatDay } from '../i18n';
import { Btn, Card, Icon, Tap, Txt, useApp } from '../ui';
import { ScanRow } from './Home';

// S6: on-device scans grouped by day; tap to reopen, trash to delete, Clear all with confirmation.
export default function History() {
  const { c, t, lang, history, reloadHistory, push } = useApp();
  const insets = useSafeAreaInsets();

  const sections: { title: string; data: Scan[] }[] = [];
  for (const s of history) {
    const title = formatDay(s.time_ms, lang, t);
    const last = sections[sections.length - 1];
    if (last?.title === title) last.data.push(s);
    else sections.push({ title, data: [s] });
  }

  const remove = (s: Scan) =>
    Alert.alert(t('history_delete'), undefined, [
      { text: t('action_cancel'), style: 'cancel' },
      { text: t('history_delete'), style: 'destructive', onPress: () => FishID.deleteScan(s.id).then(reloadHistory) },
    ]);
  const clear = () =>
    Alert.alert(t('history_clear_all'), t('history_clear_confirm'), [
      { text: t('action_cancel'), style: 'cancel' },
      { text: t('history_clear_all'), style: 'destructive', onPress: () => FishID.clearHistory().then(reloadHistory) },
    ]);

  return (
    <SectionList
      sections={sections}
      keyExtractor={s => s.id}
      stickySectionHeadersEnabled={false}
      contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 40 }}
      ListHeaderComponent={
        <View style={{ paddingTop: insets.top + 12, flexDirection: 'row', alignItems: 'center' }}>
          <Txt v="display" style={{ flex: 1 }} accessibilityRole="header">{t('history_title')}</Txt>
          {history.length > 0 && (
            <Tap onPress={clear} style={{ flexDirection: 'row', alignItems: 'center', gap: 6, padding: 8 }}>
              <Icon name="trash" size={18} color={c.danger} />
              <Txt v="label" color={c.danger} style={{ fontWeight: '700' }}>{t('history_clear_all')}</Txt>
            </Tap>
          )}
        </View>
      }
      renderSectionHeader={({ section }) => <Txt v="title" muted style={{ marginTop: 22, marginBottom: 10 }}>{section.title}</Txt>}
      ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
      renderItem={({ item }) => <ScanRow scan={item} onDelete={() => remove(item)} />}
      ListEmptyComponent={
        <Card style={{ alignItems: 'center', paddingVertical: 40, marginTop: 24 }}>
          <View style={{ width: 84, height: 84, borderRadius: 42, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="history" size={40} color={c.accent} />
          </View>
          <Txt v="headline" style={{ marginTop: 14 }}>{t('history_empty_title')}</Txt>
          <Txt muted style={{ textAlign: 'center', marginBottom: 18 }}>{t('history_empty_body')}</Txt>
          <Btn label={t('nav_scan')} icon="scan" onPress={() => push({ name: 'scanner' })} small />
        </Card>
      }
    />
  );
}

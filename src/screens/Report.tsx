import React, { useState } from 'react';
import { KeyboardAvoidingView, ScrollView, TextInput, ToastAndroid, View } from 'react-native';
import { FishID, fileUri } from '../native';
import { percent } from '../i18n';
import { font } from '../theme';
import { Btn, Card, FishImage, Header, Icon, Tap, Txt, useApp } from '../ui';

const DONT_KNOW = '';

// S7: saved to the local report queue (filesDir/reports); the copy says "saved", not "sent".
export default function Report({ scanId }: { scanId: string }) {
  const { c, t, history, fish, fishById, pop } = useApp();
  const scan = history.find(s => s.id === scanId);
  const [answer, setAnswer] = useState<string | null>(null); // null = not chosen yet
  const [place, setPlace] = useState('');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  if (!scan) return null;

  const guessId = scan.species_id ?? scan.candidates[0]?.species_id;
  const guess = guessId ? fishById[guessId] : undefined;
  const guessConf = scan.candidates.find(x => x.species_id === guessId)?.confidence;

  const submit = async () => {
    setSaving(true);
    try {
      await FishID.saveReport(scanId, answer === DONT_KNOW ? null : answer, place, note);
      ToastAndroid.show(t('report_saved'), ToastAndroid.LONG);
      pop();
    } finally {
      setSaving(false);
    }
  };

  const input = { fontFamily: font, fontSize: 16, color: c.ink, backgroundColor: c.surface, borderRadius: 18, paddingHorizontal: 18, paddingVertical: 14, boxShadow: c.shadow } as const;
  const option = (id: string, label: string, uri?: string) => {
    const on = answer === id;
    return (
      <Tap key={id || 'dont_know'} onPress={() => setAnswer(id)} accessibilityRole="radio" accessibilityState={{ selected: on }}
        style={{ flexDirection: 'row', alignItems: 'center', gap: 12, padding: 10, borderRadius: 18, backgroundColor: c.surface, borderWidth: 2, borderColor: on ? c.ink : 'transparent' }}>
        {uri !== undefined ? <FishImage uri={uri} style={{ width: 48, height: 48 }} radius={14} /> : (
          <View style={{ width: 48, height: 48, borderRadius: 14, backgroundColor: c.surfaceAlt, alignItems: 'center', justifyContent: 'center' }}><Txt v="headline">?</Txt></View>
        )}
        <Txt v="label" style={{ flex: 1, fontWeight: '600' }}>{label}</Txt>
        {on && <Icon name="check" size={20} stroke={3} />}
      </Tap>
    );
  };

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior="height">
      <ScrollView contentContainerStyle={{ paddingBottom: 40 }} keyboardShouldPersistTaps="handled">
        <Header title={t('report_title')} onBack={pop} />
        <View style={{ paddingHorizontal: 20, gap: 14 }}>
          <Card style={{ flexDirection: 'row', alignItems: 'center', gap: 14, padding: 12 }}>
            <FishImage uri={fileUri(scan.photo)} style={{ width: 84, height: 84 }} radius={18} />
            <View style={{ flex: 1 }}>
              <Txt v="caption" muted>{t('report_our_guess')}</Txt>
              <Txt v="title">{guess ? (guessConf != null ? t('report_guess_value', { name: guess.primary_name, percent: percent(guessConf) }) : guess.primary_name) : t('history_not_identified')}</Txt>
            </View>
          </Card>

          <Txt v="headline" style={{ marginTop: 10 }}>{t('report_your_answer')}</Txt>
          <View style={{ gap: 8 }}>
            {fish.map(f => option(f.id, f.primary_name, f.image_uris[0] ?? null))}
            {option(DONT_KNOW, t('report_dont_know'))}
          </View>

          <TextInput value={place} onChangeText={setPlace} placeholder={t('report_place_hint')} placeholderTextColor={c.muted} style={[input, { marginTop: 10 }]} />
          <TextInput value={note} onChangeText={setNote} placeholder={t('report_note_hint')} placeholderTextColor={c.muted} multiline style={[input, { minHeight: 110, textAlignVertical: 'top' }]} />

          <View style={{ flexDirection: 'row', gap: 10, alignItems: 'flex-start' }}>
            <Icon name="shield" size={18} color={c.muted} />
            <Txt v="caption" muted style={{ flex: 1 }}>{t('report_privacy')}</Txt>
          </View>
          <Btn label={t('report_submit')} disabled={answer === null} loading={saving} onPress={submit} />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

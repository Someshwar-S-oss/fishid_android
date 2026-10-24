import React, { useEffect, useRef } from 'react';
import { Animated, Dimensions, Easing, ScrollView, StatusBar, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FishID, fileUri, Scan } from '../native';
import { grams, kcal, percent, rupees } from '../i18n';
import { Bar, Btn, Card, Chip, CircleBtn, FishImage, Icon, Scientific, StatTile, Tap, Txt, useApp } from '../ui';

const useScan = (scanId: string): Scan | undefined => useApp().history.find(s => s.id === scanId);

/** Leaves a result screen: back to a still-open scanner (re-armed), else wherever we came from. */
function useLeave() {
  const { pop } = useApp();
  return () => { FishID.scanAgain(); pop(); };
}

/** Matched: the user's photo on top, a sheet with name, numbers and match score (Cal AI's nutrition screen). */
export function Result({ scanId }: { scanId: string }) {
  const { c, t, fishById, versions, push, home } = useApp();
  const insets = useSafeAreaInsets();
  const leave = useLeave();
  const scan = useScan(scanId);
  const f = scan?.species_id ? fishById[scan.species_id] : undefined;

  const rise = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(rise, { toValue: 1, duration: 420, easing: Easing.out(Easing.back(1.1)), useNativeDriver: true }).start();
  }, [rise]);

  if (!scan || !f) return null;
  const conf = scan.candidates.find(x => x.species_id === f.id)?.confidence ?? 0;
  const high = conf >= f.threshold + (versions?.high_margin ?? 0.1);
  const n = f.nutrition_g;
  const photoH = Dimensions.get('window').height * 0.42;

  return (
    <View style={{ flex: 1, backgroundColor: c.bg }}>
      <StatusBar barStyle="light-content" />
      <ScrollView contentContainerStyle={{ paddingBottom: 190 }} bounces={false}>
        <View style={{ height: photoH }}>
          <FishImage uri={fileUri(scan.photo)} style={{ flex: 1 }} radius={0} />
          {/* Floating labels on the photo, like Cal AI's "Pancakes 595". */}
          {f.price_inr_per_kg != null && <Floating style={{ right: 16, bottom: 56 }} title={t('price_unit_per_kg')} value={rupees(f.price_inr_per_kg)} />}
          {n && <Floating style={{ left: 16, bottom: 104 }} title={t('nutrition_protein')} value={`${grams(n.protein)} g`} />}
        </View>

        <Animated.View style={{ marginTop: -32, backgroundColor: c.bg, borderTopLeftRadius: 32, borderTopRightRadius: 32, padding: 20, opacity: rise, transform: [{ translateY: rise.interpolate({ inputRange: [0, 1], outputRange: [80, 0] }) }] }}>
          <Chip
            icon="check"
            label={t('result_match_percent', { label: t(high ? 'result_match_high' : 'result_match_likely'), percent: percent(conf) })}
            bg={high ? c.successSoft : c.warnSoft}
            fg={high ? c.success : c.warn}
          />
          <Txt v="display" style={{ marginTop: 10 }}>{f.primary_name}</Txt>
          {f.secondary_name ? <Txt v="title" muted style={{ fontWeight: '500' }}>{f.secondary_name}</Txt> : null}
          <Scientific>{f.scientific_name}</Scientific>

          <View style={{ flexDirection: 'row', gap: 10, marginTop: 18 }}>
            <StatTile icon="flame" tint={c.energy} label={t('nutrition_energy')} value={n ? t('nutrition_kcal', { kcal: kcal(n) }) : '–'} />
            <StatTile icon="price" tint={c.accent} label={t('price_title')} value={f.price_inr_per_kg != null ? rupees(f.price_inr_per_kg) : '–'} />
          </View>
          <View style={{ flexDirection: 'row', gap: 10, marginTop: 10 }}>
            <StatTile icon="bolt" tint={c.protein} label={t('nutrition_protein')} value={n ? t('nutrition_grams', { grams: grams(n.protein) }) : '–'} />
            <StatTile icon="drop" tint={c.fat} label={t('nutrition_fat')} value={n ? t('nutrition_grams', { grams: grams(n.fat) }) : '–'} />
          </View>

          <Card style={{ marginTop: 10, flexDirection: 'row', alignItems: 'center', gap: 14 }}>
            <View style={{ width: 42, height: 42, borderRadius: 14, backgroundColor: c.successSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="sparkle" size={22} color={c.success} />
            </View>
            <View style={{ flex: 1, gap: 8 }}>
              <View style={{ flexDirection: 'row' }}>
                <Txt v="label" style={{ flex: 1 }}>{t('result_match_score')}</Txt>
                <Txt v="label" style={{ fontWeight: '700' }}>{`${percent(conf)}%`}</Txt>
              </View>
              <Bar value={conf} color={high ? c.success : c.marigold} />
            </View>
          </Card>

          <Tap onPress={() => push({ name: 'report', scanId })} style={{ alignSelf: 'center', flexDirection: 'row', gap: 6, alignItems: 'center', padding: 14, marginTop: 6 }}>
            <Icon name="flag" size={16} color={c.muted} />
            <Txt v="label" muted style={{ textDecorationLine: 'underline' }}>{t('profile_report_link')}</Txt>
          </Tap>
        </Animated.View>
      </ScrollView>

      <View style={{ position: 'absolute', top: insets.top + 8, left: 16 }}>
        <CircleBtn icon="close" onPhoto label={t('action_done')} onPress={() => home()} />
      </View>

      {/* Stacked full-width: Tamil labels run ~40% longer and must not break mid-word. */}
      <View style={{ position: 'absolute', left: 0, right: 0, bottom: 0, gap: 8, padding: 16, paddingBottom: insets.bottom + 12, backgroundColor: c.surface, borderTopLeftRadius: 28, borderTopRightRadius: 28, boxShadow: c.shadow }}>
        <Btn label={t('result_view_details')} onPress={() => push({ name: 'profile', id: f.id, scanId })} />
        <Btn label={t('result_scan_again')} kind="outline" icon="scan" onPress={leave} small />
      </View>
    </View>
  );
}

function Floating({ title, value, style }: { title: string; value: string; style: object }) {
  return (
    <View style={[{ position: 'absolute', backgroundColor: '#fff', borderRadius: 18, paddingHorizontal: 16, paddingVertical: 8, alignItems: 'center', boxShadow: '0px 8px 24px rgba(0,0,0,0.18)' }, style]}>
      <Txt v="label" color="#111" style={{ fontWeight: '700' }}>{title}</Txt>
      <Txt v="title" color="#111" style={{ fontWeight: '700' }}>{value}</Txt>
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
        {candidates.map(({ f, conf }) => (
          <Card key={f.id} style={{ padding: 14 }}>
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
          <View style={{ position: 'absolute', right: -8, bottom: -8, width: 52, height: 52, borderRadius: 26, backgroundColor: c.warnSoft, borderWidth: 4, borderColor: c.bg, alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="info" size={24} color={c.warn} />
          </View>
        </View>
        <Txt v="display" style={{ textAlign: 'center', marginTop: 20 }}>{t('no_match_title')}</Txt>
      </View>
      <Card style={{ marginTop: 20, gap: 14 }}>
        {tips.map(k => (
          <View key={k} style={{ flexDirection: 'row', gap: 12, alignItems: 'center' }}>
            <View style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="check" size={18} color={c.accent} stroke={2.6} />
            </View>
            <Txt style={{ flex: 1 }}>{t(k)}</Txt>
          </View>
        ))}
      </Card>
      <Btn label={t('action_retry')} icon="scan" onPress={leave} style={{ marginTop: 20 }} />
      {scan && <Btn label={t('profile_report_link')} kind="ghost" icon="flag" onPress={() => { pop(); push({ name: 'report', scanId }); }} />}
    </ScrollView>
  );
}

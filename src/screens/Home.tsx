import React, { useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Fish, FishID, fileUri, Scan } from '../native';
import { formatTime, grams, percent, rupees } from '../i18n';
import { Card, CircleBtn, FishImage, Icon, IconName, LanguageSwitch, Logo, routeForScan, SectionTitle, Tap, Txt, useApp } from '../ui';

const BRAND = '#0E6E7E';
const PRICE_ROWS = 5;

// For shoppers at the market: identify the fish in front of you, check the going price, look a fish up by name.
export default function Home() {
  const { c, t, fish, history, push, home } = useApp();
  const insets = useSafeAreaInsets();
  const [picking, setPicking] = useState(false);

  const pick = async () => {
    setPicking(true);
    await FishID.pickPhoto().catch(() => false);
    setPicking(false);
  };

  // Priced fish first (cheapest first), then the rest by name.
  const priced = [...fish].sort((a, b) =>
    (a.price_inr_per_kg ?? Infinity) - (b.price_inr_per_kg ?? Infinity) || a.primary_name.localeCompare(b.primary_name));
  const seeAll = (onPress: () => void) => (
    <Tap onPress={onPress} style={{ padding: 6 }}><Txt v="label" color={c.accent} style={{ fontWeight: '700' }}>{t('home_see_all')}</Txt></Tap>
  );

  return (
    <ScrollView contentContainerStyle={{ paddingTop: insets.top + 12, paddingHorizontal: 20, paddingBottom: 120 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        <Logo size={40} />
        <Txt v="headline" style={{ flex: 1 }}>{t('app_name')}</Txt>
        <LanguageSwitch />
        <CircleBtn icon="settings" label={t('action_settings')} onPress={() => push({ name: 'settings' })} />
      </View>

      {/* Identify: the one thing most people open the app for. */}
      <View style={{ marginTop: 20, borderRadius: 28, padding: 22, backgroundColor: BRAND, overflow: 'hidden' }}>
        <View style={{ position: 'absolute', right: -24, top: -18, opacity: 0.16 }} pointerEvents="none">
          <Icon name="fish" size={170} color="#FFFFFF" stroke={1.4} />
        </View>
        <Txt v="display" color="#FFFFFF" style={{ maxWidth: '85%' }}>{t('home_scan_cta_title')}</Txt>
        <Txt color="#FFFFFF" style={{ opacity: 0.85, marginTop: 4, maxWidth: '92%' }}>{t('home_scan_cta_body')}</Txt>
        <View style={{ flexDirection: 'row', gap: 10, marginTop: 20 }}>
          <Tap onPress={() => push({ name: 'scanner' })} accessibilityRole="button" style={{ flex: 1, minHeight: 56, borderRadius: 999, backgroundColor: '#FFFFFF', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10 }}>
            <Icon name="camera" size={22} color={BRAND} stroke={2.2} />
            <Txt v="title" color={BRAND} style={{ fontWeight: '700' }}>{t('nav_scan')}</Txt>
          </Tap>
          <Tap onPress={pick} disabled={picking} accessibilityRole="button" accessibilityLabel={t('scan_gallery_description')}
            style={{ width: 56, height: 56, borderRadius: 28, backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center', opacity: picking ? 0.5 : 1 }}>
            <Icon name="gallery" size={24} color="#FFFFFF" />
          </Tap>
        </View>
      </View>

      {/* Look up a fish by name, in any script. */}
      <Tap onPress={() => home('guide')} accessibilityRole="search" scale={0.99}
        style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 14, minHeight: 56, borderRadius: 999, paddingHorizontal: 20, backgroundColor: c.surface, boxShadow: c.shadow }}>
        <Icon name="search" size={20} color={c.muted} />
        <Txt muted style={{ flex: 1 }} numberOfLines={1}>{t('catalogue_search_hint')}</Txt>
      </Tap>

      <SectionTitle right={fish.length > PRICE_ROWS ? seeAll(() => home('guide')) : undefined}>{t('home_market_prices')}</SectionTitle>
      <Card style={{ paddingVertical: 4 }}>
        {priced.slice(0, PRICE_ROWS).map((f, i) => <PriceRow key={f.id} f={f} last={i === Math.min(PRICE_ROWS, priced.length) - 1} />)}
      </Card>
      <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center', marginTop: 10, paddingHorizontal: 4 }}>
        <Icon name="info" size={16} color={c.muted} />
        <Txt v="caption" muted style={{ flex: 1 }}>{t('price_note')}</Txt>
      </View>

      {history.length > 0 ? (
        <>
          <SectionTitle right={history.length > 2 ? seeAll(() => home('history')) : undefined}>{t('home_recent')}</SectionTitle>
          <View style={{ gap: 12 }}>
            {history.slice(0, 2).map(s => <ScanRow key={s.id} scan={s} />)}
          </View>
        </>
      ) : (
        <>
          <SectionTitle>{t('home_how_it_works')}</SectionTitle>
          <Card style={{ gap: 18 }}>
            {([
              ['scan', c.marigold, 'onboarding_scan_title', 'onboarding_scan_body'],
              ['sparkle', c.accent, 'onboarding_identify_title', 'onboarding_identify_body'],
              ['price', c.success, 'onboarding_learn_title', 'onboarding_learn_body'],
            ] as const).map(([icon, tint, title, body]) => (
              <View key={title} style={{ flexDirection: 'row', gap: 14 }}>
                <View style={{ width: 44, height: 44, borderRadius: 14, backgroundColor: tint + '22', alignItems: 'center', justifyContent: 'center' }}>
                  <Icon name={icon} size={22} color={tint} />
                </View>
                <View style={{ flex: 1 }}>
                  <Txt v="title">{t(title)}</Txt>
                  <Txt muted>{t(body)}</Txt>
                </View>
              </View>
            ))}
          </Card>
        </>
      )}
    </ScrollView>
  );
}

/** Photo, both names, and the price per kg, big enough to compare with the seller's board. */
function PriceRow({ f, last }: { f: Fish; last: boolean }) {
  const { c, t, push } = useApp();
  return (
    <Tap onPress={() => push({ name: 'profile', id: f.id })} scale={0.99} accessibilityRole="button"
      style={{ flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 12, borderBottomWidth: last ? 0 : 1, borderBottomColor: c.line }}>
      <FishImage uri={f.image_uris[0]} style={{ width: 56, height: 56 }} radius={16} />
      <View style={{ flex: 1 }}>
        <Txt v="title" numberOfLines={2}>{f.primary_name}</Txt>
        {f.secondary_name ? <Txt v="caption" muted numberOfLines={1}>{f.secondary_name}</Txt> : null}
        {f.price_inr_per_kg == null && <Txt v="caption" muted>{t('price_unavailable')}</Txt>}
      </View>
      {f.price_inr_per_kg != null && (
        <View style={{ alignItems: 'flex-end' }}>
          <Txt v="title" style={{ fontWeight: '700' }}>{rupees(f.price_inr_per_kg)}</Txt>
          <Txt v="caption" muted>{t('price_unit_per_kg')}</Txt>
        </View>
      )}
      <Icon name="chevronRight" size={18} color={c.muted} />
    </Tap>
  );
}

/** One past scan; a failed scan says so instead of naming a fish. */
export function ScanRow({ scan, onDelete }: { scan: Scan; onDelete?: () => void }) {
  const { c, t, lang, fishById, push } = useApp();
  const f = scan.species_id ? fishById[scan.species_id] : undefined;
  const conf = scan.candidates.find(x => x.species_id === scan.species_id)?.confidence ?? scan.candidates[0]?.confidence;
  return (
    <Card onPress={() => push(routeForScan(scan))} style={{ flexDirection: 'row', alignItems: 'center', padding: 12, gap: 14 }}>
      <FishImage uri={fileUri(scan.photo)} style={{ width: 76, height: 76 }} radius={18} />
      <View style={{ flex: 1, gap: 2 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <Txt v="title" numberOfLines={1} style={{ flex: 1 }}>{f ? f.primary_name : t(scan.outcome === 'UNSURE' ? 'result_match_likely' : 'history_not_identified')}</Txt>
          <Txt v="caption" muted>{formatTime(scan.time_ms, lang)}</Txt>
        </View>
        {f ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            {f.price_inr_per_kg != null && <Mini icon="price" tint={c.accent} text={`${rupees(f.price_inr_per_kg)}/kg`} />}
            {f.nutrition_g && <Mini icon="bolt" tint={c.protein} text={`${grams(f.nutrition_g.protein)} g`} />}
            {conf != null && <Mini icon="check" tint={c.success} text={`${percent(conf)}%`} />}
          </View>
        ) : scan.outcome === 'NONE' ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
            <Icon name="info" size={16} color={c.warn} />
            <Txt v="caption" color={c.warn} numberOfLines={1} style={{ flex: 1 }}>{t('no_match_title')}</Txt>
          </View>
        ) : (
          <Txt v="caption" muted numberOfLines={1}>{t('low_confidence_title')}</Txt>
        )}
      </View>
      {onDelete && <CircleBtn icon="trash" size={38} label={t('history_delete')} onPress={onDelete} />}
    </Card>
  );
}

function Mini({ icon, tint, text }: { icon: IconName; tint: string; text: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
      <Icon name={icon} size={15} color={tint} stroke={2.4} />
      <Txt v="label" style={{ fontWeight: '600' }}>{text}</Txt>
    </View>
  );
}

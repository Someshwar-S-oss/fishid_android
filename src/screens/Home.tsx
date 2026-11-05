import React, { useContext, useEffect, useState } from 'react';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { fileUri, Scan } from '../native';
import { formatTime, grams, percent, rupees } from '../i18n';
import { Card, CircleBtn, FishImage, Icon, IconName, LanguageSwitch, Logo, routeForScan, SectionTitle, Tap, Txt, useApp } from '../ui';
import { Awake, Reveal, stagger } from '../motion';
import { useTips, useTipTarget } from '../coach';
import { FishCard, SearchField, Sort, SortChips, useFishSearch } from './Guide';

/** Fish shown on Home before "See all" sends you to the full guide. */
const GRID = 6;
const RECENT = 8;

// Search-first: for shoppers asking "what fish is this, and what should it cost?". Scanning is the floating button.
export default function Home() {
  const { c, t, history, push, home } = useApp();
  const insets = useSafeAreaInsets();
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<Sort>('name');
  const list = useFishSearch(query, sort);
  const searching = !!query.trim();
  const shown = searching ? list : list.slice(0, GRID);

  // First-run tips, once the page has faded in and only while it's the visible screen.
  const awake = useContext(Awake);
  const tips = useTips();
  const searchTip = useTipTarget('search');
  const languageTip = useTipTarget('language');
  useEffect(() => {
    if (!awake) return;
    const id = setTimeout(() => tips(['search', 'language', 'scan']), 900);
    return () => clearTimeout(id);
  }, [awake, tips]);

  const seeAll = (onPress: () => void) => (
    <Tap onPress={onPress} style={{ padding: 6 }}><Txt v="label" color={c.accent} style={{ fontWeight: '700' }}>{t('home_see_all')}</Txt></Tap>
  );

  return (
    <ScrollView contentContainerStyle={{ paddingTop: insets.top + 12, paddingHorizontal: 20, paddingBottom: 140 }} keyboardShouldPersistTaps="handled">
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        <Logo size={40} />
        <Txt v="headline" style={{ flex: 1 }}>{t('app_name')}</Txt>
        <View ref={languageTip} collapsable={false}><LanguageSwitch /></View>
        <CircleBtn icon="settings" label={t('action_settings')} onPress={() => push({ name: 'settings' })} />
      </View>

      <Reveal delay={40}>
        <Txt v="display" style={{ marginTop: 28 }} accessibilityRole="header">{t('home_search_title')}</Txt>
        <View ref={searchTip} collapsable={false} style={{ marginTop: 12 }}><SearchField query={query} setQuery={setQuery} /></View>
      </Reveal>

      {!searching && history.length > 0 && (
        <Reveal delay={100}>
          <SectionTitle right={seeAll(() => home('history'))}>{t('home_recent')}</SectionTitle>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginHorizontal: -20 }} contentContainerStyle={{ gap: 10, paddingHorizontal: 20 }}>
            {history.slice(0, RECENT).map(s => <ScanThumb key={s.id} scan={s} />)}
          </ScrollView>
        </Reveal>
      )}

      <Reveal delay={160}>
        {/* Heading and chips on their own lines in every language, so switching language never reflows them. */}
        <Txt v="headline" style={{ marginTop: 28 }} accessibilityRole="header">{t('home_market_prices')}</Txt>
        <SortChips options={['name', 'low']} sort={sort} setSort={setSort} style={{ marginTop: 10, marginBottom: 14 }} />
        {shown.length === 0 ? (
          <Txt muted style={{ textAlign: 'center', marginTop: 24 }}>{t('catalogue_no_results', { query })}</Txt>
        ) : (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', columnGap: 14, rowGap: 18 }}>
            {shown.map((f, i) => (
              <Reveal key={f.id} delay={200 + stagger(i, 45)} style={{ width: '47.5%', flexGrow: 1, maxWidth: '50%' }}><FishCard f={f} /></Reveal>
            ))}
          </View>
        )}
        {!searching && list.length > GRID && <View style={{ alignItems: 'center', marginTop: 8 }}>{seeAll(() => home('guide'))}</View>}
        <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center', marginTop: 16, paddingHorizontal: 4 }}>
          <Icon name="info" size={16} color={c.muted} />
          <Txt v="caption" muted style={{ flex: 1 }}>{t('price_note')}</Txt>
        </View>
      </Reveal>
    </ScrollView>
  );
}

/** A past scan as a small photo tile with the fish's name (or a warning dot when nothing was identified). */
function ScanThumb({ scan }: { scan: Scan }) {
  const { c, t, fishById, push } = useApp();
  const f = scan.species_id ? fishById[scan.species_id] : undefined;
  const name = f ? f.primary_name : t('history_not_identified');
  return (
    <Tap onPress={() => push(routeForScan(scan))} accessibilityRole="button" accessibilityLabel={name} style={{ width: 84 }}>
      <FishImage uri={fileUri(scan.photo)} style={{ width: 84, height: 84 }} radius={20} />
      <Txt v="caption" muted={!f} numberOfLines={1} style={{ marginTop: 4, fontWeight: f ? '600' : '400', color: f ? c.ink : c.warn }}>{name}</Txt>
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

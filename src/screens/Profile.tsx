import React, { useState } from 'react';
import { Dimensions, LayoutAnimation, ScrollView, Share, StatusBar, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { fileUri, Fish } from '../native';
import { codeLabel, grams, kcal, rupees } from '../i18n';
import { Card, Chip, CircleBtn, FishImage, Icon, IconName, IucnBadge, Scientific, StatTile, Tap, Txt, useApp } from '../ui';

// S4: shopper order — price and food value first, then how to recognise it, then ecology and names.
export default function Profile({ id, scanId }: { id: string; scanId?: string }) {
  const { c, t, fishById, history, pop, push } = useApp();
  const insets = useSafeAreaInsets();
  const f = fishById[id];
  const scan = scanId ? history.find(s => s.id === scanId) : undefined;
  const [page, setPage] = useState(0);
  if (!f) return null;

  const width = Dimensions.get('window').width;
  const photos = [...(scan ? [fileUri(scan.photo)] : []), ...f.image_uris];
  const n = f.nutrition_g;

  return (
    <View style={{ flex: 1, backgroundColor: c.bg }}>
      <StatusBar barStyle="light-content" />
      <ScrollView contentContainerStyle={{ paddingBottom: insets.bottom + 32 }}>
        <View style={{ height: width * 0.92 }}>
          <ScrollView horizontal pagingEnabled showsHorizontalScrollIndicator={false} onMomentumScrollEnd={e => setPage(Math.round(e.nativeEvent.contentOffset.x / width))}>
            {(photos.length ? photos : [null]).map((uri, i) => (
              <FishImage key={i} uri={uri} style={{ width, height: width * 0.92 }} radius={0} />
            ))}
          </ScrollView>
          {photos.length > 0 && (
            <View style={{ position: 'absolute', bottom: 48, left: 0, right: 0, alignItems: 'center', gap: 8 }}>
              <Chip label={t(scan && page === 0 ? 'label_your_photo' : 'label_reference_photo')} bg="rgba(0,0,0,0.45)" fg="#fff" style={{ alignSelf: 'center' }} />
              {photos.length > 1 && (
                <View style={{ flexDirection: 'row', gap: 6 }}>
                  {photos.map((_, i) => <View key={i} style={{ width: i === page ? 18 : 6, height: 6, borderRadius: 3, backgroundColor: i === page ? '#fff' : 'rgba(255,255,255,0.5)' }} />)}
                </View>
              )}
            </View>
          )}
        </View>

        <View style={{ marginTop: -32, backgroundColor: c.bg, borderTopLeftRadius: 32, borderTopRightRadius: 32, paddingHorizontal: 20, paddingTop: 22 }}>
          <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
            {f.price_inr_per_kg != null && <Chip icon="price" label={t('price_per_kg', { price: rupees(f.price_inr_per_kg) })} bg={c.accentSoft} fg={c.accent} />}
            {f.iucn && <IucnBadge code={f.iucn} />}
          </View>
          <Txt v="display" style={{ marginTop: 12 }} accessibilityRole="header">{f.primary_name}</Txt>
          {f.secondary_name ? <Txt v="title" muted style={{ fontWeight: '500' }}>{f.secondary_name}</Txt> : null}
          <Scientific>{f.scientific_name}</Scientific>

          <PriceCard f={f} />

          {n && (
            <>
              <Txt v="headline" style={{ marginTop: 28, marginBottom: 12 }}>{t('nutrition_title')}</Txt>
              <View style={{ flexDirection: 'row', gap: 10 }}>
                <StatTile icon="flame" tint={c.energy} label={t('nutrition_energy')} value={t('nutrition_kcal', { kcal: kcal(n) })} />
                <StatTile icon="bolt" tint={c.protein} label={t('nutrition_protein')} value={t('nutrition_grams', { grams: grams(n.protein) })} />
              </View>
              <View style={{ flexDirection: 'row', gap: 10, marginTop: 10 }}>
                <StatTile icon="drop" tint={c.fat} label={t('nutrition_fat')} value={t('nutrition_grams', { grams: grams(n.fat) })} />
                <StatTile icon="leaf" tint={c.carbs} label={t('nutrition_carbohydrate')} value={n.carbohydrate != null ? t('nutrition_grams', { grams: grams(n.carbohydrate) }) : '–'} />
              </View>
              <Composition f={f} />
            </>
          )}

          {(f.field_marks.length > 0 || f.meristics) && (
            <Section icon="search" title={t('tab_identify')} initiallyOpen>
              <FieldMarks marks={f.field_marks} />
              {f.meristics && (
                <>
                  <Txt v="label" muted style={{ marginTop: 16, marginBottom: 8 }}>{t('morphology_counts')}</Txt>
                  <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                    {([
                      ['morphology_dorsal_spines', f.meristics.dorsal_spines],
                      ['morphology_dorsal_rays', f.meristics.dorsal_rays],
                      ['morphology_anal_spines', f.meristics.anal_spines],
                      ['morphology_anal_rays', f.meristics.anal_rays],
                    ] as const).map(([k, v]) => (
                      <View key={k} style={{ width: '48%', flexGrow: 1, backgroundColor: c.surfaceAlt, borderRadius: 16, padding: 12 }}>
                        <Txt v="headline">{String(v)}</Txt>
                        <Txt v="caption" muted>{t(k)}</Txt>
                      </View>
                    ))}
                  </View>
                </>
              )}
            </Section>
          )}

          <Habitat f={f} />

          <Section icon="globe" title={t('profile_names')}>
            {([
              ['label_common_name', f.common_name],
              ['label_local_name', f.local_name_ta],
              ['label_scientific_name', f.scientific_name],
              ['label_scientific_name_tamil', f.scientific_name_ta],
            ] as const).filter(([, v]) => v).map(([k, v]) => (
              <View key={k} style={{ paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: c.line }}>
                <Txt v="caption" muted>{t(k)}</Txt>
                <Txt v="title">{v as string}</Txt>
              </View>
            ))}
          </Section>

          <View style={{ alignItems: 'center', gap: 6, marginTop: 24 }}>
            {f.source ? <Txt v="caption" muted style={{ textAlign: 'center' }}>{t('profile_source', { source: f.source })}</Txt> : null}
            {scan && (
              <Tap onPress={() => push({ name: 'report', scanId: scan.id })} style={{ flexDirection: 'row', gap: 6, alignItems: 'center', padding: 12 }}>
                <Icon name="flag" size={16} color={c.muted} />
                <Txt v="label" muted style={{ textDecorationLine: 'underline' }}>{t('profile_report_link')}</Txt>
              </Tap>
            )}
          </View>
        </View>
      </ScrollView>

      <View style={{ position: 'absolute', top: insets.top + 8, left: 16, right: 16, flexDirection: 'row', justifyContent: 'space-between' }}>
        <CircleBtn icon="back" onPhoto label={t('action_back')} onPress={pop} />
        <CircleBtn icon="share" onPhoto label={t('action_share')} onPress={() => Share.share({ message: t('profile_share_text', { name: f.primary_name, scientific_name: f.scientific_name }) })} />
      </View>
    </View>
  );
}

function PriceCard({ f }: { f: Fish }) {
  const { c, t } = useApp();
  return (
    <Card style={{ marginTop: 20, flexDirection: 'row', alignItems: 'center', gap: 16 }}>
      <View style={{ width: 52, height: 52, borderRadius: 18, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
        <Icon name="price" size={26} color={c.accent} />
      </View>
      <View style={{ flex: 1 }}>
        <Txt v="label" muted>{t('price_title')}</Txt>
        {f.price_inr_per_kg != null ? (
          <Txt v="hero" style={{ fontSize: 40, lineHeight: 46 }}>{rupees(f.price_inr_per_kg)}<Txt v="title" muted>{'  ' + t('price_unit_per_kg')}</Txt></Txt>
        ) : (
          <Txt v="title">{t('price_unavailable')}</Txt>
        )}
        {f.price_updated ? <Txt v="caption" muted>{t('price_updated', { date: f.price_updated })}</Txt> : null}
        <Txt v="caption" muted>{t('price_note')}</Txt>
      </View>
    </Card>
  );
}

/** One 100% stacked bar: moisture dominates (~76 g), so separate bars would make protein look tiny. */
function Composition({ f }: { f: Fish }) {
  const { c, t } = useApp();
  const n = f.nutrition_g!;
  const parts = ([
    [t('nutrition_protein'), n.protein, c.protein],
    [t('nutrition_fat'), n.fat, c.fat],
    [t('nutrition_carbohydrate'), n.carbohydrate, c.carbs],
    [t('nutrition_ash'), n.ash, c.ash],
    [t('nutrition_moisture'), n.moisture, c.moisture],
  ] as const).filter((p): p is [string, number, string] => p[1] != null); // not every study reports ash / carbohydrate
  const total = parts.reduce((a, p) => a + p[1], 0) || 1;
  return (
    <Card style={{ marginTop: 10 }}>
      <Txt v="title">{t('nutrition_protein_highlight', { grams: grams(n.protein) })}</Txt>
      <View style={{ flexDirection: 'row', height: 14, borderRadius: 7, overflow: 'hidden', marginTop: 12, gap: 2 }}>
        {parts.map(([label, v, color]) => <View key={label} style={{ flex: v / total, backgroundColor: color }} />)}
      </View>
      <View style={{ marginTop: 12, gap: 6 }}>
        {parts.map(([label, v, color]) => (
          <View key={label} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: color }} />
            <Txt v="label" style={{ flex: 1 }}>{label}</Txt>
            <Txt v="label" style={{ fontWeight: '700' }}>{t('nutrition_grams', { grams: grams(v) })}</Txt>
          </View>
        ))}
      </View>
    </Card>
  );
}

function FieldMarks({ marks }: { marks: string[] }) {
  const { c, t } = useApp();
  const [all, setAll] = useState(false);
  const shown = all ? marks : marks.slice(0, 4);
  return (
    <>
      {shown.map(m => (
        <View key={m} style={{ flexDirection: 'row', gap: 10, paddingVertical: 5 }}>
          <View style={{ width: 22, height: 22, borderRadius: 11, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center', marginTop: 2 }}>
            <Icon name="check" size={14} color={c.accent} stroke={3} />
          </View>
          <Txt style={{ flex: 1 }}>{m}</Txt>
        </View>
      ))}
      {marks.length > 4 && (
        <Tap onPress={() => { LayoutAnimation.easeInEaseOut(); setAll(!all); }} style={{ paddingVertical: 10 }}>
          <Txt v="label" color={c.accent} style={{ fontWeight: '700' }}>{all ? t('profile_show_fewer_features') : t('profile_show_all_features', { count: marks.length })}</Txt>
        </Tap>
      )}
    </>
  );
}

function Habitat({ f }: { f: Fish }) {
  const { c, t } = useApp();
  const tiles: [IconName, string, string | null | undefined][] = [
    ['pin', t('ecology_distribution'), f.distribution],
    ['waves', t('ecology_habitat'), f.habitat.map(h => codeLabel(t, 'habitat', h)).join(', ')],
    ['fish', t('ecology_feeding'), f.feeding && codeLabel(t, 'feeding', f.feeding)],
    ['layers', t('ecology_vertical'), f.vertical && codeLabel(t, 'vertical', f.vertical)],
    ['depth', t('ecology_depth'), f.depth_m?.length === 2 ? t('ecology_depth_value', { min: f.depth_m[0], max: f.depth_m[1] }) : null],
    ['shield', t('ecology_iucn'), f.iucn && codeLabel(t, 'iucn', f.iucn)],
  ];
  const shown = tiles.filter(([, , v]) => v);
  if (!shown.length) return null;
  return (
    <Section icon="waves" title={t('ecology_title')}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        {shown.map(([icon, label, value]) => (
          <View key={label} style={{ width: '48%', flexGrow: 1, backgroundColor: c.surfaceAlt, borderRadius: 18, padding: 14, gap: 6 }}>
            <Icon name={icon} size={22} color={c.accent} />
            <Txt v="caption" muted>{label}</Txt>
            <Txt v="label" style={{ fontWeight: '700' }}>{value as string}</Txt>
          </View>
        ))}
      </View>
    </Section>
  );
}

/** Collapsible card, like the "Ingredients" accordion in the recipe screen. */
function Section({ icon, title, children, initiallyOpen }: { icon: IconName; title: string; children: React.ReactNode; initiallyOpen?: boolean }) {
  const { c } = useApp();
  const [open, setOpen] = useState(!!initiallyOpen);
  return (
    <Card style={{ marginTop: 14, paddingVertical: 6 }}>
      <Tap onPress={() => { LayoutAnimation.easeInEaseOut(); setOpen(!open); }} accessibilityRole="button" accessibilityState={{ expanded: open }} scale={0.99} style={{ flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12 }}>
        <Icon name={icon} size={22} color={c.ink} />
        <Txt v="title" style={{ flex: 1 }}>{title}</Txt>
        <View style={{ transform: [{ rotate: open ? '180deg' : '0deg' }] }}><Icon name="chevronDown" size={22} color={c.muted} /></View>
      </Tap>
      {open && <View style={{ paddingBottom: 12 }}>{children}</View>}
    </Card>
  );
}

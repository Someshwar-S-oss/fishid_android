import React, { useEffect, useMemo, useState } from 'react';
import { FlatList, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Fish, FishID } from '../native';
import { grams, rupees } from '../i18n';
import { Chip, FishImage, Icon, Tap, Txt, useApp } from '../ui';
import { font } from '../theme';

type Sort = 'name' | 'low' | 'high';

// S5: search in every script (English, Tamil, romanised, scientific) + 2-column cards, like a recipe grid.
export default function Guide() {
  const { c, t, lang, fish, push } = useApp();
  const insets = useSafeAreaInsets();
  const [query, setQuery] = useState('');
  const [found, setFound] = useState<Fish[] | null>(null);
  const [sort, setSort] = useState<Sort>('name');

  // Matching lives in Kotlin (FishEntry.matches) so search behaves the same everywhere.
  useEffect(() => {
    if (!query.trim()) return setFound(null);
    let live = true;
    FishID.catalog(lang, query).then(cat => live && setFound(cat.entries));
    return () => { live = false; };
  }, [query, lang]);

  const list = useMemo(() => {
    const l = [...(found ?? fish)];
    const price = (f: Fish) => f.price_inr_per_kg ?? (sort === 'low' ? Infinity : -Infinity);
    if (sort === 'name') l.sort((a, b) => a.primary_name.localeCompare(b.primary_name));
    else l.sort((a, b) => (sort === 'low' ? price(a) - price(b) : price(b) - price(a)));
    return l;
  }, [found, fish, sort]);

  const header = (
    <View style={{ paddingTop: insets.top + 12, paddingBottom: 8 }}>
      <Txt v="display" accessibilityRole="header">{t('catalogue_title')}</Txt>
      <Txt muted>{t('catalogue_count', { count: fish.length })}</Txt>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: c.surface, borderRadius: 999, paddingHorizontal: 18, marginTop: 16, minHeight: 54, boxShadow: c.shadow }}>
        <Icon name="search" size={20} color={c.muted} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder={t('catalogue_search_hint')}
          placeholderTextColor={c.muted}
          style={{ flex: 1, fontFamily: font, fontSize: 16, color: c.ink, paddingVertical: 12 }}
          returnKeyType="search"
        />
        {query ? <Tap onPress={() => setQuery('')} hitSlop={10}><Icon name="close" size={18} color={c.muted} /></Tap> : null}
      </View>
      <View style={{ flexDirection: 'row', gap: 8, marginTop: 14, flexWrap: 'wrap' }}>
        {([['name', 'sort_name'], ['low', 'sort_price_low'], ['high', 'sort_price_high']] as const).map(([k, label]) => (
          <Tap key={k} onPress={() => setSort(k)} accessibilityRole="radio" accessibilityState={{ selected: sort === k }}
            style={{ borderRadius: 999, paddingHorizontal: 16, paddingVertical: 8, backgroundColor: sort === k ? c.primary : c.surface, borderWidth: 1, borderColor: sort === k ? c.primary : c.line }}>
            <Txt v="label" color={sort === k ? c.onPrimary : c.ink} style={{ fontWeight: '600' }}>{t(label)}</Txt>
          </Tap>
        ))}
      </View>
    </View>
  );

  return (
    <FlatList
      data={list}
      keyExtractor={f => f.id}
      numColumns={2}
      ListHeaderComponent={header}
      ListEmptyComponent={<Txt muted style={{ textAlign: 'center', marginTop: 40 }}>{t('catalogue_no_results', { query })}</Txt>}
      columnWrapperStyle={{ gap: 14 }}
      contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 40, gap: 18 }}
      keyboardShouldPersistTaps="handled"
      renderItem={({ item: f }) => (
        <Tap onPress={() => push({ name: 'profile', id: f.id })} style={{ flex: 1, maxWidth: '50%' }} accessibilityRole="button" accessibilityLabel={f.primary_name}>
          <View>
            <FishImage uri={f.image_uris[0]} style={{ aspectRatio: 0.92 }} radius={22} />
            <View style={{ position: 'absolute', left: 8, bottom: 8, right: 8, flexDirection: 'row', gap: 6, flexWrap: 'wrap' }}>
              {f.price_inr_per_kg != null && <Chip label={`${rupees(f.price_inr_per_kg)}/kg`} bg={c.successSoft} fg={c.success} />}
              {f.nutrition_g && <Chip label={`${grams(f.nutrition_g.protein)}g`} icon="bolt" bg={c.warnSoft} fg={c.warn} />}
            </View>
          </View>
          <Txt v="title" numberOfLines={2} style={{ marginTop: 8 }}>{f.primary_name}</Txt>
          {f.secondary_name ? <Txt v="caption" muted numberOfLines={1}>{f.secondary_name}</Txt> : null}
        </Tap>
      )}
    />
  );
}

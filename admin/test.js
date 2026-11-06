// node test.js — checks the FishBase parser and the catalog validator. Add --live to also hit fishbase.se.
import assert from 'node:assert/strict';
import { lookup, parse } from './fishbase.js';
import { merge, validate } from './catalog.js';

// Trimmed from https://www.fishbase.se/summary/Scolopsis-bimaculata.html
const page = `<title>Scolopsis bimaculata,  Thumbprint monocle bream  : fisheries</title>
<h1>Environment: milieu / climate zone / depth range / distribution range</h1><span>Ecology</span>
Marine; reef-associated; depth range ? - 60 m (Ref. <A>9710</A>). Tropical; 31&deg;N - 29&deg;S <h1>Distribution</h1> Territories
Dorsal spines (total): 10; Dorsal soft rays (total): 9; Anal spines : 3; Anal soft rays : 7. Head scales
IUCN Red List Status (Ref. 130435: Version 2025-2 (Global))<BR /></h1><span class='box'>&nbsp;</span><a>Least Concern (LC)</a>`;

assert.deepEqual(parse(page), {
  common_name: 'Thumbprint monocle bream',
  habitat: ['marine', 'reef_associated'],
  vertical: 'reef',
  depth_m: [0, 60],
  meristics: { dorsal_spines: 10, dorsal_rays: 9, anal_spines: 3, anal_rays: 7 },
  iucn: 'lc',
});
assert.deepEqual(parse('<title>nothing here</title>'), {});
await assert.rejects(lookup('atule mate; rm -rf'), /must look like/);

const cur = {
  data_version: 3,
  species: [{ id: 'a', scientific_name: 'A a', images: ['a_1.jpg'], habitat: ['marine'] }],
  text: { en: { a: { common_name: 'A', field_marks: ['x'] } }, ta: {} },
};
const ok = structuredClone(cur);
ok.species[0].price_inr_per_kg = 300;
ok.species[0].images = ['evil.jpg'];
assert.equal(validate(ok, cur), null);
assert.deepEqual(ok.species[0].images, ['a_1.jpg'], 'images are not editable');
const bad = (patch, re) => {
  const c = structuredClone(cur);
  patch(c);
  assert.match(validate(c, cur) ?? '', re);
};
bad(c => (c.species[0].price_inr_per_kg = 12.5), /price/);
bad(c => (c.species[0].depth_m = [60, 0]), /depth/);
bad(c => (c.species[0].meristics = { dorsal_spines: 1 }), /fin counts/);
bad(c => (c.species[0].habitat = ['ocean']), /habitat/);
bad(c => (c.species = []), /species/i);
bad(c => (c.text.en.a.field_marks = 'x'), /field marks/);

// A model update ships species the published catalog lacks: they're added, and the version never falls behind the app's.
const shipped = { data_version: 4, species: [...cur.species, { id: 'b', scientific_name: 'B b', images: ['b_1.jpg'], habitat: [] }],
  text: { en: { a: { common_name: 'stale' }, b: { common_name: 'B' } }, ta: {} } };
const merged = merge(cur, shipped);
assert.deepEqual(merged.species.map(s => s.id), ['a', 'b']);
assert.equal(merged.text.en.a.common_name, 'A', 'published edits win');
assert.equal(merged.text.en.b.common_name, 'B');
assert.equal(merged.data_version, 4);
assert.equal(validate(structuredClone(merged), merged), null);

if (process.argv.includes('--live')) {
  const { facts } = await lookup('Atule mate');
  assert.equal(facts.meristics.dorsal_rays, 22);
  assert.deepEqual(facts.depth_m, [1, 80]);
}
console.log('ok');

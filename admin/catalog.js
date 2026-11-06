// The published catalog: one catalog.json in a public GitHub repo that the app downloads (DataSync.kt).
// Shape = assets/data/species.json + { text: { en: text.en.json, ta: text.ta.json } }.
// GitHub, not Render's disk: Render's free disk is wiped on every restart, and GitHub keeps every version.
import { readFile } from 'node:fs/promises';

const { GITHUB_TOKEN, GITHUB_REPO, GITHUB_BRANCH = 'main' } = process.env;
const API = `https://api.github.com/repos/${GITHUB_REPO}/contents/catalog.json`;
const BUNDLED = new URL('../app/src/main/assets/data/', import.meta.url);

const CODES = {
  habitat: ['marine', 'freshwater', 'brackish', 'reef_associated'],
  feeding: ['carnivore', 'herbivore', 'omnivore', 'planktivore', 'detritivore'],
  vertical: ['pelagic', 'demersal', 'benthopelagic', 'reef'],
  iucn: ['lc', 'nt', 'vu', 'en', 'cr', 'dd', 'ne'],
};

const gh = (url, init = {}) =>
  fetch(url, {
    ...init,
    headers: { Authorization: `Bearer ${GITHUB_TOKEN}`, Accept: 'application/vnd.github+json', 'User-Agent': 'fishid-admin' },
    signal: AbortSignal.timeout(20_000),
  });

/** { catalog, sha }; before the first save, the app's bundled data (sha null). */
export async function load() {
  if (!GITHUB_TOKEN || !GITHUB_REPO) return { catalog: await bundled(), sha: null }; // preview mode (server.js)
  const res = await gh(`${API}?ref=${GITHUB_BRANCH}`);
  if (res.status === 404) return { catalog: await bundled(), sha: null };
  if (!res.ok) throw new Error(`GitHub answered ${res.status}`);
  const body = await res.json();
  return { catalog: merge(JSON.parse(Buffer.from(body.content, 'base64').toString('utf8')), await bundled()), sha: body.sha };
}

/**
 * A new model ships new species in the app's bundled data; the published catalog doesn't have them yet.
 * Adds them (with their text) so they can be edited, and keeps data_version at least the bundled one, so the
 * next save is newer than what the app update ships (phones ignore a catalog that isn't newer or drops a species).
 */
export function merge(published, bundled) {
  const have = new Set(published.species.map(s => s.id));
  const added = bundled.species.filter(s => !have.has(s.id));
  const text = { ...published.text };
  for (const lang of ['en', 'ta']) {
    text[lang] = { ...text[lang] };
    for (const s of added) if (bundled.text[lang]?.[s.id]) text[lang][s.id] = bundled.text[lang][s.id];
  }
  return { ...published, data_version: Math.max(published.data_version, bundled.data_version), species: [...published.species, ...added], text };
}

async function bundled() {
  const read = async f => JSON.parse(await readFile(new URL(f, BUNDLED), 'utf8'));
  return { ...(await read('species.json')), text: { en: await read('text.en.json'), ta: await read('text.ta.json') } };
}

/** Publishes [next] as a new data version. [sha] must match GitHub's, so two admins can't overwrite each other. */
export async function save(next, sha) {
  if (!GITHUB_TOKEN || !GITHUB_REPO) return { status: 503, error: 'Preview mode: set GITHUB_TOKEN and GITHUB_REPO on the server to publish.' };
  const cur = await load();
  if (sha !== cur.sha) return { status: 409, error: 'Someone saved in the meantime. Reload the page and redo your change.' };
  const error = validate(next, cur.catalog);
  if (error) return { status: 400, error };
  const today = new Date().toISOString().slice(0, 10);
  const catalog = { ...next, data_version: cur.catalog.data_version + 1, updated: today };
  const res = await gh(API, {
    method: 'PUT',
    body: JSON.stringify({
      message: `Species data v${catalog.data_version}`,
      content: Buffer.from(JSON.stringify(catalog, null, 2) + '\n').toString('base64'),
      branch: GITHUB_BRANCH,
      ...(cur.sha && { sha: cur.sha }),
    }),
  });
  if (res.status === 409 || res.status === 422) return { status: 409, error: 'Someone saved in the meantime. Reload the page and redo your change.' };
  if (!res.ok) throw new Error(`GitHub answered ${res.status}: ${await res.text()}`);
  return { status: 200, catalog, sha: (await res.json()).content.sha };
}

/**
 * Returns why [next] can't be published, or null. Mirrors what the app can decode (Species.kt): a catalog the app
 * rejects is silently ignored on every phone, so it must never get published. Copies images from [cur] (not editable here).
 */
export function validate(next, cur) {
  const ids = cur.species.map(s => s.id);
  if (!Array.isArray(next?.species) || next.species.length !== ids.length || !next.species.every(s => ids.includes(s?.id)))
    return 'Species can be edited here, not added or removed (that needs a model update).';
  const isInt = v => Number.isInteger(v) && v >= 0;
  const isNum = v => typeof v === 'number' && Number.isFinite(v) && v >= 0;
  const isStr = v => v == null || typeof v === 'string';
  for (const s of next.species) {
    const bad = what => `${s.scientific_name || s.id}: ${what}`;
    if (typeof s.scientific_name !== 'string' || !s.scientific_name.trim()) return bad('scientific name is required');
    for (const k of ['scientific_name_ta', 'local_name_ta', 'local_name_roman', 'price_updated', 'source'])
      if (!isStr(s[k])) return bad(`${k} must be text`);
    if (s.price_inr_per_kg != null && !(isInt(s.price_inr_per_kg) && s.price_inr_per_kg > 0)) return bad('price must be a whole number of rupees');
    if (!Array.isArray(s.habitat) || !s.habitat.every(h => CODES.habitat.includes(h))) return bad('unknown habitat');
    for (const k of ['feeding', 'vertical', 'iucn']) if (s[k] != null && !CODES[k].includes(s[k])) return bad(`unknown ${k}`);
    if (s.depth_m != null && !(Array.isArray(s.depth_m) && s.depth_m.length === 2 && s.depth_m.every(isInt) && s.depth_m[0] <= s.depth_m[1]))
      return bad('depth must be two whole numbers, shallow then deep');
    const m = s.meristics;
    if (m != null && !['dorsal_spines', 'dorsal_rays', 'anal_spines', 'anal_rays'].every(k => isInt(m[k]))) return bad('fin counts need all four whole numbers');
    const n = s.nutrition_g;
    if (n != null && !(['protein', 'fat', 'moisture'].every(k => isNum(n[k])) && ['ash', 'carbohydrate'].every(k => n[k] == null || isNum(n[k]))))
      return bad('nutrition needs protein, fat and moisture in grams');
    if (s.confidence_threshold != null && !(isNum(s.confidence_threshold) && s.confidence_threshold < 1)) return bad('match threshold must be between 0 and 1');
    s.images = cur.species.find(c => c.id === s.id).images;
  }
  for (const lang of ['en', 'ta']) {
    const text = next.text?.[lang];
    if (text == null || typeof text !== 'object') return `text.${lang} is missing`;
    for (const [id, t] of Object.entries(text)) {
      if (!['common_name', 'distribution'].every(k => isStr(t?.[k]))) return `${id}: ${lang} names must be text`;
      if (t.field_marks != null && !(Array.isArray(t.field_marks) && t.field_marks.every(x => typeof x === 'string')))
        return `${id}: ${lang} field marks must be a list of lines`;
    }
  }
  return null;
}

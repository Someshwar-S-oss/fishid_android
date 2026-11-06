// Pre-fills species facts from a FishBase species summary page (fishbase.se). The ropensci FishBase API
// (fishbase.ropensci.org) is retired, so this reads the public page instead. Anything it can't find is left out;
// an admin always reviews before saving.

const VERTICAL = [[/reef-associated/i, 'reef'], [/bentho-?pelagic|bathypelagic/i, 'benthopelagic'], [/demersal|benthic/i, 'demersal'], [/pelagic/i, 'pelagic']];
const IUCN = ['lc', 'nt', 'vu', 'en', 'cr', 'dd', 'ne'];

export function parse(html) {
  const text = html
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;?/g, ' ')
    .replace(/\s+/g, ' ');
  const out = {};

  const title = html.match(/<title>\s*[^,<]+,\s*([^:<]+?)\s*:/i);
  if (title) out.common_name = title[1].trim();

  // "Ecology Marine; brackish; reef-associated; depth range 1 - 80 m (Ref. 58302 ). Tropical; ..."
  const env = text.match(/Ecology (.{0,300})/)?.[1].split(/Distribution/)[0];
  if (env) {
    const habitat = ['marine', 'freshwater', 'brackish'].filter(w => new RegExp(`\\b${w}\\b`, 'i').test(env));
    if (/reef-associated/i.test(env)) habitat.push('reef_associated');
    if (habitat.length) out.habitat = habitat;
    const vertical = VERTICAL.find(([re]) => re.test(env));
    if (vertical) out.vertical = vertical[1];
    const depth = env.match(/depth range\s*(\d+|\?)\s*-\s*(\d+)\s*m/i);
    if (depth) out.depth_m = [depth[1] === '?' ? 0 : +depth[1], +depth[2]];
  }

  // Ranges like "22 - 25" keep the low end, as the bundled data does.
  const fin = re => +(text.match(re)?.[1] ?? NaN);
  const meristics = {
    dorsal_spines: fin(/Dorsal spines \(total\):\s*(\d+)/i),
    dorsal_rays: fin(/Dorsal soft rays \(total\):\s*(\d+)/i),
    anal_spines: fin(/Anal spines\s*:\s*(\d+)/i),
    anal_rays: fin(/Anal soft rays\s*:\s*(\d+)/i),
  };
  if (Object.values(meristics).every(Number.isInteger)) out.meristics = meristics;

  const iucn = text.match(/IUCN Red List Status.{0,400}?\(([A-Z]{2})\)/)?.[1].toLowerCase();
  if (IUCN.includes(iucn)) out.iucn = iucn;

  return out;
}

/**
 * [name] is "Genus species"; anything else is rejected so the request can only ever hit a FishBase summary page.
 * Non-fish (the crab) live on SeaLifeBase, same page layout, so that's tried second.
 */
export async function lookup(name) {
  const m = String(name).trim().match(/^([A-Z][a-z]+) ([a-z-]+)$/);
  if (!m) throw new Error('Scientific name must look like "Atule mate"');
  for (const host of ['www.fishbase.se', 'www.sealifebase.se']) {
    const url = `https://${host}/summary/${m[1]}-${m[2]}.html`;
    const res = await fetch(url, { signal: AbortSignal.timeout(20_000) });
    if (!res.ok && res.status !== 404) throw new Error(`${host} answered ${res.status}`);
    const facts = res.ok ? parse(await res.text()) : {};
    if (Object.keys(facts).length) return { url, facts };
  }
  throw new Error(`FishBase and SeaLifeBase have no summary for ${name}`);
}

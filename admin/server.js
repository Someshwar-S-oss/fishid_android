// FishID admin: edit species details and prices, publish them to the app. No dependencies (Node 22).
// Env: ADMIN_PASSWORD, GITHUB_TOKEN, GITHUB_REPO ("owner/fishid-data"), optional GITHUB_BRANCH, PORT. See README.md.
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { load, save } from './catalog.js';
import { lookup } from './fishbase.js';

const { ADMIN_PASSWORD, GITHUB_TOKEN, GITHUB_REPO, PORT = 3000 } = process.env;
if (!ADMIN_PASSWORD) {
  console.error('Set ADMIN_PASSWORD (and GITHUB_TOKEN + GITHUB_REPO to publish)');
  process.exit(1);
}
if (!GITHUB_TOKEN || !GITHUB_REPO) console.warn('No GITHUB_TOKEN/GITHUB_REPO: preview mode, showing the bundled data; saving is disabled.');

const sha256 = s => createHash('sha256').update(s).digest();
const passwordOk = pass => timingSafeEqual(sha256(pass), sha256(ADMIN_PASSWORD));

// Sign-in: a signed "<issued ms>.<hmac>" cookie, valid 30 days. Changing ADMIN_PASSWORD signs everyone out.
const MAX_AGE = 30 * 24 * 3600;
const sign = ts => createHmac('sha256', ADMIN_PASSWORD).update(`fishid-admin:${ts}`).digest('base64url');
function authed(req) {
  const [ts, mac] = (req.headers.cookie ?? '').match(/(?:^|;\s*)fishid=([^;]+)/)?.[1].split('.') ?? [];
  if (!ts || !mac || Date.now() - Number(ts) > MAX_AGE * 1000) return false;
  const want = Buffer.from(sign(ts)), got = Buffer.from(mac);
  return want.length === got.length && timingSafeEqual(want, got);
}
const cookie = (req, value, maxAge) =>
  `fishid=${value}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${maxAge}${req.headers['x-forwarded-proto'] === 'https' ? '; Secure' : ''}`;
const redirect = (res, to, headers = {}) => { res.writeHead(303, { Location: to, ...headers }); res.end(); };
const file = name => readFile(new URL(name, import.meta.url));

const send = (res, status, body, type = 'application/json') => {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store' });
  res.end(type === 'application/json' ? JSON.stringify(body) : body);
};

async function readText(req) {
  let size = 0;
  const chunks = [];
  for await (const c of req) {
    if ((size += c.length) > 2_000_000) throw Object.assign(new Error('Too large'), { status: 413 });
    chunks.push(c);
  }
  return Buffer.concat(chunks).toString('utf8');
}
const readBody = async req => JSON.parse(await readText(req));

http
  .createServer(async (req, res) => {
    const url = new URL(req.url, 'http://x');
    try {
      if (url.pathname === '/health') return send(res, 200, 'ok', 'text/plain');
      if (url.pathname === '/logo.svg') {
        res.writeHead(200, { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'public, max-age=86400' });
        return res.end(await file('logo.svg'));
      }
      if (url.pathname === '/login' && req.method === 'POST') {
        const pass = new URLSearchParams(await readText(req)).get('password') ?? '';
        if (!passwordOk(pass)) {
          await new Promise(r => setTimeout(r, 1000)); // ponytail: slows guessing; add per-IP limits if it's ever attacked
          return redirect(res, '/login?wrong=1');
        }
        const ts = String(Date.now());
        return redirect(res, '/', { 'Set-Cookie': cookie(req, `${ts}.${sign(ts)}`, MAX_AGE) });
      }
      if (url.pathname === '/logout' && req.method === 'POST') return redirect(res, '/login', { 'Set-Cookie': cookie(req, '', 0) });
      if (url.pathname === '/login') return send(res, 200, await file('login.html'), 'text/html; charset=utf-8');
      if (!authed(req)) return url.pathname.startsWith('/api/') ? send(res, 401, { error: 'Signed out. Reload to sign in again.' }) : redirect(res, '/login');

      if (req.method === 'GET' && url.pathname === '/') return send(res, 200, await file('index.html'), 'text/html; charset=utf-8');
      // The app's own UI words, for the phone preview (missing Tamil falls back to English, as in src/i18n.ts).
      if (req.method === 'GET' && url.pathname === '/api/ui') {
        const read = async f => JSON.parse(await readFile(new URL(`../i18n/${f}`, import.meta.url), 'utf8'));
        const [en, ta, fixed] = await Promise.all(['ui.en.json', 'ui.ta.json', 'ui.fixed.json'].map(read));
        return send(res, 200, { en: { ...en, ...fixed }, ta: { ...en, ...ta, ...fixed } });
      }
      if (req.method === 'GET' && url.pathname === '/api/catalog')
        return send(res, 200, { ...(await load()), repo: GITHUB_REPO, branch: process.env.GITHUB_BRANCH ?? 'main' });
      // The app's bundled photos, for the species list and header.
      const img = req.method === 'GET' && url.pathname.match(/^\/img\/([\w-]+\.(jpg|jpeg|png|webp))$/);
      if (img) {
        const file = await readFile(new URL(`../app/src/main/assets/data/images/${img[1]}`, import.meta.url)).catch(() => null);
        if (!file) return send(res, 404, { error: 'No such photo' });
        res.writeHead(200, { 'Content-Type': `image/${img[2] === 'jpg' ? 'jpeg' : img[2]}`, 'Cache-Control': 'private, max-age=86400' });
        return res.end(file);
      }
      if (req.method === 'PUT' && url.pathname === '/api/catalog') {
        const { catalog, sha } = await readBody(req);
        const r = await save(catalog, sha);
        return send(res, r.status, r.error ? { error: r.error } : { catalog: r.catalog, sha: r.sha });
      }
      if (req.method === 'GET' && url.pathname === '/api/fishbase') return send(res, 200, await lookup(url.searchParams.get('name')));
      send(res, 404, { error: 'Not found' });
    } catch (e) {
      console.error(e);
      send(res, e.status ?? (e instanceof SyntaxError ? 400 : 502), { error: e.message });
    }
  })
  .listen(PORT, () => console.log(`FishID admin on :${PORT}`));

// Head-only prerendering for static/listing routes.
//
// Why: the app is a client-side SPA, so nginx serves the same generic index.html
// for every static route. To bots (incl. Googlebot) every URL looks byte-identical
// with the same <title> and no <link rel="canonical"> -> "Duplicate without
// user-selected canonical" in Search Console. Detail pages (/kadra/<id>,
// /{cat}/rodzaj|termin/<id>) are already handled for bots by the backend OgController
// via nginx rewrites; only the static/listing routes lack per-page <head>.
//
// This runs after `vite build`. It clones dist/index.html for each static route and
// rewrites <title>, <meta name="description">, og:title/og:description, and injects
// <link rel="canonical"> + <meta property="og:url">. Body stays SPA-rendered.
//
// Meta mirrors what <Seo> (src/components/seo/Seo.tsx) sets on each page so the raw
// HTML matches the post-hydration DOM (no title flicker / mismatch). Listing titles +
// descriptions mirror EventsPage.seoTitleMap / descriptionMap (SEO copy, not the visible
// <h1>); the home title mirrors HomePage's <Seo title>.
//
// Single language (pl) -> no hreflang. Resilient: any failure logs a warning and
// keeps the SPA fallback (exit code stays 0 so the build never breaks).

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SITE_NAME = 'Fire Academy'
const BASE_URL = 'https://fireworkout.pl'
// Mirrors HomePage's <Seo title> (brand suffix appended below) + Seo.tsx DEFAULT_DESCRIPTION.
const HOME_TITLE = 'Szkoła sztuk walki Katowice — MMA, kickboxing, boks'
const DEFAULT_DESCRIPTION =
  'Szkoła sztuk walki w Katowicach — MMA, kickboxing, boks, zapasy i przygotowanie motoryczne. Trening personalny w małych grupach, realne efekty.'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')
const distIndex = join(root, 'dist', 'index.html')

function escapeAttr(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeText(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

try {
  const template = readFileSync(distIndex, 'utf-8')

  // Titles + descriptions mirror src/pages/EventsPage.tsx seoTitleMap / descriptionMap
  // and HomePage's <Seo> — keep in sync.
  const routes = [
    { path: '/', title: HOME_TITLE, description: DEFAULT_DESCRIPTION },
    { path: '/treningi', title: 'Treningi sztuk walki i przygotowanie motoryczne Katowice', description: 'Treningi sztuk walki w Katowicach — MMA, kickboxing, boks, zapasy. Personalne i małe grupy (4–6 osób), indywidualne podejście i realne efekty.' },
    { path: '/obozy', title: 'Obozy sportowe sztuk walki — Katowice i okolice', description: 'Obozy sportowe ze sztuk walki i przygotowania motorycznego z Fire Academy. Sprawdź terminy, programy i dostępne miejsca.' },
    { path: '/szkolenia', title: 'Szkolenia i kursy trenerskie sztuk walki', description: 'Szkolenia i kursy trenerskie ze sztuk walki i przygotowania motorycznego. Podnieś kwalifikacje z doświadczoną kadrą Fire Academy.' },
    { path: '/polityka-prywatnosci', title: 'Polityka prywatności', description: 'Polityka prywatności Fire Academy — jakie dane zbieramy, w jakim celu, jak długo je przechowujemy i jakie prawa Ci przysługują.' },
  ]

  let written = 0
  for (const route of routes) {
    // Matches Seo.tsx: SITE_NAME stays as-is, otherwise "Title | Fire Academy".
    const fullTitle = route.title === SITE_NAME ? SITE_NAME : `${route.title} | ${SITE_NAME}`
    const url = `${BASE_URL}${route.path}`
    const desc = route.description

    let html = template

    html = html.replace(/<title>[\s\S]*?<\/title>/, `<title>${escapeText(fullTitle)}</title>`)
    html = html.replace(
      /<meta\s+name="description"\s+content="[^"]*"\s*\/?>/,
      `<meta name="description" content="${escapeAttr(desc)}" />`,
    )
    html = html.replace(
      /<meta\s+property="og:title"\s+content="[^"]*"\s*\/?>/,
      `<meta property="og:title" content="${escapeAttr(fullTitle)}" />`,
    )
    html = html.replace(
      /<meta\s+property="og:description"\s+content="[^"]*"\s*\/?>/,
      `<meta property="og:description" content="${escapeAttr(desc)}" />`,
    )

    // canonical + og:url (template has neither) injected before </head>.
    const inject =
      `    <link rel="canonical" href="${escapeAttr(url)}" />\n` +
      `    <meta property="og:url" content="${escapeAttr(url)}" />\n`
    html = html.replace('</head>', `${inject}  </head>`)

    // Flat dist/<path>.html so nginx serves /treningi from /treningi.html with no
    // trailing-slash redirect and the served URL matches the canonical exactly.
    const outFile =
      route.path === '/'
        ? distIndex
        : join(root, 'dist', `${route.path.replace(/^\//, '')}.html`)
    mkdirSync(dirname(outFile), { recursive: true })
    writeFileSync(outFile, html, 'utf-8')
    written++
  }

  console.log(`[prerender] generated ${written} static route page(s)`)
} catch (err) {
  console.warn(`[prerender] skipped (SPA fallback kept): ${err?.message ?? err}`)
}

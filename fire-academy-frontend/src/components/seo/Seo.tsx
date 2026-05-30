import { Helmet } from 'react-helmet-async'

const SITE_NAME = 'Fire Academy'
const DEFAULT_DESCRIPTION = 'Fire Academy — treningi indywidualne i małe grupy. Obozy, szkolenia i kursy dla ambitnych sportowców.'
const DEFAULT_IMAGE = '/og-default.png'

export interface Breadcrumb {
  name: string
  path: string
}

interface SeoProps {
  title: string
  description?: string
  path: string
  image?: string | null
  type?: 'website' | 'article'
  jsonLd?: Record<string, unknown> | Record<string, unknown>[]
  breadcrumbs?: Breadcrumb[]
}

export function Seo({ title, description, path, image, type = 'website', jsonLd, breadcrumbs }: SeoProps) {
  const fullTitle = title === SITE_NAME ? title : `${title} | ${SITE_NAME}`
  const desc = description || DEFAULT_DESCRIPTION
  const img = image || DEFAULT_IMAGE
  const canonical = `${window.location.origin}${path}`
  const imgUrl = img.startsWith('http') ? img : `${window.location.origin}${img}`

  const allJsonLd: Record<string, unknown>[] = []

  if (breadcrumbs && breadcrumbs.length > 0) {
    allJsonLd.push({
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: breadcrumbs.map((bc, i) => ({
        '@type': 'ListItem',
        position: i + 1,
        name: bc.name,
        item: `${window.location.origin}${bc.path}`,
      })),
    })
  }

  if (jsonLd) {
    if (Array.isArray(jsonLd)) {
      allJsonLd.push(...jsonLd)
    } else {
      allJsonLd.push(jsonLd)
    }
  }

  return (
    <Helmet>
      <title>{fullTitle}</title>
      <meta name="description" content={desc} />
      <link rel="canonical" href={canonical} />

      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={desc} />
      <meta property="og:image" content={imgUrl} />
      <meta property="og:url" content={canonical} />
      <meta property="og:type" content={type} />
      <meta property="og:site_name" content={SITE_NAME} />
      <meta property="og:locale" content="pl_PL" />

      {allJsonLd.length > 0 && (
        <script type="application/ld+json">
          {JSON.stringify(allJsonLd.length === 1 ? allJsonLd[0] : allJsonLd)}
        </script>
      )}
    </Helmet>
  )
}

const QUERY = '(prefers-reduced-motion: reduce)'

/**
 * Whether the visitor has asked their system for less motion.
 *
 * A plain function rather than a hook: the only caller reads it once, to decide whether to mount the
 * homepage intro at all. Nobody flips this setting mid-visit to re-watch an animation they already
 * sat through, so subscribing to changes would buy nothing and cost a listener on every render.
 *
 * jsdom ships no matchMedia, and the prerendered HTML has no window at all — both answer "no
 * preference", which is the same default a browser gives when the setting was never touched.
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia(QUERY).matches
}

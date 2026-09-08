import { test as setup, expect } from '@playwright/test'

/**
 * Refuses to run the suite against somebody else's application.
 *
 * The dev server is reused when one is already listening (see `reuseExistingServer` in
 * playwright.config.ts), and reuse asks only "is something answering on this port". It is not — the
 * sibling climbing project this repo was templated from serves its own dev build on 5174 too, so
 * with that one running the whole golden path executes against it. What comes back is nine failing
 * tests describing headings and links that were never Fire Academy's, and the natural reading of
 * that is a regression here. It cost a quarter of an hour to work out the first time it happened,
 * and nothing in the output pointed at the real cause.
 *
 * A setup project rather than an assertion inside the suite: the browser projects depend on this
 * one, so a mismatch stops the run with a single explanatory failure instead of letting nine
 * misleading ones through. Checked with a plain request rather than a page — no browser needs to
 * start to read a <title>.
 *
 * Deliberately not solved by changing the port: 5174 is written into CLAUDE.md, the Vite proxy and
 * the Google OAuth redirect URI registered in the console. Nor by switching reuse off, which would
 * break the ordinary case where the dev server that is already up is the right one.
 */
setup('the server under test is Fire Academy, not another project on the same port', async ({ request, baseURL }) => {
  const response = await request.get('/')
  const html = await response.text()
  const title = html.match(/<title>(.*?)<\/title>/s)?.[1]?.trim() ?? '(no <title>)'

  // Asserted on the title rather than the whole document: a failed toContain prints the received
  // value, and printing an entire HTML page buries the explanation under it.
  expect(
    title,
    `${baseURL} is serving "${title}", which is not Fire Academy.\n\n`
      + 'Something else already holds that port and Playwright reused it — the sibling climbing '
      + 'project uses the same one. Stop that dev server (or start this one on a free port) and run '
      + 'again. Every test below would otherwise be describing the wrong application.',
  ).toContain('Fire Academy')
})

import { test, expect, type Page } from '@playwright/test'

/**
 * The unauthenticated golden path: what a stranger sees before they have an account.
 *
 * The API is STUBBED rather than reached. CI runs the frontend alone, so a suite that talked to a
 * real backend would either need one booted alongside (slow, and flaky in a way that teaches people
 * to ignore red builds) or would quietly degrade into asserting error states. Stubbing makes the
 * run deterministic and keeps every assertion about something the frontend actually owns.
 *
 * Three of these tests previously asserted nothing that could fail — `goto('/obozy')` followed by
 * "is the URL /obozy" passes on a blank page — so each one here names something visible on screen.
 */

const SLOT = {
  id: '11111111-1111-1111-1111-111111111111',
  eventTypeId: '22222222-2222-2222-2222-222222222222',
  eventTypeName: 'Kickboxing — mała grupa',
  instructorId: null, instructorName: null,
  dayOfWeek: 1, startTime: '10:00:00', endTime: '11:00:00',
  price: 60.0, maxParticipants: 6, availableSpots: 6, cancelledDates: [],
}

const EVENT = {
  id: '33333333-3333-3333-3333-333333333333',
  eventTypeId: null,
  eventTypeName: 'Obóz sportowy — Beskidy',
  description: 'Kilkudniowy obóz treningowy w górach.',
  startDate: '2099-09-18', endDate: '2099-09-23',
  startTime: '09:00:00', endTime: '16:00:00',
  location: 'Szczyrk', price: 1000.0, maxParticipants: 16, availableSpots: 14,
}

const EVENT_TYPE = {
  id: '44444444-4444-4444-4444-444444444444',
  name: 'Obóz sportowy', description: 'Kilkudniowy obóz treningowy.',
  thumbnailUrl: null, photos: [],
}

const INSTRUCTOR = {
  id: '55555555-5555-5555-5555-555555555555',
  firstName: 'Robert', lastName: 'Król',
  bio: 'Prowadzi obozy i szkolenia specjalistyczne.', photoUrl: null,
}

/**
 * Answers every public read with a fixture; anything else 404s loudly rather than hanging.
 *
 * The pattern is a regex anchored on the path, NOT the glob `**\/api\/**`. In dev Vite serves the
 * app's own modules over HTTP, and this project has a `src/api/` directory — so the glob matched
 * `/src/api/client.ts` too and handed the browser JSON where it expected JavaScript. Every page
 * then rendered blank, which looks exactly like a broken app rather than a broken stub.
 */
async function stubApi(page: Page) {
  await page.route(/^https?:\/\/[^/]+\/api\//, async route => {
    const url = route.request().url()
    const body = (data: unknown) =>
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) })

    if (url.includes('/training-slots')) return body([SLOT])
    if (url.includes('/training-holidays')) return body([])
    if (url.includes('/event-types')) return body([EVENT_TYPE])
    if (url.includes('/instructors')) return body([INSTRUCTOR])
    if (url.includes('/events')) return body([EVENT])
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
}

test.beforeEach(async ({ page }) => { await stubApi(page) })

test.describe('Golden Path — gość', () => {
  test('strona główna prowadzi do sekcji Treningi', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveTitle(/Fire Academy/)

    // The hero intro covers the page for ~3s and swallows pointer events until it exits. Waiting for
    // it to go is the assertion, not a workaround: if it ever stops finishing, the homepage stays
    // permanently unclickable and nothing else here would notice.
    await expect(page.locator('div.fixed.inset-0.z-50')).toHaveCount(0, { timeout: 10_000 })

    // Click the section heading, not the link box. Each section link is `absolute inset-0` with a
    // clip-path, so all three share the full-viewport bounding box and its centre — Playwright's
    // click target — lands inside whichever polygon happens to cover the middle, not this one.
    const trainings = page.locator('a[href="/treningi"] h2').first()
    await expect(trainings).toBeVisible()
    await trainings.click()
    await expect(page).toHaveURL(/\/treningi$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Treningi' })).toBeVisible()
  })

  test('strona treningów pokazuje grafik zajęć', async ({ page }) => {
    await page.goto('/treningi')
    await expect(page.getByRole('heading', { level: 1, name: 'Treningi' })).toBeVisible()
    // The weekday group carries the slot; its presence proves the schedule rendered, not just the shell.
    await expect(page.getByText('Poniedziałek', { exact: false })).toBeVisible()
  })

  test('strona obozów pokazuje termin z godziną bez sekund', async ({ page }) => {
    await page.goto('/obozy')
    await expect(page.getByRole('heading', { level: 1, name: 'Obozy' })).toBeVisible()
    await expect(page.getByText('Obóz sportowy — Beskidy').first()).toBeVisible()
    // The API serialises LocalTime with seconds. Anything rendering "09:00:00" to a customer is a bug.
    await expect(page.getByText('09:00', { exact: false }).first()).toBeVisible()
    await expect(page.getByText('09:00:00', { exact: false })).toHaveCount(0)
  })

  test('strona szkoleń się otwiera', async ({ page }) => {
    await page.goto('/szkolenia')
    await expect(page.getByRole('heading', { level: 1, name: 'Szkolenia' })).toBeVisible()
  })

  test('nawigacja ma sekcje i zaproszenie do logowania', async ({ page }) => {
    // Deliberately NOT the homepage: the navbar is hidden there by design (full-screen splash), so
    // asserting it on "/" is what made the old version of this test fail against correct behaviour.
    await page.goto('/treningi')
    const nav = page.locator('nav')
    await expect(nav.getByRole('link', { name: 'Treningi' })).toBeVisible()
    await expect(nav.getByRole('link', { name: 'Obozy' })).toBeVisible()
    await expect(nav.getByRole('link', { name: 'Szkolenia' })).toBeVisible()
    await expect(page.getByRole('link', { name: /Zaloguj/i }).first()).toBeVisible()
  })

  test('stopka ma link do polityki prywatności', async ({ page }) => {
    await page.goto('/treningi')
    const footer = page.locator('footer')
    await expect(footer).toBeVisible()
    await expect(footer.getByRole('link', { name: /Polityka prywatności/i })).toBeVisible()
  })

  test('polityka prywatności ma treść', async ({ page }) => {
    await page.goto('/polityka-prywatnosci')
    await expect(page.getByRole('heading', { level: 1, name: /Polityka prywatności/i })).toBeVisible()
  })

  test('panel admina odsyła gościa do logowania', async ({ page }) => {
    await page.goto('/admin')
    // The Polish route. The old test still expected /admin/login, which stopped existing when the
    // routes were renamed — it had been failing ever since, unnoticed, because CI never ran it.
    await expect(page).toHaveURL(/\/logowanie$/)
  })

  test('przy prośbie o mniej animacji intro w ogóle się nie pokazuje', async ({ page }) => {
    // The intro blocks every click for ~3s. Someone who set "reduce motion" has asked not to be made
    // to sit through that, and index.css already honoured the request for the decoration inside it —
    // the overlay was simply missed.
    // Set on the page rather than through `test.use({ reducedMotion })`: the fixture form did not
    // reach the document here — matchMedia still reported no preference — so the test would have
    // been green for the wrong reason. This form was checked against the browser directly.
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/')

    const trainings = page.locator('a[href="/treningi"] h2').first()
    await expect(trainings).toBeAttached()   // React has mounted; the page is worth inspecting

    // A one-shot count, NOT `await expect(locator).toHaveCount(0)`. Web-first assertions retry for
    // five seconds, and the intro leaves on its own after three — so the retrying form passed just
    // as happily with this fix reverted, which is to say it asserted nothing. Checked both ways.
    expect(await page.locator('div.fixed.inset-0.z-50').count()).toBe(0)
    await expect(trainings).toBeVisible()
    await trainings.click()
    await expect(page).toHaveURL(/\/treningi$/)
  })

  test('nieznany adres pokazuje stronę 404, nie pustkę', async ({ page }) => {
    await page.goto('/tego-adresu-nie-ma')
    await expect(page.getByRole('heading', { level: 1, name: /Nie ma takiej strony/i })).toBeVisible()
    // Navbar and footer stay: someone who got lost needs the way out most.
    await expect(page.locator('nav')).toBeVisible()
    await expect(page.locator('footer')).toBeVisible()
    // An empty page answering 200 is a soft 404; the noindex is what stops it being indexed as real.
    await expect(page.locator('meta[name="robots"][content*="noindex"]')).toHaveCount(1)
  })
})

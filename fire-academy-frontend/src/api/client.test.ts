import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// The real i18n bundle pulls in the whole locale set; the client only ever asks it for the current
// language and an error string, so a stub keeps this about the retry rule and nothing else.
vi.mock('../i18n', () => ({
  default: { language: 'pl', t: (key: string) => key },
}))
vi.mock('./auth', () => ({ refreshTokens: vi.fn() }))

import { fetchApi } from './client'

/** Minimal stand-in for what fetchApi reads off a Response. */
function response(status: number, body: unknown = {}): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response
}

describe('fetchApi retries', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  /**
   * A read may be sent again freely: worst case the server does the same lookup twice. This is what
   * keeps a redeploy — during which the backend is unreachable for a minute or two — from turning
   * into an error screen.
   */
  it('shouldRetryGetAfterNetworkError', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error('connection reset'))
      .mockResolvedValueOnce(response(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    const pending = fetchApi<{ ok: boolean }>('/public/events')
    await vi.advanceTimersByTimeAsync(2_000)

    await expect(pending).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  /**
   * The failure this guards against: the request reached the server, the training was created, and
   * only the answer was lost. Sending it again puts a second session in someone's plan — so a write
   * is reported as failed rather than quietly repeated.
   */
  it('shouldNotRetryPostAfterNetworkError', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('connection reset'))
    vi.stubGlobal('fetch', fetchMock)

    const pending = fetchApi('/user/my-training/trainings', {
      method: 'POST',
      body: JSON.stringify({ title: 'Trening' }),
    })
    await expect(pending).rejects.toThrow()
    await vi.advanceTimersByTimeAsync(5_000)

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shouldRetryGetOnGatewayError', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(502))
      .mockResolvedValueOnce(response(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    const pending = fetchApi<{ ok: boolean }>('/public/events')
    await vi.advanceTimersByTimeAsync(5_000)

    await expect(pending).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  /** A 502 from a backend on its way down can follow a request it already committed. */
  it('shouldNotRetryDeleteOnGatewayError', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(503, { message: 'niedostępne' }))
    vi.stubGlobal('fetch', fetchMock)

    const pending = fetchApi('/user/my-training/trainings/abc', { method: 'DELETE' })
    await expect(pending).rejects.toThrow()
    await vi.advanceTimersByTimeAsync(15_000)

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { RecurringTile } from './RecurringTile'
import type { RecurringSession } from '../../types'

const session: RecurringSession = {
  date: '2026-09-02',
  slotId: 's1',
  name: 'Boks',
  instructorName: 'Jan Kowalski',
  startTime: '18:00:00',
  endTime: '19:00:00',
}

describe('RecurringTile', () => {
  it('stays a plain, inert tile for the client', () => {
    // The client has nothing to edit here — the session is computed from their subscription and has
    // no row to change. A click target would promise something the API cannot do.
    render(<RecurringTile session={session} label="Zajęcia grupowe" />)

    expect(screen.getByText('Boks')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('becomes openable for the coach, who is reaching their own notebook', () => {
    render(<RecurringTile session={session} label="Zajęcia grupowe"
      onOpen={() => {}} openLabel="Prywatna notatka" />)

    expect(screen.getByRole('button', { name: 'Prywatna notatka' })).toBeInTheDocument()
  })

  it('goes inert while the clipboard is armed, so one tap cannot both paste and open a note', () => {
    // The armed day is the drop target and the training cards already go inert for this reason. A
    // live button here would fire its own onClick AND bubble the click to the day, doing both.
    render(<RecurringTile session={session} label="Zajęcia grupowe"
      onOpen={undefined} openLabel="Prywatna notatka" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

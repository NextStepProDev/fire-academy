import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DayColumn } from './DayColumn'
import type { RecurringSession } from '../../types'

const session: RecurringSession = {
  date: '2026-09-02', slotId: 's1', name: 'Boks', instructorName: null,
  startTime: '18:00:00', endTime: '19:00:00',
}

const labels = {
  add: 'Dodaj', copy: 'Kopiuj', cut: 'Wytnij', pasteHere: 'Wklej tutaj', unread: 'Nowe',
  comments: 'Komentarze', recurring: 'Grupowe', task: 'Zadanie', calories: 'kcal',
  openSession: 'Moja notatka', note: 'Jest notatka',
}

function renderColumn(pasteArmed: boolean) {
  const onOpenSession = vi.fn()
  render(
    <DayColumn
      date="2026-09-02" anchor="2026-09-02" trainings={[]} recurring={[session]}
      onOpenSession={onOpenSession} pasteArmed={pasteArmed} cutId={null}
      onOpen={vi.fn()} onAdd={vi.fn()} onPaste={vi.fn()} onCopy={vi.fn()} onCut={vi.fn()}
      canReshape={() => true} labels={labels}
    />,
  )
  return onOpenSession
}

describe('DayColumn — group session vs the clipboard', () => {
  it('offers the note only when the clipboard is idle', () => {
    renderColumn(false)
    expect(screen.getByRole('button', { name: 'Moja notatka' })).toBeInTheDocument()
  })

  it('withholds it while the clipboard is armed', () => {
    // With the clipboard armed the whole day is the drop target. A live button on the group tile
    // would open the note AND bubble the click into a paste — both from one tap.
    renderColumn(true)
    expect(screen.queryByRole('button', { name: 'Moja notatka' })).not.toBeInTheDocument()
  })
})

import { useCallback, useEffect, useState } from 'react'
import type { PasteMode } from '../types'

const STORAGE_KEY = 'fa.training-clipboard'

export interface TrainingClipboardEntry {
  trainingId: string
  title: string
  mode: PasteMode
}

/**
 * Copy/cut/paste state for the calendar.
 *
 * Kept in sessionStorage rather than component state so it survives paging between weeks and months
 * — copying a training and then navigating to the target week is the whole point, and a state reset
 * on the way there would make the feature useless. Cleared when the tab closes.
 */
export function useTrainingClipboard() {
  const [entry, setEntry] = useState<TrainingClipboardEntry | null>(() => read())

  // Keep two tabs of the same calendar in step.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === STORAGE_KEY) setEntry(read())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const clear = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    setEntry(null)
  }, [])

  const put = useCallback((next: TrainingClipboardEntry) => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setEntry(next)
  }, [])

  // Escape disarms the clipboard — otherwise every empty day stays a paste target with no way out.
  useEffect(() => {
    if (!entry) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') clear()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [entry, clear])

  return { entry, copy: put, clear }
}

function read(): TrainingClipboardEntry | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as TrainingClipboardEntry) : null
  } catch {
    return null
  }
}

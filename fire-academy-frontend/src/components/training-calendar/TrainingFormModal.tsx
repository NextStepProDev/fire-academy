import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { ApiError } from '../../api/client'
import { formatLongDate } from '../../utils/calendarRange'
import type { CreateTrainingBody, PersonalTraining } from '../../types'

const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

interface TrainingFormModalProps {
  /** Present when editing, absent when creating. */
  training: PersonalTraining | null
  date: string
  onClose: () => void
  /** Rejects with the server message on failure; the modal must stay open in that case. */
  onSubmit: (body: CreateTrainingBody) => Promise<unknown>
  onConflictRefresh: () => void
}

/**
 * Rendered only while open, and keyed by what it is editing, so every open starts from fresh state.
 * That is deliberately not an effect syncing props into state — remounting is both simpler and free
 * of the cascading render an effect would cause.
 */
export function TrainingFormModal({
  training, date, onClose, onSubmit, onConflictRefresh,
}: TrainingFormModalProps) {
  const { t } = useTranslation('calendar')

  const [title, setTitle] = useState(training?.title ?? '')
  const [description, setDescription] = useState(training?.description ?? '')
  // A new training defaults to no hour: that is the normal case, and forcing a time on every entry
  // would make the coach invent one.
  const [timed, setTimed] = useState(training?.startTime != null)
  const [startTime, setStartTime] = useState(training?.startTime?.slice(0, 5) ?? '17:00')
  const [endTime, setEndTime] = useState(training?.endTime?.slice(0, 5) ?? '')
  const [error, setError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)
  const [saving, setSaving] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!title.trim() || saving) return
    setError(null)
    setConflict(false)
    setSaving(true)
    try {
      // Awaited on purpose: collapsing the form before the server confirms would tell the user their
      // training was saved when it may not have been.
      await onSubmit({
        date,
        title: title.trim(),
        description: description.trim() || null,
        startTime: timed ? startTime : null,
        endTime: timed && endTime ? endTime : null,
      })
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      // Only a 409 is worth offering a refresh for — the row moved under us and the form is stale.
      setConflict(e instanceof ApiError && e.isConflict)
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={training ? t('form.editTitle') : t('form.createTitle')}
    >
      <form onSubmit={submit} className="space-y-4">
        <p className="text-sm text-surface-400">{formatLongDate(date)}</p>

        <div>
          <label htmlFor="training-title" className="block text-sm text-surface-300 mb-1">
            {t('form.title')}
          </label>
          <input
            id="training-title"
            className={inputClass}
            value={title}
            maxLength={150}
            onChange={e => setTitle(e.target.value)}
            autoFocus
          />
        </div>

        <div>
          <label htmlFor="training-description" className="block text-sm text-surface-300 mb-1">
            {t('form.description')}
          </label>
          <textarea
            id="training-description"
            className={`${inputClass} min-h-24`}
            value={description}
            maxLength={2000}
            onChange={e => setDescription(e.target.value)}
          />
        </div>

        <div className="space-y-3">
          <label className="flex items-center gap-2 text-sm text-surface-300">
            <input
              type="checkbox"
              checked={timed}
              onChange={e => setTimed(e.target.checked)}
              className="h-4 w-4 accent-primary-500"
            />
            {t('form.setTime')}
          </label>
          {timed && (
            <div className="flex items-center gap-3">
              <div>
                <label htmlFor="training-start" className="block text-xs text-surface-400 mb-1">
                  {t('form.startTime')}
                </label>
                <input id="training-start" type="time" className={inputClass}
                  value={startTime} onChange={e => setStartTime(e.target.value)} />
              </div>
              <div>
                <label htmlFor="training-end" className="block text-xs text-surface-400 mb-1">
                  {t('form.endTime')}
                </label>
                <input id="training-end" type="time" className={inputClass}
                  value={endTime} onChange={e => setEndTime(e.target.value)} />
              </div>
            </div>
          )}
        </div>

        {/* Errors belong here, next to the button that caused them — a toast at the edge of the
            screen is easy to miss, and the user is looking at Save. */}
        {error && (
          <div role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
            <p>{error}</p>
            {conflict && (
              <button type="button" onClick={onConflictRefresh}
                className="mt-1 underline underline-offset-2 hover:text-rose-200">
                {t('form.refresh')}
              </button>
            )}
          </div>
        )}

        <div className="flex justify-end gap-3">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            {t('form.cancel')}
          </Button>
          <Button type="submit" variant="primary" size="sm" loading={saving} disabled={!title.trim()}>
            {t('form.save')}
          </Button>
        </div>
      </form>
    </Modal>
  )
}

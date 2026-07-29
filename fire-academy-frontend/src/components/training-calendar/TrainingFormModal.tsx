import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Film, Link as LinkIcon, X } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { ApiError } from '../../api/client'
import { VideoPickerModal } from '../exercise-videos/VideoPickerModal'
import { formatLongDate } from '../../utils/calendarRange'
import type { AttachmentKind, CreateTrainingBody, PersonalTraining } from '../../types'

/** Three materials fit on a card without turning it into a reading list. */
const MAX_MATERIALS = 3

const inputClass = 'w-full px-3 py-2 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500'

interface TrainingFormModalProps {
  /** Present when editing, absent when creating. */
  training: PersonalTraining | null
  date: string
  onClose: () => void
  /** Rejects with the server message on failure; the modal must stay open in that case. */
  onSubmit: (body: CreateTrainingBody) => Promise<unknown>
  onConflictRefresh: () => void
  /** The library lives in the admin panel, so only the coach can attach from it. */
  canAttach: boolean
}

/**
 * Rendered only while open, and keyed by what it is editing, so every open starts from fresh state.
 * That is deliberately not an effect syncing props into state — remounting is both simpler and free
 * of the cascading render an effect would cause.
 */
export function TrainingFormModal({
  training, date, onClose, onSubmit, onConflictRefresh, canAttach,
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
  // Seeded from what the training already has, so saving without touching materials sends the same
  // list back rather than an empty one.
  const [materials, setMaterials] = useState<AttachmentDraft[]>(
    () => (training?.attachments ?? []).map(a => ({
      kind: a.kind,
      label: a.label,
      url: a.url,
      videoId: a.videoId,
      displayName: a.kind === 'VIDEO' ? (a.videoName ?? a.label ?? '') : (a.label ?? a.url ?? ''),
      thumbnailUrl: a.thumbnailUrl,
    })))
  const [pickerOpen, setPickerOpen] = useState(false)
  const [linkUrl, setLinkUrl] = useState('')

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
        // Always explicit from this form: the user saw the list and either kept or changed it.
        attachments: materials.map(m => ({
          kind: m.kind, label: m.label, url: m.url, videoId: m.videoId,
        })),
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

        {/* Materials. Videos come from the shared library so a correction there fixes every
            training at once; a plain link covers the one-off case. */}
        {canAttach && (
          <div className="space-y-2">
            <span className="block text-sm text-surface-300">
              {t('materials.title')} <span className="text-surface-500">({materials.length}/{MAX_MATERIALS})</span>
            </span>
            {materials.length > 0 && (
              <ul className="space-y-1">
                {materials.map((m, index) => (
                  <li key={`${m.kind}-${index}`}
                    className="flex items-center gap-2 rounded-lg border border-surface-800 bg-surface-800/50 px-2 py-1.5">
                    {m.thumbnailUrl
                      ? <img src={m.thumbnailUrl} alt="" className="h-8 w-14 shrink-0 rounded object-cover" />
                      : <LinkIcon className="h-4 w-4 shrink-0 text-surface-400" />}
                    <span className="min-w-0 flex-1 truncate text-sm text-surface-200">{m.displayName}</span>
                    <button type="button" aria-label={t('materials.remove')}
                      onClick={() => setMaterials(list => list.filter((_, i) => i !== index))}
                      className="flex h-6 w-6 items-center justify-center rounded text-surface-400 hover:text-rose-300">
                      <X className="h-4 w-4" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {materials.length < MAX_MATERIALS && (
              <div className="flex flex-wrap items-center gap-2">
                <Button type="button" variant="ghost" size="sm" onClick={() => setPickerOpen(true)}>
                  <Film className="mr-1.5 h-4 w-4" />
                  {t('materials.addVideo')}
                </Button>
                <input
                  type="url"
                  className={`${inputClass} flex-1 min-w-40`}
                  placeholder={t('materials.linkPlaceholder')}
                  value={linkUrl}
                  onChange={e => setLinkUrl(e.target.value)}
                />
                <Button type="button" variant="ghost" size="sm" disabled={!linkUrl.trim()}
                  onClick={() => {
                    setMaterials(list => [...list, {
                      kind: 'LINK', label: null, url: linkUrl.trim(), videoId: null,
                      displayName: linkUrl.trim(), thumbnailUrl: null,
                    }])
                    setLinkUrl('')
                  }}>
                  {t('materials.addLink')}
                </Button>
              </div>
            )}
          </div>
        )}

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

      {pickerOpen && (
      <VideoPickerModal
        onClose={() => setPickerOpen(false)}
        onPick={video => setMaterials(list => [...list, {
          kind: 'VIDEO', label: null, url: null, videoId: video.id,
          displayName: video.name, thumbnailUrl: video.thumbnailUrl,
        }])}
      />
      )}
    </Modal>
  )
}

/** What the form holds while editing: the request fields plus what it takes to render a row. */
interface AttachmentDraft {
  kind: AttachmentKind
  label: string | null
  url: string | null
  videoId: string | null
  displayName: string
  thumbnailUrl: string | null
}

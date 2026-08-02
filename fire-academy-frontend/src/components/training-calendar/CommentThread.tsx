import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Send } from 'lucide-react'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import type { TrainingCalendarAdapter } from './adapter'

/** Two people ever, so bubbles left/right carry the whole "who said this" story. */
export function CommentThread({ trainingId, adapter }: { trainingId: string; adapter: TrainingCalendarAdapter }) {
  const { t } = useTranslation('calendar')
  const queryClient = useQueryClient()
  const [body, setBody] = useState('')
  const [error, setError] = useState<string | null>(null)

  const commentsQuery = useQuery({
    queryKey: ['training-comments', trainingId],
    queryFn: () => adapter.getComments(trainingId),
    staleTime: 0,
  })

  const addMutation = useMutation({
    mutationFn: (text: string) => adapter.addComment(trainingId, text),
    onSuccess: () => {
      setBody('')
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['training-comments', trainingId] })
    },
    onError: (e: Error) => setError(e.message),
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const text = body.trim()
    if (!text || addMutation.isPending) return
    addMutation.mutate(text)
  }

  const isMine = (fromCoach: boolean) => (adapter.role === 'coach') === fromCoach

  return (
    <div className="space-y-3">
      <h4 className="text-sm font-medium text-surface-300">{t('comments.title')}</h4>

      {commentsQuery.isLoading ? (
        <LoadingSpinner size="sm" />
      ) : commentsQuery.data && commentsQuery.data.length > 0 ? (
        <ul className="max-h-56 space-y-2 overflow-y-auto pr-1">
          {commentsQuery.data.map(comment => (
            <li key={comment.id} className={clsx('flex', isMine(comment.fromCoach) ? 'justify-end' : 'justify-start')}>
              <div className={clsx(
                'max-w-[85%] rounded-lg px-3 py-2 text-sm',
                isMine(comment.fromCoach)
                  ? 'bg-primary-600/20 text-surface-100'
                  : 'bg-surface-800 text-surface-200',
              )}>
                <p className="whitespace-pre-wrap">{comment.body}</p>
                <p className="mt-1 text-[11px] text-surface-500">
                  {comment.authorName ?? t('comments.deletedAuthor')}
                  {' · '}
                  {new Date(comment.createdAt).toLocaleString('pl-PL', {
                    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
                  })}
                </p>
              </div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-surface-500">{t('comments.empty')}</p>
      )}

      <form onSubmit={submit} className="space-y-2">
        <label htmlFor="comment-body" className="sr-only">{t('comments.placeholder')}</label>
        <textarea
          id="comment-body"
          className="min-h-16 w-full rounded-lg border border-surface-700 bg-surface-800 px-3 py-2 text-sm text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          placeholder={t('comments.placeholder')}
          value={body}
          maxLength={1000}
          onChange={e => setBody(e.target.value)}
        />
        {error && (
          <p role="alert" className="rounded bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>
        )}
        <div className="flex justify-end">
          <Button type="submit" variant="secondary" size="sm"
            loading={addMutation.isPending} disabled={!body.trim()}>
            <Send className="mr-1.5 h-4 w-4" />
            {t('comments.send')}
          </Button>
        </div>
      </form>
    </div>
  )
}

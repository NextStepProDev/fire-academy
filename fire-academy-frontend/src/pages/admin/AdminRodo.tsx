import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { adminApi } from '../../api/admin'
import { Button } from '../../components/ui/Button'
import { Search, ShieldAlert, CheckCircle, AlertTriangle } from 'lucide-react'
import type { Enrollment } from '../../types'

export function AdminRodo() {
  const { t } = useTranslation('admin')
  const [email, setEmail] = useState('')
  const [results, setResults] = useState<Enrollment[] | null>(null)
  const [anonymized, setAnonymized] = useState(false)

  const searchMutation = useMutation({
    mutationFn: (email: string) => adminApi.searchEnrollmentsByEmail(email),
    onSuccess: (data) => {
      setResults(data)
      setAnonymized(false)
    },
  })

  const anonymizeMutation = useMutation({
    mutationFn: (email: string) => adminApi.anonymizeByEmail(email),
    onSuccess: () => {
      setAnonymized(true)
      setResults(null)
      setEmail('')
    },
  })

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    if (!email.trim()) return
    searchMutation.mutate(email.trim())
  }

  const handleAnonymize = () => {
    if (!email.trim() || !results?.length) return
    anonymizeMutation.mutate(email.trim())
  }

  return (
    <div>
      <div className="flex items-center gap-3 mb-6">
        <ShieldAlert className="w-6 h-6 text-primary-500" />
        <h2 className="text-xl font-semibold text-surface-100">{t('rodo.title')}</h2>
      </div>

      <div className="bg-surface-900 border border-surface-800 rounded-xl p-6 mb-6">
        <p className="text-surface-400 text-sm leading-relaxed mb-4">
          {t('rodo.description')}
        </p>
        <form onSubmit={handleSearch} className="flex gap-3">
          <div className="flex-1">
            <input
              type="email"
              value={email}
              onChange={e => setEmail(e.target.value)}
              placeholder={t('rodo.emailPlaceholder')}
              className="w-full px-4 py-2.5 bg-surface-800 border border-surface-700 rounded-lg text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <Button type="submit" variant="secondary" loading={searchMutation.isPending}>
            <Search className="w-4 h-4 mr-2" />
            {t('rodo.search')}
          </Button>
        </form>
      </div>

      {anonymized && (
        <div className="bg-green-500/10 border border-green-500/30 rounded-xl p-4 mb-6 flex items-center gap-3">
          <CheckCircle className="w-5 h-5 text-green-400 shrink-0" />
          <p className="text-green-300 text-sm">
            {t('rodo.anonymizeSuccess', { count: anonymizeMutation.data?.anonymizedCount ?? 0 })}
          </p>
        </div>
      )}

      {results !== null && (
        <div className="bg-surface-900 border border-surface-800 rounded-xl overflow-hidden">
          {results.length === 0 ? (
            <p className="text-surface-500 text-sm p-6">{t('rodo.noResults')}</p>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-surface-800 text-left text-surface-400">
                      <th className="px-4 py-3">{t('enrollments.firstName')}</th>
                      <th className="px-4 py-3">{t('enrollments.lastName')}</th>
                      <th className="px-4 py-3">{t('enrollments.email')}</th>
                      <th className="px-4 py-3">{t('enrollments.phone')}</th>
                      <th className="px-4 py-3">{t('rodo.event')}</th>
                      <th className="px-4 py-3">{t('rodo.eventDate')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {results.map(en => (
                      <tr key={en.id} className="border-b border-surface-800/50">
                        <td className="px-4 py-3 text-surface-100">{en.firstName}</td>
                        <td className="px-4 py-3 text-surface-100">{en.lastName}</td>
                        <td className="px-4 py-3 text-surface-300">{en.email}</td>
                        <td className="px-4 py-3 text-surface-300">{en.phone}</td>
                        <td className="px-4 py-3 text-surface-300">{en.eventTypeName}</td>
                        <td className="px-4 py-3 text-surface-500">{en.eventStartDate}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="border-t border-surface-800 p-4 flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm text-surface-400">
                  <AlertTriangle className="w-4 h-4 text-amber-400" />
                  {t('rodo.anonymizeWarning', { count: results.length })}
                </div>
                <Button
                  variant="danger"
                  onClick={handleAnonymize}
                  loading={anonymizeMutation.isPending}
                >
                  {t('rodo.anonymizeAll')}
                </Button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}

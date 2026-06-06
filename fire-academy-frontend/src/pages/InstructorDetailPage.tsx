import { useParams, Navigate, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, User } from 'lucide-react'
import { publicApi } from '../api/public'
import { Seo } from '../components/seo/Seo'
import { ShareButton } from '../components/ui/ShareButton'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'

export function InstructorDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation('events')
  const navigate = useNavigate()

  const query = useQuery({
    queryKey: ['public', 'instructor', id],
    queryFn: () => publicApi.getInstructor(id!),
    enabled: !!id,
  })

  if (query.isLoading) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (query.error || !query.data) {
    return <Navigate to="/" replace />
  }

  const instructor = query.data
  const fullName = `${instructor.firstName} ${instructor.lastName}`
  const shareUrl = `/kadra/${id}`

  return (
    <>
      <Seo
        title={fullName}
        description={instructor.bio || `${fullName} — instruktor Fire Academy.`}
        path={shareUrl}
        image={instructor.photoUrl}
        jsonLd={{
          '@context': 'https://schema.org',
          '@type': 'Person',
          name: fullName,
          givenName: instructor.firstName,
          familyName: instructor.lastName,
          ...(instructor.bio && { description: instructor.bio }),
          ...(instructor.photoUrl && { image: `${window.location.origin}${instructor.photoUrl}` }),
          jobTitle: 'Instruktor',
          worksFor: {
            '@type': 'Organization',
            name: 'Fire Academy',
            url: window.location.origin,
          },
          url: `${window.location.origin}${shareUrl}`,
        }}
        breadcrumbs={[
          { name: 'Fire Academy', path: '/' },
          { name: fullName, path: shareUrl },
        ]}
      />

      <div className="max-w-4xl mx-auto px-4 py-10 space-y-8">
        <button
          onClick={() => navigate(-1)}
          className="inline-flex items-center gap-1.5 text-sm text-surface-400 hover:text-primary-400 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          {t('detail.backToList')}
        </button>

        <div className="flex flex-col sm:flex-row gap-8 items-start">
          <div className="w-48 h-48 sm:w-64 sm:h-64 rounded-2xl overflow-hidden bg-surface-800 flex items-center justify-center shrink-0 mx-auto sm:mx-0">
            {instructor.photoUrl ? (
              <img src={instructor.photoUrl} alt={fullName} decoding="async" className="w-full h-full object-cover" />
            ) : (
              <User className="w-20 h-20 text-surface-600" />
            )}
          </div>

          <div className="flex-1 space-y-4">
            <div className="flex items-start justify-between gap-4">
              <h1 className="text-3xl md:text-4xl font-bold text-surface-100">{fullName}</h1>
              <ShareButton url={shareUrl} title={fullName} className="shrink-0 mt-1" />
            </div>

            {instructor.bio && (
              <p className="text-surface-300 whitespace-pre-wrap leading-relaxed">{instructor.bio}</p>
            )}
          </div>
        </div>
      </div>
    </>
  )
}

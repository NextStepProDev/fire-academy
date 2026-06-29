import { useState, useEffect, useCallback, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { useTranslation } from 'react-i18next'
import { Calendar, MapPin, Users, Phone, ChevronLeft, ChevronRight, ChevronDown, X } from 'lucide-react'
import { formatDateRange } from '../../utils/dates'
import { visibleMonths, formatMonth } from '../../utils/trainingSchedule'
import { publicApi } from '../../api/public'
import clsx from 'clsx'
import { TrainingSlotCard } from './TrainingSlotCard'
import { TrainingEnrollModal } from './TrainingEnrollModal'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { FadeImage } from '../ui/FadeImage'
import type { EventType, EventInstance, EventCategory, TrainingSlotCard as TrainingSlot } from '../../types'

const DAYS = [1, 2, 3, 4, 5, 6, 7] as const

interface EventTypeModalProps {
  eventType: EventType | null
  events: EventInstance[]
  onEnroll: (eventId: string, eventName: string) => void
  onClose: () => void
  // When TRAINING, the modal lists recurring weekly slots for this type instead of dated events.
  category?: EventCategory
  isAuthenticated?: boolean
}

export function EventTypeModal({ eventType, events, onEnroll, onClose, category, isAuthenticated = false }: EventTypeModalProps) {
  const { t } = useTranslation('events')
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)
  const touchStart = useRef<number | null>(null)

  const isTraining = category === 'TRAINING'
  const months = visibleMonths()
  const [selectedMonth, setSelectedMonth] = useState(months[0])
  const [enrollSlot, setEnrollSlot] = useState<TrainingSlot | null>(null)
  const [expandedDays, setExpandedDays] = useState<Set<number>>(new Set())

  const toggleDay = (day: number) => setExpandedDays(prev => {
    const nextSet = new Set(prev)
    if (nextSet.has(day)) nextSet.delete(day); else nextSet.add(day)
    return nextSet
  })

  const slotsQuery = useQuery({
    queryKey: ['public', 'training-slots', selectedMonth],
    queryFn: () => publicApi.getTrainingSlots(selectedMonth),
    enabled: isTraining && !!eventType,
  })
  const relatedSlots = slotsQuery.data?.filter(s => s.eventTypeId === eventType?.id) ?? []

  const photos = eventType?.photos ?? []

  const prev = useCallback(() => {
    if (lightboxIndex === null) return
    setLightboxIndex(lightboxIndex > 0 ? lightboxIndex - 1 : photos.length - 1)
  }, [lightboxIndex, photos.length])

  const next = useCallback(() => {
    if (lightboxIndex === null) return
    setLightboxIndex(lightboxIndex < photos.length - 1 ? lightboxIndex + 1 : 0)
  }, [lightboxIndex, photos.length])

  useEffect(() => {
    if (lightboxIndex === null) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') prev()
      else if (e.key === 'ArrowRight') next()
      else if (e.key === 'Escape') {
        // Intercept Escape so the modal underneath isn't closed as well
        e.stopImmediatePropagation()
        setLightboxIndex(null)
      }
    }
    // Capture phase — runs before the Escape handler in Modal
    document.addEventListener('keydown', handler, true)
    return () => document.removeEventListener('keydown', handler, true)
  }, [lightboxIndex, prev, next])

  if (!eventType) return null
  return (
    <>
      <Modal isOpen={!!eventType} onClose={onClose} size="xl" title={eventType.name}>
        <div className="space-y-6">
          {eventType.thumbnailUrl && (
            <div className="-mx-6 -mt-2 aspect-square overflow-hidden bg-surface-800">
              <FadeImage src={eventType.thumbnailUrl} alt="" decoding="async" className="w-full h-full object-cover" />
            </div>
          )}

          {eventType.description && (
            <p className="text-surface-300 whitespace-pre-wrap">{eventType.description}</p>
          )}

          {photos.length > 0 && (
            <div className="grid grid-cols-3 gap-2">
              {photos.map((p, i) => (
                <button key={p.id} onClick={() => setLightboxIndex(i)} className="aspect-square rounded-lg overflow-hidden bg-surface-800">
                  <img src={p.url} alt="" loading="lazy" decoding="async" className="w-full h-full object-cover hover:scale-105 transition-transform" />
                </button>
              ))}
            </div>
          )}

          {isTraining && (
            <div>
              <h3 className="text-lg font-semibold text-surface-100 mb-3 border-l-4 border-primary-500 pl-3">{t('sections.terminy')}</h3>

              <div className="flex flex-wrap gap-2 mb-4">
                {months.map(m => (
                  <button
                    key={m}
                    onClick={() => setSelectedMonth(m)}
                    className={clsx(
                      'px-4 py-2 text-sm font-medium rounded-lg capitalize transition-colors',
                      m === selectedMonth ? 'bg-primary-600 text-white' : 'bg-surface-900 border border-surface-800 text-surface-300 hover:bg-surface-800'
                    )}
                  >
                    {formatMonth(m)}
                  </button>
                ))}
              </div>

              {slotsQuery.isLoading ? (
                <LoadingSpinner />
              ) : relatedSlots.length ? (
                <div className={clsx('space-y-3 transition-opacity', slotsQuery.isFetching && 'opacity-60')}>
                  {DAYS.map(day => {
                    const daySlots = relatedSlots.filter(s => s.dayOfWeek === day)
                    if (!daySlots.length) return null
                    const expanded = expandedDays.has(day)
                    return (
                      <div key={day}>
                        <button
                          onClick={() => toggleDay(day)}
                          className="w-full flex items-center justify-between px-4 py-3 bg-surface-900 border border-surface-800 rounded-xl hover:bg-surface-800/50 transition-colors"
                        >
                          <span className="flex items-center gap-2 font-semibold text-surface-100">
                            {expanded ? <ChevronDown className="w-5 h-5 text-primary-400" /> : <ChevronRight className="w-5 h-5 text-surface-400" />}
                            {t(`days.${day}`)}
                            <span className="text-sm font-normal text-surface-500">({daySlots.length})</span>
                          </span>
                        </button>
                        {expanded && (
                          <div className="space-y-2 mt-2">
                            {daySlots.map(slot => (
                              <TrainingSlotCard
                                key={slot.id}
                                slot={slot}
                                isAuthenticated={isAuthenticated}
                                onEnroll={() => setEnrollSlot(slot)}
                                hideType
                              />
                            ))}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              ) : (
                <p className="text-surface-500">{t('slots.noSlots')}</p>
              )}
            </div>
          )}

          {events.length > 0 && (
            <div>
              <h3 className="text-lg font-semibold text-surface-100 mb-3 border-l-4 border-primary-500 pl-3">{t('sections.terminy')}</h3>
              <div className="space-y-3">
                {events.map(event => {
                  const isFull = event.maxParticipants != null && event.availableSpots <= 0
                  return (
                    <div key={event.id} className="flex items-center gap-3 bg-surface-800/50 rounded-lg p-3">
                      <div className="flex-1 min-w-0 space-y-1">
                        <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-surface-400">
                          <span className="flex items-center gap-1">
                            <Calendar className="w-3.5 h-3.5" />
                            {formatDateRange(event.startDate, event.endDate)}
                            {event.startTime ? `, ${event.startTime}${event.endTime ? ` – ${event.endTime}` : ''}` : ''}
                          </span>
                          {event.location && (
                            <span className="flex items-center gap-1">
                              <MapPin className="w-3.5 h-3.5" />
                              {event.location}
                            </span>
                          )}
                          {event.maxParticipants != null && (
                            <span className="flex items-center gap-1">
                              <Users className="w-3.5 h-3.5" />
                              {t('event.spotsOf', { available: event.availableSpots, max: event.maxParticipants })}
                            </span>
                          )}
                        </div>
                        {event.price != null && (
                          <p className="text-primary-400 font-medium text-sm">{event.price} PLN</p>
                        )}
                      </div>
                      {isFull ? (
                        <a href="tel:+48534823667">
                          <Button variant="waitlist" size="sm">
                            <Phone className="w-4 h-4 mr-1.5" />
                            {t('event.waitingList')}
                          </Button>
                        </a>
                      ) : (
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => onEnroll(event.id, event.eventTypeName)}
                        >
                          {t('event.enroll')}
                        </Button>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </div>
      </Modal>

      {lightboxIndex !== null && photos[lightboxIndex] && (
        <div
          className="fixed inset-0 z-[200] flex items-center justify-center bg-black/90"
          onClick={() => setLightboxIndex(null)}
          onTouchStart={e => { touchStart.current = e.touches[0].clientX }}
          onTouchEnd={e => {
            if (touchStart.current === null) return
            const diff = e.changedTouches[0].clientX - touchStart.current
            if (Math.abs(diff) > 50) { if (diff > 0) prev(); else next() }
            touchStart.current = null
          }}
        >
          <button
            onClick={e => { e.stopPropagation(); setLightboxIndex(null) }}
            className="absolute top-4 right-4 p-2 text-white/70 hover:text-white z-10"
          >
            <X className="w-6 h-6" />
          </button>

          {photos.length > 1 && (
            <>
              <button
                onClick={e => { e.stopPropagation(); prev() }}
                className="absolute left-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/50 text-white/80 hover:text-white hover:bg-black/70 transition-colors z-10"
              >
                <ChevronLeft className="w-6 h-6" />
              </button>
              <button
                onClick={e => { e.stopPropagation(); next() }}
                className="absolute right-3 top-1/2 -translate-y-1/2 p-2 rounded-full bg-black/50 text-white/80 hover:text-white hover:bg-black/70 transition-colors z-10"
              >
                <ChevronRight className="w-6 h-6" />
              </button>
            </>
          )}

          <img
            src={photos[lightboxIndex].url}
            alt=""
            decoding="async"
            className="max-w-[90vw] max-h-[90vh] object-contain rounded-lg"
            onClick={e => e.stopPropagation()}
          />

          {photos.length > 1 && (
            <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
              {photos.map((_, i) => (
                <button
                  key={i}
                  onClick={e => { e.stopPropagation(); setLightboxIndex(i) }}
                  className={`w-2 h-2 rounded-full transition-colors ${i === lightboxIndex ? 'bg-white' : 'bg-white/40'}`}
                />
              ))}
            </div>
          )}
        </div>
      )}

      <TrainingEnrollModal
        slot={enrollSlot}
        startMonth={selectedMonth}
        onClose={() => setEnrollSlot(null)}
      />
    </>
  )
}

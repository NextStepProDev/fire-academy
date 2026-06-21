import { useState, useEffect, useCallback, useRef } from 'react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { useTranslation } from 'react-i18next'
import { Calendar, MapPin, Users, Phone, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { formatDateRange } from '../../utils/dates'
import type { EventType, EventInstance } from '../../types'

interface EventTypeModalProps {
  eventType: EventType | null
  events: EventInstance[]
  onEnroll: (eventId: string, eventName: string) => void
  onClose: () => void
}

export function EventTypeModal({ eventType, events, onEnroll, onClose }: EventTypeModalProps) {
  const { t } = useTranslation('events')
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)
  const touchStart = useRef<number | null>(null)

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
      <Modal isOpen={!!eventType} onClose={onClose} title={eventType.name}>
        <div className="space-y-6">
          {eventType.thumbnailUrl && (
            <div className="relative -mx-6 -mt-2 overflow-hidden">
              <img src={eventType.thumbnailUrl} alt="" decoding="async" className="w-full aspect-square object-cover" />
              <div className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-surface-900 to-transparent" />
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
                          <Button variant="primary" size="sm">
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
    </>
  )
}

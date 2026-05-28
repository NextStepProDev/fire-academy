import { useState } from 'react'
import { Modal } from '../ui/Modal'
import { useTranslation } from 'react-i18next'
import type { EventType } from '../../types'

interface EventTypeModalProps {
  eventType: EventType | null
  onClose: () => void
}

export function EventTypeModal({ eventType, onClose }: EventTypeModalProps) {
  const { t } = useTranslation('events')
  const [selectedPhoto, setSelectedPhoto] = useState<string | null>(null)

  if (!eventType) return null
  return (
    <>
      <Modal isOpen={!!eventType} onClose={onClose} title={eventType.name}>
        <div className="space-y-4">
          {eventType.description && (
            <p className="text-surface-300 whitespace-pre-wrap">{eventType.description}</p>
          )}
          <div className="grid grid-cols-2 gap-3 text-sm">
            {eventType.price != null && (
              <div>
                <span className="text-surface-500">{t('event.price')}:</span>
                <span className="ml-2 text-surface-100">{eventType.price} PLN</span>
              </div>
            )}
            {eventType.maxParticipants != null && (
              <div>
                <span className="text-surface-500">{t('event.maxParticipants')}:</span>
                <span className="ml-2 text-surface-100">{eventType.maxParticipants}</span>
              </div>
            )}
            {eventType.duration && (
              <div>
                <span className="text-surface-500">{t('event.duration')}:</span>
                <span className="ml-2 text-surface-100">{eventType.duration}</span>
              </div>
            )}
          </div>
          {eventType.photos.length > 0 && (
            <div className="grid grid-cols-3 gap-2 mt-4">
              {eventType.photos.map(p => (
                <button key={p.id} onClick={() => setSelectedPhoto(p.url)} className="aspect-square rounded-lg overflow-hidden bg-surface-800">
                  <img src={p.url} alt="" className="w-full h-full object-cover hover:scale-105 transition-transform" />
                </button>
              ))}
            </div>
          )}
        </div>
      </Modal>
      {selectedPhoto && (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/80" onClick={() => setSelectedPhoto(null)}>
          <img src={selectedPhoto} alt="" className="max-w-[90vw] max-h-[90vh] object-contain rounded-lg" />
        </div>
      )}
    </>
  )
}

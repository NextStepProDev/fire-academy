import type { EventType } from '../../types'

interface EventTypeCardProps {
  eventType: EventType
  onClick: () => void
}

export function EventTypeCard({ eventType, onClick }: EventTypeCardProps) {
  return (
    <button
      onClick={onClick}
      className="group text-left bg-surface-900 border border-surface-800 rounded-xl overflow-hidden hover:border-primary-600/50 transition-all duration-200"
    >
      <div className="aspect-[4/3] bg-surface-800 flex items-center justify-center overflow-hidden">
        {eventType.thumbnailUrl ? (
          <img
            src={eventType.thumbnailUrl}
            alt={eventType.name}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
        ) : (
          <span className="text-surface-600 text-sm">Brak zdjęcia</span>
        )}
      </div>
      <div className="p-4">
        <h3 className="font-semibold text-surface-100">{eventType.name}</h3>
      </div>
    </button>
  )
}

import { ChevronRight } from 'lucide-react'
import { ShareButton } from '../ui/ShareButton'
import type { EventType } from '../../types'

interface EventTypeCardProps {
  eventType: EventType
  onClick: () => void
  shareUrl?: string
}

export function EventTypeCard({ eventType, onClick, shareUrl }: EventTypeCardProps) {
  return (
    <div className="w-full flex items-center gap-4 p-4 bg-surface-900 border border-surface-800 rounded-lg hover:border-primary-500/50 hover:-translate-y-0.5 active:scale-[0.98] transition-all duration-200">
      <button
        onClick={onClick}
        className="flex items-center gap-4 flex-1 min-w-0 text-left"
      >
        <div className="flex-shrink-0 w-20 h-20 bg-surface-800 rounded-lg overflow-hidden">
          {eventType.thumbnailUrl ? (
            <img
              src={eventType.thumbnailUrl}
              alt={eventType.name}
              loading="lazy"
              decoding="async"
              className="w-full h-full object-cover"
            />
          ) : (
            <span className="w-full h-full flex items-center justify-center text-surface-600 text-xs">–</span>
          )}
        </div>

        <div className="flex-1 min-w-0">
          <p className="font-semibold text-surface-100 text-lg leading-snug">{eventType.name}</p>
          {eventType.description && (
            <p className="text-sm text-surface-400 mt-1 line-clamp-2">{eventType.description}</p>
          )}
        </div>

        <ChevronRight className="flex-shrink-0 h-5 w-5 text-surface-400" />
      </button>

      {shareUrl && (
        <ShareButton url={shareUrl} title={eventType.name} className="flex-shrink-0" />
      )}
    </div>
  )
}

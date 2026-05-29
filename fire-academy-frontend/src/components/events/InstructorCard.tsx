import { User } from 'lucide-react'
import { ShareButton } from '../ui/ShareButton'
import type { Instructor } from '../../types'

interface InstructorCardProps {
  instructor: Instructor
  onClick: () => void
  shareUrl?: string
}

export function InstructorCard({ instructor, onClick, shareUrl }: InstructorCardProps) {
  const fullName = `${instructor.firstName} ${instructor.lastName}`
  return (
    <div className="group relative bg-surface-900 border border-surface-800 rounded-xl overflow-hidden hover:border-primary-600/50 active:scale-[0.98] transition-all duration-200">
      <button
        onClick={onClick}
        className="w-full text-left"
      >
        <div className="aspect-square bg-surface-800 flex items-center justify-center overflow-hidden">
          {instructor.photoUrl ? (
            <img
              src={instructor.photoUrl}
              alt={fullName}
              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            />
          ) : (
            <User className="w-16 h-16 text-surface-600" />
          )}
        </div>
        <div className="p-4">
          <p className="font-semibold text-surface-100">{fullName}</p>
        </div>
      </button>
      {shareUrl && (
        <div className="absolute bottom-3 right-3">
          <ShareButton url={shareUrl} title={fullName} />
        </div>
      )}
    </div>
  )
}

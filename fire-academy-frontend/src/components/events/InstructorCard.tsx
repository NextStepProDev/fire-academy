import { User } from 'lucide-react'
import type { Instructor } from '../../types'

interface InstructorCardProps {
  instructor: Instructor
  onClick: () => void
}

export function InstructorCard({ instructor, onClick }: InstructorCardProps) {
  return (
    <button
      onClick={onClick}
      className="group text-left bg-surface-900 border border-surface-800 rounded-xl overflow-hidden hover:border-primary-600/50 transition-all duration-200"
    >
      <div className="aspect-square bg-surface-800 flex items-center justify-center overflow-hidden">
        {instructor.photoUrl ? (
          <img
            src={instructor.photoUrl}
            alt={`${instructor.firstName} ${instructor.lastName}`}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
        ) : (
          <User className="w-16 h-16 text-surface-600" />
        )}
      </div>
      <div className="p-4">
        <p className="font-semibold text-surface-100">{instructor.firstName} {instructor.lastName}</p>
      </div>
    </button>
  )
}

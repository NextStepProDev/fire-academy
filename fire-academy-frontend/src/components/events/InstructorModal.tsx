import { Modal } from '../ui/Modal'
import { User } from 'lucide-react'
import type { Instructor } from '../../types'

interface InstructorModalProps {
  instructor: Instructor | null
  onClose: () => void
}

export function InstructorModal({ instructor, onClose }: InstructorModalProps) {
  if (!instructor) return null
  return (
    <Modal isOpen={!!instructor} onClose={onClose} title={`${instructor.firstName} ${instructor.lastName}`}>
      <div className="flex flex-col gap-4">
        <div className="w-40 h-40 rounded-full overflow-hidden bg-surface-800 flex items-center justify-center mx-auto">
          {instructor.photoUrl ? (
            <img src={instructor.photoUrl} alt={instructor.firstName} decoding="async" className="w-full h-full object-cover" />
          ) : (
            <User className="w-20 h-20 text-surface-600" />
          )}
        </div>
        {instructor.bio && (
          <p className="text-surface-300 whitespace-pre-wrap">{instructor.bio}</p>
        )}
      </div>
    </Modal>
  )
}

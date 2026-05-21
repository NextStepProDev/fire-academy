export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  emailNotificationsEnabled: boolean
  preferredLanguage: string
  hasPassword: boolean
  createdAt: string
}

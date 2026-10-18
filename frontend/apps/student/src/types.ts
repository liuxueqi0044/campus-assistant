export type Tab = 'campus' | 'timetable' | 'messages' | 'me'
export type Service = 'market' | 'errand' | 'activity'
export interface Campus {
  id: string
  schoolId?: string
  school: string
  name: string
  shortName: string
  enabled: boolean
  timezone?: string
  configVersion?: string
}
export interface Product {
  id: string
  campusId: string
  title: string
  priceFen: number
  category: string
  image: string
  condition: string
  description: string
  defects: string
  location: string
  seller: string
  avatar: string
  postedAt: string
  status: 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'PENDING_REVIEW'
  ownerId: string
  type: 'SELL' | 'WANTED'
  version: number
  buyerConfirmed?: boolean
}
export interface Errand {
  id: string
  campusId: string
  title: string
  category: string
  pickup: string
  destination: string
  rewardFen: number
  feeFen: number
  deadline: string
  description: string
  weight: string
  requesterId: string
  runnerId?: string
  status:
    | 'OPEN'
    | 'ACCEPTED'
    | 'IN_PROGRESS'
    | 'DELIVERED'
    | 'COMPLETED'
    | 'CANCELLED'
    | 'WAITING_PAYMENT'
  paymentStatus: 'UNPAID' | 'PAID' | 'REFUND_PENDING' | 'REFUNDED'
  payoutStatus: 'NONE' | 'PENDING' | 'PAID'
  version: number
}
export interface Activity {
  id: string
  campusId: string
  title: string
  category: string
  cover: string
  date: string
  time: string
  location: string
  organizer: string
  description: string
  capacity: number
  occupied: number
  participantStatus: 'NONE' | 'CONFIRMED' | 'WAITLISTED' | 'CHECKED_IN'
  waitlistPosition?: number
  status: 'OPEN' | 'CANCELLED' | 'PENDING_REVIEW'
  version: number
}
export interface Course {
  id: string
  title: string
  teacher: string
  room: string
  day: number
  start: number
  duration: number
  time: string
  weeks: number[]
  color: string
}
export interface ChatMessage {
  id: string
  seq: number
  text: string
  mine: boolean
  time: string
  status: 'sent' | 'sending' | 'failed'
  removed?: boolean
}
export interface Conversation {
  id: string
  name: string
  avatar: string
  tone: string
  preview: string
  time: string
  unread: number
  contextTitle: string
  contextType: 'market' | 'errand' | 'activity'
  contextId: string
  messages: ChatMessage[]
}
export interface Notice {
  id: string
  title: string
  text: string
  time: string
  read: boolean
  type: 'activity' | 'errand' | 'system'
}
export interface User {
  id: string
  name: string
  schoolId?: string
  school: string
  campusId: string
  loggedIn: boolean
  verification: 'NONE' | 'PENDING' | 'VERIFIED'
  runner: boolean
}

/** Wire DTOs used by the real campus context adapter. Pages continue to use Campus/User. */
export interface SchoolContextDTO {
  schoolId?: string
  /** Legacy candidate field; canonical payloads use schoolId. */
  id?: string
  name: string
  status?: string
  timezone?: string
  configVersion?: string
  campuses?: CampusContextDTO[]
}

export interface CampusContextDTO {
  campusId?: string
  /** Legacy candidate field; canonical payloads use campusId. */
  id?: string
  schoolId?: string
  name: string
  shortName?: string
  timezone?: string
  status?: string
  configVersion?: string
}

export interface ContextUserDTO {
  loggedIn?: boolean
  userId?: string | null
  id?: string
  displayName?: string | null
  name?: string
  schoolId?: string | null
  school?: string
  campusId?: string | null
  verificationStatus?: User['verification']
  verification?: User['verification']
  runnerEligible?: boolean
  runner?: boolean
}

export interface ContextDTO {
  /** Current authenticated/browse session; context may omit user entirely. */
  loggedIn?: boolean
  browseMode?: string
  school?: SchoolContextDTO
  campus?: CampusContextDTO
  schools?: SchoolContextDTO[]
  campuses?: CampusContextDTO[] | Campus[]
  campusId?: string
  capabilities?: Record<string, string>
  user?: ContextUserDTO | User
}

export interface SchoolsResponseDTO {
  items: SchoolContextDTO[]
  nextCursor?: string | null
}
export interface Activity {
  ownerId?: string
}

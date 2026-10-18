import { reactive } from 'vue'
import { demoData } from '@/data/demo'
import type { Service, Tab, User, Course, Conversation, Notice, Activity } from '@/types'
const mode = import.meta.env.VITE_API_MODE || 'demo'
if (!['demo', 'real'].includes(mode)) throw new Error('VITE_API_MODE 必须为 demo 或 real')
export const isDemo = mode === 'demo'
const initial = demoData()
if (!isDemo) {
  initial.campuses = []
  initial.products = []
  initial.errands = []
  initial.activities = []
  initial.courses = []
  initial.conversations = []
  initial.notices = []
  initial.user = {
    id: '',
    name: '',
    school: '',
    campusId: '',
    loggedIn: false,
    verification: 'NONE',
    runner: false,
  }
}
export const state = reactive({
  ...initial,
  campusId: isDemo ? 'east' : '',
  service: 'market' as Service,
  favorites: [] as string[],
  week: 3,
  fontScale: 1,
  termStart: '2026-08-31',
  defaultTab: 'campus' as Tab,
  notifications: true,
  demo: isDemo,
  networkState: 'ready' as 'ready' | 'loading' | 'error',
  networkError: '',
  contentState: {
    market: 'ready',
    errand: 'ready',
    activity: 'ready',
  } as Record<Service, 'ready' | 'loading' | 'error'>,
  contentError: {
    market: '',
    errand: '',
    activity: '',
  } as Record<Service, string>,
  privateState: 'ready' as 'ready' | 'loading' | 'error',
  privateError: '',
  sessionExpiresAt: '',
  profileCompletion: null as boolean | null,
})
interface DemoPrivateSnapshot {
  user: User
  courses: Course[]
  conversations: Conversation[]
  notices: Notice[]
  favorites: string[]
  participation: Record<string, { status: Activity['participantStatus']; position?: number }>
  confirmations: Record<string, boolean>
}
// Demo persistence stays in memory and is never exposed through the guest state.
let suspendedDemo: DemoPrivateSnapshot | null = null
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T
export function clearPrivateWorkspace() {
  state.user = {
    id: '',
    name: '',
    school: '',
    campusId: '',
    loggedIn: false,
    verification: 'NONE',
    runner: false,
  }
  state.conversations = []
  state.notices = []
  state.favorites = []
  state.courses = []
  for (const activity of state.activities) {
    activity.participantStatus = 'NONE'
    delete activity.waitlistPosition
  }
  for (const product of state.products) delete product.buyerConfirmed
  state.privateState = 'ready'
  state.privateError = ''
}
export function suspendDemoSession() {
  if (!isDemo) throw new Error('仅演示模式可以保存演示会话。')
  if (state.user.loggedIn) {
    suspendedDemo = copy({
      user: state.user,
      courses: state.courses,
      conversations: state.conversations,
      notices: state.notices,
      favorites: state.favorites,
      participation: Object.fromEntries(
        state.activities.map((a) => [
          a.id,
          { status: a.participantStatus, position: a.waitlistPosition },
        ]),
      ),
      confirmations: Object.fromEntries(
        state.products
          .filter((p) => p.buyerConfirmed !== undefined)
          .map((p) => [p.id, p.buyerConfirmed!]),
      ),
    })
  }
  clearPrivateWorkspace()
}
export function restoreDemoSession() {
  if (!isDemo) throw new Error('真实模式不可恢复演示身份。')
  if (state.user.loggedIn) return
  const saved = suspendedDemo
  if (!saved) throw new Error('演示会话不存在，请在设置中重置演示。')
  const restored = copy(saved)
  Object.assign(state, {
    user: { ...restored.user, loggedIn: true },
    courses: restored.courses,
    conversations: restored.conversations,
    notices: restored.notices,
    favorites: restored.favorites,
  })
  for (const activity of state.activities) {
    const participation = restored.participation[activity.id]
    activity.participantStatus = participation?.status || 'NONE'
    if (participation?.position !== undefined) activity.waitlistPosition = participation.position
    else delete activity.waitlistPosition
  }
  for (const product of state.products) {
    if (Object.prototype.hasOwnProperty.call(restored.confirmations, product.id))
      product.buyerConfirmed = restored.confirmations[product.id]
    else delete product.buyerConfirmed
  }
  suspendedDemo = null
}
export function replaceDemo() {
  suspendedDemo = null
  Object.assign(state, demoData(), {
    campusId: 'east',
    service: 'market',
    favorites: [],
    week: 3,
    fontScale: 1,
    termStart: '2026-08-31',
    defaultTab: 'campus',
    notifications: true,
    networkState: 'ready',
    networkError: '',
    contentState: { market: 'ready', errand: 'ready', activity: 'ready' },
    contentError: { market: '', errand: '', activity: '' },
    privateState: 'ready',
    privateError: '',
    sessionExpiresAt: '',
    profileCompletion: null,
  })
}

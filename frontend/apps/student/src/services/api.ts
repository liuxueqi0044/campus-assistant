import {
  state,
  isDemo,
  replaceDemo,
  clearPrivateWorkspace,
  suspendDemoSession,
  restoreDemoSession,
} from '@/state'
import { request, setToken, commandKey, ApiError } from './transport'
import type {
  Activity,
  Campus,
  CampusContextDTO,
  ContextDTO,
  ContextUserDTO,
  Course,
  Errand,
  Product,
  Conversation,
  Notice,
  SchoolContextDTO,
  SchoolsResponseDTO,
  User,
} from '@/types'
const pause = () => new Promise((r) => setTimeout(r, 180))
const requireUser = () => {
  if (!state.user.loggedIn) throw new ApiError('LOGIN_REQUIRED', '请先登录后继续。')
}
const find = <T extends { id: string }>(items: T[], id: string) => {
  const item = items.find((x) => x.id === id)
  if (!item) throw new ApiError('NOT_FOUND', '内容不存在或已经移除。')
  return item
}
const checkScope = (item: { campusId: string }) => {
  if (item.campusId !== state.campusId)
    throw new ApiError('SCOPE_CHANGED', '校区已变更，请返回当前校区重新操作。')
}
const pending = new Map<string, Promise<unknown>>()
const uncertainKeys = new Map<string, string>()
let generation = 0
let sessionGeneration = 0
const assertSession = (stamp: number, actorId?: string) => {
  if (
    stamp !== sessionGeneration ||
    (actorId !== undefined && (!state.user.loggedIn || state.user.id !== actorId))
  )
    throw new ApiError(
      'SESSION_CHANGED',
      '账号状态已变更，本次结果未应用。请重新进入查看当前记录。',
    )
}
function invalidateSession() {
  sessionGeneration++
  generation++
  pending.clear()
  uncertainKeys.clear()
}
async function command<T>(path: string, body: unknown, apply: () => T): Promise<T> {
  requireUser()
  const stamp = sessionGeneration,
    actorId = state.user.id,
    campusId = state.campusId
  const parts = path.split('/')
  const resources =
    parts[1] === 'market'
      ? state.products
      : parts[1] === 'errands'
        ? state.errands
        : parts[1] === 'activities'
          ? state.activities
          : []
  const resource = resources.find((item) => item.id === parts[parts[1] === 'market' ? 3 : 2])
  const payload =
    resources.length || ['market', 'errands', 'activities'].includes(parts[1])
      ? {
          ...(body as object),
          campusId,
          ...(resource ? { expectedVersion: resource.version } : {}),
        }
      : body
  const fingerprint = JSON.stringify([stamp, actorId, campusId, path, payload])
  const existing = pending.get(fingerprint)
  if (existing) return existing as Promise<T>
  const key = uncertainKeys.get(fingerprint) || commandKey()
  uncertainKeys.set(fingerprint, key)
  const run = (async () => {
    let result: T
    if (isDemo) {
      await pause()
      assertSession(stamp, actorId)
      if (campusId !== state.campusId)
        throw new ApiError('SCOPE_CHANGED', '校区已切换，请重新操作。')
      result = apply()
    } else {
      result = await request<T>('POST', path, payload, key)
      assertSession(stamp, actorId)
      await refresh()
      assertSession(stamp, actorId)
    }
    uncertainKeys.delete(fingerprint)
    return result
  })()
  pending.set(fingerprint, run)
  try {
    return await run
  } finally {
    if (pending.get(fingerprint) === run) pending.delete(fingerprint)
  }
}
async function refresh() {
  const stamp = ++generation,
    session = sessionGeneration
  const current = () => stamp === generation && session === sessionGeneration
  state.networkState = 'loading'
  state.networkError = ''
  try {
    if (isDemo) {
      await pause()
    } else {
      // The directory is public source-of-truth for the campus picker. The
      // context endpoint only describes the current selection and session;
      // it is intentionally not expected to repeat the whole directory.
      const rawContext = await request<ContextDTO>('GET', '/context')
      const directory = await request<SchoolsResponseDTO>('GET', '/schools')
      const ctx = normalizeContext(rawContext, directory)
      if (!current()) return
      Object.assign(state, ctx)
      if (!state.campusId) {
        state.products = []
        state.errands = []
        state.activities = []
        state.networkState = 'error'
        state.networkError = '暂无可用校区，校园内容暂时无法加载。'
        return
      }
      const q = `?campusId=${encodeURIComponent(state.campusId)}&limit=50`
      const [products, errands, activities] = await Promise.all([
        request<{ items: Product[] }>('GET', '/market/listings' + q),
        request<{ items: Errand[] }>('GET', '/errands/available' + q),
        request<{ items: Activity[] }>('GET', '/activities' + q),
      ])
      if (!current()) return
      state.products = products.items
      state.errands = errands.items
      state.activities = activities.items
      if (state.user.loggedIn) {
        const actorId = state.user.id
        const privateData = await request<{
          courses: Course[]
          conversations: Conversation[]
          notices: Notice[]
          favorites: string[]
        }>('GET', '/me/workspace')
        if (!current() || state.user.id !== actorId || !state.user.loggedIn) return
        Object.assign(state, privateData)
      }
    }
    if (current()) state.networkState = 'ready'
  } catch (e) {
    if (current()) {
      state.networkState = 'error'
      state.networkError = e instanceof Error ? e.message : '加载失败'
    }
    throw e
  }
}
export const api = {
  refresh,
  async switchCampus(id: string) {
    if (!find(state.campuses, id).enabled) throw new Error('该校区暂未开放。')
    if (isDemo) {
      state.campusId = id
      await refresh()
    } else {
      const campus = find(state.campuses, id)
      if (!campus.schoolId) throw new ApiError('CONTEXT_INVALID', '校区缺少所属学校信息，请刷新后重试。')
      await request('PUT', '/context', { schoolId: campus.schoolId, campusId: id })
      await refresh()
    }
  },
  async login() {
    if (state.user.loggedIn) return
    invalidateSession()
    const stamp = sessionGeneration
    if (isDemo) {
      await pause()
      assertSession(stamp)
      restoreDemoSession()
      return
    }
    const code = await new Promise<string>((resolve, reject) =>
      uni.login({
        provider: 'weixin',
        success: (r) => resolve(r.code),
        fail: () => reject(new Error('请在微信小程序中登录。')),
      }),
    )
    assertSession(stamp)
    const clientContext = state.campusId ? { campusId: state.campusId } : undefined
    const result = await request<{ accessToken: string }>('POST', '/auth/wechat/login', {
      code,
      ...(clientContext ? { clientContext } : {}),
    })
    assertSession(stamp)
    setToken(result.accessToken)
    await refresh()
    assertSession(stamp)
  },
  async logout() {
    invalidateSession()
    const remote = isDemo ? null : request('POST', '/auth/logout')
    if (isDemo) suspendDemoSession()
    else clearPrivateWorkspace()
    setToken('')
    state.networkState = 'ready'
    state.networkError = ''
    if (remote) await remote
  },
  verify: (input: { name: string; studentId: string }) =>
    command('/verifications', input, () => {
      if (!input.name.trim() || !input.studentId.trim()) throw new Error('请完整填写认证信息。')
      state.user.verification = 'PENDING'
    }),
  toggleFavorite: (id: string) =>
    command(`/market/listings/${id}/favorite`, {}, () => {
      find(state.products, id)
      const i = state.favorites.indexOf(id)
      if (i < 0) state.favorites.push(id)
      else state.favorites.splice(i, 1)
    }),
  reserveProduct: (id: string) =>
    command(`/market/listings/${id}/reservations`, {}, () => {
      const p = find(state.products, id)
      checkScope(p)
      if (p.ownerId === state.user.id || p.type === 'WANTED') throw new Error('当前商品无法预约。')
      if (p.status !== 'AVAILABLE') throw new Error('已被预约，请刷新后再试。')
      p.status = 'RESERVED'
      p.buyerConfirmed = false
      p.version++
      reservations.set(id, state.user.id)
    }),
  cancelReservation: (id: string) =>
    command(`/market/listings/${id}/reservation/cancel`, {}, () => {
      const p = find(state.products, id)
      if (reservations.get(id) !== state.user.id || p.status !== 'RESERVED')
        throw new Error('只有预约者可以取消有效预约。')
      if (p.buyerConfirmed) throw new Error('你已确认交接，请联系卖家协商后处理。')
      p.status = 'AVAILABLE'
      delete p.buyerConfirmed
      p.version++
      reservations.delete(id)
    }),
  confirmProduct: (id: string) =>
    command(`/market/listings/${id}/confirm`, {}, () => {
      const p = find(state.products, id)
      if (reservations.get(id) !== state.user.id || p.status !== 'RESERVED')
        throw new Error('请先预约交接。')
      if (p.buyerConfirmed) return
      p.buyerConfirmed = true
      p.version++
    }),
  publishProduct: (input: Partial<Product>) =>
    command('/market/listings', input, () => {
      if (
        !input.title?.trim() ||
        !input.description?.trim() ||
        !Number.isInteger(input.priceFen) ||
        input.priceFen! < 0
      )
        throw new Error('请检查标题、描述和价格。')
      const p: Product = {
        id: commandKey(),
        campusId: state.campusId,
        ownerId: state.user.id,
        title: input.title,
        priceFen: input.priceFen!,
        category: input.category || '宿舍生活',
        image: input.image || '/static/products/lamp.png',
        condition: input.condition || '九成新',
        description: input.description,
        defects: input.defects || '未填写',
        location: input.location || '校内公共区域',
        seller: state.user.name,
        avatar: '林',
        postedAt: '刚刚',
        status: 'PENDING_REVIEW',
        type: input.type || 'SELL',
        version: 1,
      }
      state.products.unshift(p)
      return p
    }),
  contactSeller: (id: string) =>
    command(`/market/listings/${id}/conversation`, {}, () => {
      const p = find(state.products, id)
      let c = state.conversations.find((x) => x.contextId === id)
      if (!c) {
        c = {
          id: commandKey(),
          name: p.seller,
          avatar: p.avatar,
          tone: '#e8ede5',
          preview: '开始聊聊交接时间吧',
          time: '刚刚',
          unread: 0,
          contextTitle: p.title,
          contextType: 'market',
          contextId: id,
          messages: [],
        }
        state.conversations.unshift(c)
      }
      return c.id
    }),
  acceptErrand: (id: string) =>
    command(`/errands/${id}/accept`, {}, () => {
      const e = find(state.errands, id)
      checkScope(e)
      if (!state.user.runner) throw new Error('需要通过跑腿员认证后才能接单。')
      if (e.requesterId === state.user.id) throw new Error('不能领取自己的任务。')
      if (e.status !== 'OPEN' || e.paymentStatus !== 'PAID')
        throw new Error('任务状态已更新，暂时无法接单。')
      if (
        state.errands.filter(
          (x) =>
            x.runnerId === state.user.id &&
            ['ACCEPTED', 'IN_PROGRESS', 'DELIVERED'].includes(x.status),
        ).length >= 2
      )
        throw new Error('最多同时处理两单，请先完成已有任务。')
      e.runnerId = state.user.id
      e.status = 'ACCEPTED'
      e.version++
    }),
  advanceErrand: (id: string) =>
    command(`/errands/${id}/advance`, {}, () => {
      const e = find(state.errands, id)
      if (e.status === 'DELIVERED' && e.requesterId === state.user.id) {
        e.status = 'COMPLETED'
        e.payoutStatus = 'PENDING'
      } else if (e.runnerId === state.user.id && e.status === 'ACCEPTED') e.status = 'IN_PROGRESS'
      else if (e.runnerId === state.user.id && e.status === 'IN_PROGRESS') e.status = 'DELIVERED'
      else throw new Error('当前身份不能执行此操作。')
      e.version++
    }),
  cancelErrand: (id: string) =>
    command(`/errands/${id}/cancel`, {}, () => {
      const e = find(state.errands, id)
      if (e.requesterId !== state.user.id || !['OPEN', 'WAITING_PAYMENT'].includes(e.status))
        throw new Error('当前状态不能直接取消，请通过订单联系入口协商。')
      e.status = 'CANCELLED'
      if (e.paymentStatus === 'PAID') e.paymentStatus = 'REFUND_PENDING'
      e.version++
    }),
  createErrand: (input: Partial<Errand>) =>
    command('/errands', input, () => {
      if (
        !input.title?.trim() ||
        !input.pickup?.trim() ||
        !input.destination?.trim() ||
        !Number.isInteger(input.rewardFen) ||
        input.rewardFen! <= 0
      )
        throw new Error('请完整填写任务和有效报酬。')
      const e: Errand = {
        id: commandKey(),
        campusId: state.campusId,
        title: input.title,
        category: input.category || '快递代取',
        pickup: input.pickup,
        destination: input.destination,
        rewardFen: input.rewardFen!,
        feeFen: 80,
        deadline: input.deadline || '今天 18:30 前',
        description: input.description || '',
        weight: input.weight || '小件 · 1kg 内',
        requesterId: state.user.id,
        status: 'WAITING_PAYMENT',
        paymentStatus: 'UNPAID',
        payoutStatus: 'NONE',
        version: 1,
      }
      state.errands.unshift(e)
      return e
    }),
  simulatePayment: (id: string) => {
    if (!isDemo) return Promise.reject(new Error('真实模式不支持模拟支付，请接入支付接口。'))
    return command(`/errands/${id}/demo-pay`, {}, () => {
      const e = find(state.errands, id)
      if (e.requesterId !== state.user.id || e.status !== 'WAITING_PAYMENT')
        throw new Error('订单不在待支付状态。')
      e.paymentStatus = 'PAID'
      e.status = 'OPEN'
      e.version++
    })
  },
  joinActivity: (id: string) =>
    command(`/activities/${id}/registrations`, {}, () => {
      const a = find(state.activities, id)
      checkScope(a)
      if (a.status !== 'OPEN') throw new Error('活动已停止报名。')
      if (a.participantStatus !== 'NONE') return
      if (a.occupied >= a.capacity) {
        a.participantStatus = 'WAITLISTED'
        a.waitlistPosition = 1
      } else {
        a.participantStatus = 'CONFIRMED'
        a.occupied++
      }
      a.version++
    }),
  cancelActivity: (id: string) =>
    command(`/activities/${id}/registration/cancel`, {}, () => {
      const a = find(state.activities, id)
      if (a.participantStatus === 'CHECKED_IN') throw new Error('已签到，不能直接退出。')
      if (a.participantStatus === 'CONFIRMED') a.occupied--
      a.participantStatus = 'NONE'
      delete a.waitlistPosition
      a.version++
    }),
  checkinActivity: (id: string) =>
    command(`/activities/${id}/checkin`, {}, () => {
      const a = find(state.activities, id)
      if (a.participantStatus !== 'CONFIRMED') throw new Error('请先报名，候补不能签到。')
      a.participantStatus = 'CHECKED_IN'
      a.version++
    }),
  publishActivity: (input: Partial<Activity>) =>
    command('/activities', input, () => {
      if (
        !input.title?.trim() ||
        !input.location?.trim() ||
        !Number.isInteger(input.capacity) ||
        input.capacity! < 1
      )
        throw new Error('请填写活动标题、地点和有效人数。')
      const a: Activity = {
        id: commandKey(),
        campusId: state.campusId,
        ownerId: state.user.id,
        title: input.title,
        category: input.category || '运动',
        cover: input.cover || '/static/activities/badminton.png',
        date: input.date || '时间待确认',
        time: input.time || '',
        location: input.location,
        organizer: state.user.name,
        description: input.description || '',
        capacity: input.capacity!,
        occupied: 0,
        participantStatus: 'NONE',
        status: 'PENDING_REVIEW',
        version: 1,
      }
      state.activities.unshift(a)
      return a
    }),
  saveCourse: (input: Partial<Course>) =>
    command('/timetable/courses/save', input, () => {
      validateCourse(input)
      if (input.id) Object.assign(find(state.courses, input.id), input)
      else state.courses.push({ ...input, id: commandKey() } as Course)
    }),
  deleteCourse: (id: string) =>
    command(`/timetable/courses/${id}/delete`, {}, () => {
      state.courses.splice(
        state.courses.findIndex((x) => x.id === find(state.courses, id).id),
        1,
      )
    }),
  commitImport: (courses: Course[]) =>
    command('/timetable/imports/confirm', { courses }, () => {
      if (!courses.length) throw new Error('草稿中没有课程。')
      courses.forEach(validateCourse)
      const seen = new Set(state.courses.map(signature))
      for (const c of courses) {
        if (seen.has(signature(c))) throw new Error('存在重复课程，请修改或移除后确认。')
        seen.add(signature(c))
      }
      state.courses.push(...courses.map((c) => ({ ...c, id: commandKey() })))
    }),
  sendMessage: (id: string, text: string) =>
    command(`/conversations/${id}/messages`, { text }, () => {
      const c = find(state.conversations, id)
      if (!text.trim() || text.length > 1000) throw new Error('消息长度应为 1–1000 字。')
      c.messages.push({
        id: commandKey(),
        seq: (c.messages[c.messages.length - 1]?.seq || 0) + 1,
        text: text.trim(),
        mine: true,
        time: '刚刚',
        status: 'sent',
      })
      c.preview = text.trim()
      c.time = '刚刚'
    }),
  readConversation: (id: string) =>
    command(`/conversations/${id}/read`, {}, () => {
      find(state.conversations, id).unread = 0
    }),
  readNotices: () =>
    command('/notices/read', {}, () => {
      state.notices.forEach((n) => (n.read = true))
    }),
  async resetDemo() {
    if (!isDemo) throw new Error('真实模式不可重置业务数据。')
    invalidateSession()
    replaceDemo()
    reservations.clear()
  },
}

function normalizeContext(raw: ContextDTO, directory?: SchoolsResponseDTO): {
  campuses: Campus[]
  campusId: string
  user: User
} {
  type RawCampus = CampusContextDTO & Partial<Campus> & { schoolName?: string; school?: string }
  type RawSchool = SchoolContextDTO & { id?: string }
  const contextSchools = (raw.schools || (raw.school ? [raw.school] : [])) as RawSchool[]
  const directorySchools = (directory?.items || []) as RawSchool[]
  const allSchools = [...directorySchools, ...contextSchools]
  const schoolNames = new Map<string, string>()
  const schoolRecords = new Map<string, RawSchool>()
  for (const school of allSchools) {
    const schoolId = school.schoolId || school.id || ''
    // The public directory is authoritative for shared school metadata;
    // context is only a compatibility fallback for records absent there.
    if (schoolId && !schoolRecords.has(schoolId)) schoolRecords.set(schoolId, school)
    if (schoolId && school.name && !schoolNames.has(schoolId)) schoolNames.set(schoolId, school.name)
  }

  // Directory records are retained even when context only contains the
  // selected campus. Context records are merged as a compatibility fallback.
  const records: RawCampus[] = []
  const add = (campus: RawCampus, parent?: RawSchool) => {
    const id = campus.campusId || campus.id || ''
    if (!id) return
    const schoolId = campus.schoolId || parent?.schoolId || parent?.id || ''
    const normalized = { ...campus, campusId: id, schoolId, schoolName: campus.schoolName || parent?.name }
    const existing = records.findIndex((item) => (item.campusId || item.id) === id)
    if (existing >= 0) {
      // Prefer the public directory's metadata, while filling missing fields
      // from a context/legacy record.
      records[existing] = { ...normalized, ...records[existing] }
      if (!records[existing].schoolId) records[existing].schoolId = schoolId
      return
    }
    records.push(normalized)
  }
  for (const school of directorySchools) for (const campus of school.campuses || []) add(campus as RawCampus, school)
  for (const school of contextSchools) for (const campus of school.campuses || []) add(campus as RawCampus, school)
  for (const campus of (raw.campuses || []) as Array<RawCampus>)
    add(campus, raw.school as RawSchool | undefined)
  if (raw.campus) add(raw.campus as RawCampus, raw.school as RawSchool | undefined)

  const campuses: Campus[] = records.map((dto) => {
    const id = dto.campusId || dto.id || ''
    const schoolId = dto.schoolId || ''
    const schoolName =
      dto.schoolName || dto.school || (schoolId ? schoolNames.get(schoolId) : '') || raw.school?.name || ''
    return {
      id,
      // Keep schoolId on every campus; switchCampus uses it for canonical PUT.
      schoolId,
      school: schoolName,
      name: dto.name,
      shortName: dto.shortName || dto.name,
      enabled: campusEnabled(dto),
      timezone: dto.timezone || (schoolId ? schoolRecords.get(schoolId)?.timezone : undefined),
      configVersion: dto.configVersion || (schoolId ? schoolRecords.get(schoolId)?.configVersion : undefined),
    }
  })

  const contextCampusId = raw.campusId || raw.campus?.campusId || raw.campus?.id || ''
  const campusId = contextCampusId || campuses.find((campus) => campus.enabled)?.id || ''
  const selected = raw.campus || campuses.find((campus) => campus.id === campusId)
  const user = normalizeUser(raw.user, selected, raw.school, raw.loggedIn)
  return { campuses, campusId, user }
}

function campusEnabled(dto: { status?: string; enabled?: boolean }) {
  if (dto.status) {
    const status = dto.status.toUpperCase()
    if (status === 'ENABLED' || status === 'ACTIVE') return true
    if (status === 'DISABLED' || status === 'INACTIVE') return false
  }
  return dto.enabled !== false
}

function normalizeUser(
  raw: ContextUserDTO | User | undefined,
  campus?: CampusContextDTO | Campus,
  school?: SchoolContextDTO,
  contextLoggedIn?: boolean,
): User {
  const value = (raw || {}) as ContextUserDTO & Partial<User>
  return {
    id: value.userId || value.id || '',
    name: value.displayName || value.name || '',
    schoolId: value.schoolId || campus?.schoolId || school?.schoolId,
    school: value.school || school?.name || (campus as Campus | undefined)?.school || '',
    campusId:
      value.campusId ||
      (campus as CampusContextDTO | undefined)?.campusId ||
      (campus as Campus | undefined)?.id ||
      '',
    loggedIn: value.loggedIn ?? contextLoggedIn === true,
    verification: value.verificationStatus || value.verification || 'NONE',
    runner: value.runnerEligible ?? value.runner ?? false,
  }
}
const reservations = new Map<string, string>()
function signature(c: Partial<Course>) {
  return `${c.title}|${c.day}|${c.start}|${(c.weeks || []).join(',')}`
}
function validateCourse(c: Partial<Course>) {
  if (
    !c.title?.trim() ||
    !c.room?.trim() ||
    !c.day ||
    c.day < 1 ||
    c.day > 7 ||
    !c.start ||
    c.start < 1 ||
    c.start > 12 ||
    !c.duration ||
    c.duration < 1 ||
    c.start + c.duration > 13 ||
    !c.weeks?.length ||
    c.weeks.some((w) => w < 1 || w > 20)
  )
    throw new Error('请检查课程名、教室、星期、节次与周次。')
}

import {
  state,
  isDemo,
  replaceDemo,
  clearPrivateWorkspace,
  suspendDemoSession,
  restoreDemoSession,
} from '@/state'
import { request, setToken, commandKey, ApiError, prepareLogoutRequest } from './transport'
import { uploadVerificationFile } from './media'
import type {
  Activity,
  Campus,
  CampusDirectoryPage,
  CampusContextDTO,
  ContextDTO,
  ContextUserDTO,
  Course,
  Errand,
  Product,
  Conversation,
  Notice,
  MembershipDTO,
  SchoolContextDTO,
  SchoolsResponseDTO,
  SubmitVerificationInput,
  User,
  VerificationApplicationDTO,
  VerificationStatus,
  VerificationUploadFile,
  WechatLoginDTO,
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
let contextSelectionGeneration = 0
let contextWriteTail: Promise<void> = Promise.resolve()
let authenticatedUserId = ''
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
  contextSelectionGeneration++
  pending.clear()
  uncertainKeys.clear()
}
async function command<T>(
  path: string,
  body: unknown,
  apply: () => T,
  options: { refreshAfter?: boolean } = {},
): Promise<T> {
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
      if (options.refreshAfter !== false) {
        await refresh()
        assertSession(stamp, actorId)
      }
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
      const [rawContext, directory] = await Promise.all([
        request<ContextDTO>('GET', '/context'),
        request<SchoolsResponseDTO>('GET', '/schools'),
      ])
      const ctx = normalizeContext(rawContext, directory)
      if (!current()) return
      if (!ctx.user.loggedIn && (state.user.loggedIn || authenticatedUserId)) clearLocalAuth()
      const previousCampusId = state.campusId
      Object.assign(state, ctx)
      if (previousCampusId !== state.campusId) clearPublicContent()
      if (!state.campusId) {
        clearPublicContent()
        state.networkState = 'error'
        state.networkError = '暂无可用校区，校园内容暂时无法加载。'
        return
      }
      // Context is a usable global capability even while later business APIs
      // are not deployed. Public-feed failures are isolated per tab below.
      state.networkState = 'ready'
      await refreshPublicContent(current)
      if (!current()) return
      if (state.user.loggedIn) {
        const actorId = state.user.id
        state.privateState = 'loading'
        state.privateError = ''
        try {
          const privateData = await request<{
            courses: Course[]
            conversations: Conversation[]
            notices: Notice[]
            favorites: string[]
          }>('GET', '/me/workspace')
          if (!current() || state.user.id !== actorId || !state.user.loggedIn) return
          Object.assign(state, privateData)
          state.privateState = 'ready'
        } catch (error) {
          if (!current() || state.user.id !== actorId || !state.user.loggedIn) return
          state.privateState = 'error'
          state.privateError = errorMessage(error, '个人记录接口暂不可用，请稍后重试。')
        }
      } else {
        state.privateState = 'ready'
        state.privateError = ''
      }
    }
    if (current()) state.networkState = 'ready'
  } catch (e) {
    if (current()) {
      if (e instanceof ApiError && e.httpStatus === 401 && state.user.loggedIn) clearLocalAuth()
      state.networkState = 'error'
      state.networkError = errorMessage(e, '加载失败')
    }
    throw e
  }
}
export const api = {
  refresh,
  canWechatLogin() {
    return isDemo || isWechatMiniProgram()
  },
  async listCampuses(keyword = '', cursor?: string): Promise<CampusDirectoryPage> {
    const normalizedKeyword = keyword.trim()
    if (normalizedKeyword.length > 160)
      throw new ApiError('REQUEST_INVALID', '学校名称最多输入 160 个字符。')
    if (isDemo) {
      const items = state.campuses.filter(
        (campus) => !normalizedKeyword || campus.school.includes(normalizedKeyword),
      )
      return { items, nextCursor: null }
    }
    const params: string[] = []
    if (normalizedKeyword) params.push(`keyword=${encodeURIComponent(normalizedKeyword)}`)
    if (cursor) params.push(`cursor=${encodeURIComponent(cursor)}`)
    const directory = await request<SchoolsResponseDTO>(
      'GET',
      `/schools${params.length ? `?${params.join('&')}` : ''}`,
    )
    const normalized = normalizeContext({}, directory)
    return { items: normalized.campuses, nextCursor: directory.nextCursor || null }
  },
  async switchCampus(selection: string | Campus) {
    const campus = typeof selection === 'string' ? find(state.campuses, selection) : selection
    const id = campus.id
    if (!campus.enabled) throw new Error('该校区暂未开放。')
    if (isDemo) {
      state.campusId = id
      await refresh()
    } else {
      if (!campus.schoolId)
        throw new ApiError('CONTEXT_INVALID', '校区缺少所属学校信息，请刷新后重试。')
      const intent = ++contextSelectionGeneration
      const session = sessionGeneration
      const assertCurrentSelection = () => {
        if (intent !== contextSelectionGeneration || session !== sessionGeneration)
          throw new ApiError('CONTEXT_SUPERSEDED', '已选择新的校区，本次旧请求结果未应用。')
      }
      const write = contextWriteTail.then(async () => {
        assertCurrentSelection()
        await request('PUT', '/context', { schoolId: campus.schoolId, campusId: id })
      })
      contextWriteTail = write.then(
        () => undefined,
        () => undefined,
      )
      await write
      assertCurrentSelection()
      await refresh()
      assertCurrentSelection()
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
    if (!isWechatMiniProgram())
      throw new ApiError('WECHAT_LOGIN_UNAVAILABLE', 'H5 暂不支持微信登录，请在微信小程序中打开。')
    const code = await new Promise<string>((resolve, reject) =>
      uni.login({
        provider: 'weixin',
        success: (r) =>
          r.code
            ? resolve(r.code)
            : reject(new ApiError('WECHAT_CODE_MISSING', '微信未返回登录凭证，请重试。')),
        fail: () =>
          reject(new ApiError('WECHAT_LOGIN_FAILED', '微信授权没有完成，请在小程序中重试。')),
      }),
    )
    assertSession(stamp)
    const clientContext = state.campusId ? { campusId: state.campusId } : undefined
    const result = await request<WechatLoginDTO>('POST', '/auth/wechat/login', {
      code,
      ...(clientContext ? { clientContext } : {}),
    })
    assertSession(stamp)
    validateLoginResult(result)
    authenticatedUserId = typeof result.userId === 'string' ? result.userId : ''
    setToken(result.accessToken)
    state.sessionExpiresAt = validExpiry(result.expiresAt) ? result.expiresAt : ''
    state.profileCompletion =
      typeof result.profileCompletion === 'boolean' ? result.profileCompletion : null
    const selectedCampus = state.campuses.find((campus) => campus.id === state.campusId)
    state.user = {
      id: authenticatedUserId,
      name: '',
      schoolId: selectedCampus?.schoolId,
      school: selectedCampus?.school || '',
      campusId: state.campusId,
      loggedIn: true,
      verification: 'NONE',
      runner: false,
    }
    try {
      await refresh()
      assertSession(stamp)
    } catch (error) {
      assertSession(stamp)
      if (error instanceof ApiError && [401, 403].includes(error.httpStatus || 0)) {
        clearLocalAuth()
        throw error
      }
      // The token response is authoritative. A later context/network failure
      // stays visible through networkState without turning into a fake login
      // failure or discarding the confirmed in-memory session.
      return
    }
    if (!state.user.loggedIn) {
      clearLocalAuth()
      throw new ApiError(
        'SESSION_NOT_ESTABLISHED',
        '登录凭证已返回，但后端未建立可用会话，请稍后重试。',
      )
    }
  },
  async logout() {
    invalidateSession()
    let sendRemoteLogout: ReturnType<typeof prepareLogoutRequest> | null = null
    if (isDemo) {
      suspendDemoSession()
    } else {
      // Invalidate private state first, then let transport clear and capture
      // the old Bearer token before any remote revocation request is sent.
      clearLocalSessionState()
      sendRemoteLogout = prepareLogoutRequest()
    }
    state.networkState = 'ready'
    state.networkError = ''
    if (sendRemoteLogout) {
      try {
        await sendRemoteLogout()
      } catch (error) {
        const apiError = error instanceof ApiError ? error : undefined
        throw new ApiError(
          'LOGOUT_REMOTE_UNKNOWN',
          '本机已退出，但服务端会话撤销结果未确认；请勿在共享设备继续使用。',
          apiError?.traceId,
          'RETRY',
          apiError?.currentVersion,
          apiError?.currentState,
          apiError?.httpStatus,
        )
      }
    }
  },
  async uploadVerificationMaterial(file: VerificationUploadFile) {
    requireUser()
    const stamp = sessionGeneration
    const actorId = state.user.id
    const campusId = state.campusId
    const assertCurrent = () => {
      assertSession(stamp, actorId)
      if (campusId !== state.campusId)
        throw new ApiError('SCOPE_CHANGED', '校区已切换，本次材料上传结果未应用。')
    }
    return uploadVerificationFile(file, assertCurrent)
  },
  async verify(input: SubmitVerificationInput): Promise<VerificationApplicationDTO> {
    const payload = normalizeVerificationSubmission(input)
    const result = await command<VerificationApplicationDTO>(
      '/verifications',
      payload,
      () => ({
        applicationId: `demo-verification-${Date.now()}`,
        status: 'PENDING',
        version: 1,
      }),
      { refreshAfter: false },
    )
    const application = normalizeVerificationApplication(result)
    state.user.verification = 'PENDING'
    return application
  },
  async checkMembership(schoolId: string, campusId: string): Promise<MembershipDTO> {
    requireUser()
    const normalizedSchoolId = positiveDecimalId(schoolId, 'schoolId')
    const normalizedCampusId = positiveDecimalId(campusId, 'campusId')
    const result = isDemo
      ? {
          effective: true as const,
          expiresAt: '2027-09-17T00:00:00+08:00',
          scopeVersion: 1,
        }
      : await request<MembershipDTO>(
          'GET',
          `/membership?schoolId=${encodeURIComponent(normalizedSchoolId)}&campusId=${encodeURIComponent(normalizedCampusId)}`,
        )
    const membership = normalizeMembership(result)
    state.user.verification = 'VERIFIED'
    state.membershipExpiresAt = membership.expiresAt
    state.membershipScopeVersion = membership.scopeVersion
    return membership
  },
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

async function refreshPublicContent(current: () => boolean) {
  const campusId = state.campusId
  const q = `?campusId=${encodeURIComponent(campusId)}&limit=50`
  const definitions = [
    ['market', '/market/listings' + q, 'products'],
    ['errand', '/errands/available' + q, 'errands'],
    ['activity', '/activities' + q, 'activities'],
  ] as const
  for (const [service] of definitions) {
    state.contentState[service] = 'loading'
    state.contentError[service] = ''
  }
  const results = await Promise.allSettled(
    definitions.map(([, path]) => request<{ items: unknown[] }>('GET', path)),
  )
  if (!current() || campusId !== state.campusId) return
  results.forEach((result, index) => {
    const [service, , field] = definitions[index]
    if (result.status === 'fulfilled' && Array.isArray(result.value?.items)) {
      assignPublicItems(field, result.value.items)
      state.contentState[service] = 'ready'
      state.contentError[service] = ''
    } else {
      assignPublicItems(field, [])
      state.contentState[service] = 'error'
      state.contentError[service] = errorMessage(
        result.status === 'rejected' ? result.reason : undefined,
        '该功能的真实接口暂不可用，没有加载演示数据。',
      )
    }
  })
}

function assignPublicItems(field: 'products' | 'errands' | 'activities', items: unknown[]) {
  if (field === 'products') state.products = items as Product[]
  else if (field === 'errands') state.errands = items as Errand[]
  else state.activities = items as Activity[]
}

function clearPublicContent() {
  state.products = []
  state.errands = []
  state.activities = []
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function clearLocalAuth() {
  setToken('')
  clearLocalSessionState()
}

function clearLocalSessionState() {
  authenticatedUserId = ''
  state.sessionExpiresAt = ''
  state.profileCompletion = null
  clearPrivateWorkspace()
}

function isWechatMiniProgram() {
  try {
    const platform = (
      uni as unknown as { getSystemInfoSync?: () => { uniPlatform?: string } }
    ).getSystemInfoSync?.().uniPlatform
    return platform === 'mp-weixin'
  } catch {
    return false
  }
}

function validateLoginResult(result: WechatLoginDTO) {
  if (!result || typeof result.accessToken !== 'string' || !result.accessToken)
    throw new ApiError('CONTRACT_INVALID', '登录响应缺少有效的 accessToken。')
}

function validExpiry(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value)
  )
}

function normalizeContext(
  raw: ContextDTO,
  directory?: SchoolsResponseDTO,
): {
  campuses: Campus[]
  campusId: string
  user: User
} {
  type RawCampus = CampusContextDTO &
    Partial<Campus> & { schoolName?: string; school?: string; schoolStatus?: string }
  type RawSchool = SchoolContextDTO & { id?: string }
  if (directory && !Array.isArray(directory.items))
    throw new ApiError('CONTRACT_INVALID', '学校目录响应缺少 items 数组。')
  if (directory) validateCanonicalDirectory(directory)
  const contextSchools = (raw.schools || (raw.school ? [raw.school] : [])) as RawSchool[]
  const directorySchools = (directory?.items || []) as RawSchool[]
  const allSchools = [...directorySchools, ...contextSchools]
  const schoolNames = new Map<string, string>()
  const schoolRecords = new Map<string, RawSchool>()
  for (const school of allSchools) {
    const schoolId = wireId(school.schoolId, school.id, 'schoolId')
    // The public directory is authoritative for shared school metadata;
    // context is only a compatibility fallback for records absent there.
    if (schoolId && !schoolRecords.has(schoolId)) schoolRecords.set(schoolId, school)
    if (schoolId && school.name && !schoolNames.has(schoolId))
      schoolNames.set(schoolId, school.name)
  }

  // Directory records are retained even when context only contains the
  // selected campus. Context records are merged as a compatibility fallback.
  const records: RawCampus[] = []
  const add = (campus: RawCampus, parent?: RawSchool) => {
    const id = wireId(campus.campusId, campus.id, 'campusId', true)
    const schoolId =
      wireId(campus.schoolId, undefined, 'schoolId') ||
      wireId(parent?.schoolId, parent?.id, 'schoolId', true)
    const normalized = {
      ...campus,
      campusId: id,
      schoolId,
      schoolName: campus.schoolName || parent?.name,
      schoolStatus: parent?.status,
    }
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
  for (const school of directorySchools)
    for (const campus of school.campuses || []) add(campus as RawCampus, school)
  for (const school of contextSchools)
    for (const campus of school.campuses || []) add(campus as RawCampus, school)
  for (const campus of (raw.campuses || []) as Array<RawCampus>)
    add(campus, raw.school as RawSchool | undefined)
  if (raw.campus) add(raw.campus as RawCampus, raw.school as RawSchool | undefined)

  const campuses: Campus[] = records.map((dto) => {
    const id = wireId(dto.campusId, dto.id, 'campusId', true)
    const schoolId = wireId(dto.schoolId, undefined, 'schoolId', true)
    const schoolName =
      dto.schoolName ||
      dto.school ||
      (schoolId ? schoolNames.get(schoolId) : '') ||
      raw.school?.name ||
      ''
    return {
      id,
      // Keep schoolId on every campus; switchCampus uses it for canonical PUT.
      schoolId,
      school: schoolName,
      name: dto.name,
      shortName: dto.shortName || dto.name,
      enabled: campusEnabled(dto) && campusEnabled({ status: dto.schoolStatus }),
      timezone: dto.timezone || (schoolId ? schoolRecords.get(schoolId)?.timezone : undefined),
      configVersion:
        dto.configVersion || (schoolId ? schoolRecords.get(schoolId)?.configVersion : undefined),
    }
  })

  const contextCampusId = wireId(raw.campusId || raw.campus?.campusId, raw.campus?.id, 'campusId')
  const contextCampus = campuses.find((campus) => campus.id === contextCampusId)
  const campusId =
    (contextCampus?.enabled ? contextCampus.id : '') ||
    campuses.find((campus) => campus.enabled)?.id ||
    ''
  const selected = raw.campus || campuses.find((campus) => campus.id === campusId)
  const user = normalizeUser(raw.user, selected, raw.school, raw.loggedIn)
  return { campuses, campusId, user }
}

function validateCanonicalDirectory(directory: SchoolsResponseDTO) {
  for (const school of directory.items) {
    const schoolId = canonicalDirectoryId(school.schoolId, 'schoolId')
    canonicalDirectoryStatus(school.status, '学校')
    if (!Array.isArray(school.campuses))
      throw new ApiError('CONTRACT_INVALID', '学校目录中的 campuses 必须是数组。')
    for (const campus of school.campuses) {
      canonicalDirectoryId(campus.campusId, 'campusId')
      const campusSchoolId = canonicalDirectoryId(campus.schoolId, 'schoolId')
      if (campusSchoolId !== schoolId)
        throw new ApiError('CONTRACT_INVALID', '学校目录中的校区归属与父学校不一致。')
      canonicalDirectoryStatus(campus.status, '校区')
    }
  }
}

function canonicalDirectoryId(value: unknown, field: 'schoolId' | 'campusId') {
  if (typeof value !== 'string' || !value.trim())
    throw new ApiError('CONTRACT_INVALID', `学校目录中的 ${field} 必须是非空字符串。`)
  return value
}

function canonicalDirectoryStatus(value: unknown, subject: '学校' | '校区') {
  if (value !== 'ENABLED')
    throw new ApiError('CONTRACT_INVALID', `学校目录中的${subject}状态必须是 ENABLED。`)
}

function campusEnabled(dto: { status?: string; enabled?: boolean }) {
  if (dto.status) {
    const status = dto.status.toUpperCase()
    if (status === 'ENABLED' || status === 'ACTIVE') return true
    if (status === 'DISABLED' || status === 'INACTIVE') return false
    return false
  }
  return dto.enabled !== false
}

function wireId(
  canonical: unknown,
  legacy: unknown,
  field: 'schoolId' | 'campusId',
  required = false,
) {
  const value = canonical ?? legacy
  if (value === undefined || value === null || value === '') {
    if (required) throw new ApiError('CONTRACT_INVALID', `学校目录中的 ${field} 缺失。`)
    return ''
  }
  if (typeof value !== 'string' || !value.trim())
    throw new ApiError('CONTRACT_INVALID', `${field} 必须是后端返回的非空字符串。`)
  return value
}

function normalizeUser(
  raw: ContextUserDTO | User | undefined,
  campus?: CampusContextDTO | Campus,
  school?: SchoolContextDTO,
  contextLoggedIn?: boolean,
): User {
  const value = (raw || {}) as ContextUserDTO & Partial<User>
  return {
    id: value.userId || value.id || (contextLoggedIn ? authenticatedUserId : ''),
    name: value.displayName || value.name || '',
    schoolId: value.schoolId || campus?.schoolId || school?.schoolId,
    school: value.school || school?.name || (campus as Campus | undefined)?.school || '',
    campusId:
      value.campusId ||
      (campus as CampusContextDTO | undefined)?.campusId ||
      (campus as Campus | undefined)?.id ||
      '',
    loggedIn: value.loggedIn ?? contextLoggedIn === true,
    verification: normalizeVerificationStatus(value.verificationStatus || value.verification),
    runner: value.runnerEligible ?? value.runner ?? false,
  }
}

function normalizeVerificationSubmission(input: SubmitVerificationInput): SubmitVerificationInput {
  if (!input || input.method !== 'STUDENT_CARD')
    throw new ApiError('REQUEST_INVALID', '当前只支持学生证材料认证。', undefined, 'USER_FIX')
  const schoolId = isDemo
    ? requireTextId(input.schoolId, 'schoolId')
    : positiveDecimalId(input.schoolId, 'schoolId')
  const campusId = isDemo
    ? requireTextId(input.campusId, 'campusId')
    : positiveDecimalId(input.campusId, 'campusId')
  if (
    !Array.isArray(input.evidenceRefs) ||
    !input.evidenceRefs.length ||
    input.evidenceRefs.length > 10
  )
    throw new ApiError('REQUEST_INVALID', '认证材料必须为 1–10 个媒体 ID。', undefined, 'USER_FIX')
  const evidenceRefs = input.evidenceRefs.map((reference) => {
    const value = requireTextId(reference, 'evidenceRefs')
    if (value.length > 128 || value.includes('://'))
      throw new ApiError(
        'REQUEST_INVALID',
        '认证材料必须使用媒体服务返回的 ID，不能提交文件地址。',
        undefined,
        'USER_FIX',
      )
    return value
  })
  if (new Set(evidenceRefs).size !== evidenceRefs.length)
    throw new ApiError('REQUEST_INVALID', '认证材料不能重复。', undefined, 'USER_FIX')
  return { schoolId, campusId, method: 'STUDENT_CARD', evidenceRefs }
}

function normalizeVerificationApplication(value: VerificationApplicationDTO) {
  if (
    !value ||
    typeof value.applicationId !== 'string' ||
    !value.applicationId.trim() ||
    (!/^[1-9][0-9]*$/.test(value.applicationId) && !isDemo) ||
    value.status !== 'PENDING' ||
    !Number.isSafeInteger(value.version) ||
    value.version < 1
  )
    throw new ApiError('CONTRACT_INVALID', '认证提交响应不符合当前契约。')
  return {
    applicationId: value.applicationId,
    status: value.status,
    version: value.version,
  }
}

function normalizeMembership(value: MembershipDTO): MembershipDTO {
  if (
    !value ||
    value.effective !== true ||
    !validExpiry(value.expiresAt) ||
    !Number.isSafeInteger(value.scopeVersion) ||
    value.scopeVersion < 1
  )
    throw new ApiError('CONTRACT_INVALID', '成员资格响应不符合当前契约。')
  return value
}

function positiveDecimalId(value: unknown, field: 'schoolId' | 'campusId') {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value))
    throw new ApiError(
      'REQUEST_INVALID',
      `${field} 必须是十进制正整数字符串。`,
      undefined,
      'USER_FIX',
    )
  return value
}

function requireTextId(value: unknown, field: string) {
  if (typeof value !== 'string' || !value.trim())
    throw new ApiError('REQUEST_INVALID', `${field} 不能为空。`, undefined, 'USER_FIX')
  return value.trim()
}

function normalizeVerificationStatus(value: unknown): VerificationStatus {
  if (value === 'APPROVED') return 'VERIFIED'
  if (
    value === 'NONE' ||
    value === 'PENDING' ||
    value === 'VERIFIED' ||
    value === 'REJECTED' ||
    value === 'EXPIRED' ||
    value === 'REVOKED'
  )
    return value
  return 'NONE'
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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'
import { state } from '@/state'
import type { Course } from '@/types'

async function finish<T>(promise: Promise<T>): Promise<T> {
  // Attach the rejection handler before advancing timers so intentional failures
  // never escape as unhandled rejections during fault-injection tests.
  const captured = promise.then(
    (value) => ({ ok: true as const, value }),
    (error) => ({ ok: false as const, error }),
  )
  await vi.runAllTimersAsync()
  const result = await captured
  if (!result.ok) throw result.error
  return result.value
}

beforeEach(async () => {
  vi.useFakeTimers()
  await api.resetDemo()
})
afterEach(() => {
  vi.useRealTimers()
})

describe('demo adapter state transitions', () => {
  it('coalesces concurrent acceptance of the same order into one transition', async () => {
    const order = state.errands.find((e) => e.id === 'parcel')!
    await finish(Promise.all(Array.from({ length: 40 }, () => api.acceptErrand(order.id))))
    expect(order.status).toBe('ACCEPTED')
    expect(order.runnerId).toBe('demo-user')
    expect(order.version).toBe(2)
    await expect(finish(api.acceptErrand(order.id))).rejects.toThrow('状态已更新')
    expect(order.version).toBe(2)
  })

  it('rejects operations on another campus without changing its order or activity', async () => {
    const orderBefore = JSON.stringify(state.errands.find((e) => e.id === 'west-parcel'))
    const activityBefore = JSON.stringify(state.activities.find((a) => a.id === 'boardgame'))
    await expect(finish(api.acceptErrand('west-parcel'))).rejects.toMatchObject({
      code: 'SCOPE_CHANGED',
    })
    await expect(finish(api.joinActivity('boardgame'))).rejects.toMatchObject({
      code: 'SCOPE_CHANGED',
    })
    expect(JSON.stringify(state.errands.find((e) => e.id === 'west-parcel'))).toBe(orderBefore)
    expect(JSON.stringify(state.activities.find((a) => a.id === 'boardgame'))).toBe(activityBefore)
  })

  it('keeps payment, fulfillment and payout as separate facts', async () => {
    const created = await finish(
      api.createErrand({
        title: '取资料',
        pickup: '图书馆',
        destination: '教学楼',
        rewardFen: 500,
      }),
    )
    expect(created).toMatchObject({
      status: 'WAITING_PAYMENT',
      paymentStatus: 'UNPAID',
      payoutStatus: 'NONE',
    })
    await finish(api.simulatePayment(created.id))
    expect(state.errands.find((e) => e.id === created.id)).toMatchObject({
      status: 'OPEN',
      paymentStatus: 'PAID',
      payoutStatus: 'NONE',
    })
    await finish(api.advanceErrand('my-parcel'))
    expect(state.errands.find((e) => e.id === 'my-parcel')).toMatchObject({
      status: 'COMPLETED',
      paymentStatus: 'PAID',
      payoutStatus: 'PENDING',
    })
    await finish(api.cancelErrand(created.id))
    expect(state.errands.find((e) => e.id === created.id)).toMatchObject({
      status: 'CANCELLED',
      paymentStatus: 'REFUND_PENDING',
      payoutStatus: 'NONE',
    })
  })

  it('records buyer confirmation without claiming a mutually confirmed sale', async () => {
    const item = state.products.find((p) => p.id === 'book')!
    await expect(finish(api.confirmProduct(item.id))).rejects.toThrow('请先预约')
    await finish(api.reserveProduct(item.id))
    expect(item).toMatchObject({ status: 'RESERVED', buyerConfirmed: false, version: 2 })
    await finish(api.confirmProduct(item.id))
    expect(item).toMatchObject({ status: 'RESERVED', buyerConfirmed: true, version: 3 })
    await finish(api.confirmProduct(item.id))
    expect(item.version).toBe(3)
    await expect(finish(api.cancelReservation(item.id))).rejects.toThrow('已确认交接')
    expect(item.status).not.toBe('SOLD')
  })

  it('releases an unconfirmed reservation and clears its confirmation state', async () => {
    await finish(api.reserveProduct('lamp'))
    await finish(api.cancelReservation('lamp'))
    const item = state.products.find((p) => p.id === 'lamp')!
    expect(item.status).toBe('AVAILABLE')
    expect(item.buyerConfirmed).toBeUndefined()
    await expect(finish(api.confirmProduct('lamp'))).rejects.toThrow('请先预约')
  })

  it('clears private state for guests and restores the same account snapshot on login', async () => {
    await finish(api.sendMessage('chat-book', '这是我的演示私信'))
    await finish(api.toggleFavorite('lamp'))
    await finish(api.joinActivity('badminton'))
    await finish(api.reserveProduct('book'))
    await finish(api.confirmProduct('book'))
    const privateBefore = JSON.stringify({
      courses: state.courses,
      conversations: state.conversations,
      notices: state.notices,
      favorites: state.favorites,
    })
    const occupied = state.activities.find((a) => a.id === 'badminton')!.occupied
    await api.logout()
    expect(state.user).toMatchObject({ id: '', name: '', loggedIn: false, runner: false })
    expect(state.courses).toEqual([])
    expect(state.conversations).toEqual([])
    expect(state.notices).toEqual([])
    expect(state.favorites).toEqual([])
    expect(state.activities.every((a) => a.participantStatus === 'NONE')).toBe(true)
    expect(state.products.find((p) => p.id === 'book')!.buyerConfirmed).toBeUndefined()
    await expect(api.sendMessage('chat-book', '访客不能发送')).rejects.toMatchObject({
      code: 'LOGIN_REQUIRED',
    })
    await api.logout() // Repeated logout must not replace the saved account with a guest snapshot.
    await finish(api.login())
    expect(state.user).toMatchObject({ id: 'demo-user', loggedIn: true })
    expect(
      JSON.stringify({
        courses: state.courses,
        conversations: state.conversations,
        notices: state.notices,
        favorites: state.favorites,
      }),
    ).toBe(privateBefore)
    expect(state.activities.find((a) => a.id === 'badminton')).toMatchObject({
      occupied,
      participantStatus: 'CONFIRMED',
    })
    expect(state.products.find((p) => p.id === 'book')).toMatchObject({
      status: 'RESERVED',
      buyerConfirmed: true,
    })
  })

  it('does not partially import a batch whose later course is duplicated', async () => {
    const before = JSON.stringify(state.courses)
    const first: Course = {
      ...state.courses[0],
      id: 'new-course',
      title: '设计概论',
      day: 6,
      weeks: [1, 2, 3],
    }
    const duplicate: Course = {
      ...state.courses[0],
      id: 'duplicate-course',
      weeks: [...state.courses[0].weeks],
    }
    await expect(finish(api.commitImport([first, duplicate]))).rejects.toThrow('重复课程')
    expect(JSON.stringify(state.courses)).toBe(before)
    await expect(
      finish(api.commitImport([first, { ...first, id: 'duplicate-in-batch' }])),
    ).rejects.toThrow('重复课程')
    expect(JSON.stringify(state.courses)).toBe(before)
    await finish(api.commitImport([first]))
    expect(state.courses).toHaveLength(JSON.parse(before).length + 1)
  })

  it('does not apply an old send after logout and a new login', async () => {
    const initialCount = state.conversations.find((c) => c.id === 'chat-book')!.messages.length
    const obsolete = api.sendMessage('chat-book', '这条旧会话请求不应落库').catch((error) => error)
    await api.logout()
    await finish(api.login())
    expect(await obsolete).toMatchObject({ code: 'SESSION_CHANGED' })
    expect(state.conversations.find((c) => c.id === 'chat-book')!.messages).toHaveLength(
      initialCount,
    )
  })

  it('isolates a reset from in-flight commands and allows fresh commands afterward', async () => {
    const obsolete = api.acceptErrand('parcel').catch((error) => error)
    await api.resetDemo()
    await finish(api.acceptErrand('parcel'))
    expect(await obsolete).toMatchObject({ code: 'SESSION_CHANGED' })
    expect(state.errands.find((e) => e.id === 'parcel')).toMatchObject({
      status: 'ACCEPTED',
      runnerId: 'demo-user',
      version: 2,
    })
  })

  it('prevents an interrupted login from restoring private data after another logout', async () => {
    await api.logout()
    const obsolete = api.login().catch((error) => error)
    await api.logout()
    await vi.runAllTimersAsync()
    expect(await obsolete).toMatchObject({ code: 'SESSION_CHANGED' })
    expect(state.user.loggedIn).toBe(false)
    expect(state.conversations).toEqual([])
    await finish(api.login())
    expect(state.user.loggedIn).toBe(true)
    expect(state.conversations.length).toBeGreaterThan(0)
  })
})

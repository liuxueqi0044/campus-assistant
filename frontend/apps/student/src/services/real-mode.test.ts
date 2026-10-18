import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface RequestOptions {
  url: string
  method?: string
  success: (response: { statusCode: number; data: unknown }) => void
  fail: (error?: unknown) => void
}
const send = vi.fn((_options: RequestOptions): void => {})
beforeEach(() => {
  vi.resetModules()
  send.mockReset()
  vi.stubEnv('VITE_API_MODE', 'real')
  vi.stubEnv('VITE_API_BASE_URL', 'https://campus.example.test/app-api/campus/v1')
  vi.stubGlobal('uni', { request: send })
})
afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
})

describe('real mode never falls back to demonstration data', () => {
  it('normalizes nested school/campus context and writes the canonical PUT context route', async () => {
    const context = {
      school: { schoolId: 'S1', name: '测试学校' },
      campus: {
        campusId: 'C11',
        schoolId: 'S1',
        name: '东校区',
        shortName: '东校',
        timezone: 'Asia/Shanghai',
        status: 'ACTIVE',
        configVersion: '7',
      },
      campuses: [
        {
          campusId: 'C11',
          schoolId: 'S1',
          name: '东校区',
          shortName: '东校',
          status: 'ACTIVE',
        },
        {
          campusId: 'C12',
          schoolId: 'S1',
          name: '西校区',
          shortName: '西校',
          status: 'ACTIVE',
        },
      ],
      user: { loggedIn: false },
    }
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({ statusCode: 200, data: { code: 0, data: context } })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    await api.refresh()
    expect(state.campuses).toMatchObject([
      { id: 'C11', schoolId: 'S1', school: '测试学校', enabled: true },
      { id: 'C12', schoolId: 'S1', school: '测试学校', enabled: true },
    ])
    await api.switchCampus('C12')
    expect(send.mock.calls.some(([request]) => request.method === 'PUT' && request.url.endsWith('/context'))).toBe(true)
    expect(send.mock.calls.find(([request]) => request.method === 'PUT')?.[0]).toMatchObject({
      data: { schoolId: 'S1', campusId: 'C12' },
    })
  })

  it('merges the public schools directory with the current context and honors canonical statuses', async () => {
    const context = { loggedIn: true, browseMode: 'PUBLIC', campus: null }
    const schools = {
      items: [
        {
          schoolId: 'S1',
          name: '目录学校',
          campuses: [
            { campusId: 'C-disabled', schoolId: 'S1', name: '已关闭', status: 'DISABLED' },
            { campusId: 'C-enabled', schoolId: 'S1', name: '可浏览', status: 'ENABLED' },
          ],
        },
      ],
      nextCursor: null,
    }
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({ statusCode: 200, data: { code: 0, data: context } })
      else if (options.url.endsWith('/schools'))
        options.success({ statusCode: 200, data: { code: 0, data: schools } })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    await api.refresh()
    expect(state.campuses).toMatchObject([
      { id: 'C-disabled', schoolId: 'S1', enabled: false },
      { id: 'C-enabled', schoolId: 'S1', enabled: true },
    ])
    // No context selection is persisted by the frontend: this is only the
    // first enabled public browsing default.
    expect(state.campusId).toBe('C-enabled')
    expect(state.user).toMatchObject({ loggedIn: true, id: '', verification: 'NONE', runner: false })
    expect(send.mock.calls.some(([request]) => request.method === 'PUT')).toBe(false)
  })

  it('accepts legacy ids, preserves the parent school id, and keeps anonymous state unprivileged', async () => {
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              loggedIn: false,
              school: { id: 'legacy-school', name: '旧学校' },
              campus: null,
            },
          },
        })
      else if (options.url.endsWith('/schools'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              items: [
                {
                  id: 'legacy-school',
                  name: '旧学校',
                  campuses: [{ id: 'legacy-campus', name: '旧校区', status: 'ACTIVE' }],
                },
              ],
            },
          },
        })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    await api.refresh()
    expect(state.campuses).toMatchObject([
      { id: 'legacy-campus', schoolId: 'legacy-school', school: '旧学校', enabled: true },
    ])
    expect(state.campusId).toBe('legacy-campus')
    expect(state.user).toMatchObject({ id: '', loggedIn: false, verification: 'NONE', runner: false })
  })

  it('shows an understandable state and keeps selection empty when no campus is enabled', async () => {
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({ statusCode: 200, data: { code: 0, data: { loggedIn: false } } })
      else if (options.url.endsWith('/schools'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              items: [
                {
                  schoolId: 'S1',
                  name: '维护中学校',
                  campuses: [{ campusId: 'C1', schoolId: 'S1', name: '维护中', status: 'INACTIVE' }],
                },
              ],
            },
          },
        })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    await api.refresh()
    expect(state.campusId).toBe('')
    expect(state.networkState).toBe('error')
    expect(state.networkError).toContain('暂无可用校区')
    expect(send.mock.calls.some(([request]) => request.url.includes('/market/listings'))).toBe(false)
  })

  it('starts empty and remains empty when the initial refresh times out', async () => {
    send.mockImplementation((options) => options.fail({ errMsg: 'request:fail timeout' }))
    const { state, isDemo } = await import('@/state')
    const { api } = await import('./api')
    expect(isDemo).toBe(false)
    expect(state.demo).toBe(false)
    expect(state.user).toMatchObject({
      id: '',
      name: '',
      loggedIn: false,
      verification: 'NONE',
      runner: false,
    })
    await expect(api.refresh()).rejects.toMatchObject({ code: 'NETWORK_UNKNOWN' })
    expect(state.networkState).toBe('error')
    expect(state.networkError).not.toBe('')
    for (const list of [
      state.campuses,
      state.products,
      state.errands,
      state.activities,
      state.courses,
      state.conversations,
      state.notices,
      state.favorites,
    ])
      expect(list).toEqual([])
    expect(state.user.name).not.toBe('林同学')
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('shows a configuration error instead of seeds when the real backend URL is absent', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    const { state } = await import('@/state')
    const { api } = await import('./api')
    await expect(api.refresh()).rejects.toMatchObject({ code: 'CONFIG_MISSING' })
    expect(state.networkState).toBe('error')
    expect(state.user.loggedIn).toBe(false)
    expect(state.products).toEqual([])
    expect(send).not.toHaveBeenCalled()
  })

  it('rejects simulated payment and demo reset without modifying live-mode state', async () => {
    const { state } = await import('@/state')
    const { api } = await import('./api')
    const before = JSON.stringify(state)
    await expect(api.simulatePayment('not-a-real-order')).rejects.toThrow('真实模式不支持模拟支付')
    await expect(api.resetDemo()).rejects.toThrow('真实模式不可重置业务数据')
    expect(JSON.stringify(state)).toBe(before)
    expect(send).not.toHaveBeenCalled()
  })

  it('discards a private workspace response that arrives after logout', async () => {
    let workspace: RequestOptions | undefined
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              campuses: [],
              campusId: 'real-campus',
              user: {
                id: 'real-user',
                name: '接口测试用户',
                school: '测试学校',
                campusId: 'real-campus',
                loggedIn: true,
                verification: 'VERIFIED',
                runner: false,
              },
            },
          },
        })
      else if (options.url.endsWith('/me/workspace')) workspace = options
      else if (options.url.endsWith('/auth/logout'))
        options.success({ statusCode: 200, data: { code: 0, data: null } })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    const refresh = api.refresh()
    await vi.waitFor(() => expect(workspace).toBeDefined())
    await api.logout()
    workspace!.success({
      statusCode: 200,
      data: {
        code: 0,
        data: {
          courses: [],
          conversations: [],
          notices: [
            {
              id: 'late-private',
              title: '不应显示',
              text: '旧账号通知',
              time: '刚刚',
              read: false,
              type: 'system',
            },
          ],
          favorites: ['private-favorite'],
        },
      },
    })
    await refresh
    expect(state.user.loggedIn).toBe(false)
    expect(state.user.id).toBe('')
    expect(state.notices).toEqual([])
    expect(state.favorites).toEqual([])
    expect(state.networkState).toBe('ready')
  })
})

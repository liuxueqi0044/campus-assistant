import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface RequestOptions {
  url: string
  method?: string
  data?: unknown
  header?: Record<string, string>
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
  it('refuses to fabricate a WeChat code in H5', async () => {
    const login = vi.fn()
    vi.stubGlobal('uni', {
      request: send,
      login,
      getSystemInfoSync: () => ({ uniPlatform: 'web' }),
    })
    const { api } = await import('./api')
    expect(api.canWechatLogin()).toBe(false)
    await expect(api.login()).rejects.toMatchObject({ code: 'WECHAT_LOGIN_UNAVAILABLE' })
    expect(login).not.toHaveBeenCalled()
    expect(send).not.toHaveBeenCalled()
  })

  it('uses the mini-program code, keeps token material private, and records expiry metadata', async () => {
    const login = vi.fn((options: { success: (result: { code: string }) => void }) =>
      options.success({ code: 'one-time-wechat-code' }),
    )
    vi.stubGlobal('uni', {
      request: send,
      login,
      getSystemInfoSync: () => ({ uniPlatform: 'mp-weixin' }),
    })
    send.mockImplementation((options) => {
      if (options.url.endsWith('/auth/wechat/login'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              accessToken: 'memory-only-access-token',
              expiresAt: '2026-09-18T10:15:30+08:00',
              userId: '9007199254740997',
              profileCompletion: false,
            },
          },
        })
      else if (options.url.endsWith('/context'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              loggedIn: true,
              browseMode: 'PUBLIC',
              school: { schoolId: 'S1', name: '测试学校', status: 'ENABLED' },
              campus: {
                schoolId: 'S1',
                campusId: 'C1',
                name: '东校区',
                status: 'ENABLED',
              },
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
                  schoolId: 'S1',
                  name: '测试学校',
                  status: 'ENABLED',
                  campuses: [{ schoolId: 'S1', campusId: 'C1', name: '东校区', status: 'ENABLED' }],
                },
              ],
            },
          },
        })
      else if (options.url.endsWith('/me/workspace'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: { courses: [], conversations: [], notices: [], favorites: [] },
          },
        })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    expect(api.canWechatLogin()).toBe(true)
    await api.login()
    expect(
      send.mock.calls.find(([request]) => request.url.endsWith('/auth/wechat/login'))?.[0],
    ).toMatchObject({
      method: 'POST',
      data: { code: 'one-time-wechat-code' },
    })
    expect(
      send.mock.calls.find(([request]) => request.url.endsWith('/context'))?.[0].header,
    ).toMatchObject({ Authorization: 'Bearer memory-only-access-token' })
    expect(state.user).toMatchObject({
      id: '9007199254740997',
      loggedIn: true,
      verification: 'NONE',
      runner: false,
    })
    expect(state.sessionExpiresAt).toBe('2026-09-18T10:15:30+08:00')
    expect(state.profileCompletion).toBe(false)
    expect(JSON.stringify(state)).not.toContain('memory-only-access-token')
  })

  it('clears the confirmed local session before reporting an uncertain remote logout', async () => {
    const login = vi.fn((options: { success: (result: { code: string }) => void }) =>
      options.success({ code: 'one-time-code' }),
    )
    vi.stubGlobal('uni', {
      request: send,
      login,
      getSystemInfoSync: () => ({ uniPlatform: 'mp-weixin' }),
    })
    let logoutFails = false
    let localStateAtLogoutRequest: unknown
    let readLocalState = () => undefined as unknown
    send.mockImplementation((options) => {
      if (options.url.endsWith('/auth/wechat/login'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              accessToken: 'logout-test-token',
              expiresAt: '2026-09-18T10:15:30+08:00',
              userId: 'U1',
              profileCompletion: true,
            },
          },
        })
      else if (options.url.endsWith('/auth/logout') && logoutFails) {
        localStateAtLogoutRequest = readLocalState()
        options.fail()
      } else if (options.url.endsWith('/context'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              loggedIn: true,
              school: { schoolId: 'S1', name: '测试学校', status: 'ENABLED' },
              campus: { schoolId: 'S1', campusId: 'C1', name: '东校区', status: 'ENABLED' },
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
                  schoolId: 'S1',
                  name: '测试学校',
                  status: 'ENABLED',
                  campuses: [{ schoolId: 'S1', campusId: 'C1', name: '东校区', status: 'ENABLED' }],
                },
              ],
            },
          },
        })
      else if (options.url.endsWith('/me/workspace'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: { courses: [], conversations: [], notices: [], favorites: [] },
          },
        })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    readLocalState = () => ({
      loggedIn: state.user.loggedIn,
      sessionExpiresAt: state.sessionExpiresAt,
      privateItems: state.courses.length + state.conversations.length + state.notices.length,
    })
    const { api } = await import('./api')
    await api.login()
    logoutFails = true
    await expect(api.logout()).rejects.toMatchObject({
      code: 'LOGOUT_REMOTE_UNKNOWN',
      requiredAction: 'RETRY',
    })
    expect(state.user.loggedIn).toBe(false)
    expect(state.sessionExpiresAt).toBe('')
    expect(state.profileCompletion).toBeNull()
    expect(localStateAtLogoutRequest).toEqual({
      loggedIn: false,
      sessionExpiresAt: '',
      privateItems: 0,
    })
    const logoutRequest = send.mock.calls.find(([request]) =>
      request.url.endsWith('/auth/logout'),
    )?.[0]
    expect(logoutRequest?.header).toMatchObject({ Authorization: 'Bearer logout-test-token' })
  })

  it('does not let a login response restore an account after logout', async () => {
    let pendingLogin: RequestOptions | undefined
    const login = vi.fn((options: { success: (result: { code: string }) => void }) =>
      options.success({ code: 'late-code' }),
    )
    vi.stubGlobal('uni', {
      request: send,
      login,
      getSystemInfoSync: () => ({ uniPlatform: 'mp-weixin' }),
    })
    send.mockImplementation((options) => {
      if (options.url.endsWith('/auth/wechat/login')) pendingLogin = options
      else if (options.url.endsWith('/auth/logout'))
        options.success({ statusCode: 200, data: { code: 0, data: true } })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    const obsolete = api.login().catch((error) => error)
    await vi.waitFor(() => expect(pendingLogin).toBeDefined())
    await api.logout()
    pendingLogin!.success({
      statusCode: 200,
      data: {
        code: 0,
        data: {
          accessToken: 'must-never-be-installed',
          expiresAt: '2026-09-18T10:15:30+08:00',
          userId: 'old-user',
          profileCompletion: true,
        },
      },
    })
    expect(await obsolete).toMatchObject({ code: 'SESSION_CHANGED' })
    expect(state.user.loggedIn).toBe(false)
    expect(state.sessionExpiresAt).toBe('')
    expect(send.mock.calls.some(([request]) => request.url.endsWith('/context'))).toBe(false)
  })

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
    expect(
      send.mock.calls.some(
        ([request]) => request.method === 'PUT' && request.url.endsWith('/context'),
      ),
    ).toBe(true)
    expect(send.mock.calls.find(([request]) => request.method === 'PUT')?.[0]).toMatchObject({
      data: { schoolId: 'S1', campusId: 'C12' },
    })
  })

  it('merges the canonical public directory and chooses its first campus for browsing', async () => {
    const context = { loggedIn: true, browseMode: 'PUBLIC', campus: null }
    const schools = {
      items: [
        {
          schoolId: 'S1',
          name: '目录学校',
          status: 'ENABLED',
          campuses: [
            { campusId: 'C-east', schoolId: 'S1', name: '东校区', status: 'ENABLED' },
            { campusId: 'C-west', schoolId: 'S1', name: '西校区', status: 'ENABLED' },
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
      { id: 'C-east', schoolId: 'S1', enabled: true },
      { id: 'C-west', schoolId: 'S1', enabled: true },
    ])
    // No context selection is persisted by the frontend: this is only the
    // first enabled public browsing default.
    expect(state.campusId).toBe('C-east')
    expect(state.user).toMatchObject({
      loggedIn: true,
      id: '',
      verification: 'NONE',
      runner: false,
    })
    expect(send.mock.calls.some(([request]) => request.method === 'PUT')).toBe(false)
  })

  it('uses server-side school search and opaque cursor pagination without rewriting ids', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: {
            items: [
              {
                schoolId: '9007199254740997',
                name: '很长的测试学校名称',
                status: 'ENABLED',
                campuses: [
                  {
                    schoolId: '9007199254740997',
                    campusId: '9007199254741999',
                    name: '滨江校区',
                    status: 'ENABLED',
                  },
                ],
              },
            ],
            nextCursor: 'next/opaque+=',
          },
        },
      }),
    )
    const { api } = await import('./api')
    await expect(api.listCampuses('  测试 学校  ', 'cursor/opaque+=')).resolves.toEqual({
      items: [
        expect.objectContaining({
          id: '9007199254741999',
          schoolId: '9007199254740997',
          school: '很长的测试学校名称',
        }),
      ],
      nextCursor: 'next/opaque+=',
    })
    expect(send.mock.calls[0][0].url).toBe(
      'https://campus.example.test/app-api/campus/v1/schools?keyword=%E6%B5%8B%E8%AF%95%20%E5%AD%A6%E6%A0%A1&cursor=cursor%2Fopaque%2B%3D',
    )
  })

  it('rejects numeric wire ids instead of treating unsafe JavaScript numbers as authority', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: {
            items: [
              {
                schoolId: 9007199254740996,
                name: '错误 ID 学校',
                campuses: [{ campusId: 'C1', schoolId: 'S1', name: '东校区' }],
              },
            ],
          },
        },
      }),
    )
    const { api } = await import('./api')
    await expect(api.listCampuses()).rejects.toMatchObject({ code: 'CONTRACT_INVALID' })
  })

  it('does not accept legacy ids or missing statuses from the canonical schools endpoint', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: {
            items: [
              {
                id: 'legacy-school',
                name: '错误目录学校',
                campuses: [{ id: 'legacy-campus', schoolId: 'legacy-school', name: '错误校区' }],
              },
            ],
          },
        },
      }),
    )
    const { api } = await import('./api')
    await expect(api.listCampuses()).rejects.toMatchObject({ code: 'CONTRACT_INVALID' })
  })

  it('keeps schools and context usable when undeployed business feeds fail', async () => {
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              loggedIn: false,
              browseMode: 'PUBLIC',
              school: { schoolId: 'S1', name: '测试学校', status: 'ENABLED', campuses: [] },
              campus: {
                campusId: 'C1',
                schoolId: 'S1',
                name: '东校区',
                status: 'ENABLED',
              },
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
                  schoolId: 'S1',
                  name: '测试学校',
                  status: 'ENABLED',
                  campuses: [{ campusId: 'C1', schoolId: 'S1', name: '东校区', status: 'ENABLED' }],
                },
              ],
            },
          },
        })
      else
        options.success({
          statusCode: 404,
          data: { code: 404, message: '接口尚未实现', traceId: 'trace-not-ready' },
        })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    await expect(api.refresh()).resolves.toBeUndefined()
    expect(state.networkState).toBe('ready')
    expect(state.campusId).toBe('C1')
    expect(state.campuses).toHaveLength(1)
    expect(state.contentState).toEqual({ market: 'error', errand: 'error', activity: 'error' })
    expect(state.products).toEqual([])
  })

  it('limits legacy id compatibility to context fallback and keeps it unprivileged', async () => {
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              loggedIn: false,
              school: { id: 'legacy-school', name: '旧学校' },
              campus: {
                id: 'legacy-campus',
                schoolId: 'legacy-school',
                name: '旧校区',
                status: 'ACTIVE',
              },
            },
          },
        })
      else if (options.url.endsWith('/schools'))
        options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
      else options.success({ statusCode: 200, data: { code: 0, data: { items: [] } } })
    })
    const { state } = await import('@/state')
    const { api } = await import('./api')
    await api.refresh()
    expect(state.campuses).toMatchObject([
      { id: 'legacy-campus', schoolId: 'legacy-school', school: '旧学校', enabled: true },
    ])
    expect(state.campusId).toBe('legacy-campus')
    expect(state.user).toMatchObject({
      id: '',
      loggedIn: false,
      verification: 'NONE',
      runner: false,
    })
  })

  it('shows an understandable state and keeps selection empty for an empty directory', async () => {
    send.mockImplementation((options) => {
      if (options.url.endsWith('/context'))
        options.success({ statusCode: 200, data: { code: 0, data: { loggedIn: false } } })
      else if (options.url.endsWith('/schools'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: { items: [] },
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
    expect(send.mock.calls.some(([request]) => request.url.includes('/market/listings'))).toBe(
      false,
    )
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
    expect(send).toHaveBeenCalledTimes(2)
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
              school: { schoolId: 'real-school', name: '测试学校', status: 'ENABLED' },
              campus: {
                schoolId: 'real-school',
                campusId: 'real-campus',
                name: '东校区',
                status: 'ENABLED',
              },
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

  it('serializes rapid context writes so an older response cannot win', async () => {
    let selectedCampus = 'C1'
    const writes: RequestOptions[] = []
    send.mockImplementation((options) => {
      if (options.method === 'PUT' && options.url.endsWith('/context')) {
        writes.push(options)
        return
      }
      if (options.url.endsWith('/context'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              loggedIn: false,
              browseMode: 'PUBLIC',
              school: { schoolId: 'S1', name: '测试学校', status: 'ENABLED' },
              campus: {
                schoolId: 'S1',
                campusId: selectedCampus,
                name: selectedCampus === 'C1' ? '东校区' : '西校区',
                status: 'ENABLED',
              },
            },
          },
        })
      else if (options.url.includes('/schools'))
        options.success({
          statusCode: 200,
          data: {
            code: 0,
            data: {
              items: [
                {
                  schoolId: 'S1',
                  name: '测试学校',
                  status: 'ENABLED',
                  campuses: [
                    { campusId: 'C1', schoolId: 'S1', name: '东校区', status: 'ENABLED' },
                    { campusId: 'C2', schoolId: 'S1', name: '西校区', status: 'ENABLED' },
                  ],
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
    const obsolete = api.switchCampus('C1').catch((error) => error)
    await vi.waitFor(() => expect(writes).toHaveLength(1))
    const latest = api.switchCampus('C2')
    selectedCampus = 'C1'
    writes[0].success({ statusCode: 200, data: { code: 0, data: {} } })
    await vi.waitFor(() => expect(writes).toHaveLength(2))
    selectedCampus = 'C2'
    writes[1].success({ statusCode: 200, data: { code: 0, data: {} } })
    await latest
    expect(await obsolete).toMatchObject({ code: 'CONTEXT_SUPERSEDED' })
    expect(writes.map((request) => request.data)).toEqual([
      { schoolId: 'S1', campusId: 'C1' },
      { schoolId: 'S1', campusId: 'C2' },
    ])
    expect(state.campusId).toBe('C2')
  })

  it('submits the canonical M01.03 verification command with an idempotency key', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: { applicationId: '9007199254740997', status: 'PENDING', version: 1 },
        },
      }),
    )
    const { state } = await import('@/state')
    state.user = {
      id: '101',
      name: '测试用户',
      schoolId: '11',
      school: '测试学校',
      campusId: '12',
      loggedIn: true,
      verification: 'NONE',
      runner: false,
    }
    const { api } = await import('./api')
    await expect(
      api.verify({
        schoolId: '11',
        campusId: '12',
        method: 'STUDENT_CARD',
        evidenceRefs: ['9007199254741999'],
      }),
    ).resolves.toEqual({
      applicationId: '9007199254740997',
      status: 'PENDING',
      version: 1,
    })
    expect(send).toHaveBeenCalledTimes(1)
    expect(send.mock.calls[0][0]).toMatchObject({
      method: 'POST',
      data: {
        schoolId: '11',
        campusId: '12',
        method: 'STUDENT_CARD',
        evidenceRefs: ['9007199254741999'],
      },
      header: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }),
    })
    expect(send.mock.calls[0][0].url).toBe(
      'https://campus.example.test/app-api/campus/v1/verifications',
    )
    expect(state.user.verification).toBe('PENDING')
  })

  it('rejects non-canonical verification ids and public evidence URLs before transport', async () => {
    const { state } = await import('@/state')
    state.user.loggedIn = true
    state.user.id = '101'
    const { api } = await import('./api')
    await expect(
      api.verify({
        schoolId: 'S1',
        campusId: '12',
        method: 'STUDENT_CARD',
        evidenceRefs: ['https://public.example.test/student-card.jpg'],
      }),
    ).rejects.toMatchObject({ code: 'REQUEST_INVALID' })
    expect(send).not.toHaveBeenCalled()
  })

  it('fails closed when the verification response does not contain canonical string ids', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: { applicationId: 9007199254740996, status: 'PENDING', version: 1 },
        },
      }),
    )
    const { state } = await import('@/state')
    state.user.loggedIn = true
    state.user.id = '101'
    const { api } = await import('./api')
    await expect(
      api.verify({
        schoolId: '11',
        campusId: '12',
        method: 'STUDENT_CARD',
        evidenceRefs: ['13'],
      }),
    ).rejects.toMatchObject({ code: 'CONTRACT_INVALID' })
    expect(state.user.verification).toBe('NONE')
  })

  it('uses membership SELF_CHECK only as proof of an effective membership', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: {
            effective: true,
            expiresAt: '2027-09-17T00:00:00+08:00',
            scopeVersion: 7,
          },
        },
      }),
    )
    const { state } = await import('@/state')
    state.user.loggedIn = true
    state.user.id = '101'
    const { api } = await import('./api')
    await expect(api.checkMembership('11', '12')).resolves.toMatchObject({
      effective: true,
      scopeVersion: 7,
    })
    expect(send.mock.calls[0][0]).toMatchObject({ method: 'GET' })
    expect(send.mock.calls[0][0].url).toBe(
      'https://campus.example.test/app-api/campus/v1/membership?schoolId=11&campusId=12',
    )
    expect(state.user.verification).toBe('VERIFIED')
    expect(state.membershipExpiresAt).toBe('2027-09-17T00:00:00+08:00')
    expect(state.membershipScopeVersion).toBe(7)
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

interface RequestOptions {
  url: string
  method: string
  data?: unknown
  timeout: number
  header: Record<string, string>
  success: (response: { statusCode: number; data: unknown }) => void
  fail: (error?: unknown) => void
}
const send = vi.fn((_options: RequestOptions): void => {})

beforeEach(() => {
  vi.resetModules()
  send.mockReset()
  vi.stubEnv('VITE_API_BASE_URL', 'https://campus.example.test/app-api/campus/v1/')
  vi.stubGlobal('uni', { request: send })
})
afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
})

describe('uni.request transport contract', () => {
  it('unwraps a successful response without converting string IDs', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: { code: '0', data: { id: '9007199254740997', amountFen: 580 } },
      }),
    )
    const { request } = await import('./transport')
    await expect(request('GET', '/errands/order-1')).resolves.toEqual({
      id: '9007199254740997',
      amountFen: 580,
    })
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('forwards Bearer and idempotency headers, and removes auth after clearing the token', async () => {
    send.mockImplementation((options) =>
      options.success({ statusCode: 201, data: { code: 0, data: { accepted: true } } }),
    )
    const { request, setToken } = await import('./transport')
    setToken('test-token-not-a-real-credential')
    await request('POST', '/errands', { rewardFen: 500 }, 'stable-command-1')
    expect(send.mock.calls[0][0]).toMatchObject({
      url: 'https://campus.example.test/app-api/campus/v1/errands',
      method: 'POST',
      data: { rewardFen: 500 },
      timeout: 12000,
      header: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer test-token-not-a-real-credential',
        'Idempotency-Key': 'stable-command-1',
      },
    })
    setToken('')
    await request('GET', '/context')
    expect(send.mock.calls[1][0].header).not.toHaveProperty('Authorization')
    expect(send.mock.calls[1][0].header).not.toHaveProperty('Idempotency-Key')
  })

  it('rejects business errors even when HTTP succeeds and preserves trace information', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 200,
        data: { code: 'ALREADY_ACCEPTED', msg: '任务已被其他同学接走', traceId: 'trace-business' },
      }),
    )
    const { request, ApiError } = await import('./transport')
    const error = await request('POST', '/errands/parcel/accept').catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      code: 'ALREADY_ACCEPTED',
      message: '任务已被其他同学接走',
      traceId: 'trace-business',
    })
  })

  it('preserves structured recovery metadata from CampusErrorDetail', async () => {
    send.mockImplementation((options) =>
      options.success({
        statusCode: 409,
        data: {
          code: 1040000010,
          message: '数据已被其他操作修改',
          data: {
            retryable: false,
            requiredAction: 'USER_FIX',
            currentVersion: '7',
            currentState: 'ACTIVE',
          },
          traceId: 'trace-conflict',
        },
      }),
    )
    const { request, ApiError } = await import('./transport')
    const error = await request('PUT', '/context').catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      code: '1040000010',
      requiredAction: 'USER_FIX',
      currentVersion: '7',
      currentState: 'ACTIVE',
      traceId: 'trace-conflict',
    })
  })

  it.each([0, '0'])(
    'rejects a non-2xx response even if its body claims success code %s',
    async (code) => {
      send.mockImplementation((options) =>
        options.success({
          statusCode: 503,
          data: { code, message: '服务暂不可用', traceId: 'trace-http' },
        }),
      )
      const { request, ApiError } = await import('./transport')
      const error = await request('GET', '/context').catch((e) => e)
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({ code: '503', message: '服务暂不可用', traceId: 'trace-http' })
    },
  )

  it.each([
    [401, '登录状态已失效'],
    [403, '当前账号没有执行此操作的权限'],
  ])('keeps HTTP %s distinct when the backend omits a message', async (statusCode, message) => {
    send.mockImplementation((options) =>
      options.success({ statusCode, data: { code: statusCode, traceId: `trace-${statusCode}` } }),
    )
    const { request, ApiError } = await import('./transport')
    const error = await request('GET', '/context').catch((caught) => caught)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      code: String(statusCode),
      httpStatus: statusCode,
      message: expect.stringContaining(message),
    })
  })

  it('classifies a timeout as unknown rather than assuming a submitted command failed', async () => {
    send.mockImplementation((options) => options.fail({ errMsg: 'request:fail timeout' }))
    const { request } = await import('./transport')
    await expect(
      request('POST', '/payments/1/prepay', {}, 'payment-original-key'),
    ).rejects.toMatchObject({ code: 'NETWORK_UNKNOWN' })
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('refuses missing backend configuration before making any network request', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    const { request } = await import('./transport')
    await expect(request('GET', '/context')).rejects.toMatchObject({ code: 'CONFIG_MISSING' })
    expect(send).not.toHaveBeenCalled()
  })

  it('does not treat an unwrapped or malformed HTTP success response as business success', async () => {
    send.mockImplementation((options) => options.success({ statusCode: 200, data: { items: [] } }))
    const { request, ApiError } = await import('./transport')
    await expect(request('GET', '/activities')).rejects.toBeInstanceOf(ApiError)
  })
})

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public traceId?: string,
    public requiredAction?: 'RETRY' | 'USER_FIX' | 'OPERATOR',
    public currentVersion?: string,
    public currentState?: string,
  ) {
    super(message)
  }
}
let accessToken = ''
export function setToken(value: string) {
  accessToken = value
}
const base = String(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
export function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  data?: unknown,
  key?: string,
): Promise<T> {
  if (!base)
    return Promise.reject(
      new ApiError('CONFIG_MISSING', '尚未配置后端地址，请设置 VITE_API_BASE_URL。'),
    )
  return new Promise((resolve, reject) =>
    uni.request({
      url: base + path,
      method,
      data: data as any,
      timeout: 12000,
      header: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...(key ? { 'Idempotency-Key': key } : {}),
      },
      success(res) {
        const body = res.data as {
          code: number | string
          message?: string
          msg?: string
          data?: T
          traceId?: string
        }
        const successCode = body?.code === 0 || body?.code === '0'
        if (res.statusCode >= 200 && res.statusCode < 300 && successCode) resolve(body.data as T)
        else {
          const detail = body?.data as {
            requiredAction?: 'RETRY' | 'USER_FIX' | 'OPERATOR'
            currentVersion?: string
            currentState?: string
          } | undefined
          reject(
            new ApiError(
              String(successCode ? res.statusCode : body?.code || res.statusCode),
              body?.message || body?.msg || '请求未完成，请稍后重试。',
              body?.traceId,
              detail?.requiredAction,
              detail?.currentVersion,
              detail?.currentState,
            ),
          )
        }
      },
      fail() {
        reject(
          new ApiError(
            'NETWORK_UNKNOWN',
            '网络未连接或请求超时；提交结果可能尚未确认，请刷新查询，勿重复付款。',
          ),
        )
      },
    }),
  )
}
let counter = 0
export const commandKey = () =>
  `campus-${Date.now()}-${++counter}-${Math.random().toString(36).slice(2, 10)}`

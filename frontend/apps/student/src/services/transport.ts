export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public traceId?: string,
    public requiredAction?: 'RETRY' | 'USER_FIX' | 'OPERATOR',
    public currentVersion?: string,
    public currentState?: string,
    public httpStatus?: number,
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
  return requestWithBearer(method, path, data, key, accessToken)
}

export function uploadCampusBytes<T>(
  uploadUrl: string,
  data: ArrayBuffer,
  headers: Record<string, string>,
): Promise<T> {
  let target: string
  try {
    target = resolveUploadTarget(uploadUrl, true)
  } catch (error) {
    return Promise.reject(error)
  }
  return new Promise((resolve, reject) =>
    uni.request({
      url: target,
      method: 'PUT',
      data,
      timeout: 30000,
      header: {
        ...headers,
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      success(res) {
        settleCampusResponse<T>(res.statusCode, res.data, resolve, reject)
      },
      fail() {
        reject(networkUnknown('材料字节上传'))
      },
    }),
  )
}

export function uploadSignedBytes(
  uploadUrl: string,
  data: ArrayBuffer,
  headers: Record<string, string>,
): Promise<void> {
  let target: string
  try {
    target = resolveUploadTarget(uploadUrl, false)
  } catch (error) {
    return Promise.reject(error)
  }
  return new Promise((resolve, reject) =>
    uni.request({
      url: target,
      method: 'PUT',
      data,
      timeout: 30000,
      header: headers,
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) resolve()
        else reject(httpError(res.statusCode, res.data))
      },
      fail() {
        reject(networkUnknown('材料字节上传'))
      },
    }),
  )
}

function requestWithBearer<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  data: unknown,
  key: string | undefined,
  bearerToken: string,
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
        ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
        ...(key ? { 'Idempotency-Key': key } : {}),
      },
      success(res) {
        settleCampusResponse<T>(res.statusCode, res.data, resolve, reject)
      },
      fail() {
        reject(networkUnknown('请求'))
      },
    }),
  )
}

function resolveUploadTarget(uploadUrl: string, includeBearer: boolean) {
  if (!base) throw new ApiError('CONFIG_MISSING', '尚未配置后端地址，请设置 VITE_API_BASE_URL。')
  let configured: URL
  let target: URL
  try {
    configured = new URL(base)
    target = new URL(uploadUrl, configured.origin)
  } catch {
    throw new ApiError('CONTRACT_INVALID', '上传地址不符合约定。')
  }
  if (includeBearer && target.origin !== configured.origin)
    throw new ApiError('CONTRACT_INVALID', '受登录保护的上传地址必须与 API 同源。')
  if (!includeBearer && target.protocol !== 'https:' && target.origin !== configured.origin)
    throw new ApiError('CONTRACT_INVALID', '直传地址必须使用 HTTPS。')
  return target.toString()
}

function settleCampusResponse<T>(
  statusCode: number,
  rawBody: unknown,
  resolve: (value: T) => void,
  reject: (reason?: unknown) => void,
) {
  const body = rawBody as {
    code?: number | string
    message?: string
    msg?: string
    data?: T
    traceId?: string
  }
  const successCode = body?.code === 0 || body?.code === '0'
  if (statusCode >= 200 && statusCode < 300 && successCode) resolve(body.data as T)
  else reject(httpError(statusCode, rawBody))
}

function httpError(statusCode: number, rawBody: unknown) {
  const body = rawBody as {
    code?: number | string
    message?: string
    msg?: string
    data?: {
      requiredAction?: 'RETRY' | 'USER_FIX' | 'OPERATOR'
      currentVersion?: string
      currentState?: string
    }
    traceId?: string
  }
  const successCode = body?.code === 0 || body?.code === '0'
  const fallbackMessage =
    statusCode === 401
      ? '登录状态已失效，请重新登录。'
      : statusCode === 403
        ? '当前账号没有执行此操作的权限。'
        : '请求未完成，请稍后重试。'
  return new ApiError(
    String(successCode ? statusCode : body?.code || statusCode),
    body?.message || body?.msg || fallbackMessage,
    body?.traceId,
    body?.data?.requiredAction,
    body?.data?.currentVersion,
    body?.data?.currentState,
    statusCode,
  )
}

function networkUnknown(operation: string) {
  return new ApiError(
    'NETWORK_UNKNOWN',
    `网络未连接或请求超时；${operation}结果可能尚未确认，请先查询状态，不要盲目重试。`,
  )
}

/** Clears the shared token immediately; the returned closure can revoke only that captured token. */
export function prepareLogoutRequest() {
  const tokenToRevoke = accessToken
  accessToken = ''
  return () =>
    requestWithBearer<unknown>('POST', '/auth/logout', undefined, undefined, tokenToRevoke)
}
let counter = 0
export const commandKey = () =>
  `campus-${Date.now()}-${++counter}-${Math.random().toString(36).slice(2, 10)}`

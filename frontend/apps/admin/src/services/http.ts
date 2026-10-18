import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { accessToken, clearAdminSession, refreshToken, saveAdminSession } from '@/utils/auth'
import type { AdminSessionTokens } from '@/utils/auth'

export type RequiredAction = 'RETRY' | 'USER_FIX' | 'OPERATOR'

interface CampusEnvelope<T> {
  code: string | number
  msg?: string
  message?: string
  data: T
  traceId?: string
}

interface RetriableRequest extends InternalAxiosRequestConfig {
  campusRetried?: boolean
}

export class AdminApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number | null,
    public readonly traceId: string,
    public readonly requiredAction: RequiredAction | null,
    public readonly outcomeUnknown = false
  ) {
    super(message)
    this.name = 'AdminApiError'
  }
}

const baseURL = import.meta.env.VITE_ADMIN_API_BASE_URL || '/admin-api'
const http = axios.create({ baseURL, timeout: 15_000, withCredentials: false })
let refreshPromise: Promise<void> | null = null

http.interceptors.request.use((config) => {
  const token = accessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse<CampusEnvelope<unknown>>) => {
    const envelope = response.data
    if (!isRecord(envelope)) {
      throw new AdminApiError('CONTRACT_INVALID', '服务端响应格式无效', response.status, '', null)
    }
    if (envelope.code !== 0 && envelope.code !== 200 && envelope.code !== '0') {
      throw parseAdminErrorEnvelope(envelope, response.status, response.headers['x-trace-id'])
    }
    // Axios fixes interceptor output to AxiosResponse at the type level even when an
    // application unwraps an envelope. adminRequest exposes the actual data type.
    return envelope.data as unknown as AxiosResponse
  },
  async (cause: AxiosError<CampusEnvelope<unknown>>) => {
    const request = cause.config as RetriableRequest | undefined
    if (cause.response?.status === 401 && request && !request.campusRetried && refreshToken()) {
      request.campusRetried = true
      try {
        await refreshSession()
        request.headers.Authorization = `Bearer ${accessToken()}`
        return http.request(request)
      } catch {
        clearAdminSession()
      }
    }
    throw normalizeTransportError(cause)
  }
)

export function adminRequest<T>(config: AxiosRequestConfig): Promise<T> {
  return http.request(config) as unknown as Promise<T>
}

async function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<CampusEnvelope<AdminSessionTokens>>(
        `${baseURL}/campus/v1/session/refresh`,
        { refreshToken: refreshToken() },
        { timeout: 15_000 }
      )
      .then(({ data }) => {
        if ((data.code !== 0 && data.code !== 200 && data.code !== '0') || !validTokens(data.data)) {
          throw new Error('refresh rejected')
        }
        saveAdminSession(data.data)
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

function normalizeTransportError(error: AxiosError<CampusEnvelope<unknown>>) {
  const envelope = error.response?.data
  if (envelope && typeof envelope === 'object') {
    return parseAdminErrorEnvelope(
      envelope,
      error.response?.status || null,
      error.response?.headers['x-trace-id']
    )
  }
  const unknown = !error.response
  return new AdminApiError(
    unknown ? 'RESULT_UNKNOWN' : 'REQUEST_FAILED',
    unknown ? '请求结果未知，请查询原操作后再决定是否重试' : error.message,
    error.response?.status || null,
    '',
    unknown ? 'OPERATOR' : 'RETRY',
    unknown
  )
}

export function parseAdminErrorEnvelope(
  payload: unknown,
  status: number | null = null,
  headerTrace: unknown = ''
) {
  const envelope = isRecord(payload) ? payload : {}
  const detail = isRecord(envelope.data) ? envelope.data : {}
  const requiredAction =
    detail.requiredAction === 'RETRY' ||
    detail.requiredAction === 'USER_FIX' ||
    detail.requiredAction === 'OPERATOR'
      ? detail.requiredAction
      : null
  return new AdminApiError(
    String(detail.code || envelope.code || 'REQUEST_FAILED'),
    String(envelope.msg || envelope.message || detail.message || '请求失败'),
    status,
    String(envelope.traceId || headerTrace || ''),
    requiredAction
  )
}

function validTokens(value: unknown): value is AdminSessionTokens {
  return (
    isRecord(value) &&
    typeof value.userId === 'string' &&
    /^[1-9][0-9]*$/.test(value.userId) &&
    typeof value.accessToken === 'string' &&
    Boolean(value.accessToken) &&
    typeof value.refreshToken === 'string' &&
    Boolean(value.refreshToken) &&
    typeof value.expiresAt === 'string' &&
    !Number.isNaN(Date.parse(value.expiresAt))
  )
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

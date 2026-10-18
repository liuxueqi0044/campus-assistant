const ACCESS_TOKEN_KEY = 'campus-admin-access-token'
const REFRESH_TOKEN_KEY = 'campus-admin-refresh-token'
const USER_ID_KEY = 'campus-admin-user-id'
const EXPIRES_AT_KEY = 'campus-admin-expires-at'

export function accessToken() {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

export function refreshToken() {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

export function hasAdminSession() {
  return Boolean(accessToken())
}

export interface AdminSessionTokens {
  userId: string
  accessToken: string
  refreshToken: string
  expiresAt: string
}

export function saveAdminSession(tokens: AdminSessionTokens) {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, tokens.accessToken)
  sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
  sessionStorage.setItem(USER_ID_KEY, tokens.userId)
  sessionStorage.setItem(EXPIRES_AT_KEY, tokens.expiresAt)
}

export function clearAdminSession() {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
  sessionStorage.removeItem(USER_ID_KEY)
  sessionStorage.removeItem(EXPIRES_AT_KEY)
}

import { adminRequest } from './http'
import { clearAdminSession, saveAdminSession } from '@/utils/auth'
import type { AdminSessionTokens } from '@/utils/auth'

export interface LoginInput {
  username: string
  password: string
  captchaVerification?: string
}

export const adminSessionCapability = Object.freeze({
  available: true,
  reason:
    '校园后台专用会话契约已冻结；真实后端实现与 HTTP 联调尚待完成。客户端不会发送 tenant-id。'
})

export async function loginAdmin(input: LoginInput) {
  const result = await adminRequest<AdminSessionTokens>({
    method: 'POST',
    url: '/campus/v1/session/login',
    data: input
  })
  if (
    !/^[1-9][0-9]*$/.test(result.userId) ||
    !result.accessToken ||
    !result.refreshToken ||
    Number.isNaN(Date.parse(result.expiresAt))
  ) {
    throw new Error('登录响应不符合校园后台会话契约')
  }
  saveAdminSession(result)
}

export async function logoutAdmin() {
  try {
    const result = await adminRequest<boolean>({ method: 'POST', url: '/campus/v1/session/logout' })
    if (result !== true) throw new Error('退出响应不符合校园后台会话契约')
  } finally {
    clearAdminSession()
  }
}

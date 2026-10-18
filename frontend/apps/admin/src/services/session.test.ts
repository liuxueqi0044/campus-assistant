import { describe, expect, it, vi } from 'vitest'

const adminRequest = vi.hoisted(() => vi.fn())
vi.mock('./http', () => ({ adminRequest }))
const saveAdminSession = vi.hoisted(() => vi.fn())
vi.mock('@/utils/auth', () => ({ saveAdminSession, clearAdminSession: vi.fn() }))

import { loginAdmin } from './session'

describe('admin session capability', () => {
  it('uses the platform-bound campus session endpoint without a tenant field', async () => {
    adminRequest.mockResolvedValue({
      userId: '901',
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresAt: '2026-09-18T04:00:00Z'
    })
    await loginAdmin({ username: 'operator', password: 'secret' })
    expect(adminRequest).toHaveBeenCalledWith({
      method: 'POST',
      url: '/campus/v1/session/login',
      data: { username: 'operator', password: 'secret' }
    })
    expect(adminRequest.mock.calls[0][0].data).not.toHaveProperty('tenantId')
    expect(saveAdminSession).toHaveBeenCalledOnce()
  })
})

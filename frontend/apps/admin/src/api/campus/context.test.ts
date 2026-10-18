import { describe, expect, it, vi } from 'vitest'

const adminRequest = vi.hoisted(() => vi.fn())
vi.mock('@/services/http', () => ({ adminRequest }))

import { getAdminCampusContext } from './context'

describe('admin campus context client', () => {
  it('uses the frozen context endpoint without a client tenant or school selector', async () => {
    adminRequest.mockResolvedValue({ operatorId: '101' })
    await getAdminCampusContext()
    expect(adminRequest).toHaveBeenCalledWith({ method: 'GET', url: '/campus/v1/context' })
  })
})

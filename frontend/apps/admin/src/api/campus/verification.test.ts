import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createCampusCommandIntent } from './client'

const adminRequest = vi.hoisted(() => vi.fn())
vi.mock('@/services/http', () => ({ adminRequest }))

import { getVerificationDetail, getVerificationQueue, reviewVerification } from './verification'

describe('verification admin client', () => {
  beforeEach(() => adminRequest.mockReset())

  it('uses the frozen queue query and validates the page', async () => {
    adminRequest.mockResolvedValue({
      list: [
        {
          applicationId: '9007199254740997',
          schoolId: '11',
          campusId: '111',
          method: 'STUDENT_CARD',
          status: 'PENDING',
          version: 1,
          submittedAt: '2026-09-18T04:10:00+08:00'
        }
      ],
      total: 1,
      page: 1,
      pageSize: 20
    })
    await expect(getVerificationQueue({ schoolId: '11' })).resolves.toMatchObject({ total: 1 })
    expect(adminRequest).toHaveBeenCalledWith({
      method: 'GET',
      url: '/campus/v1/verifications',
      params: { schoolId: '11', status: 'PENDING', page: 1, pageSize: 20 }
    })
  })

  it('accepts detail evidence only as asset ids', async () => {
    adminRequest.mockResolvedValue({
      applicationId: '9007199254740997',
      schoolId: '11',
      campusId: '111',
      method: 'STUDENT_CARD',
      status: 'PENDING',
      version: 1,
      submittedAt: '2026-09-18T04:10:00Z',
      evidence: [{ assetId: '7001' }]
    })
    await expect(getVerificationDetail('9007199254740997')).resolves.toMatchObject({
      evidence: [{ assetId: '7001' }]
    })
    expect(adminRequest.mock.calls[0][0]).toEqual({
      method: 'GET',
      url: '/campus/v1/verifications/9007199254740997'
    })
  })

  it('fails closed if detail evidence leaks a storage field', async () => {
    adminRequest.mockResolvedValue({
      applicationId: '9007199254740997',
      schoolId: '11',
      campusId: '111',
      method: 'STUDENT_CARD',
      status: 'PENDING',
      version: 1,
      submittedAt: '2026-09-18T04:10:00Z',
      evidence: [{ assetId: '7001', objectKey: 'private/path' }]
    })
    await expect(getVerificationDetail('9007199254740997')).rejects.toMatchObject({
      code: 'CONTRACT_INVALID'
    })
  })

  it('reuses the caller-owned intent key for retries', async () => {
    adminRequest.mockResolvedValue({
      applicationId: '9007199254740997',
      status: 'APPROVED',
      version: 2,
      membershipId: '9007199254741999',
      scopeVersion: 1
    })
    const intent = createCampusCommandIntent('verification-review')
    const input = { decision: 'APPROVE' as const, reason: '材料与学校范围一致', expectedVersion: 1 }
    await reviewVerification('9007199254740997', input, intent)
    await reviewVerification('9007199254740997', input, intent)
    expect(adminRequest).toHaveBeenCalledTimes(2)
    expect(adminRequest.mock.calls[0][0].headers['Idempotency-Key']).toBe(intent.key)
    expect(adminRequest.mock.calls[1][0].headers['Idempotency-Key']).toBe(intent.key)
  })

  it('rejects an invalid response instead of showing success', async () => {
    adminRequest.mockResolvedValue({
      applicationId: 9007199254740997,
      status: 'APPROVED',
      version: 2,
      membershipId: null,
      scopeVersion: 1
    })
    await expect(
      reviewVerification(
        '9007199254740997',
        { decision: 'APPROVE', reason: '材料一致', expectedVersion: 1 },
        createCampusCommandIntent('verification-review')
      )
    ).rejects.toMatchObject({ code: 'CONTRACT_INVALID' })
  })

  it('rejects a queue page over the frozen maximum', async () => {
    await expect(getVerificationQueue({ schoolId: '11', pageSize: 101 })).rejects.toMatchObject({
      code: 'REQUEST_INVALID'
    })
    expect(adminRequest).not.toHaveBeenCalled()
  })
})

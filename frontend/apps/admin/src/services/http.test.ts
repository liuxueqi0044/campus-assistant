import { describe, expect, it } from 'vitest'
import { parseAdminErrorEnvelope } from './http'

describe('admin HTTP errors', () => {
  it('preserves structured campus error fields for recovery UI', () => {
    const error = parseAdminErrorEnvelope(
      {
        code: 409,
        msg: '申请已被其他人处理',
        traceId: 'trace-body',
        data: { code: 'VERSION_CONFLICT', requiredAction: 'USER_FIX' }
      },
      409,
      'trace-header'
    )
    expect(error).toMatchObject({
      code: 'VERSION_CONFLICT',
      message: '申请已被其他人处理',
      status: 409,
      traceId: 'trace-body',
      requiredAction: 'USER_FIX',
      outcomeUnknown: false
    })
  })
})

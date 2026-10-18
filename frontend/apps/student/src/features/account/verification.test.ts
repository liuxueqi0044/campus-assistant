import { describe, expect, it } from 'vitest'
import { ApiError } from '@/services/transport'
import {
  beginVerificationSubmission,
  beginVerificationUpload,
  canSubmitVerification,
  completeVerificationSubmission,
  createVerificationFlowState,
  failVerificationSubmission,
  retryVerificationFlow,
  selectVerificationMaterials,
  updateVerificationMaterial,
  verificationEvidenceRefs,
} from './verification'

describe('membership verification failure state machine', () => {
  it('fails closed while the media contract is not deployed', () => {
    const state = createVerificationFlowState(false)
    expect(state.phase).toBe('UNAVAILABLE')
    expect(() =>
      selectVerificationMaterials(state, [{ localId: 'private-path', name: '学生证.jpg' }]),
    ).toThrowError(expect.objectContaining({ code: 'MEDIA_UPLOAD_UNAVAILABLE' }))
    expect(state.materials).toEqual([])
    expect(canSubmitVerification(state)).toBe(false)
  })

  it('does not submit an incomplete batch when one of two materials fails', () => {
    const state = createVerificationFlowState(true)
    selectVerificationMaterials(state, [
      { localId: 'front', name: '正面.jpg' },
      { localId: 'back', name: '背面.jpg' },
    ])
    beginVerificationUpload(state)
    updateVerificationMaterial(state, 'front', 'READY', { assetId: '101' })
    updateVerificationMaterial(state, 'back', 'FAILED', {
      errorCode: 'FILE_INVALID',
      errorMessage: '图片内容无法验证。',
    })
    expect(state.phase).toBe('FAILED')
    expect(state.materials[0]).toMatchObject({ status: 'READY', assetId: '101' })
    expect(canSubmitVerification(state)).toBe(false)
    expect(() => verificationEvidenceRefs(state)).toThrowError(
      expect.objectContaining({ code: 'MEDIA_NOT_READY' }),
    )
  })

  it('keeps verified media references for a safe retry after a confirmed rejection', () => {
    const state = readyState()
    beginVerificationSubmission(state)
    failVerificationSubmission(
      state,
      new ApiError('VERIFICATION_CONFLICT', '认证状态已变化。', 'trace-conflict', 'USER_FIX'),
    )
    expect(state).toMatchObject({
      phase: 'FAILED',
      failureStage: 'SUBMIT',
      errorCode: 'VERIFICATION_CONFLICT',
      traceId: 'trace-conflict',
    })
    retryVerificationFlow(state)
    expect(state.phase).toBe('READY')
    expect(verificationEvidenceRefs(state)).toEqual(['101'])
  })

  it('blocks blind resubmission after an unknown network result', () => {
    const state = readyState()
    beginVerificationSubmission(state)
    failVerificationSubmission(state, new ApiError('NETWORK_UNKNOWN', '请求结果未知。'))
    expect(state.phase).toBe('RESULT_UNKNOWN')
    expect(() => retryVerificationFlow(state)).toThrowError(
      expect.objectContaining({ code: 'RESULT_CONFIRMATION_REQUIRED' }),
    )
    expect(state.phase).toBe('RESULT_UNKNOWN')
  })

  it('drops evidence references after the application is accepted', () => {
    const state = readyState()
    beginVerificationSubmission(state)
    completeVerificationSubmission(state, {
      applicationId: '9007199254740997',
      status: 'PENDING',
      version: 1,
    })
    expect(state).toMatchObject({
      phase: 'PENDING',
      applicationId: '9007199254740997',
      applicationVersion: 1,
      applicationStatus: 'PENDING',
    })
    expect(state.materials).toEqual([])
  })
})

function readyState() {
  const state = createVerificationFlowState(true)
  selectVerificationMaterials(state, [{ localId: 'front', name: '正面.jpg' }])
  beginVerificationUpload(state)
  updateVerificationMaterial(state, 'front', 'READY', { assetId: '101' })
  return state
}

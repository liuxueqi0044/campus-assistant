import { describe, expect, it } from 'vitest'
import { ApiError } from '@/services/transport'
import {
  beginVerificationSubmission,
  beginVerificationUpload,
  canSubmitVerification,
  completeVerificationSubmission,
  createVerificationFlowState,
  discardVerificationFlow,
  failVerificationSubmission,
  retryVerificationFlow,
  selectVerificationMaterials,
  updateVerificationMaterial,
  verificationEvidenceRefs,
  verificationResultRequiresConfirmation,
} from './verification'

describe('membership verification failure state machine', () => {
  it('fails closed while the media contract is not deployed', () => {
    const state = createVerificationFlowState(false)
    expect(state.phase).toBe('UNAVAILABLE')
    expect(() =>
      selectVerificationMaterials(state, [selected('private-path', '学生证.jpg')]),
    ).toThrowError(expect.objectContaining({ code: 'MEDIA_UPLOAD_UNAVAILABLE' }))
    expect(state.materials).toEqual([])
    expect(canSubmitVerification(state)).toBe(false)
  })

  it('does not submit an incomplete batch when one of two materials fails', () => {
    const state = createVerificationFlowState(true)
    selectVerificationMaterials(state, [
      selected('front', '正面.jpg'),
      selected('back', '背面.jpg'),
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

  it('keeps verified media references for a safe retry after a confirmed submit failure', () => {
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

    selectVerificationMaterials(state, [selected('replacement', '重新上传的学生证.jpg')])
    expect(state.phase).toBe('SELECTED')
    expect(state.materials).toEqual([
      expect.objectContaining({ localId: 'replacement', status: 'SELECTED' }),
    ])
  })

  it('keeps a completed M02.01 byte upload in VERIFYING until M02.02 confirms it', () => {
    const state = createVerificationFlowState(true)
    selectVerificationMaterials(state, [selected('front', '正面.jpg')])
    beginVerificationUpload(state)
    updateVerificationMaterial(state, 'front', 'VERIFYING', {
      assetId: '101',
      generation: 1,
    })
    expect(state.phase).toBe('VERIFYING')
    expect(state.materials[0]).toMatchObject({
      status: 'VERIFYING',
      assetId: '101',
      generation: 1,
    })
    expect(canSubmitVerification(state)).toBe(false)
    expect(() => verificationEvidenceRefs(state)).toThrowError(
      expect.objectContaining({ code: 'MEDIA_NOT_READY' }),
    )
  })

  it('blocks blind byte re-upload after an unknown upload result', () => {
    const state = createVerificationFlowState(true)
    selectVerificationMaterials(state, [selected('front', '正面.jpg')])
    beginVerificationUpload(state)
    updateVerificationMaterial(state, 'front', 'FAILED', {
      errorCode: 'UPLOAD_RESULT_UNKNOWN',
      errorMessage: '上传结果未知。',
    })
    expect(() => beginVerificationUpload(state)).toThrowError(
      expect.objectContaining({ code: 'RESULT_CONFIRMATION_REQUIRED' }),
    )
    expect(state.materials[0].status).toBe('FAILED')
  })

  it('cannot bypass an unknown byte upload by clearing or replacing materials', () => {
    const state = createVerificationFlowState(true)
    selectVerificationMaterials(state, [selected('front', '正面.jpg')])
    beginVerificationUpload(state)
    updateVerificationMaterial(state, 'front', 'FAILED', {
      errorCode: 'UPLOAD_RESULT_UNKNOWN',
      errorMessage: '上传结果未知。',
    })
    const original = structuredClone(state.materials)

    expect(verificationResultRequiresConfirmation(state)).toBe(true)
    expect(() => discardVerificationFlow(state)).toThrowError(
      expect.objectContaining({ code: 'RESULT_CONFIRMATION_REQUIRED' }),
    )
    expect(() =>
      selectVerificationMaterials(state, [selected('replacement', '另一张.jpg')]),
    ).toThrowError(expect.objectContaining({ code: 'RESULT_CONFIRMATION_REQUIRED' }))
    expect(state.materials).toEqual(original)
  })

  it('cannot bypass an unknown application submission by starting over', () => {
    const state = readyState()
    beginVerificationSubmission(state)
    failVerificationSubmission(
      state,
      new ApiError('NETWORK_UNKNOWN', '请求结果未知。', 'trace-unknown'),
    )
    const original = structuredClone(state.materials)

    expect(verificationResultRequiresConfirmation(state)).toBe(true)
    expect(() => discardVerificationFlow(state)).toThrowError(
      expect.objectContaining({ code: 'RESULT_CONFIRMATION_REQUIRED' }),
    )
    expect(() =>
      selectVerificationMaterials(state, [selected('replacement', '另一张.jpg')]),
    ).toThrowError(expect.objectContaining({ code: 'RESULT_CONFIRMATION_REQUIRED' }))
    expect(state.materials).toEqual(original)
    expect(state.traceId).toBe('trace-unknown')
  })
})

function readyState() {
  const state = createVerificationFlowState(true)
  selectVerificationMaterials(state, [selected('front', '正面.jpg')])
  beginVerificationUpload(state)
  updateVerificationMaterial(state, 'front', 'READY', { assetId: '101' })
  return state
}

function selected(localId: string, name: string) {
  return { localId, name, size: 1024, mime: 'image/jpeg' as const }
}

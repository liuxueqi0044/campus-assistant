import { ApiError } from '@/services/transport'
import type {
  VerificationApplicationDTO,
  VerificationFlowState,
  VerificationMaterial,
  VerificationMaterialStatus,
} from '@/types'

export function createVerificationFlowState(mediaAvailable: boolean): VerificationFlowState {
  return {
    mediaAvailable,
    phase: mediaAvailable ? 'IDLE' : 'UNAVAILABLE',
    materials: [],
    applicationId: '',
    applicationVersion: null,
    applicationStatus: '',
    failureStage: '',
    errorCode: '',
    errorMessage: '',
    traceId: '',
  }
}

export function resetVerificationFlow(
  state: VerificationFlowState,
  mediaAvailable = state.mediaAvailable,
) {
  Object.assign(state, createVerificationFlowState(mediaAvailable))
}

export function setVerificationMediaAvailability(
  state: VerificationFlowState,
  mediaAvailable: boolean,
) {
  resetVerificationFlow(state, mediaAvailable)
}

export function selectVerificationMaterials(
  state: VerificationFlowState,
  materials: Array<Pick<VerificationMaterial, 'localId' | 'name'>>,
) {
  if (!state.mediaAvailable)
    throw new ApiError(
      'MEDIA_UPLOAD_UNAVAILABLE',
      '认证材料上传接口尚未就绪，当前不能提交真实认证。',
      undefined,
      'OPERATOR',
    )
  if (!materials.length || materials.length > 10)
    throw new ApiError('REQUEST_INVALID', '请选择 1–10 份认证材料。', undefined, 'USER_FIX')
  const identifiers = new Set<string>()
  state.materials = materials.map((material) => {
    const localId = material.localId.trim()
    const name = material.name.trim()
    if (!localId || !name || identifiers.has(localId))
      throw new ApiError('REQUEST_INVALID', '认证材料存在缺失或重复项。', undefined, 'USER_FIX')
    identifiers.add(localId)
    return { localId, name, status: 'SELECTED' }
  })
  clearApplication(state)
  clearFailure(state)
  state.phase = 'SELECTED'
}

export function beginVerificationUpload(state: VerificationFlowState) {
  if (!state.mediaAvailable || !state.materials.length)
    throw new ApiError('MEDIA_UPLOAD_UNAVAILABLE', '当前没有可上传的认证材料。')
  clearFailure(state)
  for (const material of state.materials) {
    if (material.status !== 'READY') {
      material.status = 'UPLOADING'
      delete material.assetId
      delete material.errorCode
      delete material.errorMessage
    }
  }
  state.phase = 'UPLOADING'
}

export function updateVerificationMaterial(
  state: VerificationFlowState,
  localId: string,
  status: Exclude<VerificationMaterialStatus, 'SELECTED'>,
  options: { assetId?: string; errorCode?: string; errorMessage?: string } = {},
) {
  const material = state.materials.find((item) => item.localId === localId)
  if (!material) throw new ApiError('MATERIAL_NOT_FOUND', '认证材料已被移除，请重新选择。')
  if (status === 'READY' && !isOpaqueMediaId(options.assetId))
    throw new ApiError('CONTRACT_INVALID', '材料验证完成但没有返回有效的媒体 ID。')
  material.status = status
  material.assetId = status === 'READY' ? options.assetId : undefined
  material.errorCode = status === 'FAILED' ? options.errorCode || 'MEDIA_UPLOAD_FAILED' : undefined
  material.errorMessage =
    status === 'FAILED' ? options.errorMessage || '材料未上传完成，请重试。' : undefined
  recomputeMaterialPhase(state)
}

export function verificationEvidenceRefs(state: VerificationFlowState) {
  if (!canSubmitVerification(state))
    throw new ApiError('MEDIA_NOT_READY', '请等待全部认证材料验证完成。', undefined, 'RETRY')
  return state.materials.map((material) => material.assetId!)
}

export function canSubmitVerification(state: VerificationFlowState) {
  return (
    state.mediaAvailable &&
    state.materials.length > 0 &&
    state.materials.every(
      (material) => material.status === 'READY' && isOpaqueMediaId(material.assetId),
    )
  )
}

export function beginVerificationSubmission(state: VerificationFlowState) {
  verificationEvidenceRefs(state)
  clearFailure(state)
  state.phase = 'SUBMITTING'
}

export function completeVerificationSubmission(
  state: VerificationFlowState,
  application: VerificationApplicationDTO,
) {
  state.applicationId = application.applicationId
  state.applicationVersion = application.version
  state.applicationStatus = application.status
  // Evidence references are no longer needed after the accepted command and
  // must not become a long-lived account cache.
  state.materials = []
  state.phase = 'PENDING'
  clearFailure(state)
}

export function failVerificationSubmission(state: VerificationFlowState, error: unknown) {
  const apiError = error instanceof ApiError ? error : undefined
  state.phase = apiError?.code === 'NETWORK_UNKNOWN' ? 'RESULT_UNKNOWN' : 'FAILED'
  state.failureStage = 'SUBMIT'
  state.errorCode = apiError?.code || 'SUBMIT_FAILED'
  state.errorMessage = apiError?.message || '认证申请未提交成功，已保留验证完成的材料引用供重试。'
  state.traceId = apiError?.traceId || ''
}

export function retryVerificationFlow(state: VerificationFlowState) {
  if (state.phase === 'RESULT_UNKNOWN')
    throw new ApiError(
      'RESULT_CONFIRMATION_REQUIRED',
      '提交结果未知，必须先查询原申请，不能直接再次提交。',
      state.traceId || undefined,
      'OPERATOR',
    )
  clearFailure(state)
  recomputeMaterialPhase(state)
}

function recomputeMaterialPhase(state: VerificationFlowState) {
  if (!state.mediaAvailable) {
    state.phase = 'UNAVAILABLE'
    return
  }
  if (!state.materials.length) {
    state.phase = 'IDLE'
    return
  }
  if (state.materials.some((material) => material.status === 'FAILED')) {
    state.phase = 'FAILED'
    state.failureStage = 'UPLOAD'
    const failed = state.materials.find((material) => material.status === 'FAILED')!
    state.errorCode = failed.errorCode || 'MEDIA_UPLOAD_FAILED'
    state.errorMessage = failed.errorMessage || '部分材料未上传完成，请重试失败项。'
    return
  }
  if (state.materials.some((material) => material.status === 'UPLOADING')) {
    state.phase = 'UPLOADING'
    return
  }
  if (state.materials.some((material) => material.status === 'VERIFYING')) {
    state.phase = 'VERIFYING'
    return
  }
  state.phase = canSubmitVerification(state) ? 'READY' : 'SELECTED'
}

function clearApplication(state: VerificationFlowState) {
  state.applicationId = ''
  state.applicationVersion = null
  state.applicationStatus = ''
}

function clearFailure(state: VerificationFlowState) {
  state.failureStage = ''
  state.errorCode = ''
  state.errorMessage = ''
  state.traceId = ''
}

function isOpaqueMediaId(value: unknown): value is string {
  return (
    typeof value === 'string' && !!value.trim() && value.length <= 128 && !value.includes('://')
  )
}

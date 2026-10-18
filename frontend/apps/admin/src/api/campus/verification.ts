import { adminRequest } from '@/services/http'
import { campusAdminQueriesEnabled } from '@/config/capabilities'
import {
  CampusClientCapabilityError,
  type CampusCommandIntent,
  requireCampusId,
  requireText
} from './client'

export type ReviewDecision = 'APPROVE' | 'REJECT'

export interface ReviewVerificationInput {
  decision: ReviewDecision
  reason: string
  expectedVersion: number
}

export interface VerificationReviewResult {
  applicationId: string
  status: 'APPROVED' | 'REJECTED'
  version: number
  membershipId: string | null
  scopeVersion: number | null
}

export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED'

export interface VerificationQueueQuery {
  schoolId: string
  campusId?: string
  status?: VerificationStatus
  page?: number
  pageSize?: number
}

export interface VerificationQueueItem {
  applicationId: string
  schoolId: string
  campusId: string
  method: string
  status: VerificationStatus
  version: number
  submittedAt: string
}

export interface VerificationQueuePage {
  list: VerificationQueueItem[]
  total: number
  page: number
  pageSize: number
}

export interface VerificationDetail extends VerificationQueueItem {
  evidence: Array<{ assetId: string }>
}

export const verificationQueryCapability = Object.freeze({
  available: campusAdminQueriesEnabled,
  reason: campusAdminQueriesEnabled
    ? '认证队列与详情查询已由构建配置开启。'
    : '认证队列与详情契约已冻结；等待真实后端 B 级 HTTP 证据后再开启。'
})

export async function getVerificationQueue(
  query: VerificationQueueQuery
): Promise<VerificationQueuePage> {
  const schoolId = requireCampusId(query.schoolId, 'schoolId')
  const campusId = query.campusId ? requireCampusId(query.campusId, 'campusId') : undefined
  const status = query.status || 'PENDING'
  if (!isVerificationStatus(status)) requestError('status 不符合认证状态契约')
  const page = positivePage(query.page ?? 1, 'page')
  const pageSize = positivePage(query.pageSize ?? 20, 'pageSize')
  if (pageSize > 100) requestError('pageSize 不能超过 100')
  const result = await adminRequest<unknown>({
    method: 'GET',
    url: '/campus/v1/verifications',
    params: { schoolId, ...(campusId ? { campusId } : {}), status, page, pageSize }
  })
  return normalizeQueuePage(result)
}

export async function getVerificationDetail(applicationId: string): Promise<VerificationDetail> {
  const id = requireCampusId(applicationId, 'applicationId')
  const result = await adminRequest<unknown>({
    method: 'GET',
    url: `/campus/v1/verifications/${encodeURIComponent(id)}`
  })
  const item = normalizeQueueItem(result, ['evidence'])
  if (!isRecord(result) || !Array.isArray(result.evidence)) throw contractError()
  const evidence = result.evidence.map((entry) => {
    if (!isRecord(entry) || Object.keys(entry).some((key) => key !== 'assetId')) {
      throw contractError()
    }
    return { assetId: responseId(entry.assetId) }
  })
  return { ...item, evidence }
}

export async function reviewVerification(
  applicationId: string,
  input: ReviewVerificationInput,
  intent: CampusCommandIntent
): Promise<VerificationReviewResult> {
  const id = requireCampusId(applicationId, 'applicationId')
  if (input.decision !== 'APPROVE' && input.decision !== 'REJECT') {
    throw new CampusClientCapabilityError('REQUEST_INVALID', 'decision 必须为 APPROVE 或 REJECT')
  }
  const reason = requireText(input.reason, 'reason', 500)
  if (!Number.isSafeInteger(input.expectedVersion) || input.expectedVersion < 1) {
    throw new CampusClientCapabilityError(
      'REQUEST_INVALID',
      'expectedVersion 必须为正整数',
      'USER_FIX'
    )
  }
  const key = requireText(intent?.key, 'Idempotency-Key', 256)
  const result = await adminRequest<unknown>({
    method: 'POST',
    url: `/campus/v1/verifications/${encodeURIComponent(id)}/review`,
    data: { decision: input.decision, reason, expectedVersion: input.expectedVersion },
    headers: { 'Idempotency-Key': key }
  })
  return normalizeVerificationReviewResult(result)
}

function normalizeVerificationReviewResult(value: unknown): VerificationReviewResult {
  if (!isRecord(value)) throw contractError()
  const applicationId = responseId(value.applicationId)
  if (value.status !== 'APPROVED' && value.status !== 'REJECTED') throw contractError()
  if (!Number.isSafeInteger(value.version) || (value.version as number) < 2) throw contractError()
  const membershipId = value.membershipId === null ? null : responseId(value.membershipId)
  const scopeVersion = value.scopeVersion
  if (
    scopeVersion !== null &&
    (!Number.isSafeInteger(scopeVersion) || (scopeVersion as number) < 1)
  ) {
    throw contractError()
  }
  return {
    applicationId,
    status: value.status,
    version: value.version as number,
    membershipId,
    scopeVersion: scopeVersion as number | null
  }
}

function normalizeQueuePage(value: unknown): VerificationQueuePage {
  if (!isRecord(value) || !Array.isArray(value.list)) throw contractError()
  const total = nonNegativeInteger(value.total)
  const page = positiveInteger(value.page)
  const pageSize = positiveInteger(value.pageSize)
  if (pageSize > 100) throw contractError()
  return { list: value.list.map((item) => normalizeQueueItem(item)), total, page, pageSize }
}

function normalizeQueueItem(value: unknown, allowedExtra: string[] = []): VerificationQueueItem {
  if (!isRecord(value)) throw contractError()
  const allowed = new Set([
    'applicationId',
    'schoolId',
    'campusId',
    'method',
    'status',
    'version',
    'submittedAt',
    ...allowedExtra
  ])
  if (Object.keys(value).some((key) => !allowed.has(key))) throw contractError()
  const method = typeof value.method === 'string' ? value.method.trim() : ''
  if (!method || method.length > 32 || !isVerificationStatus(value.status)) throw contractError()
  const version = positiveInteger(value.version)
  const submittedAt = rfc3339(value.submittedAt)
  return {
    applicationId: responseId(value.applicationId),
    schoolId: responseId(value.schoolId),
    campusId: responseId(value.campusId),
    method,
    status: value.status,
    version,
    submittedAt
  }
}

function positivePage(value: unknown, field: string) {
  if (!Number.isSafeInteger(value) || (value as number) < 1) requestError(`${field} 必须为正整数`)
  return value as number
}

function positiveInteger(value: unknown) {
  if (!Number.isSafeInteger(value) || (value as number) < 1) throw contractError()
  return value as number
}

function nonNegativeInteger(value: unknown) {
  if (!Number.isSafeInteger(value) || (value as number) < 0) throw contractError()
  return value as number
}

function rfc3339(value: unknown) {
  if (
    typeof value !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value) ||
    Number.isNaN(Date.parse(value))
  ) {
    throw contractError()
  }
  return value
}

function isVerificationStatus(value: unknown): value is VerificationStatus {
  return value === 'PENDING' || value === 'APPROVED' || value === 'REJECTED' || value === 'REVOKED'
}

function requestError(message: string): never {
  throw new CampusClientCapabilityError('REQUEST_INVALID', message, 'USER_FIX')
}

function responseId(value: unknown) {
  try {
    return requireCampusId(value, 'response id')
  } catch {
    throw contractError()
  }
}

function contractError() {
  return new CampusClientCapabilityError('CONTRACT_INVALID', '认证审核接口响应不符合冻结契约')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

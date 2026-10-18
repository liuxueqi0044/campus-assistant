import { ApiError, request, uploadCampusBytes, uploadSignedBytes } from '@/services/transport'
import type { MediaUploadReceipt, VerificationMime, VerificationUploadFile } from '@/types'

const MAX_FILE_BYTES = 10 * 1024 * 1024
const MAX_MATERIALS = 10
const PURPOSE = 'MEMBERSHIP_VERIFICATION' as const

interface UploadTicketDTO {
  assetId: string
  generation: number
  uploadMethod: 'PROXY_PUT' | 'SIGNED_PUT'
  uploadUrl: string
  expiresAt: string
  requiredHeaders: Record<string, string>
}

interface MediaUploadDependencies {
  requestTicket: (input: UploadTicketInput, key: string) => Promise<unknown>
  readBytes: (localId: string) => Promise<ArrayBuffer>
  putCampus: (
    uploadUrl: string,
    bytes: ArrayBuffer,
    headers: Record<string, string>,
  ) => Promise<unknown>
  putSigned: (
    uploadUrl: string,
    bytes: ArrayBuffer,
    headers: Record<string, string>,
  ) => Promise<void>
  completeUpload: (assetId: string, generation: number) => Promise<unknown>
  now: () => number
}

interface UploadReceiptDTO {
  assetId: string
  generation: number
  bytesWritten: number
  status: 'UPLOADING'
}

interface UploadTicketInput {
  purpose: typeof PURPOSE
  fileName: string
  size: number
  mime: VerificationMime
}

export type VerificationFileSelection = Omit<VerificationUploadFile, 'ticketKey'>

const defaultDependencies: MediaUploadDependencies = {
  requestTicket: (input, key) => request('POST', '/media/upload-tickets', input, key),
  readBytes: readLocalFile,
  putCampus: uploadCampusBytes,
  putSigned: uploadSignedBytes,
  completeUpload: (assetId, generation) =>
    request('POST', `/media/assets/${encodeURIComponent(assetId)}/complete`, { generation }),
  now: Date.now,
}

export async function chooseVerificationImages(
  count = MAX_MATERIALS,
): Promise<VerificationFileSelection[]> {
  if (!Number.isSafeInteger(count) || count < 1 || count > MAX_MATERIALS)
    throw new ApiError('REQUEST_INVALID', '认证材料数量必须为 1–10。', undefined, 'USER_FIX')
  return new Promise((resolve, reject) =>
    uni.chooseImage({
      count,
      sizeType: ['original'],
      sourceType: ['album', 'camera'],
      success(result) {
        try {
          const rawFiles = Array.isArray(result.tempFiles)
            ? result.tempFiles
            : result.tempFiles
              ? [result.tempFiles]
              : []
          const files = rawFiles.map((file, index) =>
            normalizeSelectedImage(file, result.tempFilePaths[index], index),
          )
          resolve(files)
        } catch (error) {
          reject(error)
        }
      },
      fail(error) {
        if (
          String(error?.errMsg || '')
            .toLowerCase()
            .includes('cancel')
        )
          resolve([])
        else reject(new ApiError('FILE_SELECTION_FAILED', '未能读取所选材料，请重试。'))
      },
    }),
  )
}

export async function uploadVerificationFile(
  input: VerificationUploadFile,
  assertCurrent: () => void = () => undefined,
  overrides: Partial<MediaUploadDependencies> = {},
): Promise<MediaUploadReceipt> {
  const dependencies = { ...defaultDependencies, ...overrides }
  const file = normalizeUploadFile(input)
  let uploadStarted = false
  try {
    const ticket = normalizeTicket(
      await dependencies.requestTicket(
        {
          purpose: PURPOSE,
          fileName: file.name,
          size: file.size,
          mime: file.mime,
        },
        file.ticketKey,
      ),
      dependencies.now(),
    )
    assertCurrent()
    const bytes = await dependencies.readBytes(file.localId)
    assertCurrent()
    if (!(bytes instanceof ArrayBuffer) || bytes.byteLength !== file.size)
      throw new ApiError(
        'FILE_SIZE_CHANGED',
        '所选材料已变化或无法完整读取，请重新选择。',
        undefined,
        'USER_FIX',
      )
    let receipt: UploadReceiptDTO
    if (ticket.uploadMethod === 'PROXY_PUT') {
      requireProxyTicket(ticket, file.mime)
      uploadStarted = true
      const raw = await dependencies.putCampus(ticket.uploadUrl, bytes, ticket.requiredHeaders)
      receipt = normalizeUploadReceipt(raw)
    } else {
      uploadStarted = true
      await dependencies.putSigned(ticket.uploadUrl, bytes, ticket.requiredHeaders)
      receipt = {
        assetId: ticket.assetId,
        generation: ticket.generation,
        bytesWritten: file.size,
        status: 'UPLOADING',
      }
    }
    assertCurrent()
    if (
      receipt.assetId !== ticket.assetId ||
      receipt.generation !== ticket.generation ||
      receipt.bytesWritten !== file.size
    )
      throw new ApiError('CONTRACT_INVALID', '上传回执与票据不一致。')
    const completion = normalizeCompletion(
      await dependencies.completeUpload(ticket.assetId, ticket.generation),
      ticket,
    )
    assertCurrent()
    if (completion.status === 'REJECTED')
      throw new ApiError('FILE_INVALID', '材料内容与声明格式不一致，请重新选择。', undefined, 'USER_FIX')
    if (completion.status !== 'VALID')
      throw new ApiError('MEDIA_NOT_READY', '材料仍在验证中，请稍后重试。', undefined, 'RETRY')
    return { ...receipt, status: 'VALID', assetVersion: completion.assetVersion }
  } catch (error) {
    if (uploadStarted && isIndeterminateUploadFailure(error)) {
      const cause = error instanceof ApiError ? error : undefined
      throw new ApiError(
        'UPLOAD_RESULT_UNKNOWN',
        '材料字节上传结果无法确认；完成查询接口就绪前请勿重复上传该文件。',
        cause?.traceId,
        'OPERATOR',
        cause?.currentVersion,
        cause?.currentState,
        cause?.httpStatus,
      )
    }
    throw error
  }
}

function normalizeCompletion(value: unknown, ticket: UploadTicketDTO) {
  if (!isRecord(value)) throw contractError('验证结果')
  const assetId = positiveId(value.assetId, '验证结果 assetId')
  if (
    assetId !== ticket.assetId ||
    value.generation !== ticket.generation ||
    (value.status !== 'VALIDATING' && value.status !== 'VALID' && value.status !== 'REJECTED') ||
    !Number.isSafeInteger(value.assetVersion) ||
    (value.assetVersion as number) < 1
  )
    throw contractError('验证结果')
  return {
    assetId,
    generation: value.generation as number,
    status: value.status,
    assetVersion: value.assetVersion as number,
  }
}

function normalizeSelectedImage(
  raw: unknown,
  fallbackPath: string,
  index: number,
): VerificationFileSelection {
  const candidate = raw as { path?: string; name?: string; type?: string; size?: number }
  const localId = String(candidate.path || fallbackPath || '').trim()
  const mime = inferImageMime(candidate.type, candidate.name || localId)
  const name = safeFileName(candidate.name || localId, mime, index)
  const size = Number(candidate.size)
  return normalizeSelection({ localId, name, size, mime })
}

function normalizeSelection(input: VerificationFileSelection): VerificationFileSelection {
  if (!input.localId.trim())
    throw new ApiError('REQUEST_INVALID', '材料临时路径为空。', undefined, 'USER_FIX')
  if (!input.name.trim() || input.name.length > 255)
    throw new ApiError('REQUEST_INVALID', '材料文件名不符合要求。', undefined, 'USER_FIX')
  if (!Number.isSafeInteger(input.size) || input.size < 1 || input.size > MAX_FILE_BYTES)
    throw new ApiError(
      'FILE_SIZE_EXCEEDED',
      '单份材料必须小于或等于 10 MB。',
      undefined,
      'USER_FIX',
    )
  if (!isVerificationMime(input.mime))
    throw new ApiError(
      'UPLOAD_PURPOSE_DENIED',
      '仅支持 JPG、PNG 或 PDF 材料。',
      undefined,
      'USER_FIX',
    )
  return {
    localId: input.localId.trim(),
    name: input.name.trim(),
    size: input.size,
    mime: input.mime,
  }
}

function normalizeUploadFile(input: VerificationUploadFile): VerificationUploadFile {
  const selection = normalizeSelection(input)
  const ticketKey = input.ticketKey?.trim()
  if (!ticketKey || ticketKey.length > 256)
    throw new ApiError('REQUEST_INVALID', '上传幂等键不符合要求。')
  return { ...selection, ticketKey }
}

function normalizeTicket(value: unknown, now: number): UploadTicketDTO {
  if (!isRecord(value)) throw contractError('上传票据')
  const assetId = positiveId(value.assetId, 'assetId')
  if (!Number.isSafeInteger(value.generation) || (value.generation as number) < 1)
    throw contractError('上传票据 generation')
  if (value.uploadMethod !== 'PROXY_PUT' && value.uploadMethod !== 'SIGNED_PUT')
    throw contractError('上传方式')
  if (
    typeof value.uploadUrl !== 'string' ||
    !value.uploadUrl.trim() ||
    value.uploadUrl.length > 4096
  )
    throw contractError('上传地址')
  if (typeof value.expiresAt !== 'string') throw contractError('票据有效期')
  const expiresAt = Date.parse(value.expiresAt)
  if (!Number.isFinite(expiresAt) || expiresAt <= now) throw contractError('票据有效期')
  return {
    assetId,
    generation: value.generation as number,
    uploadMethod: value.uploadMethod,
    uploadUrl: value.uploadUrl,
    expiresAt: value.expiresAt,
    requiredHeaders: normalizeRequiredHeaders(value.requiredHeaders),
  }
}

function normalizeUploadReceipt(value: unknown): UploadReceiptDTO {
  if (!isRecord(value)) throw contractError('上传回执')
  if (
    !Number.isSafeInteger(value.generation) ||
    (value.generation as number) < 1 ||
    !Number.isSafeInteger(value.bytesWritten) ||
    (value.bytesWritten as number) < 1 ||
    value.status !== 'UPLOADING'
  )
    throw contractError('上传回执')
  return {
    assetId: positiveId(value.assetId, 'assetId'),
    generation: value.generation as number,
    bytesWritten: value.bytesWritten as number,
    status: value.status,
  }
}

function normalizeRequiredHeaders(value: unknown) {
  if (!isRecord(value) || Object.keys(value).length > 32) throw contractError('上传请求头')
  const headers: Record<string, string> = {}
  for (const [name, headerValue] of Object.entries(value)) {
    if (
      !/^[A-Za-z0-9-]{1,100}$/.test(name) ||
      typeof headerValue !== 'string' ||
      !headerValue ||
      headerValue.length > 4096 ||
      /[\r\n]/.test(headerValue)
    )
      throw contractError('上传请求头')
    headers[name] = headerValue
  }
  return headers
}

function requireProxyTicket(ticket: UploadTicketDTO, mime: VerificationMime) {
  let target: URL
  try {
    target = new URL(ticket.uploadUrl, 'https://campus.invalid')
  } catch {
    throw contractError('上传地址')
  }
  const expectedPath = `/app-api/campus/v1/media/assets/${ticket.assetId}/content`
  if (target.pathname !== expectedPath || target.search || target.hash)
    throw contractError('受保护上传地址')
  const uploadToken = header(ticket.requiredHeaders, 'x-upload-token')
  const contentType = header(ticket.requiredHeaders, 'content-type')
  if (!uploadToken || contentType !== mime) throw contractError('受保护上传请求头')
}

function header(headers: Record<string, string>, name: string) {
  return Object.entries(headers).find(([key]) => key.toLowerCase() === name)?.[1]
}

function inferImageMime(type: string | undefined, name: string): VerificationMime {
  const normalized = String(type || '').toLowerCase()
  if (normalized === 'image/jpeg' || normalized === 'image/jpg') return 'image/jpeg'
  if (normalized === 'image/png') return 'image/png'
  const cleanName = name.split(/[?#]/)[0].toLowerCase()
  if (/\.jpe?g$/.test(cleanName)) return 'image/jpeg'
  if (/\.png$/.test(cleanName)) return 'image/png'
  throw new ApiError(
    'UPLOAD_PURPOSE_DENIED',
    '无法确认图片类型，请选择 JPG 或 PNG。',
    undefined,
    'USER_FIX',
  )
}

function safeFileName(source: string, mime: VerificationMime, index: number) {
  const clean = source.split(/[?#]/)[0]
  const candidate = clean.split(/[\\/]/).pop()?.trim()
  const extension = mime === 'image/png' ? '.png' : mime === 'application/pdf' ? '.pdf' : '.jpg'
  const name =
    candidate && /\.[A-Za-z0-9]{1,8}$/.test(candidate)
      ? candidate
      : `学生证-${index + 1}${extension}`
  return name.length <= 255 ? name : `${name.slice(0, 255 - extension.length)}${extension}`
}

async function readLocalFile(localId: string): Promise<ArrayBuffer> {
  if (/^(blob:|data:)/i.test(localId) && typeof fetch === 'function') {
    const response = await fetch(localId)
    if (!response.ok) throw new ApiError('FILE_READ_FAILED', '无法读取所选材料，请重新选择。')
    return response.arrayBuffer()
  }
  const getManager = (
    uni as unknown as {
      getFileSystemManager?: () => {
        readFile: (options: {
          filePath: string
          success: (result: { data: string | ArrayBuffer }) => void
          fail: () => void
        }) => void
      }
    }
  ).getFileSystemManager
  if (!getManager) throw new ApiError('FILE_READ_UNAVAILABLE', '当前运行环境不支持读取认证材料。')
  return new Promise((resolve, reject) =>
    getManager().readFile({
      filePath: localId,
      success(result) {
        if (result.data instanceof ArrayBuffer) resolve(result.data)
        else reject(new ApiError('FILE_READ_FAILED', '材料读取格式不正确，请重新选择。'))
      },
      fail() {
        reject(new ApiError('FILE_READ_FAILED', '无法读取所选材料，请重新选择。'))
      },
    }),
  )
}

function isIndeterminateUploadFailure(error: unknown) {
  if (!(error instanceof ApiError)) return true
  if (error.code === 'SESSION_CHANGED' || error.code === 'SCOPE_CHANGED') return false
  return (
    error.code === 'NETWORK_UNKNOWN' ||
    error.code === 'CONTRACT_INVALID' ||
    error.httpStatus === 409 ||
    (error.httpStatus !== undefined && error.httpStatus >= 500)
  )
}

function isVerificationMime(value: unknown): value is VerificationMime {
  return value === 'image/jpeg' || value === 'image/png' || value === 'application/pdf'
}

function positiveId(value: unknown, field: string) {
  if (typeof value !== 'string' || !/^[1-9][0-9]{0,18}$/.test(value)) throw contractError(field)
  return value
}

function contractError(field: string) {
  return new ApiError('CONTRACT_INVALID', `${field}不符合冻结契约。`)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

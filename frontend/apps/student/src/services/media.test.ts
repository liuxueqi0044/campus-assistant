import { describe, expect, it, vi } from 'vitest'
import { ApiError } from './transport'
import { uploadVerificationFile } from './media'
import type { VerificationUploadFile } from '@/types'

describe('membership verification media upload', () => {
  it('issues an idempotent purpose-bound ticket and validates the proxy receipt', async () => {
    const requestTicket = vi.fn().mockResolvedValue(ticket())
    const readBytes = vi.fn().mockResolvedValue(new Uint8Array([1, 2, 3]).buffer)
    const putCampus = vi.fn().mockResolvedValue({
      assetId: '101',
      generation: 1,
      bytesWritten: 3,
      status: 'UPLOADING',
    })
    const assertCurrent = vi.fn()
    const completeUpload = vi.fn().mockResolvedValue({
      assetId: '101',
      generation: 1,
      status: 'VALID',
      assetVersion: 3,
    })
    const result = await uploadVerificationFile(file(), assertCurrent, {
      requestTicket,
      readBytes,
      putCampus,
      completeUpload,
      now: () => Date.parse('2026-09-17T00:00:00Z'),
    })

    expect(requestTicket).toHaveBeenCalledWith(
      {
        purpose: 'MEMBERSHIP_VERIFICATION',
        fileName: '学生证.jpg',
        size: 3,
        mime: 'image/jpeg',
      },
      'stable-upload-key',
    )
    expect(putCampus).toHaveBeenCalledWith(
      '/app-api/campus/v1/media/assets/101/content',
      expect.any(ArrayBuffer),
      { 'Content-Type': 'image/jpeg', 'X-Upload-Token': 'temporary-secret' },
    )
    expect(result).toEqual({
      assetId: '101',
      generation: 1,
      bytesWritten: 3,
      status: 'VALID',
      assetVersion: 3,
    })
    expect(completeUpload).toHaveBeenCalledWith('101', 1)
    expect(result).not.toHaveProperty('uploadUrl')
    expect(result).not.toHaveProperty('requiredHeaders')
    expect(assertCurrent).toHaveBeenCalledTimes(4)
  })

  it('fails closed before reading bytes when the ticket has a numeric asset id', async () => {
    const readBytes = vi.fn()
    await expect(
      uploadVerificationFile(file(), undefined, {
        requestTicket: vi.fn().mockResolvedValue({ ...ticket(), assetId: 101 }),
        readBytes,
        now: () => Date.parse('2026-09-17T00:00:00Z'),
      }),
    ).rejects.toMatchObject({ code: 'CONTRACT_INVALID' })
    expect(readBytes).not.toHaveBeenCalled()
  })

  it('does not blind-retry when a byte upload has an unknown result', async () => {
    await expect(
      uploadVerificationFile(file(), undefined, {
        requestTicket: vi.fn().mockResolvedValue(ticket()),
        readBytes: vi.fn().mockResolvedValue(new Uint8Array([1, 2, 3]).buffer),
        putCampus: vi.fn().mockRejectedValue(new ApiError('NETWORK_UNKNOWN', 'timeout')),
        now: () => Date.parse('2026-09-17T00:00:00Z'),
      }),
    ).rejects.toMatchObject({
      code: 'UPLOAD_RESULT_UNKNOWN',
      requiredAction: 'OPERATOR',
    })
  })

  it('uses the non-bearer signed upload path without retaining temporary headers', async () => {
    const putSigned = vi.fn().mockResolvedValue(undefined)
    const result = await uploadVerificationFile(file(), undefined, {
      requestTicket: vi.fn().mockResolvedValue({
        ...ticket(),
        uploadMethod: 'SIGNED_PUT',
        uploadUrl: 'https://private-upload.example.test/object?signature=temporary',
        requiredHeaders: { 'Content-Type': 'image/jpeg', 'X-Signed-Header': 'temporary' },
      }),
      readBytes: vi.fn().mockResolvedValue(new Uint8Array([1, 2, 3]).buffer),
      putSigned,
      completeUpload: vi.fn().mockResolvedValue({
        assetId: '101',
        generation: 1,
        status: 'VALID',
        assetVersion: 3,
      }),
      now: () => Date.parse('2026-09-17T00:00:00Z'),
    })
    expect(putSigned).toHaveBeenCalledTimes(1)
    expect(result).toEqual({
      assetId: '101',
      generation: 1,
      bytesWritten: 3,
      status: 'VALID',
      assetVersion: 3,
    })
    expect(JSON.stringify(result)).not.toContain('temporary')
  })

  it('fails closed when server-side validation rejects the uploaded bytes', async () => {
    await expect(
      uploadVerificationFile(file(), undefined, {
        requestTicket: vi.fn().mockResolvedValue(ticket()),
        readBytes: vi.fn().mockResolvedValue(new Uint8Array([1, 2, 3]).buffer),
        putCampus: vi.fn().mockResolvedValue({
          assetId: '101',
          generation: 1,
          bytesWritten: 3,
          status: 'UPLOADING',
        }),
        completeUpload: vi.fn().mockResolvedValue({
          assetId: '101',
          generation: 1,
          status: 'REJECTED',
          assetVersion: 3,
        }),
        now: () => Date.parse('2026-09-17T00:00:00Z'),
      }),
    ).rejects.toMatchObject({ code: 'FILE_INVALID', requiredAction: 'USER_FIX' })
  })
})

function file(): VerificationUploadFile {
  return {
    localId: 'wxfile://student-card.jpg',
    name: '学生证.jpg',
    size: 3,
    mime: 'image/jpeg',
    ticketKey: 'stable-upload-key',
  }
}

function ticket() {
  return {
    assetId: '101',
    generation: 1,
    uploadMethod: 'PROXY_PUT' as const,
    uploadUrl: '/app-api/campus/v1/media/assets/101/content',
    expiresAt: '2026-09-17T00:10:00Z',
    requiredHeaders: {
      'Content-Type': 'image/jpeg',
      'X-Upload-Token': 'temporary-secret',
    },
  }
}

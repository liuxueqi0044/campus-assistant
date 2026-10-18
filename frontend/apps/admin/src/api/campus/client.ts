export class CampusClientCapabilityError extends Error {
  constructor(
    public readonly code: 'CAPABILITY_NOT_READY' | 'REQUEST_INVALID' | 'CONTRACT_INVALID',
    message: string,
    public readonly requiredAction: 'OPERATOR' | 'USER_FIX' = 'OPERATOR'
  ) {
    super(message)
    this.name = 'CampusClientCapabilityError'
  }
}

export interface CampusCommandIntent {
  readonly key: string
  readonly createdAt: number
}

export function createCampusCommandIntent(prefix: string): CampusCommandIntent {
  const suffix = globalThis.crypto?.randomUUID?.() || randomFallback()
  return Object.freeze({ key: `${prefix}-${suffix}`, createdAt: Date.now() })
}

export function requireCampusId(value: unknown, field: string) {
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) {
    throw new CampusClientCapabilityError(
      'REQUEST_INVALID',
      `${field} 必须是十进制正整数字符串`,
      'USER_FIX'
    )
  }
  return value
}

export function requireText(value: unknown, field: string, maxLength: number) {
  if (typeof value !== 'string' || !value.trim() || value.trim().length > maxLength) {
    throw new CampusClientCapabilityError(
      'REQUEST_INVALID',
      `${field} 必须为 1–${maxLength} 个字符`,
      'USER_FIX'
    )
  }
  return value.trim()
}

function randomFallback() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random()
    .toString(36)
    .slice(2)}`
}

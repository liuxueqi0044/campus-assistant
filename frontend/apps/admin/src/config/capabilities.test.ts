import { describe, expect, it } from 'vitest'
import { campusAdminQueriesEnabled, isCampusAdminQueriesEnabled } from './capabilities'

describe('admin query capability gate', () => {
  it('only accepts the explicit build-time true value', () => {
    expect(isCampusAdminQueriesEnabled('true')).toBe(true)
    for (const value of [undefined, null, '', 'TRUE', '1', true, 1]) {
      expect(isCampusAdminQueriesEnabled(value)).toBe(false)
    }
  })

  it('keeps campus queries disabled when no opt-in environment is supplied', () => {
    expect(campusAdminQueriesEnabled).toBe(false)
  })
})

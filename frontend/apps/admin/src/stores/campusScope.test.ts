import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCampusScopeStore } from './campusScope'
import type { AdminCampusContext } from '@/api/campus/context'

describe('campus scope store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('fails closed while the context contract is unavailable', async () => {
    const scope = useCampusScopeStore()
    await scope.loadContext()
    expect(scope).toMatchObject({
      status: 'unavailable',
      errorCode: 'CAPABILITY_NOT_READY',
      schools: [],
      permissions: []
    })
  })

  it('invalidates captured list and detail selections after a school switch', () => {
    const scope = readyScope()
    const selection = scope.captureSelection()
    scope.selectSchool('22')
    expect(scope.isCurrent(selection)).toBe(false)
    expect(scope.selectedSchoolId).toBe('22')
  })

  it('uses the union of school and selected-campus actions', () => {
    const scope = readyScope()
    expect(scope.canReviewVerification).toBe(true)
    scope.selectSchool('22')
    expect(scope.canReviewVerification).toBe(false)
    scope.selectCampus('222')
    expect(scope.canReviewVerification).toBe(true)
  })

  it('classifies malformed server context as a contract error', () => {
    const scope = useCampusScopeStore()
    const generation = scope.beginLoad()
    expect(() =>
      scope.applyContext({ ...context(), operatorId: 'bad' }, generation)
    ).toThrowError(expect.objectContaining({ code: 'CONTRACT_INVALID' }))
  })

  it('rejects schools outside the returned authorization', () => {
    const scope = readyScope()
    expect(() => scope.selectSchool('33')).toThrowError(
      expect.objectContaining({ code: 'REQUEST_INVALID' })
    )
  })
})

function readyScope() {
  const scope = useCampusScopeStore()
  const generation = scope.beginLoad()
  scope.applyContext(context(), generation)
  return scope
}

function context(): AdminCampusContext {
  return {
    operatorId: '101',
    authorizationVersion: 7,
    permissions: ['campus:verification:review'],
    menus: ['campus-verification-review'],
    schools: [
      {
        schoolId: '11',
        name: '第一大学',
        campuses: [{ campusId: '111', name: '东校区', actions: [] }],
        actions: ['VERIFICATION_REVIEW']
      },
      {
        schoolId: '22',
        name: '第二大学',
        campuses: [{ campusId: '222', name: '本部', actions: ['VERIFICATION_REVIEW'] }],
        actions: []
      }
    ]
  }
}

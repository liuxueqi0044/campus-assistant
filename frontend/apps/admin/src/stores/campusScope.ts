import { defineStore } from 'pinia'
import {
  getAdminCampusContext,
  campusContextCapability,
  type AdminCampusContext,
  type AdminSchoolScope
} from '@/api/campus/context'
import { CampusClientCapabilityError, requireCampusId } from '@/api/campus/client'

export type CampusScopeStatus = 'idle' | 'loading' | 'ready' | 'unavailable' | 'error'

interface CampusScopeState {
  status: CampusScopeStatus
  operatorId: string
  authorizationVersion: number | null
  permissions: string[]
  schools: AdminSchoolScope[]
  selectedSchoolId: string
  selectedCampusId: string
  generation: number
  errorCode: string
  errorMessage: string
}

export const useCampusScopeStore = defineStore('campus-scope', {
  state: (): CampusScopeState => ({
    status: 'idle',
    operatorId: '',
    authorizationVersion: null,
    permissions: [],
    schools: [],
    selectedSchoolId: '',
    selectedCampusId: '',
    generation: 0,
    errorCode: '',
    errorMessage: ''
  }),
  getters: {
    selectedSchool: (state) =>
      state.schools.find((school) => school.schoolId === state.selectedSchoolId),
    selectedCampus(state) {
      const school = state.schools.find((item) => item.schoolId === state.selectedSchoolId)
      return school?.campuses.find((campus) => campus.campusId === state.selectedCampusId)
    },
    effectiveActions(): string[] {
      return [...new Set([...(this.selectedSchool?.actions || []), ...(this.selectedCampus?.actions || [])])]
    },
    canReviewVerification(state): boolean {
      return (
        state.status === 'ready' &&
        state.permissions.includes('campus:verification:review') &&
        this.effectiveActions.includes('VERIFICATION_REVIEW')
      )
    }
  },
  actions: {
    beginLoad() {
      this.generation += 1
      this.status = 'loading' as const
      this.errorCode = ''
      this.errorMessage = ''
      return this.generation
    },
    async loadContext() {
      const generation = this.beginLoad()
      try {
        if (!campusContextCapability.available) {
          throw new CampusClientCapabilityError(
            'CAPABILITY_NOT_READY',
            campusContextCapability.reason
          )
        }
        this.applyContext(await getAdminCampusContext(), generation)
      } catch (cause) {
        if (generation !== this.generation) return
        this.clearAuthority()
        if (cause instanceof CampusClientCapabilityError && cause.code === 'CAPABILITY_NOT_READY') {
          this.status = 'unavailable'
          this.errorCode = cause.code
          this.errorMessage = cause.message
        } else {
          this.status = 'error'
          this.errorCode = cause instanceof CampusClientCapabilityError ? cause.code : 'CONTEXT_LOAD_FAILED'
          this.errorMessage = cause instanceof Error ? cause.message : '学校权限上下文加载失败'
        }
      }
    },
    applyContext(context: AdminCampusContext, generation: number) {
      if (generation !== this.generation) return false
      const normalized = normalizeContext(context)
      this.operatorId = normalized.operatorId
      this.authorizationVersion = normalized.authorizationVersion
      this.permissions = normalized.permissions
      this.schools = normalized.schools
      this.selectedSchoolId = normalized.schools[0]?.schoolId || ''
      this.selectedCampusId = ''
      this.status = 'ready'
      this.errorCode = ''
      this.errorMessage = ''
      return true
    },
    selectSchool(schoolId: string) {
      const id = requireCampusId(schoolId, 'schoolId')
      if (!this.schools.some((school) => school.schoolId === id)) {
        throw new CampusClientCapabilityError(
          'REQUEST_INVALID',
          '当前操作者没有该学校范围',
          'USER_FIX'
        )
      }
      if (id === this.selectedSchoolId) return
      this.selectedSchoolId = id
      this.selectedCampusId = ''
      this.generation += 1
    },
    selectCampus(campusId: string) {
      if (!campusId) {
        if (!this.selectedCampusId) return
        this.selectedCampusId = ''
        this.generation += 1
        return
      }
      const id = requireCampusId(campusId, 'campusId')
      const school = this.schools.find((item) => item.schoolId === this.selectedSchoolId)
      if (!school?.campuses.some((campus) => campus.campusId === id)) {
        throw new CampusClientCapabilityError(
          'REQUEST_INVALID',
          '当前操作者没有该校区范围',
          'USER_FIX'
        )
      }
      if (id === this.selectedCampusId) return
      this.selectedCampusId = id
      this.generation += 1
    },
    captureSelection() {
      return Object.freeze({
        generation: this.generation,
        schoolId: this.selectedSchoolId,
        campusId: this.selectedCampusId
      })
    },
    isCurrent(selection: { generation: number; schoolId: string; campusId: string }) {
      return (
        selection.generation === this.generation &&
        selection.schoolId === this.selectedSchoolId &&
        selection.campusId === this.selectedCampusId
      )
    },
    clearAuthority() {
      this.operatorId = ''
      this.authorizationVersion = null
      this.permissions = []
      this.schools = []
      this.selectedSchoolId = ''
      this.selectedCampusId = ''
    },
    reset() {
      this.generation += 1
      this.status = 'idle'
      this.errorCode = ''
      this.errorMessage = ''
      this.clearAuthority()
    }
  }
})

function normalizeContext(context: AdminCampusContext): AdminCampusContext {
  try {
    if (
      !context ||
      !Number.isSafeInteger(context.authorizationVersion) ||
      context.authorizationVersion < 1 ||
      !Array.isArray(context.permissions) ||
      !Array.isArray(context.schools) ||
      !Array.isArray(context.menus)
    ) {
      throw new Error('invalid context')
    }
    const operatorId = requireCampusId(context.operatorId, 'operatorId')
    const schoolIds = new Set<string>()
    const schools = context.schools.map((school) => {
      const schoolId = requireCampusId(school.schoolId, 'schoolId')
      if (schoolIds.has(schoolId) || !school.name?.trim()) throw new Error('invalid school')
      schoolIds.add(schoolId)
      if (!Array.isArray(school.campuses) || !Array.isArray(school.actions)) {
        throw new Error('invalid school fields')
      }
      const campusIds = new Set<string>()
      const campuses = school.campuses.map((campus) => {
        const campusId = requireCampusId(campus.campusId, 'campusId')
        if (campusIds.has(campusId) || !campus.name?.trim()) throw new Error('invalid campus')
        campusIds.add(campusId)
        if (!Array.isArray(campus.actions)) throw new Error('invalid campus actions')
        return { campusId, name: campus.name.trim(), actions: uniqueStrings(campus.actions) }
      })
      return {
        schoolId,
        name: school.name.trim(),
        campuses,
        actions: uniqueStrings(school.actions)
      }
    })
    return {
      operatorId,
      authorizationVersion: context.authorizationVersion,
      permissions: uniqueStrings(context.permissions),
      schools,
      menus: uniqueStrings(context.menus)
    }
  } catch {
    throw new CampusClientCapabilityError('CONTRACT_INVALID', '学校权限上下文不符合约定')
  }
}

function uniqueStrings(values: unknown[]) {
  if (values.some((value) => typeof value !== 'string' || !value.trim())) {
    throw new Error('invalid string collection')
  }
  return [...new Set(values.map((value) => String(value).trim()))]
}

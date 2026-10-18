import { adminRequest } from '@/services/http'
import { campusAdminQueriesEnabled } from '@/config/capabilities'

export interface AdminCampusRef {
  campusId: string
  name: string
  actions: string[]
}

export interface AdminSchoolScope {
  schoolId: string
  name: string
  campuses: AdminCampusRef[]
  actions: string[]
}

export interface AdminCampusContext {
  operatorId: string
  authorizationVersion: number
  permissions: string[]
  schools: AdminSchoolScope[]
  menus: string[]
}

export const campusContextCapability = Object.freeze({
  available: campusAdminQueriesEnabled,
  reason: campusAdminQueriesEnabled
    ? '学校授权上下文查询已由构建配置开启。'
    : '学校授权上下文契约已冻结；等待真实后端 B 级 HTTP 证据后再开启。'
})

export async function getAdminCampusContext(): Promise<AdminCampusContext> {
  return adminRequest<AdminCampusContext>({ method: 'GET', url: '/campus/v1/context' })
}

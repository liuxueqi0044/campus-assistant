/**
 * Query capabilities are deliberately opt-in at build time. A successful typecheck or unit test
 * is not enough to open them; the value should only be enabled for a build whose server B-level
 * HTTP evidence has been reviewed.
 */
export function isCampusAdminQueriesEnabled(value: unknown): boolean {
  return value === 'true'
}

export const campusAdminQueriesEnabled = isCampusAdminQueriesEnabled(
  import.meta.env.VITE_ADMIN_ENABLE_CAMPUS_QUERIES,
)

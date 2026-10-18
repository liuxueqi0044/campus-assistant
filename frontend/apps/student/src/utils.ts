import { state } from '@/state'
export const money = (fen: number) =>
  Number.isFinite(fen) ? (fen / 100).toFixed(fen % 100 ? 2 : 0) : '待确认'
export const go = (url: string) => uni.navigateTo({ url })
export function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.reLaunch({ url: '/pages/index/index' })
}
export const toast = (title: string) => uni.showToast({ title, icon: 'none' })
export function requireLogin() {
  if (state.user.loggedIn) return true
  go('/pages-account/login')
  return false
}

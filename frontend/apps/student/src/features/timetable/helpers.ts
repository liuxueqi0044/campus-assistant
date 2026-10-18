import type { Course } from '@/types'

export const dayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
export const courseColors = ['#E5F2EC', '#E8EEF9', '#F8EEDD', '#EEE9F7', '#F7E7E6', '#E3F0F3']
export const periods = [
  ['08:00', '08:40'],
  ['08:55', '09:35'],
  ['10:00', '10:40'],
  ['10:55', '11:35'],
  ['14:00', '14:40'],
  ['14:55', '15:35'],
  ['16:00', '16:40'],
  ['16:55', '17:35'],
  ['18:30', '19:10'],
  ['19:25', '20:05'],
  ['20:20', '21:00'],
  ['21:15', '21:55'],
]
export interface CourseForm {
  title: string
  teacher: string
  room: string
  day: number
  start: number
  duration: number
  weeksText: string
  color: string
}
export function newCourseForm(): CourseForm {
  return {
    title: '',
    teacher: '',
    room: '',
    day: 1,
    start: 1,
    duration: 2,
    weeksText: '1-16',
    color: courseColors[0]!,
  }
}
export function formatWeeks(weeks: number[]): string {
  const values = [...new Set(weeks)].sort((a, b) => a - b)
  const chunks: string[] = []
  for (let i = 0; i < values.length; i++) {
    const first = values[i]!
    let last = first
    while (i + 1 < values.length && values[i + 1] === last + 1) {
      last = values[++i]!
    }
    chunks.push(first === last ? `${first}` : `${first}-${last}`)
  }
  return chunks.join(', ')
}
export function parseWeeks(text: string): number[] {
  if (!text.trim()) throw new Error('请填写上课周次')
  const result = new Set<number>()
  const input = text.replace(/[，、]/g, ',').replace(/[–—～~]/g, '-')
  for (const part of input.split(',')) {
    const match = part.trim().match(/^(\d{1,2})(?:\s*-\s*(\d{1,2}))?$/)
    if (!match) throw new Error('周次请填写为 1-16 或 1,3,5，不要留空项')
    const from = Number(match[1]),
      to = Number(match[2] || match[1])
    if (from < 1 || to > 20 || from > to)
      throw new Error('周次范围应在 1—20 周内，起始周不能晚于结束周')
    for (let week = from; week <= to; week++) result.add(week)
  }
  return [...result].sort((a, b) => a - b)
}
export function courseTime(start: number, duration: number): string {
  return `${periods[start - 1]?.[0] || '--:--'}—${periods[start + duration - 2]?.[1] || '--:--'}`
}
export function formFromCourse(course: Course): CourseForm {
  return {
    title: course.title,
    teacher: course.teacher,
    room: course.room,
    day: course.day,
    start: course.start,
    duration: course.duration,
    weeksText: formatWeeks(course.weeks),
    color: course.color,
  }
}
export function validateCourse(form: CourseForm): {
  data?: Omit<Course, 'id'>
  errors: Record<string, string>
} {
  const errors: Record<string, string> = {}
  if (!form.title.trim()) errors.title = '请填写课程名称'
  if (form.title.trim().length > 40) errors.title = '课程名称最多 40 个字'
  if (!form.room.trim()) errors.room = '请核对并填写上课地点，可填写“线上”或“待定”'
  if (!Number.isInteger(form.day) || form.day < 1 || form.day > 7) errors.day = '请选择星期'
  if (
    form.start < 1 ||
    form.start > 12 ||
    form.duration < 1 ||
    form.duration > 4 ||
    form.start + form.duration > 13
  )
    errors.period = '课程需在第 1—12 节内，连续 1—4 节'
  let weeks: number[] = []
  try {
    weeks = parseWeeks(form.weeksText)
  } catch (error) {
    errors.weeks = (error as Error).message
  }
  if (Object.keys(errors).length) return { errors }
  return {
    errors,
    data: {
      title: form.title.trim(),
      teacher: form.teacher.trim(),
      room: form.room.trim(),
      day: form.day,
      start: form.start,
      duration: form.duration,
      time: courseTime(form.start, form.duration),
      weeks,
      color: form.color,
    },
  }
}
export function courseConflicts(
  course: Omit<Course, 'id'>,
  courses: Course[],
  excludeId = '',
): Course[] {
  return courses.filter(
    (item) =>
      item.id !== excludeId &&
      item.day === course.day &&
      item.start < course.start + course.duration &&
      course.start < item.start + item.duration &&
      item.weeks.some((week) => course.weeks.includes(week)),
  )
}
export function weekDates(termStart: string, week: number): Date[] {
  const [year, month, day] = termStart.split('-').map(Number)
  return Array.from(
    { length: 7 },
    (_, index) => new Date(year!, month! - 1, day! + (week - 1) * 7 + index),
  )
}
export function sameDate(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}
export function demoImportCourses(): Course[] {
  return [
    {
      id: 'draft-1',
      title: '概率论与数理统计',
      teacher: '李老师',
      room: '理科楼 B203',
      day: 3,
      start: 3,
      duration: 2,
      time: courseTime(3, 2),
      weeks: [1, 3, 5, 7, 9, 11, 13, 15],
      color: courseColors[1]!,
    },
    {
      id: 'draft-2',
      title: '大学英语阅读',
      teacher: '陈老师',
      room: '',
      day: 5,
      start: 5,
      duration: 2,
      time: courseTime(5, 2),
      weeks: Array.from({ length: 16 }, (_, i) => i + 1),
      color: courseColors[0]!,
    },
  ]
}

import { ElMessage, ElNotification } from 'element-plus'

const ErrorDeduplicationWindowMillis = 2_000

let lastErrorMessage = ''
let lastErrorAt = 0

function readableMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message
  }
  if (typeof error === 'string' && error.trim()) {
    return error
  }
  return fallback
}

export function showError(error: unknown, fallback = '操作失败') {
  const message = readableMessage(error, fallback)
  const now = Date.now()
  if (message === lastErrorMessage && now - lastErrorAt < ErrorDeduplicationWindowMillis) {
    return
  }
  lastErrorMessage = message
  lastErrorAt = now

  ElNotification.error({
    title: fallback,
    message,
    position: 'top-right',
    duration: 8_000,
    showClose: true,
    offset: 64,
    customClass: 'gm-error-notification',
  })
}

export function showSuccess(message: string) {
  ElMessage.success({
    message,
    grouping: true,
    showClose: true,
    offset: 64,
  })
}

export function showWarning(message: string) {
  ElMessage.warning({
    message,
    grouping: true,
    showClose: true,
    offset: 64,
  })
}

import { ref, onUnmounted } from 'vue'

/**
 * 扫描任务自动轮询工具
 * <p>
 * 解决 G4-02：对 RUNNING/PENDING 状态的扫描任务启动定时轮询，
 * 自动刷新任务状态，无需用户手动刷新页面。
 * </p>
 *
 * @param {Function} fetchFn — 刷新数据的异步函数
 * @param {Function} hasActiveFn — 判断是否还有活跃任务需要继续轮询
 * @param {number} interval — 轮询间隔（毫秒），默认 3000ms
 * @returns {{ startPolling, stopPolling, isPolling }} — 轮询控制方法
 */
export function useTaskPolling(fetchFn, hasActiveFn, interval = 3000) {
  const isPolling = ref(false)
  let timer = null

  const startPolling = () => {
    if (isPolling.value) return
    isPolling.value = true

    const poll = async () => {
      if (!isPolling.value) return
      try {
        await fetchFn()
      } catch (e) {
        console.error('轮询刷新失败:', e)
      }
      // 如果还有活跃任务，继续轮询；否则停止
      if (hasActiveFn() && isPolling.value) {
        timer = setTimeout(poll, interval)
      } else {
        stopPolling()
      }
    }

    timer = setTimeout(poll, interval)
  }

  const stopPolling = () => {
    isPolling.value = false
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  onUnmounted(() => {
    stopPolling()
  })

  return { startPolling, stopPolling, isPolling }
}

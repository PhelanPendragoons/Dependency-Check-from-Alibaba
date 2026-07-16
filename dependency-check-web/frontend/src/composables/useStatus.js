/**
 * 扫描任务状态映射工具
 * <p>
 * 提供统一的扫描任务状态到 UI 展示的映射，避免在多个组件中重复定义。
 * 解决 G4-01：statusType/statusText 在 Dashboard/Tasks/TaskDetail 中重复定义。
 * </p>
 */
export function useStatus() {
  const statusType = (status) => {
    const map = {
      COMPLETED: 'success',
      RUNNING: 'warning',
      PENDING: 'info',
      FAILED: 'danger',
      CANCELLED: 'info',
    }
    return map[status] || 'info'
  }

  const statusText = (status) => {
    const map = {
      COMPLETED: '已完成',
      RUNNING: '扫描中',
      PENDING: '等待中',
      FAILED: '失败',
      CANCELLED: '已取消',
    }
    return map[status] || status
  }

  /** 判断任务是否处于活跃状态（需要轮询） */
  const isActive = (status) => status === 'RUNNING' || status === 'PENDING'

  return { statusType, statusText, isActive }
}

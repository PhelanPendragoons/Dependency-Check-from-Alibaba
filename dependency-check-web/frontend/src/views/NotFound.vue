<template>
  <div class="not-found">
    <div class="not-found-content">
      <h1 class="code">404</h1>
      <p class="message">页面不存在</p>
      <p class="hint">您访问的页面可能已被移除或链接错误</p>
      <el-button type="primary" @click="$router.push('/')">
        <el-icon><HomeFilled /></el-icon>返回首页
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.not-found-content {
  text-align: center;
}

.code {
  font-size: 96px;
  font-weight: 700;
  color: #dcdfe6;
  margin: 0;
  line-height: 1.2;
}

.message {
  font-size: 24px;
  color: #606266;
  margin: 16px 0 8px;
}

.hint {
  font-size: 14px;
  color: #909399;
  margin-bottom: 24px;
}
</style>

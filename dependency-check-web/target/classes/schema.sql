-- ============================================================
-- 应用依赖安全与合规分析平台 - 数据库初始化脚本
-- ============================================================
-- 数据库: H2 (嵌入式模式)
-- 说明: Spring Boot 启动时自动执行此脚本创建表结构
-- ============================================================

-- 项目表
-- 存储用户上传的项目信息
CREATE TABLE IF NOT EXISTS project (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE COMMENT '项目名称',
    description VARCHAR(1000) COMMENT '项目描述',
    file_path   VARCHAR(1000) COMMENT '项目文件存储路径',
    file_type   VARCHAR(50)  NOT NULL DEFAULT 'ZIP' COMMENT '文件类型(ZIP/JAR/DIRECTORY)',
    status      VARCHAR(50)  DEFAULT 'UPLOADED' COMMENT '状态(UPLOADED/SCANNING/COMPLETED/FAILED)',
    deleted     INT          DEFAULT 0 COMMENT '逻辑删除(0=未删除,1=已删除)',
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
);

-- 扫描任务表
-- 记录每次扫描任务的执行状态和结果摘要
CREATE TABLE IF NOT EXISTS scan_task (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id             BIGINT       NOT NULL COMMENT '关联项目ID',
    status                 VARCHAR(50)  DEFAULT 'PENDING' COMMENT '任务状态(PENDING/RUNNING/COMPLETED/FAILED/CANCELLED)',
    progress               INT          DEFAULT 0 COMMENT '扫描进度(0-100)',
    total_dependencies     INT          DEFAULT 0 COMMENT '发现的依赖总数',
    vulnerable_dependencies INT         DEFAULT 0 COMMENT '有漏洞的依赖数',
    error_message          VARCHAR(4000) COMMENT '错误信息',
    started_at             TIMESTAMP    COMMENT '开始时间',
    completed_at           TIMESTAMP    COMMENT '完成时间',
    created_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project(id)
);

-- 扫描结果表
-- 存储每次扫描发现的依赖漏洞和许可证信息
CREATE TABLE IF NOT EXISTS scan_result (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id            BIGINT        NOT NULL COMMENT '关联任务ID',
    dependency_name    VARCHAR(500)  COMMENT '依赖名称',
    dependency_version VARCHAR(100)  COMMENT '依赖版本',
    file_path          VARCHAR(1000) COMMENT '文件路径',
    cve_id             VARCHAR(50)   COMMENT 'CVE编号',
    cvss_score         DECIMAL(3,1)  COMMENT 'CVSS评分(0.0-10.0)',
    severity           VARCHAR(20)   COMMENT '严重等级(LOW/MEDIUM/HIGH/CRITICAL)',
    description        VARCHAR(4000) COMMENT '漏洞描述',
    license_name       VARCHAR(200)  COMMENT '许可证名称',
    license_url        VARCHAR(1000) COMMENT '许可证URL',
    is_vulnerable      BOOLEAN       DEFAULT FALSE COMMENT '是否有漏洞',
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_result_task FOREIGN KEY (task_id) REFERENCES scan_task(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_scan_task_project_id ON scan_task(project_id);
CREATE INDEX IF NOT EXISTS idx_scan_result_task_id ON scan_result(task_id);
CREATE INDEX IF NOT EXISTS idx_scan_result_cve_id ON scan_result(cve_id);
CREATE INDEX IF NOT EXISTS idx_scan_result_severity ON scan_result(severity);

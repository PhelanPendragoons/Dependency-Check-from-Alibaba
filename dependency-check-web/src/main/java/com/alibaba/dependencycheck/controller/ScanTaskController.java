package com.alibaba.dependencycheck.controller;

import com.alibaba.dependencycheck.model.dto.ScanResultDTO;
import com.alibaba.dependencycheck.model.dto.ScanTaskDTO;
import com.alibaba.dependencycheck.model.vo.Result;
import com.alibaba.dependencycheck.service.ScanTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 扫描任务管理接口
 * <p>
 * 提供扫描任务的创建、状态查询和结果查看功能。
 * </p>
 *
 * <b>接口列表：</b>
 * <ul>
 *   <li>POST /api/tasks — 创建扫描任务</li>
 *   <li>GET /api/tasks/{id} — 获取任务状态</li>
 *   <li>GET /api/tasks/{id}/results — 获取扫描结果</li>
 * </ul>
 *
 * <b>扫描流程：</b>
 * <ol>
 *   <li>前端调用 POST /api/tasks 创建任务，传入 projectId</li>
 *   <li>后端返回任务 ID，任务在后台异步执行</li>
 *   <li>前端轮询 GET /api/tasks/{id} 获取任务状态</li>
 *   <li>任务完成后，调用 GET /api/tasks/{id}/results 获取结果</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class ScanTaskController {

    private final ScanTaskService scanTaskService;

    /**
     * 创建扫描任务
     * <p>
     * 为指定项目创建扫描任务，任务会在后台异步执行。
     * 前端可以通过返回的 taskId 轮询任务状态。
     * </p>
     *
     * @param projectId 项目 ID
     * @return 创建的扫描任务信息（包含 taskId）
     */
    @PostMapping
    public Result<ScanTaskDTO> createTask(@RequestParam Long projectId) {
        log.info("创建扫描任务: projectId={}", projectId);
        ScanTaskDTO task = scanTaskService.createTask(projectId);
        return Result.success(task);
    }

    /**
     * 获取任务状态
     * <p>
     * 前端轮询此接口获取扫描进度。
     * 状态流转：PENDING → RUNNING → COMPLETED / FAILED
     * </p>
     *
     * @param id 任务 ID
     * @return 任务状态信息（包含进度百分比）
     */
    @GetMapping("/{id}")
    public Result<ScanTaskDTO> getTask(@PathVariable Long id) {
        ScanTaskDTO task = scanTaskService.getTask(id);
        return Result.success(task);
    }

    /**
     * 获取扫描结果列表
     * <p>
     * 任务完成后调用此接口获取详细的扫描结果，
     * 包括每个依赖的漏洞信息和许可证信息。
     * </p>
     *
     * @param id 任务 ID
     * @return 扫描结果列表
     */
    @GetMapping("/{id}/results")
    public Result<List<ScanResultDTO>> getResults(@PathVariable Long id) {
        List<ScanResultDTO> results = scanTaskService.getResults(id);
        return Result.success(results);
    }
}

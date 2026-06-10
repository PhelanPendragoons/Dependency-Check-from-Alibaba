package com.alibaba.dependencycheck.controller;

import com.alibaba.dependencycheck.model.dto.ProjectDTO;
import com.alibaba.dependencycheck.model.vo.Result;
import com.alibaba.dependencycheck.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 项目管理接口
 * <p>
 * 提供项目的上传、列表查询、详情查看和删除功能。
 * </p>
 *
 * <b>接口列表：</b>
 * <ul>
 *   <li>POST /api/projects — 上传项目 ZIP 文件</li>
 *   <li>GET /api/projects — 获取项目列表</li>
 *   <li>GET /api/projects/{id} — 获取项目详情</li>
 *   <li>DELETE /api/projects/{id} — 删除项目</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 上传项目 ZIP 文件
     * <p>
     * 接收前端上传的 ZIP 文件，解压后保存项目信息。
     * 支持最大 500MB 的文件上传（由 application.yml 中的配置控制）。
     * </p>
     *
     * @param file        上传的 ZIP 文件
     * @param name        项目名称
     * @param description 项目描述（可选）
     * @return 创建的项目信息
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ProjectDTO> createProject(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description) throws IOException {

        log.info("上传项目: name={}, fileName={}, size={}", name, file.getOriginalFilename(), file.getSize());
        ProjectDTO project = projectService.createProject(file, name, description);
        return Result.success(project);
    }

    /**
     * 获取项目列表
     *
     * @return 所有项目的列表
     */
    @GetMapping
    public Result<List<ProjectDTO>> listProjects() {
        List<ProjectDTO> projects = projectService.listProjects();
        return Result.success(projects);
    }

    /**
     * 获取项目详情
     *
     * @param id 项目 ID
     * @return 项目详细信息
     */
    @GetMapping("/{id}")
    public Result<ProjectDTO> getProject(@PathVariable Long id) {
        ProjectDTO project = projectService.getProject(id);
        return Result.success(project);
    }

    /**
     * 删除项目
     *
     * @param id 项目 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.success();
    }
}

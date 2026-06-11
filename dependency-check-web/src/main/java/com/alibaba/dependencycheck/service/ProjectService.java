package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.exception.BusinessException;
import com.alibaba.dependencycheck.mapper.ProjectMapper;
import com.alibaba.dependencycheck.mapper.ScanResultMapper;
import com.alibaba.dependencycheck.mapper.ScanTaskMapper;
import com.alibaba.dependencycheck.model.dto.ProjectDTO;
import com.alibaba.dependencycheck.model.entity.Project;
import com.alibaba.dependencycheck.model.entity.ScanResult;
import com.alibaba.dependencycheck.model.entity.ScanTask;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * 项目管理服务
 * <p>
 * 负责处理项目文件的上传、解压、存储和 CRUD 操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ScanTaskMapper scanTaskMapper;
    private final ScanResultMapper scanResultMapper;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;


    /**
     * 创建项目（上传并解压 ZIP 文件）
     *
     * @param file        上传的 ZIP 文件
     * @param name        项目名称
     * @param description 项目描述（可选）
     * @return 项目 DTO
     */
    public ProjectDTO createProject(MultipartFile file, String name, String description) throws IOException {
        // 1. 检查文件是否为空
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        // 2. 检查文件名是否合法
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        // 3. 创建项目目录
        String projectDir = uploadDir + File.separator + name;
        Path projectPath = Paths.get(projectDir);
        if (!Files.exists(projectPath)) {
            Files.createDirectories(projectPath);
        }

        // 4. 保存上传的 ZIP 文件
        String zipPath = projectDir + File.separator + originalFilename;
        file.transferTo(new File(zipPath));

        // 5. 解压 ZIP 文件
        unzip(zipPath, projectDir);

        // 6. 保存项目信息到数据库
        Project project = new Project();
        project.setName(name);
        project.setDescription(description);
        project.setFilePath(projectDir);
        project.setFileType("ZIP");
        project.setStatus("UPLOADED");
        projectMapper.insert(project);

        log.info("项目创建成功: id={}, name={}", project.getId(), name);

        return convertToDTO(project);
    }

    /**
     * 获取项目列表
     */
    public List<ProjectDTO> listProjects() {
        return projectMapper.selectList(null)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取项目详情
     */
    public ProjectDTO getProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在: " + id);
        }
        return convertToDTO(project);
    }

    /**
     * 删除项目（逻辑删除 + 级联清理）
     * <p>
     * 删除项目时执行以下清理操作：
     * <ol>
     *   <li>级联删除关联的扫描结果（scan_result）</li>
     *   <li>级联删除关联的扫描任务（scan_task）</li>
     *   <li>逻辑删除项目记录（project）</li>
     *   <li>清理服务器上的物理文件（上传目录）</li>
     * </ol>
     * </p>
     *
     * <b>设计说明：</b>
     * <ul>
     *   <li>使用代码手动级联而非数据库外键 ON DELETE CASCADE，
     *       因为 schema.sql 中的外键未指定级联删除，且 H2 的 ALTER TABLE 不支持添加级联</li>
     *   <li>代码级联更可控，可以在删除数据库记录的同时清理物理文件</li>
     *   <li>物理文件删除失败不会影响数据库删除操作（仅记录警告日志）</li>
     * </ul>
     */
    @Transactional
    public void deleteProject(Long id) {
        // 1. 检查项目是否存在
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在: " + id);
        }

        // 2. 查询该项目关联的所有扫描任务
        LambdaQueryWrapper<ScanTask> taskQuery = new LambdaQueryWrapper<>();
        taskQuery.eq(ScanTask::getProjectId, id);
        List<ScanTask> tasks = scanTaskMapper.selectList(taskQuery);

        // 3. 级联删除每个任务的扫描结果
        for (ScanTask task : tasks) {
            LambdaQueryWrapper<ScanResult> resultQuery = new LambdaQueryWrapper<>();
            resultQuery.eq(ScanResult::getTaskId, task.getId());
            int deletedResults = scanResultMapper.delete(resultQuery);
            if (deletedResults > 0) {
                log.debug("已删除扫描结果: taskId={}, count={}", task.getId(), deletedResults);
            }
        }

        // 4. 级联删除关联的扫描任务
        int deletedTasks = scanTaskMapper.delete(taskQuery);
        if (deletedTasks > 0) {
            log.debug("已删除扫描任务: projectId={}, count={}", id, deletedTasks);
        }

        // 5. 逻辑删除项目记录
        projectMapper.deleteById(id);
        log.info("项目已删除: id={}, name={}", id, project.getName());

        // 6. 清理服务器上的物理文件
        //    注意：物理文件删除失败不影响数据库操作，仅记录警告
        try {
            Path projectPath = Paths.get(project.getFilePath());
            if (Files.exists(projectPath)) {
                deleteDirectory(projectPath);
                log.info("已清理项目物理文件: {}", project.getFilePath());
            }
        } catch (Exception e) {
            log.warn("清理项目物理文件失败: {}", project.getFilePath(), e);
        }
    }

    /**
     * 递归删除目录及其所有子文件和子目录
     * <p>
     * 使用 Java NIO 的 walk() 方法遍历目录树，
     * 先删除所有文件（深度优先），最后删除空目录。
     * </p>
     *
     * @param directory 要删除的目录路径
     * @throws IOException 如果删除过程中发生 I/O 错误
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> pathStream = Files.walk(directory)) {
                // 按深度降序排列，先删除子文件/子目录，再删除父目录
                pathStream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("删除文件失败: {}", path, e);
                            }
                        });
            }
        }
    }


    /**
     * 解压 ZIP 文件（安全版本，防止路径穿越攻击）
     * <p>
     * 使用 Path.normalize() 和 startsWith() 检查，
     * 防止恶意 ZIP 文件中包含 "../" 路径穿越攻击。
     * </p>
     */
    private void unzip(String zipFilePath, String destDir) throws IOException {
        Path destPath = Paths.get(destDir).normalize();
        if (!Files.exists(destPath)) {
            Files.createDirectories(destPath);
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(zipFilePath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // 安全校验：防止路径穿越攻击（Zip Slip 漏洞）
                Path entryPath = destPath.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destPath)) {
                    log.warn("检测到路径穿越攻击，已跳过: {}", entry.getName());
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // 确保父目录存在
                    Files.createDirectories(entryPath.getParent());
                    // 写入文件
                    Files.copy(zis, entryPath);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * 将实体类转换为 DTO
     */
    private ProjectDTO convertToDTO(Project project) {
        ProjectDTO dto = new ProjectDTO();
        BeanUtils.copyProperties(project, dto);
        return dto;
    }
}

package com.alibaba.dependencycheck.service;

import com.alibaba.dependencycheck.exception.BusinessException;
import com.alibaba.dependencycheck.mapper.ProjectMapper;
import com.alibaba.dependencycheck.model.dto.ProjectDTO;
import com.alibaba.dependencycheck.model.entity.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
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
     * 删除项目（逻辑删除）
     */
    public void deleteProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在: " + id);
        }
        projectMapper.deleteById(id);
        log.info("项目已删除: id={}", id);
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

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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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

    // ==================== B4 安全修复常量 ====================

    /** B4-06: Windows 系统保留名称（大小写不敏感） */
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    /** B4-11: 项目名称最大长度 */
    private static final int MAX_PROJECT_NAME_LENGTH = 255;

    /** B4-05: 文件删除重试次数 */
    private static final int MAX_DELETE_RETRIES = 3;

    /** B4-05: 文件删除重试间隔（毫秒），应对 Windows 文件锁延迟 */
    private static final long DELETE_RETRY_DELAY_MS = 100;

    /** B4-05#4: ZIP 文件独立存储目录，与项目解压目录物理隔离
     * <p>ZIP 存放在项目目录内部时，若 ZIP 被占用无法删除会连累整个项目目录残留。
     * 改为独立目录后，ZIP 删除失败不影响项目目录清理。命名使用已通过
     * {@link #validateProjectName} 校验的 name，安全可靠。</p> */
    private static final String ZIP_DIR = "./uploads/_zips";

    // ==================== B4 安全校验方法 ====================

    /**
     * B4-11 + B4-06: 项目名称安全校验
     * <p>
     * 校验规则：
     * <ol>
     *   <li>不能为 null 或空白</li>
     *   <li>长度不能超过 {@value #MAX_PROJECT_NAME_LENGTH} 个字符</li>
     *   <li>不能包含禁止字符：/ \ &lt; &gt; : " | ? *</li>
     *   <li>不能包含路径穿越序列 ".."</li>
     *   <li>不能使用 Windows 系统保留名称（B4-06）</li>
     * </ol>
     * </p>
     *
     * @param name 项目名称
     * @throws BusinessException 如果名称不合法
     */
    private void validateProjectName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("项目名称不能为空");
        }
        if (name.length() > MAX_PROJECT_NAME_LENGTH) {
            throw new BusinessException("项目名称长度不能超过 " + MAX_PROJECT_NAME_LENGTH + " 个字符，当前: " + name.length());
        }
        // 检查禁止字符
        for (char c : name.toCharArray()) {
            if (c == '/' || c == '\\' || c == '<' || c == '>' || c == ':' ||
                    c == '"' || c == '|' || c == '?' || c == '*') {
                throw new BusinessException("项目名称包含非法字符: '" + c + "'");
            }
        }
        // 检查路径穿越序列
        if (name.contains("..")) {
            throw new BusinessException("项目名称包含非法字符序列: \"..\"");
        }
        // B4-06: 检查 Windows 系统保留名称
        if (WINDOWS_RESERVED_NAMES.contains(name.toUpperCase())) {
            throw new BusinessException("项目名称使用了 Windows 系统保留名，不允许: " + name);
        }
    }

    /**
     * 创建项目（上传并解压 ZIP 文件）
     * <p>
     * 使用 {@code @Transactional} 确保文件保存和数据库插入的原子性。
     * 如果数据库插入失败，已保存的文件不会被回滚（文件系统不支持事务），
     * 但至少保证数据库状态的一致性。
     * </p>
     *
     * @param file        上传的 ZIP 文件
     * @param name        项目名称
     * @param description 项目描述（可选）
     * @return 项目 DTO
     */
    @Transactional
    public ProjectDTO createProject(MultipartFile file, String name, String description) throws IOException {
        // B4-11 + B4-06: 项目名称安全校验
        validateProjectName(name);

        // 1. 检查文件是否为空
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        // 2. 检查文件名是否合法
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("文件名不能为空");
        }

        // B4-08: 检查项目名是否重复
        Project existing = projectMapper.findByName(name);
        if (existing != null) {
            throw new BusinessException("项目名已存在: " + name);
        }

        // 3. 创建项目目录
        String projectDir = uploadDir + File.separator + name;
        Path projectPath = Paths.get(projectDir);
        if (!Files.exists(projectPath)) {
            Files.createDirectories(projectPath);
        }

        // 4. 保存上传的 ZIP 文件
        // B4-05#4: ZIP 独立存储到 _zips/ 目录，与解压目录物理隔离
        // 使用绝对路径避免 Tomcat DiskFileItem.write() 解析相对路径时的 CWD 不一致问题
        Path zipsDir = Paths.get(ZIP_DIR).toAbsolutePath();
        if (!Files.exists(zipsDir)) {
            Files.createDirectories(zipsDir);
        }
        Path zipPath = zipsDir.resolve(name + ".zip");
        file.transferTo(zipPath.toFile());

        // 5. 解压 ZIP 文件
        // B4-05: Zip Slip 检测到后清理恶意 ZIP 和已解压文件
        try {
            unzip(zipPath.toString(), projectDir);
        } catch (BusinessException e) {
            // B4-05: 清理恶意 ZIP 文件和已解压目录（加固版，含重试和失败收集）
            java.util.List<String> cleanupFailures = new java.util.ArrayList<>();

            // 清理恶意 ZIP 文件（带重试）
            if (!deleteFileWithRetry(zipPath)) {
                cleanupFailures.add(zipPath.toString());
            } else {
                log.info("已删除恶意ZIP文件: {}", zipPath);
            }

            // 清理已解压的目录（带重试，收集失败列表）
            if (Files.exists(projectPath)) {
                List<String> dirFailures = deleteDirectory(projectPath);
                if (dirFailures.isEmpty()) {
                    log.info("已清理被污染的目录: {}", projectDir);
                } else {
                    cleanupFailures.addAll(dirFailures);
                }
            }

            // 统一报告清理结果
            if (!cleanupFailures.isEmpty()) {
                log.error("B4-05 清理不彻底！以下 {} 个文件/目录可能残留: {}",
                        cleanupFailures.size(), cleanupFailures);
            }

            // B4-05#3: 顶层事后验证 — 确认项目目录是否已完全清理
            // 即使 deleteDirectory 返回空列表，也做最终 Files.exists 确认，
            // 防止边缘情况（文件系统延迟、返回列表为空但目录仍存在）产生静默残留
            if (Files.exists(projectPath)) {
                log.error("B4-05 顶层验证失败：项目目录清理后仍存在残留: {}", projectPath.toAbsolutePath());
            }
            throw e;
        }

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
     * 获取项目列表（分页）
     * <p>
     * B4-02 修复：从全量返回改为分页返回，避免大数量级下的性能问题。
     * 使用 MyBatis-Plus 的 {@link Page} 进行物理分页。
     * </p>
     *
     * @param page     当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public IPage<ProjectDTO> listProjects(int page, int pageSize) {
        Page<Project> pageParam = new Page<>(page, pageSize);
        IPage<Project> result = projectMapper.selectPage(pageParam, null);
        return result.convert(this::convertToDTO);
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
        Path projectPath = Paths.get(project.getFilePath());
        if (Files.exists(projectPath)) {
            List<String> failures = deleteDirectory(projectPath);
            if (failures.isEmpty()) {
                log.info("已清理项目物理文件: {}", project.getFilePath());
            } else {
                log.warn("清理项目物理文件不彻底，{} 个文件/目录残留: {}", failures.size(), failures);
            }

            // B4-05#3: 顶层事后验证 — 确认项目目录是否已完全清理
            // deleteDirectory 返回空列表不能保证目录真正消失（边缘情况），
            // 使用 Files.exists 做最终确认，防止静默残留
            if (Files.exists(projectPath)) {
                log.error("B4-05 顶层验证失败：项目目录删除后仍存在残留: {}", projectPath.toAbsolutePath());
            }
        }

        // B4-05#4: 清理独立存储的 ZIP 文件（与项目目录物理隔离，互不影响）
        Path zipFile = Paths.get(ZIP_DIR + File.separator + project.getName() + ".zip");
        if (Files.exists(zipFile)) {
            if (deleteFileWithRetry(zipFile)) {
                log.debug("已清理独立ZIP文件: {}", zipFile);
            } else {
                log.warn("清理独立ZIP文件失败（不影响项目目录）: {}", zipFile);
            }
        }
    }

    /**
     * 带重试的单文件删除
     * <p>
     * B4-05 加固：文件删除失败后等待 {@value #DELETE_RETRY_DELAY_MS}ms 重试，
     * 最多重试 {@value #MAX_DELETE_RETRIES} 次，应对 Windows 下文件锁短暂延迟。
     * </p>
     *
     * @param path 要删除的文件或目录路径
     * @return true 如果删除成功，false 如果所有重试均失败
     */
    private boolean deleteFileWithRetry(Path path) {
        for (int attempt = 1; attempt <= MAX_DELETE_RETRIES; attempt++) {
            try {
                Files.deleteIfExists(path);
                if (!Files.exists(path)) {
                    return true;
                }
                if (attempt < MAX_DELETE_RETRIES) {
                    Thread.sleep(DELETE_RETRY_DELAY_MS);
                }
            } catch (IOException e) {
                if (attempt == MAX_DELETE_RETRIES) {
                    log.warn("删除文件失败（已重试{}次）: {}", MAX_DELETE_RETRIES, path, e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("删除文件被中断: {}", path);
                return false;
            }
        }
        return !Files.exists(path);
    }

    /**
     * 递归删除目录及其所有子文件和子目录（加固版）
     * <p>
     * B4-05 加固改进：
     * </p>
     * <ol>
     *   <li>每个文件/目录通过 {@link #deleteFileWithRetry(Path)} 删除，含重试机制</li>
     *   <li>不再静默吞掉失败 — 返回所有删除失败的路径列表</li>
     *   <li>空列表表示完全清理成功，非空列表表示有残留</li>
     * </ol>
     * <p>
     * 使用 Java NIO 的 walk() 方法遍历目录树，
     * 先删除所有文件（深度优先），最后删除空目录。
     * </p>
     *
     * @param directory 要删除的目录路径
     * @return 删除失败的路径列表（空列表 = 完全清理成功）
     */
    private List<String> deleteDirectory(Path directory) {
        List<String> failures = new java.util.ArrayList<>();
        if (Files.exists(directory)) {
            try (Stream<Path> pathStream = Files.walk(directory)) {
                // 按深度降序排列，先删除子文件/子目录，再删除父目录
                pathStream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            if (!deleteFileWithRetry(path)) {
                                failures.add(path.toString());
                            }
                        });
            } catch (IOException e) {
                log.warn("遍历目录失败: {}", directory, e);
                failures.add(directory.toString());
            }
        }
        return failures;
    }


    /**
     * 解压 ZIP 文件（安全版本，防止路径穿越攻击）
     * <p>
     * B4-05 修复：检测到路径穿越时不再仅记录警告，而是抛出异常，
     * 由调用方负责清理恶意 ZIP 文件和已解压目录。
     * </p>
     * <p>
     * 使用 Path.normalize() 和 startsWith() 检查，
     * 防止恶意 ZIP 文件中包含 "../" 路径穿越攻击。
     * </p>
     *
     * @param zipFilePath ZIP 文件路径
     * @param destDir     解压目标目录
     * @throws IOException       如果解压过程中发生 I/O 错误
     * @throws BusinessException 如果检测到路径穿越攻击（B4-05）
     */
    private void unzip(String zipFilePath, String destDir) throws IOException {
        Path destPath = Paths.get(destDir).normalize();
        if (!Files.exists(destPath)) {
            Files.createDirectories(destPath);
        }

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(zipFilePath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // B4-05: 安全校验：防止路径穿越攻击（Zip Slip 漏洞）
                Path entryPath = destPath.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destPath)) {
                    // B4-05 修复：检测到路径穿越后抛出异常，由调用方清理恶意文件
                    log.error("检测到路径穿越攻击: entry={}, resolvedPath={}", entry.getName(), entryPath);
                    zis.closeEntry();
                    throw new BusinessException("检测到路径穿越攻击，文件上传被拒绝");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    // 确保父目录存在
                    Files.createDirectories(entryPath.getParent());
                    // B4-10 修复：添加 REPLACE_EXISTING 选项，防止同名文件覆盖时报错
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
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

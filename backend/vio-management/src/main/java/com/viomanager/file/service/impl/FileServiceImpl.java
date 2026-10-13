package com.viomanager.file.service.impl;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.common.util.Sha256Util;
import com.viomanager.file.config.FileProperties;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.mapper.FileObjectMapper;
import com.viomanager.file.service.ClamAvScanner;
import com.viomanager.file.service.IFileService;
import com.viomanager.file.vo.FileUploadVO;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务实现。
 * ⚠️ upload 刻意不加 @Transactional：病毒命中（INFECTED）与扫描不可用（UNAVAILABLE）的
 * 记录必须在拒绝后仍然留库当证据——加事务的话抛异常会把记录一起回滚掉。
 */
@Slf4j
@Service
public class FileServiceImpl implements IFileService {

    @Resource
    private FileObjectMapper fileObjectMapper;

    @Resource
    private MinioClient minioClient;

    @Resource
    private FileProperties fileProperties;

    @Resource
    private ClamAvScanner clamAvScanner;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public Result<FileUploadVO> upload(MultipartFile file, String bizType, Long bizId) {
        //1.基础校验：非空、扩展名白名单、大小上限
        if (file == null || file.isEmpty()) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String fileExt = extractExt(originalName);
        if (!fileProperties.getAllowedExtensions().contains(fileExt)) {
            throw new BizException(CommonErrorCode.FILE_TYPE_NOT_ALLOWED, "不支持的文件格式：." + fileExt);
        }
        long maxBytes = fileProperties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(CommonErrorCode.FILE_TOO_LARGE,
                    "文件超过 " + fileProperties.getMaxSizeMb() + "MB 上限");
        }
        //2.读字节、算内容指纹、生成对象键（形如 material/2026/10/09/uuid.pdf，对齐表注释约定）
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }
        String sha256 = Sha256Util.sha256Hex(bytes);
        String objectKey = buildObjectKey(bizType, fileExt);
        //3.落 MinIO：桶懒创建（首次上传时建，服务启动不依赖 MinIO 在线）
        ensureBucket();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(fileProperties.getBucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 落盘失败，objectKey={}：{}", objectKey, e.getMessage());
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        //4.落库：scan_status=PENDING，created_by / org_id 取当前登录人（基座填充不含 created_by，手工置）
        CurrentUser user = UserHolder.getUser();
        FileObject record = new FileObject();
        record.setObjectKey(objectKey);
        record.setOriginalName(originalName);
        record.setFileExt(fileExt);
        record.setContentType(file.getContentType());
        record.setFileSize((long) bytes.length);
        record.setSha256(sha256);
        record.setBizType(bizType);
        record.setBizId(bizId);
        record.setScanStatus("PENDING");
        record.setDownloadCount(0);
        record.setOrgId(user == null ? null : user.getOrgId());
        record.setCreatedBy(user == null ? null : user.getUserId());
        fileObjectMapper.insert(record);
        //5.病毒扫描：CLEAN 放行；INFECTED 标记留证后抛 1010；
        //   扫描服务不可用不拦用户（组长 2026-10-10 拍板：不因服务故障影响使用）——
        //   文件照常成功返回但标记 UNAVAILABLE，error 日志留痕供管理员事后关注
        String finalScanStatus = "CLEAN";
        try {
            ClamAvScanner.ScanResult scanResult = clamAvScanner.scan(bytes);
            if (!scanResult.isClean()) {
                updateScanStatus(record.getId(), "INFECTED", scanResult.getSignature());
                log.warn("病毒检测命中：fileId={}，signature={}", record.getId(), scanResult.getSignature());
                throw new BizException(CommonErrorCode.FILE_INFECTED, "文件未通过病毒检测，已拒绝并记录");
            }
            updateScanStatus(record.getId(), "CLEAN", null);
        } catch (IOException e) {
            updateScanStatus(record.getId(), "UNAVAILABLE", null);
            finalScanStatus = "UNAVAILABLE";
            log.error("ClamAV 不可用，文件已放行入库并标记 UNAVAILABLE：fileId={}，原因：{}",
                    record.getId(), e.getMessage());
        }
        //6.组装返回
        FileUploadVO vo = new FileUploadVO();
        vo.setFileId(record.getId());
        vo.setScanStatus(finalScanStatus);
        vo.setOriginalName(originalName);
        return Result.ok(vo);
    }

    @Override
    public void download(Long fileId, HttpServletResponse response) {
        //1.查记录：不存在或已删一律 1004，不区分"删了"和"没有"（不给人试探空间）
        FileObject record = fileObjectMapper.selectById(fileId);
        if (record == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "文件不存在");
        }
        //2.权限：超管全放行；部门管理员限本部门子树；学生仅本人上传
        checkDownloadPermission(record);
        //3.染毒文件不放行下载；UNAVAILABLE（上传时扫描服务不可用）按组长拍板照常放行
        if ("INFECTED".equals(record.getScanStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "文件未通过病毒检测，暂不可下载");
        }
        //4.MinIO 流式回写：客户端只见文件流，永远见不到对象存储地址
        try (GetObjectResponse objectStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(fileProperties.getBucket())
                .object(record.getObjectKey())
                .build())) {
            String contentType = record.getContentType() == null
                    ? "application/octet-stream" : record.getContentType();
            response.setContentType(contentType);
            // 双写法兼容中文文件名：filename 给老客户端的 ASCII 回退（百分号编码），
            // filename* 是标准 UTF-8 写法（浏览器优先认它，URLEncoder 后空格变 %20）
            String encodedName = URLEncoder.encode(record.getOriginalName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
            byte[] buffer = new byte[8192];
            int len = objectStream.read(buffer);
            while (len != -1) {
                response.getOutputStream().write(buffer, 0, len);
                len = objectStream.read(buffer);
            }
        } catch (Exception e) {
            log.error("文件读取失败，fileId={}：{}", fileId, e.getMessage());
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "文件读取失败");
        }
        //5.计数 +1（原子自增，并发不丢）
        fileObjectMapper.increaseDownloadCount(fileId);
    }

    @Override
    public FileObject loadOwnedFile(Long fileId, Long userId) {
        //1.查记录：不存在或已删，与"非本人上传"统一 1001 同文案，不给探测空间
        FileObject record = fileObjectMapper.selectById(fileId);
        if (record == null) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileId 无效或非当前用户上传");
        }
        //2.归属：upload 落库时 createdBy 写的是上传人 userId，这里必须对得上
        if (userId == null || !userId.equals(record.getCreatedBy())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileId 无效或非当前用户上传");
        }
        return record;
    }

    /**
     * 下载权限矩阵（骨架口径，材料细则由 C 线在业务层叠加）：
     * 超管全放行；部门管理员 = 文件所属组织在自己子树内；学生 = 仅本人上传。
     * 子树判断用 sys_org.path 前缀匹配（/0/1/ 前缀 = 本部门及其下级）。
     */
    private void checkDownloadPermission(FileObject record) {
        CurrentUser user = UserHolder.getUser();
        String role = user.getRole();
        //1.超管
        if ("SUPER_ADMIN".equals(role)) {
            return;
        }
        //2.学生：仅本人上传
        if ("STUDENT".equals(role)) {
            if (user.getUserId() != null && user.getUserId().equals(record.getCreatedBy())) {
                return;
            }
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权下载该文件");
        }
        //3.部门管理员：文件的组织在自己子树内；查询异常一律按拒绝处理（宁可误拒不可误放）
        try {
            String adminPath = jdbcTemplate.queryForObject(
                    "SELECT path FROM sys_org WHERE id = ? AND deleted = 0", String.class, user.getOrgId());
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_org WHERE id = ? AND deleted = 0 AND path LIKE CONCAT(?, '%')",
                    Integer.class, record.getOrgId(), adminPath);
            if (adminPath != null && count != null && count > 0) {
                return;
            }
        } catch (DataAccessException e) {
            log.warn("下载权限子树校验查询失败，按拒绝处理：{}", e.getMessage());
        }
        throw new BizException(CommonErrorCode.FORBIDDEN, "无权下载该文件");
    }

    /** 更新扫描结果（只更新非空字段） */
    private void updateScanStatus(Long id, String scanStatus, String signature) {
        FileObject update = new FileObject();
        update.setId(id);
        update.setScanStatus(scanStatus);
        update.setScanSignature(signature);
        update.setScanAt(LocalDateTime.now());
        fileObjectMapper.updateById(update);
    }

    /** 桶懒创建：不存在就建，让服务启动不依赖 MinIO 在线 */
    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(fileProperties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(fileProperties.getBucket()).build());
            }
        } catch (Exception e) {
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "对象存储不可用");
        }
    }

    /** 对象键：业务类型/年/月/日/uuid.ext，对齐表注释约定 */
    private String buildObjectKey(String bizType, String fileExt) {
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return bizType.toLowerCase() + "/" + datePath + "/" + UUID.randomUUID()
                + (fileExt.isEmpty() ? "" : "." + fileExt);
    }

    /** 从文件名提扩展名：小写、不含点；没有扩展名返回空串 */
    private String extractExt(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot + 1).toLowerCase();
    }
}

package com.viomanager.file.service.impl;

import com.viomanager.common.auth.CurrentUser;
import com.viomanager.common.auth.UserHolder;
import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.common.result.Result;
import com.viomanager.common.util.Sha256Util;
import com.viomanager.datascop.OrgScopeSupport;
import com.viomanager.file.config.FileProperties;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.mapper.FileObjectMapper;
import com.viomanager.file.service.ClamAvScanner;
import com.viomanager.file.service.IFileService;
import com.viomanager.file.support.MinioStreamSupport;
import com.viomanager.file.vo.FileUploadVO;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务实现。
 * ⚠️ upload 刻意不加 @Transactional：病毒命中（INFECTED）与扫描不可用（UNAVAILABLE）的
 * 记录必须在拒绝后仍然留库当证据——加事务的话抛异常会把记录一起回滚掉。
 * 下载侧的查记录 / 病毒闸门 / MinIO 流式回写在 MinioStreamSupport（单文件行数规矩拆出）。
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
    private OrgScopeSupport orgScopeSupport;

    @Resource
    private MinioStreamSupport streamSupport;

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
        //1.查记录（不存在或已删一律 1004，不给人试探空间）
        FileObject record = streamSupport.requireFile(fileId);
        //2.权限：超管全放行；知识库附件公共放行；部门管理员限本部门子树；学生仅本人上传
        checkDownloadPermission(record);
        //3.病毒闸门 + MinIO 流式回写 + 计数（业务侧口子 streamFile 复用同一段）
        streamSupport.requireNotInfected(record);
        streamSupport.streamOut(record, response);
    }

    @Override
    public FileObject loadOwnedFile(Long fileId, Long userId) {
        //1.查记录（1004 口径同 download）
        FileObject record = streamSupport.requireFile(fileId);
        //2.归属：upload 落库时 createdBy 写的是上传人 userId，这里必须对得上；
        //   不存在与不是本人上传统一 1001 同文案，不给探测空间
        if (userId == null || !userId.equals(record.getCreatedBy())) {
            throw new BizException(CommonErrorCode.PARAM_INVALID, "fileId 无效或非当前用户上传");
        }
        return record;
    }

    @Override
    public void streamFile(Long fileId, HttpServletResponse response) {
        //业务层已做完自身权限判定（如 MS04 的本队成员校验），这里只保留病毒闸门与计数
        FileObject record = streamSupport.requireFile(fileId);
        streamSupport.requireNotInfected(record);
        streamSupport.streamOut(record, response);
    }

    @Override
    public InputStream openCheckedStream(Long fileId) {
        //1.记录 + 病毒闸门（染毒 1006 由调用方决定跳过还是整包失败）
        FileObject record = streamSupport.requireFile(fileId);
        streamSupport.requireNotInfected(record);
        //2.打开对象流：关闭责任在调用方（try-with-resources）
        return streamSupport.openStream(record);
    }

    /**
     * 下载权限矩阵（骨架口径，材料细则由 C 线在业务层叠加）：
     * 超管全放行；知识库附件（KB_FILE）= 公共资源登录即可下；部门管理员 = 文件所属组织在自己子树内；学生 = 仅本人上传。
     * 子树判断用 sys_org.path 前缀匹配（/0/1/ 前缀 = 本部门及其下级）。
     */
    private void checkDownloadPermission(FileObject record) {
        CurrentUser user = UserHolder.getUser();
        String role = user.getRole();
        //1.超管
        if ("SUPER_ADMIN".equals(role)) {
            return;
        }
        //2.公共资源：知识库附件随条目全员可见，登录即可下载——条目可见性（PUBLISHED）由知识库服务把关
        if ("KB_FILE".equals(record.getBizType())) {
            return;
        }
        //3.学生：仅本人上传
        if ("STUDENT".equals(role)) {
            if (user.getUserId() != null && user.getUserId().equals(record.getCreatedBy())) {
                return;
            }
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权下载该文件");
        }
        //4.部门管理员：文件的组织在自己子树内；查询异常一律按拒绝处理（宁可误拒不可误放）
        //  判定收口到 OrgScopeSupport（插入点①，Redis cache-aside + 降级直查库）
        if (!orgScopeSupport.inSubtree(user.getOrgId(), record.getOrgId())) {
            throw new BizException(CommonErrorCode.FORBIDDEN, "无权下载该文件");
        }
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

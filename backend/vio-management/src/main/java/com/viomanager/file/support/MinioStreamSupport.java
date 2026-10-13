package com.viomanager.file.support;

import com.viomanager.common.enums.CommonErrorCode;
import com.viomanager.common.exception.BizException;
import com.viomanager.file.config.FileProperties;
import com.viomanager.file.entity.FileObject;
import com.viomanager.file.mapper.FileObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件流支撑：查记录、病毒闸门、MinIO 流式回写与对象流打开。
 * 从 FileServiceImpl 拆出（单文件不超 300 行规矩），供其
 * download / streamFile / openCheckedStream / loadOwnedFile 复用。
 */
@Slf4j
@Component
public class MinioStreamSupport {

    @Resource
    private FileObjectMapper fileObjectMapper;

    @Resource
    private MinioClient minioClient;

    @Resource
    private FileProperties fileProperties;

    /** 查记录：不存在或已删一律 1004，不区分"删了"和"没有"（不给人试探空间） */
    public FileObject requireFile(Long fileId) {
        FileObject record = fileObjectMapper.selectById(fileId);
        if (record == null) {
            throw new BizException(CommonErrorCode.NOT_FOUND, "文件不存在");
        }
        return record;
    }

    /** 病毒闸门：INFECTED 拒绝下载与打包；UNAVAILABLE（扫描服务不可用）按组长拍板照常放行 */
    public void requireNotInfected(FileObject record) {
        if ("INFECTED".equals(record.getScanStatus())) {
            throw new BizException(CommonErrorCode.STATE_NOT_ALLOWED, "文件未通过病毒检测，暂不可下载");
        }
    }

    /** MinIO 流式回写响应 + 计数 +1：客户端只见文件流，永远见不到对象存储地址 */
    public void streamOut(FileObject record, HttpServletResponse response) {
        try (GetObjectResponse objectStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(fileProperties.getBucket())
                .object(record.getObjectKey())
                .build())) {
            String contentType = record.getContentType() == null
                    ? "application/octet-stream" : record.getContentType();
            response.setContentType(contentType);
            // 双写法兼容中文文件名：filename 是 ASCII 回退（百分号编码），filename* 是标准 UTF-8 写法
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
            log.error("文件读取失败，fileId={}：{}", record.getId(), e.getMessage());
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "文件读取失败");
        }
        //原子自增，并发不丢
        fileObjectMapper.increaseDownloadCount(record.getId());
    }

    /** 打开对象流（关闭责任在调用方，用 try-with-resources）；存储故障统一 500 */
    public InputStream openStream(FileObject record) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(fileProperties.getBucket())
                    .object(record.getObjectKey())
                    .build());
        } catch (Exception e) {
            log.error("对象存储读取失败，fileId={}：{}", record.getId(), e.getMessage());
            throw new BizException(CommonErrorCode.INTERNAL_ERROR, "文件读取失败");
        }
    }
}

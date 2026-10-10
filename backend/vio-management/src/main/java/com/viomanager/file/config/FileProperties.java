package com.viomanager.file.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务配置项（application.yml 的 vio.file 段）。
 * 组长规矩：配置类一律放 config 包（模块内的 file.config）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vio.file")
public class FileProperties {

    /** MinIO 桶名（懒创建：首次上传时不存在就建，服务启动不依赖 MinIO 在线） */
    private String bucket = "vio-files";

    /** 单文件大小上限（MB）。⚠️ 刻意低于 spring multipart 的 25MB：业务校验先给 1009 业务码 */
    private int maxSizeMb = 20;

    /** 扩展名白名单（小写、不带点）。不在白名单的 1008 拒绝 */
    private List<String> allowedExtensions = new ArrayList<>();

    /** ClamAV 病毒扫描连接位（compose 的 clamav 容器，3310 是 clamd 协议端口） */
    private ClamAv clamav = new ClamAv();

    /** ClamAV 连接配置 */
    @Data
    public static class ClamAv {
        private String host = "localhost";
        private int port = 3310;
    }
}

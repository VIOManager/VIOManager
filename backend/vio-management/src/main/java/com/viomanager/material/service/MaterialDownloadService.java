package com.viomanager.material.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 材料下载服务（MS04 单文件下载 / MS05 全队打包）：下载权限按 C 线清单——
 * 管理端或本队在册成员（5009），文件流经骨架 IFileService 口子出，不暴露对象存储地址。
 */
public interface MaterialDownloadService {

    /** MS04：下载单条提交的当前文件（业务权限在本层判定，病毒闸门与计数在骨架文件服务） */
    void downloadSubmission(Long submissionId, HttpServletResponse response);

    /** MS05：把该队全部当前版本材料打包成 zip 流（按要求名分目录，染毒文件跳过留痕） */
    void packageTeam(Long teamId, HttpServletResponse response);
}

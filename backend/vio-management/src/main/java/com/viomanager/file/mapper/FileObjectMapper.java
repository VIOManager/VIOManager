package com.viomanager.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.viomanager.common.datascop.DataScope;
import com.viomanager.file.entity.FileObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文件对象 Mapper。
 * ⚠️ SELF 声明的理由同 SysUserMapper：文件服务在 service 层自管下载权限
 * （超管全放行 / 部门管理员本部门子树 / 学生仅本人上传），
 * 不能让数据范围拦截器的"学生默认拒绝"在 SQL 层把合法查询拦掉。
 */
@Mapper
@DataScope(scope = DataScope.Scope.SELF)
public interface FileObjectMapper extends BaseMapper<FileObject> {

    /**
     * 下载计数原子自增：并发下载不能丢计数，必须 count = count + 1 而不是先读后写。
     * 手写 SQL 显式带 deleted = 0（@TableLogic 对手写 SQL 不生效）。
     */
    @Update("UPDATE file_object SET download_count = download_count + 1 "
            + "WHERE id = #{id} AND deleted = 0")
    int increaseDownloadCount(@Param("id") Long id);
}

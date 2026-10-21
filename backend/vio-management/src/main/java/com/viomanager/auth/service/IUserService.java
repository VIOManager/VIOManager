package com.viomanager.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.viomanager.auth.dto.LoginDTO;
import com.viomanager.auth.dto.LoginVO;
import com.viomanager.auth.dto.MyProfileVO;
import com.viomanager.auth.dto.PasswordDTO;
import com.viomanager.auth.entity.SysUser;
import com.viomanager.common.result.Result;

/**
 * 登录账号服务：登录、改密、本人档案。
 */
public interface IUserService extends IService<SysUser> {

    /**
     * 账密登录，成功返回 token 与身份信息，成功失败都记 sys_login_log。
     */
    Result<LoginVO> login(LoginDTO loginDTO, String ip, String userAgent);

    /**
     * 修改本人密码，改完旧 token 全部失效。
     */
    Result<Void> changePassword(PasswordDTO passwordDTO);

    /**
     * 本人档案（论坛独立库方案 §二.3）：JWT 身份 + orgId 换组织名。
     * 消费方：主站个人资料展示 + 论坛服务快照首建/组织变化时转发用户 JWT 调用。
     */
    Result<MyProfileVO> myProfile();
}

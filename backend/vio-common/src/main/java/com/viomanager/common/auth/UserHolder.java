package com.viomanager.common.auth;

/**
 * 当前登录用户的存取入口（ThreadLocal 方案）。
 * 过滤器进来时 saveUser，业务代码任意位置 getUser，请求结束 removeUser。
 */
public class UserHolder {

    private static final ThreadLocal<CurrentUser> tl = new ThreadLocal<>();

    public static void saveUser(CurrentUser user) {
        tl.set(user);
    }

    public static CurrentUser getUser() {
        return tl.get();
    }

    public static void removeUser() {
        tl.remove();
    }
}

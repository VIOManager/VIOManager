package com.viomanager.common.datascop;

/**
 * 组织 path 维护工具，A 线建组织时用它拼子组织的 path。
 *
 * path 约定（表 sys_org.path 注释）：形如 /0/1/12/ ——
 * 以斜杠开头和结尾、包含自身 ID、根组织的父 ID 记 0。
 * 子树查询 = 前缀匹配：path LIKE CONCAT(某组织path, '%')。
 * ⚠️ 冲突：结尾斜杠不能省——"/0/1/12/" 不会误匹配 "/0/1/120/"，
 * 去掉斜杠后前缀匹配会把 /0/1/120/ 也当成 /0/1/12/ 的子孙，数据范围就串部门了。
 */
public class OrgPathUtil {

    private OrgPathUtil() {
    }

    /**
     * 拼子组织的 path：父 path + 自己的 ID + 斜杠。
     * 例：父 "/0/1/"、自己 ID 12 → "/0/1/12/"
     */
    public static String buildChildPath(String parentPath, Long childId) {
        return parentPath + childId + "/";
    }

    /**
     * 判断 path 是否等于 ancestorPath 或在其子树下（含自己）。
     */
    public static boolean isSelfOrDescendant(String ancestorPath, String path) {
        if (ancestorPath == null || path == null) {
            return false;
        }
        return path.startsWith(ancestorPath);
    }
}

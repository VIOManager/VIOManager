package com.viomanager.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析：优先 X-Forwarded-For 逐跳解析，取第一个非内网地址（最前端是真实客户端）；
 * 没有该头或全是内网地址时，退回直连地址。
 * 逐跳：请求每经过一层代理，代理会把自己的地址追加到 XFF 尾部——
 * "202.97.1.1, 10.0.0.5" 表示客户端 202.97.1.1 经内网代理 10.0.0.5 转发过来。
 * ⚠️ 内网部署时 XFF 是唯一能拿到真实外网 IP 的途径（Step 3 登录日志的遗留项在此统一落地）。
 */
public final class IpUtil {

    private IpUtil() {
    }

    /** 解析当前请求的客户端 IP */
    public static String resolveClientIp(HttpServletRequest request) {
        //1.逐跳解析 X-Forwarded-For：从头往后找，第一个非内网地址即真实客户端
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            String[] hops = xff.split(",");
            for (int i = 0; i < hops.length; i++) {
                String ip = hops[i].trim();
                if (!ip.isEmpty() && !isInternalIp(ip)) {
                    return ip;
                }
            }
        }
        //2.没有 XFF 或全是内网地址：直连地址兜底
        return request.getRemoteAddr();
    }

    /** 是否内网/本机地址：127 段、10 段、192.168 段、172.16-31 段、IPv6 本机 */
    private static boolean isInternalIp(String ip) {
        if ("127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return true;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            // 172.16.0.0 ~ 172.31.255.255 是内网段，其余 172 段是公网
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (NumberFormatException e) {
                // 格式异常的地址按非内网处理，交给调用方原样落库
                return false;
            }
        }
        return false;
    }
}

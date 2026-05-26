package com.richie.component.dao.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 当前请求的租户编码上下文，基于 TransmittableThreadLocal，支持异步/线程池透传。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
public class TenantCodeContextHolder {

    private static final TransmittableThreadLocal<Long> CTX = new TransmittableThreadLocal<>();

    protected static void setTenantCode(Long tenantCode) {
        CTX.set(tenantCode);
    }

    public static Long getTenantCode() {
        return CTX.get();
    }

    protected static void clearContext() {
        CTX.remove();
    }

}

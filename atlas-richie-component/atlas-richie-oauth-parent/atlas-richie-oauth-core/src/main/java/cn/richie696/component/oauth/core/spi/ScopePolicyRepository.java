package cn.richie696.component.oauth.core.spi;

import java.util.Map;
import java.util.Set;

/**
 * 网关/API Scope 策略的读取端口。
 * <p>
 * 把"接口编码 → 路径/方法/所需 scope"这套映射数据从 {@link ScopeResolver} 中剥离,核心解析器只
 * 依赖该端口,不直接接触 Redis/数据库;默认实现见
 * {@link cn.richie696.component.oauth.core.support.GlobalCacheScopePolicyRepository},业务方可以
 * 接入 Apollo、Nacos 等配置中心。
 * </p>
 * <p>
 * 处于 oauth-core 的策略数据接入位置:被 {@link ScopeResolver} 单向调用;反向依赖来自网关拦截器
 * 与 Resource Server 鉴权流程,它们只关心策略接口,不关心后端。
 * </p>
 * <p>
 * 解决的问题:让"哪些 API 需要哪些 Scope"这类运营数据可以从代码中剥离,在不重启服务的前提下热
 * 更新,同时让 oauth-core 保持"策略无关",避免对 Redis 强耦合。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface ScopePolicyRepository {

    Set<String> apiCodes();

    Map<String, String> apiConfig(String apiCode);

    Set<String> requiredScopes(String apiCode);
}

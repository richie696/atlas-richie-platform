package cn.richie696.component.oauth.core.spi;

import java.util.Map;
import java.util.Set;

/** Gateway/API Scope 策略读取端口，核心解析器不直接依赖 Redis。 */
public interface ScopePolicyRepository {

    Set<String> apiCodes();

    Map<String, String> apiConfig(String apiCode);

    Set<String> requiredScopes(String apiCode);
}

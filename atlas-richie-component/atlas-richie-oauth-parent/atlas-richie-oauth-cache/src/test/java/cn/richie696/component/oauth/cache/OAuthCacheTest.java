package cn.richie696.component.oauth.cache;

import cn.richie696.component.cache.GlobalCacheManager;
import cn.richie696.component.oauth.cache.config.OAuthCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthCacheTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OAuthCacheAutoConfiguration.class);

    @Test
    void autoConfigurationCreatesGlobalCacheAdapterWhenCacheManagerExistsAndInMemoryFallbackOtherwise() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(InMemoryOAuthCache.class));

        contextRunner.withBean(GlobalCacheManager.class, () -> new GlobalCacheManager(
                        null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null))
                .run(context -> assertThat(context).hasSingleBean(GlobalCacheOAuthCache.class));
    }

    @Test
    void namespaceRejectsBlankValues() {
        assertThat(OAuthCacheKeys.namespace("oauth", "token"))
                .isEqualTo("oauth:token");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> OAuthCacheKeys.namespace("", "token"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

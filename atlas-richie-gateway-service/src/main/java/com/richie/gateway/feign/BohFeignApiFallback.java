package com.richie.gateway.feign;

import com.richie.component.i18n.resolver.I18nResolver;

public class BohFeignApiFallback extends AbstractBaseFeignApiFallback implements BohFeignApi {

    public BohFeignApiFallback(I18nResolver i18n) {
        super(i18n);
    }

}

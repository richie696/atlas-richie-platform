package com.richie.gateway.feign;


import com.richie.component.i18n.resolver.I18nResolver;

public class FohFeignApiFallback extends AbstractBaseFeignApiFallback implements FohFeignApi {

    public FohFeignApiFallback(I18nResolver i18n) {
        super(i18n);
    }

}

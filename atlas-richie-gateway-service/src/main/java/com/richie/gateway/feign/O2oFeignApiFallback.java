package com.richie.gateway.feign;


import com.richie.component.i18n.resolver.I18nResolver;

public class O2oFeignApiFallback extends AbstractBaseFeignApiFallback implements O2oFeignApi {

    public O2oFeignApiFallback(I18nResolver i18n) {
        super(i18n);
    }

}

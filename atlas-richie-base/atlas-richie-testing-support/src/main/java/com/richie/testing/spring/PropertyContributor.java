package com.richie.testing.spring;

import java.util.List;

@FunctionalInterface
public interface PropertyContributor {

    void contribute(List<String> pairs);
}

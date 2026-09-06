/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

/** Marker for a versioned, provider-owned typed query extension. */
public interface ProviderQueryOptions {

    String extensionId();

    String version();
}

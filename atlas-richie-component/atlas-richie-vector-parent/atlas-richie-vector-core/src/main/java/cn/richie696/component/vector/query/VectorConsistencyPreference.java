/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

/** Provider-neutral consistency preference; providers must reject unsupported overrides. */
public enum VectorConsistencyPreference {
    PROVIDER_DEFAULT,
    EVENTUAL,
    BOUNDED,
    STRONG
}

package com.example.mcp;

import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.annotation.McpTool;

public final class OptionalPrimitiveToolFixture {
    @McpTool(name = "forecast", group = "generic")
    public int forecast(
            @McpArgument(
                    name = "days",
                    required = false,
                    defaultValue = "3",
                    minimum = "1",
                    maximum = "7")
            int days) {
        return days;
    }
}

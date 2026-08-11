package com.example.mcp;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.annotation.McpTool;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class InventoryToolFixture {
    @McpTool(
            name = "inventory.query",
            description = "original description",
            readOnly = true,
            group = "inventory")
    public CompletionStage<InventoryResult> query(
            @McpArgument(name = "request", description = "inventory request")
            InventoryQuery request,
            McpCallContext context) {
        return CompletableFuture.completedFuture(new InventoryResult(
                context.tenantId(), request.storeId(), request.itemCodes().size()));
    }

    public record InventoryQuery(String storeId, List<String> itemCodes, StockType type) {
    }

    public record InventoryResult(String tenantId, String storeId, int itemCount) {
    }

    public enum StockType {
        AVAILABLE,
        PHYSICAL
    }
}

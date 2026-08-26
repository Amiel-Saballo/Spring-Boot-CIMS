package com.clinic.inventory.dto;

import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}
    public record NeedAttention(String signal, Long itemId, String itemCode, String itemName,
                                Integer onHand, Integer reorderLevel, Integer reorderQuantity,
                                String detail) {}
    public record Response(long activeItems, long nearExpiryBatches, long lowStockItems,
                           long pendingReceiving, long equipmentInUse,
                           List<NeedAttention> needsAttention,
                           List<TransactionDtos.Response> recentTransactions) {}
}

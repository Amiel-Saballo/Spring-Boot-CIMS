package com.clinic.inventory.controller;
import com.clinic.inventory.dto.DashboardDtos;
import com.clinic.inventory.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardRestController {
    private final DashboardService service;

    @GetMapping
    public DashboardDtos.Response dashboard(Authentication authentication) {
        DashboardDtos.Response response = service.get();
        boolean mayViewLogs = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "PERM_TRANSACTION_LOG".equals(a.getAuthority()));
        if (mayViewLogs) return response;
        return new DashboardDtos.Response(response.activeItems(), response.nearExpiryBatches(), response.lowStockItems(),
                response.pendingReceiving(), response.equipmentInUse(), response.needsAttention(), List.of());
    }
}

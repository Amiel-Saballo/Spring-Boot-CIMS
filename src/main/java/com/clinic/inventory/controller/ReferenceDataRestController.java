package com.clinic.inventory.controller;

import com.clinic.inventory.dto.ReferenceDtos;
import com.clinic.inventory.service.ReferenceDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class ReferenceDataRestController {
    private final ReferenceDataService service;

    @GetMapping("/units-of-measure") public List<ReferenceDtos.ReferenceResponse> uoms() { return service.uoms(); }
    @PostMapping("/units-of-measure") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyAuthority('PERM_SETTINGS','PERM_ITEMS')")
    public ReferenceDtos.ReferenceResponse addUom(@Valid @RequestBody ReferenceDtos.NameRequest request) { return service.addUom(request); }

    @GetMapping("/locations") public List<ReferenceDtos.ReferenceResponse> locations() { return service.locations(); }
    @PostMapping("/locations") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('PERM_LOCATIONS')")
    public ReferenceDtos.ReferenceResponse addLocation(@Valid @RequestBody ReferenceDtos.NameRequest request) { return service.addLocation(request); }

    @GetMapping("/near-expiry-days") public ReferenceDtos.NearExpiryResponse nearExpiryDays() { return new ReferenceDtos.NearExpiryResponse(service.nearExpiryDays()); }
    @PutMapping("/near-expiry-days") @PreAuthorize("hasAuthority('PERM_SETTINGS')")
    public ReferenceDtos.NearExpiryResponse setNearExpiry(@Valid @RequestBody ReferenceDtos.NearExpiryRequest request) { return service.setNearExpiryDays(request); }
}

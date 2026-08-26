package com.clinic.inventory.controller;

import java.io.IOException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.ReportDtos;
import com.clinic.inventory.security.CurrentUserService;
import com.clinic.inventory.service.ReportExportService;
import com.clinic.inventory.service.ReportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_REPORTS')")
public class ReportRestController {
    private final ReportService service;
    private final ReportExportService exportService;
    private final CurrentUserService currentUser;

    @GetMapping("/records")
    public Page<ReportDtos.RecordResponse> records(
            @PageableDefault(size = 10, sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.history(pageable);
    }

    @GetMapping("/records/{id}/preview")
    public ReportDtos.GeneratedReport previewRecord(@PathVariable Long id) {
        return service.previewRecord(id);
    }

    @GetMapping("/records/{id}/export/{format}")
    public ResponseEntity<byte[]> exportRecord(@PathVariable Long id,
            @PathVariable String format) throws IOException {
        return exportResponse(format, service.previewRecord(id));
    }

    @PostMapping("/generate")
    public ReportDtos.GeneratedReport generate(
            @Valid @RequestBody ReportDtos.GenerateRequest request,
            Authentication auth) {
        return service.generate(request, currentUser.require(auth));
    }

    @PostMapping("/preview")
    public ReportDtos.GeneratedReport preview(
            @Valid @RequestBody ReportDtos.GenerateRequest request) {
        return service.preview(request);
    }

    @PostMapping("/export/{format}")
    public ResponseEntity<byte[]> export(@PathVariable String format,
            @Valid @RequestBody ReportDtos.GenerateRequest request)
            throws IOException {
        return exportResponse(format, service.preview(request));
    }

    private ResponseEntity<byte[]> exportResponse(String format,
            ReportDtos.GeneratedReport report) throws IOException {
        byte[] bytes;
        MediaType mediaType;
        String extension;
        switch (format.toLowerCase()) {
        case "csv" -> {
            bytes = exportService.csv(report);
            mediaType = MediaType.parseMediaType("text/csv");
            extension = "csv";
        }
        case "xlsx", "excel" -> {
            bytes = exportService.excel(report);
            mediaType = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            extension = "xlsx";
        }
        case "pdf" -> {
            bytes = exportService.pdf(report);
            mediaType = MediaType.APPLICATION_PDF;
            extension = "pdf";
        }
        default -> throw new IllegalArgumentException(
                "Supported formats: csv, xlsx, pdf");
        }
        String filename = report.reportType().name().toLowerCase() + "-"
                + report.to() + "." + extension;
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }
}

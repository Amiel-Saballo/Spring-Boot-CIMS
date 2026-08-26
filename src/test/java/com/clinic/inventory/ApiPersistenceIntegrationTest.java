package com.clinic.inventory;

import com.clinic.inventory.repository.ReceivingTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiPersistenceIntegrationTest {

    private static final String PASSWORD = "ChangeMe123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReceivingTransactionRepository receivingRepository;

    @Test
    void receivingCreatedThroughRestApiIsPersistedAndVisibleToAnotherRequest() throws Exception {
        long nurseId = objectMapper.readTree(mvc.perform(get("/api/session/me")
                        .with(httpBasic("nurse@clinic.local", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        // A Nurse must be able to read active items for the Receiving/Issuance UI even without Item Master permission.
        mvc.perform(get("/api/items").param("status", "ACTIVE")
                        .with(httpBasic("nurse@clinic.local", PASSWORD)))
                .andExpect(status().isOk());

        JsonNode uoms = objectMapper.readTree(mvc.perform(get("/api/settings/units-of-measure")
                        .with(httpBasic("admin@clinic.local", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode locations = objectMapper.readTree(mvc.perform(get("/api/settings/locations")
                        .with(httpBasic("nurse@clinic.local", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long uomId = uoms.get(0).get("id").asLong();
        long locationId = locations.get(0).get("id").asLong();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String itemJson = """
                {"code":"MED-TEST-%s","name":"API Persistence Medicine %s","category":"MEDICINE","unitOfMeasureId":%d,"reorderLevel":10,"reorderQuantity":25}
                """.formatted(suffix, suffix, uomId);
        JsonNode item = objectMapper.readTree(mvc.perform(post("/api/items")
                        .with(httpBasic("admin@clinic.local", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(itemJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        String supplierJson = """
                {"name":"API Supplier %s","contactPerson":"Integration Test","contactNo":"09170000000","address":"Alabang"}
                """.formatted(suffix);
        JsonNode supplier = objectMapper.readTree(mvc.perform(post("/api/suppliers")
                        .with(httpBasic("nurse@clinic.local", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(supplierJson))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        String reference = "DR-API-" + suffix;
        String receivingJson = """
                {"supplierId":%d,"referenceNumber":"%s","dateReceived":"2026-08-25","remarks":"Created through REST API","lines":[{"itemId":%d,"quantity":12,"brand":"Test Brand","batchNumber":null,"expiryDate":"2027-08-25","model":null,"serialNumber":null,"assetTag":null,"locationId":%d}]}
                """.formatted(supplier.get("id").asLong(), reference, item.get("id").asLong(), locationId);

        mvc.perform(post("/api/receiving")
                        .with(httpBasic("nurse@clinic.local", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(receivingJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceNumber").value(reference))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // This independent HTTP request represents another browser/client reading the shared database.
        mvc.perform(get("/api/receiving").param("receivedBy", Long.toString(nurseId)).param("size", "100")
                        .with(httpBasic("nurse@clinic.local", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.referenceNumber == '%s')]".formatted(reference)).exists());

        assertThat(receivingRepository.findByReferenceNumber(reference)).isPresent();
    }

    @Test
    void servedFrontendContainsNoBrowserLocalInventoryStorageFallback() throws Exception {
        ClassPathResource js = new ClassPathResource("static/js/app.js");
        String source = js.getContentAsString(StandardCharsets.UTF_8);
        assertThat(source).doesNotContain("localStorage");
        assertThat(source).doesNotContain("cimsMockupState");
        assertThat(source).contains("/api/receiving", "/api/items", "/api/session/me");
    }
    @Test
    void databaseHealthEndpointConfirmsPersistenceLayerIsReachable() throws Exception {
        mvc.perform(get("/api/system/health")
                        .with(httpBasic("nurse@clinic.local", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").isNotEmpty());
    }

    @Test
    void bothServedEntryPointsUseOnlyTheRestApiClient() throws Exception {
        for (String resource : java.util.List.of("static/index.html", "static/mockup.html")) {
            String source = new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8);
            assertThat(source).contains("/js/app.js");
            assertThat(source).doesNotContain("localStorage", "cimsMockupState");
        }
    }

    @Test
    void frontendReferencesEveryMajorPersistedApiArea() throws Exception {
        String source = new ClassPathResource("static/js/app.js").getContentAsString(StandardCharsets.UTF_8);
        assertThat(source).contains(
                "/api/dashboard",
                "/api/items",
                "/api/suppliers",
                "/api/receiving",
                "/api/approvals",
                "/api/issuances",
                "/api/batches",
                "/api/equipment",
                "/api/disposals",
                "/api/users",
                "/api/roles",
                "/api/settings",
                "/api/transaction-logs",
                "/api/reports",
                "/api/system/health"
        );
    }

}

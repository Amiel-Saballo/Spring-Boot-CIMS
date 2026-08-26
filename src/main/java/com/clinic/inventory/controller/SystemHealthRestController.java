package com.clinic.inventory.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemHealthRestController {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @GetMapping("/health")
    public Map<String, Object> health() throws Exception {
        Integer probe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        try (Connection connection = dataSource.getConnection()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", probe != null && probe == 1 ? "UP" : "DOWN");
            result.put("database", connection.getMetaData().getDatabaseProductName());
            result.put("databaseVersion", connection.getMetaData().getDatabaseProductVersion());
            result.put("readOnly", connection.isReadOnly());
            return result;
        }
    }
}

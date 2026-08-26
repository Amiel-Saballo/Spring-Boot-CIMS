package com.clinic.inventory.config;

import com.clinic.inventory.entity.*;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ItemStatus;
import com.clinic.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserAccountRepository userRepository;
    private final UnitOfMeasureRepository uomRepository;
    private final ClinicLocationRepository locationRepository;
    private final SystemSettingRepository settingRepository;
    private final ItemRepository itemRepository;
    private final SupplierRepository supplierRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed-demo-data:true}")
    private boolean seedDemoData;

    @Value("${app.demo-password:ChangeMe123!}")
    private String demoPassword;

    @Bean
    CommandLineRunner initializeReferenceData() {
        return args -> {
            Map<String, Permission> permissions = seedPermissions();
            Role nurse = seedRole("Nurse", "Day-to-day clinic inventory operations.",
                    Set.of("RECEIVING", "ISSUANCE", "BATCHES", "EQUIPMENT", "DISPOSAL", "SUPPLIERS", "REPORTS", "TRANSACTION_LOG"), permissions);
            Role supervisor = seedRole("Supervisor", "Reviews receiving requests and generates reports.",
                    Set.of("RECEIVING", "APPROVALS", "BATCHES", "EQUIPMENT", "REPORTS", "TRANSACTION_LOG"), permissions);
            Role administrator = seedRole("Administrator", "Manages master data, users, roles, and system settings.",
                    Set.of("ITEMS", "USERS", "ROLES", "SETTINGS", "LOCATIONS"), permissions);

            seedUser("nurse@clinic.local", "Nina Cruz", nurse);
            seedUser("supervisor@clinic.local", "Marco Lim", supervisor);
            seedUser("admin@clinic.local", "Ana Villanueva", administrator);

            for (String name : List.of("tablet", "capsule", "box", "unit", "piece", "pack", "bottle", "vial")) {
                uomRepository.findByNameIgnoreCase(name).orElseGet(() -> uomRepository.save(UnitOfMeasure.builder().name(name).build()));
            }
            for (String name : List.of("Alabang", "Cebu", "Makati")) {
                locationRepository.findByNameIgnoreCase(name).orElseGet(() -> locationRepository.save(ClinicLocation.builder().name(name).build()));
            }
            settingRepository.findByKey("NEAR_EXPIRY_DAYS").orElseGet(() -> settingRepository.save(SystemSetting.builder()
                    .key("NEAR_EXPIRY_DAYS").value("90").build()));

            if (seedDemoData) seedMasterData();
        };
    }

    private Map<String, Permission> seedPermissions() {
        Map<String, String> defs = new LinkedHashMap<>();
        defs.put("ITEMS", "Item Master");
        defs.put("RECEIVING", "Receiving");
        defs.put("APPROVALS", "Approvals");
        defs.put("ISSUANCE", "Issuance");
        defs.put("BATCHES", "Batches");
        defs.put("EQUIPMENT", "Equipment");
        defs.put("DISPOSAL", "Disposal");
        defs.put("SUPPLIERS", "Suppliers");
        defs.put("REPORTS", "Reports");
        defs.put("USERS", "Users");
        defs.put("ROLES", "Roles");
        defs.put("SETTINGS", "System Settings");
        defs.put("LOCATIONS", "Locations");
        defs.put("TRANSACTION_LOG", "Transaction Log");
        Map<String, Permission> map = new LinkedHashMap<>();
        defs.forEach((code, name) -> map.put(code, permissionRepository.findByCodeIgnoreCase(code)
                .orElseGet(() -> permissionRepository.save(Permission.builder().code(code).name(name).description("Access " + name).build()))));
        return map;
    }

    private Role seedRole(String name, String description, Set<String> permissionCodes, Map<String, Permission> permissions) {
        // Seed defaults only when the role is first created. Existing permissions are user-managed
        // and must survive application restarts, even if every permission was intentionally revoked.
        return roleRepository.findByNameIgnoreCase(name).orElseGet(() -> {
            Role role = Role.builder().name(name).description(description).active(true).build();
            role.setPermissions(new LinkedHashSet<>(permissionCodes.stream().map(permissions::get).toList()));
            return roleRepository.save(role);
        });
    }

    private void seedUser(String email, String fullName, Role role) {
        userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(UserAccount.builder()
                .email(email).fullName(fullName).passwordHash(passwordEncoder.encode(demoPassword)).role(role).active(true).build()));
    }

    private void seedMasterData() {
        UnitOfMeasure tablet = uomRepository.findByNameIgnoreCase("tablet").orElseThrow();
        UnitOfMeasure capsule = uomRepository.findByNameIgnoreCase("capsule").orElseThrow();
        UnitOfMeasure box = uomRepository.findByNameIgnoreCase("box").orElseThrow();
        UnitOfMeasure unit = uomRepository.findByNameIgnoreCase("unit").orElseThrow();
        seedItem("MED-PCM500", "Paracetamol 500 mg", ItemCategory.MEDICINE, tablet, 100, 500);
        seedItem("MED-AMB500", "Amoxicillin 500 mg", ItemCategory.MEDICINE, capsule, 100, 300);
        seedItem("SUP-MASK", "Surgical Face Mask", ItemCategory.SUPPLY, box, 20, 50);
        seedItem("SUP-GLOVE", "Nitrile Gloves", ItemCategory.SUPPLY, box, 15, 40);
        seedItem("EQ-BPMON", "Digital BP Monitor", ItemCategory.EQUIPMENT, unit, 0, 1);
        supplierRepository.findByNameIgnoreCase("Metro Medical Trading").orElseGet(() -> supplierRepository.save(Supplier.builder()
                .name("Metro Medical Trading").contactPerson("Lara Santos").contactNo("0917 555 0142").address("Makati City").active(true).build()));
        supplierRepository.findByNameIgnoreCase("HealthSource Pharma").orElseGet(() -> supplierRepository.save(Supplier.builder()
                .name("HealthSource Pharma").contactPerson("Paolo Reyes").contactNo("0918 223 8801").address("Pasig City").active(true).build()));
    }

    private void seedItem(String code, String name, ItemCategory category, UnitOfMeasure uom, int reorderLevel, int reorderQuantity) {
        itemRepository.findByCodeIgnoreCase(code).orElseGet(() -> itemRepository.save(Item.builder()
                .code(code).name(name).category(category).unitOfMeasure(uom).reorderLevel(reorderLevel)
                .reorderQuantity(reorderQuantity).status(ItemStatus.ACTIVE).build()));
    }
}

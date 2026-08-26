package com.clinic.inventory.repository;
import com.clinic.inventory.entity.ClinicLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ClinicLocationRepository extends JpaRepository<ClinicLocation, Long> {
    Optional<ClinicLocation> findByNameIgnoreCase(String name);
}

package com.clinic.inventory.repository;
import com.clinic.inventory.entity.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {
    Optional<UnitOfMeasure> findByNameIgnoreCase(String name);
}

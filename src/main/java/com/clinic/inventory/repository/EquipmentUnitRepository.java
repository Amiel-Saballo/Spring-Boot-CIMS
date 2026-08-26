package com.clinic.inventory.repository;
import com.clinic.inventory.entity.EquipmentUnit;
import com.clinic.inventory.enums.EquipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface EquipmentUnitRepository extends JpaRepository<EquipmentUnit, Long> {
    Optional<EquipmentUnit> findByAssetTagIgnoreCase(String assetTag);
    boolean existsByAssetTagIgnoreCase(String assetTag);
    boolean existsBySerialNumberIgnoreCase(String serialNumber);
    boolean existsByItemIdAndStatusNot(Long itemId, EquipmentStatus status);
    Page<EquipmentUnit> findAll(Pageable pageable);
    List<EquipmentUnit> findByLocationIdOrderByAssetTagAsc(Long locationId);
}

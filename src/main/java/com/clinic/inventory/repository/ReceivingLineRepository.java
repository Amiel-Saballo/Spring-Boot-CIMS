package com.clinic.inventory.repository;
import com.clinic.inventory.entity.ReceivingLine;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReceivingLineRepository extends JpaRepository<ReceivingLine, Long> {
    boolean existsByAssetTagIgnoreCase(String assetTag);
    boolean existsBySerialNumberIgnoreCase(String serialNumber);
}

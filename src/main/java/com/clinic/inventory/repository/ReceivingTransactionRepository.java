package com.clinic.inventory.repository;
import com.clinic.inventory.entity.ReceivingTransaction;
import com.clinic.inventory.enums.ReceivingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface ReceivingTransactionRepository extends JpaRepository<ReceivingTransaction, Long> {
    Optional<ReceivingTransaction> findByReferenceNumber(String referenceNumber);
    Page<ReceivingTransaction> findByStatus(ReceivingStatus status, Pageable pageable);
    Page<ReceivingTransaction> findByReceivedById(Long userId, Pageable pageable);
    List<ReceivingTransaction> findByStatusAndDateReceivedBetweenOrderByDateReceivedAsc(ReceivingStatus status, LocalDate from, LocalDate to);
    boolean existsBySupplierIdAndStatusIn(Long supplierId, List<ReceivingStatus> statuses);
    boolean existsBySupplierIdAndStatusAndDateReceivedGreaterThanEqual(Long supplierId, ReceivingStatus status, LocalDate date);
    @Query("""
      select count(rl) from ReceivingLine rl
      join rl.transaction rt
      where rt.supplier.id = :supplierId
        and rt.status = :approvedStatus
        and rl.item.status = :activeStatus
      """)
    long countApprovedActiveItemFromSupplier(@Param("supplierId") Long supplierId,
                                                  @Param("approvedStatus") ReceivingStatus approvedStatus,
                                                  @Param("activeStatus") com.clinic.inventory.enums.ItemStatus activeStatus);
}

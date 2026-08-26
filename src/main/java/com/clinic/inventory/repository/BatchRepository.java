package com.clinic.inventory.repository;
import com.clinic.inventory.entity.Batch;
import com.clinic.inventory.enums.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
public interface BatchRepository extends JpaRepository<Batch, Long> {
    boolean existsByItemIdAndStatus(Long itemId, BatchStatus status);
    List<Batch> findByItemIdAndStatusAndOnHandGreaterThanOrderByExpiryDateAscIdAsc(Long itemId, BatchStatus status, int minimumOnHand);
    Page<Batch> findAll(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.id = :id")
    Batch findForUpdate(@Param("id") Long id);
    List<Batch> findByItemIdAndStatus(Long itemId, BatchStatus status);
    @Query("select coalesce(sum(b.onHand),0) from Batch b where b.item.id=:itemId and b.status=:status")
    Long sumOnHandByItem(@Param("itemId") Long itemId, @Param("status") BatchStatus status);
    List<Batch> findByExpiryDateBetweenAndStatus(LocalDate from, LocalDate to, BatchStatus status);
}

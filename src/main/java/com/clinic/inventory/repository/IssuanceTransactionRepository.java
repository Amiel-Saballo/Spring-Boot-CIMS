package com.clinic.inventory.repository;
import com.clinic.inventory.entity.IssuanceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface IssuanceTransactionRepository extends JpaRepository<IssuanceTransaction, Long> {
    Page<IssuanceTransaction> findAll(Pageable pageable);
    List<IssuanceTransaction> findByDateIssuedBetweenOrderByDateIssuedAsc(LocalDate from, LocalDate to);
}

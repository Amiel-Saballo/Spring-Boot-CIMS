package com.clinic.inventory.repository;
import com.clinic.inventory.entity.DisposalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface DisposalRecordRepository extends JpaRepository<DisposalRecord, Long> {
    Page<DisposalRecord> findAll(Pageable pageable);
    List<DisposalRecord> findByDisposalDateBetweenOrderByDisposalDateAsc(LocalDate from, LocalDate to);
}

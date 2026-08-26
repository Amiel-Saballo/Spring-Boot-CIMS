package com.clinic.inventory.repository;
import com.clinic.inventory.entity.ReportRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportRecordRepository extends JpaRepository<ReportRecord, Long> {
    Page<ReportRecord> findAll(Pageable pageable);
}

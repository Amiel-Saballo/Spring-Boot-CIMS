package com.clinic.inventory.repository;
import com.clinic.inventory.entity.TransactionLog;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    @Query("""
      select t from TransactionLog t
      where (:type is null or t.transactionType = :type)
        and (:category is null or t.affectedItem.category = :category)
        and (:from is null or t.transactionDate >= :from)
        and (:to is null or t.transactionDate <= :to)
      """)
    Page<TransactionLog> search(@Param("type") TransactionType type,
                                @Param("category") ItemCategory category,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                Pageable pageable);
    @Query("""
      select t from TransactionLog t
      where (:type is null or t.transactionType = :type)
        and (:category is null or t.affectedItem.category = :category)
        and t.transactionDate between :from and :to
      order by t.transactionDate desc
      """)
    List<TransactionLog> reportRows(@Param("type") TransactionType type,
                                    @Param("category") ItemCategory category,
                                    @Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to);
}

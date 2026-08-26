package com.clinic.inventory.repository;
import com.clinic.inventory.entity.Item;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
    List<Item> findByStatusOrderByCodeAsc(ItemStatus status);
    List<Item> findByCategoryAndStatusOrderByCodeAsc(ItemCategory category, ItemStatus status);
    @Query("""
      select i from Item i
      where (:q is null or lower(i.code) like lower(concat('%', :q, '%')) or lower(i.name) like lower(concat('%', :q, '%')))
        and (:category is null or i.category = :category)
        and (:status is null or i.status = :status)
        and (:uomId is null or i.unitOfMeasure.id = :uomId)
      """)
    Page<Item> search(@Param("q") String q, @Param("category") ItemCategory category,
                      @Param("status") ItemStatus status, @Param("uomId") Long uomId, Pageable pageable);
}

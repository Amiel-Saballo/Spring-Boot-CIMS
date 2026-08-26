package com.clinic.inventory.service;

import com.clinic.inventory.dto.ItemDtos;
import com.clinic.inventory.entity.Item;
import com.clinic.inventory.enums.*;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {
    private final ItemRepository itemRepository;
    private final UnitOfMeasureRepository uomRepository;
    private final BatchRepository batchRepository;
    private final EquipmentUnitRepository equipmentRepository;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public Page<ItemDtos.Response> search(String q, ItemCategory category, ItemStatus status, Long uomId, Pageable pageable) {
        return itemRepository.search(blankToNull(q), category, status, uomId, pageable).map(mapper::item);
    }

    @Transactional(readOnly = true)
    public ItemDtos.Response get(Long id) { return mapper.item(require(id)); }

    @Transactional
    public ItemDtos.Response create(ItemDtos.CreateRequest request) {
        if (itemRepository.findByCodeIgnoreCase(request.code()).isPresent()) throw new BusinessRuleException("Item code already exists");
        Item item = Item.builder()
                .code(request.code().trim()).name(request.name().trim()).category(request.category())
                .unitOfMeasure(uomRepository.findById(request.unitOfMeasureId()).orElseThrow(() -> new ResourceNotFoundException("Unit of Measure not found")))
                .reorderLevel(request.reorderLevel()).reorderQuantity(request.reorderQuantity()).status(ItemStatus.ACTIVE).build();
        return mapper.item(itemRepository.save(item));
    }

    @Transactional
    public ItemDtos.Response update(Long id, ItemDtos.UpdateRequest request) {
        Item item = require(id);
        if (itemRepository.existsByCodeIgnoreCaseAndIdNot(request.code(), id)) throw new BusinessRuleException("Item code already exists");
        item.setCode(request.code().trim()); item.setName(request.name().trim()); item.setCategory(request.category());
        item.setUnitOfMeasure(uomRepository.findById(request.unitOfMeasureId()).orElseThrow(() -> new ResourceNotFoundException("Unit of Measure not found")));
        item.setReorderLevel(request.reorderLevel()); item.setReorderQuantity(request.reorderQuantity());
        return mapper.item(itemRepository.save(item));
    }

    @Transactional
    public void softDelete(Long id) {
        Item item = require(id);
        if (batchRepository.existsByItemIdAndStatus(id, BatchStatus.ACTIVE)) throw new BusinessRuleException("Item cannot be deleted while it has an active batch");
        if (equipmentRepository.existsByItemIdAndStatusNot(id, EquipmentStatus.DISPOSED)) throw new BusinessRuleException("Item cannot be deleted while it has active equipment units");
        item.setStatus(ItemStatus.INACTIVE);
        itemRepository.save(item);
    }

    @Transactional
    public ItemDtos.Response reactivate(Long id) {
        Item item = require(id);
        item.setStatus(ItemStatus.ACTIVE);
        return mapper.item(itemRepository.save(item));
    }

    public Item require(Long id) { return itemRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Item not found")); }
    private String blankToNull(String q) { return q == null || q.isBlank() ? null : q.trim(); }
}

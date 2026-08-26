package com.clinic.inventory.service;

import com.clinic.inventory.dto.ReferenceDtos;
import com.clinic.inventory.entity.*;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferenceDataService {
    public static final String NEAR_EXPIRY_DAYS = "NEAR_EXPIRY_DAYS";
    private final UnitOfMeasureRepository uomRepository;
    private final ClinicLocationRepository locationRepository;
    private final SystemSettingRepository settingRepository;

    @Transactional(readOnly = true)
    public List<ReferenceDtos.ReferenceResponse> uoms() {
        return uomRepository.findAll(Sort.by("name").ascending()).stream().map(x -> new ReferenceDtos.ReferenceResponse(x.getId(), x.getName())).toList();
    }
    @Transactional
    public ReferenceDtos.ReferenceResponse addUom(ReferenceDtos.NameRequest request) {
        String name = request.name().trim();
        if (uomRepository.findByNameIgnoreCase(name).isPresent()) throw new BusinessRuleException("Unit of Measure already exists");
        UnitOfMeasure saved = uomRepository.save(UnitOfMeasure.builder().name(name).build());
        return new ReferenceDtos.ReferenceResponse(saved.getId(), saved.getName());
    }

    @Transactional(readOnly = true)
    public List<ReferenceDtos.ReferenceResponse> locations() {
        return locationRepository.findAll(Sort.by("name").ascending()).stream().map(x -> new ReferenceDtos.ReferenceResponse(x.getId(), x.getName())).toList();
    }
    @Transactional
    public ReferenceDtos.ReferenceResponse addLocation(ReferenceDtos.NameRequest request) {
        String name = request.name().trim();
        if (locationRepository.findByNameIgnoreCase(name).isPresent()) throw new BusinessRuleException("Location already exists");
        ClinicLocation saved = locationRepository.save(ClinicLocation.builder().name(name).build());
        return new ReferenceDtos.ReferenceResponse(saved.getId(), saved.getName());
    }

    @Transactional(readOnly = true)
    public int nearExpiryDays() { return Integer.parseInt(settingRepository.findByKey(NEAR_EXPIRY_DAYS).orElseThrow().getValue()); }
    @Transactional
    public ReferenceDtos.NearExpiryResponse setNearExpiryDays(ReferenceDtos.NearExpiryRequest request) {
        SystemSetting setting = settingRepository.findByKey(NEAR_EXPIRY_DAYS).orElseGet(() -> SystemSetting.builder().key(NEAR_EXPIRY_DAYS).build());
        setting.setValue(Integer.toString(request.days()));
        settingRepository.save(setting);
        return new ReferenceDtos.NearExpiryResponse(request.days());
    }
}

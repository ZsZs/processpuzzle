package com.processpuzzle.baseentity.unit.adapters.inbound;

import com.processpuzzle.baseentity.model.OrganizationUnitInput;
import com.processpuzzle.baseentity.model.OrganizationUnitStatus;
import com.processpuzzle.baseentity.model.Page;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.UnitStatus;
import com.processpuzzle.core.quantity.Decimals;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

/** Between the contract's unit and the domain's. Decimals travel as text and are read here, so a malformed one is a violation, not a 500. */
@Component
class OrganizationUnitMapper {

    OrganizationUnit toDomain(OrganizationUnitInput input) {
        OrganizationUnit unit = new OrganizationUnit();
        unit.setCode(input.getCode());
        unit.setSymbol(input.getSymbol());
        unit.setName(input.getName());
        unit.setKind(input.getKind());
        unit.setFactor(OrganizationUnit.decimalOrNull(input.getFactor()));
        unit.setDivisor(input.getDivisor() == null ? BigDecimal.ONE : OrganizationUnit.decimalOrNull(input.getDivisor()));
        unit.setStatus(input.getStatus() == null ? UnitStatus.ACTIVE : UnitStatus.valueOf(input.getStatus().name()));
        unit.setVersion(input.getVersion() == null ? null : input.getVersion().longValue());
        return unit;
    }

    com.processpuzzle.baseentity.model.OrganizationUnit toModel(OrganizationUnit unit) {
        return new com.processpuzzle.baseentity.model.OrganizationUnit()
            .id(unit.getId())
            .code(unit.getCode())
            .symbol(unit.getSymbol())
            .name(unit.getName())
            .kind(unit.getKind())
            .factor(Decimals.text(unit.getFactor()))
            .divisor(Decimals.text(unit.getDivisor()))
            .status(OrganizationUnitStatus.valueOf(unit.getStatus().name()))
            .version(unit.getVersion() == null ? null : unit.getVersion().intValue())
            .createdAt(unit.getCreatedAt() == null ? null : OffsetDateTime.ofInstant(unit.getCreatedAt(), ZoneOffset.UTC))
            .createdBy(unit.getCreatedBy())
            .updatedAt(unit.getUpdatedAt() == null ? null : OffsetDateTime.ofInstant(unit.getUpdatedAt(), ZoneOffset.UTC))
            .updatedBy(unit.getUpdatedBy());
    }

    Page toPage(org.springframework.data.domain.Page<OrganizationUnit> page) {
        List<Object> content = page.getContent().stream().map(this::toModel).map(Object.class::cast).toList();
        return new Page()
            .content(content)
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements((int) page.getTotalElements())
            .totalPages(page.getTotalPages());
    }
}

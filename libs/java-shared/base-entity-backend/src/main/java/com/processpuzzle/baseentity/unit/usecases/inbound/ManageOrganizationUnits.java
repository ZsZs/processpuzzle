package com.processpuzzle.baseentity.unit.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.baseentity.unit.OrganizationUnitCatalogs;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnit;
import com.processpuzzle.baseentity.unit.domain.OrganizationUnitRepository;
import com.processpuzzle.baseentity.unit.domain.UnitStatus;
import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds, reads, renames and retires the units an organization adds to the standard catalog. Reading takes access to the
 * organization (every form that shows a quantity needs the catalog); changing takes the right to design.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class ManageOrganizationUnits {

    private final OrganizationUnitRepository repository;
    private final OrganizationUnitCatalogs catalogs;
    private final OrganizationGuard guard;

    public OrganizationUnit create(String orgKey, OrganizationUnit unit) {
        guard.requireDesign(orgKey);
        unit.setOrgKey(orgKey);
        List<Violation> violations = unit.violations();
        UnitCatalog standard = UnitCatalog.standard();
        if (unit.getKind() != null && standard.kinds().stream().noneMatch(kind -> kind.code().equals(unit.getKind()))) {
            violations = new ArrayList<>(violations);
            violations.add(new Violation("kind", "'%s' is not a quantity kind of the standard catalog".formatted(unit.getKind())));
        }
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        if (unit.getCode() != null && standard.findUnit(unit.getCode()).isPresent()) {
            throw new ConflictException("'%s' is a unit of the standard catalog".formatted(unit.getCode()));
        }
        if (repository.existsByOrgKeyAndCode(orgKey, unit.getCode())) {
            throw new ConflictException("Unit '%s' already exists".formatted(unit.getCode()));
        }
        OrganizationUnit saved = repository.save(unit);
        catalogs.evict(orgKey);
        return saved;
    }

    /** Takes the renaming and the status of {@code incoming}; refuses a change of anything a stored value depends on. */
    public OrganizationUnit replace(String orgKey, String code, OrganizationUnit incoming) {
        guard.requireDesign(orgKey);
        OrganizationUnit unit = repository.findByOrgKeyAndCode(orgKey, code)
            .orElseThrow(() -> new NotFoundException("Unit '%s' not found".formatted(code)));
        List<Violation> violations = incoming.violations();
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
        if (incoming.getVersion() != null && !incoming.getVersion().equals(unit.getVersion())) {
            throw new ConflictException("Unit '%s' was changed by someone else".formatted(code));
        }
        requireUnchanged(unit, incoming);
        unit.setSymbol(incoming.getSymbol());
        unit.setName(incoming.getName());
        unit.setStatus(incoming.getStatus() == null ? UnitStatus.ACTIVE : incoming.getStatus());
        OrganizationUnit saved = repository.save(unit);
        catalogs.evict(orgKey);
        return saved;
    }

    @Transactional(readOnly = true)
    public OrganizationUnit find(String orgKey, String code) {
        guard.requireAccess(orgKey);
        return repository.findByOrgKeyAndCode(orgKey, code).orElseThrow(() -> new NotFoundException("Unit '%s' not found".formatted(code)));
    }

    @Transactional(readOnly = true)
    public Page<OrganizationUnit> list(String orgKey, UnitStatus status, Pageable pageable) {
        guard.requireAccess(orgKey);
        return status == null ? repository.findAllByOrgKey(orgKey, pageable) : repository.findAllByOrgKeyAndStatus(orgKey, status, pageable);
    }

    private static void requireUnchanged(OrganizationUnit unit, OrganizationUnit incoming) {
        if (!unit.getCode().equals(incoming.getCode())) {
            throw new ConflictException("The code of unit '%s' cannot change".formatted(unit.getCode()));
        }
        if (!unit.getKind().equals(incoming.getKind()) || unit.getFactor().compareTo(incoming.getFactor()) != 0
            || unit.getDivisor().compareTo(incoming.getDivisor()) != 0) {
            throw new ConflictException(("The kind, factor and divisor of unit '%s' cannot change: stored amounts are derived from them. "
                + "Retire it and add another unit.").formatted(unit.getCode()));
        }
    }
}

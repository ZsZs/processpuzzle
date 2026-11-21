package com.processpuzzle.baseentity.unit.adapters.inbound;

import com.processpuzzle.baseentity.api.BaseEntityUnitsApi;
import com.processpuzzle.baseentity.model.OrganizationUnit;
import com.processpuzzle.baseentity.model.OrganizationUnitInput;
import com.processpuzzle.baseentity.model.OrganizationUnitStatus;
import com.processpuzzle.baseentity.model.Page;
import com.processpuzzle.baseentity.unit.domain.UnitStatus;
import com.processpuzzle.baseentity.unit.usecases.inbound.ManageOrganizationUnits;
import com.processpuzzle.core.logging.LogClass;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Inbound REST adapter of the units an organization adds, implementing the generated {@link BaseEntityUnitsApi}. */
@RestController
@LogClass
@RequiredArgsConstructor
public class OrganizationUnitEndpoint implements BaseEntityUnitsApi {

    private final ManageOrganizationUnits units;
    private final OrganizationUnitMapper mapper;

    @Override
    public ResponseEntity<Page> listOrganizationUnits(String orgKey, OrganizationUnitStatus status, Integer page, Integer size) {
        UnitStatus domainStatus = status == null ? null : UnitStatus.valueOf(status.name());
        return ResponseEntity.ok(mapper.toPage(units.list(orgKey, domainStatus, PageRequest.of(page != null ? page : 0, size != null ? size : 20))));
    }

    @Override
    public ResponseEntity<OrganizationUnit> createOrganizationUnit(String orgKey, OrganizationUnitInput input) {
        var created = units.create(orgKey, mapper.toDomain(input));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{code}").buildAndExpand(created.getCode()).toUri();
        return ResponseEntity.created(location).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<OrganizationUnit> getOrganizationUnit(String orgKey, String code) {
        return ResponseEntity.ok(mapper.toModel(units.find(orgKey, code)));
    }

    @Override
    public ResponseEntity<OrganizationUnit> replaceOrganizationUnit(String orgKey, String code, OrganizationUnitInput input) {
        return ResponseEntity.ok(mapper.toModel(units.replace(orgKey, code, mapper.toDomain(input))));
    }
}

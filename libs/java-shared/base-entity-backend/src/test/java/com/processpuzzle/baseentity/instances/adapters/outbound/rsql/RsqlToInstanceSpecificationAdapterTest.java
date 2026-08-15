package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RsqlToInstanceSpecificationAdapterTest {

    @Mock
    private EntityDefinitionLookupPort lookupPort;

    private RsqlToInstanceSpecificationAdapter adapter;

    @BeforeEach
    void setUp() {
        AttributePathResolver resolver = new AttributePathResolver(lookupPort);
        adapter = new RsqlToInstanceSpecificationAdapter(resolver);
    }

    @Test
    void toSpecification_nullOrBlankRsql_returnsConjunctionSpecification() {
        Specification<EntityObject> specNull = adapter.toSpecification(null, "partner");
        assertThat(specNull).isNotNull();

        Specification<EntityObject> specBlank = adapter.toSpecification("   ", "partner");
        assertThat(specBlank).isNotNull();
    }

    @Test
    void toSpecification_validRsql_buildsSpecification() {
        Specification<EntityObject> spec = adapter.toSpecification("name==ACME", "partner");
        assertThat(spec).isNotNull();
    }
}

package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RsqlToInstanceSpecificationAdapterTest {

    @Mock
    private EntityDefinitionLookupPort lookupPort;

    @Mock
    private Root<EntityObject> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

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

        Predicate conjunctionMock = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunctionMock);
        Predicate pred = specNull.toPredicate(root, query, cb);
        assertThat(pred).isSameAs(conjunctionMock);

        Specification<EntityObject> specBlank = adapter.toSpecification("   ", "partner");
        assertThat(specBlank).isNotNull();
        Predicate predBlank = specBlank.toPredicate(root, query, cb);
        assertThat(predBlank).isSameAs(conjunctionMock);
    }

    @Test
    @SuppressWarnings("unchecked")
    void toSpecification_validRsql_buildsAndExecutesPredicate() {
        EntityDefinitionView def = new EntityDefinitionView(
                "partner",
                false,
                List.of(new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, false))
        );
        when(lookupPort.findByCode("partner")).thenReturn(Optional.of(def));

        Path<Object> payloadPath = mock(Path.class);
        when(root.get("payload")).thenReturn(payloadPath);
        Expression<String> literalExpr = mock(Expression.class);
        when(cb.literal(any(String.class))).thenReturn(literalExpr);

        Expression<Object> buildObjectExpr = mock(Expression.class);
        when(cb.function(eq("jsonb_build_object"), eq(Object.class), any(Expression[].class)))
                .thenReturn(buildObjectExpr);

        Expression<Boolean> pathExistsExpr = mock(Expression.class);
        when(cb.function(eq("jsonb_path_exists"), eq(Boolean.class), eq(payloadPath), eq(literalExpr), eq(buildObjectExpr)))
                .thenReturn(pathExistsExpr);

        Predicate isTruePredicate = mock(Predicate.class);
        when(cb.isTrue(pathExistsExpr)).thenReturn(isTruePredicate);

        Specification<EntityObject> spec = adapter.toSpecification("name==ACME", "partner");
        assertThat(spec).isNotNull();

        Predicate result = spec.toPredicate(root, query, cb);
        assertThat(result).isSameAs(isTruePredicate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void toSpecification_andAndOrNodes_buildsCombinedPredicates() {
        EntityDefinitionView def = new EntityDefinitionView(
                "partner",
                false,
                List.of(
                        new EntityAttributeView("name", ValueKindView.TEXT, false, false, null, false),
                        new EntityAttributeView("status", ValueKindView.TEXT, false, false, null, false)
                )
        );
        when(lookupPort.findByCode("partner")).thenReturn(Optional.of(def));

        Path<Object> payloadPath = mock(Path.class);
        when(root.get("payload")).thenReturn(payloadPath);
        Expression<String> literalExpr = mock(Expression.class);
        when(cb.literal(any(String.class))).thenReturn(literalExpr);

        Expression<Object> buildObjectExpr = mock(Expression.class);
        when(cb.function(eq("jsonb_build_object"), eq(Object.class), any(Expression[].class)))
                .thenReturn(buildObjectExpr);

        Expression<Boolean> pathExistsExpr = mock(Expression.class);
        when(cb.function(eq("jsonb_path_exists"), eq(Boolean.class), eq(payloadPath), eq(literalExpr), eq(buildObjectExpr)))
                .thenReturn(pathExistsExpr);

        Predicate isTruePredicate = mock(Predicate.class);
        when(cb.isTrue(pathExistsExpr)).thenReturn(isTruePredicate);

        Predicate andPredicate = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(andPredicate);

        Specification<EntityObject> andSpec = adapter.toSpecification("name==ACME;status==ACTIVE", "partner");
        Predicate andResult = andSpec.toPredicate(root, query, cb);
        assertThat(andResult).isSameAs(andPredicate);

        Predicate orPredicate = mock(Predicate.class);
        when(cb.or(any(Predicate[].class))).thenReturn(orPredicate);

        Specification<EntityObject> orSpec = adapter.toSpecification("name==ACME,status==ACTIVE", "partner");
        Predicate orResult = orSpec.toPredicate(root, query, cb);
        assertThat(orResult).isSameAs(orPredicate);
    }
}

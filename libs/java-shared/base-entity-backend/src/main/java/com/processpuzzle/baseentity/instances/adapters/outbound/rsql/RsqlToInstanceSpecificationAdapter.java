package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.AndNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.Node;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * PROVISIONAL — see RsqlToInstanceSpecificationPort. This class re-implements RSQL parsing
 * + AND/OR composition from scratch (RSQLParser + a hand-rolled RSQLVisitor) because
 * processpuzzle-core's RsqlSpecificationBuilder wasn't available to compose against at the time
 * this was written. It almost certainly duplicates parsing/composition logic that already exists
 * there. Once its API is confirmed, this class should be rewritten to extend/wrap it — only the
 * genuinely novel part, {@code jsonb_path_exists}-based JSONB predicate construction for a single
 * ComparisonNode, should remain here.
 * <p>
 * Every RSQL comparison, however deep or however many embedded-component arrays it crosses,
 * resolves to one {@code jsonb_path_exists(payload, path, vars)} predicate — no correlated
 * subqueries or extra joins even when the path crosses an embedded array.
 */
@Component
@RequiredArgsConstructor
public class RsqlToInstanceSpecificationAdapter implements RsqlToInstanceSpecificationPort {

    private final AttributePathResolver pathResolver;

    @Override
    public Specification<EntityObject> toSpecification(String rsql, String rootEntityDefinitionCode) {
        if (rsql == null || rsql.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        Node rootNode = new RSQLParser().parse(rsql);
        return (root, query, cb) -> rootNode.accept(new PredicateVisitor(root, cb, rootEntityDefinitionCode), null);
    }

    @RequiredArgsConstructor
    private class PredicateVisitor implements cz.jirutka.rsql.parser.ast.RSQLVisitor<Predicate, Void> {

        private final Root<EntityObject> root;
        private final CriteriaBuilder cb;
        private final String rootEntityDefinitionCode;

        @Override
        public Predicate visit(AndNode node, Void ctx) {
            return cb.and(node.getChildren().stream().map(child -> child.accept(this, ctx)).toArray(Predicate[]::new));
        }

        @Override
        public Predicate visit(cz.jirutka.rsql.parser.ast.OrNode node, Void ctx) {
            return cb.or(node.getChildren().stream().map(child -> child.accept(this, ctx)).toArray(Predicate[]::new));
        }

        @Override
        public Predicate visit(ComparisonNode node, Void ctx) {
            ResolvedAttributePath path = pathResolver.resolve(rootEntityDefinitionCode, node.getSelector());

            String jsonPath = JsonPathExpressionBuilder.buildPath(path, node.getOperator(), node.getArguments());
            Expression<Object> vars = JsonPathExpressionBuilder.buildVars(cb, path.valueKind(), node.getArguments());

            Expression<Boolean> matches = cb.function(
                "jsonb_path_exists", Boolean.class,
                root.get("payload"), cb.literal(jsonPath), vars);

            return cb.isTrue(matches);
        }
    }
}

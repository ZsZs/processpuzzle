package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.domain.RecordPlanner;
import com.processpuzzle.baseentity.instances.domain.RecordPlanner.RecordInput;
import com.processpuzzle.baseentity.instances.domain.RecordPlanner.Ref;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads a batch of records into an organization, additively: a record whose natural key a stored record already has is
 * left as it is and reported as already present, and the rest are created. Nothing is updated or removed, so a later
 * file adds to what an earlier one loaded.
 *
 * <p>The batch is planned first ({@link RecordPlanner}), so a record comes after the records it refers to. A record
 * is refused, and the records that refer to it with it, when its definition does not exist, a reference matches no
 * record, or the record itself is refused by validation or by a natural key. Each record is created through
 * {@link CreateEntityInstanceUseCase}, so the payload is validated and the rules and events apply as for any other
 * write. A dry run is the same load rolled back.
 */
@Component
public class LoadRecordsUseCase {

    /** What happened to one record of the batch. */
    public enum Action { CREATED, ALREADY_PRESENT, REJECTED }

    /**
     * @param index  the record's position in the batch
     * @param code   the definition the record is of
     * @param id     the stored record's id, for CREATED and ALREADY_PRESENT; null otherwise
     * @param detail why it was refused, or which stored record it matches; empty for CREATED
     */
    public record Item(int index, String code, Action action, String id, String detail) {
    }

    /** @param dryRun whether nothing was written */
    public record Report(boolean dryRun, List<Item> items) {

        public long count(Action action) {
            return items.stream().filter(item -> item.action() == action).count();
        }

        public boolean hasRejected() {
            return count(Action.REJECTED) > 0;
        }
    }

    private final EntityObjectScope scope;
    private final EntityDefinitionLookupPort definitions;
    private final CreateEntityInstanceUseCase create;
    private final TransactionTemplate transactions;

    public LoadRecordsUseCase(EntityObjectScope scope, EntityDefinitionLookupPort definitions,
                              CreateEntityInstanceUseCase create, PlatformTransactionManager transactionManager) {
        this.scope = scope;
        this.definitions = definitions;
        this.create = create;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Loads the batch. Records refused on their own do not stop the others: the report says what happened to each.
     *
     * @param dryRun when true, the report says what would happen and nothing is kept
     */
    public Report load(String orgKey, List<RecordInput> records, boolean dryRun) {
        return transactions.execute(status -> {
            Report report = apply(orgKey, records, dryRun);
            if (dryRun) {
                status.setRollbackOnly();
            }
            return report;
        });
    }

    private Report apply(String orgKey, List<RecordInput> records, boolean dryRun) {
        RecordPlanner.Ordering ordering = RecordPlanner.order(records);
        Map<Integer, String> ids = new HashMap<>();
        Set<Integer> refused = new HashSet<>();
        Item[] items = new Item[records.size()];
        for (int index : ordering.order()) {
            Item item = loadOne(orgKey, records, index, ordering.cannotLoad().get(index), ids, refused);
            if (item.action() == Action.REJECTED) {
                refused.add(index);
            }
            items[index] = item;
        }
        return new Report(dryRun, List.of(items));
    }

    private Item loadOne(String orgKey, List<RecordInput> records, int index, String cannotLoad,
                         Map<Integer, String> ids, Set<Integer> refused) {
        RecordInput input = records.get(index);
        if (cannotLoad != null) {
            return rejected(index, input.code(), cannotLoad);
        }
        Optional<EntityDefinitionView> definition = definitions.findByCode(orgKey, input.code());
        if (definition.isEmpty()) {
            return rejected(index, input.code(), "there is no entity definition '" + input.code() + "'");
        }
        try {
            Map<String, Object> payload = RecordPlanner.resolve(input.payload(), ref ->
                    resolve(orgKey, records, ref, ids, refused));
            Optional<Item> present = storedDuplicate(orgKey, index, definition.get(), payload);
            if (present.isPresent()) {
                ids.put(index, present.get().id());
                return present.get();
            }
            EntityObject created = create.create(orgKey, input.code(), payload);
            ids.put(index, created.getId().toString());
            return new Item(index, input.code(), Action.CREATED, created.getId().toString(), "");
        } catch (Refusal | IllegalArgumentException | ValidationException | ConflictException e) {
            return rejected(index, input.code(), e.getMessage());
        }
    }

    /** The id a reference resolves to: a record of this batch, already loaded, or a stored one. */
    private String resolve(String orgKey, List<RecordInput> records, Ref ref, Map<Integer, String> ids, Set<Integer> refused) {
        Optional<Integer> target = RecordPlanner.findTarget(records, ref);
        if (target.isPresent()) {
            if (refused.contains(target.get()) || !ids.containsKey(target.get())) {
                throw new Refusal("refers to a '" + ref.entity() + "' record of this file that was not loaded");
            }
            return ids.get(target.get());
        }
        return scope.findAll(orgKey, ref.entity()).stream()
                .filter(stored -> !stored.isRetired() && RecordPlanner.matches(stored.getPayload(), ref))
                .findFirst()
                .map(stored -> stored.getId().toString())
                .orElseThrow(() -> new Refusal("refers to a '" + ref.entity() + "' that does not exist"));
    }

    /** The stored record that already has one of the definition's natural keys, if any. */
    private Optional<Item> storedDuplicate(String orgKey, int index, EntityDefinitionView definition, Map<String, Object> payload) {
        List<EntityObject> stored = scope.findAll(orgKey, definition.code());
        for (List<String> key : definition.identifiers()) {
            for (EntityObject other : stored) {
                if (!other.isRetired() && RecordPlanner.sameKey(other.getPayload(), payload, key)) {
                    return Optional.of(new Item(index, definition.code(), Action.ALREADY_PRESENT,
                            other.getId().toString(), "a stored '" + definition.code() + "' has the same " + String.join(", ", key)));
                }
            }
        }
        return Optional.empty();
    }

    private static Item rejected(int index, String code, String reason) {
        return new Item(index, code, Action.REJECTED, null, reason);
    }

    /** A record that cannot be written because of its references; the message says why. */
    private static final class Refusal extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Refusal(String message) {
            super(message);
        }
    }
}

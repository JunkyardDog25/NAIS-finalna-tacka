package com.nais.finalna_tacka.saga;

import org.springframework.stereotype.Component;

/**
 * Coordinates writes that must stay consistent across MongoDB and Neo4j.
 *
 * <p>The two stores share the same id values for the same entity (see the domain
 * classes), so this saga is where create/update/delete operations are applied to both
 * stores and compensated if one side fails.</p>
 *
 * <p>TODO (teammates): implement the saga steps and compensations here, e.g.:</p>
 * <ul>
 *     <li>TODO: createSong(...) -> write to Mongo, then mirror to Neo4j; compensate by
 *         deleting the Mongo document if the Neo4j write fails.</li>
 *     <li>TODO: define a SagaStep abstraction (action + compensation) if the flows grow.</li>
 *     <li>TODO: decide on transactionality (Mongo multi-document tx via the rs0 replica
 *         set + Neo4j tx) and the ordering/compensation strategy.</li>
 * </ul>
 */
@Component
public class SyncSaga {

    // TODO (teammates): inject the mongo + graph repositories and implement saga flows.
}

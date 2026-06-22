package com.nais.finalna_tacka.repository.mongo;

import com.nais.finalna_tacka.saga.state.SagaState;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Persistence for {@link SagaState}, keyed by sagaId. */
public interface SagaStateRepository extends MongoRepository<SagaState, String> {
}

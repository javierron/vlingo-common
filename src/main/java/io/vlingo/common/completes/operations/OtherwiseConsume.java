// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.common.completes.operations;

import java.util.function.Consumer;

import io.vlingo.common.completes.Operation;
import io.vlingo.common.completes.exceptions.FailedOperationException;

public class OtherwiseConsume<Receives> extends Operation<Receives, Receives> {
    private final Consumer<Receives> consumer;

    public OtherwiseConsume(Consumer<Receives> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onOutcome(Receives receives) {
        emitOutcome(receives);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onError(Exception cause) {
        if (cause instanceof FailedOperationException) {
            Object failureValue = ((FailedOperationException) cause).failureValue;
            consumer.accept((Receives) failureValue);
        }

        emitError(cause);
    }
}

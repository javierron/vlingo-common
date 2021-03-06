// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.common;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class RepeatableCompletes<T> extends BasicCompletes<T> {

  public RepeatableCompletes(final Scheduler scheduler) {
    super(new RepeatableActiveState<T>(scheduler));
  }

  public RepeatableCompletes(final T outcome, final boolean succeeded) {
    super(new RepeatableActiveState<T>(), outcome, succeeded);
  }

  public RepeatableCompletes(final T outcome) {
    super(new RepeatableActiveState<T>(), outcome);
  }

  @Override
  public Completes<T> repeat() {
    if (state.isOutcomeKnown()) {
      state.repeat();
    }
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <O> Completes<O> with(O outcome) {
    super.with(outcome);
    state.repeat();
    return (Completes<O>) this;
  }

  protected static class RepeatableActiveState<T> extends BasicActiveState<T> {
    private final Queue<Action<T>> actionsBackup;
    private final AtomicBoolean repeating;

    protected RepeatableActiveState(final Scheduler scheduler) {
      super(scheduler);
      this.actionsBackup = new ConcurrentLinkedQueue<>();
      this.repeating = new AtomicBoolean(false);
    }

    protected RepeatableActiveState() {
      this(null);
    }

    @Override
    public void backUp(final Action<T> action) {
      if (action != null) {
        actionsBackup.add(action);
      }
    }

    @Override
    public void repeat() {
      if (repeating.compareAndSet(false, true)) {
        restore();
        outcomeKnown(false);
        repeating.set(false);
      }
    }

    @Override
    public void restore() {
      while (actionsBackup.peek() != null) {
        restore(actionsBackup.poll());
      }
    }
  }
}

// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.common;

import java.util.function.Consumer;
import java.util.function.Function;

import io.vlingo.common.completes.SinkAndSourceBasedCompletes;
import io.vlingo.common.completes.exceptions.FailedOperationException;

/**
 * {@code Completes<T>} models the eventual completion of an asynchronous operation
 * that has an answer (return value) of a specific type. You will find this
 * similar to {@code Future} and {@code CompletableFuture} from the Java platform. Yet,
 * {@code Completes<T>} is designed for non-blocking and is typically used in
 * that manner. There is a means to explicitly block on the outcome but
 * this is used only for testing.
 *
 * <p>
 * Use {@code Completes<T>} beyond handling the eventual outcome of asynchronous outcomes.
 * For example, use a pipeline of multiple {@code Completes<T>} when you must send a number
 * of asynchronous messages for multiple outcomes, but each outcome must precede the next.
 * Thus, while each message send is entirely asynchronous, {@code Completes<T>} manages the
 * sequencing of each message and its outcome with the possibility of each outcome being used
 * for the next message send. {@code Completes<T>} is a monad.
 *
 * <code>
 * reservations
 *    .reserveTravel(ticketingDetails)
 *    .andThenTo(reservation -&gt; booking.record(reservation))
 *    .andThenTo(bookingRecord -&gt; traveler.bookFor(bookingRecord))
 *    .andThenTo(points -&gt; rewards.credit(points));
 * </code>
 *
 * <p>
 * Note that the various forms of {@code andThenTo()} pipeline using a nested {@code Completes<T>}
 * instance, making it possible for the registered {@code Function<T,O>} to send an asynchronous
 * message to an actor with without damaging the parent {@code Completes<T>}. Using {@code andThen()}
 * to send an asynchronous message to an actor will have undefined results.
 *
 * Note that the {@code Completes<T>} doesn't necessarily need to be executed when there is an outcome
 * if there is no subscription to it. Deciding when to run is implementation dependent, and is encouraged
 * to subscribe to the {@code Completes<T>} using the {@code andFinallyConsume} method.
 *
 * @param <T> the type that is expected as the outcome (return value)
 */
public interface Completes<T> {
  /**
   * Answer a new {@code Completes<T>} that uses the {@code scheduler}. The
   * instance has not completed at the time of creation.
   * @param scheduler the Scheduler to use for timeouts
   * @param <T> the T typed outcome of the Completes
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> using(final Scheduler scheduler) {
    if (SinkAndSourceBasedCompletes.isToggleActive()) {
      return SinkAndSourceBasedCompletes.withScheduler(scheduler);
    }

    return new BasicCompletes<T>(scheduler);
  }

  static <T> Completes<T> noTimeout() {
    return new BasicCompletes<T>((Scheduler) null);
  }

  /**
   * Answer a new {@code Completes<T>} that has a successful {@code outcome}.
   * The instance has already completed at the time of creation,
   * which means that any function/accessor used to read the
   * successful {@code outcome} will be immediate.
   * @param outcome the T typed outcome answer (return value)
   * @param <T> the T typed outcome of the Completes
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> withSuccess(final T outcome) {
    if (SinkAndSourceBasedCompletes.isToggleActive()) {
      return SinkAndSourceBasedCompletes.withScheduler(new Scheduler()).with(outcome);
    }

    return new BasicCompletes<T>(outcome, true);
  }

  /**
   * Answer a new {@code Completes<T>} that has a failure {@code outcome}.
   * The instance has already completed at the time of creation,
   * which means that any function/accessor used to read the
   * failure {@code outcome} will be immediate.
   * @param outcome the T typed outcome answer (return value)
   * @param <T> the T typed outcome of the Completes
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> withFailure(final T outcome) {
    if (SinkAndSourceBasedCompletes.isToggleActive()) {
      SinkAndSourceBasedCompletes<T> completes = SinkAndSourceBasedCompletes
              .withScheduler(new Scheduler());

      completes.source.emitError(new FailedOperationException(outcome));
      return completes;
    }

    return new BasicCompletes<T>(outcome, false);
  }

  /**
   * Answer a new {@code Completes<T>} that has a failure outcome of {@code null}.
   * The instance has already completed at the time of creation,
   * which means that any function/accessor used to read the
   * failure {@code outcome} will be immediate.
   * @param <T> the T typed outcome of the Completes
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> withFailure() {
    return withFailure(null);
  }

  /**
   * Answer a new {@code Completes<T>} that is repeatable, meaning that
   * following a given completion the instance may be reused to
   * achieve one or more subsequent eventual outcomes. To do so
   * use {@link Completes#repeat()} to prepare for the next outcome.
   * The instance has not completed at the time of creation.
   * @param scheduler the Scheduler to use for timeouts
   * @param <T> the T typed outcome of the Completes
   * @see Completes#repeat()
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> repeatableUsing(final Scheduler scheduler) {
    if (SinkAndSourceBasedCompletes.isToggleActive()) {
      return SinkAndSourceBasedCompletes.withScheduler(scheduler);
    }

    return new RepeatableCompletes<T>(scheduler);
  }

  /**
   * Answer a new {@code Completes<T>} that is repeatable, meaning that
   * following a given completion the instance may be reused to
   * achieve one or more subsequent eventual outcomes. To do so
   * use {@link Completes#repeat()} to prepare for the next outcome.
   * The instance has already completed successfully at the time of creation.
   * @param outcome the T typed outcome answer (return value) of {@code null}
   * @param <T> the T typed outcome of the Completes
   * @see Completes#repeat()
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> repeatableWithSuccess(final T outcome) {
    if (SinkAndSourceBasedCompletes.isToggleActive()) {
      return SinkAndSourceBasedCompletes.withScheduler(new Scheduler()).with(outcome);
    }

    return new RepeatableCompletes<T>(outcome, true);
  }

  /**
   * Answer a new {@code Completes<T>} that is repeatable, meaning that
   * following a given completion the instance may be reused to
   * achieve one or more subsequent eventual outcomes. To do so
   * use {@link Completes#repeat()} to prepare for the next outcome.
   * The instance has already completed with failure at the time of creation.
   * @param outcome the T typed outcome answer (return value) of {@code null}
   * @param <T> the T typed outcome of the Completes
   * @see Completes#repeat()
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> repeatableWithFailure(final T outcome) {
    if (SinkAndSourceBasedCompletes.isToggleActive()) {
      Completes<T> failure = SinkAndSourceBasedCompletes.withScheduler(new Scheduler()).with(outcome);
      failure.failed();
      return failure;
    }

    return new RepeatableCompletes<T>(outcome, false);
  }

  /**
   * Answer a new {@code Completes<T>} that is repeatable, meaning that
   * following a given completion the instance may be reused to
   * achieve one or more subsequent eventual outcomes. To do so
   * use {@link Completes#repeat()} to prepare for the next outcome.
   * The instance has already completed with failure of {@code null} at the
   * time of creation.
   * @param <T> the T typed outcome of the Completes
   * @see Completes#repeat()
   * @return {@code Completes<T>}
   */
  static <T> Completes<T> repeatableWithFailure() {
    return repeatableWithFailure(null);
  }

  /**
   * Inverts an {@code Outcome} of {@code Completes}
   * to a {@code Completes} of {@code Outcome}.
   *
   * @param outcome the {@code Outcome<E, Completes<A>>} that will be inverted
   * @param <E> the type of the Failure
   * @param <A> the type of the Success
   * @return {@code Completes<Outcome<E, A>>}
   */
  static <E extends Throwable, A> Completes<Outcome<E, A>> invert(final Outcome<E, Completes<A>> outcome) {
    return outcome.resolve(
        e -> Completes.withSuccess(Failure.of(e)),
        s -> s.andThen(Success::of));
  }

  /**
   * Answer the {@code Completes<O>} instance after registering the {@code function} to be used to
   * apply the value of type {@code O} when it is available, along with the {@code timeout} and
   * {@code failedOutcomeValue}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that you must not use any form of {@code andThen()} to send a message to an actor. See
   * {@link Completes#andThenTo(long, Object, Function)} for that.
   * @param timeout the long number of milliseconds until this {@code Completes<O>} is considered timed out
   * @param failedOutcomeValue the O typed value answered in the case of failure, including for timeout
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the {@code Completes<O>} of the next operation
   * @param <O> the O typed outcome of the Completes, which may be different from T
   * @return {@code Completes<O>}
   */
  <O> Completes<O> andThen(final long timeout, final O failedOutcomeValue, final Function<T,O> function);

  /**
   * Answer the {@code Completes<O>} instance after registering the {@code function} to be used to
   * apply the value of type {@code O} when it is available, along with the {@code failedOutcomeValue}.
   * There is no timeout specified in this operation, meaning that the outcome could be
   * infinite. Note that failure is different from exceptional outcomes, which are handled by
   * {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that you must not use any form of {@code andThen()} to send a message to an actor. See
   * {@link Completes#andThenTo(Object, Function)} for that.
   * @param failedOutcomeValue the O typed value answered in the case of failure
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the {@code Completes<O>} of the next operation
   * @param <O> the O typed outcome of the Completes, which may be different from T
   * @return {@code Completes<O>}
   */
  <O> Completes<O> andThen(final O failedOutcomeValue, final Function<T,O> function);

  /**
   * Answer the {@code Completes<O>} instance after registering the {@code function} to be used to
   * apply the value of type {@code O} when it is available, along with the {@code timeout} and a
   * failed outcome value of {@code null}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that you must not use any form of {@code andThen()} to send a message to an actor. See
   * {@link Completes#andThenTo(long, Function)} for that.
   * @param timeout the long number of milliseconds until this {@code Completes<O>} is considered timed out
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the {@code Completes<O>} of the next operation
   * @param <O> the O typed outcome of the Completes, which may be different from T
   * @return {@code Completes<O>}
   */
  <O> Completes<O> andThen(final long timeout, final Function<T,O> function);

  /**
   * Answer the {@code Completes<O>} instance after registering the {@code function} to be used to
   * apply the value of type {@code O} when it is available. There is no {@code timeout} and a
   * failed outcome value of {@code null}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that you must not use any form of {@code andThen()} to send a message to an actor. See
   * {@link Completes#andThenTo(Function)} for that.
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the {@code Completes<O>} of the next operation
   * @param <O> the O typed outcome of the Completes, which may be different from T
   * @return {@code Completes<O>}
   */
  <O> Completes<O> andThen(final Function<T,O> function);

  /**
   * Answer the {@code Completes<T>} instance after registering the {@code consumer} to be used to
   * accept the value of type {@code T} when it is available, along with the {@code timeout} and
   * {@code failedOutcomeValue}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * @param timeout the long number of milliseconds until this {@code Completes<O>} is considered timed out
   * @param failedOutcomeValue the T typed value answered in the case of failure, including for timeout
   * @param consumer the {@code Consumer<T>} to receive the {@code Completes<T>} of the async operation
   * @return {@code Completes<T>}
   */
  Completes<T> andThenConsume(final long timeout, final T failedOutcomeValue, final Consumer<T> consumer);

  /**
   * Answer the {@code Completes<T>} instance after registering the {@code consumer} to be used to
   * accept the value of type {@code T} when it is available, along with the {@code failedOutcomeValue}.
   * There is no timeout specified in this operation, meaning that the outcome could be
   * infinite. Note that failure is different from exceptional outcomes, which are handled by
   * {@link Completes#recoverFrom(Function)}.
   * @param failedOutcomeValue the T typed value answered in the case of failure
   * @param consumer the {@code Consumer<T>} to receive the {@code Completes<T>} of the async operation
   * @return {@code Completes<O>}
   */
  Completes<T> andThenConsume(final T failedOutcomeValue, final Consumer<T> consumer);

  /**
   * Answer the {@code Completes<T>} instance after registering the {@code consumer} to be used to
   * accept the value of type {@code T} when it is available, along with the {@code timeout} and a
   * failed outcome value of {@code null}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * @param timeout the long number of milliseconds until this {@code Completes<T>} is considered timed out
   * @param consumer the {@code Consumer<T>} to receive the {@code Completes<T>} of the async operation
   * @return {@code Completes<T>}
   */
  Completes<T> andThenConsume(final long timeout, final Consumer<T> consumer);

  /**
   * Answer the {@code Completes<T>} instance after registering the {@code consumer} to be used to
   * accept the value of type {@code T} when it is available. There is no {@code timeout} and a
   * failed outcome value of {@code null}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * @param consumer the {@code Consumer<T>} to receive the {@code Completes<T>} of the async operation
   * @return {@code Completes<O>}
   */
  Completes<T> andThenConsume(final Consumer<T> consumer);

  /**
   * Answer the {@code O} instance after registering the {@code function} to be used to
   * apply the value of type {@code O} when it is available, along with the {@code timeout} and
   * {@code failedOutcomeValue}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that {@code andThenTo()} pipelines using a nested {@code Completes<T>} instance, making it possible
   * for the registered {@code Function<T,O>} to send an asynchronous message to an actor with without damaging
   * the parent {@code Completes<T>}. Using {@code andThen()} to send an asynchronous message to an actor will
   * have undefined results.
   * @param timeout the long number of milliseconds until this {@code Completes<T>} is considered timed out
   * @param failedOutcomeValue the F typed value answered in the case of failure, including for timeout
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the {@code O} outcome
   * @param <F> the F type of the failedOutcomeValue
   * @param <O> the O typed outcome, which may be different from T and may be a Completes
   * @return O
   */
  <F,O> O andThenTo(final long timeout, final F failedOutcomeValue, final Function<T,O> function);

  /**
   * Answer the {@code O} instance after registering the {@code function} to be used to
   * apply the value of type {@code T} when it is available, along with the {@code failedOutcomeValue}.
   * There is no timeout specified in this operation, meaning that the outcome could be
   * infinite. Note that failure is different from exceptional outcomes, which are handled by
   * {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that {@code andThenTo()} pipelines using a nested {@code Completes<T>} instance, making it possible
   * for the registered {@code Function<T,O>} to send an asynchronous message to an actor with without damaging
   * the parent {@code Completes<T>}. Using {@code andThen()} to send an asynchronous message to an actor will
   * have undefined results.
   * @param failedOutcomeValue the F typed value answered in the case of failure
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the {@code O} outcome
   * @param <F> the F type of the failedOutcomeValue
   * @param <O> the O typed outcome, which may be different from T and may be a Completes
   * @return O
   */
  <F,O> O andThenTo(final F failedOutcomeValue, final Function<T,O> function);

  /**
   * Answer the {@code O} instance after registering the {@code function} to be used to
   * apply the value of type {@code T} when it is available, along with the {@code timeout} and a
   * failed outcome value of {@code null}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that {@code andThenTo()} pipelines using a nested {@code Completes<T>} instance, making it possible
   * for the registered {@code Function<T,O>} to send an asynchronous message to an actor with without damaging
   * the parent {@code Completes<T>}. Using {@code andThen()} to send an asynchronous message to an actor will
   * have undefined results.
   * @param timeout the long number of milliseconds until this {@code Completes<T>} is considered timed out
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the {@code O} outcome
   * @param <O> the O typed outcome, which may be different from T and may be a Completes
   * @return O
   */
  <O> O andThenTo(final long timeout, final Function<T,O> function);

  /**
   * Answer the {@code O} instance after registering the {@code function} to be used to
   * apply the value of type {@code O} when it is available. There is no {@code timeout} and a
   * failed outcome value of {@code null}. Note that failure is different from exceptional outcomes,
   * which are handled by {@link Completes#recoverFrom(Function)}.
   * <p>
   * Note that {@code andThenTo()} pipelines using a nested {@code Completes<T>} instance, making it possible
   * for the registered {@code Function<T,O>} to send an asynchronous message to an actor with without damaging
   * the parent {@code Completes<T>}. Using {@code andThen()} to send an asynchronous message to an actor will
   * have undefined results.
   * @param function the {@code Function<T,O>} to receive the {@code Completes<T>} of the async operation and to produce the O of the next operation
   * @param <O> the O typed outcome, which may be different from T and may be a Completes
   * @return O
   */
  <O> O andThenTo(final Function<T,O> function);

  /**
   * Answer the {@code Completes<T>} after registering the {@code function} to be used to
   * apply the failure outcome if such occurs.
   * @param function the {@code Function<E,T>} to receive the {@code Completes<T>} of the async operation and to produce the {@code Completes<T>} of the next operation
   * @param <E> the expected error type
   * @return {@code Completes<T>}
   */
  <E> Completes<T> otherwise(final Function<E,T> function);

  /**
   * Answer the {@code Completes<T>} after registering the {@code consumer} to be used to
   * accept the failure outcome if such occurs.
   * @param consumer the {@code Consumer<T>} to receive the {@code Completes<T>} of the async operation and to produce the {@code Completes<T>} of the next operation
   * @return {@code Completes<T>}
   */
  Completes<T> otherwiseConsume(final Consumer<T> consumer);

  /**
   * Answer the {@code Completes<T>} after registering the {@code function} to be used to
   * apply the exceptional outcome if such occurs.
   * @param function the {@code Function<Exception,T>} to receive the {@code Completes<T>} of the async operation and to produce the {@code Completes<T>} of the next operation
   * @return {@code Completes<T>}
   */
  Completes<T> recoverFrom(final Function<Exception,T> function);

  /**
   * Answer the {@code O} outcome as the final implicit function in a pipeline.
   * Subscribes to the current {@code Completes<T>} and runs if the outcome is successful.
   * <p>
   * Note that you must have either {@code andFinally()} or {@code andFinallyConsume()} as
   * a terminator for your pipeline, or the {@code Completes<T>} will never run.
   *
   * @param <O> the O type of outcome from the function
   * @return {@code Completes<O>}
   */
  <O> Completes<O> andFinally();

  /**
   * Answer the {@code O} outcome as the final function in a pipeline.
   * Subscribes to the current {@code Completes<T>} and runs if the outcome is successful.
   * <p>
   * Note that you must have either {@code andFinally()} or {@code andFinallyConsume()} as
   * a terminator for your pipeline, or the {@code Completes<T>} will never run.
   *
   * @param function the {@code Function<T,O>} that will receive the successful value, if any.
   * @param <O> the O type of outcome from the function
   * @return {@code Completes<O>}
   */
  <O> Completes<O> andFinally(final Function<T,O> function);

  /**
   * Subscribes to the current {@code Completes<T>} and runs if the outcome is successful.
   * <p>
   * Note that you must have either {@code andFinally()} or {@code andFinallyConsume()} as
   * a terminator for your pipeline, or the {@code Completes<T>} will never run.
   *
   * @param consumer the {@code Consumer<T>} that will receive the successful value, if any.
   */
  void andFinallyConsume(final Consumer<T> consumer);

  /**
   * Answer the {@code O} outcome after blocking indefinitely for completion.
   * @param <O> the O type of outcome
   * @return O
   */
  <O> O await();

  /**
   * Answer the {@code O} outcome after blocking for a maximum of
   * {@code timeout} milliseconds for completion.
   * @param timeout the long maximum number of milliseconds to block for an outcome
   * @param <O> the O type of outcome
   * @return O
   */
  <O> O await(final long timeout);

  /**
   * Answer whether or not this {@code Completes<T>} has completed.
   * @return boolean
   */
  boolean isCompleted();

  /**
   * Answer whether or not this {@code Completes<T>} has failed.
   * @return boolean
   */
  boolean hasFailed();

  /**
   * Cause this {@code Completes<T>} to fail, unless it has already completed.
   */
  void failed();

  /**
   * Cause this {@code Completes<T>} to fail with {@code exception}, unless it has already completed.
   * @param exception the Exception the caused the failure
   */
  void failed(final Exception exception);

  /**
   * Answer whether or not this {@code Completes<T>} has an available outcome,
   * which will be true for either success or failure.
   * @return boolean
   */
  boolean hasOutcome();

  /**
   * Answer my {@code outcome}, which may be a unknown, success, or failure.
   * @return T
   */
  T outcome();

  /**
   * Answer myself after I am prepared to handle the next outcome. By default
   * this throws {@code UnsupportedOperationException}, but will provided intended
   * behavior and answer if the underlying type is {@code RepeatableCompletes}. Use
   * {@link Completes#repeatableUsing(Scheduler)} to create a valid instance.
   * @return {@code Completes<T>}
   */
  Completes<T> repeat();

  /**
   * Answer myself after registering the {@code timeout}.
   * <p>
   * WARNING: If you use this method along with {@code useFailedOutcomeOf(F)}, you must
   * use this one after to register the {@code timeout} threshold. Otherwise the timeout
   * may occur prior to knowing the proper {@code failedOutcomeValue} to set.
   * @param timeout the long number of milliseconds until this {@code Completes<T>} is considered timed out
   * @return {@code Completes<T>}
   */
  Completes<T> timeoutWithin(final long timeout);

  /**
   * Answer myself after registering the {@code failedOutcomeValue}.
   * <p>
   * WARNING: If you use this method along with {@code timeoutWithin(long)}, you must
   * use this one first to register the {@code failedOutcomeValue}. Otherwise the timeout
   * may occur prior to knowing the proper {@code failedOutcomeValue} to set.
   * @param failedOutcomeValue the F outcome to use when a failure occurs
   * @param <F> the type of the failedOutcomeValue
   * @return {@code Completes<T>}
   */
  <F> Completes<T> useFailedOutcomeOf(final F failedOutcomeValue);

  /**
   * Answer myself after setting my {@code outcome}. This should normally be used only
   * by internal operations or when available through an actor for its results.
   * @param outcome the O typed outcome to set as my outcome
   * @param <O> the type to be answered
   * @return {@code Completes<O>}
   */
  <O> Completes<O> with(final O outcome);
}

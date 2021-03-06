// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class BasicCompletesTest {
  private Integer andThenValue;
  private Integer failureValue;

  @Test
  public void testCompletesWith() {
    final Completes<Integer> completes = new BasicCompletes<>(5, false);

    assertEquals(new Integer(5), completes.outcome());
  }

  @Test
  public void testCompletesAfterFunction() {
    final Completes<Integer> completes = new BasicCompletes<>(0);

    completes.andThen((value) -> value * 2);

    completes.with(5);

    assertEquals(new Integer(10), completes.outcome());
  }

  @Test
  public void testCompletesAfterConsumer() {
    final Completes<Integer> completes = new BasicCompletes<>(0);

    completes.andThen((value) -> andThenValue = value);

    completes.with(5);

    assertEquals(new Integer(5), completes.outcome());
  }

  @Test
  public void testCompletesAfterAndThen() {
    final Completes<Integer> completes = new BasicCompletes<>(0);

    completes
      .andThen((value) -> value * 2)
      .andThen((value) -> andThenValue = value);

    completes.with(5);

    assertEquals(new Integer(10), andThenValue);
    assertEquals(new Integer(10), completes.outcome());
  }

  @Test
  public void testCompletesAfterAndThenMessageOut() {
    final Completes<Integer> completes = new BasicCompletes<>(0);

    final Holder holder = new Holder();

    completes
      .andThen((value) -> value * 2)
      .andThen((value) -> { holder.hold(value); return value; } );

    completes.with(5);

    completes.await();

    assertEquals(new Integer(10), andThenValue);
  }

  @Test
  public void testOutcomeBeforeTimeout() {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    completes
      .andThen(1000, (value) -> value * 2)
      .andThen((value) -> andThenValue = value);

    completes.with(5);

    completes.await(10);

    assertEquals(new Integer(10), andThenValue);
  }

  @Test
  public void testTimeoutBeforeOutcome() throws Exception {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    completes
      .andThen(1, 0, (value) -> value * 2)
      .andThen((value) -> andThenValue = value);

    Thread.sleep(100);

    completes.with(5);

    completes.await();

    assertTrue(completes.hasFailed());
    assertNotEquals(new Integer(10), andThenValue);
    assertNull(andThenValue);
  }

  @Test
  public void testThatFailureOutcomeFails() {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    completes
      .andThen(null, (value) -> value * 2)
      .andThen((Integer value) -> andThenValue = value)
      .otherwise((failedValue) -> failureValue = 1000);

    completes.with(null);

    completes.await();

    assertTrue(completes.hasFailed());
    assertNull(andThenValue);
    assertEquals(new Integer(1000), failureValue);
  }

  @Test
  public void testThatNonNullFailureOutcomeFails() {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    completes
            .andThen(new Integer(-100), (value) -> 2 * value)
            .andThen((x) -> andThenValue = x)
            .otherwise((x) -> failureValue = 1000);

    completes.with(-100);

    final Integer completed = completes.await();

    assertTrue(completes.hasFailed());
    assertEquals(new Integer(1000), completed);
    assertEquals(null, andThenValue);
    assertEquals(new Integer(1000), failureValue);
  }

  @Test
  public void testThatFluentTimeoutWithNonNullFailureTimesout() throws Exception {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    completes
      .useFailedOutcomeOf(new Integer(-100))
      .timeoutWithin(1)
      .andThen(value -> 2 * value)
      .otherwise((Integer failedValue) -> failedValue.intValue() - 100);

    Thread.sleep(100);

    completes.with(5);

    final Integer failureOutcome = completes.await();

    assertTrue(completes.hasFailed());
    assertEquals(new Integer(-200), failureOutcome);
  }

  @Test
  public void testThatExceptionOutcomeFails() {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    completes
      .andThen(null, (value) -> value * 2)
      .andThen((Integer value) -> { throw new IllegalStateException("" + (value * 2)); })
      .recoverFrom((e) -> failureValue = Integer.parseInt(e.getMessage()));

    completes.with(2);

    completes.await();

    assertTrue(completes.hasFailed());
    assertNull(andThenValue);
    assertEquals(new Integer(8), failureValue);
  }

  @Test
  public void testThatExceptionHandlerDelayRecovers() {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    completes
      .andThen(null, (value) -> value * 2)
      .andThen((Integer value) -> { throw new IllegalStateException("" + (value * 2)); });

    completes.with(10);

    completes
      .recoverFrom((e) -> failureValue = Integer.parseInt(e.getMessage()));

    completes.await();

    assertTrue(completes.hasFailed());
    assertNull(andThenValue);
    assertEquals(new Integer(40), failureValue);
  }

  @Test
  public void testThatAwaitTimesout() throws Exception {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    final Integer completed = completes.await(10);

    completes.with(5);

    assertNotEquals(new Integer(5), completed);
    assertNull(completed);
  }

  @Test
  public void testThatAwaitCompletes() throws Exception {
    final Completes<Integer> completes = new BasicCompletes<>(new Scheduler());

    new Thread() {
      @Override
      public void run() {
        try {
          Thread.sleep(100);
          completes.with(5);
        } catch (Exception e) {
          // ignore
        }
      }
    }.start();

    final Integer completed = completes.await();

    assertEquals(new Integer(5), completed);
  }

  @Test
  public void testInvertWithFailedOutcome() throws InterruptedException {
    final Outcome<RuntimeException, Completes<String>> failed = Failure.of(new RuntimeException("boom"));
    Completes<Outcome<RuntimeException, String>> inverted = Completes.invert(failed);
    CountDownLatch latch = new CountDownLatch(1);
    inverted.andThenConsume(outcome -> {
      assertTrue("was not Failure", outcome instanceof Failure);
      assertNull("was not null", outcome.getOrNull());
      assertEquals("was not the expected error message", "boom", outcome.otherwise(Throwable::getMessage).get());
      latch.countDown();
    });
    assertTrue("timed out", latch.await(1, TimeUnit.SECONDS));
  }

  @Test
  public void testInvertWithSuccessOutcomeOfSuccessCompletes() throws InterruptedException {
    final Outcome<RuntimeException, Completes<String>> success = Success.of(Completes.withSuccess("YAY"));
    Completes<Outcome<RuntimeException, String>> inverted = Completes.invert(success);
    CountDownLatch latch = new CountDownLatch(1);
    inverted.andThenConsume(outcome -> {
      assertTrue("was not Success", outcome instanceof Success);
      assertNotNull("was null", outcome.getOrNull());
      assertEquals("was not the expected value", "YAY", outcome.get());
      latch.countDown();
    });
    assertTrue("timed out", latch.await(1, TimeUnit.SECONDS));
  }

  @Test
  public void testInvertWithSuccessOutcomeOfFailedCompletes() throws InterruptedException {
    final Outcome<RuntimeException, Completes<String>> successfulFailure = Success.of(Completes.withFailure("ERROR"));
    Completes<Outcome<RuntimeException, String>> inverted = Completes.invert(successfulFailure);
    assertTrue("hasn't failed", inverted.hasFailed());
    CountDownLatch latch = new CountDownLatch(1);
    inverted.andThenConsume(outcome -> latch.countDown());
    assertFalse("din't timeout", latch.await(1, TimeUnit.MILLISECONDS));
  }

  private class Holder {
    private void hold(final Integer value) {
      andThenValue = value;
    }
  }
}

package com.github.dakusui.jcunit.framework.tests.ipo2;

import com.github.dakusui.jcunit.constraint.ConstraintManager;
import com.github.dakusui.jcunit.constraint.ConstraintObserver;
import com.github.dakusui.jcunit.core.LabeledTestCase;
import com.github.dakusui.jcunit.core.Utils;
import com.github.dakusui.jcunit.core.factor.Factors;
import com.github.dakusui.jcunit.core.tuples.Tuple;
import com.github.dakusui.jcunit.exceptions.JCUnitSymbolException;
import com.github.dakusui.jcunit.generators.ipo2.IPO2;
import com.github.dakusui.jcunit.generators.ipo2.optimizers.IPO2Optimizer;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SimpleConstraintConsciousTest extends IPO2Test {
  private List<Tuple> prohibitedTuples = null;

  public List<Tuple> getProhibitedTuples() {
    return this.prohibitedTuples;
  }

  protected void setProhibitedTuples(Tuple... tuples) {
    this.prohibitedTuples = new LinkedList<Tuple>();
    Collections.addAll(prohibitedTuples, tuples);
  }

  @Override
  public ConstraintManager createConstraintManager() {
    return new TestConstraintManager(getProhibitedTuples());
  }

  @Test
  public void test_001a() {
    int strength = 2;
    this.setProhibitedTuples(
        new Tuple.Builder().put("F1", "L1x").put("F2", "L2x").build());

    Factors factors = new Factors.Builder()
        .add(factor("F1", "L11", "L1x"))
        .add(factor("F2", "L21", "L2x"))
        .add(factor("F3", "L31"))
        .add(factor("F4", "L41", "L42")).build();
    ConstraintManager constraintManager = createConstraintManager();
    IPO2Optimizer optimizer = createOptimizer();

    IPO2 ipo = generateIPO2(factors,
        strength, constraintManager, optimizer);
    verify(strength, factors, constraintManager, ipo.getResult(),
        ipo.getRemainders());
  }

  @Test
  public void test_001b() {
    int strength = 3;
    this.setProhibitedTuples(
        new Tuple.Builder().put("F1", "L1x").put("F2", "L2x").build());

    Factors factors = new Factors.Builder()
        .add(factor("F1", "L11", "L1x"))
        .add(factor("F2", "L21", "L2x"))
        .add(factor("F3", "L31"))
        .add(factor("F4", "L41", "L42")).build();
    ConstraintManager constraintManager = createConstraintManager();
    IPO2Optimizer optimizer = createOptimizer();

    IPO2 ipo = generateIPO2(factors,
        strength, constraintManager, optimizer);
    verify(strength, factors, constraintManager, ipo.getResult(),
        ipo.getRemainders());
  }

  @Override
  protected void verifyRemaindersViolateConstraints(List<Tuple> remainders,
      List<Tuple> result, ConstraintManager constraintManager) {
    // Since in this test class there is no implicit constraint, we
    // can simply verify them.
    System.err.println(result);
    for (Tuple tuple : remainders) {
      assertThat(String.format("'%s' is contained in result set.", tuple),
          find(tuple, result), is(false));
      assertThat(String.format("'%s' doesn't violate any constraints.", tuple),
          checkConstraints(
              constraintManager,
              tuple), is(false));
    }
  }

  public static class TestConstraintManager implements ConstraintManager {
    private final Set<Tuple> constraints;

    TestConstraintManager(List<Tuple> constraints) {
      this.constraints = new HashSet<Tuple>();
      this.constraints.addAll(constraints);
    }

    private static boolean matches(Tuple constraint, Tuple t) {
      for (String fName : constraint.keySet()) {
        if (!Utils.eq(constraint.get(fName), t.get(fName))) {
          return false;
        }
      }
      return true;
    }

    @Override
    public boolean check(Tuple tuple) throws JCUnitSymbolException {
      boolean insufficientTuple = false;
      for (Tuple c : constraints) {
        if (!tuple.keySet().containsAll(c.keySet())) {
          insufficientTuple = true;
          continue;
        }
        if (matches(c, tuple)) {
          return false;
        }
      }
      if (insufficientTuple) throw new JCUnitSymbolException();
      return true;
    }

    @Override public void init(Object[] params) {
    }

    @Override public Factors getFactors() {
      return null;
    }

    @Override public void setFactors(Factors factors) {
    }

    @Override
    public void addObserver(ConstraintObserver observer) {

    }

    @Override
    public void removeObservers(ConstraintObserver observer) {

    }

    @Override public List<LabeledTestCase> getViolations() {
      return Collections.emptyList();
    }
  }
}
package com.github.dakusui.jcunit.runners.experimentals.theories;

import com.github.dakusui.jcunit.core.Checks;
import com.github.dakusui.jcunit.core.factor.Factor;
import com.github.dakusui.jcunit.core.factor.Factors;
import com.github.dakusui.jcunit.core.tuples.Tuple;
import com.github.dakusui.jcunit.exceptions.UndefinedSymbol;
import com.github.dakusui.jcunit.plugins.Plugin;
import com.github.dakusui.jcunit.plugins.caengines.CoveringArray;
import com.github.dakusui.jcunit.plugins.caengines.CoveringArrayEngine;
import com.github.dakusui.jcunit.plugins.caengines.IPO2CoveringArrayEngine;
import com.github.dakusui.jcunit.plugins.constraints.Constraint;
import com.github.dakusui.jcunit.plugins.constraints.ConstraintBase;
import com.github.dakusui.jcunit.runners.core.RunnerContext;
import com.github.dakusui.jcunit.runners.experimentals.theories.annotations.GenerateWith;
import com.github.dakusui.jcunit.runners.experimentals.theories.annotations.Name;
import com.github.dakusui.jcunit.runners.standard.annotations.Checker;
import com.github.dakusui.jcunit.runners.standard.annotations.Generator;
import com.github.dakusui.jcunit.runners.standard.annotations.Value;
import org.junit.Assert;
import org.junit.experimental.theories.PotentialAssignment;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.experimental.theories.internal.Assignments;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TheoriesWithJCUnit extends Theories {
  public TheoriesWithJCUnit(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected void validateTestMethods(List<Throwable> errors) {
    super.validateTestMethods(errors);
    for (FrameworkMethod method : computeTestMethods()) {
      Collection<String> names = new ArrayList<String>();
      for (Name each : getNameAnnotationsFromMethod(method, this.getTestClass())) {
        if (names.contains(each.value())) {
          errors.add(new Error("Parameter name '" + each.value() + "' is used more than once in " + method.getName()));
        }
        names.add(each.value());
      }
    }
  }

  private List<Name> getNameAnnotationsFromMethod(FrameworkMethod method, TestClass testClass) {
    List<Name> ret = new ArrayList<Name>();
    Assignments assignments = Assignments.allUnassigned(method.getMethod(), testClass);
    try {
      while (!assignments.isComplete()) {
        Name name = assignments.nextUnassigned().getAnnotation(Name.class);
        if (name != null) {
          ret.add(name);
        }
        assignments = assignments.assignNext(null);
      }
    } catch (Throwable throwable) {
      throw Checks.wrap(throwable);
    }
    return ret;
  }

  @Override
  public Statement methodBlock(final FrameworkMethod method) {
    Factors.Builder factorsBuilder = new Factors.Builder();
    final TestClass testClass = getTestClass();
    Assignments assignments = Assignments.allUnassigned(method.getMethod(), testClass);
    try {
      int i = 0;
      while (!assignments.isComplete()) {
        List<PotentialAssignment> potentials = assignments.potentialsForNextUnassigned();
        String prefix = String.format("param%03d", i);
        Name name = assignments.nextUnassigned().getAnnotation(Name.class);
        ////
        // Guarantee the factors names are generated in dictionary order.
        String factorName = String.format("%s:%s", prefix, name != null ? name.value() : prefix);
        Factor.Builder factorBuilder = new Factor.Builder(factorName);
        for (PotentialAssignment each : potentials) {
          factorBuilder.addLevel(each);
        }
        factorsBuilder.add(factorBuilder.build());
        assignments = assignments.assignNext(null);
        i++;
      }
    } catch (Throwable throwable) {
      throw Checks.wrap(throwable);
    }
    final CoveringArrayEngine tg = createCoveringArrayEngine(method.getMethod());
    tg.setFactors(factorsBuilder.build());
    tg.init();
    return new TheoryAnchor(method, testClass) {
      int successes = 0;
      List<AssumptionViolatedException> fInvalidParameters = new ArrayList<AssumptionViolatedException>();

      @Override
      public void evaluate() throws Throwable {
        CoveringArray ca = tg.getCoveringArray();
        for (Tuple each : ca) {
          runWithCompleteAssignment(tuple2assignments(method.getMethod(), testClass, each));
        }
        //if this test method is not annotated with Theory, then no successes is a valid case
        boolean hasTheoryAnnotation = method.getAnnotation(Theory.class) != null;
        if (successes == 0 && hasTheoryAnnotation) {
          Assert.fail("Never found parameters that satisfied method assumptions.  Violated assumptions: "
              + fInvalidParameters);
        }
      }

      @Override
      protected void handleAssumptionViolation(AssumptionViolatedException e) {
        fInvalidParameters.add(e);
      }

      @Override
      protected void handleDataPointSuccess() {
        successes++;
      }
    };
  }


  protected CoveringArrayEngine createCoveringArrayEngine(final Method method) {
    GenerateWith tgAnn = method.getAnnotation(GenerateWith.class);
    CoveringArrayEngine tg;
    final Constraint cm;
    RunnerContext runnerContext = createRunnerContext();
    if (tgAnn != null) {
      tg = createCoveringArrayEngine(tgAnn.generator(), runnerContext);
      cm = createConstraintManager(tgAnn.checker(), runnerContext);
    } else {
      tg = new IPO2CoveringArrayEngine(2);
      cm = Constraint.DEFAULT_CONSTRAINT_MANAGER;
    }
    tg.setConstraint(new ConstraintBase() {
      Constraint baseCM = cm;

      @Override
      public boolean check(Tuple tuple) throws UndefinedSymbol {
        return baseCM.check(convert(tuple));
      }

      private Tuple convert(Tuple tuple) {
        Tuple.Builder b = new Tuple.Builder();
        for (String each : tuple.keySet()) {
          try {
            b.put(each.substring(each.indexOf(':') + 1), ((PotentialAssignment) tuple.get(each)).getValue());
          } catch (PotentialAssignment.CouldNotGenerateValueException e) {
            throw Checks.wrap(e);
          }
        }
        return b.build();
      }
    });
    return tg;
  }

  private RunnerContext createRunnerContext() {
    return new RunnerContext.Base(this.getTestClass().getJavaClass());
  }

  protected Constraint createConstraintManager(Checker checkerAnnotation, RunnerContext runnerContext) {
    Value.Resolver resolver = new Value.Resolver();
    //noinspection unchecked
    return Checks.cast(
        Constraint.class,
        new Plugin.Factory<Constraint, Value>(
            (Class<Constraint>) checkerAnnotation.value(),
            resolver,
            runnerContext
        )
            .create(checkerAnnotation.args()));
  }

  private static CoveringArrayEngine createCoveringArrayEngine(final Generator generatorAnnotation, RunnerContext runnerContext) {
    Value.Resolver resolver = new Value.Resolver();
    //noinspection unchecked
    return Checks.cast(
        CoveringArrayEngine.class,
        new Plugin.Factory<CoveringArrayEngine, Value>((Class<CoveringArrayEngine>) generatorAnnotation.value(),
            resolver,
            runnerContext).create(generatorAnnotation.args()));
  }

  private static Assignments tuple2assignments(Method method, TestClass testClass, Tuple t) {
    // Tuple generator generates dictionary order guaranteed tuples.
    Assignments ret = Assignments.allUnassigned(method, testClass);
    for (Object each : t.values()) {
      ret = ret.assignNext(Checks.cast(PotentialAssignment.class, each));
    }
    return ret;
  }
}

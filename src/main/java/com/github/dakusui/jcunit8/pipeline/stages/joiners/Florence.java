package com.github.dakusui.jcunit8.pipeline.stages.joiners;

import com.github.dakusui.combinatoradix.Combinator;
import com.github.dakusui.jcunit.core.tuples.Tuple;
import com.github.dakusui.jcunit.core.tuples.TupleUtils;
import com.github.dakusui.jcunit.core.utils.Checks;
import com.github.dakusui.jcunit8.factorspace.Factor;
import com.github.dakusui.jcunit8.pipeline.Requirement;
import com.github.dakusui.jcunit8.pipeline.stages.Joiner;
import com.github.dakusui.jcunit8.testsuite.SchemafulTupleSet;
import com.github.dakusui.jcunit8.testsuite.TupleSet;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.dakusui.jcunit8.core.Utils.memoize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.StreamSupport.stream;

@SuppressWarnings("NonAsciiCharacters")
public class Florence extends Joiner.Base {
  private final Requirement requirement;

  public Florence(Requirement requirement) {
    this.requirement = requirement;
  }

  protected long sizeOf(SchemafulTupleSet tupleSet) {
    return tupleSet.width();
  }

  /**
   * <pre>
   *   Algorithm: IPOG-Test (int t , ParameterSet ps ) {
   *     1.  initialize test set ts to be an empty set
   *     2.  denote the parameters in ps , in an arbitrary order, as P1 , P2, ...,
   *         and Pn
   *     3.  add into test set ts a test for each combination of values of the first
   *         t parameters (*1)
   *     4.  for (int i = t + 1 ; i ≤ n ; i ++ ){
   *     5.     let π be the set of t-way combinations of values involving parameter
   *            Pi and t -1 parameters among the first i – 1 parameters (*2)
   *     6.     // horizontal extension for parameter Pi
   *     7.     for (each test τ = (v 1 , v 2 , ..., v i-1 ) in test set ts ) {
   *     8.         choose a value vi of Pi and replace τ with τ’ = (v 1 , v 2 ,
   *                ..., vi-1 , vi ) so that τ’ covers the most number of
   *                combinations of values in π (*3)
   *     9.         remove from π the combinations of values covered by τ’
   *     10.    }
   *     11.    // vertical extension for parameter P i
   *     12.    for (each combination σ in set π ) {
   *     13.      if (there exists a test that already covers σ ) {
   *     14.          remove σ from π
   *     15.      } else {
   *     16.          change an existing test, if possible, or otherwise add a new test
   *                  to cover σ and remove it from π (*4) (*a)
   *     17.      }
   *     18.    }
   *     19.  }
   *     20.  return ts;
   *    }
   *   See http://barbie.uta.edu/~fduan/ACTS/IPOG_%20A%20General%20Strategy%20for%20T-Way%20Software%20Testing.pdf
   *
   *   Constraint handling consideration (if an impossible constraint is given)
   *   (*1)  If one or more impossible constraints are involved in first t parameters,
   *         ts can become empty. This method should return an empty set immediately.
   *   (*2)  If one or more impossible constraints are involved in first i-1 parameters,
   *         π will become empty.
   *   (*3)
   *   (*4)
   * // https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4652878/
   *  </pre>
   */
  @Override
  protected SchemafulTupleSet doJoin(SchemafulTupleSet lhs, SchemafulTupleSet rhs) {
    int t = this.requirement.strength();
    Session session = new Session();

    List<String> alreadyProcessedFactors = new LinkedList<>();
    SchemafulTupleSet.Builder ts = new SchemafulTupleSet.Builder(lhs.getAttributeNames()).addAllEntries(lhs);
    for (int i = 0; i < rhs.width(); i++) {
      String F = rhs.getAttributeNames().get(i);
      TupleSet π = session.allPossibleUniqueTuplesOfStrength(
          lhs,
          rhs,
          alreadyProcessedFactors,
          F,
          t
      );
      for (Tuple τ : new ArrayList<>(ts)) {
        Object vi = session.chooseLevelThatCoversMostTuplesIn(τ, F, π, lhs, rhs, alreadyProcessedFactors, t);
        Tuple.Builder b = Tuple.builder().putAll(τ);
        List<Tuple> candidates = rhs.index().find(
            TupleUtils.project(
                b.put(F, vi).build(),
                concat(alreadyProcessedFactors.stream(), Stream.of(F)).collect(toList())
            )
        );
        assert !candidates.isEmpty();
        if (candidates.size() == 1)
          b.putAll(candidates.get(0));
        π.removeAll(
            tuplesNewlyCovered(lhs.getAttributeNames(), alreadyProcessedFactors, F, vi, t, τ)
        );
        ts.remove(τ);
        ts.add(b.build());
        while (!π.isEmpty()) {

        }
      }
    }
    return ts.build();

    // all valid t-way tuples are already covered in LHS.
    // all valid t-way tuples are already covered in RHS.
    //
    // ts; tuple set to be returned. Initially simply built from LHS.
    // for each Factor (Fi) in RHS;
    //   for each tuple (T) in ts;
    //     if T doesn't have a value for Fi,
    //       choose best Vj in Fi for T and assign it to T's Fi
    //     if there is only one tuple in RHS that can satisfy chosen values, assign
    //     the other values, too, using the tuple.
    //     remove all tuplets that can be created by combinating the best Vj and
    //     processedFactors from π.
    //
    //   add Fi to processedFactors
    ////
    /*
     *     4.  for (int i = t + 1 ; i ≤ n ; i ++ ){
     *         * t; strength
     *         * 0-origin
     */
    //    TupleSet π;
    //    for (int i = 0; i < rhs.width(); i++) {
    //      /*     5.    let π be the set of t -way combinations of values involving parameter
    //       *            Pi and t -1 parameters among the first i – 1 parameters (*2)
    //       */
    //      Factor Pi = getFactorFrom(rhs, rhs.getAttributeNames().get(i));
    //      processedFactors.add(Pi);
    //      π = prepare_π(processedFactors, allFactors, allConstraints, t);
    //      /*     6.     // horizontal extension for parameter Pi
    //       *     7.     for (each test τ = (v 1 , v 2 , ..., v i-1 ) in test set ts ) {
    //       */
    //      for (Tuple τ : ts) {
    //        /*     8.         choose a value vi of Pi and replace τ with τ’ = (v 1 , v 2 ,
    //         *                ..., vi-1 , vi ) so that τ’ covers the most number of
    //         *                combinations of values in π (*3)
    //         */
    //        Object vi = chooseLevelThatCoversMostTuples(
    //            τ, Pi, π, t,
    //            allFactors,
    //            allConstraints,
    //            session
    //        ).orElseThrow(
    //            ////
    //            // (*3) This cannot happen
    //            () -> TestDefinitionException.failedToCover(Pi.getName(), Pi.getLevels(), τ)
    //        );
    //        τ.put(Pi.getName(), vi);
    //        /*  9.         remove from π the combinations of values covered by τ’
    //         */
    //        π.removeAll(TupleUtils.subtuplesOf(τ, t));
    //      }
    //
    //      /* 10.
    //       * 11.    // vertical extension for parameter P i
    //       * 12.    for (each combination σ in set π ) {
    //       */
    //      for (Tuple σ : new LinkedList<>(π)) {
    //        /* 13.      if (there exists a test that already covers σ ) {
    //         * 14.          remove σ from π
    //         * 15.      } else {
    //         * 16.        change an existing test, if possible, or otherwise add a new test
    //         *            to cover σ and remove it from π (*4)
    //         * 17.      }
    //         */
    //        if (ts.stream().anyMatch(σ::isSubtupleOf)) {
    //          π.remove(σ);
    //        } else {
    //          Tuple chosenTest = streamIncompleteTestsToCoverGivenTuple(
    //              ts, σ
    //          ).filter(
    //              (Tuple tuple) -> isAllowedTuple(allFactors, allConstraints, this.session).test(
    //                  Tuple.builder().putAll(removeDontCares(tuple)).putAll(σ).build()
    //              ) // (*4)
    //          ).findFirst(
    //          ).orElseGet(
    //              () -> createTupleFrom(
    //                  FactorUtils.toFactorNames(processedFactors),
    //                  σ
    //              )
    //          );
    //          /*
    //           * <pre>
    //           * 16. change an existing test, if possible, or otherwise add a new test
    //           *     to cover σ (*a)
    //           * </pre>
    //           */
    //          chosenTest.putAll(σ);
    //          if (!ts.contains(chosenTest))
    //            ts.add(chosenTest);
    //          π.remove(σ);
    //        }
    //      }
    //      ts = ts.stream()
    //          .map(
    //              replaceDontCareValuesWithActualLevels(
    //                  allFactors,
    //                  allConstraints,
    //                  session
    //              )
    //          ).collect(toList());
    //    }
  }

  private List<Tuple> tuplesNewlyCovered(List<String> factorsFromLhs, List<String> factorsFromRhs, String currentFactor, Object valueForCurrentFactor, int t, Tuple τ) {
    return stream(new Combinator<>(
            concat(
                factorsFromLhs.stream(),
                factorsFromRhs.stream()
            ).collect(toList()), t - 1).spliterator(),
        false
    ).map(factorNames -> new Tuple.Builder() {
      {
        factorNames.forEach(k -> put(k, τ.get(k)));
      }
    }.put(currentFactor, valueForCurrentFactor).build()).collect(toList());
  }

  private Factor getFactorFrom(SchemafulTupleSet tupleSet, String factorName) {
    return Factor.create(
        factorName,
        new LinkedHashSet<Object>() {{
          tupleSet.stream()
              .map(tuple -> tuple.get(factorName))
              .forEach(this::add);
        }}.toArray()
    );
  }

  private List<Factor> initializeProcessedFactors(SchemafulTupleSet tupleSet) {
    return tupleSet.getAttributeNames().stream().map(
        factorName -> getFactorFrom(tupleSet, factorName)
    ).collect(toList());
  }

  private static class Session {
    private final Function<SchemafulTupleSet, Function<List<String>, TupleSet>> uniqueTuplesFunction = memoize(
        (SchemafulTupleSet tuples) -> memoize(
            (List<String> factorNames) -> _uniqueTuples(tuples, factorNames)
        ));


    private TupleSet allPossibleUniqueTuplesOfStrength(
        SchemafulTupleSet lhs,
        SchemafulTupleSet rhs,
        List<String> alreadyProcessedFactorsInRhs,
        String newFactorNameInRhs,
        int strength
    ) {
      Checks.checkcond(strength > 1);
      Checks.checkcond(lhs.width() + alreadyProcessedFactorsInRhs.size() + 1 >= strength);
      return IntStream.range(
          1,
          strength
      ).filter(
          (int i) -> i + alreadyProcessedFactorsInRhs.size() + 1 >= strength
      ).mapToObj(
          (int i) -> uniqueTuplesOfStrength(lhs, i).cartesianProduct(
              new TupleSet.Builder().addAll(
                  stream(
                      new Combinator<>(alreadyProcessedFactorsInRhs, strength - i - 1).spliterator(),
                      false
                  ).flatMap(
                      (List<String> chosenFactorNames) -> uniqueTuples(
                          rhs,
                          concat(
                              chosenFactorNames.stream(),
                              Stream.of(newFactorNameInRhs)
                          ).collect(toList())
                      ).stream()
                  ).distinct(
                  ).collect(
                      toList()
                  )
              ).build()
          )
      ).reduce(
          (TupleSet t, TupleSet u) -> new TupleSet.Builder().addAll(t).addAll(u).build()
      ).orElseThrow(
          AssertionError::new
      );
    }

    private TupleSet uniqueTuplesOfStrength(SchemafulTupleSet tuples, int strength) {
      return new TupleSet.Builder().addAll(
          stream(
              new Combinator<>(
                  tuples.getAttributeNames(),
                  strength
              ).spliterator(),
              false
          ).flatMap(
              factorNames -> uniqueTuples(tuples, factorNames).stream()
          ).distinct(
          ).collect(
              toList()
          )
      ).build();
    }

    /*
     * This does exactly the same as what _uniqueTuples does but with better performance
     * by memoization.
     */
    private TupleSet uniqueTuples(SchemafulTupleSet tuples, List<String> factorNames) {
      return uniqueTuplesFunction.apply(tuples).apply(factorNames);
    }

    private TupleSet _uniqueTuples(SchemafulTupleSet tuples, List<String> factorNames) {
      return new TupleSet.Builder().addAll(
          tuples.stream()
              .map(tuple -> TupleUtils.project(tuple, factorNames))
              .distinct()
              .collect(toList())
      ).build();
    }

    public Object chooseLevelThatCoversMostTuplesIn(Tuple τ, String f, TupleSet π, SchemafulTupleSet lhs, SchemafulTupleSet rhs, List<String> alreadyProcessedFactors, int strength) {
      return null;
    }
  }

  public static void main(String... args) {
    System.out.println(new Combinator<String>(Collections.emptyList(), 0).size());
    System.out.println(new Combinator<String>(Collections.emptyList(), 0).get(0));
  }
}
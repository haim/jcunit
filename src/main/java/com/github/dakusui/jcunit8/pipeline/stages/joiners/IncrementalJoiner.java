package com.github.dakusui.jcunit8.pipeline.stages.joiners;

import com.github.dakusui.combinatoradix.Combinator;
import com.github.dakusui.jcunit.core.tuples.Tuple;
import com.github.dakusui.jcunit.core.tuples.TupleUtils;
import com.github.dakusui.jcunit8.core.StreamableCartesianator;
import com.github.dakusui.jcunit8.exceptions.FrameworkException;
import com.github.dakusui.jcunit8.pipeline.Requirement;
import com.github.dakusui.jcunit8.pipeline.stages.Joiner;
import com.github.dakusui.jcunit8.testsuite.SchemafulTupleSet;
import com.github.dakusui.jcunit8.testsuite.TupleSet;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.dakusui.jcunit.core.tuples.TupleUtils.*;
import static com.github.dakusui.jcunit8.core.Utils.memoize;
import static com.github.dakusui.jcunit8.core.Utils.sizeOfIntersection;
import static com.github.dakusui.jcunit8.pipeline.stages.joiners.StandardJoiner.findCoveringTuplesIn;
import static com.github.dakusui.jcunit8.pipeline.stages.joiners.StandardJoiner.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

public class IncrementalJoiner extends Joiner.Base {

  private final Requirement requirement;

  public IncrementalJoiner(Requirement requirement) {
    this.requirement = requirement;
  }

  @Override
  protected SchemafulTupleSet doJoin(SchemafulTupleSet lhs, SchemafulTupleSet rhs) {
    Session session = new Session(requirement, lhs, rhs);
    log("phase-0: incremental-join started");
    List<Tuple> ts = buildInitialTupleSet(requirement, lhs, rhs);
    log("phase-1: ts(init)=%s%n", ts.size());

    final int t = requirement.strength();
    final int n = lhs.width();

    List<String> processedFactors = new LinkedList<>();
    for (int i = t; i < n; i++) {

      String Pi = lhs.getAttributeNames().get(i);
      log("phase-2.1: Pi=%s, ts.size=%s", Pi, ts.size());
      @SuppressWarnings("NonAsciiCharacters") TupleSet π = prepare_π(t, Pi, processedFactors, lhs, rhs);
      log("phase-2.2a: π=%s", π.size());
      streamCoveredTuples(
          t,
          Pi,
          processedFactors,
          toSchemafulTupleSet(lhs.getAttributeNames(), rhs.getAttributeNames(), ts),
          rhs.getAttributeNames()
      ).forEach(
          π::remove
      );
      log("phase-2.2b: π=%s", π.size());
      processedFactors.add(Pi);
      while (!π.isEmpty()) {
        log("phase-2.2.1: π=%s", π.size());
        session.findBestCombinationsFor(
            π.stream().findFirst().orElseThrow(
                IllegalStateException::new
            ),
            ts,
            π
        );
      }
      log("phase-2.3: ts.size=%s", ts.size());
    }
    ensureAllTuplesAreUsed(ts, lhs, rhs);
    log("phase-3: ts.size=%s", ts.size());
    return new SchemafulTupleSet.Builder(
        concat(lhs.getAttributeNames().stream(), rhs.getAttributeNames().stream()).collect(toList())
    ).addAll(
        ts
    ).build();
  }

  private SchemafulTupleSet toSchemafulTupleSet(List<String> lhsAttributeNames, List<String> rhsAttributeNames, List<Tuple> ts) {
    return new SchemafulTupleSet.Builder(
        concat(
            lhsAttributeNames.stream(),
            rhsAttributeNames.stream()
        ).collect(
            toList()
        )
    ).addAll(
        ts
    ).build();
  }

  private static List<Tuple> buildInitialTupleSet(Requirement requirement, SchemafulTupleSet lhs, SchemafulTupleSet rhs) {
    List<String> lhsAttributeNamesInSeeds = lhs.getAttributeNames().subList(0, requirement.strength());
    SchemafulTupleSet seeds = new StandardJoiner(requirement).apply(
        lhs.project(lhsAttributeNamesInSeeds), rhs
    );
    Map<Tuple, Integer> numUsed = new HashMap<>();
    return seeds.stream(
    ).map(
        tupleInSeeds -> {
          Tuple chosen = lhs.stream(
          ).filter(
              project(tupleInSeeds, lhsAttributeNamesInSeeds)::isSubtupleOf
          ).min(
              comparingInt(o -> numUsed.getOrDefault(o, 0))
          ).orElseThrow(
              () -> noMatchingTupleFound(tupleInSeeds)
          );
          numUsed.put(chosen, numUsed.getOrDefault(chosen, 0) + 1);
          return TupleUtils.connect(chosen, tupleInSeeds);
        }
    ).collect(toList());
  }

  @SuppressWarnings({ "unchecked", "NonAsciiCharacters" })
  private static TupleSet prepare_π(int strength, String pi, List<String> processedFactors, SchemafulTupleSet lhs, SchemafulTupleSet rhs) {
    return new TupleSet.Builder().addAll(
        streamInvolvedFactorNames(strength, pi, processedFactors, rhs.getAttributeNames()).flatMap(
            involvedFactorNames -> new StreamableCartesianator<>(
                lhs.index().allPossibleTuples(involvedFactorNames.stream().filter(lhs.index()::hasAttribute).collect(toList())),
                rhs.index().allPossibleTuples(involvedFactorNames.stream().filter(rhs.index()::hasAttribute).collect(toList()))
            ).stream()
        ).map(
            tuples -> TupleUtils.connect(tuples.get(0), tuples.get(1))
        ).collect(
            toList()
        )
    ).build();
  }

  private static Stream<Tuple> streamCoveredTuples(int strength, String pi, List<String> processedFactors, SchemafulTupleSet ts, List<String> rhsAttributeNames) {
    return streamInvolvedFactorNames(strength, pi, processedFactors, rhsAttributeNames).flatMap(
        involvedFactorNames -> ts.index().allPossibleTuples(involvedFactorNames).stream()
    );
  }

  @SuppressWarnings("NonAsciiCharacters")
  private static int countCoveredTuple(TupleSet π, Tuple tuple, int strength) {
    return (int) TupleUtils.subtuplesOf(tuple, strength).stream().filter(π::contains).count();
  }

  private static void ensureAllTuplesAreUsed(List<Tuple> ts, SchemafulTupleSet lhs, SchemafulTupleSet rhs) {
    List<Tuple> lhsNotUsed = ts.stream().filter(each -> lhs.index().find(each).isEmpty()).collect(toList());
    List<Tuple> rhsNotUsed = ts.stream().filter(each -> rhs.index().find(each).isEmpty()).collect(toList());
    int min = min(lhsNotUsed.size(), rhsNotUsed.size());
    for (int i = 0; i < max(lhsNotUsed.size(), rhsNotUsed.size()); i++) {
      ts.add(connect(lhsNotUsed.get(i % min), rhsNotUsed.get(i % min)));
    }
  }

  private static Stream<List<String>> streamInvolvedFactorNames(int strength, String pi, List<String> processedFactors, List<String> rhsFactorNames) {
    List<List<String>> ret = new LinkedList<>();
    for (int i = 1; i < strength; i++) {
      int j = strength - i - 1;
      new Combinator<>(rhsFactorNames, i).forEach(
          fromRhs -> new Combinator<>(processedFactors, j).forEach(fromProcessed -> {
            List<String> cur = new ArrayList<>(strength);
            cur.add(pi);
            cur.addAll(fromProcessed);
            cur.addAll(fromRhs);
            ret.add(cur);
          })
      );
    }
    return ret.stream();
  }

  private static FrameworkException noMatchingTupleFound(Tuple tupleInSeeds) {
    throw new FrameworkException(String.format("No matching tuple was found in lhs for: %s", tupleInSeeds)) {
    };
  }

  private static FrameworkException noCoveringTuple(TupleSet tupleSet) {
    throw new FrameworkException(String.format("No covering tuple can't be generated for: %s", tupleSet)) {
    };
  }

  static class Session {
    final         SchemafulTupleSet                                               lhs;
    final         SchemafulTupleSet                                               rhs;
    final private Function<Tuple, List<Tuple>>                                    coveredByLhs;
    final private Function<Tuple, List<Tuple>>                                    coveredByRhs;
    final private Function<Integer, Function<Tuple, Function<Tuple, Set<Tuple>>>> connectingSubtuplesOf;
    final private Function<Tuple, Function<Tuple, Tuple>>                         connect;
    private final Requirement                                                     requirement;

    Session(Requirement requirement, SchemafulTupleSet lhs, SchemafulTupleSet rhs) {
      this.requirement = requirement;
      this.lhs = lhs;
      this.rhs = rhs;
      this.coveredByLhs = memoize(
          tuple -> findCoveringTuplesIn(project(tuple, lhs.getAttributeNames()), lhs)
      );
      this.coveredByRhs = memoize(
          tuple -> findCoveringTuplesIn(project(tuple, rhs.getAttributeNames()), rhs)
      );
      this.connectingSubtuplesOf = memoize(
          strength -> memoize(
              (Function<Tuple, Function<Tuple, Set<Tuple>>>) lhsTuple -> memoize(
                  rhsTuple -> connectingSubtuplesOf(HashSet::new, lhsTuple, rhsTuple, strength)
              )
          )
      );
      this.connect = memoize(
          left -> memoize(
              right -> TupleUtils.connect(left, right)
          ));
    }

    private Tuple connect(Tuple tuple1, Tuple tuple2) {
      return connect.apply(tuple1).apply(tuple2);
    }

    private void findBestCombinationsFor(Tuple tupleToCover_, List<Tuple> alreadyUsed, TupleSet remainingTuplesToBeCovered) {
      List<Tuple> tuplesToCover = Stream.concat(
          Stream.of(tupleToCover_),
          remainingTuplesToBeCovered.stream().filter(
              tuple -> tupleToCover_.keySet().stream().allMatch(tuple::containsKey) && !tupleToCover_.equals(tuple)
          )
      ).collect(toList());
      int[] last = new int[] { Integer.MAX_VALUE };
      tuplesToCover.forEach(
          each -> {
            int most = 0;
            Tuple bestLhs = null, bestRhs = null;
            outer:
            for (Tuple lhsTuple : this.coveredByLhs.apply(each)) {
              for (Tuple rhsTuple : this.coveredByRhs.apply(each)) {
                if (alreadyUsed.contains(connect(lhsTuple, rhsTuple)))
                  continue;
                Set<Tuple> connectingSubtuples = this.connectingSubtuplesOf.apply(requirement.strength()).apply(lhsTuple).apply(rhsTuple);
                int numCovered = sizeOfIntersection(
                    connectingSubtuples,
                    remainingTuplesToBeCovered
                );
                if (numCovered > most) {
                  most = numCovered;
                  bestLhs = lhsTuple;
                  bestRhs = rhsTuple;
                  if (last[0] == most || most == remainingTuplesToBeCovered.size() || most == connectingSubtuples.size())
                    break outer;
                }
              }
            }
            last[0] = most;
            if (bestLhs != null && bestRhs != null) {
              alreadyUsed.add(connect(bestLhs, bestRhs));
              remainingTuplesToBeCovered.removeAll(
                  connectingSubtuplesOf
                      .apply(requirement.strength())
                      .apply(bestLhs)
                      .apply(bestRhs)
              );
            }
          }
      );
    }

    private Optional<Tuple> findBestTupleFor(Tuple tuple, List<Tuple> candudates, List<Tuple> alreadyUsed, TupleSet remainingTuplesToBeCovered) {
      int most = 0;
      Tuple bestRhs = null;
      for (Tuple rhsTuple : candudates) {
        if (alreadyUsed.contains(connect(tuple, rhsTuple)))
          continue;
        Set<Tuple> connectingSubtuples = this.connectingSubtuplesOf.apply(requirement.strength()).apply(tuple).apply(rhsTuple);
        int numCovered = sizeOfIntersection(
            connectingSubtuples,
            remainingTuplesToBeCovered
        );
        if (numCovered > most) {
          most = numCovered;
          bestRhs = rhsTuple;
          if (most == remainingTuplesToBeCovered.size() || most == connectingSubtuples.size())
            break;
        }
      }
      return most == 0 ?
          Optional.empty() :
          Optional.of(bestRhs);
    }
  }
}
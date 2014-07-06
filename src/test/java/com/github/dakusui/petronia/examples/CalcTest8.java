package com.github.dakusui.petronia.examples;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.dakusui.jcunit.core.BasicSummarizer;
import com.github.dakusui.jcunit.core.Generator;
import com.github.dakusui.jcunit.core.In;
import com.github.dakusui.jcunit.core.JCUnit;
import com.github.dakusui.jcunit.core.JCUnitBase;
import com.github.dakusui.jcunit.core.Out;
import com.github.dakusui.jcunit.core.RuleSet;
import com.github.dakusui.jcunit.core.Summarizer;
import com.github.dakusui.jcunit.generators.ipo2.IPO2TestArrayGenerator;
import com.github.dakusui.petronia.examples.Calc.Op;

@RunWith(JCUnit.class)
@Generator(IPO2TestArrayGenerator.class)
public class CalcTest8 extends JCUnitBase {
  @ClassRule
  public static Summarizer summarizer = new BasicSummarizer();
  @Rule
  public RuleSet rules = new JCUnitBase().autoRuleSet(this).summarizer(summarizer);
  @In
  public int a;
  @In
  public int b;
  @In
  public Op op;
  @Out
  public int r;
  @Out
  public Class<? extends Throwable> t;

  @Test
  public void test() {
    try {
      Calc calc = new Calc();
      r = calc.calc(op, a, b);
    } catch (RuntimeException e) {
      t = e.getClass();
    }
  }
}
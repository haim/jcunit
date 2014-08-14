package com.github.dakusui.jcunit.core;

import com.github.dakusui.enumerator.tuple.AttrValue;
import com.github.dakusui.jcunit.core.tuples.Tuple;

import java.lang.reflect.Field;

/**
 */
public class TestCaseUtils {
  public static AttrValue<String, Object> factor(String name, Object level) {
    return new AttrValue<String, Object>(name, level);
  }

  public static Tuple newTestCase(AttrValue<String, Object>... attrs) {
    Tuple.Builder b = new Tuple.Builder();
    for (AttrValue<String, Object> attrValue : attrs) {
      b.put(attrValue.attr(), attrValue.value());
    }
    return b.build();
  }

  public static void initializeObjectWithTuple(Object testObject,
      Tuple tuple) {
    for (String fieldName : tuple.keySet()) {
      Field f;

      //noinspection unchecked
      f = Utils.getField(testObject, fieldName,
          FactorField.class);
      Utils.setFieldValue(testObject, f, tuple.get(fieldName));
    }
  }

  public static <T> T toTestObject(Class<T> testClass, Tuple testCase) {
    Utils.checknotnull(testClass);
    Utils.checknotnull(testCase);
    T ret = null;
    try {
      ret = testClass.newInstance();
    } catch (InstantiationException e) {
      Utils.rethrow(e, "Failed to instantiate %s", testClass);
    } catch (IllegalAccessException e) {
      Utils.rethrow(e, "Failed to instantiate %s", testClass);
    }
    initializeObjectWithTuple(ret, testCase);
    return ret;
  }

  public static <T> Tuple toTestCase(T testObject) {
    Utils.checknotnull(testObject);
    Tuple.Builder b = new Tuple.Builder();
    for (Field each : Utils.getAnnotatedFields(testObject.getClass(), FactorField.class)) {
      try {
        b.put(each.getName(), each.get(testObject));
      } catch (IllegalAccessException e) {
        Utils.rethrow(e);
      }
    }
    return b.build();
  }
}

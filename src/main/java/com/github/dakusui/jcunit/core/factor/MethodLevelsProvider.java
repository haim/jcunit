package com.github.dakusui.jcunit.core.factor;

import com.github.dakusui.jcunit.core.Checks;
import com.github.dakusui.jcunit.core.FactorField;
import com.github.dakusui.jcunit.core.ParamType;
import com.github.dakusui.jcunit.core.Utils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodLevelsProvider<T> extends LevelsProviderBase<T> {
  private Object levels;
  private int    size;

  static Method getFactorLevelsMethod(Class<?> testClass, Field inField) {
    Method ret = null;
    try {
      try {
        ret = testClass.getMethod(inField.getName());
      } catch (NoSuchMethodException e) {
        ret = testClass.getDeclaredMethod(inField.getName());
      }
    } catch (SecurityException e) {
      Checks.rethrow(e, "JCUnit cannot be run in this environment. (%s:%s)",
          e.getClass()
              .getName(), e.getMessage());
    } catch (NoSuchMethodException e) {
      Checks.rethrow(e,
          "Method to generate a domain for '%s' isn't defined in class '%s' or not visible.",
          inField, testClass);
    }
    if (!validateDomainMethod(inField, ret)) {
      String msg = String.format(
          "Domain method '%s' isn't compatible with field '%s'", ret, inField);
      throw new IllegalArgumentException(msg, null);
    }
    return ret;
  }

  private static boolean checkIfStatic(Method domainMethod) {
    return Modifier.isStatic(domainMethod.getModifiers());
  }

  private static boolean checkIfTypeCompatible(Field inField,
      Method domainMethod) {
    return domainMethod.getReturnType().isArray()
        && domainMethod.getReturnType().getComponentType() == inField.getType();
  }

  private static boolean checkIfAnyParameterExists(Method domainMethod) {
    return domainMethod.getParameterTypes().length != 0;
  }

  private static boolean checkIfReturnTypeIsArray(Method domainMethod) {
    return domainMethod.getReturnType().isArray();
  }

  private static boolean validateDomainMethod(Field inField,
      Method domainMethod) {
    boolean ret;
    ret = checkIfStatic(domainMethod);
    ret &= !checkIfAnyParameterExists(domainMethod);
    ret &= checkIfReturnTypeIsArray(domainMethod);
    ret &= checkIfTypeCompatible(inField, domainMethod);
    return ret;
  }

  @Override public ParamType[] parameterTypes() {
    return new ParamType[0];
  }

  @Override protected void init(Field targetField,
      FactorField annotation, Object[] parameters) {
    Method levelsMethod = getFactorLevelsMethod(targetField.getDeclaringClass(),
        targetField);
    ////
    // It is guaranteed that levelsMethod is static by validation in 'getFactorLevelsMethod'.
    this.levels = Utils.invokeMethod(null, levelsMethod);
    this.size = Array.getLength(this.levels);
  }

  @Override public int size() {
    return this.size;
  }

  @Override public T get(int index) {
    return (T) Array.get(levels, index);
  }

}

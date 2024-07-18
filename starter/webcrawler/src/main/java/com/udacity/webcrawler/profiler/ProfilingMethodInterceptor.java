package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.annotation.Annotation;
import java.time.Clock;
import java.util.Objects;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object delegate;
  private final ProfilingState state;

  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, Object delegate, ProfilingState state)  {
    this.clock = Objects.requireNonNull(clock);
    this.delegate = delegate;
    this.state = state;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable{
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.
    if (checkProfiledAnnotation(method)) {
      return invokeProfiledMethod(method, args);
    } else {
      return invokeNonProfiledMethod(method, args);
    }

  }

  private Object invokeProfiledMethod(Method method, Object[] args) throws Throwable {
    Instant start = clock.instant();
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } finally {
      Duration duration = Duration.between(start, clock.instant());
      state.record(delegate.getClass(), method, duration);
    }
  }

  private Object invokeNonProfiledMethod(Method method, Object[] args) throws Throwable {
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean checkProfiledAnnotation(Method method){
    return Arrays.stream(method.getAnnotations())
            .anyMatch(annotation -> annotation instanceof Profiled);
  }
}

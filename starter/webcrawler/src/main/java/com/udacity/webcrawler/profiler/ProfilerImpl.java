package com.udacity.webcrawler.profiler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Arrays;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T wrap(Class<T> klass, T delegate) {

    // TODO: Use a dynamic proxy (java.lang.reflect.Proxy) to "wrap" the delegate in a
    //       ProfilingMethodInterceptor and return a dynamic proxy from this method.
    //       See https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html.
    Objects.requireNonNull(klass);
    boolean flag = Arrays.stream(klass.getDeclaredMethods())
            .flatMap(method -> Arrays.stream(method.getAnnotations()))
            .anyMatch(annotation -> annotation.annotationType() == Profiled.class);

    if (!flag) throw new IllegalArgumentException();
    return (T) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
            new Class<?>[]{klass},
            new ProfilingMethodInterceptor(clock, delegate, state));

  }

  @Override
  public void writeData(Path path) {
    // TODO: Write the ProfilingState data to the given file path. If a file already exists at that
    //       path, the new data should be appended to the existing file.
    Objects.requireNonNull(path);
    try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
      writeData(writer);
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}

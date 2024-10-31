package org.miracum.streams.ume.obdstofhir.mapper;

import java.util.function.Consumer;

/** This class contains some useful util functions to be used within mapper classes */
public class MapperUtils {

  private MapperUtils() {}

  /**
   * Executes given lambda function if, and only if, given value is NULL
   *
   * @param value The value to be checked to be not NULL
   * @param runnable The function to be executed if value is not NULL
   */
  public static void ifNull(Object value, Runnable runnable) {
    if (value == null) {
      runnable.run();
    }
  }

  /**
   * Executes given lambda function if, and only if, given value is not NULL
   *
   * @param value The value to be checked to be not NULL
   * @param consumer The function to be executed if value is not NULL
   * @param <T> The values type
   */
  public static <T> void ifNotNull(T value, Consumer<T> consumer) {
    if (value != null) {
      consumer.accept(value);
    }
  }

  /**
   * Executes given lambda function if, and only if, given value is not null. If value is NULL, the
   * given RuntimeException will be thrown
   *
   * @param value The value to be checked to be not null
   * @param consumer The function to be executed if value is not null
   * @param elseException The exception to be thrown if the value is null
   * @param <T> The values type
   */
  public static <T> void ifNotNull(T value, Consumer<T> consumer, RuntimeException elseException) {
    if (value != null) {
      consumer.accept(value);
      return;
    }
    throw elseException;
  }
}

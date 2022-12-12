package org.miracum.streams.ume.onkoadttofhir.model;

public class Tupel<T, U> {

  private final T first;
  private final U second;

  public Tupel(T first, U second) {
    this.first = first;
    this.second = second;
  }

  public T getFirst() {
    return first;
  }

  public U getSecond() {
    return second;
  }
}

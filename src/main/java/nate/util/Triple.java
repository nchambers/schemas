package nate.util;


public class Triple {
  Object first;
  Object second;
  Object third;

  public Triple(Object one, Object two, Object three) {
    first = one;
    second = two;
    third = three;
  }

  public Object first() { return first; }
  public Object second() { return second; }
  public Object third() { return third; }
  public void setFirst(Object obj) { first = obj; }
  public void setSecond(Object obj) { second = obj; }
  public void setThird(Object obj) { third = obj; }
  
  public String toString() {
    return (first + ":" + second + ":" + third);
  }
}
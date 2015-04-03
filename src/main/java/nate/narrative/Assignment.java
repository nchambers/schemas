package nate.narrative;

/**
 * A handy class to pass around event to chain assignments.
 * It also includes an arg field to specify when a chain should be constrained
 * to only that argument type.
 */
public class Assignment {
  String event = null;
  int chainID = -1;
  String arg = null;
  float score;

  Assignment(String event, String arg, int id, float score) {
    this.event = event;
    this.chainID = id;
    this.arg = arg;
    this.score = score;
  }

  public String toString() {
    return event + "(" + arg + ") -> " + chainID + " = " + score;
  }

  public String event() { return event; }
  public int chainID() { return chainID; }
  public String arg() { return arg; }
  public float score() { return score; }
}

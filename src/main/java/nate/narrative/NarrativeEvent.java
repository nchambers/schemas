package nate.narrative;

/**
 * Stupid Java class for an event and its score within a narrative
 * This was made just to facilitate sorting by score...
 */
public class NarrativeEvent implements Comparable {
  String event;
  float score;


  public NarrativeEvent(String e, float s) {
    event = e;
    score = s;
  }

  public float score() { return score; }
  public String event() { return event; }

  public int compareTo(Object o){
    //    System.out.println(this + " -- " + (NarrativeEvent)o + " = " +
    //		       (int)(((NarrativeEvent)o).score - score));
    float diff = ((NarrativeEvent)o).score - score;
    if( diff > 0.0f ) return 1;
    if( diff < 0.0f ) return -1;
    else return 0;
  }

  public String toString() {
    return (event + ": " + score);
  }
}
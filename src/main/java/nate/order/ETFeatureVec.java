package nate.order;

import java.util.HashMap;

import nate.order.tb.TLink;

/**
 * A feature vector for an Event-Time tlink
 */

public class ETFeatureVec extends Features {

  private String event;
  private String timex;
  private String relation; // BEFORE, SIMULTANEOUS, etc...

  // The relation depends on the order of the event and time!
  public static int EVENT_TIME = 0;
  public static int TIME_EVENT = 1;
  private int order = EVENT_TIME;

  // Store all the features here
  private String features[] = new String[16];


  ETFeatureVec(String eid,String tid) { 
    event = eid;
    timex = tid;
    makeDefaults();
  }

  private void makeDefaults() {
    features[ETFeatureType.TENSE.ordinal()] = "1";
    features[ETFeatureType.ASPECT.ordinal()] = "1";
    features[ETFeatureType.MODAL.ordinal()] = "1";
    features[ETFeatureType.POLARITY.ordinal()] = "1";
    features[ETFeatureType.CLASS.ordinal()] = "1";
  }

  public void set(ETFeatureType feat, String val) {
    features[feat.ordinal()] = val;
  }
  public String get(ETFeatureType feat) {
    return features[feat.ordinal()];
  }


  public void setPOS(int[] p) {
    if( p != null && p.length == 4 ) {
      features[ETFeatureType.POS0.ordinal()] = String.valueOf(p[0]);
      features[ETFeatureType.POS1.ordinal()] = String.valueOf(p[1]);
      features[ETFeatureType.POS2.ordinal()] = String.valueOf(p[2]);
      features[ETFeatureType.POS3.ordinal()] = String.valueOf(p[3]);
    }
  }
  public void setTense(int t)    { features[ETFeatureType.TENSE.ordinal()] = String.valueOf(t); }
  public void setAspect(int t)   { features[ETFeatureType.ASPECT.ordinal()] = String.valueOf(t); }
  public void setModality(int t) { features[ETFeatureType.MODAL.ordinal()] = String.valueOf(t); }
  public void setClass(int t)    { features[ETFeatureType.CLASS.ordinal()] = String.valueOf(t); }
  public void setPolarity(int t) { features[ETFeatureType.POLARITY.ordinal()] = String.valueOf(t); }

  public void setWord(String w) { features[ETFeatureType.WORD.ordinal()] = w; }
  public void setLemma(String l) { features[ETFeatureType.LEMMA.ordinal()] = l; }
  public void setSynset(String s) { features[ETFeatureType.SYNSET.ordinal()] = s; }

  public void setRelation(String rel, int order) { 
    relation = rel;
    this.order = order;
  }

  public String event() { return event; }
  public String timex() { return timex; }
  // these are for abstract Feature vectors to call...
  public String event1() { return event; }
  public String event2() { return timex; }
  
  public String word() { return features[ETFeatureType.WORD.ordinal()]; }
  public String lemma() { return features[ETFeatureType.LEMMA.ordinal()]; }
  public String synset() { return features[ETFeatureType.SYNSET.ordinal()]; }
  public String relation() { return relation; }


  /**
   * Create a new instance from a string of features
   * @return A new ETFeatureVec
   */
  public static ETFeatureVec fromString(String str) {
    String parts[] = str.split("\\s+");

    if( parts.length != (ETFeatureType.values().length+3) ) { // +1 because the relation is there    
      //    if( parts.length != (ETFeatureType.values().length+2) ) { // +2 because of the event/timex IDs
      System.err.println("String does not match size of ETFeatureVec");
      System.err.println("  (" + str + ")");
      return null;
    }

    // Figure out if the time is first, or if the event is first
    String ee = parts[0];
    String tt;
    int order = ETFeatureVec.EVENT_TIME;
    if( ee.charAt(0) == 't' ) {
      tt = ee;
      ee = parts[1];
      order = ETFeatureVec.TIME_EVENT;
    } else tt = parts[1];

    ETFeatureVec vec = new ETFeatureVec(ee,tt);
    vec.setRelation(parts[2],order);

    for( int i = 0; i < ETFeatureType.values().length; i++ )
      vec.set(ETFeatureType.values()[i], parts[i+3]);

    return vec;
  }

  public String features() {
    String str;
    if( order == EVENT_TIME ) str = event + " " + timex + " " + relation;
    else {
      int rel = Integer.valueOf(relation);
      String srel = Closure.intToStringRelation(rel);
      TLink.TYPE therel = TLink.TYPE.valueOf(srel);
      //	str = timex + " " + event + " " + relation;
      if( therel == TLink.TYPE.BEFORE )
        str = event + " " + timex + " " + TLink.TYPE.AFTER;
      else if( therel == TLink.TYPE.AFTER )
        str = event + " " + timex + " " + TLink.TYPE.BEFORE;
      else // overlap can be inverted
        str = event + " " + timex + " " + relation;
    }

    for( int i = 0; i < ETFeatureType.values().length; i++ )
      str += " " + features[i];
    return str;
  }

  public String toString() {
    return features();
  }
}

package nate.order;

import java.util.HashMap;

/**
 * A feature vector for a single Event
 */

public class SingleFeatureVec extends Features {

  private String event;
  private String relation; // TENSE, ASPECT, etc...

  // Store all the features here
  private String features[] = new String[16];

  public SingleFeatureVec(String eid) { 
    event = eid;
    makeDefaults();
  }

  public SingleFeatureVec(String eid,int[] ps) { 
    this(eid);
    setPOS(ps);
  }

  private void makeDefaults() {
    features[EFeatureType.MODAL_WORD.ordinal()] = "1";
    features[EFeatureType.HAVE_WORD.ordinal()] = "1";
    features[EFeatureType.BE_WORD.ordinal()] = "1";
    features[EFeatureType.NOT_WORD.ordinal()] = "1";
  }

  public void set(EFeatureType feat, String val) {
    features[feat.ordinal()] = val;
  }
  public void set(int feat, String val) {
    features[feat] = val;
  }
  public String get(EFeatureType feat) {
    return features[feat.ordinal()];
  }

  /**
   * Since this is a single Event feature list, the "relation" of the event
   * can be the tense, aspect, or the class...so the API lets you set which
   * you want it to be.
   */
  public void setRelation(String feat) {
    relation = feat;
  }
  public String relation() {
    if( relation.equals("TENSE") )
      return features[EFeatureType.TENSE.ordinal()];
    else if( relation.equals("ASPECT") )
      return features[EFeatureType.ASPECT.ordinal()];
    else if( relation.equals("CLASS") )
      return features[EFeatureType.CLASS.ordinal()];

    return null;
  }

  public void setPOS(int[] p) {
    if( p != null && p.length == 4 ) {
      features[EFeatureType.POS0.ordinal()] = String.valueOf(p[0]);
      features[EFeatureType.POS1.ordinal()] = String.valueOf(p[1]);
      features[EFeatureType.POS2.ordinal()] = String.valueOf(p[2]);
      features[EFeatureType.POS3.ordinal()] = String.valueOf(p[3]);
    }
  }
  public void setTense(int t)    { features[EFeatureType.TENSE.ordinal()] = String.valueOf(t); }
  public void setAspect(int t)   { features[EFeatureType.ASPECT.ordinal()] = String.valueOf(t); }
  public void setModality(int t) { features[EFeatureType.MODAL.ordinal()] = String.valueOf(t); }
  public void setClass(int t)    { features[EFeatureType.CLASS.ordinal()] = String.valueOf(t); }
  public void setPolarity(int t) { features[EFeatureType.POLARITY.ordinal()] = String.valueOf(t); }

  public void setModalWord(String w) { features[EFeatureType.MODAL_WORD.ordinal()] = w; }
  public void setHaveWord(String w) { features[EFeatureType.HAVE_WORD.ordinal()] = w; }
  public void setBeWord(String w) { features[EFeatureType.BE_WORD.ordinal()] = w; }
  public void setNotWord(boolean b) { 
    if( b ) features[EFeatureType.NOT_WORD.ordinal()] = "2";
    else features[EFeatureType.NOT_WORD.ordinal()] = "1";
  }

  public void setWord(String w) { features[EFeatureType.WORD.ordinal()] = w; }
  public void setLemma(String l) { features[EFeatureType.LEMMA.ordinal()] = l; }
  public void setSynset(String s) { features[EFeatureType.SYNSET.ordinal()] = s; }

  public String event() { return event; }
  public String modalWord() { return features[EFeatureType.MODAL_WORD.ordinal()]; }
  public String haveWord() { return features[EFeatureType.HAVE_WORD.ordinal()]; }
  public String beWord() { return features[EFeatureType.BE_WORD.ordinal()]; }
  public String word() { return features[EFeatureType.WORD.ordinal()]; }
  public String lemma() { return features[EFeatureType.LEMMA.ordinal()]; }
  public String synset() { return features[EFeatureType.SYNSET.ordinal()]; }


  /**
   * Create a new instance from a string of features
   * @return A new SingleFeatureVec
   */
  public static SingleFeatureVec fromString(String str, String relation) {
    SingleFeatureVec vec = fromString(str);
    vec.setRelation(relation);
    return vec;
  }

  /**
   * Create a new instance from a string of features
   * @return A new SingleFeatureVec
   */
  public static SingleFeatureVec fromString(String str) {
    String parts[] = str.split("\\s+");
    
    if( parts.length != (EFeatureType.values().length+1) ) { // +1 because the event ID is here
      System.err.println("String does not match size of SingleFeatureVec");
      System.err.println("  (" + str + ")");
      return null;
    }

    SingleFeatureVec vec = new SingleFeatureVec(parts[0],null);
    for( int i = 0; i < EFeatureType.values().length; i++ )
      vec.set(EFeatureType.values()[i], parts[i+1]);

    return vec;
  }

  public String features() {
    String str = event;
    for( int i = 0; i < EFeatureType.values().length; i++ )
      str += " " + features[i];
    return str;
  }

  public String toString() {
    return features();
  }
}

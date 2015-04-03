package nate.order;

import java.util.HashMap;
import java.util.Iterator;

public class FeatureVector extends Features {

  // Store all the features here
  private String features[] = new String[39];
  private String event1,event2,relation; // event IDs in String form


  /**
   * Constructors
   */
  public FeatureVector(String one, String two, String rel) {
    event1 = one;
    event2 = two;
    relation = rel;
  }
  public FeatureVector(String one, String two) {
    event1 = one;
    event2 = two;
  }


  public void setWordOne(String word) { features[FeatureType.WORD1.ordinal()] = word; }
  public void setWordTwo(String word) { features[FeatureType.WORD2.ordinal()] = word; }

  public void setLemmaOne(String word) { features[FeatureType.LEMMA1.ordinal()] = word; }
  public void setLemmaTwo(String word) { features[FeatureType.LEMMA2.ordinal()] = word; }

  public void setSynsetOne(long w) { features[FeatureType.SYNSET1.ordinal()] = String.valueOf(w); }
  public void setSynsetOne(String w) { features[FeatureType.SYNSET1.ordinal()] = w; }
  public void setSynsetTwo(long w) { features[FeatureType.SYNSET2.ordinal()] = String.valueOf(w); }
  public void setSynsetTwo(String w) { features[FeatureType.SYNSET2.ordinal()] = w; }


  // Event features
  public void setMatchTense(int t) { features[FeatureType.TENSE_MATCH.ordinal()] = String.valueOf(t); }
public void setMatchAspect(int t) { features[FeatureType.ASPECT_MATCH.ordinal()] = String.valueOf(t); }
  public void setMatchClass(int t) { features[FeatureType.CLASS_MATCH.ordinal()] = String.valueOf(t); }
  
  public void setTenses(int t, int t2) { 
    features[FeatureType.TENSE1.ordinal()] = String.valueOf(t);
    features[FeatureType.TENSE2.ordinal()] = String.valueOf(t2);
    if( t == t2 ) setMatchTense(2);
  }
  public void setAspects(int t, int t2) { 
    features[FeatureType.ASPECT1.ordinal()] = String.valueOf(t);
    features[FeatureType.ASPECT2.ordinal()] = String.valueOf(t2);
    if( t == t2 ) setMatchAspect(2);
  }
  public void setModalities(int t, int t2) { 
    features[FeatureType.MODAL1.ordinal()] = String.valueOf(t);
    features[FeatureType.MODAL2.ordinal()] = String.valueOf(t2);
  }
  public void setPolarities(int t, int t2) { 
    features[FeatureType.POLARITY1.ordinal()] = String.valueOf(t);
    features[FeatureType.POLARITY2.ordinal()] = String.valueOf(t2);
  }
  public void setClasses(int t, int t2) { 
    features[FeatureType.CLASS1.ordinal()] = String.valueOf(t);
    features[FeatureType.CLASS2.ordinal()] = String.valueOf(t2);
    if( t == t2 ) setMatchClass(2);
  }

  public void setPOS1(int[] pos) {
    features[FeatureType.POS1_0.ordinal()] = String.valueOf(pos[0]);
    features[FeatureType.POS1_1.ordinal()] = String.valueOf(pos[1]);
    features[FeatureType.POS1_2.ordinal()] = String.valueOf(pos[2]);
    features[FeatureType.POS1_3.ordinal()] = String.valueOf(pos[3]);
  }
  public void setPOS2(int[] pos) {
    features[FeatureType.POS2_0.ordinal()] = String.valueOf(pos[0]);
    features[FeatureType.POS2_1.ordinal()] = String.valueOf(pos[1]);
    features[FeatureType.POS2_2.ordinal()] = String.valueOf(pos[2]);
    features[FeatureType.POS2_3.ordinal()] = String.valueOf(pos[3]);
  }

  // Preposition
  public void setPrep(String event,String prep) {
    int pid = 1;
    if( prep.equalsIgnoreCase("during") ) pid = 2;
    else if( prep.equalsIgnoreCase("with") ) pid = 3;
    else if( prep.equalsIgnoreCase("on") ) pid = 4;
    else if( prep.equalsIgnoreCase("among") ) pid = 5;
    else if( prep.equalsIgnoreCase("of") ) pid = 6;
    else if( prep.equalsIgnoreCase("for") ) pid = 7;
    else if( prep.equalsIgnoreCase("at") ) pid = 8;
    else if( prep.equalsIgnoreCase("in") ) pid = 9;
    else if( prep.equalsIgnoreCase("as") ) pid = 10;
    else if( prep.equalsIgnoreCase("by") ) pid = 11;
    else if( prep.equalsIgnoreCase("before") ) pid = 12;
    else if( prep.equalsIgnoreCase("into") ) pid = 13;
    else if( prep.equalsIgnoreCase("about") ) pid = 14;
    else if( prep.equalsIgnoreCase("from") ) pid = 15;
    else if( prep.equalsIgnoreCase("after") ) pid = 16;
    else if( prep.equalsIgnoreCase("since") ) pid = 17;
    else if( prep.equalsIgnoreCase("over") ) pid = 18;
    else if( prep.equalsIgnoreCase("without") ) pid = 19;
    else if( prep.equalsIgnoreCase("out") ) pid = 20;
    else if( prep.equalsIgnoreCase("despite") ) pid = 21;
    else if( prep.equalsIgnoreCase("between") ) pid = 22;
    else if( prep.equalsIgnoreCase("behind") ) pid = 23;
    else if( prep.equalsIgnoreCase("through") ) pid = 24;
    else if( prep.equalsIgnoreCase("under") ) pid = 25;
    else if( prep.equalsIgnoreCase("amid") ) pid = 26;
    else if( prep.equalsIgnoreCase("because") ) pid = 27;
    else if( prep.equalsIgnoreCase("besides") ) pid = 28;
    else if( prep.equalsIgnoreCase("if") ) pid = 29;
    else if( prep.equalsIgnoreCase("toward") ) pid = 30;
    else if( prep.equalsIgnoreCase("while") ) pid = 31;
    else if( prep.equalsIgnoreCase("against") ) pid = 32;
    else if( prep.equalsIgnoreCase("throughout") ) pid = 33;
    else if( prep.equalsIgnoreCase("than") ) pid = 34;
    else if( prep.equalsIgnoreCase("beyond") ) pid = 35;
    else if( prep.equalsIgnoreCase("up") ) pid = 36;
    else if( prep.equalsIgnoreCase("like") ) pid = 37;
    else if( prep.equalsIgnoreCase("upon") ) pid = 38;
    else if( prep.equalsIgnoreCase("inside") ) pid = 39;
    else if( prep.equalsIgnoreCase("around") ) pid = 40;
    else System.out.println("UNKNOWN PREP: " + prep);

    if( event1.equalsIgnoreCase(event) ) features[FeatureType.PREP1.ordinal()] = String.valueOf(pid);
    else if( event2.equalsIgnoreCase(event) ) features[FeatureType.PREP2.ordinal()] = String.valueOf(pid);
    else System.err.println("Bad preposition set...(can't set pid=" + pid + ")");
  }

  /**
   * Retrieve a feature's value
   */
  public String get(FeatureType feat) {
    return features[feat.ordinal()];
  }
  public void set(FeatureType feat, String value) {
    features[feat.ordinal()] = value;
  }
  public void set(FeatureType feat, int value) {
    features[feat.ordinal()] = String.valueOf(value);
  }

  public void setRelation(String rel) { 
    relation = rel;
  }

  public void setBefore(boolean b) { 
    if( b ) features[FeatureType.BEFORE.ordinal()] = "1";
    else features[FeatureType.BEFORE.ordinal()] = "2";
  }
  public void setSameSentence(boolean b) { 
    if( b ) features[FeatureType.SAME_SENTENCE.ordinal()] = "1";
    else features[FeatureType.SAME_SENTENCE.ordinal()] = "2";
  }
  public void setEntityMatch(int e) { features[FeatureType.ENTITY_MATCH.ordinal()] = String.valueOf(e); }
  public void setDominance(int d) { features[FeatureType.DOMINANCE.ordinal()] = String.valueOf(d); }

  // Equality check between two events and this feature vector
  public boolean equals(String e1, String e2) {
    if( event1.equals(e1) && event2.equals(e2) ) return true;
    else return false;
  }

  // Retrieval functions
  public String event1() { return event1; }
  public String event2() { return event2; }
  public String relation() { return relation; }
  public int size() { return features.length; }


  public static FeatureVector fromString(String str) {
    //    System.out.println("Converting: " + str);
    String parts[] = str.split("\\s+");
    FeatureVector vec = new FeatureVector(parts[0],parts[1]);
    
    if( parts.length != (FeatureType.values().length+3) ) { // +1 because the relation is there
      System.err.println("String size " + parts.length + " does not match size of FeatureVector " + (FeatureType.values().length+3));
      System.err.println("  (" + str + ")");
      return null;
    }

    vec.setRelation(parts[2]);
    for( int i = 0; i < FeatureType.values().length; i++ ) {
      vec.set(intToType(i), parts[i+3]);
    }

    return vec;
  }

  /**
   * Convert an integer to the feature type enum
   */
  public static FeatureType intToType(int i) {
    return FeatureType.values()[i];
  }

  /** 
   * Return all the features in a space-delimited list (tlink relation is first)
   * @return features The String of features
   */
  public String features() {
    //    String str = relation() + " ";
    String str = event1() + " " + event2() + " " + relation() + " ";
    String temp;
    for( int i = 0; i < FeatureType.values().length; i++ ) {
      temp = features[i];
      if( temp == null || temp.length() == 0 ) temp = "1";
      str += temp + " ";
    }
    return str.trim();
  }

  public String toString() {
    return event1 + " " + event2 + ": " + features();
  }
}

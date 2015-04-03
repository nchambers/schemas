package nate.order;



public class Features {

  // Class sizes
  public static int TENSE_SIZE = 4;
  public static int ASPECT_SIZE = 4;
  public static int CLASS_SIZE = 7;
  public static int POS_SIZE = 42;
  // Dominance values
  public static int DOMINATES = 2;
  public static int DOMINATED = 3;

  public static int tenseToNum(String x) {
    if( x.equalsIgnoreCase("PAST") ) return 2;
    else if( x.equalsIgnoreCase("PRESENT") ) return 3;
    else if( x.equalsIgnoreCase("FUTURE") ) return 4;
    else return 1;
  }
  public static int aspectToNum(String x) {
    if( x.equalsIgnoreCase("PROGRESSIVE") ) return 2;
    else if( x.equalsIgnoreCase("PERFECTIVE") ) return 3;
    else if( x.equalsIgnoreCase("PERFECTIVE_PROGRESSIVE") ) return 4;
    else return 1;
  }
  public static int modalityToNum(String x) {
    // ignore: must, unlikely, close, have (only appears once)
    if( x.equalsIgnoreCase("to") ) return 2;
    else if( x.equalsIgnoreCase("should") ) return 3;
    else if( x.equalsIgnoreCase("could") ) return 4;
    else if( x.equalsIgnoreCase("can") ) return 5;
    else if( x.equalsIgnoreCase("might") ) return 6;
    else if( x.equalsIgnoreCase("would") ) return 7;
    else return 1;
  }
  public static int classToNum(String x) {
    if( x.equalsIgnoreCase("REPORTING") ) return 2;
    else if( x.equalsIgnoreCase("ASPECTUAL") ) return 3;
    else if( x.equalsIgnoreCase("STATE") ) return 4;
    else if( x.equalsIgnoreCase("I_STATE") ) return 5;
    else if( x.equalsIgnoreCase("I_ACTION") ) return 6;
    else if( x.equalsIgnoreCase("PERCEPTION") ) return 7;
    else return 1; // OCCURRENCE
  }
  public static int polarityToNum(String x) {
    if( x.equalsIgnoreCase("NEG") ) return 2;
    else return 1;
  }

  public static int posToNum(String p) {
    if( p.equalsIgnoreCase("CC") ) return 2;
    else if( p.equalsIgnoreCase("CD") ) return 3;
    else if( p.equalsIgnoreCase("DT") ) return 4;
    else if( p.equalsIgnoreCase("EX") ) return 5;
    else if( p.equalsIgnoreCase("FW") ) return 6;
    else if( p.equalsIgnoreCase("IN") ) return 7;
    else if( p.equalsIgnoreCase("JJ") ) return 8;
    else if( p.equalsIgnoreCase("JJR") ) return 9;
    else if( p.equalsIgnoreCase("JJS") ) return 10;
    else if( p.equalsIgnoreCase("LS") ) return 11;
    else if( p.equalsIgnoreCase("MD") ) return 12;
    else if( p.equalsIgnoreCase("NN") ) return 13;
    else if( p.equalsIgnoreCase("NNS") ) return 14;
    else if( p.equalsIgnoreCase("NNP") ) return 15;
    else if( p.equalsIgnoreCase("NNPS") ) return 16;
    else if( p.equalsIgnoreCase("PDT") ) return 17;
    else if( p.equalsIgnoreCase("POS") ) return 18;
    else if( p.equalsIgnoreCase("PRP") ) return 19;
    else if( p.equalsIgnoreCase("PRP$") ) return 20;
    else if( p.equalsIgnoreCase("RB") ) return 21;
    else if( p.equalsIgnoreCase("RBR") ) return 22;
    else if( p.equalsIgnoreCase("RBS") ) return 23;
    else if( p.equalsIgnoreCase("RP") ) return 24;
    else if( p.equalsIgnoreCase("SYM") ) return 25;
    else if( p.equalsIgnoreCase("TO") ) return 26;
    else if( p.equalsIgnoreCase("UH") ) return 27;
    else if( p.equalsIgnoreCase("VB") ) return 28;
    else if( p.equalsIgnoreCase("VBD") ) return 29;
    else if( p.equalsIgnoreCase("VBG") ) return 30;
    else if( p.equalsIgnoreCase("VBN") ) return 31;
    else if( p.equalsIgnoreCase("VBP") ) return 32;
    else if( p.equalsIgnoreCase("VBZ") ) return 33;
    else if( p.equalsIgnoreCase("WDT") ) return 34;
    else if( p.equalsIgnoreCase("WP") ) return 35;
    else if( p.equalsIgnoreCase("WP$") ) return 36;
    else if( p.equalsIgnoreCase("WRB") ) return 37;
    else if( p.equalsIgnoreCase(".") ) return 38;
    else if( p.equalsIgnoreCase(",") ) return 39;
    else if( p.equalsIgnoreCase("$") ) return 40;
    else if( p.equalsIgnoreCase(":") ) return 41;
    else if( p.equalsIgnoreCase("``") || p.equalsIgnoreCase("''") ) return 42;
    else { 
      // don't complain about parentheses
      if( !p.equals("-RRB-") && !p.equals("-LRB-") )
	System.out.println("New POS: " + p); 
      return 1; 
    }
  }

  public static int isAuxHave(String w) {
    if( w.equalsIgnoreCase("have") ) return 2;
    else if( w.equalsIgnoreCase("had") ) return 3;
    else if( w.equalsIgnoreCase("has") ) return 4;
    else if( w.equalsIgnoreCase("having") ) return 5;
    else return 1;
  }

  public static int isAuxBe(String w) {
    if( w.length() < 5 ) {
      if( w.equalsIgnoreCase("is") ) return 2;
      else if( w.equalsIgnoreCase("was") ) return 3;
      else if( w.equalsIgnoreCase("were") ) return 4;
      else if( w.equalsIgnoreCase("are") ) return 5;
      else if( w.equalsIgnoreCase("be") ) return 6;
      else if( w.equalsIgnoreCase("am") ) return 7;
      else if( w.equalsIgnoreCase("been") ) return 8;
    }
    return 1;
  }

  public static boolean isNegation(String w) {
    if( w.equalsIgnoreCase("not") || w.equalsIgnoreCase("n't") )
      return true;
    return false;
  }

  /**
   * This differs from modalityToNum above because we cover all
   * possible modal MD tags ... not just a subset of Timebank ones.
   */
  public static int isModal(String x) {
    // ignore: unlikely, close, have (only appears once)

    // most strings are "1" coming in...
    if( x.length() == 1 ) return 1;
    else if( x.equalsIgnoreCase("to") ) return 2;
    else if( x.equalsIgnoreCase("should") ) return 3;
    else if( x.equalsIgnoreCase("could") ) return 4;
    else if( x.equalsIgnoreCase("can") || x.equalsIgnoreCase("ca") ) return 5;
    else if( x.equalsIgnoreCase("might") ) return 6;
    else if( x.equalsIgnoreCase("would") || x.equalsIgnoreCase("'d") ) return 7;
    else if( x.equalsIgnoreCase("will") || x.equalsIgnoreCase("wo") ||
	     x.equalsIgnoreCase("'ll") || x.equalsIgnoreCase("shall") ) return 8;
    else if( x.equalsIgnoreCase("must") ) return 9;
    else if( x.equalsIgnoreCase("may") ) return 10;

    //    System.out.println("MODAL ERROR: unknown modal " + x);
    return 1;
  }


 /**
   * These should be overridden
   */
  public String event1() { return ""; }
  public String event2() { return ""; }
 
  /**
   * This function should be overridden
   */
  public String relation() { return ""; }
  /**
   * This function should be overridden
   */
  public String get(String feat) { return ""; }
  public String get(FeatureType feat) { return ""; }
  public String get(Object feat) { return ""; }
  /**
   * This function should be overridden
   */
  public String features() { return "[features]"; }
  /**
   * This function should be overridden
   */
  public String toString() { return features(); }
}

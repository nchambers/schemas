package nate;

import java.util.HashMap;
import java.util.Vector;


public class WordEvent {
  public static final String DEP_SUBJECT = "s";
  public static final String DEP_OBJECT  = "o";
  public static final String DEP_PREP    = "p";

  public static final int VERB = 0;
  public static final int NOUN = 1;
  public static final int JJ = 2;

  String token;
  int posTag;
  int eventID;
  int sentenceID; // indexed from 1, not 0
  int position; // in the sentence
  HashMap<Integer, String> arguments = null;
  HashMap<Integer, EntityMention> argumentMentions = null;
  // Entity ID to the Index in the sentence that is the head in the dependency.
  HashMap<Integer, Integer> argumentIndices = null;


  public WordEvent(String word, int pos, int sid) {
    this(0,word,pos,sid);  
  }
  public WordEvent(String word, int pos, int sid, String tag) {
    this(0,word,pos,sid);
    setPOSTag(tag);
  }
  public WordEvent(int eid, String word, int pos, int sid) {
    eventID = eid;
    token = word; 
    position = pos;
    sentenceID = sid;
  }

  /*
  private void addSubject(int id) {
    if( subjects == null ) subjects = new Vector();
    subjects.add(id);
  }

  private void addObject(int id) {
    if( objects == null ) objects = new Vector();
    objects.add(id);
  }
   */

  public void setSentenceID(int id) {
    sentenceID = id;
  }

  public void setPosition(int pos) {
    position = pos;
  }

  public void setToken(String w) {
    token = w;
  }

  public void setPOSTag(int tag) {
    posTag = tag;
  }

  /**
   * Set the POS tag for this event using the string representation.
   * e.g. NN, NNS, VBG, JJ, etc.
   */
  public void setPOSTag(String tag) {
    int num = posTagType(tag);
    if( num == -1 ) {
      System.out.println("ERROR: unknown tag string " + tag);
      System.exit(1);
    }
    else setPOSTag(num);
  }

  /**
   * @return The int representing the POS tag.  
   *         -1 if not a noun, verb or adjective.
   */
  public static int posTagType(String tag) {
    if( tag.startsWith("V") || tag.startsWith("v") )
      return VERB;
    else if( tag.startsWith("N") || tag.startsWith("n") )
      return NOUN;
    else if( tag.startsWith("N") || tag.startsWith("n") )
      return JJ;
    else
      return -1;
  }

  /**
   * Build a concatenated version of a word with its posTag type.
   */
  public static String stringify(String word, int posTag) {
    return word + "*" + posTag;
  }

  /**
   * Check a string for a POS tag: "arrest*0" and return just the
   * word without the tag: "arrest"
   */
  public static String stripWordFromPOSTag(String str) {
    int marker = str.length()-2;
    if( str.charAt(marker) == '*' )
      return str.substring(0, marker);
    else return str;
  }

  public static String stripPOSTagFromWord(String str) {
    int marker = str.length()-2;
    if( str.charAt(marker) == '*' )
      return str.substring(marker+1);
    else return str;
  }

  /**
   * Don't normalize the relation string.
   */
  public void addArgAsIs(String relation, int argid) {
    if( arguments == null ) arguments = new HashMap<Integer, String>(8);
    arguments.put(argid, relation);
  }

  public void addArg(String relation, int argid) {
    addArg(relation, argid, false);
  }

  /**
   * Adds the relation as an argument, and normalizes the relation type.
   * @param relation The relation (e.g. nsubj)
   * @param argid The ID of the argument filler.
   * @param fullprep True if you want full prep relations: e.g. 'p_during'
   *                 False if just 'p'
   */
  public void addArg(String relation, int argid, boolean fullprep) {
    if( arguments == null ) arguments = new HashMap<Integer, String>(8);
    if( fullprep )
      arguments.put(argid, normalizeRelationFullPrep(relation));
    else
      arguments.put(argid, normalizeRelation(relation));
  }

  /**
   * Adds the relation as an argument and saves the EntityMention. Don't call this one
   * if you don't need the mentions later ... saves space.
   */
  public void addArg(String relation, int index, EntityMention mention, boolean fullprep) {
    addArg(relation, mention.entityID(), fullprep);

    if( argumentMentions == null ) argumentMentions = new HashMap<Integer, EntityMention>(8);
    argumentMentions.put(mention.entityID(), mention);
    
    if( argumentIndices == null ) argumentIndices = new HashMap<Integer, Integer>(8);
    argumentIndices.put(mention.entityID(), index);
  }

  public static String normalizeRelation(String rel) {
    // assume one letter words are already reduced...
    if( rel.length() == 1 ) return rel;

    // nsubj, xsubj, SUBJ
    if( !rel.contains("pass") && // !nsubjpass
        (rel.contains("subj") || rel.startsWith("S") || rel.startsWith("s")) )
      return DEP_SUBJECT;
    // agent
    if( rel.equals("agent") ) 
      return DEP_SUBJECT;
    // prep_X, PPOBJ
    if( rel.startsWith("prep") || rel.startsWith("pp") || rel.equals("PPOBJ") ) 
      return DEP_PREP;
    // dobj, iobj
    if( rel.endsWith("obj") || rel.equals("nsubjpass") ) 
      return DEP_OBJECT;
    // others
    else return rel;
  }

  public static String normalizeRelationFullPrep(String rel) {
    // assume one letter words are already reduced...
    if( rel.length() == 1 ) return rel;

    // nsubj, xsubj, SUBJ
    if( !rel.contains("pass") && // !nsubjpass
        (rel.contains("subj") || rel.startsWith("S") || rel.startsWith("s")) )
      return DEP_SUBJECT;
    // agent
    if( rel.equals("agent") ) 
      return DEP_SUBJECT;
    // prep_X, PPOBJ
    if( rel.startsWith("prep") || rel.startsWith("pp") || rel.equals("PPOBJ") ) {
      int underscore = rel.indexOf('_');
      if( underscore == -1 )
        return DEP_PREP;
      else
        return DEP_PREP + "_" + rel.substring(underscore+1);
    }
    // dobj, iobj
    if( rel.endsWith("obj") || rel.equals("nsubjpass") ) 
      return DEP_OBJECT;
    // others
    else return rel;
  }

  public boolean containsArg(int argid) {
    if( arguments != null && arguments.containsKey(argid) ) return true;
    return false;
  }

  public EntityMention getArgMention(int argid) {
    if( argumentMentions != null )
      return argumentMentions.get(argid);
    else
      return null;
  }

  public Integer getArgIndex(int argid) {
    if( argumentIndices != null )
      return argumentIndices.get(argid);
    else
      return null;
  }

  /**
   * @return True if one integer in the parameter appears in
   * this object's args list.
   */
  /*
  public boolean shares(Vector<Integer> others) {
    if( (subjects != null || objects != null ) && others != null ) {
      for( Integer id : others )
	if( containsArg(id) ) return true;
    }
    return false;
  }
   */

  public boolean shares(Vector<Integer> others) {
    if( arguments != null )
      for( Integer id : arguments.keySet() )
        if( others.contains(id) ) return true;
    return false;
  }

  /**
   * @return A string of the relation pair that is shared
   *         i.e. SUBJECT:OBJECT or OBJECT:PP-OBJECT
   */
  public String sharedArgument(HashMap<Integer,String> others) {
    if( arguments != null && others != null ) {
      for( Integer id : arguments.keySet() ) {
        if( others.containsKey(id) ) 
          return arguments.get(id) + ":" + others.get(id);
      }
    }
    return null;
  }

  /**
   * @return A string ID of the entity that is shared
   */
  public Integer sharedArgumentID(HashMap<Integer,String> others) {
    if( arguments != null && others != null ) {
      for( Integer id : arguments.keySet() ) {
        if( others.containsKey(id) ) 
          return id;
      }
    }
    return null;
  }

  //  public Vector<Integer> subjects() { return subjects; }
  //  public Vector<Integer> objects() { return objects; }
  public HashMap<Integer,String> arguments() { return arguments; }
  public String token() { return token; }
  public int posTag() { return posTag; }
  public int position() { return position; }
  public int eventID() { return eventID; }
  public int sentenceID() { return sentenceID; }

  /**
   * Create a WordEvent instance from a string representation
   * e.g. "124 word 4 51 [ ]"
   *      "124 ask a 4 51 [ ]"
   */
  public static WordEvent fromString(String str) {
    // Messy space finding because the event string can have spaces in it!    
    int space = str.indexOf(' ');
    int space4 = str.indexOf(" [");
    int i = 2;
    while( str.charAt(space4-i) != ' ' ) i++;
    int space3 = space4-i;
    i = 2;
    while( str.charAt(space3-i) != ' ' ) i++;
    int space2 = space3-i;

    //    System.out.println("***\n" + str);
    //    System.out.println(space + " " + space2 + " " + space3 + " " + space4);

    /*
    // be more cautious with the string...
    int space = str.indexOf(' ');
    int space2 = str.indexOf(' ',space+1);
    int space3 = str.indexOf(' ',space2+1);
    int space4 = str.indexOf(' ',space3+1);
     */

    try {
      int eid = Integer.parseInt(str.substring(0,space));
      String word = str.substring(space+1,space2);
      int sid = Integer.parseInt(str.substring(space2+1,space3));
      int pos;
      if( space4 > -1 ) pos = Integer.parseInt(str.substring(space3+1,space4));
      else pos = Integer.parseInt(str.substring(space3+1,str.length()));

      //      System.out.println(new WordEvent(eid,word,pos,sid));
      return new WordEvent(eid,word,pos,sid);
    } catch( Exception ex ) { 
      // Some numbers "1 1/2" get through as events... try recursing
      if( Character.isDigit(str.charAt(space+1)) )
        return fromString(str.substring(space2+1,str.length()));
      else {
        //	ex.printStackTrace(); 
        System.out.println("WordEvent Exception: *" + str + "*");
      }
    }
    return null;
  }

  public String toStringWithMentions() { 
    //    String str = eventID + " " + token + " " + sentenceID + " " + position + " [";
    String str = eventID + " " + token + " " + posTag + " " + sentenceID + " " + position + " [";
    if( argumentMentions != null )
      for( Integer id : argumentMentions.keySet() ) str += " " + arguments.get(id) + "," + argumentMentions.get(id);
    str += " ]";
    return str; 
  }

  public String toStringFull() { 
    //    String str = eventID + " " + token + " " + sentenceID + " " + position + " [";
    String str = eventID + " " + token + " " + posTag + " " + sentenceID + " " + position + " [";
    if( arguments != null )
      for( Integer id : arguments.keySet() ) str += " " + arguments.get(id) + "," + id;
    str += " ]";
    return str; 
  }

  public String toString() { 
    //    String str = eventID + " " + token + " " + sentenceID + " " + position + " [";
    String str = eventID + " " + token + " " + posTag + " " + sentenceID + " " + position + " [";
    if( arguments != null )
      for( Integer id : arguments.keySet() ) str += " " + id;
    str += " ]";
    return str; 
  }
}
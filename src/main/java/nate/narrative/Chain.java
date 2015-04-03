package nate.narrative;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import nate.Pair;

public class Chain {
  // arrest-o
  Set<String> events;
  // arrest
  Set<String> verbs;
  // If not null, is a constraint that this chain must have this argument.
  String arg = null;
  // A possible cache to store the chain's current score.
  float score = 0.0f;
  // Sometimes the need for chain's to be associated with a specific entity
  // is needed.  This is different from "arg" above, which specifies the type.
  String argID = null;

  // Cache to store scores for this chain for each argument.
  // Calculating chain scores is O(n^2), so saving these can be a big gain.
  // **NOTE: this needs to be cleared whenever an event is added or removed!
  Map<String,Float> argScores = null;
  // The best cached argument, and its score
  String bestCachedArg = null;
  Float bestCachedScore = -1.0f;

  // Cache argument to hold the chain score with no arguments considered.
  public static final String NO_ARG = "*none*";

  /**
   * Constructor
   */
  public Chain() {
    events = new HashSet();
    verbs = new HashSet();
  }

  public Chain(Set<String> newEvents) {
    this();
    for( String event : newEvents )
      add(event);
  }

  /**
   * Add an entire other chain's events to this chain.
   */
  public void mergeChain(Chain newchain) {
    for( String event : newchain.events() ) {
      events.add(event);
      // Save the verb
      Pair pair = EventPairScores.split(event);
      verbs.add((String)pair.first());
    }
  }

  public void add(String event) {
    events.add(event);
    // Save the verb
    Pair pair = EventPairScores.split(event);
    verbs.add((String)pair.first());
  }

  public void remove(String event) {
    events.remove(event);
    // Remove the verb
    Pair pair = EventPairScores.split(event);
    verbs.remove((String)pair.first());
  }

  /**
   * The event is unknown, but we want anything with this verb removed.
   */
  public void removeVerb(String verb) {
    Set<String> removal = new HashSet();
    for( String event : events() ) {
      Pair pair = EventPairScores.split(event);
      String v = (String)pair.first();
      if( v.equals(verb) )
	removal.add(event);
    }

    for( String r : removal ) remove(r);
  }

  public String eventAt(int index) {
    int i = 0;
    for( String event : events ) {
      if( i == index ) return event;
      i++;
    }
    return null;
  }

  public void setArg(String arg) {
    this.arg = arg;
  }

  public void setArgID(String id) {
    this.argID = id;
  }

  public boolean containsVerb(String verb) {
    if( verbs != null && verbs.contains(verb) ) return true;
    else return false;
  }

  public boolean containsEvent(String event) {
    if( events != null && events.contains(event) ) return true;
    else return false;
  }

  /**
   * Saves the chain score using the given argument.
   */
  public void cacheArgScore(String arg, Float score) {
    if( argScores == null )
      argScores = new HashMap();
    argScores.put(arg, score);

    if( score > bestCachedScore ) {
      bestCachedScore = score;
      bestCachedArg = arg;
    }
    //    System.out.println("Chain.java caching " + arg + " score " + score);
  }

  /**
   * Return a cached score for an argument, the overall chain score
   * if the protagonist is this argument type.
   */
  public Float getCachedArgScore(String arg) {
    if( argScores != null )
      return argScores.get(arg);
    else
      return null;
  }

  public int argCacheSize() {
    if( argScores != null )
      return argScores.size();
    else return 0;
  }

  /**
   * Clear the cache of argument scores.
   */
  public void clearArgCache() {
    if( argScores != null ) argScores.clear();
    bestCachedScore = -1.0f;
    bestCachedArg = null;
    System.out.println("Chain.java cleared arg cache");
  }

  /**
   * Makes a simple copy of this chain.  Does not copy cached information.
   * The event and verb objects are not copied, still referencing the same objects.
   */
  public Chain clone() {
    Chain copy = new Chain();
    for( String event : this.events() )
      copy.add(event);
    copy.setArgID(this.argID());
    return copy;
  }

  public String toString() {
    String str = "[";
    for( String event : events ) str += " " + event;
    str += " ] a=" + arg;

    if( argScores != null ) {
      str += " (";
      for( Map.Entry<String,Float> entry : argScores.entrySet() ) {
	str += " " + entry.getKey() + " " + entry.getValue();
      }
      str += ")";
    }
    return str;
  }

  /**
   * Same as the normal toString function, but it sorts the argument scores
   * before printing them.  This creates new wrapper objects around the HashMap
   * entries, so it is memory and time intensive.
   */
  public String toString(boolean sorted) {
    if( !sorted ) return toString();

    String str = "[";
    for( String event : events ) str += " " + event;
    str += " ]";
    
    if( arg != null ) str += "  a=" + arg;
    if( argID != null ) str += " id=" + argID;

    if( argScores != null ) {

      // Sort the arguments
      List<CustomEntry> list = new ArrayList();
      for( Map.Entry entry : argScores.entrySet() )
	list.add(new CustomEntry(entry));
      Collections.sort(list);
      
      // Continue printing
      str += " (";
      for( CustomEntry entry : list ) {
	str += " " + entry.getEntry().getKey() + 
	  //	  (new PrintfFormat(" %.3f").sprintf(entry.getEntry().getValue()));
	  String.format(" %.3f", entry.getEntry().getValue());
      }
      str += ")";
    }
    return str;
  }

  public Set<String> events() { return events; }
  public String arg() { return arg; }
  public String argID() { return argID; }
  public float score() { return score; }
  public String bestCachedArg() { return bestCachedArg; }
  public int size() { return events.size(); }


  /**
   * Map wrapper to allow us to sort a Map by value
   */
  public class CustomEntry implements Comparable{
    private Map.Entry entry;

    public CustomEntry(Map.Entry entry) {
      this.entry = entry;
    }

    public Map.Entry getEntry() {
      return this.entry;
    }

    public int compareTo(CustomEntry anotherEntry) {
      Float thisFloat = (Float)(this.getEntry().getValue());
      //      int thisVal = thisFloat.intValue();
      Float anotherFloat = (Float)(anotherEntry.getEntry().getValue());
      //      int anotherVal = anotherFloat.intValue();
      return (thisFloat<anotherFloat ? 1 : (thisFloat==anotherFloat ? 0 : -1));
    }

    public int compareTo(Object o) {
      return compareTo((CustomEntry)o);
    }
  }
}

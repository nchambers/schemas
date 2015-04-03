package nate.narrative;

import java.util.Vector;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.StringReader;

import nate.Pair;
import nate.narrative.Chain;
import nate.cluster.Cluster;


public class Narrative implements nate.cluster.Cluster {
  Vector<Chain> chains;
  Set<String> verbs;
  // Debugging info, a cache of verb scores within this narrative.
  Map<String,Float> verbScores;

  Narrative() {
    chains = new Vector();
    verbs = new HashSet();
  }

  /**
   * Create a new empty chain.
   */
  public int createChain() {
    chains.add(new Chain());
    return chains.size()-1;
  }

  /**
   * Create a new chain and add the given event to it.
   */
  public int createChain(String event) {
    Chain chain = new Chain();
    chain.add(event);
    chains.add(chain);
    // Save the verb
    Pair pair = EventPairScores.split(event);
    verbs.add((String)pair.first());

    // The new chain ID
    return chains.size()-1;
  }

  public int addChain(Chain chain) {
    chains.add(chain);
    for( String event : chain.events() ) {
      Pair pair = EventPairScores.split(event);
      verbs.add((String)pair.first());
    }
    return chains.size()-1;
  }

  /**
   * Adds a single event to a single chain
   * @param event The event
   * @param chainID The vector position of the chain
   */
  public void assignEvent(String event, int chainID) {
    // Put the event in the correct chain
    chains.elementAt(chainID).add(event);
    // Save the verb
    Pair pair = EventPairScores.split(event);
    verbs.add((String)pair.first());
  }

  /**
   * Constrains the chain at the given index by requiring it to have
   * the given argument as the protagonist.
   */
  public void constrainChain(int chainID, String arg) {
    chains.elementAt(chainID).setArg(arg);
  }

  /**
   * Creates a single chain of events for one protagonist
   * @param events The set of events for the chain
   * @param args The argument constraining the events, or null if none
   * @return The new ID created for the new chain created for the given set.
   */
  public int addStaticChain(Set<String> events, String arg) {
    int id = createChain();
    for( String event : events ) assignEvent(event, id);
    if( arg != null ) constrainChain(id, arg);
    return id;
  }

  /**
   * Adds an already created chain to the narrative
   * @param events The chain of events
   * @param args The argument constraining the chain, or null if none
   */
  public void addStaticChain(Chain chain, String arg) {
    chains.add(chain);
    // Save the verbs
    for( String event : chain.events() ) {
      Pair pair = EventPairScores.split(event);
      verbs.add((String)pair.first());
    }
    // Check if an argument constraint was given
    if( arg != null ) constrainChain(chains.size()-1, arg);
  }
  public void addStaticChain(Chain chain) {
    addStaticChain(chain, null);
  }

  /**
   * @return The number of chains in this narrative
   */
  public int chainSize() { 
    if( chains != null ) return chains.size();
    else return 0;
  }

  public boolean containsVerb(String verb) {
    if( verbs != null && verbs.contains(verb) ) return true;
    else return false;
  }

  /**
   * @return The chain at index id
   */
  public Chain chain(int id) {
    if( chains != null ) return chains.elementAt(id);
    return null;
  }

  /**
   * Removes all events involving this verb from the narrative.
   */
  public void removeVerb(String verb) {
    if( verbs != null ) 
      verbs.remove(verb);
    if( verbScores != null && verbScores.containsKey(verb) ) 
      verbScores.remove(verb);
    for( Chain chain : this.chains() )
      chain.removeVerb(verb);
  }

  /**
   * Return all the events of all chains in one Set
   */
  public Set<String> allEvents() {
    if( chainSize() > 0 ) {
      Set<String> events = new HashSet();
      for( Chain chain : chains ) {
	for( String event : chain.events() ) events.add(event);
      }
      return events;
    }
    else return null;
  }

  public Set<String> verbs() {
    return verbs;
  }

  public Map<String,Float> verbScores() {
    return verbScores;
  }

  /**
   * Cache the score of a verb.
   */
  public void setVerbScore(String verb, float score) {
    if( verbScores == null ) verbScores = new HashMap();
    verbScores.put(verb, score);
  }

  /**
   * @return The cached score for a verb.
   */
  public Float verbScore(String verb) {
    if( verbScores == null ) return null;
    else return verbScores.get(verb);
  }

  /**
   * Make a copy of this Narrative object.  All chains are copied, but
   * the objects within the chains are still referencing the same
   * objects.  Argument caches in the chains are cleared.
   */
  public Narrative clone() {
    Narrative copy = new Narrative();

    for( String verb : this.verbs() )
      copy.addVerb(verb);
    for( Chain chain : this.chains() )
      copy.addChain(chain.clone());
    return copy;
  }

  /**
   * DESTRUCTIVE!
   * This should really only be called by a clustering algorithm, and if
   * the chains have argument IDs.  In other words, each chain is a specific
   * entity that we already know, say from a document.
   * Merging is thus just lining up chains by argument ID, and not searching
   * for a maximum score chain alignment.
   */
  public void merge(Object nar) {
    if( nar instanceof Narrative ) {
      Narrative newnar = (Narrative)nar;

      // For each of the given object's chains...
      for( Chain chain : newnar.chains() ) {
	boolean merged = false;
	// Search for a matching chain.
	if( chains != null ) {
	  for( Chain mychain : chains() ) {
	    if( mychain.argID.equals(chain.argID()) ) {
	      // Merge the chains.
	      mychain.mergeChain(chain);
	      // Update our verbs list.
	      for( String event : chain.events() ) {
		Pair pair = EventPairScores.split(event);
		verbs.add((String)pair.first());
	      }
	      merged = true;
	      break;
	    }
	  }
	}
	// Add the brand new chain.
	if( !merged ) addChain(chain);
      }
    }
    else {
      System.out.println("ERROR: Narrative merge() received non-narrative object");
      System.exit(1);
    }
  }

  private void addVerb(String verb) {
    verbs.add(verb);
  }

  public String toString() {
    String str = "";
    int i = 0;

    if( chains != null ) {
      for( Chain chain : chains ) {
	if( i++ > 0 ) str += "\n";
	str += chain.toString(true);
      }
    }
    return str;
  }

  /**
   * Creates a Narrative object from its string output form.
   * @return A new Narrative object.
   */
  public static Narrative fromString(String str) {
    BufferedReader in = new BufferedReader(new StringReader(str));
    String line;
    Narrative nar = new Narrative();

    try {
      while( (line = in.readLine()) != null ) {
	// Line describes a chain.
	if( line.length() > 2 && line.charAt(0) == '[' ) {
	  int end = line.indexOf(']');
	  if( end == -1 ) {
	    System.out.println("ERROR: bad narrative input: " + line);
	    System.exit(1);
	  }

	  //	  System.out.println("Narrative: new chain from: " + line);

	  // Create the new chain.
	  int chainID = nar.addChain(new Chain());
	  // Split the string into its events.
	  String eventStr = line.substring(1,end);
	  String[] events = eventStr.split("\\s+");
	  //	  System.out.println("Narrative: substring chain: " + eventStr);
	  // Add the events to the new chain.
	  for( String event : events ) {
	    //	    System.out.println("Narrative: event " + event);
	    if( !event.matches("^\\s*$") ) {
	      //	      System.out.println("Narrative: assigning " + event + " to ID=" + chainID);
	      nar.assignEvent(event, chainID);
	    }
	  }
	}
	// There may be other text, particularly if we are reading a
	// verbose online database with scores and verbs listed
	// separately.
	else { }
      }
    } catch( Exception ex ) { ex.printStackTrace(); System.exit(1); }

    System.out.println("Created Narrative: " + nar);
    return nar;
  }

  public Vector<Chain> chains() { return chains; }

  // Size of a schema is the number of events involved.
  public int size() { return verbs.size(); }
}

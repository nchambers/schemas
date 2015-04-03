package nate.narrative;

import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;

import nate.Pair;
import nate.WordEvent;
import nate.IDFMap;
import nate.CountVerbDeps;


/**
 * This class runs Cloze tests over a directory of script files.
 * There are a couple different modes:
 *   base: Chain only Cloze test
 *   nar:  Narrative (multi-chain) Cloze test
 *   -ignoreargs: Narrative test, but don't look at the argument fillers
 * 
 * java Cloze [-ignoreargs] [-mode base|nar] pair-scores arg-scores idfs script-dir
 *
 */
public class Cloze {
  BuildNarrativeWithAllArgs builder;
  IDFMap idf;
  String pairPath, argPath, idfPath;
  CountVerbDeps transitives;
  String transitivesPath = "verbs-lemmas.trans";
  int clozeMode = ORIGINAL_CLOZE;

  // Constants and Global settings
  Vector<Pair> TRANS_ARGS;
  Vector<Pair> INTRANS_ARGS;
  boolean USELEMMAS = true;
  boolean IGNOREARGS = false;
  int MAXSCORE = 9000;
  float MINIDF = 0.9f; // for lemmas (1.7 for tokens)
  public static final int ORIGINAL_CLOZE = 0;
  public static final int NARRATIVE_CLOZE = 1;


  Cloze(String[] args) {
    handleParameters(args);
    // Read idf scores
    idf = new IDFMap();
    idf.fromFile(idfPath);
    // Read the arguments
    builder = new BuildNarrativeWithAllArgs(pairPath, argPath);
    builder.loadData();
    // Transitive arguments
    TRANS_ARGS = new Vector();
    TRANS_ARGS.add(new Pair(WordEvent.DEP_SUBJECT, null));
    TRANS_ARGS.add(new Pair(WordEvent.DEP_OBJECT, null));
    INTRANS_ARGS = new Vector();
    INTRANS_ARGS.add(new Pair(WordEvent.DEP_SUBJECT, null));
    // Load the transitive verbs
    transitives = new CountVerbDeps();
    transitives.transitivesFromFile(transitivesPath);

    if( clozeMode == ORIGINAL_CLOZE ) System.out.println("**MODE = Base Chain");
    if( clozeMode == NARRATIVE_CLOZE ) System.out.println("**MODE = Narrative");
    System.out.println("**MINIDF = " + MINIDF);
    System.out.println("**MAXSCORE = " + MAXSCORE);
    System.out.println("**IGNOREARGS = " + IGNOREARGS);
    System.out.println("**LEMMAS = " + USELEMMAS);
    System.out.println("**Builder's Score Normalizer = " + builder.normalizeVerbAdditions());
  }

  private void handleParameters(String[] args) {
    for( int i = 0; i < args.length; i++ ) {
      if( args[i].equals("-ignoreargs") ) {
	IGNOREARGS = true;
	System.out.println("**Ignoring args**");
      }
      // sets the lambda weight on argument frequencies
      else if( args[i].contains("-argweight") ) {
	try {
	  float weight = Float.valueOf(args[i+1]);
	  BuildNarrativeWithArg.setArgWeight(weight);
	  System.out.println("**setting argweight=" + weight + "**");
	} catch( Exception ex ) { 
	  System.out.println("Bad argweight " + args[i+1]);
	  System.exit(1);
	}
	i++;
      }
      // sets the cutoff for loading arguments into memory, min occurrences
      else if( args[i].contains("-argcountcutoff") ) {
	try {
	  int cutoff = Integer.valueOf(args[i+1]);
	  BuildNarrativeWithAllArgs.setArgCountCutoff(cutoff);
	  System.out.println("**setting argcountcutoff=" + cutoff + "**");
	} catch( Exception ex ) { 
	  System.out.println("Bad argcountcutoff " + args[i+1]);
	  System.exit(1);
	}
	i++;
      }
      // sets the score when new chains are created
      else if( args[i].contains("-minchainscore") ) {
	try {
	  float score = Float.valueOf(args[i+1]);
	  BuildNarrativeWithAllArgs.setMinChainScore(score);
	  System.out.println("**setting minchainscore=" + score + "**");
	} catch( Exception ex ) { 
	  System.out.println("Bad minchainscore " + args[i+1]);
	  System.exit(1);
	}
	i++;
      }
      else if( args[i].equals("-mode") ) {
	if( args[i+1].startsWith("nar") )
	  clozeMode = NARRATIVE_CLOZE;
	else if( args[i+1].startsWith("base") )
	  clozeMode = ORIGINAL_CLOZE;
	else {
	  System.out.println("Unknown cloze mode: " + args[i+1]);
	  System.exit(1);
	}
	i++;
      }
      else if( args[i].startsWith("-") ) {
	System.out.println("Unknown flag " + args[i]);
	System.exit(1);
      }
      else {
	pairPath = args[i];
	argPath = args[i+1];
	idfPath = args[i+2];
	System.out.println(pairPath + " " + argPath + " " + idfPath);
	i += 3;
      }
    }
  }

  /**
   * Run N cloze tests on the given file of N events
   */
  public Pair crossValidateSingleFile(String path) {
    Set<String> tested = new HashSet(); // don't test the same lemma twice
    int sumPositions = 0;
    int numFolds = 0;
    System.out.println("File " + path);

    Set<String> allevents = readEventsFromFile(path, USELEMMAS);
    Vector<String> test = new Vector();

    System.out.println("Got " + allevents.size() + " events");

    // Remove high IDF events
    Set<String> events = removeIDFEvents(allevents, MINIDF);

    System.out.println("After IDF removal: " + events.size() + " events");

    // One cloze test for each event
    for( int skip = 0; skip < events.size(); skip++ ) {
      String skipped = null;
      String bestEvent = null;  String bestArg = null;  float best = 0.0f;

      // Build our test chain
      Chain chain = new Chain();
      int i = 0;
      for( String event : events ) {
	if( i++ != skip ) chain.add(event);
	else skipped = event;
      }

      Pair pair = EventPairScores.split(skipped);
      String skippedVerb = (String)pair.first();

      // Don't include low IDF words in the evaluation
      if( idf.get(skippedVerb) > MINIDF && !tested.contains(skipped) ) {
	tested.add(skipped);

	SortableScore[] scores = null;
	if( clozeMode == ORIGINAL_CLOZE )
	  scores = clozeOriginal(chain);
	else if( clozeMode == NARRATIVE_CLOZE )
	  scores = clozeNarrative(chain, skipped);
	else {
	  System.out.println("Unknown cloze mode: " + clozeMode);
	  System.exit(1);
	}

	// Sort our events by score
	Arrays.sort(scores);
	for( int j = 0; j < 100 && j < scores.length; j++ )
	//	for( int j = 0; j < scores.length; j++ )
	  System.out.println(scores[j]);

	// Score our sorted list
	float position = scoreGuessList(scores, skipped);
	System.out.println(position + " (" + skipped + ")");
	sumPositions += position;
	numFolds++;
      }
    }

    return new Pair(sumPositions, numFolds);
  }


  /**
   * Cloze test that chooses the best event by looking for the best overall
   * verb that adds all of its events to the narrative.
   */
  private SortableScore[] clozeNarrative(Chain chain, String skippedEvent) {
    System.out.println("Narrative Cloze");
    // Get the neighbors in our event graph
    Set<String> verbs = getVerbNeighbors(chain.events());
    Vector<SortableScore> scores = new Vector(verbs.size());
    int xx = 0;
    Vector<Pair> args;

    // Create the narrative with one chain
    Narrative narrative = new Narrative();
    narrative.addStaticChain(chain);

    // Fill narrative with the other arguments of this one chain
    fillNarrative(narrative, skippedEvent);
    Set<String> narrativeEvents = narrative.allEvents();
    System.out.println("Filled Narrative: ");
    System.out.println(narrative);

    // **************************************************
    // Score all chains ahead of time
    float preChainScores[] = new float[narrative.chainSize()];
    for( int i = 0; i < preChainScores.length; i++ ) {
      Chain chainy = narrative.chains().elementAt(i);
      chainy.clearArgCache();
      Pair result = builder.scoreChain(chainy, null, IGNOREARGS);
      //      preChainScores[i] = (Float)result.second() / BuildNarrativeWithAllArgs.CHOOSE[chainy.size()];
      preChainScores[i] = (Float)result.second();
      System.out.println("clozeNarrative: prechain " + i + " score " + preChainScores[i]);
    }

    System.out.println("Post prechain scores: ");
    System.out.println(narrative);

    // **************************************************
    // Try each verb
    for( String verb : verbs ) {
      //      System.out.println("clozeNarrative verb " + verb);

      // Always assume transitive...
      args = TRANS_ARGS;
      //      if( transitives.isTransitive(verb) ) args = TRANS_ARGS;
      //      else args = INTRANS_ARGS;

      // Try each dependency in the main chain
      for( Pair dep : args ) {
	//	System.out.println("clozeNarrative dep " + dep + " for verb " + verb);
	String argEvent = EventPairScores.buildKey(verb, (String)dep.first());

	// Don't test an event that is already part of the narrative
	if( !narrativeEvents.contains(argEvent) ) {
	  // Add this event (verb-dep) to the main chain
	  Pair result = builder.scoreChainWithEventUseCache(argEvent, null, chain, IGNOREARGS);
	  chain.add(argEvent);
	  //	  Pair result = builder.scoreChain(chain, null, IGNOREARGS);
	  String thearg = (String)result.first();
	  //	newChainScores[0] = (Float)result.second() / BuildNarrativeWithAllArgs.CHOOSE[chain.size()];
	  float newProtagScore = (Float)result.second();
	  float changeProtagScore = newProtagScore - preChainScores[0]; 
	
	  // Now that one dependency is set as the main protagonist, 
	  // try other dependencies in other chains.
	  if( args.size() > 1 ) {
	    // Create the new args array of the remaining dependencies to add
	    Vector<Pair> otherArgs = new Vector(args.size()-1);
	    int j = 0;
	    for( Pair other : args ) {
	      if( other != dep ) otherArgs.add(other);
	    }

	    // Now add all the other dependencies to the best other chains
	    result = builder.scoreVerbInNarrative(verb, otherArgs, narrative, IGNOREARGS);
	    Vector<Assignment> assignments = (Vector<Assignment>)result.second();      

	    /*
	    int num = 0;
	    for( Assignment ass : assignments )
	      System.out.println("clozeNarrative ass " + (num++) + ": " + ass);
	    System.out.println("clozeNarrative changeProtag = " + changeProtagScore);
	    System.out.println("clozeNarrative assignChange = " + scoreChange(assignments, preChainScores));
	    */

	    //	    System.out.println("clozeNarrative final score = " + 
	    //			       (changeProtagScore + scoreChange(assignments, preChainScores)));
	    scores.add(new SortableScore(changeProtagScore + scoreChange(assignments, preChainScores), 
					 argEvent, thearg));
	  } 
	  // Just one dependency for this verb, so use the difference in main chain scores
	  else {
	    //	  System.out.println("clozeNarrative final score = " + changeProtagScore);
	    scores.add(new SortableScore(changeProtagScore, argEvent, thearg));
	  }

	  chain.remove(argEvent);
	} // if an ok event
      } // for each dep
    } // for each verb

    SortableScore arr[] = new SortableScore[scores.size()];
    return scores.toArray(arr);
  }

  /**
   * Given a vector of assignments and chain scores before without the assignments,
   * return the total change in scores if the assignments were made.
   */
  private float scoreChange(Vector<Assignment> assignments, float preChainScores[]) {
    float change = 0.0f;

    for( Assignment ass : assignments ) {
      //      System.out.println("scoreChange ass: " + ass);
      //      if( ass.chainID() > -1 ) 
      //	System.out.println("scoreChange pre: " + preChainScores[ass.chainID()]);

      float newscore = ass.score();
      float oldscore = (ass.chainID() >= 0 ? preChainScores[ass.chainID()] : 0.0f);
      change += newscore - oldscore;
    }
    return change;
  }

  /**
   * Cloze test that searches all neighboring events (arrest-o), adding each
   * one to the chain, scoring, and returning the highest scoring event.
   * This is the original chain-based test, not a full narrative test.
   */
  private SortableScore[] clozeOriginal(Chain chain) {
    System.out.println("Chain Only Cloze");

    // Score the base chain ahead of time
    chain.clearArgCache();
    Pair preResult = builder.scoreChain(chain, null, IGNOREARGS);
    System.out.println("clozeOriginal: " + (Float)preResult.second() + " for " + (String)preResult.first());

    // Get the neighbors in our event graph
    Set<String> neighbors = getEventNeighbors(chain.events());

    // Check each neighbor for the highest score
    Vector<SortableScore> scores = new Vector(neighbors.size());
    int i = 0;
    for( String neighbor : neighbors ) {
      //      System.out.print(i + "."); i++;

      // Don't test an event that is already part of the narrative
      if( !chain.events().contains(neighbor) ) {

	// Don't test preposition events for now
	if( !neighbor.contains("-p") ) {

	  Pair result = builder.scoreChainWithEventUseCache(neighbor, null, chain, IGNOREARGS);

	  //	  chain.add(neighbor);

	  // Score our chain      
	  //	  Pair result = builder.scoreChain(chain, null, IGNOREARGS);
	  float score = (Float)result.second();
	  scores.add(new SortableScore((Float)result.second(), neighbor,
				       (String)result.first()));
	  //      System.out.println("Got " + (Float)result.second() + " for " + neighbor +
	  //			 " arg " + (String)result.first());
	  
	  //	  chain.remove(neighbor);
	}
      }
    }

    SortableScore arr[] = new SortableScore[scores.size()];
    return scores.toArray(arr);
  }

  /**
   * Gets all the neighboring verbs, by stripping off the verbs from
   * the neighbor events in the graph.
   */
  private Set<String> getVerbNeighbors(Set<String> events) {
    Set<String> seen = new HashSet();

    /*
    // Record the current verbs
    for( String event : events ) {
      Pair pair = EventPairScores.split(event);
      seen.add((String)pair.first());
    }
    */

    // Get the events, already removed by IDF score on the verb
    Set<String> eventNeighbors = getEventNeighbors(events);
    Set<String> verbs = new HashSet();

    // Loop over all events, stripping off verbs
    for( String event : eventNeighbors ) {
      Pair pair = EventPairScores.split(event);
      String verb = (String)pair.first();
      //      if( !seen.contains(verb) )
	verbs.add(verb);
    }
    return verbs;
  }

  /**
   * Gets all the neighboring events of a given set of events, minus all
   * events with low IDF scores.
   * @return A set of events (throw-s)
   */
  private Set<String> getEventNeighbors(Set<String> events) {
    Set<String> initial = builder.getNeighbors(events);
    Set<String> lowidfs = new HashSet();

    // Check the IDF scores, save the low ones for removal
    for( String event : initial ) {
      Pair pair = EventPairScores.split(event);
      String verb = (String)pair.first();
      float idfScore = idf.get(verb);
      //      System.out.println(event + " " + idfScore);
      if( idfScore < MINIDF && idfScore > 0.0f ) // 0.0f = unseen
	lowidfs.add(event);
    }

    System.out.println("Got " + initial.size() + " neighbor events");

    // Remove the low IDF events
    for( String event : lowidfs ) {
      initial.remove(event);
      System.out.println("Removing " + event);
    }

    /*
    // Remove the events already in our given set
    for( String event : events ) {
      initial.remove(event);
      System.out.println("Removing duplicate " + event);
    }
    */

    System.out.println("Post-idf trim with " + initial.size() + " events");
    return initial;
  }


  /**
   * Completes a narrative that might only have some argument positions
   * of the verbs in the narrative, and so we add the other arguments
   * that are not present.  i.e. arrest-s should have arrest-o too
   * @param skippedEvent The event we're testing for, so don't add it now.
   */
  private void fillNarrative(Narrative narrative, String skippedEvent) {
    Set<String> events = new HashSet();
    Set<String> verbs = new HashSet();
    System.out.println("fillNarrative " + narrative);

    // Pull out all events and verbs
    for( Chain chain : narrative.chains() ) {
      for( String event : chain.events() ) {
	System.out.println("fillNarrative: event " + event);
	Pair pair = EventPairScores.split(event);
	String verb = (String)pair.first();
	events.add(event);
	verbs.add(verb);
      }
    }

    System.out.println("fillNarrative: " + verbs.size() + " verbs");

    // Check every verb
    for( String verb : verbs ) {
      // Check that the subject event of this verb is in the narrative
      String sub = EventPairScores.buildKey(verb, WordEvent.DEP_SUBJECT);
      if( !events.contains(sub) && !skippedEvent.equals(sub) )
	builder.addEventToNarrative(narrative, sub, null, IGNOREARGS);

      // If a transitive verb, check that the object is in the narrative	
      if( transitives.isTransitive(verb) ) {
	String obj = EventPairScores.buildKey(verb, WordEvent.DEP_OBJECT);
	if( !events.contains(obj) && !skippedEvent.equals(obj) )
	  builder.addEventToNarrative(narrative, obj, null, IGNOREARGS);
      }
    }
  }


  /**
   * Find the gold event in the scores list
   */
  private int scoreGuessList(SortableScore scores[], String gold) {
    int found = MAXSCORE;

    int i = 0;
    for( SortableScore score : scores ) {
      if( score.event().equals(gold) ) {
	found = i;
	break;
      }
      i++;
    }
    return found;
  }

  /**
   * Given a vector of events, create a new vector with the same events,
   * but ignoring any with IDF scores that are too low.
   */
  private Set<String> removeIDFEvents(Set<String> allevents, float minIDF) {
    Set<String> events = new HashSet();
    for( String event : allevents ) {

      Pair pair = EventPairScores.split(event);
      String verb = (String)pair.first();

      float idfScore = idf.get(verb);
      if( idfScore > minIDF || idfScore == 0.0f ) {
	events.add(event);
      } else {
	System.out.println("Ignoring IDF " + event + " " + idfScore);
      }
    }
    return events;
  }

  /**
   * Open a script file, read the events, return them in a Vector.
   * @param lemma If true, return the lemma, not the token string.
   */
  public static Set<String> readEventsFromFile(String path, boolean uselemma) {
    Set<String> events = new HashSet();

    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;

      // Read the lines
      while( (line = in.readLine()) != null ) {
	System.out.println("*" + line + "*");
	// Stop when we reach an empty line
	if( line.matches("\\s*") ) break;

	// e.g. 8 finished finish subj
	String parts[] = line.split("\\s+");
	if( parts.length < 4 ) {
	  System.out.println("Bad event line: " + line);
	  System.exit(1);
	}
	String word = parts[1];
	String lemma = parts[2];
	String dep = WordEvent.normalizeRelation(parts[3]);

	if( uselemma )
	  events.add(lemma + EventPairScores.separator + dep);
	else 
	  events.add(word + EventPairScores.separator + dep);
      }

      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    return events;
  }

  /**
   * @return A map from event indices to event tokens.
   */
  public static Map<String,String> readEventIDsFromFile(String path, boolean uselemma) {
    Map<String, String> indices = new HashMap();

    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;

      // Read the lines
      while( (line = in.readLine()) != null ) {
	System.out.println("*" + line + "*");
	// Stop when we reach an empty line
	if( line.matches("\\s*") ) break;

	// e.g. 8 finished finish subj
	String parts[] = line.split("\\s+");
	if( parts.length < 4 ) {
	  System.out.println("Bad event line: " + line);
	  System.exit(1);
	}
	String index = parts[0];
	String token = (uselemma ? parts[2] : parts[1]);
	indices.put(index, token);
      }
      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    return indices;
  }

  /**
   * Open a script file, read the predicate ordering, return pairs of ordered
   * predicates.
   * @param lemma If true, return the lemma, not the token string.
   * @return A set of Pairs, the two predicates that are ordered.  The first
   *         pair element is "temporally before" the second.
   */
  public static Set<Pair> readPredicateOrderFromFile(String path, boolean uselemma) {
    Map<String, String> indexMap = new HashMap();
    Set<Pair> pairs = new HashSet();

    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;

      // Read the lines
      while( (line = in.readLine()) != null ) {
	//	System.out.println("*" + line + "*");
	// Stop when we reach a breakpoint.
	if( line.startsWith("---") ) break;

	// e.g. 8 finished finish subj
	else if( line.matches("^\\d+ .+") ) {
	  String parts[] = line.split("\\s+");
	  if( parts.length < 4 ) {
	    System.out.println("Bad event line: " + line);
	    System.exit(1);
	  }
	  String index = parts[0];
	  String token = (uselemma ? parts[2] : parts[1]);
	  
	  indexMap.put(index, token);
	}

	// e.g. B 11 9
	else if( line.matches("^B \\d+ \\d+$") ) {
	  String parts[] = line.split("\\s+");
	  String verb1 = indexMap.get(parts[1]);
	  String verb2 = indexMap.get(parts[2]);
	  if( verb1 == null || verb2 == null ) {
	    System.out.println("ERROR: unknown index in line: " + line);
	    System.exit(1);
	  }
	  pairs.add(new Pair(verb1, verb2));
	}
	
	else { 
	  //	  System.out.println("Skipping line: " + line);
	}
      }
      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    return pairs;
  }

  /**
   * Read a directory of script files, process each one in order.
   */
  public void processDir(String dirPath) {
    System.out.println("Processing " + dirPath);
    int sumPositions = 0;
    int numFolds = 0;
    int numFiles = 0;

    File dir = new File(dirPath);
    if( dir.isDirectory() ) {
      String files[] = dir.list();
      
      for( String f : files ) {
	if( f.contains("NYT") ) {
	  //	if( f.contains("20010904.0446") ) {
	  Pair result = crossValidateSingleFile(dirPath + File.separator + f);
	  sumPositions += (Integer)result.first();
	  numFolds += (Integer)result.second();
	  numFiles++;
	}
	//	if( numFiles > 3 ) break;
      }
    }

    System.out.println("argWeight = " + BuildNarrativeWithArg.argWeight());
    System.out.println("Sum over positions is " + sumPositions + " of " +
		       numFolds + " tests from " + numFiles + " files.");
    System.out.println("Score is " + ((float)sumPositions / (float)numFolds));
  }

  /**
   * Runs the same Cloze test with different argument weights
   */
  public void argWeightBattery(String dirPath) {
    System.out.println("*** ARGWEIGHTBATTERY STARTING ***");

    // Toggle chain score normalizer on/off
    clozeMode = NARRATIVE_CLOZE;
    for( int i = 0; i < 2; i++ ) {
      if( i == 0 )
	BuildNarrativeWithAllArgs.setNormalizeVerbAdditions(false);
      else
	BuildNarrativeWithAllArgs.setNormalizeVerbAdditions(true);

      // Cycle chain splitting threshold
      for( float m=0.1f; m < 0.6f; m += 0.1f ) {
	BuildNarrativeWithAllArgs.setMinChainScore(m);
	
	// Cycle argument frequency count weight
	for( float w=0.01f; w < 0.25f; w += 0.02f ) {
	  BuildNarrativeWithArg.setArgWeight(w);

	  System.out.println("**** NEW BATTERY (NAR) ****"
			     + "normalizer=" + BuildNarrativeWithAllArgs.normalizeVerbAdditions()
			     + " minchain=" + BuildNarrativeWithAllArgs.minChainScore()
			     + " argweight=" + BuildNarrativeWithArg.argWeight());
	  // Do the dirty work!
	  processDir(dirPath);
	}
      }
    }

    // Cycle argument frequency count weight
    clozeMode = ORIGINAL_CLOZE;
    for( float w=0.01f; w < 0.25f; w += 0.02f ) {
      BuildNarrativeWithArg.setArgWeight(w);
      
      System.out.println("**** NEW BATTERY (BASE) ****"
			 + " argweight=" + BuildNarrativeWithArg.argWeight());
      // Do the dirty work!
      processDir(dirPath);
    }
    
  }

  /**
   * Convenience class that can be sorted by score of events
   */
  private class SortableScore implements Comparable {
    float score = 0.0f;
    String event = null;
    String arg = null;

    SortableScore(float s, String e, String a) {
      score = s;
      event = e;
      arg = a;
    }

    public int compareTo(Object b) {
      if( b == null ) return -1;
      if( score < ((SortableScore)b).score() ) return 1;
      else if( ((SortableScore)b).score() > score ) return -1;
      else return 0;
    }

    public float score() { return score; }
    public String event() { return event; }
    public String arg() { return arg; }
    public String toString() {
      return event + "(" + arg + ") " + score;
    }
  }
  
  /**
   * Main
   */
  public static void main(String[] args) {
    if( args.length < 4 ) {
      System.out.println("need more args");
      System.exit(1);
    } else {
      Cloze cloze = new Cloze(args);//args[0], args[1], args[2]);
 
      cloze.processDir(args[args.length-1]);
      //    cloze.argWeightBattery(args[args.length-1]);
   }
  }
}
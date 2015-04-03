package nate.narrative;

import java.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.BufferedReader;

import nate.*;
import nate.util.HandleParameters;
import nate.util.SortableScore;


/**
 * This class takes pairwise scores (pmi or t-test) between events and
 * is able to build discrete sets of narratives centered around single
 * events.  The sets can be large, or trimmed to a certain size, keeping
 * only the most inter-related events.
 *
 * -ignoreargs
 * If flag is present, don't consider args in the schema scoring.
 *
 * -argweight <float>
 * Sets the linear interpolation weight for the argument score that is 
 * used during chain scoring.  This is in BuildNarrativeWithArg.java.
 *
 * -argcountcutoff <int>
 * The number of times an argument must be seen in a pair of verb slots
 * for it to be loaded into memory (and thus used in scoring or not).
 *
 * -minchainscore <float>
 * The cutoff score at which we add an event to a new chain, not to
 * an existing chain with such a low score.
 * 
 * -normalize true|false
 * Normalize the chain scores by chain length, or not.
 *
 * -mindocs <int>
 * The minimum times a verb must appear to start a new schema.
 *
 * -maxdocs <int>
 * The maximum times a verb can appear to start a new schema.
 *
 * -size <int>
 * The size of the schemas to create.
 *
 */
public class FindBestNarratives {
  BuildNarrativeWithAllArgs builder;
  IDFMap idf;
  String pairPath, argPath, idfPath;
  Vector<Pair> TRANS_ARGS;
  Vector<Pair> INTRANS_ARGS;

  // Parameters to narrative building
  boolean USELEMMAS = true;
  boolean IGNOREARGS = false;
  float MINIDF = 0.9f; // for lemmas (1.7 for tokens)

  int _narrativeSize = 6;
  int _minDocCount = 3000;
  int _maxDocCount = 50000;

  public FindBestNarratives(String args[]) {
    handleParameters(args);
    loadData();
  }

  private void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-ignoreargs") ) {
	IGNOREARGS = true;
	System.out.println("**Ignoring args**");
    }
    // sets the lambda weight on argument frequencies
    if( params.hasFlag("-argweight") ) {
      String param = params.get("-argweight");
      try {
	float weight = Float.valueOf(param);
	BuildNarrativeWithArg.setArgWeight(weight);
	System.out.println("**setting argweight=" + weight + "**");
      } catch( Exception ex ) { 
	System.out.println("Bad argweight " + param);
	System.exit(1);
      }
    }
    // sets the cutoff for loading arguments into memory, min occurrences
    if( params.hasFlag("-argcountcutoff") ) {
      String param = params.get("-argcountcutoff");
	try {
	  int cutoff = Integer.valueOf(param);
	  BuildNarrativeWithAllArgs.setArgCountCutoff(cutoff);
	  System.out.println("**setting argcountcutoff=" + cutoff + "**");
	} catch( Exception ex ) { 
	  System.out.println("Bad argcountcutoff " + param);
	  System.exit(1);
	}
    }
    // sets the score when new chains are created
    if( params.hasFlag("-minchainscore") ) {
      String param = params.get("-minchainscore");
	try {
	  float score = Float.valueOf(param);
	  BuildNarrativeWithAllArgs.setMinChainScore(score);
	  System.out.println("**setting minchainscore=" + score + "**");
	} catch( Exception ex ) { 
	  System.out.println("Bad minchainscore " + param);
	  System.exit(1);
	}
    }
    // sets the score when new chains are created
    if( params.hasFlag("-normalize") ) {
      String param = params.get("-normalize");
      if( param.equals("true") )
	BuildNarrativeWithAllArgs.setNormalizeVerbAdditions(true);
      else if( param.equals("false") )
	BuildNarrativeWithAllArgs.setNormalizeVerbAdditions(false);
      else {
	System.out.println("Unknown normalizing boolean " + param);
	System.exit(1);
      }
      System.out.println("**normalizing " + param);
    }
    // The number of documents a verb must appear in to be used as
    // the base verb in generating a new narrative schema.
    if( params.hasFlag("-mindocs") ) {
      _minDocCount = Integer.parseInt(params.get("-mindocs"));
    }
    if( params.hasFlag("-maxdocs") ) {
      _maxDocCount = Integer.parseInt(params.get("-maxdocs"));
    }
    // Adjust the max size of schemas.
    if( params.hasFlag("-size") ) {
      _narrativeSize = Integer.parseInt(params.get("-size"));
    }

    // Set the remaining arguments.
    pairPath = args[args.length-3];
    argPath  = args[args.length-2];
    idfPath  = args[args.length-1];
    System.out.println(pairPath + " " + argPath + " " + idfPath);
  }


  private void loadData() {
    // Read idf scores
    idf = new IDFMap();
    idf.fromFile(idfPath);
    // Read the arguments
    builder = new BuildNarrativeWithAllArgs(pairPath, argPath);
    builder.loadData();

    System.out.println("**MINIDF = " + MINIDF);
    System.out.println("**IGNOREARGS = " + IGNOREARGS);
    System.out.println("**LEMMAS = " + USELEMMAS);
    System.out.println("**_narrativeSize = " + _narrativeSize);
    System.out.println("**_minDocCount = " + _minDocCount);
    System.out.println("**_maxDocCount = " + _maxDocCount);
    System.out.println("**Builder's Score Normalizer = " + builder.normalizeVerbAdditions());

    // Transitive arguments
    TRANS_ARGS = new Vector();
    TRANS_ARGS.add(new Pair(WordEvent.DEP_SUBJECT, null));
    TRANS_ARGS.add(new Pair(WordEvent.DEP_OBJECT, null));
    INTRANS_ARGS = new Vector();
    INTRANS_ARGS.add(new Pair(WordEvent.DEP_SUBJECT, null));
  }


  /**
   * We cycle through all known verbs, building narratives for the ones that
   * have occurred in enough documents.  The top scoring narratives that are
   * built are selected and output.
   * @param topN The number of top scoring narratives to print.
   */
  public void findNarratives(int topN) {
    Vector<String> goodVerbs = new Vector();

    // Get all observed verbs.
    Set<String> allVerbs = idf.getWords();

    // Figure out how many verbs are ok.
    for( String verb : allVerbs ) {
      int docCount = idf.getDocCount(verb);
      // Check verbs occurring a normal amount of time
      if( docCount > _minDocCount && docCount < _maxDocCount ) {
	goodVerbs.add(verb);
	//	System.out.println("verb " + verb);
      }
    }
    System.out.println("Found " + goodVerbs.size() + " verbs to lookup.");

    // Make the sortable array of scores.
    SortableNarrativeScore[] scored = new SortableNarrativeScore[goodVerbs.size()];
    int i = 0;

    System.out.println("Total good verbs: " + goodVerbs.size());
    //    System.exit(1);
    
    // Build narratives for all of these verbs!
    for( String verb : goodVerbs ) {
      int docCount = idf.getDocCount(verb);
      System.out.println("Checking " + verb + " docCount = " + docCount);

      // Build a new narrative of size one.
      Narrative narrative = new Narrative();
      Vector<Pair> verbs = new Vector(1);
      verbs.add(new Pair(verb, TRANS_ARGS));
      builder.createNarrative(narrative, verbs);
      
      // Grow the narrative to a certain size.
      Set<String> events = narrative.allEvents();
      builder.growCluster(narrative, events, _narrativeSize-1);
      
      float score = builder.scoreNarrative(narrative, IGNOREARGS);
      scored[i++] = new SortableNarrativeScore(narrative, verb, score);
      // Score each verb's contribution. Stores scores in Narrative object.
      builder.scoreNarrativeVerbs(narrative, IGNOREARGS);
      //      System.exit(1);
    }

    // Sort and print the narratives!
    System.out.println("\n\n*******SORTED NARRATIVES******");
    Arrays.sort(scored);

    Vector<Narrative> used = new Vector();
    for( i = 0; i < scored.length; i++ ) {
      Narrative nar = scored[i].narrative();
      boolean skip = false;

      // Don't print narratives with significant overlap of events with
      // a narrative that we've already printed.
      for( Narrative earlier : used ) {
	int overlap = overlapSize(earlier, nar);
	if( overlap >= (_narrativeSize/2 + (_narrativeSize % 2)) ) {
	  skip = true;
	  //	  System.out.println("SKIPPED overlap = " + overlapSize(earlier, nar));
	}
      }

      // If this is a new narrative, print it.
      if( !skip && scored[i].score() > 0.0f ) {
	used.add(scored[i].narrative());
	System.out.println("*****");
	System.out.println("score=" + scored[i].score() + " base=" + 
			   scored[i].verb());

	// Sort the events by score.
	Map<String,Float> verbScores = nar.verbScores();
	int j = 0;
	SortableScore sortables[] = new SortableScore[verbScores.size()];
	for( Map.Entry<String,Float> entry : verbScores.entrySet() )
	  sortables[j++] = new SortableScore(entry.getValue(), entry.getKey());
	Arrays.sort(sortables);
	System.out.print("Events:");
	for( SortableScore sortable : sortables )
	  System.out.print(" " + sortable.key());
	System.out.println();
	System.out.print("Scores:");
	for( SortableScore sortable : sortables )
	  System.out.printf(" %.3f", sortable.score());

	System.out.println();
	System.out.println(nar);
	System.out.println();
      }
      /*
      else {
	System.out.println("  -> cutoff = " + (_narrativeSize/2 + (_narrativeSize % 2)));
 	System.out.println("score=" + scored[i].score() + " base=" + 
 			   scored[i].verb() + "** ");
 	System.out.println(nar);
      }
      */
    }
  }

  /**
   * Crude way of counting how many verbs are shared between the two.
   * @return The number of verbs that are in both narratives.
   */
  private int overlapSize(Narrative nar, Narrative nar2) {
    int overlap = 0;
    for( String v : nar.verbs() ) {
      for( String v2 : nar2.verbs() ) {
	if( v.equals(v2) ) overlap++;
      }
    }
    return overlap;
  }


  private class SortableNarrativeScore implements Comparable {
    float score;
    Narrative narrative;
    String verb; // the seed verb that started the narrative

    SortableNarrativeScore(Narrative nar, String v, float s) {
      score = s;
      verb = v;
      narrative = nar;
    }

    public int compareTo(Object other) {
      if( ((SortableNarrativeScore)other).score() > score ) return 1;
      else if( score > ((SortableNarrativeScore)other).score() ) return -1;
      else return 0;
    }

    public float score() { return score; }
    public Narrative narrative() { return narrative; }
    public String verb() { return verb; }
  }


  public static void main(String[] args) {
    FindBestNarratives finder = new FindBestNarratives(args);
    finder.findNarratives(10);
  }
}
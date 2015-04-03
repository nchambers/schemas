package nate.narrative;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import nate.BasicEventAnalyzer;
import nate.Pair;
import nate.args.VerbPairArgCounts;
import nate.cluster.Cluster;
import nate.util.Util;

/**
 * This class builds sets of events for a full narrative with all arguments
 * specified, linked, and filled in.
 * Input: (1) pairwise scores (pmi or t-test) between events
 *        (2) counts of event arguments
 *
 * Provides a command line interface that lets you enter verbs
 * and builds the narrative based on that initial set.
 *
 */
public class BuildNarrativeWithAllArgs implements nate.cluster.ClusteringScorer {
  EventPairScores pairs; // the pmi or t-test scores between pairs
  VerbPairArgCounts argCounts; // occurrence counts of args with verb pairs
  HashMap<String,HashMap<String,Float>> scores = new HashMap();
  //  static float factor = 0.25f; // threshold factor
  static float factor = 0.01f; // threshold factor
  static float scoreCutoff = 0.25f; // group member cutoff
  static float emptyArgPenalty = 1.0f; // if no arguments seen, penalize the pairwise score
  static float argWeight = 0.1f;
  // The size of the group where we will stop considering new arguments.
  // For instance, if we add a 6th event, and that event has arguments that we have not
  // yet seen in the previous 5, we will not consider those arguments.
  static int groupSizeConsiderNewArgs = 4;
  String pairsPath = "";
  String argCountsPath = "";
  boolean _ignoreArgs = false;
  // Normalizes the scores of chains ... favors longer chains if false.
  static boolean _normalizeVerbAdditions = false;

  String DEPS[] = { "s", "o" };
  Vector<Pair> BASEARGS;
  Vector<int[][]> COMBOS;
  // The maximum number of events to auto-add to a narrative
  int MAXGROWTH = 5;
  // The minimum chain score below which we start a new chain
  //  float MINCHAINSCORE = 0.35f;
  static float MINCHAINSCORE = 0.2f;
  // The number of times an argument must be seen with a pair of events
  // for it to be loaded into memory.
  static int _argCountCutoff = 2;
  // The number of loops during the permutation phase
  int PERMUTELOOPS = 500;
  // (i choose 2) 
  static final int CHOOSE[] = { 0, 1, 1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 66, 78, 91, 
    105, 120, 136, 153, 171, 190, 210, 231, 253, 276, 300, 325,
    351, 378, 406, 435, 465, 496, 528, 561, 595, 630, 666 };

  /**
   * Constructor
   */
  public BuildNarrativeWithAllArgs(EventPairScores pairScores, VerbPairArgCounts argCounts) {
    this.pairs = pairScores;
    this.argCounts = argCounts;
    buildCombos();
  }
  public BuildNarrativeWithAllArgs(String pairsPath, String argsPath) {
    this.pairsPath = pairsPath;
    this.argCountsPath = argsPath;
    buildCombos();
  }

  /**
   * Load pairs and argument counts from disk
   */
  public void loadData() {
    if( pairs != null ) pairs.clear();
    pairs = new EventPairScores();
    pairs.fromFile(pairsPath, null, 0.0f, 0, false);
    Util.reportMemory();

    if( argCounts != null ) argCounts.clear();
    argCounts = new VerbPairArgCounts(argCountsPath, _argCountCutoff);
    Util.reportMemory();

    BASEARGS = new Vector();
    for( String dep : DEPS )  BASEARGS.add(new Pair(dep, null));
  }

  /**
   * Build the orderings ahead of time for ease of use and programming.
   */
  private void buildCombos() {
    int[][] x;
    COMBOS = new Vector();

    // Empty zero cell
    COMBOS.add(new int[1][1]);
    // If only one event, choose the top scoring one
    x = new int[2][1];
    x[0] = new int[]{ 0 };
    x[1] = new int[]{ 1 }; // just in case the top is a violation
    COMBOS.add(x);
    // Two events, try top scoring pair, permute if constraints are violated
    x = new int[4][2];
    x[0] = new int[]{ 0, 0 };
    x[1] = new int[]{ 0, 1 };
    x[2] = new int[]{ 1, 0 };
    x[3] = new int[]{ 1, 1 };
    COMBOS.add(x);
    // If three events
    x = new int[15][3];
    x[0] =  new int[]{ 0, 0, 0 };
    x[1] =  new int[]{ 0, 0, 1 };
    x[2] =  new int[]{ 0, 1, 0 };
    x[3] =  new int[]{ 1, 0, 0 };
    x[4] =  new int[]{ 0, 1, 1 };
    x[5] =  new int[]{ 1, 1, 0 };
    x[6] =  new int[]{ 1, 0, 1 };
    x[7] =  new int[]{ 1, 1, 1 };
    x[8] =  new int[]{ 1, 1, 2 };
    x[9] =  new int[]{ 1, 2, 1 };
    x[10] = new int[]{ 2, 1, 1 };
    x[11] = new int[]{ 1, 2, 2 };
    x[12] = new int[]{ 2, 2, 1 };
    x[13] = new int[]{ 2, 1, 2 };
    x[14] = new int[]{ 2, 2, 2 };
    COMBOS.add(x);
  }

  /**
   * Endless loop that reads from STDIN to build narratives around different
   * events without reloading data from disk.
   */
  public void onlineInput() {
    try {
      BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      String input;
      Narrative narrative = new Narrative();
      System.out.print("> ");

      while( (input = in.readLine()) != null ) {
        // Read one line from input
        String parts[] = input.split(" ");

        // Check the format
        if( parts.length == 0  ) {
          System.out.println("Wrong format: verb verb2 ... ");
          System.out.println("- Set max size: max <int>");
          System.out.print("> ");
        }

        else if( parts[0].equals("reset") || parts[0].equals("new") ) {
          narrative = new Narrative();
        }

        // Set the splitting into a new chain minimum score
        else if( parts[0].equals("split") ) {
          try {
            MINCHAINSCORE = Float.valueOf(parts[1]);
          } catch( Exception ex ) { System.out.println("split <float>"); }
          System.out.println("MINCHAINSCORE = " + MINCHAINSCORE);
        }

        else if( parts[0].equals("normalize") ) {
          if( _normalizeVerbAdditions )
            _normalizeVerbAdditions = false;
          else
            _normalizeVerbAdditions = true;
          System.out.println("normalize " + _normalizeVerbAdditions);
        }

        else if( parts[0].equals("ignoreargs") ) {
          if( _ignoreArgs ) {
            _ignoreArgs = false;
            // Should lower the score since args aren't adding to them...
            MINCHAINSCORE *= 2;
          }
          else {
            _ignoreArgs = true;
            // Should raise the score with args
            MINCHAINSCORE /= 2;
          }
          System.out.println("ignoreArgs " + _ignoreArgs);
        }

        else if( parts[0].equals("") ) {
          if( narrative != null ) System.out.println(narrative);
          else System.out.println();
        }

        // Add one chain: "chain <argument> event1 event2 ... eventn"
        //   e.g. eventi is "verb-s" or "verb-o"
        else if( parts[0].equals("chain") ) {
          Set<String> events = new HashSet(parts.length);
          String arg = null;
          int start = 1;
          // Save the argument (if it is the first token)
          if( parts[1].indexOf('-') == -1 ) {
            arg = parts[1];
            start = 2;
          }
          // Add the events
          for( int i = start; i < parts.length; i++ ) events.add(parts[i]);
          int chainID = narrative.addStaticChain(events, arg);
          // Score the chain to set the argument score cache.
          scoreChain(narrative.chain(chainID), null, _ignoreArgs);
        }

        // Grow the narrative by a certain number of verbs
        else if( parts[0].equals("grow") && parts.length == 2 ) {
          Set<String> events = narrative.allEvents();
          try {
            Integer iterations = Integer.valueOf(parts[1]);
            growCluster(narrative, events, iterations);
          } catch( Exception ex ) { ex.printStackTrace(); }
        }

        // Append to our narrative a set of verbs with optional args
        else {
          // Pull off the verbs now  e.g. "arrest convict"
          Set<String> verbs = new HashSet();
          for( int i = 0; i < parts.length; i++ )
            verbs.add(parts[i]);
          createNarrative(narrative, parseLine(parts));
          float score = scoreNarrative(narrative, _ignoreArgs);

          // Output the result
          System.out.println("Narrative Score = " + score);
          System.out.println(narrative);
          //	  for( NarrativeEvent e : n ) System.out.println(e);
          System.out.println("FIN");      
        }

        System.out.print("> ");
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * Parses the input line of a narrative with potential argument constraints:
   * - arrest convict plead
   * - throw o-ball s-player catch o-ball lose hit
   * @return Pairs of (1) verb and (2) vector of pairs: arg positions and fillers
   */
  private Vector<Pair> parseLine(String[] parts) {
    Vector<Pair> verbPairs = new Vector();
    Vector<Pair> argPairs = new Vector();
    Set seen = new HashSet();
    String verb = null;
    int i = 0;

    while( i < parts.length ) {
      System.out.println("i=" + i + " " + parts[i]);
      int split = parts[i].indexOf('-');

      // If the token is an argument (e.g. o-suspect)
      if( split > -1 ) {
        if( verb == null ) {
          System.out.println("Bad input: expecting a verb, got an event");
          return null;
        }

        String dep = parts[i].substring(0,split);
        Pair argpair = new Pair(dep, parts[i].substring(split+1));
        argPairs.add(argpair);
        seen.add(dep);
      }

      // The token is a verb
      else {

        for( String dep : DEPS ) {
          // add the default argument (no constraint)
          if( !seen.contains(dep) )  argPairs.add(new Pair(dep, null));
        }

        // Add the previous verb
        if( verb != null ) {
          verbPairs.add(new Pair(verb, argPairs));
        }

        // Now save the new verb
        verb = parts[i];
        argPairs = new Vector();
        seen.clear();
      }

      i++;
    }


    // Final cleanup
    for( String dep : DEPS ) {
      // add the default argument (no constraint)
      if( !seen.contains(dep) )  argPairs.add(new Pair(dep, null));
    }
    verbPairs.add(new Pair(verb, argPairs));

    // DEBUG
    for( Pair pair : verbPairs ) {
      System.out.print("v=" + (String)pair.first() + " args=");
      Vector<Pair> args = (Vector<Pair>)pair.second();
      for( Pair p : args ) {
        System.out.print(p + " ");
      }
      System.out.println();
    }

    // Return the verbs with their argument choices
    return verbPairs;
  }


  /**
   * @desc Creates a discrete clustered narrative centered around
   *       the given verbs.
   * @param verbs The set of core verbs to build the narrative around.
   *              A vector of pairs, each pair: 
   *                 (1) dep and (2) argument (can be null)
   * @return A hashmap of narrative events and their individual scores
   *         Does not include the main event.
   */
  //  public Narrative createNarrative(Set<String> verbs) {
  public void createNarrative(Narrative narrative, Vector<Pair> verbs) {
    Set<String> events = new HashSet();

    // Add each verb
    for( Pair verbInfo : verbs ) {
      String verb = (String)verbInfo.first();
      Vector<Pair> deps = (Vector<Pair>)verbInfo.second();
      for( Pair depInfo : deps )
        events.add(EventPairScores.buildKey(verb, (String)depInfo.first()));
      addVerb(verb, deps, narrative);
    }

    System.out.println("Initial add");
    System.out.println(narrative);

    /*
    System.out.println("Getting neighbors of " + events.size() + " events");
    // Make a list of neighbors
    Set<String> neighbors = getNeighbors(events);
    // Pull out just the verbs
    //    verbs = eventsToVerbs(neighbors);
    // Return NOW if there are no neighbors
    if( neighbors == null ) return;
     */

    // Grow the cluster!!
    //    growCluster(narrative, events, MAXGROWTH);

    //    System.out.println("***** FINISHED CLUSTER *****");

    // Permute the cluster
    //    permute(narrative);
  }

  /**
   * Adds a single verb to the narrative by adding each of its dependency
   * types as an event.  Each dep is assigned to a current chain in the
   * full narrative structure, or creates a new chain of which it is the
   * only member.
   * @param verb The verb to add
   * @param args The set of dependencies for the verb. A vector of pairs, 
   *             each pair: (1) dependency and (2) argument (can be null)
   */
  private void addVerb(String verb, Vector<Pair> args, Narrative narrative) {

    // Find the best chain assignment for all dependencies.
    // No two deps can be in the same chain.
    Pair result = scoreVerbInNarrative(verb, args, narrative, _ignoreArgs);
    Vector<Assignment> assignments = (Vector<Assignment>)result.second();

    // Add the verb's events to the chains
    assignEventsToChains(assignments, narrative);
    System.out.println("**NARRATIVE**");
    System.out.println(narrative);
  }

  /**
   * Scores a single verb with a narrative by adding each of its dependency
   * types as an event.  Each dep is "assigned" to a current chain in the
   * full narrative structure.  The function returns a set of chain assignments
   * and a total score if those assignments are implemented in the narrative.
   * @param verb The verb to add
   * @param args A vector of dependencies to go with the verb.  Each pair in the
   *             vector is a dependency and an argument (null if no arg constraint)
   * @param narrative The full narrative structure we are adding the verb to
   * @param fillChainID If >= 0, specifies a constraint that one of the verb's
   *                    events must be put in that chain ID.  Ignored otherwise.
   * @return A Pair object with (1) the score and (2) the Vector of Assignment
   *         objects that contain event->chainID assignments.
   *         Score is NOT NORMALIZED for length of chains.
   */
  public Pair scoreVerbInNarrative(String verb, Vector<Pair> args, Narrative narrative,
      int fillChainID, boolean ignoreArgs) {
    int depIndex = 0;

    // First check that some chains exist, if not, create them
    int needed = args.size() - narrative.chainSize();
    for( int i = 0; i < needed; i++ )
      narrative.createChain();

    // Create scores arrays
    String events[] = new String[args.size()];
    ChainScore chainScores[][] = new ChainScore[args.size()][narrative.chainSize()+1];

    // Score each dependency type with each chain
    for( Pair pair : args ) {
      String dep = (String)pair.first();
      String arg = (String)pair.second();
      String event = EventPairScores.buildKey(verb, dep);
      int chainIndex = 0;

      //      System.out.println("scoreVerbInNarrative " + event + " with " + 
      //			 narrative.chainSize() + " chains");

      // Score the event in each chain
      Vector<Chain> chains = narrative.chains();
      for( Chain chain : chains ) {
        String bestArg = null;
        float score = 0.0f;

        if( !chain.containsVerb(verb) ) {
          // Get the score and the best argument
          //	  Pair result = scoreChainWithEvent(event, arg, chain, ignoreArgs);
          Pair result = scoreChainWithEventUseCache(event, arg, chain, ignoreArgs);
          bestArg = (String)result.first();

          if( _normalizeVerbAdditions )
            score = (Float)result.second() / CHOOSE[chain.size()+1];
          else
            score = (Float)result.second();
        }

        events[depIndex] = event;
        //	args[depIndex][chainIndex] = bestArg;
        chainScores[depIndex][chainIndex] = 
          new ChainScore(score, bestArg, chainIndex, (arg != null ? true : false));

        //	System.out.println("**scoreVerbInNarrative " + event + " chainIndex=" + chainIndex + 
        //	   " score=" + chainScores[depIndex][chainIndex]);

        chainIndex++;
      }
      // Add a dummy score for creation of a brand new chain
      chainScores[depIndex][chainIndex] = 
        new ChainScore(MINCHAINSCORE, null, -1, (arg != null ? true : false));

      depIndex++;
    }

    // Sort the scores arrays
    for( int i = 0; i < chainScores.length; i++ ) {
      Arrays.sort(chainScores[i]);
    }

    // Find the best chain assignment for all dependencies.
    // No two deps can be in the same chain.
    return chooseBestChainsForEvents(chainScores, events, narrative.chains(), fillChainID);
  }

  public Pair scoreVerbInNarrative(String verb, Vector<Pair> args, 
      Narrative narrative, boolean ignoreArgs) {
    return scoreVerbInNarrative(verb, args, narrative, -1, ignoreArgs);
  }

  /**
   * Returns null if the chain already contains a form of the event
   * Otherwise returns a normal score, a Pair of the best argument and score
   */
  private Pair scoreChainWithEventCheckDuplicates(String event, Chain chain, boolean ignoreArgs) {
    Pair pair = EventPairScores.split(event);
    String eventString = (String)pair.first() + EventPairScores.separator;

    // Check for the event's verb already in this chain
    for( String ev : chain.events() ) {
      if( ev.startsWith(eventString) ) {
        System.out.println("skipping chain " + chain);
        return new Pair(null, 0.0f);
      }
    }

    return scoreChainWithEvent(event, null, chain, ignoreArgs);
  }

  /**
   * Score a chain including a brand new event, finding the best argument.
   * O(R*G^2) : R number of arguments, G chain size
   * @param event The event e.g. arrest-o
   * @param arg The argument type constraining the event.  If null, no
   *            constraints are applied and all possible arguments are looked
   *            at for the highest score.
   * @return A pair of (1) the best argument and (2) its score.
   *         The score is NOT NORMALIZED BY LENGTH of the chain.  Make sure to 
   *         do so if you need it.
   */
  private Pair scoreChainWithEvent(String event, String arg, Chain chain, boolean ignoreArgs) {
    String bestArg = null;
    Float best = 0.0f;

    //        System.out.println("scoreChainWithEvent with " + event);
    //        System.out.println("  chain = " + chain);

    // Set the constraint if the chain requires it.
    if( arg == null && chain.arg() != null ) arg = chain.arg();

    // The chain has at least one event in it already
    // The chain's argument matches are given argument
    if( chain.size() > 0 ) {
      // Temporarily add
      chain.add(event);

      Pair result = scoreChain(chain, arg, ignoreArgs);
      bestArg = (String)result.first();
      best = (Float)result.second();

      // Remove again
      chain.remove(event);
    }

    // The chain is empty, just fill with the minimum score
    else if( chain.size() == 0 ) {
      best = MINCHAINSCORE;
      if( arg != null ) bestArg = arg;
    }

    // Mismatching arguments, no score
    else {
      best = 0.0f;
      bestArg = chain.arg();
    }

    //    System.out.println("scoreChainWithEvent with " + event + " chain " + chain);
    //    System.out.println("scoreChainWithEvent best= " + best);

    return new Pair(bestArg, best);
  }

  /**
   * Find the best argument for this event with this chain.
   * Assume the chain has cached argument scores. We just incrementally
   * add this event's score to the already calculated chain scores.
   * O(R*G) : R number of arguments, G chain size
   * @param event The event we want to add to the chain for a score.
   * @param arg If not null, we just find the score for the given argument.
   * @param chain The chain of events.
   * @return A Pair of (1) the best argument and (2) the score with the argument
   */
  public Pair scoreChainWithEventUseCache(String event, String arg, Chain chain, boolean ignoreArgs) {
    String bestArg = null;
    float best = 0.0f;

    Pair ePair = EventPairScores.split(event);
    String eVerb = (String)ePair.first();
    String eDep  = (String)ePair.second();

    //    System.out.println("scoreWithCache chain " + chain + " new event " + event);

    // Ignoring arguments ( this is actually G^2 )
    if( ignoreArgs ) {
      chain.add(event);
      best = BuildNarrativeWithArg.groupScoreWithArg(pairs, argCounts, chain.events(), 
          arg, ignoreArgs);
      chain.remove(event);
      bestArg = arg;
    }

    // We just want one argument, already known.
    else if( arg != null ) {
      Float chainScore = chain.getCachedArgScore(arg);
      float addedScore = scoreEventWithChain(chain, event, eVerb, eDep, arg, false);
      bestArg = arg;
      best = chainScore + addedScore;
    }

    // This is an empty chain, add the event by itself.
    else if( chain.size() == 0 ) {
      best = MINCHAINSCORE;
      bestArg = null;
    }

    // Check all possible arguments
    else {
      // Collect the possible argument set
      //      Set<String> args = new HashSet();
      //      chain.add(event);
      //      BuildNarrativeWithArg.unionArgs(argCounts, chain.events(), args);
      //      chain.remove(event);

      // Only the arguments that this event contributes.
      Set<String> eventArgs = BuildNarrativeWithArg.argsFromEvent(argCounts, chain.events(), eVerb, eDep);
      // And add the best chain argument
      eventArgs.add(chain.bestCachedArg());

      //      System.out.println(chain + " ... " + event);
      //      System.out.println(chain.argCacheSize() + " now " + eventArgs.size() + " args");

      // If there is at least one argument.
      if( chain.argCacheSize() > 0 ) {
        //	if( args.size() > 0 ) {
        //	for( String aa : args ) {
        for( String aa : eventArgs ) {
          //	  long prescore = System.currentTimeMillis();

          Float chainScore = chain.getCachedArgScore(aa);
          float addedScore = scoreEventWithChain(chain, event, eVerb, eDep, aa, false);

          // The cache may not have the argument if it was only seen with this new event
          // and not with the current events in the chain.	
          if( chainScore == null ) {
            //	    System.out.println("new arg: " + aa);
            chainScore = chain.getCachedArgScore(Chain.NO_ARG);

            if( chainScore == null ) {
              // Score with no args, get a base pair score
              chainScore = BuildNarrativeWithArg.groupScoreWithArg(pairs, argCounts, chain.events(), null, true);
              chain.cacheArgScore(Chain.NO_ARG, chainScore);
            }
            //	    chain.cacheArgScore(aa, chainScore);
          }
          //	  else System.out.println("found arg: " + aa);

          //	  System.out.println("orig=" + chainScore + " add=" + addedScore);

          if( chainScore + addedScore > best ) {
            bestArg = aa;
            best = chainScore + addedScore;
          }

          //	  long postscore = System.currentTimeMillis();
          //	  System.out.println("arg took " + ((float)(prescore-postscore) / 1000.0f) + " seconds");
        }

        //	long allargstime = System.currentTimeMillis();
        //	System.out.println("arg scoring took " + ((float)(allargstime-argtime) / 1000.0f) + " seconds");
      }

      // no arguments were found, then ignore arguments and just use pair scores
      else {
        Float chainScore = chain.getCachedArgScore(Chain.NO_ARG);
        float addedScore = scoreEventWithChain(chain, event, eVerb, eDep, null, true);
        if( chainScore == null ) System.out.println("No cached NO_ARG element, run scoreChain()?");
        best = chainScore + addedScore;

        //	System.out.println("NO ARGS");
        //	System.out.println("orig=" + chainScore + " add=" + addedScore);

        //	chain.add(event);
        ///	best = BuildNarrativeWithArg.groupScoreWithArg(pairs, argCounts, chain.events(), 
        //						       arg, true);
        //	chain.remove(event);
        bestArg = null;
      }
    }

    //    System.out.println("best=" + best + " bestArg=" + bestArg);

    // Return the best scoring argument!
    return new Pair(bestArg, best);
  }

  /**
   * Doesn't score the entire chain, but only the contribution to the score
   * that the given event would provide against all the events in the chain.
   * @param ignoreArgs If true, we just score the chain using events, ignoring arg counts.
   * O(G) : G chain size
   */
  public Float scoreEventWithChain(Chain chain, String event, String verb, String dep, 
      String arg,
      boolean ignoreArgs) {
    float score = 0.0f;

    // Score with each event in the chain
    for( String g : chain.events() ) {
      Pair gPair = EventPairScores.split(g);
      String gVerb = (String)gPair.first();
      String gDep  = (String)gPair.second();

      //      String pair = argCounts.buildKey(verb, dep, gVerb, gDep);
      float pairScore = BuildNarrativeWithArg.scorePairWithArg(gVerb, gDep, 
          verb, dep,
          arg,
          pairs.getScore(event, g),
          argCounts, 
          ignoreArgs);
      score += pairScore;
    }
    return score;
  }

  /**
   * Scores a single chain, using the best argument if none given.
   * This function also stores the argument scores in the chain for later lookup.
   * O(R*G^2) : R number of arguments, G chain size
   * @param chain The chain we are scoring
   * @param arg The argument to score with, or null if returning best possible
   * @param ignoreArgs True if we just want to score based on events
   * @return Pair of (1) the best argument and (2) the chain score NOT NORMALIZED
   */
  public Pair scoreChain(Chain chain, String arg, boolean ignoreArgs) {
    String bestArg = null;
    float best = 0.0f;

    System.out.println("scoreChain chain " + chain);

    // No argument constraints, score all arguments.
    if( arg == null && !ignoreArgs ) {
      // Score all the arguments for this chain
      Map<String,Float> args = new HashMap();
      BuildNarrativeWithArg.unionArgs(argCounts, chain.events(), args);

      System.out.println("args size " + args.size());

      if( args.size() > 0 ) {
        BuildNarrativeWithArg.groupScoreAllArgs(pairs, argCounts, ignoreArgs,
            chain.events(), args);

        // Loop over arguments and get the best scoring chain instance
        for( Map.Entry<String,Float> entry : args.entrySet() ) {
          if( entry.getValue() > best ) {
            best = entry.getValue();
            bestArg = entry.getKey();
          }
          // Cache the score for later lookup.
          chain.cacheArgScore(entry.getKey(), entry.getValue());
        }
      }
      // no arguments were found, then ignore arguments and just use pair scores
      else {
        best = BuildNarrativeWithArg.groupScoreWithArg(pairs, argCounts, chain.events(), 
            arg, true);
        // Cache the score for no argument.
        chain.cacheArgScore(Chain.NO_ARG, best);
        bestArg = null;
      }
    }

    // Argument given, or the chain itself requires one argument.
    else {
      best = BuildNarrativeWithArg.groupScoreWithArg(pairs, argCounts, chain.events(), 
          arg, ignoreArgs);
      bestArg = arg;
    }

    //    System.out.println("scoreChain best=" + best);
    return new Pair(bestArg, best);
  }


  /**
   * Given an array of scores, one score for each chain, assign the events 
   * to the chains to maximize the sum of scores.  No two events can be
   * assigned to a single chain.
   * @param scores A double array of scores for each event with each chain.
   *               Each array *must be sorted* in order by highest score.
   * @param events The array of events for a single verb.
   * @param chains A Vector containing the possible chains.
   * @param fillChainID If >= 0, specifies a constraint that one of the verb's
   *                    events must be put in that chain ID.  Ignored otherwise.
   * @return Pair of (1) sum score of events and (2) Vector of Assignment objects
   */
  private Pair chooseBestChainsForEvents(ChainScore[][] chainScores, 
      String[] events, 
      Vector<Chain> chains, 
      int fillChainID) {
    Vector<Assignment> assignments = new Vector();
    int scalar = events.length;
    float best = -1.0f;
    int[] bestcombo = null;
    int[][] combos = COMBOS.elementAt(events.length);

    //    System.out.println("Choosing chains for events " + Arrays.toString(events));

    // Mark chains as safe if they don't contain our verb already
    Pair pair = EventPairScores.split(events[0]);
    String verb = (String)pair.first();
    boolean safe[] = new boolean[chains.size()];
    for( int i = 0; i < chains.size(); i++ ) {
      if( chains.elementAt(i).containsVerb(verb) ) safe[i] = false;
      else safe[i] = true;
    }

    // Score each possible chain assignment
    for( int i = 0; i < combos.length; i++ ) {
      int[] combo = combos[i];

      //      System.out.println("combo " + Arrays.toString(combo));

      // Check that constraints aren't violated, one event per chain      
      if( validCombo(chainScores, combo, fillChainID, safe) ) {
        // Score this chain assignment
        float score = scoreCombo(chainScores, combo);
        if( score > best ) {
          best = score;
          bestcombo = combo;
        }
        //	System.out.println("combo " + i + " is " + Arrays.toString(combo) + " score=" + score);
      }
      else {
        //	System.out.println("Skipping bad combo: " + Arrays.toString(combo));
      }
    }

    // no combo found because of fillChainID
    if( best < 0.0f && fillChainID >= 0 ) {
      //      System.out.println("Improvise because we need to fill id=" + fillChainID);
      int[] combo = new int[events.length];
      int[][] subcombo = COMBOS.elementAt(events.length - 1);
      for( int xx = 0; xx < events.length; xx++ ) {
        combo[xx] = find(fillChainID, chainScores[xx]);
        for( int yy = 0; yy < subcombo.length; yy++ ) {
          int ss = 0;
          for( int zz = 0; zz < events.length; zz++ ) {
            if( zz != xx ) combo[zz] = subcombo[yy][ss++];
          }

          // Check if the combo is valid
          //	  System.out.println("combo=" + Arrays.toString(combo));
          if( validCombo(chainScores, combo, fillChainID, safe) ) {
            //	    System.out.println("VALID");
            // Score this chain assignment
            float score = scoreCombo(chainScores, combo);
            if( score > best ) {
              best = score;
              bestcombo = combo;
            }
          }
        }
      }
    }

    // Assign to chains
    //    System.out.println("Best combo: " + Arrays.toString(bestcombo));
    for( int j = 0; j < events.length; j++ ) {
      // combo = 2 0 1
      int nth = bestcombo[j];
      //      System.out.println("Assigning event " + events[j] + " position " + j + " nth " + nth);
      //      System.out.println("Chain Scores are " + Arrays.toString(chainScores[j]));
      //      System.out.println("The actual score object is " + chainScores[j][nth]);

      // See if there is an argument constraint
      String arg = null;
      if( chainScores[j][nth].argRequired() )
        arg = chainScores[j][nth].arg();

      // Add the assignment
      if( chainScores[j][nth].score() >= MINCHAINSCORE ||
          fillChainID == chainScores[j][nth].chainIndex() )
        assignments.add(new Assignment(events[j], arg, chainScores[j][nth].chainIndex(),
            chainScores[j][nth].score()));
      // Start a new chain if our score is too low
      else {
        //	System.out.println("Low score " + chainScores[j][nth].score() + 
        //			   " assigning to new chain");
        assignments.add(new Assignment(events[j], arg, -1, MINCHAINSCORE));
      }
    }

    return new Pair(best, assignments);
  }

  /**
   * Linear search an array of integers for a specific integer
   * @return The index of the desired integer
   */
  private int find(int key, ChainScore nums[]) {
    for( int i = 0 ; i < nums.length; i++ )
      if( nums[i].chainIndex() == key ) return i;
    return -1;
  }


  /**
   * Given a list of event->chainID assignments, this function adds the events to
   * the appropriate chains.  The chainID is just the index in the chains Vector.
   * @param assignments A Vector of Pairs where each pair is (String event, int chainID)
   * @param chains A Vector of the chains we are augmenting
   */
  private void assignEventsToChains(Vector<Assignment> assignments, Narrative narrative) {
    for( Assignment assign : assignments ) {
      String event = assign.event();
      int id = assign.chainID();

      // Assigning event to a chain
      if( id >= 0 ) narrative.assignEvent(event, id);
      // Creating a brand new chain with just this event
      else id = narrative.createChain(event);

      // Cache the scores for each chain
      scoreChain(narrative.chain(id), null, _ignoreArgs);

      if( assign.arg() != null)
        narrative.constrainChain(id, assign.arg());
    }
  }

  /**
   * Adds a single event to the highest scoring chain.
   * @param narrative The narrative object
   * @param event The event we are adding  e.g. arrest-o
   * @param arg The argument to constrain the event.  Null is ok, just
   *            means all possible arguments will be checked.
   */
  public void addEventToNarrative(Narrative narrative, String event, String arg,
      boolean ignoreArgs) {
    //    System.out.println("addEventToNarrative: event " + event);
    Pair pair = EventPairScores.split(event);
    String verb = (String)pair.first();
    int i = 0;
    float best = -1.0f;  int besti = -1;

    // Check each chain for the best score
    for( Chain chain : narrative.chains() ) {
      // Don't score a chain if it already has the verb in it
      if( !chainContainsVerb(chain, verb) ) {
        Pair result = scoreChainWithEvent(event, arg, chain, ignoreArgs);
        float score = ((Float)result.second() / CHOOSE[chain.size()+1]);

        //	System.out.println("addEventToNarrative: score=" + score);
        if( score >= best ) {
          best = score;
          besti = i;
        }
      }
      i++;
    }

    // If our best score is too low, create a new chain.
    if( besti > -1 && best < MINCHAINSCORE ) {
      int newChainID = narrative.createChain();
      //      System.out.println("addEventToNarrative: created new chain due to low score");
      besti = newChainID;
      best = MINCHAINSCORE;
    }

    // If didn't find a best, then all chains must have the verb's other 
    // arguments.  Create a new chain.
    else if( besti == -1 && narrative.chainSize() > 0 ) {
      int newChainID = narrative.createChain();
      //      System.out.println("addEventToNarrative: created new chain");
      besti = newChainID;
      best = MINCHAINSCORE;
    }

    // Add the event
    //    System.out.println("addEventToNarrative: Adding event " + event + " to " + 
    //		       narrative.chain(besti));
    narrative.assignEvent(event, besti);
  }

  /**
   * @return True if the chain has an event with the given verb
   */
  private boolean chainContainsVerb(Chain chain, String verb) {
    for( String event : chain.events() ) {
      Pair pair = EventPairScores.split(event);
      String v = (String)pair.first();

      if( v.equals(verb) ) return true;
    }
    return false;
  }

  /**
   * @return True if the given combo doesn't assign 2 events to 1 chain
   */
  private boolean validCombo(ChainScore[][] chainScores, 
      int[] combo, 
      int fillChainID,
      boolean safe[]) {
    //    System.out.println("validCombo chainScores.length=" + chainScores.length +
    //		       " combo=" + Arrays.toString(combo) + " id=" + fillChainID);
    // Make sure the array lengths line up
    if( chainScores.length > 0 && chainScores.length == combo.length ) {
      int[] seen = new int[chainScores[0].length];
      boolean satisfied = false;
      if( fillChainID < 0 ) satisfied = true;

      for( int i = 0; i < combo.length; i++ ) {
        int chainIndex = chainScores[i][combo[i]].chainIndex();
        //	System.out.println("validCombo chainIndex=" + chainIndex);
        if( chainIndex > -1 ) {
          // Return now if this index violates our safety array
          if( !safe[chainIndex] ) return false;
          // Check for duplicate chain indices
          if( seen[chainIndex] != 0 ) return false;
          else seen[chainIndex] = 1;
        }
        // Check that we've filled our desired chainID
        if( chainIndex == fillChainID ) satisfied = true;
      }

      // No duplicates yet, but now check that we've filled out desired chain.
      if( satisfied ) return true;
      else return false;
    }
    else return false;
  }

  /**
   * Given a combination ordering, we return the sum of chain scores using
   * that assignment order.  The length of the combo should equal the length
   * of the chainScores array.
   * @param chainScores Double array of scores of chains for each verbal event
   * @param combo A combinatorics ordering of the number of events
   */
  private float scoreCombo(ChainScore[][] chainScores, int[] combo) {
    float score = 0.0f;
    for( int i = 0; i < combo.length; i++ )
      score += chainScores[i][combo[i]].score();
    return score;
  }

  /**
   * Takes a given set of event pairs (verb-dep) and returns a set of just
   * verbs involved in all pairs.
   */
  private Set<String> eventsToVerbs(Set<String> neighbors) {
    Set<String> verbs = new HashSet();
    for( String neighbor : neighbors ) {
      Pair pair = EventPairScores.split(neighbor);
      String verb = (String)pair.first();

      verbs.add(verb);
    }
    return verbs;
  }

  public Set<String> getNeighbors(Set<String> events) {
    return pairs.getNeighbors(events);
  }

  /**
   * Clusters new verbs around the given narrative.
   * @param narrative The Narrative we want to grow.
   * @param events The set of events to find neighbors of, which determines
   *               what neighbors are considered for addition to the narrative.
   */
  public void growCluster(Narrative narrative, Set<String> events, int maxGrowth) {
    System.out.println("GROWING CLUSTER!");
    for( String ev : events ) System.out.println("..." + ev);

    for( int i = 0; i < maxGrowth; i++ ) {
      // Make a list of neighbors
      Set<String> neighbors = getNeighbors(events);

      //      for( String ne : neighbors ) System.out.println("--" + ne);

      // Pull out just the verbs
      Set<String> verbs = eventsToVerbs(neighbors);
      // Return NOW if there are no neighbors
      if( neighbors == null ) return;

      // Grow the cluster
      Pair result = growClusterByOne(narrative, verbs);
      String verb = (String)result.first();
      float score = (Float)result.second();

      // Save the new events we just added
      for( String dep : DEPS )
        events.add(EventPairScores.buildKey(verb, dep));

      System.out.println("**Added verb " + verb + "**");
      System.out.println(narrative);
    }
  }

  /**
   * Chooses one verb that increases the narrative score by the most due
   * to its inclusion in the narrative.  We always choose the closest verb,
   * there is no threshold cutoff to *not* include a verb.
   * @param narrative The Narrative we want to grow.
   * @param verbs The set of potential verbs to add to our narrative.
   * @return A Pair: (1) added verb, (2) verb score with the narrative
   */
  private Pair growClusterByOne(Narrative narrative, Set<String> verbs) {
    Vector<Assignment> bestAssignment = null;
    float best = -1.0f;
    String bestVerb = null;

    System.out.println("Growing by One from " + verbs.size());

    // Check each neighboring event
    for( String verb : verbs ) {
      // Skip verbs we already include
      if( !narrative.containsVerb(verb) ) {
        // Score this verb in the narrative      
        Pair result = scoreVerbInNarrative(verb, BASEARGS, narrative, _ignoreArgs);
        float score = (Float)result.first();
        //	System.out.println("Final " + verb + " score=" + score);
        if( score > best ) {
          best = score;
          bestVerb = verb;
          bestAssignment = (Vector<Assignment>)result.second();
        }
      }
    }

    System.out.println("Best verb: " + bestVerb + " score: " + best);

    // Add the best
    if( bestAssignment != null )
      assignEventsToChains(bestAssignment, narrative);

    // Return the best score
    return new Pair(bestVerb, best);
  }


  /**
   * Scores a narrative by summing the individual chain scores.
   * @param narrative The narrative object to score.
   * @param ignoreArgs Set to true if the scoring should ignore args.
   * @return The overall score for the given narrative.
   */
  public float scoreNarrative(Narrative narrative, boolean ignoreArgs) {
    float totalScore = 0.0f;

    // Loop over the chains, sum the scores!
    for( int i = 0; i < narrative.chainSize(); i++ ) {
      Pair result = scoreChain(narrative.chain(i), null, ignoreArgs);
      String bestArg = (String)result.first();
      float score = (Float)result.second();

      System.out.println("chain i=" + i + " score=" + score);
      // Normalize by chain size.
      if( _normalizeVerbAdditions ) {
        //	score = score / (float)CHOOSE[narrative.chain(i).size() + 1];
        score = (score > 0.0f ) ? score / (float)(narrative.chain(i).size() - 1) : 0.0f;
      }

      totalScore += score;
    }
    return totalScore;
  }


  /**
   * Scores each individual verb's contribution to the narrative.
   * Updates the Narrative object with each verb's score.
   */
  public void scoreNarrativeVerbs(Narrative narrative, boolean ignoreArgs) {
    float score = scoreNarrative(narrative, ignoreArgs);
    System.out.println("Narrative score: " + score);

    for( String verb : narrative.verbs() ) {
      System.out.println("Removing verb: " + verb);
      Narrative copy = narrative.clone();
      copy.removeVerb(verb);
      System.out.println("New Narrative:");
      System.out.println(copy);

      float newScore = scoreNarrative(copy, ignoreArgs);
      float diff = score - newScore;

      System.out.println("Setting verb: " + verb + " score: " + diff);
      narrative.setVerbScore(verb, diff);
    }
  }


  /**
   * Performs random "walks" through the narrative, making small moves
   * of the events into different chains based on distributions of their
   * confidence scores in each chain.
   */
  private void permute(Narrative narrative) {
    Random random = new Random();
    Vector<Chain> chains = narrative.chains();
    int loop = 0;
    float alpha = 3.0f;
    float alphaDecrement = alpha / PERMUTELOOPS * 2;

    System.out.println("******************");
    System.out.println("Beginning Permutations!");
    System.out.println("******************");

    while( loop < PERMUTELOOPS ) {
      String event = removeRandomEvent(narrative);

      // Calculate the score for each chain
      float probs[] = new float[chains.size()];
      float sum = 0.0f;
      int i = 0;
      for( Chain chain : chains ) {
        Pair result = scoreChainWithEventCheckDuplicates(event, chain, _ignoreArgs);
        float score = ((Float)result.second() / CHOOSE[chain.size()+1]);

        // Smooth the score (unless zero score (constraint violated))
        if( score > 0.0f ) score += alpha;

        // Penalize
        score -= chainPenaltyWithEvent(event, chain);

        sum += score;
        probs[i++] = score;
      }

      // Create distribution over scores
      for( int j = 0; j < probs.length; j++ )
        probs[j] = probs[j] / sum;

      // Randomly choose a chain
      i = 0;
      float end = 0.0f;
      float rand = random.nextFloat();
      while( rand > end && i < chains.size() ) end += probs[i++];
      int chosenChain = i-1;

      System.out.println("**" + event + " " + Arrays.toString(probs) + " chose " +
          chosenChain);
      //      System.out.println("  *alpha=" + alpha);

      // Put the event in the chain
      chains.elementAt(chosenChain).add(event);

      System.out.println(narrative);

      loop++;
      // Decrement the smoothing constant
      alpha = alpha - alphaDecrement;
      if( alpha < 0.4 ) alpha = 0.0f;
    }    
  }

  /**
   * Randomly chooses an event in the narrative and removes it from its chain,
   * returning the string (e.g. arrest-o).
   */
  private String removeRandomEvent(Narrative narrative) {
    Vector<Chain> chains = narrative.chains();

    // Count all the events
    int count = 0;
    for( Chain chain : chains ) count += chain.size();

    // Get a random number
    Random random = new Random();
    int choose = random.nextInt(count);

    // Remove and return the event
    count = 0;
    for( Chain chain : chains ) {
      if( choose < count + chain.size() ) {
        String event = chain.eventAt(choose - count);
        chain.remove(event);
        return event;
      }
      count += chain.size();
    }

    return null;
  }


  private float narrativePenalty(Narrative narrative) {
    float penalty = 0.0f;
    for( Chain chain : narrative.chains() ) {
      penalty += chainPenalty(chain);
    }
    return penalty;
  }

  private float chainPenalty(Chain chain) {
    int numEvents = chain.size();
    if( numEvents > 0 ) return 1.0f * 0.1f;
    else return 0.0f;
  }

  private float chainPenaltyWithEvent(String event, Chain chain) {
    // If we're starting a new chain, then penalize
    if( chain.size() == 0 ) return 1.0f * 0.1f;
    else return 0.0f;
  }

  public static void setMinChainScore(float s) {
    MINCHAINSCORE = s;
  }
  public static float minChainScore() {
    return MINCHAINSCORE;
  }

  public static void setNormalizeVerbAdditions(boolean n) {
    _normalizeVerbAdditions = n;
  }
  public static boolean normalizeVerbAdditions() {
    return _normalizeVerbAdditions;
  }

  public static void setArgCountCutoff(int c) {
    _argCountCutoff = c;
  }
  public static float argCountCutoff() {
    return _argCountCutoff;
  }

  public void setIgnoreArgs(boolean ignore) {
    _ignoreArgs = ignore;
  }

  //******************************************
  /**
   * ClusteringScorer Interface
   */
  public double score(Cluster cluster) {
    return scoreNarrative((Narrative)cluster, false);
  }

  /**
   * This should really only be called by a clustering algorithm, and if
   * the chains have argument IDs.  In other words, each chain is a specific
   * entity that we already know, say from a document.
   * Merging is thus just lining up chains by argument ID, and not searching
   * for a maximum score chain alignment.
   */
  public double scoreMerge(Cluster c1, Cluster c2) {
    Narrative primary = ((Narrative)c1).clone();
    Narrative tomerge = (Narrative)c2;

    double primaryScore = scoreNarrative(primary, false);
    double tomergeScore = scoreNarrative(tomerge, false);

    System.out.println("scoreMerge c1 = " + c1);
    System.out.println("scoreMerge c2 = " + c2);

    primary.merge(tomerge);
    double mergedScore = scoreNarrative(primary, false);

    System.out.println("scoreMerge merged! = " + primary);

    double score = mergedScore - primaryScore - tomergeScore;
    // This could be negative if the two schemas are repeated event mentions,
    // using the same verb string.
    if( score < 0.0f ) score = 0.0f;
    // Normalize by the size of the merged narrative.
    System.out.println("Normalizing " + score + " by " + (primary.size()-1));
    if( primary.size() > 1 ) score /= primary.size() - 1;

    System.out.println("scoreMerge SCORES " + mergedScore + " - " + primaryScore + " - " + tomergeScore + " = " + score);

    System.out.println("scoreMerge() returning " + score + "!");
    return score;
  }
  //******************************************



  /**
   * Special class to store scores for events being added to a chain.
   * We need to be able to sort these scores and still be linked to the
   * chain from which the scores came.
   */
  private class ChainScore implements Comparable {
    float score;
    String arg;
    boolean argRequired = false;
    int chainIndex;

    ChainScore(float score, String arg, int chainIndex, boolean required) {
      this.score = score;
      this.arg = arg;
      this.chainIndex = chainIndex;
      this.argRequired = required;
    }

    public int compareTo(Object other) {
      if( ((ChainScore)other).score() > score ) return 1;
      else if( score > ((ChainScore)other).score() ) return -1;
      else return 0;
    }

    public String toString() {
      return chainIndex + " " + arg + " " + score + " " + argRequired;
    }      

    public float score() { return score; }
    public String arg() { return arg; }
    public boolean argRequired() { return argRequired; }
    public int chainIndex() { return chainIndex; }
  }


  /**
   * Main
   */
  public static void main(String[] args) {
    // Test narrative
    BuildNarrativeWithAllArgs builder = new BuildNarrativeWithAllArgs(args[0], args[1]);

    // Read the data files from disk
    builder.loadData();

    // Command prompt
    builder.onlineInput();
  }
}
package nate.narrative;

import java.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import nate.Pair;
import nate.BasicEventAnalyzer;
import nate.util.Triple;
import nate.args.VerbPairArgCounts;
import nate.cluster.GeneralHierarchicalClustering;
import nate.cluster.Cluster;
import nate.cluster.ClusterUtil;


/**
 * This class takes a list of document events that have their arguments
 * already resolved (A kill B), and reads them all in.  Each event is put
 * into its own narrative.  The class then clusters the narratives,
 * creating larger narratives that satisfy the variable constraints, and
 * scored with our event graph.
 *
 * Partition <pmi pairs> <arg counts>|ignore
 *
 */
public class Partition {
  BuildNarrativeWithAllArgs _narrativeScorer;
  String _pairsPath;
  String _argCountsPath;

  // Clustering merge cutoff.
  float _minClusterScore = 0.15f;


  public Partition(String pairsPath, String argsPath) {
    this._pairsPath = pairsPath;
    this._argCountsPath = argsPath;

    if( _argCountsPath.equals("ignore") ) 
      _argCountsPath = null;
  }

  /**
   * Load pairs and argument counts from disk
   */
  public void loadData() {
    _narrativeScorer = new BuildNarrativeWithAllArgs(_pairsPath, _argCountsPath);
    if( _argCountsPath == null )
      _narrativeScorer.setIgnoreArgs(true);
    _narrativeScorer.loadData();
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
      Vector<Narrative> schemas = new Vector();
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
	  //	  slots.clear();
	  schemas.clear();
	}

	else if( parts[0].equals("") ) {
	  if( narrative != null ) System.out.println(narrative);
	  else System.out.println();
	}

	// Grow the narrative by a certain number of verbs
	else if( parts[0].equals("partition") && parts.length == 1 ) {
	  //	  partition(slots);
	  partition(schemas);
	}

	else if( parts[0].equals("test") && parts.length == 1 ) {
	  test();
	}

	// Append to our event slots.
	else {
	  Narrative schema = createShortNarrative(input);
	  schemas.add(schema);
	  System.out.println(schema);
	}

	System.out.print("> ");
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Creates a short narrative from a single event with variables
   * in place of arguments.
   * e.g. A arrest B
   *      This creates a narrative with 2 chains: A arrest, arrest B.
   */
  private Narrative createShortNarrative(String str) {
    Narrative schema = new Narrative();
    String parts[] = str.split(" ");

    // Pull off the events now  e.g. "A arrest B"
    if( parts[0].length() == 1 ) {
      Chain chain = new Chain();
      chain.setArgID(parts[0]);
      chain.add(parts[1] + "-s");
      schema.addChain(chain);

      if( parts.length == 3 ) {
	chain = new Chain();
	chain.setArgID(parts[2]);
	chain.add(parts[1] + "-o");
	schema.addChain(chain);
      }
    }

    // "arrest B"
    else {
      Chain chain = new Chain();
      chain.setArgID(parts[1]);
      chain.add(parts[0] + "-o");
      schema.addChain(chain);
    }

    return schema;
  }


  private void partition(Vector<Narrative> schemas) {
    System.out.println("Partition paritioning!!");
    for( Narrative nar : schemas ) System.out.println("* " + nar);

    // Cluster the Schemas.
    GeneralHierarchicalClustering clustering = 
      new GeneralHierarchicalClustering(_narrativeScorer);
    clustering.setMinInitialSimilarityScore(0.0f);
    clustering.setMinClusteringScore(_minClusterScore);

    // Clustering is destructive to the objects, so I clone them since there
    // usually aren't many narratives to merge.
    Vector<Narrative> cloned = new Vector();
    for( Narrative schema : schemas ) cloned.add(schema.clone());
    // Cluster!
    Vector<Triple> history = clustering.cluster(cloned);

    // Print the History
    System.out.println("******** CLUSTERING FINISHED ********");
    for( Triple trip : history ) System.out.println(trip);
    printClusters(schemas, history);
    System.out.println("********");
  }


  private void printClusters(Vector<Narrative> schemas, Vector<Triple> order) {
    Vector<Set<Integer>> clusters = ClusterUtil.reconstructClusters(order);

    for( Set<Integer> cluster : clusters ) {
      Narrative merged = null;
      boolean first = true;
      for( Integer index : cluster ) {
	Narrative schema = schemas.elementAt(index);
	if( first ) merged = schema.clone();
	else merged.merge(schema);
	System.out.print(index + " ");
	first = false;
      }
      // Print the merged narrative.
      System.out.println();
      System.out.println(merged);
    }
  }


  private void test() {
    String[] tests = { "B kidnap A",
		       "find A dead",
		       "C kidnap A",
		       "C force A",
		       "A accompany C",
		       "B kidnap A",
		       "A travel",
		       "A face D",
		       "find A guilty",
		       "execute A",
		       "murder A",
		       "E kill F",
		       "kill G",
		       "G travel",
		       "H blow_up I" };
    Vector<Narrative> schemas = new Vector();
    for( String test : tests )
      schemas.add(createShortNarrative(test));
    partition(schemas);

    // TEST 2
    String[] tests2 = { "kill A",
			"injure B",
			"D launch E",
			//			"set on fire E",
			"result G",
			"D bomb H",
			"cause K",
			"kill A",
			"injure A" };
    schemas = new Vector();
    for( String test : tests2 )
      schemas.add(createShortNarrative(test));
    partition(schemas);

    // TEST 3
    String[] tests3 = { "A carry_out",
	      "A follow C",
	      "D cause E",
	      "mobilize F",
	      "fear G",
	      "H send I",
	      "H call",
	      "J oppose K",
	      "I call",
	      "M occur",
	      "N conclude 1",
	      "N explain 2",
	      "O implement 3",
	      "hurl 4",
	      "Q occur",
	      "5 leave R",
	      "place S",
	      "F place S",
	      "S go_off",
	      "T shatter U",
	      "F receive 6" };
    schemas = new Vector();
    for( String test : tests3 )
      schemas.add(createShortNarrative(test));
    partition(schemas);

    // TEST 4
    String[] tests4 = { "A kidnap B",
	      "A force B",
	      "B climb",
	      "A take_off",
	      "F escape",
	      "F take 1",
	      "F determine 2",
	      "kidnap B",
	      "A set_fire G",
	      "A cause",
	      "3 rack C",
	      "blame H",
	      "H accuse" };
    schemas = new Vector();
    for( String test : tests4 )
      schemas.add(createShortNarrative(test));
    partition(schemas);

    // TEST 5
    String[] tests5 = { "A perpetrate",
			"A set_on B",
			"A fail",
			"C alert",
			"C flee",
			"A arrive",
			"A overpower D",
			"A burn E",
			"burn E",
			"A attempt",
			"A bring C",
			"C flea",
			"A block F",
			"A destroy F",
			"prevent G",
			"G follow A" };
    schemas = new Vector();
    for( String test : tests5 )
      schemas.add(createShortNarrative(test));
    partition(schemas);

    // TEST 6
    String[] tests6 = { "A claim C",
			"A kill B",
			"A claim C",
			"A detonate D",
			"D cause F",
			"D destroy G" };

    schemas = new Vector();
    for( String test : tests6 )
      schemas.add(createShortNarrative(test));
    partition(schemas);

  }


  /**
   * Main
   */
  public static void main(String[] args) {
    // Test narrative
    Partition builder = new Partition(args[0], args[1]);

    // Read the data files from disk
    builder.loadData();

    // Command prompt
    builder.onlineInput();
  }
}

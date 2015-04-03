package nate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import nate.order.EventParser;
import nate.util.Ling;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;
import net.didion.jwnl.JWNL;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.dictionary.Dictionary;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.util.Span;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * Reads a directory of EnglishGigaword files as input, outputs some basic 
 * stats about event ordering.
 * Option 1: Calculate IDF scores.  With the -calcidf flag, only counts verbs
 *           and outputs their IDF scores to your current directory.
 * Option 2: Run normally with -events/-deps/-parsed and it outputs a file of
 *           all verb pairs sharing arguments in *.pairs-year files.
 *           It also simultaneously creates .tlink files to represent each verb
 *           pair in tlink (Timebank) form.
 * Option 3: Run option 2 but with the -nocoref flag.  This ignores the coref
 *           system and only matches arguments if their strings are similar.
 * 
 *
 * -idf <file>
 *  Gives a cache file of IDF scores to use as constant across all input.
 * -idflemma <file>
 * Gives a cache file of IDF lemmas to build on with -calcidf
 *
 * -calcidf
 * Runs and calculates IDF scores for verbs.
 *
 * -parsed <dir>
 * Indicates the given directory contains parse trees in *.parse files.
 *
 * -deps <dir>
 * Indicates the given directory contains the dependency files.
 *
 * -events <dir>
 * The directory containing all the files with event/entity information.
 * If included, we skip the coref step.
 *
 * -filter <file>
 * Gives the path to a list of document names. Only these documents are
 * processed and all others are ignored.
 *
 * -dist <int>
 * The sentence distance to cutoff counting pairs of events.
 *
 * -lemmas
 * Converts all events to their lemmas before counting.
 *
 * -all
 * Count all event pairs, not just ones that share arguments.
 *
 * -nocoref
 * Don't use the coreference output, but only match similar strings.
 *
 * -single
 * Present if the -events and -parsed paths are files, not directories.
 * All data paths should be single files with the same stories in them.
 *
 */
public class BasicEventAnalyzer {
  private Options options;
  private MaxentTagger tagger;
  private LexicalizedParser parser;
  private TreebankLanguagePack tlp;
  private GrammaticalStructureFactory gsf;
  private PrintWriter pwOut;
  private Coref coref = null;

  public String tlinkDir = "verbs-tlinks";
  public static String pairsOutput = "verbpairs-nyt-lemmas-wnouns/verbs.pairs";
  public static String trigramsOutput = "verbs.tri";
  private String serializedGrammar = "";
  private String _wordnetPath = "";
  private String _corefPath = "";
  private String _dataPath = "";
  private String _eventPath = "";
  private String _depsPath = "";
  private String _duplicatesPath = "duplicates";
  private String _filterList = null;
  private boolean _dataSingleFile = false; // _dataPath is directory or single file

  private HashMap<String,Boolean> wordToVerb;
  private HashMap<String,String> verbToLemma;
  private HashMap<String,String> nounToLemma;
  private HashMap<String,String> adjToLemma;
  private Map<String, Integer> pairCounts;
  private Map<String, Integer> trigramCounts;
  private HashSet<String> _duplicates;
  private HashSet<String> _ignoreList;
  private HashSet<String> tlinks;
  private int NUMDOCS = 0;
  private long startTime;


  // ---------- IDF Information ----------
  public static float IDFcutoff = 1.5f;
  public static float IDFLemmaCutoff = 0.7f;
  //  private int docCutoff = 10; // ignore words occurring in less docs
  //  private int pairsCutoff = 10; // don't save pairs occurring less times
  public static int docCutoff = 10; // ignore words occurring in this or less docs
  public static int pairsCutoff = 1; // don't save pairs occurring this or less times
  public static int trigramCutoff = 1; // don't save trigrams occurring this or less times
  private IDFMap idf;
  private IDFMap idfLemmas;
  private String idfCache = "";
  private String idfLemmaCache = "";
  public  boolean calculateIDF = false; // true if IDF to be counted
  private boolean parsed = false; // changed to false if a cache is loaded
  private boolean nocoref = false;
  private boolean _lemmasOnly = false; // true if verbs should be lemmatized
  private boolean countTrigrams = false; // true if trigrams counted too!
  private boolean countAll = false; // true if counting all verbs, no arg sharing required
  private int pairDistance = 999999;


  private String tracking[] = { };
  /*    "answered:hoped;nsubj:nsubj",
    "criticized:refused;obj:nsubj",
    "fell:sat;nsubj:nsubj",
    "give:believe;obj:nsubj",
    "laments:writes;nsubj:nsubj",
    "pay:reduced;obj:nsubj",
    "played:scoring;nsubj:nsubj",
    "pointed:asked;nsubj:obj",
    "predicted:believed;nsubj:nsubj",
    "saw:wondered;nsubj:nsubj",
    "signed:comes;nsubj:nsubj",
    "tell:married;nsubj:nsubj" };
   */

  public BasicEventAnalyzer(String[] args) {
    handleParameters(args);
    System.out.println("Wordnet at " + _wordnetPath);

    wordToVerb = new HashMap(75000);
    verbToLemma = new HashMap(75000);
    nounToLemma = new HashMap(75000);
    adjToLemma = new HashMap(75000);
    pairCounts = new HashMap(2000000);
    //    trigramCounts = new HashMap(2000000);
    tlinks = new HashSet();
    idf = new IDFMap(75000);
    idfLemmas = new IDFMap(75000);
    if( !calculateIDF ) {
      idf.fromFile(idfCache);
      if( idfLemmaCache.length() > 0 ) idfLemmas.fromFile(idfLemmaCache);
    }

    EventParser.createDirectory(tlinkDir);

    initLexResources();
    System.out.println("parsed " + parsed + ", nocoref " + nocoref);
  }


  private void initLexResources() {
    options = new Options();

    try {
      // Load WordNet
      if( _wordnetPath.length() > 0 )
        JWNL.initialize(new FileInputStream(_wordnetPath));

      // POS Tagger
      if( !parsed )
        tagger = new MaxentTagger("../stanford-postagger/bidirectional/train-wsj-0-18.holder");
      //      tagger = new MaxentTagger("/u/nlp/distrib/postagger-2006-05-21/wsj3t0-18-bidirectional/train-wsj-0-18.holder");

      // Parser
      parser = Ling.createParser(serializedGrammar);
      tlp = new PennTreebankLanguagePack();
      gsf = tlp.grammaticalStructureFactory();
      pwOut = parser.getOp().tlpParams.pw();

      // Coref
      if( _corefPath.length() > 0 && _eventPath.length() == 0 && !calculateIDF ) 
        coref = new Coref(_corefPath);
      else if( _eventPath.length() == 0 ) { // if events are loaded, don't need coref
        System.out.println("WARNING: no coref loaded");
        nocoref = true;
      }

      // Duplicate Gigaword files to ignore
      _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);

      // Ignore List (evaluation docs)
      _ignoreList = new HashSet();
      _ignoreList.add("NYT_ENG_19940701.0076");
      _ignoreList.add("NYT_ENG_19940701.0266");
      _ignoreList.add("NYT_ENG_19940701.0271");
      _ignoreList.add("NYT_ENG_19940703.0098");
      _ignoreList.add("NYT_ENG_19940705.0192");
      _ignoreList.add("NYT_ENG_19940707.0298");
      _ignoreList.add("NYT_ENG_19940707.0302");
      _ignoreList.add("NYT_ENG_19940708.0340");
      _ignoreList.add("NYT_ENG_19940708.0246");
      _ignoreList.add("NYT_ENG_19940709.0181");
      String arr[] = { 
          "NYT_ENG_20010103.0419", "NYT_ENG_20010421.0160", "NYT_ENG_20010920.0485",
          "NYT_ENG_20010109.0219", "NYT_ENG_20010504.0008", "NYT_ENG_20010926.0056",
          "NYT_ENG_20010118.0310", "NYT_ENG_20010509.0423", "NYT_ENG_20011006.0231",
          "NYT_ENG_20010119.0006", "NYT_ENG_20010509.0428", "NYT_ENG_20011016.0074",
          "NYT_ENG_20010129.0047", "NYT_ENG_20010601.0019", "NYT_ENG_20011025.0388",
          "NYT_ENG_20010206.0268", "NYT_ENG_20010606.0375", "NYT_ENG_20011102.0438",
          "NYT_ENG_20010220.0078", "NYT_ENG_20010622.0207", "NYT_ENG_20011104.0024",
          "NYT_ENG_20010222.0365", "NYT_ENG_20010628.0267", "NYT_ENG_20011116.0194",
          "NYT_ENG_20010226.0275", "NYT_ENG_20010628.0346", "NYT_ENG_20011121.0151",
          "NYT_ENG_20010303.0173", "NYT_ENG_20010706.0092", "NYT_ENG_20011201.0008",
          "NYT_ENG_20010306.0129", "NYT_ENG_20010706.0292", "NYT_ENG_20011201.0169",
          "NYT_ENG_20010307.0079", "NYT_ENG_20010708.0122", "NYT_ENG_20011205.0143",
          "NYT_ENG_20010307.0105", "NYT_ENG_20010726.0177", "NYT_ENG_20011224.0120",
          "NYT_ENG_20010328.0175", "NYT_ENG_20010801.0291", "NYT_ENG_20011224.0125",
          "NYT_ENG_20010416.0419", "NYT_ENG_20010802.0276",
          "NYT_ENG_20010417.0324", "NYT_ENG_20010828.0078",
          "NYT_ENG_20010417.0372", "NYT_ENG_20010829.0034",
          "NYT_ENG_20010419.0058", "NYT_ENG_20010904.0446" };
      for( String s : arr ) _ignoreList.add(s);

    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  private void handleParameters(String[] args) {
    int i = 0;
    while( i < args.length ) {
      // specifies the location of the WordNet .xml file
      if( args[i].equalsIgnoreCase("-wordnet") && args.length > i+1 ) {
        _wordnetPath = args[i+1];
        i++;
      }
      // specifies the location of the coref data file
      if( args[i].equalsIgnoreCase("-coref") && args.length > i+1 ) {
        _corefPath = args[i+1];
        i++;
      }
      // specifies the location of the file of IDF scores
      else if( args[i].equalsIgnoreCase("-idf") && args.length > i+1 ) {
        idfCache = args[i+1];
        System.out.println("IDF Cache " + idfCache);
        calculateIDF = false; // don't recalculate
        i++;
      }
      // specifies the location of the file of IDF scores for lemmas
      else if( args[i].equalsIgnoreCase("-idflemma") && args.length > i+1 ) {
        idfLemmaCache = args[i+1];
        System.out.println("IDF Lemma Cache " + idfLemmaCache);
        i++;
      }
      // specifies the location of the parser grammar
      else if( args[i].equalsIgnoreCase("-grammar") && args.length > i+1 ) {
        serializedGrammar = args[i+1];
        System.out.println("Grammar " + serializedGrammar);
        i++;
      }
      else if( args[i].equalsIgnoreCase("-parsed") ) {
        System.out.println("Parsed data");
        parsed = true;
      }
      else if( args[i].equalsIgnoreCase("-filter") ) {
        System.out.println("Filter");
        _filterList = args[i+1];
        i++;
      }
      else if( args[i].equalsIgnoreCase("-events") ) {
        System.out.println("Event File");
        _eventPath = args[i+1];
        i++;
      }
      else if( args[i].equalsIgnoreCase("-deps") ) {
        System.out.println("Dependencies File");
        _depsPath = args[i+1];
        i++;
      }
      else if( args[i].equalsIgnoreCase("-calcidf") ) {
        System.out.println("IDF to be calculated");
        calculateIDF = true;
      }
      else if( args[i].equalsIgnoreCase("-nocoref") ) {
        System.out.println("Coref off");
        nocoref = true;
      }
      else if( args[i].equalsIgnoreCase("-dist") ) {
        pairDistance = Integer.parseInt(args[i+1]);
        System.out.println("Distance " + pairDistance);
      }
      else if( args[i].equalsIgnoreCase("-lemmas") ) {
        _lemmasOnly = true;
        IDFcutoff = IDFLemmaCutoff;
        System.out.println("Using lemma forms");
      }
      else if( args[i].equalsIgnoreCase("-all") ) {
        countAll = true;
        pairsOutput = pairsOutput + "-all";
        trigramsOutput = trigramsOutput + "-all";
        System.out.println("Counting all verb pairs!");
      }
      else if( args[i].equalsIgnoreCase("-trigrams") ) {
        countTrigrams = true;
        System.out.println("Counting trigrams too...");
      }
      else if( args[i].equalsIgnoreCase("-single") ) {
        _dataSingleFile = true;
        System.out.println("-single flag true");
      }
      i++;
    }

    _dataPath = args[args.length - 1];
  }


  /**
   * @return True if the tree contains the entire context span (char based span)
   * Overlaps are returned false.
   */
  private boolean containsContext(Tree full, Tree subtree, MentionContext mc) {
    // Get the offset of the subtree
    int offset = TreeOperator.inorderTraverse(full, subtree);
    Span span = mc.getSpan();

    //    if( span.getEnd() < offset ) return false;
    if( span.getStart() < offset ) {
      //      System.out.println("Not contained");
      return false;
    }

    // Get the length of the subtree
    int length = TreeOperator.treeStringLength(subtree);
    if( span.getEnd() <= (offset+length) ) return true;

    return false;
  }


  /**
   * @return True if the subtree contains the word indices start->end
   */
  private boolean containsSpan(Tree full, Tree subtree, int start, int end) {
    // +1, words indexed at 1 not 0
    int offset = TreeOperator.wordIndex(full, subtree)+1; 
    if( start < offset ) { return false; }

    ///    int length = TreeOperator.treeWordLength(subtree);
    int length = TreeOperator.countLeaves(subtree);
    if( end < offset+length ) { return true; }

    return false;
  }


  /**
   * @desc Returns the syntactic relation the context holds in relation
   *       to the verb.
   * @param full The sentence's full parse tree
   * @param subtree The verb subtree
   * @param mention An entity mention in the tree
   * @return A String indicating the syntactic relationship, null if none
   */
  private String mentionRelationToVerb(Tree full, Tree subtree, EntityMention mention) {
    //    System.out.println("check " + mention);
    Tree gparent = subtree.parent(full);
    Tree descendant = subtree;
    while( gparent.label().toString().startsWith("VP") ) {
      descendant = gparent;
      gparent = gparent.parent(full);
    }

    // Find the subject, make a list of NPs before the verb
    LinkedList<Tree> list = new LinkedList();
    for( Tree child : gparent.getChildrenAsList() ) {
      if( child == descendant ) break;
      list.addFirst(child);
    }
    // Only look at the closest NP to the Verb
    for( Tree child : list ) {
      if( child.label().toString().startsWith("NP") ) {
        //	if( containsContext(full, child, mc) ) return "SUBJECT";
        if( containsSpan(full, child, mention.start(), mention.end()) ) 
          return "SBJ";
        break;
      }
    }

    //    System.out.println("** " + mention);
    //    System.out.println(":: " + subtree);

    // Check the object
    boolean foundVerb = false;
    for( Tree child : subtree.getChildrenAsList() ) {
      String label = child.label().toString();
      //      System.out.println(foundVerb + " child: " + label);

      if( foundVerb ) {
        // skip adverbs (ADVP) and "not" (RB)
        if( label.equals("ADVP") || label.equals("RB") ) { }
        // skip relative clauses (SBAR) and infinitive "to" adjuncts (S)
        else if( label.equals("SBAR") || label.equals("S") ) { } // Also "S" ??
        // skip adjectives "is X", they don't carry entity info
        else if( label.equals("ADJP") ) { }
        // prepositional phrases
        // WARNING: The PP could have a relative clause SBAR in it...we
        //          should really check for that.
        else if( label.equals("PP") ) { 
          if( containsSpan(full, child, mention.start(), mention.end()) ) {
            //	    System.out.println("PP " + child.firstChild().firstChild());
            return "PPOBJ";
          }
        }
        // noun objects
        else if( label.equals("NP") ) {
          if( containsSpan(full, child, mention.start(), mention.end()) ) 
            //	    System.out.println("OB " + child.firstChild().firstChild());
            return "OBJ";
        }
        // VP arguments mean that the verb is probably just an auxiliary
        else if( label.equals("VP") ) {
          break;
        }
        else {
          //	  System.out.println("ERROR: UNEXPECTED phrase adjunct: " + label);
        }
      }

      // the head verb is not always first child...
      else if( label.startsWith("VB") ) foundVerb = true;
    }

    return null;
  }


  private Tree eventSubTree(Tree tree, WordEvent event) {
    Tree terminal = TreeOperator.indexToSubtree(tree, event.position());
    return terminal.parent(tree); // return the VP, e.g. (VP (VBG running)) 
  }

  /**
   * Find all dependencies with the event as governor, and particle as relation
   */
  public static String findParticle(Collection<TypedDependency> deps, WordEvent event) {
    for( TypedDependency dep : deps ) {
      if( dep.gov().index() == event.position() ) {
        if( dep.reln().toString().equals("prt") ) {
          //	  System.out.println("Found particle: " + dep.dep().value());
          return dep.dep().label().value();
        }
      }
    }
    return null;
  }

  /**
   * Return the participle of a verb's phrase structure tree.
   */
  public static String findParticleInTree(Tree verb) {
    if( verb != null ) {
      for( Tree child : verb.getChildrenAsList() ) {
        // (PRT (RB up))
        if( child.label().value().equals("PRT") )
          return child.firstChild().firstChild().label().value();
      }      
    }
    return null;
  }


  /**
   * Search a list of dependencies for an entity's mention with a specific event.
   */
  public static String depVerbRelation(Collection<TypedDependency> deps, WordEvent event, EntityMention mention) {
    Pair<String,Integer> pair = depVerbRelationWithIndex(deps, event, mention);
    if( pair != null ) return pair.first();
    else return null;
  }

  /**
   * Search a list of dependencies for an entity's mention with a specific event.
   * @return A pair: the relation between event and mention, and the word index of the mention in the dependency.
   */
  public static Pair<String,Integer> depVerbRelationWithIndex(Collection<TypedDependency> deps, WordEvent event, EntityMention mention) {
    Vector<TypedDependency> found = new Vector<TypedDependency>();

    // make sure the mention doesn't overlap this event
    // (this happens with nominal events sometimes)
    if( event.position() < mention.start() || event.position() > mention.end() ) {

      // find all dependencies with the event as governor
      for( TypedDependency dep : deps ) {
        if( dep.gov().index() == event.position() ) found.add(dep);
      }

      // search event dependencies for the mention
      for( TypedDependency dep : found ) {
        int depIndex = dep.dep().index();
        if( mention.start() == depIndex || mention.end() == depIndex || (mention.start() < depIndex && mention.end() > depIndex ) )
          return new Pair<String,Integer>(dep.reln().toString(), depIndex);
      }
    }

    return null;
  }

  /**
   * Uses already extracted events and entities
   * @param alldeps Vector of dependencies, in sentence order already
   * @param parses A Vector of parsed sentences
   * @param entities A list of entities that have been resolved to each other
   * @param eventVec A Vector of WordEvents seen in the entire document
   */
  private void analyzeDepParses( Vector<String> parses,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      List<WordEvent> eventVec,
      int storyID ) {
    Vector<WordEvent> events = new Vector();
    Vector<EntityMention> mentions[] = new Vector[parses.size()];
    Vector<WordEvent> sidEvents[] = new Vector[parses.size()];
    TreeFactory tf = new LabeledScoredTreeFactory();

    if( parses.size() != alldeps.size() ) {
      System.out.println("Deps size not parse size! " + parses.size() +
          " " + alldeps.size());
      System.exit(1);
    }

    // Create sentence based arrays
    for( int i = 0; i < parses.size(); i++ ) {
      mentions[i] = new Vector();
      sidEvents[i] = new Vector();
    }

    // Create sentence indexed events
    Set<String> seen = new HashSet();
    int sid = 0;
    for( List<TypedDependency> sentence : alldeps ) {
      try {
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parses.elementAt(sid))), tf);
        Tree parseTree = ptr.readTree();

        for( TypedDependency dep : sentence ) {
          //	  System.out.println("- " + dep);
          if( acceptableDependencyAll(dep.reln().toString()) &&
              !seen.contains(dep.gov().label().value().toString()) ) {
            seen.add(dep.gov().label().value().toString());
            int position = dep.gov().index();
            Tree subtree = TreeOperator.indexToSubtree(parseTree, position);
            String token = dep.gov().label().value().toString();
            String posTag = subtree.label().value();
            // If the word has a POS tag that is a Noun, Verb or Adjective.
            if( WordEvent.posTagType(posTag) != -1 ) {
              if( _lemmasOnly )
                token = lemmatizeTaggedWord(token, posTag);
              WordEvent event = new WordEvent(token, position, sid, posTag);
              //	      System.out.println(" - new event: " + event);
              sidEvents[sid].add(event);
            }
          }
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
      sid++;
    }

    // Create sentence indexed entity mentions
    for( EntityMention mention : entities )
      mentions[mention.sentenceID()-1].add(mention);

    // Go through each sentence
    for( sid = 0; sid < parses.size(); sid++ ) {
      //      System.out.println("**sid=" + sid + "**");

      for( WordEvent event : sidEvents[sid] ) {
        //	System.out.println(" event = " + event);
        // sometimes underscores are labelled as main verbs...
        if( event.token().length() > 1 &&
            event.token().matches("[a-zA-Z].*") ) {//charAt(0)equals("_") ) {
          List<TypedDependency> deps = alldeps.get(sid);

          // Add the particle
          String particle = findParticle(deps, event);
          //	  if( particle != null ) event.setParticle(particle);
          if( particle != null ) event.setToken(event.token() + "_" + particle.toLowerCase());

          // Add entity arguments to the verb event
          boolean addedArgument = false;
          for( EntityMention mention : mentions[sid] ) {
            //	    System.out.println("  check mention " + mention);
            if( !mention.string().equals("_") ) { 
              // Find the syntactic relation to the verb
              String relation = depVerbRelation(deps, event, mention);
              if( relation != null ) {
                //		if( acceptableDependency(relation, event.position(), deps) ) {
                if( acceptableDependencyAll(relation) ) {
                  //		  System.out.println("  ...adding reln " + relation);
                  event.addArg(relation, mention.entityID());
                  addedArgument = true;
                }
              }
            }
          }
          if( addedArgument ) 
            events.add(event);
        }
      }
    }

    // do the pair counts
    countEventPairs(events, storyID);
  }


  /**
   * Uses already extracted events and entities
   * @param alldeps Vector of dependencies, in sentence order already
   * @param parses A Vector of parsed sentences
   * @param entities A list of entities that have been resolved to each other
   * @param eventVec A Vector of WordEvents seen in the entire document
   */
  public static List<WordEvent> extractEvents( Vector<String> parses,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      boolean lemmasOnly,
      WordNet wordnet,
      int storyID ) {
    Vector<WordEvent> events = new Vector();
    Vector<EntityMention> mentions[] = new Vector[parses.size()];
    Vector<WordEvent> sidEvents[] = new Vector[parses.size()];
    TreeFactory tf = new LabeledScoredTreeFactory();

    if( parses.size() != alldeps.size() ) {
      System.out.println("Deps size not parse size! " + parses.size() +
          " " + alldeps.size());
      System.exit(1);
    }

    // Create sentence based arrays
    for( int i = 0; i < parses.size(); i++ ) {
      mentions[i] = new Vector();
      sidEvents[i] = new Vector();
    }

    // Create sentence indexed events
    Set<String> seen = new HashSet();
    int sid = 0;
    for( List<TypedDependency> sentence : alldeps ) {
      try {
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parses.elementAt(sid))), tf);
        Tree parseTree = ptr.readTree();

        for( TypedDependency dep : sentence ) {
          //	  System.out.println("- " + dep);
          if( acceptableDependencyAll(dep.reln().toString()) &&
              !seen.contains(dep.gov().label().value().toString()) ) {
            seen.add(dep.gov().label().value().toString());
            int position = dep.gov().index();
            Tree subtree = TreeOperator.indexToSubtree(parseTree, position);
            String token = dep.gov().label().value().toString();
            String posTag = subtree.label().value();
            // If the word has a POS tag that is a Noun, Verb or Adjective.
            if( WordEvent.posTagType(posTag) != -1 ) {
              //	      System.out.print(token + "/" + posTag + "->");
              if( lemmasOnly )
                token = wordnet.lemmatizeTaggedWord(token, posTag);
              //	      System.out.println(token);
              WordEvent event = new WordEvent(token, position, sid, posTag);
              //	      System.out.println(" - new event: " + event);
              sidEvents[sid].add(event);
            }
          }
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
      sid++;
    }

    // Create sentence indexed entity mentions
    for( EntityMention mention : entities )
      mentions[mention.sentenceID()-1].add(mention);

    // Go through each sentence
    for( sid = 0; sid < parses.size(); sid++ ) {
      //      System.out.println("**sid=" + sid + "**");

      for( WordEvent event : sidEvents[sid] ) {
        //	System.out.println(" event = " + event);
        // sometimes underscores are labelled as main verbs...
        if( event.token().length() > 1 &&
            event.token().matches("[a-zA-Z].*") ) {//charAt(0)equals("_") ) {
          List<TypedDependency> deps = alldeps.get(sid);

          // Add the particle
          String particle = findParticle(deps, event);
          //	  if( particle != null ) event.setParticle(particle);
          if( particle != null ) event.setToken(event.token() + "_" + particle.toLowerCase());

          // Add entity arguments to the verb event
          boolean addedArgument = false;
          for( EntityMention mention : mentions[sid] ) {
            //	    System.out.println("  check mention " + mention);
            if( !mention.string().equals("_") ) { 
              // Find the syntactic relation to the verb
              String relation = depVerbRelation(deps, event, mention);
              if( relation != null ) {
                //		if( acceptableDependency(relation, event.position(), deps) ) {
                if( acceptableDependencyAll(relation) ) {
                  //		  System.out.println("  ...adding reln " + relation);
                  event.addArg(relation, mention.entityID());
                  addedArgument = true;
                }
              }
            }
          }
          if( addedArgument ) 
            events.add(event);
        }
      }
    }

    return events;

    // do the pair counts
    //    countEventPairs(events, storyID);
  }


  /**
   * @returns True if the event is the head verb, defined by having a dependency which is
   * a subject, agent or object.
   */
  public static boolean acceptableEvent(WordEvent event, Collection<TypedDependency> deps) {
    for( TypedDependency dep : deps ) {
      if( dep.gov().index() == event.position() ) {
        String reln = dep.reln().toString();
        if( reln.startsWith("nsub") || reln.equals("xsubj") || 
            reln.endsWith("obj") || reln.equals("agent") ) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @returns True if the dependency is a content bearing dependency that could be a
   *          noun coreferring object.
   */
  public static boolean acceptableDependencyAll(String dep) {
    if( !dep.equals("det")          // determiners
        && !dep.startsWith("quant") // quantity modifiers
        && !dep.equals("tmod")      // time modifiers
        && !dep.equals("num")       // numbers
        && !dep.startsWith("conj")  // conjunctions
        && !dep.equals("rel")       // relative clauses
        && !dep.equals("rcmod")     // not sure, but takes predicates as arguments
        && !dep.equals("ccomp")     // complement phrases (verbs)
        && !dep.equals("cop")       // copulars (be verbs)
        && !dep.equals("aux")       // auxillaries for verbs
    )
      return true;
    else return false;
  }

  /**
   * @returns True if the dependency is one of: nsubj, nsubjpass, xsubj, Xobj, agent, prep_X
   */
  public static boolean acceptableDependency(String dep) {
    // matches: nsubj, nsubjpass, xsubj, Xobj, agent, prep_X
    if( dep.startsWith("nsub") || dep.equals("xsubj") ||
        dep.endsWith("obj") || dep.equals("agent") || dep.startsWith("prep") )
      return true;
    return false;
  }

  /**
   * @returns True if the dependency is one of: nsubj, nsubjpass, xsubj, Xobj, agent, prep_X
   */
  public static boolean acceptableDependencyCopular(String dep, int verbID, 
      Collection<TypedDependency> deps) {
    // If a subject, check if it is a copular subject (ignore those).
    if( dep.equals("nsubj") ) {
      if( isCopular(verbID, deps) ) {
        return false;
      }
      else return true;
    }
    // All other relations.
    else if( dep.startsWith("nsub") || dep.equals("xsubj") || // nsubjpass
        dep.endsWith("obj") || dep.equals("agent") || dep.startsWith("prep") )
      return true;
    return false;
  }

  /**
   * @return True if the given word position appears with a copular dependency anywhere
   *         in the list.  
   *         "dave is a boy" is a copular.  The dependencies create a false subject
   *         relation like: (nsubj boy dave).  We can detect these by searching for 'boy'
   *         in a copular dependency: (cop boy is).
   */
  public static boolean isCopular(int verbPosition, Collection<TypedDependency> deps) {
    for( TypedDependency dep : deps ) {
      if( dep.gov().index() == verbPosition && dep.reln().toString().equals("cop") )
        return true;
    }
    return false;
  }

  /**
   * Uses already extracted events and entities
   * @param parses A Vector of parsed sentences
   * @param entities A list of entities that have been resolved to each other
   */
  private void analyzePreParses( Vector<String> parses, 
      List<EntityMention> entities,
      List<WordEvent> eventVec ) {

    TreeFactory tf = new LabeledScoredTreeFactory();
    Vector<WordEvent> events = new Vector();
    Vector<EntityMention> mentions[] = new Vector[parses.size()];
    Vector<WordEvent> sidEvents[] = new Vector[parses.size()];


    // Read in all the parse trees
    Tree trees[] = new Tree[parses.size()];
    int i = 0;
    for( String parse : parses ) {
      try {
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), tf);
        trees[i] = ptr.readTree();
        mentions[i] = new Vector();
        sidEvents[i] = new Vector();
      } catch( Exception ex ) { ex.printStackTrace(); }
      i++;
    }

    // Create sentence indexed events and entity mentions
    for( WordEvent event : eventVec )
      sidEvents[event.sentenceID()-1].add(event);
    for( EntityMention mention : entities )
      mentions[mention.sentenceID()-1].add(mention);

    // Save the verbs in each parse
    int sentence = 0;
    for( Tree tree : trees ) {
      //      System.out.println("Raw: " + TreeOperator.toRaw(tree));

      // Look for the verbs in the tree
      if( tree != null ) {

        for( WordEvent event : sidEvents[sentence] ) {
          Tree subtree = eventSubTree(tree,event);

          // Add entity arguments to the verb event
          for( EntityMention mention : mentions[sentence] ) {
            // Find the syntactic relation to the verb
            String relation = mentionRelationToVerb(tree, subtree, mention);
            if( relation != null ) {
              event.addArg(relation, mention.entityID());
            }
          }

          events.add(event);
        }
      }
      sentence++;
    }

    // do the pair counts
    countEventPairs(events);
  }


  /**
   * Counts pairs of verbs from parse trees
   * @param parses A Vector of parsed sentences
   * @param entities A list of entities that have been resolved to each other
   */
  private void analyzeParses( Vector<String> parses, Collection<EntityMention> entities ) {

    if( entities != null ) {
      Vector<String> verbs = new Vector();
      Vector<WordEvent> events = new Vector();
      TreeFactory tf = new LabeledScoredTreeFactory();

      // Read in all the parse trees
      Tree trees[] = new Tree[parses.size()];
      Vector<EntityMention> mentions[] = new Vector[parses.size()];
      int i = 0;
      for( String parse : parses ) {
        //	System.out.println(parse);
        try {
          PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), tf);
          trees[i] = ptr.readTree();
          mentions[i] = new Vector();
          //	  System.out.println(i + ": " + trees[i]);
        } catch( Exception ex ) { ex.printStackTrace(); }
        i++;
      }

      // Convert all entity spans from character spans to word-based
      for( EntityMention mention : entities ) {
        //	System.out.println("----\n" + mention);
        mention.convertCharSpanToIndex(TreeOperator.toRaw(trees[mention.sentenceID()-1]));
        mentions[mention.sentenceID()-1].add(mention);
        //	System.out.println(mention);
      }


      // Save the verbs in each parse
      int sid = 0;
      for( Tree tree : trees ) {
        // Look for the verbs in the tree
        if( tree != null ) {
          //System.out.println(TreeOperator.toRaw(tree));
          Vector<Tree> parseVerbs = TreeOperator.verbTreesFromTree(tree);
          for( Tree verb : parseVerbs ) {
            //	    WordEvent event = new WordEvent(verb.firstChild().firstChild().value(),
            WordEvent event = new WordEvent(verb.firstChild().value(),
                TreeOperator.wordIndex(tree,verb)+1,sid+1);

            // Add entity arguments to the verb event
            for( EntityMention mention : mentions[sid] ) {
              // Find the syntactic relation to the verb
              String relation = mentionRelationToVerb(tree, verb, mention);
              if( relation != null ) {
                //		System.out.println("mention  = " + mention);
                //		System.out.println("relation = " + relation);
                event.addArg(relation, mention.entityID());
              }
            }

            events.add(event);
          }

        }
        sid++;
      }

      // do the pair counts
      //    countPairs(verbs);
      countEventPairs(events);
    }
  }


  /**
   * @desc Process parses for verbs
   * @param parses The list of parses in String form
   */
  private void analyzeParsesNoCoref( Vector<String> parses ) {
    TreeFactory tf = new LabeledScoredTreeFactory();
    Vector<String> verbs = new Vector();

    // Save the verbs in each parse
    int sentence = 0;
    for( String parse : parses ) {
      try {
        // Read the parse
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), tf);
        Tree ansTree = ptr.readTree();

        // Look for the verbs in the tree
        if( ansTree != null ) {

          Vector<Tree> parseVerbs = TreeOperator.verbTreesFromTree(ansTree);
          for( Tree verb : parseVerbs )
            //	    verbs.add(verb.firstChild().firstChild().value());
            verbs.add(verb.firstChild().value());

        }
      } catch( Exception ex ) { ex.printStackTrace(); }
      sentence++;
    }

    // do the pair counts
    countPairs(verbs);
  }


  /**
   * @desc Process chunks (sentences) of text for event information
   * @param IDFonly Set to true if you want to update IDF counts and not
   * track verb pairs.  False does not update IDFs and only tracks verb pairs.
   */
  private void analyzeSentences( Vector<String> paragraphs, boolean IDFonly ) {
    HashMap<String,Boolean> seen = new HashMap(100);
    HashMap<String,Boolean> seenLemmas = new HashMap(100);
    Vector<String> verbs = new Vector<String>();

    // Paragraphs may be multiple sentences
    for( String fragment : paragraphs ) {

      // Loop over each sentence
      for( List<HasWord> sentence : Ling.getSentencesFromText(fragment) ) {
//      while( dp.iterator().hasNext() ) {
//        List<HasWord> sentence = dp.iterator().next();

//        for( Word word : (List<Word>)sentence ) {
        for( HasWord word : sentence ) {

          String wordString = word.word().toLowerCase(); // lowercased!
          if( wordString.matches(".*\\d.*") ) wordString = "NUMERAL";

          if( isVerb(wordString) ) {

            if( !IDFonly ) verbs.add(wordString);

            if( IDFonly ) {
              // Count the documents the word appears in, for IDF score
              if( !seen.containsKey(wordString) ) {
                seen.put(wordString, true);
                // add the count of documents
                idf.increaseDocCount(wordString);
              }

              // Count the documents the lemma appears in, for IDF score
              String lemmaString = verbToLemma(wordString);
              if( lemmaString == null ) lemmaString = wordString;
              if( !seenLemmas.containsKey(lemmaString) ) {
                seenLemmas.put(lemmaString, true);
                // add the count of documents
                idfLemmas.increaseDocCount(lemmaString);
              }
            }
          }
        }
      }
    }

    // do the pair counts
    if( !IDFonly ) countPairs(verbs);
  }


  /**
   * Counts all *verbs* in the parse trees and tracks IDF counts.
   */
  private void calculateIDFofVerbs( Vector<String> parses ) {
    TreeFactory tf = new LabeledScoredTreeFactory();
    HashMap<String,Boolean> seen = new HashMap(100);
    HashMap<String,Boolean> seenLemmas = new HashMap(100);

    // Loop over each parse tree
    for( String parse : parses ) {
      try {
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), tf);
        Tree ansTree = ptr.readTree();

        if( ansTree != null ) {

          // Look for the verbs in the tree
          Vector<Tree> parseVerbs = TreeOperator.verbTreesFromTree(ansTree);
          for( Tree verb : parseVerbs ) {
            String wordString = verb.firstChild().value().toLowerCase();

            // Look for a particle
            String particle = findParticleInTree(verb.parent(ansTree));
            if( particle != null ) wordString += "_" + particle.toLowerCase();

            // Don't count numerals or weird starting punctuation tokens
            if( wordString.matches("[a-zA-Z].*") && !wordString.matches(".*\\d.*") )  {
              // Count the documents the word appears in, for IDF score
              if( !seen.containsKey(wordString) ) {
                seen.put(wordString, true);
                // add the count of documents
                idf.increaseDocCount(wordString);
              }

              // Count the documents the lemma appears in, for IDF score
              String lemmaString = verbToLemma(wordString);
              if( lemmaString == null ) lemmaString = wordString;
              if( !seenLemmas.containsKey(lemmaString) ) {
                seenLemmas.put(lemmaString, true);
                // add the count of documents
                idfLemmas.increaseDocCount(lemmaString);
              }

              // Increment word frequency
              idf.increaseTermFrequency(wordString);
              idfLemmas.increaseTermFrequency(lemmaString);
            }
          }
        }

      } catch( Exception ex ) { ex.printStackTrace(); }
    }
  }


  private boolean shares(Collection one, Collection two) {
    if( one == null || two == null ) return false;
    for( Object obj : one )
      for( Object obj2 : two )
        if( obj == obj2 ) return true;
    return false;
  }

  /**
   * @desc Count all before/after pairs of verbs that share an NP argument
   * @param verbs A Vector of WordEvents in order they appear in a document
   * @param distance Max sentence distance to consider for pairs
   * @param fid File ID of the story we are counting events in
   */
  private void countEventPairs(Vector<WordEvent> verbs, int distance, int fid) {
    String w1, w2, w3, combo, shared = null;
    WordEvent e1, e2, e3;
    WordEvent list[] = new WordEvent[verbs.size()];
    list = verbs.toArray(list);
    HashSet seen = new HashSet(list.length * 2);

    // This array tracks all words that pair with each word.
    // It is meant to avoid two instances of the same verb pairing with 
    // a different verb later in the document.
    Set<String>[] matched = new HashSet[verbs.size()];
    for( int i = 0; i < matched.length; i++ ) matched[i] = new HashSet();

    // NORMAL PAIR COUNTING
    //    System.out.println("countEventPairs()!!");
    for( int i = 0; i < list.length-1; i++ ) {
      e1 = (WordEvent)list[i];
      w1 = e1.token();
      //      if( e1.particle() != null ) w1 += "_" + e1.particle();
      seen.clear();
      //      System.out.println(" " + i + " e1 = " + e1);

      if( idf.getDocCount(w1) > docCutoff && idf.get(w1) > IDFcutoff ) {

        for( int j = i+1; j < list.length && j < i+1+distance; j++ ) {
          e2 = (WordEvent)list[j];
          w2 = e2.token();
          //	  if( e2.particle() != null ) w2 += "_" + e2.particle();
          //	  System.out.println("  " + j + " e2= " + e2);

          if( w1.equals(w2) ) continue; // skip if the same verb

          if( idf.getDocCount(w2) > docCutoff && idf.get(w2) > IDFcutoff ) {

            // sharing argument types
            if( !countAll ) shared = e1.sharedArgument(e2.arguments());
            else shared = "S:S"; // meaningless pattern when counting ALL pairs

            // If w1 hasn't already paired with a similar w2.
            if( shared != null && !seen.contains(w2) ) {
              // If this word (w2) hasn't already paired with a word like w1.
              if( !matched[j].contains(w1) ) {
                matched[j].add(w1);

                //		String full = sortBigram(w1,w2,shared);
                String full = sortBigram(WordEvent.stringify(w1, e1.posTag()), 
                    WordEvent.stringify(w2, e2.posTag()), shared);

                if( !pairCounts.containsKey(full) ) pairCounts.put(full, 1);
                else pairCounts.put(full, pairCounts.get(full)+1);
                seen.add(w2);

                //		System.out.println("**Adding pair " + full);
                //	      if( trackable(full) ) System.out.println("**" + full);

                // Create the TLink
                if( !countAll ) tlinks.add(fid + " " + e1.eventID() + " " + e2.eventID() + 
                    " " + w1 + " " + w2);

                // trigrams
                if( countTrigrams ) {
                  for( int k = j+1; k < list.length && k < j+1+distance; k++ ) {
                    e3 = (WordEvent)list[k];
                    w3 = e3.token();
                    //		  if( e3.particle() != null ) w3 += "_" + e3.particle();

                    if( w1.equals(w3) || w2.equals(w3) ) break; // break if same verbs

                    if( idf.getDocCount(w3) > docCutoff && idf.get(w3) > IDFcutoff ) {

                      // sharing argument types
                      String trishared = e2.sharedArgument(e3.arguments());
                      if( trishared != null && 
                          shared.substring(shared.indexOf(':')+1).equals(trishared.substring(0,trishared.indexOf(':'))) ) {
                        String trifull = sortTrigram(w1,w2,w3,shared,trishared);
                        //		    String trifull = tri + ";" + shared + ":" + trishared.substring(trishared.indexOf(':')+1);
                        //		    System.out.println("* " + trifull);

                        if( !trigramCounts.containsKey(trifull) ) trigramCounts.put(trifull, 1);
                        else trigramCounts.put(trifull, trigramCounts.get(trifull)+1);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private void countEventPairs(Vector<WordEvent> verbs) {
    countEventPairs(verbs, pairDistance, 0);
  }

  private void countEventPairs(Vector<WordEvent> verbs, int fid) {
    countEventPairs(verbs, pairDistance, fid);
  }

  /**
   * Returns a string form of the words and grammatical categories
   * sorted alphabetically.
   * @param shared Grammatical sharing of w1 and w2 "subj:obj"
   */
  public static String sortBigram(String w1,String w2,String shared) {
    // 1 2
    if( w1.compareTo(w2) <= 0 ) return w1 + ":" + w2 + ";" + shared;
    // 2 1
    else {
      int mid = shared.indexOf(':');
      return w2 + ":" + w1 + ";" + shared.substring(mid+1) + ":" + shared.substring(0,mid);
    }
  }

  /**
   * Returns a string form of the words and grammatical categories
   * sorted alphabetically.
   * @param shared Grammatical sharing of w1 and w2 "subj:obj"
   * @param shared Grammatical sharing of w2 and w3 "obj:prep"
   */
  public static String sortTrigram(String w1,String w2,String w3,String shared,String shared2) {
    int mid1 = shared.indexOf(':');
    int mid2 = shared2.indexOf(':');

    // 1 2 3
    if( w1.compareTo(w2) <= 0 && w2.compareTo(w3) <= 0 )
      return w1 + ":" + w2 + ":" + w3 + ";" + shared + ":" + shared2.substring(mid2+1);
    // 1 3 2
    else if( w1.compareTo(w3) <= 0 && w3.compareTo(w2) <= 0 ) 
      return w1 + ":" + w3 + ":" + w2 + ";" + shared.substring(0,mid1) + ":" + shared2.substring(mid2+1) + ":" + shared2.substring(0,mid2);
    // 2 1 3
    else if( w2.compareTo(w1) <= 0 && w1.compareTo(w3) <= 0 )
      return w2 + ":" + w1 + ":" + w3 + ";" + shared.substring(mid1+1) + ":" + shared.substring(0,mid1) + ":" + shared2.substring(mid2+1);
    // 2 3 1
    else if( w2.compareTo(w3) <= 0 && w3.compareTo(w1) <= 0 )
      return w2 + ":" + w3 + ":" + w1 + ";" + shared.substring(mid1+1) + ":" + shared2.substring(mid2+1) + ":" + shared.substring(0,mid1);
    // 3 1 2
    else if( w3.compareTo(w1) <= 0 && w1.compareTo(w2) <= 0 )
      return w3 + ":" + w1 + ":" + w2 + ";" + shared2.substring(mid2+1) + ":" + shared.substring(0,mid1) + ":" + shared.substring(mid1+1);
    // 3 2 1
    else return w3 + ":" + w2 + ":" + w1 + ";" + shared2.substring(mid2+1) + ":" + shared.substring(mid1+1) + ":" + shared.substring(0,mid1);
  }


  private boolean trackable(String str) {
    for( String pattern : tracking )
      if( str.equals(pattern) ) return true;
    return false;
  }

  /**
   * @desc Count all before/after pairs of verbs
   * @param verbs A Vector of verbs in order they appear in a document
   * @param distance Max sentence distance to consider for pairs
   */
  private void countPairs(Vector<String> verbs, int distance) {
    String w1, w2, combo;
    Object list[] = verbs.toArray();
    Set<String> local = new HashSet();

    for( int i = 0; i < list.length-1; i++ ) {
      w1 = (String)list[i];

      if( idf.getDocCount(w1) > docCutoff && idf.get(w1) > IDFcutoff ) {

        for( int j = i+1; j < list.length && j < i+1+distance; j++ ) {
          w2 = (String)list[j];

          if( w1.equals(w2) ) break; // break if the same verb

          if( idf.getDocCount(w2) > docCutoff && idf.get(w2) > IDFcutoff ) {
            combo = w1 + ":" + w2;
            // don't add the pair twice from one document
            if( !local.contains(combo) ) { 
              if( !pairCounts.containsKey(combo) ) pairCounts.put(combo, 1);
              else pairCounts.put(combo, pairCounts.get(combo)+1);
              local.add(combo);
            }
          }
        }
      }
    }
  }

  private void countPairs(Vector<String> verbs) {
    countPairs(verbs, pairDistance);
  }

  public void printPairs() {
    printPairs(0);
  }

  public void printPairs(int cutoff) {
    Set<String> keys = pairCounts.keySet();
    TreeSet<String> treeset = new TreeSet(keys);
    for( String pair : treeset ) {
      Integer count = pairCounts.get(pair);
      if( count > cutoff ) System.out.println(pair + " " + count);
    }
  }

  public void pairsToFile(String filename, int cutoff) {
    Set<String> keys = pairCounts.keySet();
    TreeSet<String> treeset = new TreeSet(keys);
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      for( String pair : treeset ) {
        Integer count = pairCounts.get(pair);
        if( count > cutoff ) out.write(pair + " " + count + "\n");
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  public void trigramsToFile(String filename, int cutoff) {
    if( trigramCounts != null ) {
      Set<String> keys = trigramCounts.keySet();
      TreeSet<String> treeset = new TreeSet(keys);
      try {
        BufferedWriter out = new BufferedWriter(new FileWriter(filename));
        for( String pair : treeset ) {
          Integer count = trigramCounts.get(pair);
          if( count > cutoff ) out.write(pair + " " + count + "\n");
        }
        out.close();
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
  }

  public void tlinksToFile(HashSet<String> tlinks, String filename) {
    System.out.println("Tlinks: " + filename);
    //    Set<String> keys = tlinks.keySet();
    TreeSet<String> treeset = new TreeSet(tlinks);
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      for( String pair : treeset ) {
        out.write(pair + "\n");
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * Trim away pairs appearing too sparse, to save memory
   */
  private void trimPairs(Map<String,Integer> map, int cutoff) {
    Set<String> keys = map.keySet();
    Vector<String> removed = new Vector();

    for( String pair : keys ) {
      Integer count = map.get(pair);
      // Remove the pair if it is sparse
      if( count <= cutoff ) removed.add(pair);
    }

    for( String pair : removed ) map.remove(pair);

    System.out.println("Trimmed " + removed.size() + " pairs");
  }

  private String lemmatizeTaggedWord(String token, String postag) {
    String lemma = null;

    if( postag.startsWith("VB") )
      lemma = verbToLemma(token);
    else if( postag.startsWith("N") )
      lemma = nounToLemma(token);
    else if( postag.startsWith("J") )
      lemma = adjectiveToLemma(token);
    //    else
    //      System.out.println("Unknown TAG: " + postag);

    if( lemma == null ) lemma = token;

    return lemma;
  }

  public String verbToLemma(String verb) {
    return wordToLemma(verb, POS.VERB, verbToLemma);
  }

  public String nounToLemma(String word) {
    return wordToLemma(word, POS.NOUN, nounToLemma);
  }

  public String adjectiveToLemma(String word) {
    return wordToLemma(word, POS.ADJECTIVE, adjToLemma);
  }

  /**
   * @param word A word
   * @return The lemma of the word if it is a verb, null otherwise
   */
  public String wordToLemma(String word, POS type, Map<String,String> cache) {
    //    System.out.println("wordToLemma " + word + " - " + type);
    // save time with a table lookup
    if( cache.containsKey(word) ) return cache.get(word);

    try {
      // don't return lemmas for hyphenated words
      if( word.indexOf('-') > -1 || word.indexOf('/') > -1 ) {
        cache.put(word, null);
        return null;	
      }

      // get the lemma
      IndexWord iword = Dictionary.getInstance().lookupIndexWord(type, word);
      if( iword == null ) {
        cache.put(word, null);
        return null;
      }
      else {
        String lemma = iword.getLemma();
        if( lemma.indexOf(' ') != -1 ) // Sometimes it returns a two word phrase
          lemma = lemma.trim().replace(' ','_');

        cache.put(word, lemma);
        return lemma;
      }
    } catch( Exception ex ) { ex.printStackTrace(); }

    return null;
  }


  /**
   * @param word A Word
   * @return True if the word can be a verb according to WordNet
   */
  private boolean isVerb(String word) {
    // save time with a table lookup
    if( wordToVerb.containsKey(word) )
      return wordToVerb.get(word);

    try {
      IndexWord iword = Dictionary.getInstance().lookupIndexWord(POS.VERB, word);

      if( iword == null ) {
        wordToVerb.put(word, false);
        return false;
      }
      else {
        wordToVerb.put(word, true);
        return true;
      }
    } catch( Exception ex ) { ex.printStackTrace(); }

    return false;
  }


  /**
   * @param word A Word
   * @return True if the word can be a verb according to WordNet
   */
  private boolean isVerbPOS(String tag) {
    if( tag.startsWith("VB") ) return true;
    else return false;
  }

  /**
   * @param dir Directory to search
   * @param starts String that the filename must start with
   * @param contains String that the filename must contain
   * @param except String that the filename must *not* contain
   **/
  public static String findFile(String dir, String starts, String contains, String except) {
    //    System.out.println(dir + " - " + starts + " - " + contains);

    File fdir = new File(dir);
    if( fdir.isDirectory() ) {
      String files[] = fdir.list();

      // Return the first file name to match	
      for( String file : files ) {
        //	System.out.println("Checking " + file);
        if( file.startsWith(starts) && file.indexOf(contains) > -1 &&
            (except == null || file.indexOf(except) == -1) ) {
          //	  System.out.println("Found File: " + file);
          return file;
        }
      }
    } 

    System.out.println("No Found File in " + dir + ": " + contains);    
    return null;    
  }
  public static String findFile(String dir, String starts, String contains) {
    return findFile(dir,starts,contains,null);
  }

  /**
   * @desc Uses the global dataPath directory and reads every .gz file
   * in it, processing each sentence in each document in each file.
   */
  public void processData() {
    startTime = System.currentTimeMillis();
    NUMDOCS = 0;

    if( _dataPath.length() > 0 ) {

      File dir = new File(_dataPath);
      if( dir.isDirectory() ) {
        int fid = 0;
        String files[] = dir.list();
        Arrays.sort(files);

        for( String file : files ) {
          // Only look at *.gz files
          if( !file.startsWith(".") && file.contains("parse") &&
              (file.endsWith(".gz") || file.endsWith(".parse")) ) {
            String year = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(8,12) : "noyear";
            String month = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(12,14) : "nomonth";
            System.out.println("file: " + file);
            tlinks.clear();

            GigawordHandler giga;
            if( _filterList != null ) 
              giga = new GigawordFiltered(_dataPath + File.separator + file, _filterList);
            else if( parsed ) 
              giga = new GigawordProcessed(_dataPath + File.separator + file);
            else 
              giga = new GigawordHandler(_dataPath + File.separator + file);

            GigaDocReader gigaEvents = null;
            GigaDocReader gigaDeps = null;
            if( !calculateIDF ) {
              String ePath = _eventPath + File.separator + file.substring(0,file.indexOf(".parse")) + ".events.gz";
              String dPath = _depsPath + File.separator + file.substring(0,file.indexOf(".parse")) + ".deps.gz";
              gigaEvents = new GigaDocReader(ePath);
              gigaDeps =   new GigaDocReader(dPath);
            }

            // Load the next story
            Vector<String> sentences = giga.nextStory();

            int storyID = giga.currentStoryNum();
            // Read the documents in this file 
            while( sentences != null ) {
              if( _duplicates.contains(giga.currentStory()) ) {
                System.out.println("Duplicate " + giga.currentStory());
              } 
              else if( _ignoreList.contains(giga.currentStory()) ) {
                System.out.println("Ignore List " + giga.currentStory());
              } else {
                NUMDOCS++;
                if( gigaEvents != null ) gigaEvents.nextStory(giga.currentStory());
                if( gigaDeps != null ) gigaDeps.nextStory(giga.currentStory());

                System.out.print(NUMDOCS + ": (" + giga.currentDoc() + ")");
                //		System.out.print("(" + wordToVerb.size() + " words) ");
                System.out.print("(" + pairCounts.size() + " vpairs) ");
                //		System.out.print("(" + trigramCounts.size() + " trigrams) ");
                System.out.println(giga.currentStory());

                if( calculateIDF ) {
                  idf.increaseDocCount();
                  idfLemmas.increaseDocCount();
                  //		  analyzeSentences( sentences, true );
                  calculateIDFofVerbs( sentences );
                }
                else if( parsed && nocoref ) 
                  analyzeParsesNoCoref( sentences );
                else if( parsed && gigaEvents != null && gigaDeps != null ) {
                  analyzeDepParses( sentences, gigaDeps.getDependencies(), gigaEvents.getEntities(), gigaEvents.getEvents(), storyID );
                }
                else if( parsed && gigaEvents != null ) {
                  if( !gigaEvents.currentStory().equals(giga.currentStory()) ) {
                    System.err.println("STORIES DON'T MATCH!");
                    System.exit(1);
                  }
                  analyzePreParses( sentences, gigaEvents.getEntities(), gigaEvents.getEvents() );
                }
                else if( parsed ) {
                  Collection<EntityMention> entities = null;
                  if( coref != null ) entities = coref.processParses( sentences );
                  // Analyze the parses with entities
                  analyzeParses( sentences, entities );
                }
                //		else analyzeSentences( sentences, false );
              }

              sentences = giga.nextStory();
              storyID = giga.currentStoryNum();

              if( NUMDOCS % 100 == 0 ) {
                Util.reportMemory();
                reportElapsedTime(startTime);
              }
            } // while sentences

            System.out.println("**Finished file**");

            // Calculate IDF scores
            if( calculateIDF ) {
              System.out.println("------SAVING IDF PRECOUNT------");
              idf.calculateIDF();
              idf.saveToFile("verbstemp.idf" + "-" + year);
              idfLemmas.calculateIDF();
              idfLemmas.saveToFile("verbs-lemmastemp.idf" + "-" + year);
            }
            // Trim and Save the current list of pairs
            else {
              // trim every other file
              if( (countAll && fid % 2 == 1) ) {
                System.out.println("------TRIMMING PAIRS------");
                trimPairs(pairCounts, pairsCutoff);
                //		trimPairs(trigramCounts,1);
              }
              System.out.println("Year " + year + " month " + month);
              // there is no 199912 in the AP portion
              if( month.equals("12") || (year.equals("1999") && month.equals("11")) ) {
                System.out.println("------SAVING PAIRS " + year + "------");
                pairsToFile(pairsOutput + "-" + year, pairsCutoff);
                //	trigramsToFile(trigramsOutput + "-" + year,trigramCutoff);
              }
              if( !countAll ) // don't make these if we're doing all possible pairs
                tlinksToFile(tlinks, tlinkDir + File.separator + file + ".tlinks");
            }
            fid++;
            //	    if( fid == 4 ) return;
          }
        }
      }
    }
    else {
      System.out.println("No data path given!");
      System.exit(1);
    }
  }


  /**
   * @desc Interprets _dataPath as a single file of concatenated documents.
   *       It assumes the parses and events files are also single files of the
   *       same documents.  This is used instead of processData() that is the
   *       normal function for processing directories of document files.
   */
  public void processSingleFiles() {
    startTime = System.currentTimeMillis();
    NUMDOCS = 0;

    if( _dataPath.length() > 0 ) {

      File dir = new File(_dataPath);
      if( !dir.isDirectory() ) {
        int fid = 0;

        System.out.println("file: " + _dataPath);
        tlinks.clear();

        GigawordHandler giga;
        if( _filterList != null ) 
          giga = new GigawordFiltered(_dataPath, _filterList);
        else if( parsed ) 
          giga = new GigawordProcessed(_dataPath);
        else 
          giga = new GigawordHandler(_dataPath);

        GigaDocReader gigaEvents = null;
        GigaDocReader gigaDeps = null;
        if( !calculateIDF ) {
          System.out.println("Loading event and deps readers...");
          String ePath = _eventPath;
          String dPath = _depsPath;
          gigaEvents = new GigaDocReader(ePath);
          gigaDeps =   new GigaDocReader(dPath);
        }

        // Load the next story
        System.out.println("Getting first story...");
        Vector<String> sentences = giga.nextStory();
        int storyID = giga.currentStoryNum();

        System.out.println(giga.currentStory() + " id=" + storyID);

        // Read the documents in this file 
        while( sentences != null ) {
          if( _duplicates.contains(giga.currentStory()) ) {
            System.out.println("Duplicate " + giga.currentStory());
          } 
          else if( _ignoreList.contains(giga.currentStory()) ) {
            System.out.println("Ignore List " + giga.currentStory());
          } else {
            NUMDOCS++;
            if( gigaEvents != null ) gigaEvents.nextStory(giga.currentStory());
            if( gigaDeps != null ) gigaDeps.nextStory(giga.currentStory());

            //	    System.out.print(NUMDOCS + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") ")
            System.out.print(NUMDOCS + ": (" + giga.currentDoc() + "/??) ");
            System.out.print("(" + pairCounts.size() + " vpairs) ");
            System.out.println(giga.currentStory());

            // Normal processing, count event pairs.
            if( !calculateIDF && parsed && gigaEvents != null && gigaDeps != null ) {
              analyzeDepParses( sentences, gigaDeps.getDependencies(), gigaEvents.getEntities(), gigaEvents.getEvents(), storyID );
            }
            // Calculate IDF scores.
            else if( calculateIDF ) {
              idf.increaseDocCount();
              idfLemmas.increaseDocCount();
              calculateIDFofVerbs( sentences );
            }

          } // while sentences

          sentences = giga.nextStory();
          storyID = giga.currentStoryNum();

          if( NUMDOCS % 100 == 0 ) {
            Util.reportMemory();
            reportElapsedTime(startTime);
          }

          fid++;
        }

        // Calculate IDF scores
        if( calculateIDF ) {
          System.out.println("------SAVING IDFS------");
          idf.calculateIDF();
          idf.saveToFile("verbstemp.idf-single");
          idfLemmas.calculateIDF();
          idfLemmas.saveToFile("verbs-lemmastemp.idf-single");
        }
      }
    } else {
      System.out.println("No data path given!");
      System.exit(1);
    }
  }

  public void process() {
    if( _dataSingleFile )
      processSingleFiles();
    else
      processData();
  }

  
  public static void reportElapsedTime(long startTime) {
    String str = EventParser.timeString(System.currentTimeMillis()-startTime);
    System.out.println("............................ runtime " + str + " ..........................");
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      BasicEventAnalyzer analyzer = new BasicEventAnalyzer(args);
      analyzer.process();
      if( !analyzer.calculateIDF ) {
        analyzer.pairsToFile(BasicEventAnalyzer.pairsOutput, BasicEventAnalyzer.pairsCutoff);
        analyzer.trigramsToFile(BasicEventAnalyzer.trigramsOutput, BasicEventAnalyzer.trigramCutoff);
      }
    }
  }
}

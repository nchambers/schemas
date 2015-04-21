package nate.reading;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.BasicEventAnalyzer;
import nate.Coref;
import nate.CountTokenPairs;
import nate.CountVerbDepCorefs;
import nate.EntityMention;
import nate.IDFMap;
import nate.NERSpan;
import nate.Pair;
import nate.args.CountArgumentTypes;
import nate.cluster.ClusterUtil;
import nate.narrative.ScoreCache;
import nate.reading.ir.IRFrameCounts;
import nate.util.Dimensional;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.Locks;
import nate.util.SortableObject;
import nate.util.SortableScore;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Synset;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.StringUtils;


/**
 * Clusters the domain's tokens with agglomerative or LDA topics.
 * Then runs over the docs and labels each one with its top clusters/topics.
 * 
 * -outputkeys
 * If present, prints the tokens used in clustering for each document.
 * Debugging, or to send to LDA topic modeling.
 *
 * -type
 * Type of tokens to use in clustering.
 *
 * -clustersize
 * Size of maximum cluster, when to stop clustering.
 *
 * -domainidf
 * Domain IDF scores. POS tagged format.
 * -corpusidf
 * Corpus IDF scores. POS tagged format.
 * -initialidf
 * IDF scores of words in the first couple of sentences. POS format.
 *
 * -numdocclusters [int]
 * -numsentclusters [int]
 * The number of top n clusters to use in the evaluation.
 * 
 * -onlyconsistent
 * If present, this sets the top n sentences to only include clusters that match for two
 * consecutive sentences...it ignores the top sentence function.
 * 
 * -doclusters -dotopics
 * Sets the flag to run clusters and/or topics to true.
 * -noclusters -notopics
 * Sets the flag to run clusters and/or topics to false.
 * 
 * -dependents
 * If present, then we include dependent tokens as well as the default governors of the
 * typed dependencies in the clustering and topic matching algorithms. 
 * 
 * -noargs
 * Don't extract arguments from documents, just do document classification.
 * 
 * -docsim max|newlink|newlink-tight
 * -sentsim
 * Set the type of similarity metric that the clustering algorithm uses to compare a cluster
 * to a document or sentence.
 * 
 * -docmember
 * -sentmember
 * If present, this requires a cluster to contain at least one word that is in the doc or sentence.
 * 
 * -interpargs
 * If present, then the pairwise PMI scores are merged with arg similarities...used in clustering.
 * 
 */
public class LabelDocument {
  ProcessedData _trainDataReader;
  ProcessedData _test1234DataReader;
  ProcessedData _test12DataReader;
  ProcessedData _test34DataReader;
  String _topicsModelDir;
  DomainVerbDetector _detector;
  public static MUCKeyReader _trainAnswerKey;
  public static MUCKeyReader _test1234AnswerKey;
  public static MUCKeyReader _test12AnswerKey;
  public static MUCKeyReader _test34AnswerKey;
  int _tokenType = CountTokenPairs.VERBS_AND_NOMINALS;
//  int _maxClusterSize = 40; // great for vbnom
  int _maxClusterSize = 60; // used when verb-objects are included?
  int _maxTopDocClusters = 6; // number of best topics/clusters to choose for an entire document.
  int _maxTopSentenceClusters = 2; // number of best topics/clusters to choose by single sentences.
  String _currentStory = null;
  boolean _includeDependents = false;  // true if both govs and deps are used from TypedDependencies.
  HandleParameters _params;
//  PantelClusters _thesaurus;
  SlotInducer _slotInducer = null;
  NamedEntityWords _newords = null;
  //String _cachePath = "/home/nchamber/scr/cache/labeldocument";
  //String _tempOutputDir = "/home/nchamber/scr/cache/frames";
  String _cachePath = "./cache/labeldocument";
  String _tempOutputDir = "./cache/frames";
  double _centralTokenCutoff = 0.25;
  double _docProbCutoff = -4.35;

  boolean _doClusters = true;
  boolean _doTopics = false;
  boolean _doArgs = true; // if false, just does document classification and not argument lookup.
  boolean _docRequireMembership = false;
  boolean _sentenceRequireMembership = true;
  boolean _includeCollocationObjects = true;
  // If true, tests on the test set, not the train set.
  TestType _evaluateOnTest = TestType.train;
  // If true, clears the frames in cache and relearns their slots.  
  boolean _reinduceSlots = false;
  // If true, the process sits and waits for the frames to learn their slots in other processes.
  // It polls the filesystem until all are created, then it runs over the MUC data.
  boolean _waitForSlots = false;
  
  public static boolean INTERP_ARGS_WITH_PAIRS = false;
  public static boolean REMOVE_REPORTING_WORDS = true;
  public static String CLUSTER_DOC_SIM  = "newlink";
  public static String CLUSTER_SENT_SIM = "newlink";
  public static boolean CONSISTENT_SENTENCE_ONLY = true;
  
  // Global data objects.
  Map<String,Object> _globalCache = new HashMap<String, Object>();
  WordNet _wordnet;
  IDFMap _domainIDF;
  IDFMap _generalIDF;
  IDFMap _initialIDF;
  IDFMap _kidnapIRIDF;
  IDFMap _bombingIRIDF;
  CountTokenPairs _domainTokenPairCounts;
  CountVerbDepCorefs _relnCountsDomain;
  CountTokenPairs _kidnapIRPairCounts;
  CountTokenPairs _bombingIRPairCounts;
  List<Template> _currentTemplates;
  Map<String, List<Integer>> _totalArgScores;
  Map<String,Integer> _falsePositiveArgs;
  Map<String,Integer> _falseNegativeArgs;
  TopicsMUC _topicsMUC = null;
  // Map from document ID to a map of template types (e.g. bomb, kidnap).
  // The submap is from template types to slot guesses.
  // The second submap is from MUC slot indices to a list of entities we guessed for them.
  Map<String,Map<String,Map<Integer,List<String>>>> _docRoleGuesses;
  Map<String,List<Frame>> _docFrameGuesses;
  Map<String,List<ScoredFrame[]>> _docSentenceGuesses;
  Map<String,List<ScoredFrame>> _docBlendedGuesses;
  Map<String,List<ScoredFrame>> _docConsistentGuesses;
  Map<String,List<ScoredFrame>> _docSimVecGuesses;
  Map<String,List<ScoredFrame>> _docOnlyGuesses;
  Map<String,List<ScoredFrame>> _docCentralGuesses;

  // Frame ID -> IRFrameCounts containing all types of corpus counts.
  Map<Integer,IRFrameCounts> _frameIRCounts;
  
  // Globals for evaluations.
  Map<String,Integer> _truePositives = new HashMap<String, Integer>();
  Map<String,Integer> _falsePositives = new HashMap<String, Integer>();
  Map<String,Integer> _totals = new HashMap<String, Integer>();
//  String[] _types = { "KIDNAP", "BOMBING", "ATTACK" };
  String[] _types = { "KIDNAP", "BOMBING", "ATTACK", "FORCED WORK STOPPAGE", "ROBBERY", "ARSON" };
  //Integer[] _ids = { 19, 0, 1 };
  //int[] _ids = { 47, 0, 9 }; // vbnn
  int[] _ids = { -1,-1,-1,-1,-1,-1 };
  Map<String,Integer> _topicTruePositives = new HashMap<String, Integer>();
  Map<String,Integer> _topicFalsePositives = new HashMap<String, Integer>();
  Map<String,Integer> _topicTotals = new HashMap<String, Integer>();
  //int[] _topicids = { 38, 34, 51 }; // vbnn
  int[] _topicids = { -1, -1, -1 };

  public LabelDocument() {
  }

  /**
   * Constructor
   */
  public LabelDocument(String args[]) {
    _params = new HandleParameters(args);
    _params.fromFile("reading.properties");
    
    // Set the token type to count.
    if( _params.hasFlag("-type") ) 
      _tokenType = CountTokenPairs.getType(_params.get("-type"));
    // Size of the biggest cluster (when to stop the clustering algorithm)
    if( _params.hasFlag("-clustersize") ) 
      _maxClusterSize = Integer.valueOf(_params.get("-clustersize"));
    if( _params.hasFlag("-numdocclusters") ) 
      _maxTopDocClusters = Integer.valueOf(_params.get("-numdocclusters"));
    if( _params.hasFlag("-numsentclusters") ) 
      _maxTopSentenceClusters = Integer.valueOf(_params.get("-numsentclusters"));
    if( _params.hasFlag("-onlyconsistent") ) CONSISTENT_SENTENCE_ONLY = true;

    // Are we counting dependents and not just governors?
    if( _params.hasFlag("-dependents") ) 
      _includeDependents = true;

    if( _params.hasFlag("-noargs") || _params.hasFlag("-noarg") )
      _doArgs = false;
    
    System.out.println("parse file: " + _params.get("-parsed"));
    _trainDataReader = new ProcessedData(_params.get("-parsed"), _params.get("-deps"), _params.get("-events"), _params.get("-ner"));
    
    if( _params.hasFlag("-testparsed") ) {
      _test1234DataReader  = new ProcessedData(_params.get("-testparsed"), _params.get("-testdeps"), _params.get("-testevents"), _params.get("-testner"));
      _test12DataReader  = new ProcessedData(_params.get("-test12parsed"), _params.get("-test12deps"), _params.get("-test12events"), _params.get("-test12ner"));
      _test34DataReader  = new ProcessedData(_params.get("-test34parsed"), _params.get("-test34deps"), _params.get("-test34events"), _params.get("-test34ner"));
    }
    if( _params.hasFlag("-topicmodel") ) _topicsModelDir = _params.get("-topicmodel");
//    _thesaurus = new PantelClusters(_params.get("-cbc"));

    if( _params.hasFlag("-evaltest") ) _evaluateOnTest = TestType.test1234;
    if( _params.hasFlag("-evaltest12") ) _evaluateOnTest = TestType.test12;
    if( _params.hasFlag("-evaltest34") ) _evaluateOnTest = TestType.test34;
    System.out.println("evaltest \t\t= " + _evaluateOnTest.toString());

    System.out.println("includeDependents \t= " + _includeDependents);
    System.out.println("maxClusterSize \t\t= " + _maxClusterSize);
    System.out.println("tokenType \t\t= " + _tokenType);
    System.out.println("maxTopDocClusters \t= " + _maxTopDocClusters);
    System.out.println("maxTopSentenceClusters \t= " + _maxTopSentenceClusters);
    System.out.println("onlyConsistentSent \t= " + CONSISTENT_SENTENCE_ONLY);

    System.out.println("topicsModel \t\t= " + _params.get("-topicmodel"));

    if( _params.hasFlag("-docsim") )
      CLUSTER_DOC_SIM = _params.get("-docsim");
    if( _params.hasFlag("-sentsim") )
      CLUSTER_SENT_SIM = _params.get("-sentsim");
    System.out.println("docsim\t\t\t= " + CLUSTER_DOC_SIM);
    System.out.println("sentsim\t\t\t= " + CLUSTER_SENT_SIM);
    
    if( _params.hasFlag("-doclusters") ) _doClusters = true;
    if( _params.hasFlag("-noclusters") ) _doClusters = false;
    if( _params.hasFlag("-dotopics") ) _doTopics = true;
    if( _params.hasFlag("-notopics") ) _doTopics = false;
    System.out.println("doClusters\t\t= " + _doClusters);
    System.out.println("doTopics\t\t= " + _doTopics);    
    System.out.println("doArgs\t\t\t= " + _doArgs);

    if( _params.hasFlag("-sentmember") ) _sentenceRequireMembership = true;
    if( _params.hasFlag("-docmember") ) _docRequireMembership = true;
    System.out.println("sentMember\t\t= " + _sentenceRequireMembership);
    System.out.println("docMember\t\t= " + _docRequireMembership);
    
    if( _params.hasFlag("-interpargs") ) INTERP_ARGS_WITH_PAIRS = true;

    if( _params.hasFlag("-reinduce") ) _reinduceSlots = true;
    System.out.println("reinduceSlots \t\t= " + _reinduceSlots);
    if( _params.hasFlag("-wait") ) _waitForSlots = true;
    System.out.println("waitForSlots \t\t= " + _waitForSlots);
    
    System.out.println("domainIDF \t\t= " + _params.get("-domainidf"));
    System.out.println("generalIDF \t\t= " + _params.get("-generalidf"));
    System.out.println("initialIDF \t\t= " + _params.get("-initialidf"));
    if( _params.hasFlag("-wordnet") )
      _wordnet = new WordNet(_params.get("-wordnet"));
    else
      _wordnet = new WordNet(WordNet.findWordnetPath());
    _detector = new DomainVerbDetector(_params.get("-domainidf"), _params.get("-generalidf"), _params.get("-initialidf"));
    _domainIDF = _detector._domainIDF;
    _generalIDF = _detector._generalIDF;
    _initialIDF = _detector._initialIDF;

    _newords = new NamedEntityWords(_params.get("-deps"), _params.get("-ner"), _domainIDF);
//    Set<String> nes = newords.getWords();
//    for( String ne : nes ) System.out.println("NE: " + ne);
    
    if( _params.hasFlag("-centralcutoff") )
      _centralTokenCutoff = Double.valueOf(_params.get("-centralcutoff"));
    System.out.println("Central token prob cutoff " + _centralTokenCutoff);

    // positive, turned negative (command-line doesn't like negative signs)
    if( _params.hasFlag("-docprobcutoff") ) 
      _docProbCutoff = -1.0 * Double.valueOf(_params.get("-docprobcutoff"));
    System.out.println("Document prob cutoff " + _docProbCutoff);

    if( !_params.hasFlag("-outputkeys") ) {
      if( _doClusters ) {
        // Load token pair counts.
        _domainTokenPairCounts = new CountTokenPairs(_params.get("-domainpairdist"));
//        if( _params.hasFlag("-kidnap-pairs") ) {
//          _kidnapIRPairCounts = new CountTokenPairs(_params.get("-kidnap-pairs"));
//          _kidnapIRPairCounts.addCountsFloat(_domainTokenPairCounts);
//          _kidnapIRIDF = new IDFMap(_params.get("-kidnap-idf"));
//          IRFrameCounts.addVerbObjectsToIDF(_kidnapIRIDF, new VerbArgCounts(_params.get("-kidnap-argcounts"), 1));
//        }
//        if( _params.hasFlag("-bombing-pairs") ) {
//          _bombingIRPairCounts = new CountTokenPairs(_params.get("-bombing-pairs"));
//          _bombingIRPairCounts.addCountsFloat(_domainTokenPairCounts);
//          _bombingIRIDF = new IDFMap(_params.get("-bombing-idf"));
//          IRFrameCounts.addVerbObjectsToIDF(_bombingIRIDF, new VerbArgCounts(_params.get("-bombing-argcounts"), 1));
//        }

        // Load the verb-dep counts.
        _relnCountsDomain = new CountVerbDepCorefs(_params.get("-domaindepcounts"));
      }

      // Load the SlotInducer - requires lots of data files.
      if( _doArgs || INTERP_ARGS_WITH_PAIRS ) {
        System.out.println("** Loading the SlotInducer **");
        _slotInducer = new SlotInducer(_params, _detector, _wordnet);
        System.out.println("** Finished SlotInducer **");
      }

      // Load the counts from doing IR for each frame. 
      if( _doClusters && _doArgs ) {
        if( _params.hasFlag("-frame-ir-counts") ) {
          _frameIRCounts = IRFrameCounts.loadFrameCountsFromDirectory(_params.get("-frame-ir-counts"), _slotInducer._domainSlotArgCounts.getAllSlotTokens(), _slotInducer._domainSlotArgCounts, _domainTokenPairCounts, _slotInducer._domainCorefPairs);
          _slotInducer.setIRFrameCounts(_frameIRCounts);
          System.out.println("Loaded all IR counts for all frames.");
          Util.reportMemory();
        }
      }
      
      // e.g. key-dev-0101.muc4
      _trainAnswerKey = new MUCKeyReader(_params.get("-muckey"));
      _test1234AnswerKey = new MUCKeyReader(_params.get("-muckeytest1234"));
      _test12AnswerKey = new MUCKeyReader(_params.get("-muckeytest12"));
      _test34AnswerKey = new MUCKeyReader(_params.get("-muckeytest34"));

      System.out.println("pairCounts \t= " + _params.get("-domainpairdist"));
      System.out.println("relnCountsDoman \t= " + _params.get("-domaindepcounts"));
    }

    // Add the verb-object collocations to IDF scores.
    if( _slotInducer != null ) {
      IRFrameCounts.addVerbObjectsToIDF(_domainIDF, _slotInducer._domainSlotArgCounts);
      IRFrameCounts.addVerbObjectsToIDF(_generalIDF, _slotInducer._generalSlotArgCounts);
    }
    
    _docSentenceGuesses = new HashMap<String,List<ScoredFrame[]>>();
    _docBlendedGuesses = new HashMap<String,List<ScoredFrame>>();
    _docConsistentGuesses = new HashMap<String,List<ScoredFrame>>();
    _docSimVecGuesses = new HashMap<String,List<ScoredFrame>>();
    _docOnlyGuesses = new HashMap<String,List<ScoredFrame>>();
    _docCentralGuesses = new HashMap<String,List<ScoredFrame>>();
  }

  /**
   * Find all syntactic slots that the gold entities appear in, count them for each MUC
   * slot type, and print the counts.
   */
  public void findSyntacticSlots(ProcessedData theDataReader, MUCKeyReader answerKey) {
    Map<String,Counter<String>> fullSlotsToCounts = new HashMap<String, Counter<String>>();
    
    if( theDataReader != null ) {
      // Load the next story.
      theDataReader.nextStory();

      // Read the documents in this file.
      while( theDataReader.getParseStrings() != null ) {
        String storyname = theDataReader.currentStory();
        List<Tree> trees = TreeOperator.stringsToTrees(theDataReader.getParseStrings());

        // Extra merging of coref clusters.
        List<EntityMention> entities = theDataReader.getEntities();
        Coref.mergeNameEntities(entities, _wordnet);

        // Gold Templates for this story.
        List<Template> goldTemplates = answerKey.getTemplates(storyname);
        List<MUCEntity> goldEntities = TemplateTester.getGoldEntities(goldTemplates);
        
        Map<String,Counter<String>> mucslotToCounts = findSyntacticSlots(trees, theDataReader.getDependencies(), entities, goldEntities);
        for( String key : mucslotToCounts.keySet() ) {
          if( !fullSlotsToCounts.containsKey(key) )
            fullSlotsToCounts.put(key, new IntCounter<String>());
          fullSlotsToCounts.get(key).addAll(mucslotToCounts.get(key));
        }        
        
        // Next story.
        theDataReader.nextStory();
      } // while parses
      theDataReader.close();
    }
    
    // Print the final results.
    for( String key : fullSlotsToCounts.keySet() ) {
      System.out.println("**" + key + "**");
      Counter<String> counts = fullSlotsToCounts.get(key);
      for( String slot : Counters.toSortedList(counts) )
        System.out.println("\t" + slot + " " + counts.getCount(slot));
    }
  }
  
  /**
   * Count all governor dependencies that see a gold entity as its dependent.
   */
  public Map<String,Counter<String>> findSyntacticSlots(List<Tree> trees, List<List<TypedDependency>> alldeps, List<EntityMention> entities, List<MUCEntity> goldEntities) {
    Map<String,Counter<String>> mucslotToCounts = new HashMap<String,Counter<String>>();
    // Find any mentions in the sentence that match gold entities, save their indices.
    if( goldEntities != null ) {
      for( EntityMention mention : entities ) {
        for( int sid = 0; sid < trees.size(); sid++ ) {
          if( mention.sid()-1 == sid ) {
            for( MUCEntity gold : goldEntities ) {
              if( TemplateTester.stringMatchToMUCEntity(gold, mention.string(), false) ) {
                System.out.println("Gold mention " + mention + " from muc gold " + gold);
                List<String> slots = StatisticsDeps.spanToSlots(mention.start(), mention.end()+1, trees.get(sid), alldeps.get(sid), _wordnet);
                for( String slot : slots ) {
                  String type = gold._templateType + ":" + gold._slotType;
                  if( !mucslotToCounts.containsKey(type) )
                    mucslotToCounts.put(type, new IntCounter<String>());
                  mucslotToCounts.get(type).incrementCount(slot);
                }
                System.out.println(" -- gold slots: " + slots);
              }
            }
          }
        }
      }
    }
    return mucslotToCounts;
  }
  
  /**
   * Print a tree's leaves (raw text) with brackets around the entities that are gold labels from MUC.
   * @param sid  The sentence index in the document.
   * @param tree The sentence's parse tree.
   * @param entities The entities as extracted from the tree.
   * @param goldEntities The gold entity strings from MUC.
   * @return
   */
  private String markupSentence(int sid, Tree tree, List<TypedDependency> sentDeps, List<EntityMention> entities, List<MUCEntity> goldEntities, List<Pair<String,EntityMention>> roleMentions) {
    Set<Integer> starts = new HashSet<Integer>();
    Set<Integer> ends = new HashSet<Integer>();
    Map<Integer,String> endLabels = new HashMap<Integer, String>();
    Set<Integer> roleStarts = new HashSet<Integer>();
    Set<Integer> roleEnds = new HashSet<Integer>();
    Map<Integer,String> endRoles = new HashMap<Integer, String>();
        
    // Find any mentions in the sentence that match gold entities, save their indices.
    if( goldEntities != null ) {
      for( EntityMention mention : entities ) {
        if( mention.sid()-1 == sid ) {
          for( MUCEntity gold : goldEntities ) {
            if( TemplateTester.stringMatchToMUCEntity(gold, mention.string(), false) ) {
              starts.add(mention.start()-1);
              ends.add(mention.end()-1);
              endLabels.put(mention.end()-1, gold.getTemplateType() + "-" + MUCTemplate.shortSlotType(gold.type()));
              
              System.out.println("Gold mention " + mention + " from muc gold " + gold);
              List<String> slots = StatisticsDeps.spanToSlots(mention.start(), mention.end()+1, tree, sentDeps, _wordnet);
              System.out.println(" -- gold slots: " + slots);
            }
          }
        }
      }
    }
    
    // All given role mentions are in this sentence, so just map their end indices to strings...
    for( Pair<String,EntityMention> roleMention : roleMentions ) {
      String id = roleMention.first();
      if( id.matches("^[A-Za-z]+.*") ) {
        EntityMention mention = roleMention.second();
        roleStarts.add(mention.start()-1);
        roleEnds.add(mention.end()-1);
        endRoles.put(mention.end()-1, id);
      }
    }
    
    // Build the sentence string with bracketed gold entities.
    StringBuffer sb = new StringBuffer();
    List<Tree> leaves = TreeOperator.leavesFromTree(tree);
    for( int i = 0; i < leaves.size(); i++ ) {
      if( starts.contains(i) ) sb.append('[');
      if( roleStarts.contains(i) ) sb.append('[');
      sb.append(TreeOperator.toRaw(leaves.get(i)));
      if( ends.contains(i) ) {
        sb.append(']');
        sb.append(endLabels.get(i));
      }
      if( roleEnds.contains(i) ) {
        sb.append(']');
        sb.append(endRoles.get(i));
      }
      sb.append(' ');
    }
    
    return sb.toString();
  }
  
  /**
   * Given a list of frames, find all mentions that are associated with roles in the
   * frames that were extracted from the sentence in the given sentence index.
   * @param sid The sentence we want all mentions from (sentences assumed to start at index 0).
   * @param frames The frames that contains mentions with frame roles.
   * @return A list of pairs: (frame-role-id, EntityMention)
   */
  private List<Pair<String,EntityMention>> getRoleMentions(int sid, Collection<Frame> frames) {
    List<Pair<String,EntityMention>> roleMentions = new ArrayList<Pair<String,EntityMention>>();

    if( frames != null ) {
      for( Frame frame : frames ) {
//        System.out.println("checking frame " + frame);
        for( int roleid = 0; roleid < frame.getNumRoles(); roleid++ ) {
//          System.out.println("  - roleid = " + roleid);
          for( Integer entityID : frame.getEntityIDsOfRole(roleid) ) {
//            System.out.println("    - entityID = " + entityID);
            EntityMention mention = frame.getEntity(entityID)._mainMention;
            if( mention.sentenceID() == sid+1 )
              roleMentions.add(new Pair<String,EntityMention>(frame.getID() + "-" + roleid, mention));
          }
        }
      }
    }
    return roleMentions;
  }

  /**
   * Debugging purposes: this prints the raw text of the documents, with gold/guessed frames and entities marked up.
   * @param theDataReader The training/testing data we want to debug.
   * @param answerKey The gold MUC templates per document.
   * @param generalFrames The general learned frames.
   * @param frameIDs The IDs of the frames that map to the MUC types in the mucTypes parameter.
   * @param mucTypes The string names of the MUC types that we care about.
   */
  private void printTextMarkup(ProcessedData theDataReader, MUCKeyReader answerKey, Frame[] generalFrames, int[] frameIDs, String[] mucTypes) {
    Map<Integer,String> idToType = new HashMap<Integer, String>();
    if( frameIDs != null )
      for( int i = 0; i < frameIDs.length; i++ ) idToType.put(frameIDs[i], mucTypes[i]);
    theDataReader.close();
    theDataReader.reset();
    
    System.out.println("**** DEBUG Text Markup ****");
    if( theDataReader != null ) {
      // Load the next story.
      theDataReader.nextStory();
      int storyID = theDataReader.currentStoryNum();

      // Read the documents in this file.
      while( theDataReader.getParseStrings() != null ) {
        String storyname = theDataReader.currentStory();
        List<Tree> trees = TreeOperator.stringsToTrees(theDataReader.getParseStrings());
        System.out.println("\n****\n" + storyname + " id=" + storyID);

        // Extra merging of coref clusters.
        List<EntityMention> entities = theDataReader.getEntities();
        Coref.mergeNameEntities(entities, _wordnet);

        // Gold Templates for this story.
        List<Template> goldTemplates = answerKey.getTemplates(storyname);
        List<MUCEntity> goldEntities = TemplateTester.getGoldEntities(goldTemplates);

        // TODO: this doesn't seem to be printing anything...
        // Print the frame IDs of the top frames for this story.
        List<Frame> docGuesses = null;
        if( _docFrameGuesses != null ) {
          docGuesses = _docFrameGuesses.get(storyname.toLowerCase());
          if( docGuesses != null ) 
            for( Frame guess : docGuesses ) {
              if( idToType.containsKey(guess.getID()) )
                System.out.print(idToType.get(guess.getID()) + " ");
              else System.out.print(guess.getID() + " ");
            }
          System.out.println();
        }
        
        List<ScoredFrame[]> sentenceGuesses = null;
        if( _docSentenceGuesses != null ) sentenceGuesses = _docSentenceGuesses.get(storyname);
        
        if( goldEntities != null)
          for( MUCEntity entity : goldEntities ) System.out.println("  *" + entity.getTemplateType() + "-" + MUCTemplate.shortSlotType(entity.type()) + "\t" + entity);

        // DEBUG
//        for( int sid = 0; sid < trees.size(); sid++ ) {
//          System.out.println("SID=" + sid);
//          List<Pair> roleMentions = getRoleMentions(sid, docGuesses);
//          for( Pair roleMention : roleMentions ) {
//            String id = (String)roleMention.first();
//            Integer frameid = Integer.parseInt(id.substring(0, id.indexOf('-')));
//            if( idToType.containsKey(frameid) )
//              roleMention.setFirst(idToType.get(frameid) + "-" + id.substring(id.indexOf('-')+1));
//            System.out.println("  role=" + roleMention);
//          }
//        }
        
        // *********************
        // Print the story text.
        int sid = 0;
        for( Tree tree : trees ) {
          // Frame guesses for this sentence.
          if( sentenceGuesses != null ) {
            ScoredFrame[] scored = sentenceGuesses.get(sid);
            for( int si = 0; si < _maxTopSentenceClusters && si < scored.length; si++ ) {
//            for( int si = 0; si < scored.length; si++ ) {
              if( idToType.containsKey(scored[si].frame().getID()) ) 
                System.out.print(idToType.get(scored[si].frame().getID()) + " ");
//              else System.out.print(scored[si].frame().getID() + " ");
            }
          }
          
          // All extracted mentions from this particular sentence that were put in roles.
          // Pair: frameid-roleid, EntityMention
          List<Pair<String,EntityMention>> roleMentions = getRoleMentions(sid, docGuesses);
          for( Pair<String,EntityMention> roleMention : roleMentions ) {
            String id = roleMention.first();
            Integer frameid = Integer.parseInt(id.substring(0, id.indexOf('-')));
            if( idToType.containsKey(frameid) )
              roleMention.setFirst(idToType.get(frameid) + "-" + id.substring(id.indexOf('-')+1));
          }
          
          // The raw text with entities highlighted.
          String marked = markupSentence(sid, tree, theDataReader.getDependencies().get(sid), theDataReader.getEntities(), goldEntities, roleMentions);
          System.out.println(marked);
          sid++;
        }
        
        // Next story.
        theDataReader.nextStory();
        storyID = theDataReader.currentStoryNum();
      } // while parses
      theDataReader.close();
    }
    
  }
  
  /**
   * Destructive to the frame, this removes any entity arguments whose heads are not physical
   * objects or materials.  If the word is known to wordnet, and it is not physical, then we
   * don't want it.
   */
  private void trimByPhysicalObjects(Frame frame) {
    Set<Integer> markedForRemoval = new HashSet<Integer>();
    Set<Integer> entities = frame.getEntityIDs();
    System.out.println("trimByPhysicalObjects entities=" + entities);
    if( entities != null ) {
      for( Integer id : entities ) {
        String arg = frame.getEntity(id).string();
        String head = arg;
        // Look at only the last word.
        if( head == null ) System.out.println("WARNING: null head in trimByPhysicalObjects id=" + id + " from frame " + frame);
        if( head != null && head.indexOf(' ') > -1 ) 
          head = arg.substring(arg.lastIndexOf(' ')+1);
        // Get the lemma (some words have non-phys plurals, by phys singulars)
        head = _wordnet.lemmatizeTaggedWord(head, "NN").toLowerCase();
        
        if( head != null )
          System.out.println(head + " unk " + _wordnet.isUnknown(head) + " phys " + _wordnet.isPhysicalObject(head) +
              " mat " + _wordnet.isMaterial(head) + " pers " + _wordnet.isNounPersonOrGroup(head));
        
        // Remove if the word is known and it is not a physical object/material.
        if( head != null && !_wordnet.isUnknown(head) && !_wordnet.isPhysicalObject(head) && !_wordnet.isMaterial(head) &&
            !_wordnet.isNounPersonOrGroup(head) ) {
          markedForRemoval.add(id);
          System.out.println("trimming non-phys " + arg);
        }
        // Remove if it is an unknown WordNet word, but very high frequency! (e.g. others, everything, anything, something)
        // Careful: names like Lopez, Hernandez, Martinez are frequent.
        else if( _wordnet.isUnknown(head) && _generalIDF.getDocCount("n-" + head) > 20000 ) {
          markedForRemoval.add(id);
          System.out.println("trimming unknown, but high frequency " + arg);
        }

      }
    }

    for( Integer id : markedForRemoval )
      frame.removeEntity(id);
  }

  private void trimPronounsAndMore(Frame frame) {
    Set<Integer> markedForRemoval = new HashSet<Integer>();
    Set<Integer> entities = frame.getEntityIDs();
    System.out.println("trimPronounsAndMore entities=" + entities);
    if( entities != null ) {
      for( Integer id : entities ) {
        String arg = frame.getEntity(id).string();

        if( Ling.isNumber(arg) || Ling.isPersonPronoun(arg) || Ling.isAbstractPerson(arg) || Ling.isInanimatePronoun(arg) ||
            arg.equals("'s") || !arg.matches(".*[a-zA-Z].*") || arg.startsWith("-") )
          markedForRemoval.add(id);
      }
    }

    for( Integer id : markedForRemoval ) {
      System.out.println("Removing pronoun/number " + frame.getEntity(id).string());
      frame.removeEntity(id);
    }
  }
  
  /**
   * Sometimes two different entities have the same name: e.g. explosion and explosion.
   * We don't want to return both, but only one of them.  It can mess up the evaluation
   * otherwise.
   */
  private void trimDuplicateFillers(Frame frame) {
    for( int roleid = 0; roleid < frame.getNumRoles(); roleid++ ) {
      Set<Integer> removal = new HashSet<Integer>();
      Set<String> seen = new HashSet<String>();
      for( Integer entityID : frame.getEntityIDsOfRole(roleid)) {
        EntityFiller filler = frame.getEntity(entityID);
        String name = filler.string();
        if( seen.contains(name) )
          removal.add(entityID);
        else
          seen.add(name);
      }
      // Remove
      for( Integer remove : removal ) {
        System.out.println("Removing duplicate entity " + remove + " from role " + roleid);
        frame.removeEntity(remove);
      }
    }
  }
  
  /**
   * @param token A token from the domain e.g. "v-kidnap"
   * @return A list of synonyms (with "v-" attached)
   */
  private List<String> synonymsOf(String token) {
    POS pos = POS.VERB;
    if( token.startsWith("n") ) pos = POS.NOUN;
    String header = token.substring(0, 2);
    
    // Get the synsets.
    Synset[] synsets = _wordnet.synsetsOf(token.substring(2), pos);
    
    // Get all words in all synsets.
    List<String> synonyms = new ArrayList<String>();
    if( synsets != null ) {
      for( Synset synset : synsets ) {
        List<String> syms = _wordnet.wordsInSynset(synset);
//        System.out.println("  token " + token + " synset " + synset + ": " + syms);
        for( String sym : syms ) {
          String strtoken = header + sym;
          if( !synonyms.contains(strtoken) )
            synonyms.add(strtoken);
        }
      }
    }
    return synonyms;
  }

  private void addSynonymLinksToCache(ScoreCache cache) {
    for( Pair<String,String> merge : preclusterSynonyms() ) {
      cache.setScore(merge.first(), merge.second(), 999.0f);
    }
  }

  private List<Pair<String,String>> preclusterSynonyms() {
    double vbnomRatioCutoff = 4.0;
//    double vbnnRatioCutoff = 4.0;
    
    // Domain tokens...
    Set<String> tokensLemmas = StatisticsDeps.keyDomainTokens(_domainIDF, _generalIDF, 
        _wordnet, _relnCountsDomain, _tokenType);
    
//    Map<String,Double> ratios = new HashMap();
    Set<String> highscorers = new HashSet<String>();
    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);
    for( String token : tokensLemmas ) {
      if( _domainIDF.getFrequency(token) > 5 ) {
//        if( token.startsWith("v-") || (token.startsWith("n-") && _wordnet.isNounEvent(token.substring(2))) ) {
          double ratio = verbDetector.likelihoodRatio(token);
//          ratios.put(token, ratio);
          System.out.printf("ratio = %.1f\t%s\n", ratio, token);
          if( ratio > vbnomRatioCutoff ) {
            highscorers.add(token);
          }
//          else if( _domainIDF.get(token) > 3.0f )
//            System.out.println(" -- passes idf? " + token + " " + _domainIDF.get(token));

//        }
      }
    }

    List<Pair<String,String>> pairs = new ArrayList<Pair<String,String>>();

    // Merge all verbs with similar nouns.  "v-release" -> "n-release"
    for( String token : tokensLemmas ) {
      if( token.startsWith("v-") ) {
        String noun = "n-" + token.substring(2);
        if( tokensLemmas.contains(noun) ) {
          System.out.println("vbnn merge: " + token + " " + noun);
          pairs.add(new Pair<String,String>(token, noun));
        }
      }
    }
    
    // Merge all nouns that have nominalization links with their verbs.
    for( String token : tokensLemmas ) {
      if( token.startsWith("n-") ) {
        String basenoun = token.substring(2);
        List<String> verbs = _wordnet.getVerbsOfNominalization(basenoun);
        if( verbs != null ) {
          for( String baseverb : verbs ) {
            int distance = StringUtils.editDistance(basenoun, baseverb);
            float ratio = (float)distance / (float)basenoun.length();
            System.out.println("distance: " + basenoun + " " + baseverb + " dist " + distance + " ratio " + ratio);
            if( basenoun.startsWith(baseverb) || ratio < .6 ) {
              String verb = "v-" + baseverb;
              if( tokensLemmas.contains(verb) ) {
                System.out.println("nominal merge: " + token + " " + verb + " dist " + distance);
                pairs.add(new Pair<String,String>(token, verb));
              }
            }
          }
        }
      }
    }
    
    // Merge all synonyms of high scored words.
    for( String token : highscorers ) {
      // Skip compounds e.g. v-plant#o#bomb
      if( !CountArgumentTypes.isObjectString(token) ) {
        System.out.println("syn check obj: " + token + " " + CountArgumentTypes.isObjectString(token));
        List<String> syns = synonymsOf(token);
        System.out.println("synonyms of " + token + ":\t" + syns);
        for( String syn : syns ) {
          if( !token.equals(syn) ) {
            if( highscorers.contains(syn) ) {
              System.out.println("merge: " + token + " " + syn);
              pairs.add(new Pair<String,String>(token, syn));
            }
          }
        }
      }
    }
  
    return pairs;  
  }
  
  
  /**
   * Calculate the probability of a token belonging to a frame based on its PMI edge scores
   * with that frame, normalized by its scores with all other frames.
   * @param frames All frames.
   * @param tokens The tokens we want to create probabilities for.
   * @param cache The pmi scores between tokens (v-kidnap and v-escape)
   */
  private void setTokenFrameProbsByPMI(Frame[] frames, Collection<String> tokens, ScoreCache cache) {
    double[] scores = new double[frames.length];
    
//    System.out.println("found kidnap " + cache.getScore("v-found", "v-kidnap"));
//    System.out.println("found release " + cache.getScore("v-found", "v-release"));
//    System.out.println("found abduct " + cache.getScore("v-found", "v-abduct"));
//    
//    if( tokens.contains("v-abduct") )
//      System.out.println("YES ABDUCT");
//    else System.out.println("NO ABDUCT");
    
    // Build probabilities for each token.
    for( String token : tokens ) {
      double sum = 0;
      int framei = 0;
      // Sum scores over each frame.
      for( Frame frame : frames ) {
        Set<String> frameTokens = new HashSet<String>(frame.tokens());
        if( !frameTokens.contains(token) ) frameTokens.add(token);
        Map<String,Double> tokenscores = ClusterUtil.scoreClusterTokens(frameTokens, cache);

        double score = tokenscores.get(token);
        // Default "high pmi score" when a token is already in this frame.
        if( frame.tokens().contains(token) ) score += 20.0; 
          
        scores[framei] = score;
        sum += score;
        framei++;
//        if( token.equals("v-kidnap") )
//          System.out.printf("*score* %s with %s %.4f\n", Util.getFirstN(frame.tokens(),5), token, score);
      }
    
      // Calculate P(frame | token) = score(frame, token) / sum_X score(frameX, token)
      framei = 0;
      for( Frame frame : frames ) {
        double prob = (sum > 0.0 ? scores[framei++] / sum : 0.0);
        frame.setTokenProb(token, prob);
//        if( token.equals("v-kidnap") ) System.out.printf("*prob* %s %.4f\n", token, prob);
      }
    }
  }

  private List<String> centralTokensInFrame(Frame frame, double probCutoff) {
    List<String> central = new ArrayList<String>();
    IDFMap frameIDF = (_frameIRCounts != null ? _frameIRCounts.get(frame.getID()).idf() : null);
    List<String> sortedKeys = (frame._tokenProbs != null ? Util.sortKeysByValues(frame._tokenProbs) : null);
    
    if( _frameIRCounts != null ) {
      for( String token : sortedKeys ) {
        //      if( frame.tokens().contains("v-kidnap") )
        //        System.out.println(token + " " + frame.getTokenProb(token) + " freq " + frameIDF.getFrequency(token) +
        //            " generalfreq " + _generalIDF.getFrequency(token));
        // Token needs to have a high probability, and be seen enough times in the IR corpus.
        if( frame.getTokenProb(token) >= probCutoff && frameIDF.getFrequency(token) > 200 ) {
          // If a verb that is seen a few times.
          if( token.startsWith("v-") && _generalIDF.getFrequency(token) > 250 )
            central.add(token);
          // If a noun that is seen more often and known to WordNet.
          else if( token.startsWith("n-") && _generalIDF.getFrequency(token) > 500 && !_wordnet.isUnknown(token.substring(2)) && !_wordnet.isNamedEntity(token.substring(2)))
            central.add(token);
        }
      }
    }
    
    for( String token : central ) {
      System.out.print(token + " " + frame.getTokenProb(token));
    }
    System.out.println();

    // Add key tokens as central if they are unique.
    for( String token : frame.tokens() ) {
      double generalProb = (double)_generalIDF.getFrequency(token) / (double)_generalIDF.totalCorpusCount();
      double irProb = (frameIDF == null ? 0.0 : (double)frameIDF.getFrequency(token) / (double)frameIDF.totalCorpusCount());
      double ratio = irProb / generalProb;
      System.out.printf("! %s genprob=%.6f irprob=%.6f ratio=%.6f framescore=%.1f\n", token, generalProb, irProb, ratio, frame.getTokenScores().get(token));
      if( frame.getTokenScores().get(token) > 20.0 ) {
        central.add(token);
        System.out.println("adding main token: " + token);
      }
    }
    
    // Add the top scores if we don't have 6 of them yet.
    if( sortedKeys != null ) {
      for( String token : sortedKeys ) {
        if( !central.contains(token) && frameIDF != null && frameIDF.getFrequency(token) > 200 ) {
          // If a verb that is seen a few times.
          if( token.startsWith("v-") && _generalIDF.getFrequency(token) > 250 )
            central.add(token);
          // If a noun that is seen more often and known to WordNet.
          else if( token.startsWith("n-") && _generalIDF.getFrequency(token) > 500 && !_wordnet.isUnknown(token.substring(2)) && !_wordnet.isNamedEntity(token.substring(2)))
            central.add(token);
        }
        if( central.size() >= 7 )
          break;
      }
    }
    
    for( String token : central ) {
      System.out.print(token + " " + frame.getTokenProb(token));
    }
    System.out.println();
    
    return central;
  }
  
  /**
   * Calculate the probability of a token belonging to a frame based on its likelihood
   * ratio score with that frame's IR documents, normalized by its scores with all other frames.
   * @param frames All frames.
   * @param tokens The tokens we want to create probabilities for.
   */
  private void setTokenFrameProbsByLikelihood(Frame[] frames, Collection<String> tokens) {
    System.out.println("setTokenFrameProbs top with " + tokens.size() + " tokens.");
    double[] scores = new double[frames.length];
        
    // PROBABILITIES: Compute the probability for each token.
    for( String token : tokens ) {
      double sum = 0;
      int framei = 0;
      // Sum scores over each frame.
      for( Frame frame : frames ) {
        IRFrameCounts ircounts = (_frameIRCounts != null ? _frameIRCounts.get(frame.getID()) : null);
        if( ircounts != null ) {
          double frequency = (double)ircounts.idf().getFrequency(token);

          double domainProb = frequency / (double)ircounts.idf().totalCorpusCount();
          double generalProb = (double)_generalIDF.getFrequency(token) / (double)_generalIDF.totalCorpusCount();

          double score = domainProb / generalProb;          
          scores[framei] = score;
          sum += score;
          framei++;
          //        if( frame.tokens().contains("v-explode") ) System.out.printf("*score* %s %.4f\n", token, score);
          //        if( token.equals("v-claim#o#responsibility") ) System.out.printf("*score* %s with %s %.4f\n", Util.getFirstN(frame.tokens(),5), token, score);
        }
        else System.out.println("WARNING: no IR counts for frame " + frame.getID());
      }
      
      // Calculate P(frame | token) = score(frame, token) / sum_X score(frameX, token)
      framei = 0;
      for( Frame frame : frames ) {
        double prob = (sum > 0.0 ? scores[framei++] / sum : 0.0);
        frame.setTokenProb(token, prob);
        //        if( token.equals("v-claim#o#responsibility") ) System.out.printf("*prob* %d %s %.4f\n", frame._id, token, prob);
      }
    }

    // CENTRAL TOKENS: set the central tokens (top probs, unique words)
    for( Frame frame : frames ) {
      System.out.println("Central frame " + frame.getID());

      frame.setCentralTokens(centralTokensInFrame(frame, _centralTokenCutoff));
      System.out.println("Central tokens frame " + frame.getID() + ": " + frame.getCentralTokens());

      // DEBUG
      if( frame.tokens().contains("v-kidnap") )
        System.out.println("KIDNAP FRAME");
      else if( frame.tokens().contains("v-explode") )
        System.out.println("EXPLODE FRAME");
      for( String token : frame._centralTokens )
        System.out.printf("**%s\t%.4f\t%d\n", token, ((frame._tokenProbs != null && frame._tokenProbs.containsKey(token)) ? frame._tokenProbs.get(token) : 0.0), _generalIDF.getFrequency(token));
      int i = 0;
      if( frame._tokenProbs != null )
        for( String token : Util.sortKeysByValues(frame._tokenProbs) ) {
          System.out.printf("%s\t%.4f\t%d\n", token, frame._tokenProbs.get(token), _generalIDF.getFrequency(token));
          i++;
          if( i >= 250 ) break;
        }
      System.out.println("Main tokens: ");
      for( String token : frame.tokens() ) {
        Double tokenProb = 0.0;
        if( frame._tokenProbs != null )
          tokenProb = frame._tokenProbs.get(token);
        if( tokenProb == null ) tokenProb = 0.0;
        System.out.printf("--%s\t%.4f\t%d\n", token, tokenProb, _generalIDF.getFrequency(token));
      }
      // END DEBUG

      // If no IR counts were available, set the token probs for central tokens really high (baseline evaluation).
      if( frame._tokenProbs == null ) {
        for( String token : frame._centralTokens )
          frame.setTokenProb(token, 1.0);
        _docProbCutoff = -Double.MAX_VALUE;
      }
    }
  }
  
  /**
   * We are interested in the word at index targetIndex.  It is a member of the given mention, presumably
   * not the head word.  We look at the mention and if it is a nominal event, then we split off the target
   * word into a new EntityMention.
   * @param targetIndex The index of our target word (e.g. bomb)
   * @param mention The entity mention of a potential nominal event (e.g. a bomb explosion)
   * @return A new mention, or the old mention if it is not a nominal event.
   */
  private EntityMention createMentionFromNominalEventModifier(List<EntityMention> mentions, Tree tree, int targetIndex, EntityMention mention) {
    // If the target word is not the leftmost.
    if( mention.end() > targetIndex ) {
      String[] tokens = mention.string().split("\\s+");
      System.out.println("createMention target=" + targetIndex + " checking " + tokens[tokens.length-1] + " in " + mention);

      // This mention is an event, so create a new mention just for its modifier.
      if( _wordnet.isNounEvent(tokens[tokens.length-1]) ) {
        
        // Check that the mention is a flat NP.
        if( TreeOperator.flatNP(tree, mention.start(), mention.end()) ) {
          int newID = EntityMention.maxID(mentions)+1;
          //        int target = mention.end() - mention.start() + targetIndex - mention.start() - 1;
          int target = targetIndex - mention.start();
          EntityMention newMention = new EntityMention(mention.sid(), tokens[target], targetIndex, targetIndex, newID);
          mentions.add(newMention);
          System.out.println("  - created new mention " + newMention + " for " + targetIndex + " from " + mention); 
          return newMention;
        }
        // Mention is not a flat NP.
        else {
//          System.out.println("  - mention is not a flat NP!");
          return mention;
        }
      }
      // Rightmost word in the mention is not an event word.
      else {
        System.out.println("RETURNING NULL AS NON-EVENT!");
//        return mention;
        return null;
      }
    }
    // The target index is already the last word in the mention.
    else return mention;
  }
  
  /**
   * Does the given token occur in this role's slots a lot?
   * @return The score for the token, or -1 if it is invalid.
   */
  private double isCentralArgumentOfRole(String token, FrameRole role, List<FrameRole> allRoles) {
    // ACL had .05
    final double ratioCutoff = 0.04;
    // Information Content cutoff, based on resnik's similarity, from WordNet.
    final float icCutoff = 3.0f;
    token = token.toLowerCase();
    
    // HACK - testing how this changes the score.
//    if( token.startsWith("member") && role.getSlots().contains("v-blow_up:s") )
//      return 0.9;
    
    // Get the token's scores.
    Double argscore = (role.getArgs() != null ? role.getArgs().get(token) : null);
    float informationContent = _generalIDF.getInformationContent("n-" + token);
//    System.out.println("  " + token + " centralArg " + argscore + " ic " + informationContent);
    
    // If the scores pass the thresholds.
    if( argscore != null && argscore > ratioCutoff && informationContent > icCutoff ) {

      // Check that it is not higher scoring in another role.
      boolean thebest = true;
      for( FrameRole otherRole : allRoles ) {
        Double score = (otherRole.getArgs() != null ? otherRole.getArgs().get(token) : null);
        if( score != null && score > argscore ) {
          thebest = false;
          break;
        }
      }
      
      // Check that the token is a valid entity argument.
      if( thebest && TemplateExtractor.validEntityArgument(token, _wordnet) ) 
        return argscore;
    }
        
    return -1.0;
  }
  
  private Map<RelnAndEntity,Integer> findRoleArgs(int sid, Tree tree, List<TypedDependency> deps, List<EntityMention> mentions, List<FrameRole> roles) {
    Map<RelnAndEntity,Integer> entityToRole = new HashMap<RelnAndEntity, Integer>();
    Map<Integer,Double> entityToScore = new HashMap<Integer, Double>();
    Map<Integer, String> particles = Ling.particlesInSentence(deps);
    
    if( roles != null ) {
      int roleid = 0;
      for( FrameRole role : roles ) {
        System.out.println("role: " + role);
        System.out.println("  -> roleid: " + roleid);
//        System.out.println("  -> args: " + role.getArgs());

        for( TypedDependency dep : deps ) {
          String strdep = dep.dep().value();
          String strgov = dep.gov().value();
//          System.out.println("  checking dep " + strdep + " gov " + strgov);
          String deplemma = CountTokenPairs.buildTokenLemma(strdep, dep.dep().index(), tree, particles, _wordnet);
          String basedep = deplemma.substring(2);
          String govlemma = CountTokenPairs.buildTokenLemma(strgov, dep.gov().index(), tree, particles, _wordnet);
          String basegov = govlemma.substring(2);
//          System.out.println("  checking dep " + deplemma + " gov " + govlemma);

          if( deplemma.startsWith("n-") && role.getArgs() != null && role.getArgs().containsKey(basedep) ) {
            double argscore = isCentralArgumentOfRole(basedep, role, roles);
//            System.out.println("  - score = " + argscore);
            if( argscore > 0.0 ) {
              EntityMention mention = TemplateExtractor.mentionAtIndex(mentions, sid, dep.dep().index(), -1);
              if( mention != null ) {

                // If the mention's head is not this word (probably not), and the head is an event, then
                // create a new mention that is just the dependent.
                if( mention.end() > dep.dep().index() ) {
                  mention = createMentionFromNominalEventModifier(mentions, tree, dep.dep().index(), mention);
                  //                    System.out.println("new mention " + mention);
                }

                if( mention != null ) {
                  Set<FrameRole.TYPE> entityTypes = validEntityRoleTypes(mention.entityID(), mentions, basedep);
                  if( entityTypes.contains(role._type) ) {                  
                    System.out.println("  - adding (dep) " + strdep + " -- " + mention + " -- score " + argscore);
                    if( !entityToScore.containsKey(mention.entityID()) || entityToScore.get(mention.entityID()) < argscore ) {
                      entityToScore.put(mention.entityID(), argscore);
                      entityToRole.put(new RelnAndEntity(basedep, basedep, dep.reln().getLongName(), mention), roleid);
                      for( RelnAndEntity reln : entityToRole.keySet() ) System.out.println("  -- " + reln + " role " + entityToRole.get(reln));
                    }
                  } else System.out.println("findRoleArgs(dep) skipping unmatched role type: " + deplemma + " in dep " + dep);
                }
              } else System.out.println("  - WOULD add, but no mention: " + strdep);
            }
          }
          
          if( govlemma.startsWith("n-") && role.getArgs() != null && role.getArgs().containsKey(basegov) ) {
            double argscore = isCentralArgumentOfRole(basedep, role, roles);
//            System.out.println("  - score = " + argscore);
            if( argscore > 0.0 ) {
              EntityMention mention = TemplateExtractor.mentionAtIndex(mentions, sid, dep.gov().index(), -1);
              if( mention != null ) {

                Set<FrameRole.TYPE> entityTypes = validEntityRoleTypes(mention.entityID(), mentions, basegov);
                if( entityTypes.contains(role._type) ) {
                  System.out.println("  - adding (gov) " + strgov + " -- " + mention + " -- score " + argscore);
                  if( !entityToScore.containsKey(mention.entityID()) || entityToScore.get(mention.entityID()) < argscore ) {
                    entityToScore.put(mention.entityID(), argscore);
                    //                  entityToRole.put(mention.entityID(), roleid);
                    entityToRole.put(new RelnAndEntity(basegov, basegov, dep.reln().getLongName(), mention), roleid);
                    for( RelnAndEntity reln : entityToRole.keySet() ) System.out.println("  -- " + reln + " role " + entityToRole.get(reln));
                  }
                } else System.out.println("findRoleArgs(gov) skipping unmatched role type: " + deplemma + " in dep " + dep);
              } else System.out.println("  - WOULD add, but no mention: " + strgov);
            }
          }
        }
        roleid++;
      }
    }
    
//    Set<Pair> args = new HashSet<Pair>();
//    for( Integer id : entityToRole.keySet() ) {
//      args.add(new Pair(id, entityToRole.get(id)));
//    }

    System.out.println("EntityToRole returning: ");
    for( RelnAndEntity reln : entityToRole.keySet() ) System.out.println("  -- " + reln + " role " + entityToRole.get(reln));
    
    return entityToRole;
  }
  
  /**
   * Cluster the tokens in the domain's document.  Return a set of scored clusters.
   * The SortableObject contains a score and the cluster in the form of a List of Strings.
   */
  private ReadingCluster[] clusterDomainTokens(ScoreCache cache) {
    System.out.println("LabelDocument clusterDomainTokens() top.");
    ReadingCluster[] theclusters = (ReadingCluster[])_globalCache.get("hierclusters");
    if( theclusters == null ) {
      // Choose domain *event* words with high counts.
      Set<String> tokensLemmas = StatisticsDeps.keyDomainTokens(_domainIDF, _generalIDF, _wordnet, _relnCountsDomain, _tokenType);
      // Put the set into a list form that can be indexed..
      List<String> tokenlist = Util.collectionToList(tokensLemmas);

      // Generate possible clusters.
      System.out.println("Hierarchical Clustering!!");
      List<ReadingCluster> rawclusters = ClusterMUC.hierarchicalCluster(tokenlist, cache, ClusterUtil.NEW_LINK_WITH_CONNECTION_PENALTY, _maxClusterSize);
//          ClusterUtil.NEW_LINK, _maxClusterSize);
      // Sort the clusters by cluster score.
      theclusters = new ReadingCluster[rawclusters.size()];
      theclusters = rawclusters.toArray(theclusters);
      Arrays.sort(theclusters);

      // Remove duplicate clusters.
      theclusters = ClusterMUC.removeDuplicateClusters(theclusters);
      System.out.println("Total possible clusters: " + theclusters.length);

      // Remove pointless small clusters.
      theclusters = ClusterMUC.removeSmallClusters(theclusters, 3);

      // Cache these clusters.
      _globalCache.put("hierclusters", theclusters);

      // Print the clusters.
      System.out.println("Number of possible clusters: " + theclusters.length);
      int i = 0;
      for( ReadingCluster cluster : theclusters ) {
        cluster.setID(i);
        
        // Debugging output of cluster and nearby words.
        System.out.println("  Cluster " + cluster);
        ClusterMUC.nearbyWords(tokenlist, cluster.getTokens(), cache);
        System.out.println();

//        // DEBUGGING
//        if( cluster.getTokens().contains("v-kidnap") && _slotInducer._kidnapIRCorefPairs != null)
//          ClusterMUC.nearbyWordsBySlot(tokenlist, cluster.getTokens(), _slotInducer._kidnapIRCorefPairs);
//        if( cluster.getTokens().contains("n-explosion") && _slotInducer._bombingIRCorefPairs != null )
//          ClusterMUC.nearbyWordsBySlot(tokenlist, cluster.getTokens(), _slotInducer._bombingIRCorefPairs);
//
//        // DEBUGGING
//        if( cluster.getTokens().contains("v-kidnap") && _slotInducer._kidnapIRCorefPairs != null)
//          ClusterMUC.nearbyWords(tokenlist, cluster.getTokens(), getCacheForFrame(new Frame(cluster.getID(), cluster.getTokenScores(), cluster.score()), cache));
//        if( cluster.getTokens().contains("n-explosion") && _slotInducer._bombingIRCorefPairs != null )
//          ClusterMUC.nearbyWords(tokenlist, cluster.getTokens(), getCacheForFrame(new Frame(cluster.getID(), cluster.getTokenScores(), cluster.score()), cache));

        
//        List<String> synonyms = synonymsOf(cluster.getTokens());
//        System.out.println("Synonyms: " + synonyms);
//        for( String syn : synonyms ) {
//          if( !cluster.getTokens().contains(syn) && _slotInducer._domainIDF.contains(syn) )
//            cluster.setTokenScore(syn.toLowerCase(), 0.1f);
//        }
        i++;
      }
    }

    return theclusters;
  }

  /**
   * Convenience function that maintains a global variable so we don't have to
   * recompute the key tokens for the same file over and over again...
   * @return Set of tokens from the document deemed most important.
   */
  private List<List<String>> getKeyTokensBySentence(ProcessedDocument doc) {
    List<List<String>> senttokens = (List<List<String>>)_globalCache.get("keytokensbysentence-" + _currentStory);
    if( senttokens == null ) {
      senttokens = new ArrayList<List<String>>();
      senttokens = ClusterMUC.getKeyTokensInDocument(doc, _wordnet, _generalIDF, _domainIDF, _tokenType, _includeDependents, _includeCollocationObjects);
      _globalCache.put("keytokensbysentence-" + _currentStory, senttokens);
    }
    return senttokens;
  }
  
  private Set<String> getKeyTokens(ProcessedDocument doc) {
    Set<String> thetokens = (Set<String>)_globalCache.get("keytokens-" + _currentStory);
    if( thetokens == null ) {
      thetokens = new HashSet<String>();
      List<List<String>> senttokens = getKeyTokensBySentence(doc);
      for( List<String> tokens : senttokens )
        for( String token : tokens )
          thetokens.add(token);
      _globalCache.put("keytokens-" + _currentStory, thetokens);
    }
    return thetokens;
  }

  /**
   * Given a list of words, find all words in the document that are close
   * to that cluster and add them to the Frame itself, matching them with 
   * the semantic roles, and scoring each entity according to the words it
   * was seen with.
   */
  public void extractBasedOnCluster(ProcessedDocument doc, Frame cluster, List<ScoredFrame[]> sentenceScores, boolean confineToCluster) {
    // Build cluster.
    List<String> clusterList = new ArrayList<String>();
    for( String item : cluster.tokens() ) clusterList.add(item);
    System.out.println("extractBasedOnCluster: " + clusterList + " with confineToCluster=" + confineToCluster);

    // All possible tokens from which to get arguments.
    Set<String> tokens = getKeyTokens(doc);
    ScoreCache cache = (ScoreCache)_globalCache.get("domain-pairpmi");
    
    if( tokens.size() > 0 ) {

      // Calculate PMI scores between pairs.
      if( cache == null ) {
        cache = StatisticsDeps.pairCountsToPMI(_domainTokenPairCounts, _domainIDF, null, 5, .7);
        _globalCache.put("domain-pairpmi", cache);
      }

      // Cluster thesaurus.
      Map<String,String> synonyms = new HashMap<String, String>();
//      Map<String,String> synonyms = synonymsOf(clusterList);
//      System.out.println("Synonyms: " + synonyms.keySet());
      
      // Calculate token scores based on their proximity to the Frame.
      List<SortableScore> scores = new ArrayList<SortableScore>();
      for( String token : tokens ) {
        float score = 0.0f;
        if( !confineToCluster || clusterList.contains(token) )
          score = ClusterMUC.scoreWithCluster(token, clusterList, cache, _domainIDF);
        // Score synonyms with the original cluster token.
        else if( synonyms.keySet().contains(token) ) {
          score = ClusterMUC.scoreWithCluster(synonyms.get(token), clusterList, cache, _domainIDF);
          System.out.println("scored synonym " + token + " of " + synonyms.get(token) + " = " + score);
        }
        
        if( score > 0.0f ) {
          scores.add(new SortableScore(score, token));
          System.out.println("added score: " + score + " for " + token);
        } else System.out.println("not added 0.0 score: " + token);
      }

      // Sort the scored tokens.
      SortableScore[] scoreArr = new SortableScore[scores.size()];
      scoreArr = scores.toArray(scoreArr);
      Arrays.sort(scoreArr);
      // Trim if we haven't confined it to our cluster, to avoid too many spurious predicates.
      if( !confineToCluster )
        scoreArr = (SortableScore[])Util.firstN(scoreArr, 10);

      // Get all the entities from this document that fill slots of the tokens.
      setFrameEntities(cluster, scoreArr, cache, doc);
    }

    // Find the main sentences that we matched with this cluster.  Then find all words in 
    // those sentences that are strong indicators in our roles.
    boolean nearbyArgs = true;
    boolean nearbySlots = true;

    List<List<String>> keyTokens = getKeyTokensBySentence(doc);

    // Identify matched sentences for quick sentence index lookup.
    Set<Integer> sidMatches = new HashSet<Integer>();
    int sid = 0;
    for( ScoredFrame[] scores : sentenceScores ) {
//      for( int i = 0; i < _maxTopDocClusters && i < scores.length; i++ ) {
      for( int i = 0; i < _maxTopDocClusters && i < scores.length && scores[i].score() > 0.0; i++ ) {
        if( scores[i].frame().getID() == cluster.getID() ) 
          sidMatches.add(sid);
      }
      sid++;
    }
    
    // Loop over each sentence, extract entities if our cluster scored highly.
    for( sid = 0; sid < sentenceScores.size(); sid++ ) {
      System.out.println("*sentence " + sid);

      // Find any words in this sentence that are key argument roles.
//      if( nearbyArgs && (sidMatches.contains(sid) || (sid > 0 && sidMatches.contains(sid-1))) ) {
      if( nearbyArgs && sidMatches.contains(sid) ) {
        System.out.println("Check top args");
        Map<RelnAndEntity,Integer> entityToRole = findRoleArgs(sid, doc.trees().get(sid), doc.deps.get(sid), doc.mentions, cluster.getRoles());
        System.out.println("done findRoleArgs with " + entityToRole.size() + " entities.");
        for( RelnAndEntity relnEntity : entityToRole.keySet() ) {
          int entityID = relnEntity.entityMention.entityID();
          System.out.println("   ** arg " + TemplateExtractor.entityBestString(entityID, doc.mentions) + " torole = " + entityToRole.get(relnEntity));
          if( cluster.getRoleOfEntity(entityID) == null ) {
            int role = entityToRole.get(relnEntity);
            // Auto-added argument: so set the type to the same as the role.
            Set<FrameRole.TYPE> validRoleTypes = new HashSet<FrameRole.TYPE>();
            validRoleTypes.add(cluster.getRoles().get(role)._type);
            // 0.99 is a made up score, is this used later?
            EntityFiller filler = new EntityFiller(entityID, TemplateExtractor.entityBestString(entityID, doc.mentions), 0.99, 
                relnEntity.entityMention, validRoleTypes, "TOP-ARG", null);
            cluster.setEntityRole(filler, role);
          } else System.out.println("     entity nearby is already in a role!");
        }
      }

      // Find any key events that are *nearby* to this cluster. (nearby defined by the slots in the roles already in the frame)
//      if( nearbySlots && (sidMatches.contains(sid) || (sid > 0 && sidMatches.contains(sid-1))) ) {
      if( nearbySlots && sidMatches.contains(sid) ) {
        System.out.println("Check nearby slots");
        Map<String, Double> nearbyScores = getNearbyWords(cluster, keyTokens.get(sid), getCacheForFrame(cluster, cache));
        //            Map<Integer,List<RelnAndEntity>> entitySlots = entitiesWithWords(nearbyScores.keySet(), trees, alldeps, entities);
        for( String token : nearbyScores.keySet() ) {
          List<RelnAndEntity> relns = TemplateExtractor.argumentsOfWord(token, doc.trees(), doc.deps, doc.mentions, _wordnet, sid, true);
          if( relns != null ) {
            for( RelnAndEntity reln : relns ) {
              System.out.println("  nearby within sentence reln: " + reln);
              int entityID = reln.entityMention.entityID();
              // Don't process an entity if we already have a role for it (from earlier non-nearby processing).
              if( cluster.getRoleOfEntity(entityID) == null ) {
                String descriptor = TemplateExtractor.entityBestString(entityID, doc.mentions);
                Set<FrameRole.TYPE> validRoleTypes = validEntityRoleTypes(entityID, doc.mentions, descriptor);
                int role = selectRole(reln, cluster.getRoles(), validRoleTypes);
                // 0.88 is a made up score, is this used later?
                EntityFiller filler = new EntityFiller(entityID, descriptor, 0.88, reln.entityMention, validRoleTypes, "NEARBY-SLOT", reln.govAndReln());
                cluster.setEntityRole(filler, role);
                System.out.println("  - setting nearby entity " + entityID + " " + descriptor + " to role " + role);
              } else System.out.println("     entity arg is already in a role!");
            }
          }
        }
      }
    }
    trimByPhysicalObjects(cluster);
    trimPronounsAndMore(cluster);
    trimDuplicateFillers(cluster);
  }

  /**
   * Given an entity (and its mentions), find all NER labels with the mentions, and lookup its
   * head word in wordnet to see if we can determine if the entity is a person, location,
   * or other role type.  We return the set of possible types.
   * @param entityID The entity
   * @param mentions The list of all mentions of all entities.
   * @param mainDescriptor The main string description for the entity.
   */
  private Set<FrameRole.TYPE> validEntityRoleTypes(int entityID, List<EntityMention> mentions, String mainDescriptor) {
    Set<NERSpan.TYPE> validNETypes = entityMentionNETypes(entityID, mentions);
    Set<FrameRole.TYPE> validEntityTypes = new HashSet<FrameRole.TYPE>();
    
    String key = mainDescriptor;
    int space = mainDescriptor.lastIndexOf(' ');
    if( space > -1 ) key = mainDescriptor.substring(space+1);

    // TODO: temporary check
    if( key.equalsIgnoreCase("person") || key.equalsIgnoreCase("people") ) {
      validEntityTypes.add(FrameRole.TYPE.PERSON);
      return validEntityTypes;
    }
    
    System.out.println("valid lookup key " + key + " ners " + validNETypes);
    System.out.println("  isPerson " + SlotTypeCache.isPerson(key, _wordnet));
    System.out.println("  isPhysObj " + SlotTypeCache.isPhysObject(key, _wordnet));
    System.out.println("  isLocation " + SlotTypeCache.isLocation(key, _wordnet));
    System.out.println("  isEvent " + SlotTypeCache.isEvent(key, _wordnet));
    System.out.println("  isUnk " + _wordnet.isUnknown(key));
    System.out.println("  isOther " + SlotTypeCache.isOther(key, _wordnet));

    if( validNETypes.contains(NERSpan.TYPE.LOCATION) || SlotTypeCache.isLocation(key, _wordnet) )
      validEntityTypes.add(FrameRole.TYPE.LOCATION);
    if( validNETypes.contains(NERSpan.TYPE.PERSON) || validNETypes.contains(NERSpan.TYPE.ORGANIZATION) || SlotTypeCache.isPerson(key, _wordnet) )
      validEntityTypes.add(FrameRole.TYPE.PERSON);

    // Don't label things events if they had NER tags.
    if( validNETypes.size() == 0 && SlotTypeCache.isEvent(key, _wordnet) )
      validEntityTypes.add(FrameRole.TYPE.EVENT);

    // Don't label things as physical objects if they had NER tags.
    if( validNETypes.size() == 0 && SlotTypeCache.isPhysObject(key, _wordnet) )
      validEntityTypes.add(FrameRole.TYPE.PHYSOBJECT);

    // Unknown words could be people or locations.
    if( validEntityTypes.size() == 0 && _wordnet.isUnknown(key) ) {
      validEntityTypes.add(FrameRole.TYPE.PERSON);
      validEntityTypes.add(FrameRole.TYPE.LOCATION);
    }
    
    
    // Physical objects (non-people) are OTHER.
    if( _wordnet.isUnknown(key) || _wordnet.isPhysicalObject(key) || _wordnet.isMaterial(key) )
      validEntityTypes.add(FrameRole.TYPE.OTHER);
    
    // Other is also basically anything that isn't a person or location...
    else if( SlotTypeCache.isOther(key, _wordnet) )
        validEntityTypes.add(FrameRole.TYPE.OTHER); 
    
//    // Physical objects (non-people) are OTHER.
//    if( _wordnet.isUnknown(key) || _wordnet.isMaterial(key) )
//      validEntityTypes.add(FrameRole.TYPE.OTHER);
//    
//    // Other is also basically anything that isn't a person or location...
//    else if( SlotTypeCache.isOther(key, _wordnet) )
//        validEntityTypes.add(FrameRole.TYPE.OTHER);
//    
//    // Not a person, but is physical.
//    else if( !validEntityTypes.contains(FrameRole.TYPE.PERSON) && _wordnet.isPhysicalObject(key) )
//      validEntityTypes.add(FrameRole.TYPE.OTHER);
    
    return validEntityTypes;
  }
  
  /**
   * @return The NE labels that tagged any of the entity's mentions.
   */
  private Set<NERSpan.TYPE> entityMentionNETypes(int entityID, List<EntityMention> mentions) {
    Set<NERSpan.TYPE> types = new HashSet<NERSpan.TYPE>();
    for( EntityMention mention : mentions ) {
      if( mention.entityID() == entityID ) {
//        System.out.println("mentionTagged: " + mention.namedEntity() + " by mention " + mention);
        if( mention.namedEntity() != NERSpan.TYPE.NONE )
          types.add(mention.namedEntity());
      }
    }    
    return types;
  }
  
  /**
   * Given a document, a frame, and a list of scored tokens, this function finds all entity
   * mentions that fill any of the tokens' slots.  It then adds them to the frame with entity
   * scores (sums of all tokens it is seen with).
   * @param frame The frame we want entities for.
   * @param scoreArr All scored tokens that we want to find entities with (usually the entire doc's key tokens).
   * @param cache The pairwise event-event cache (for clustering, not topics)
   */
  private void setFrameEntities(Frame frame, SortableScore[] scoreArr, ScoreCache cache, ProcessedDocument doc) {
    // Map the tokens to their scores.
    Map<String,Double> tokenScores = new HashMap<String,Double>();
    for( SortableScore scored : scoreArr ) tokenScores.put(scored.key(), scored.score());
    System.out.println("setFrameEntities scores: " + Arrays.toString(scoreArr));

    // Get all entities and the tokens' relations that they appear in.
    Map<Integer,List<RelnAndEntity>> entitySlots = entitiesWithWords(tokenScores.keySet(), doc);
    System.out.println("setFrameEntities frame = " + frame);
    System.out.println("Found " + entitySlots.size() + " entities with words.");
    for( Integer id : entitySlots.keySet() ) {
      List<RelnAndEntity> slots = entitySlots.get(id);
      System.out.println("Got entity id " + id + " with slots " + slots);
      double score = 0.0;
      for( RelnAndEntity slot : slots ) score += tokenScores.get(slot.original());

      String descriptor = TemplateExtractor.entityBestString(id, doc.mentions);
      Set<FrameRole.TYPE> validRoleTypes = validEntityRoleTypes(id, doc.mentions, descriptor);
      System.out.println("validRoleTypes for " + id + ": " + validRoleTypes);
      int bestrole = selectRole(slots, frame.getRoles(), validRoleTypes);
      
      System.out.println("setFrameEntities() id=" + id + " bestrole=" + bestrole + " descriptor=" + descriptor + " score=" + score);
      EntityFiller filler = new EntityFiller(id, descriptor, score, slots.get(0).entityMention, validRoleTypes, "TRIGGER-SLOT", slots.get(0).govAndReln());
      frame.setEntityRole(filler, bestrole);
//      frame.setEntityRole(id, bestrole);
//      frame.setEntityScore(id, score);
//      frame.setEntityString(id, descriptor);
//      frame.setEntityMention(id, slots.get(0).entityMention);
      System.out.println("Setting entity " + id + " " + descriptor + " with score " + score + " to role " + bestrole);
      System.out.println("  - slots: " + slots);
    }
    
    // Get the top scored entities in sorted order.
    trimByPhysicalObjects(frame);
    trimPronounsAndMore(frame);
    trimDuplicateFillers(frame);
    List<String> topEntitiesByScore = frame.getEntitiesByScore(10);
    System.out.println("topEntities: " + topEntitiesByScore);
    System.out.println("returning: " + (topEntitiesByScore == null ? null : topEntitiesByScore.subList(0, Math.min(5,topEntitiesByScore.size()))));
  }

  private Pair<Double,Double> meanDeviation(Collection<SortableScore> scores) {
    double sum = 0.0;
    for( SortableScore score : scores ) {
      sum += score.score();
    }
    double mean = sum / (double)scores.size();
    
    sum = 0.0;
    for( SortableScore score : scores ) {
      double diff = score.score() - mean;
      sum += diff * diff;
    }
    double stddev = Math.sqrt(sum / (double)scores.size());
    
    return new Pair<Double,Double>(mean, stddev);
  }
  
  /**
   * This function gets all nearby words to a frame based on the given cache's scores.
   * It does not look at adjectives, and it returns the top 150 tokens.
   * @param frame The frame we want nearby words for
   * @param docTokens The possible tokens that can be nearby.
   * @param cache The scores between words that determine nearby neighbors.
   * @param topN The number of top words to return.
   * @param scoreFrameTokens If true, we include frame tokens as nearby tokens, false doesn't.
   * @return The top 150 tokens and their nearby scores.
   */
  private Map<String,Double> getNearbyWordsPrecise(Frame frame, Collection<String> docTokens, ScoreCache cache, int topN, boolean scoreFrameTokens) {
    System.out.println("getNearbyWordsPrecise!! " + frame.tokens());
    // Get a special kidnap/bombing cache.
    cache = getCacheForFrame(frame, cache);
    
    SortableScore[] scored = ClusterMUC.nearbyWordsScored(docTokens, frame.tokens(), cache, true);
    
    // Calculate the mean score of the cluster members.
//    List<SortableScore> memberScores = new ArrayList<SortableScore>(); 
//    for( SortableScore score : scored ) {
//      if( frame.getTokens().contains(score.key()) )
//        memberScores.add(score);
//    }
//    Pair meanDev = meanDeviation(memberScores);
//    Double mean = (Double)meanDev.first();
//    Double stddev = (Double)meanDev.second();
//    System.out.println("cluster member mean: " + mean + " and dev: " + stddev);
    
    System.out.println("Total nearbys = " + scored.length);
    
    // Save all nearby words that score higher than the average cluster member's scores.
    Map<String,Double> scoremap = new HashMap<String,Double>();
    int i = 0;
    for( SortableScore score : scored ) {
//      if( score.score() >= (mean - stddev) && !frame.getTokens().contains(score.key()) ) {
      if( !score.key().startsWith("j-") && i < topN && !frame.tokens().contains(score.key()) ) {
        scoremap.put(score.key(), score.score());
        System.out.printf("Nearby %d %s %.4f\n", i, score.key(), score.score());
        i++;
      }
      else if( scoreFrameTokens && frame.tokens().contains(score.key()) ) {
        scoremap.put(score.key(), score.score());
        System.out.printf("Nearby %d %s %.4f\n", i, score.key(), score.score());
      }
      else System.out.printf("Skip Nearby %s = %.4f\n", score.key(), score.score());
    }
    return scoremap;
  }
  
  /**
   * Given a list of tokens in the document, return all of the tokens that are close to the given
   * frame, along with their cluster score.
   */
  private Map<String,Double> getNearbyWords(Frame frame, Collection<String> docTokens, ScoreCache cache) {
    // Get a special kidnap/bombing cache.
    cache = getCacheForFrame(frame, cache);
    
    SortableScore[] scored = ClusterMUC.nearbyWordsScored(docTokens, frame.tokens(), cache, false);
    Map<String,Double> scoremap = new HashMap<String,Double>();
    int i = 0;
    for( SortableScore score : scored ) {
      if( score.score() > 4.0 ) { // TODO: change this from a constant to be principled...
        scoremap.put(score.key(), score.score());
        System.out.println("Nearby " + score.key() + " = " + score.score());
      }
      i++;
    }
    return scoremap;
  }
  
  /**
   * Given a list of tokens in the document, return all of the verbs that are close to the given
   * frame with a high score, along with their cluster scores.
   */
  private Map<String,Double> getNearbyVerbs(Frame frame, Collection<String> docTokens, ScoreCache cache) {
    Map<String,Double> scored = getNearbyWords(frame, docTokens, cache);
    Set<String> removal = new HashSet<String>();
    for( Map.Entry<String, Double> entry : scored.entrySet() ) {
      if( entry.getValue() < 6.0f || entry.getKey().charAt(0) != 'v' )
        removal.add(entry.getKey());
    }

    for( String remove : removal ) scored.remove(remove);
    return scored;
  }

  /**
   * A helper function that usually just returns the given cache, unless the frame is about
   * kidnap or bombing, then it returns the IR generated cache.
   */
  private ScoreCache getCacheForFrame(Frame frame, ScoreCache mucCache) {
    ScoreCache cache = (ScoreCache)_globalCache.get("ir-pmi-" + frame.getID());
    
    if( cache == null && _frameIRCounts == null ) 
      return mucCache;
    else if( cache == null && _frameIRCounts != null ) {
      IRFrameCounts frameCounts = _frameIRCounts.get(frame.getID());
      if( frameCounts != null && frameCounts.pairDistanceCounts() != null ) {
        System.out.println("getCacheForFrame " + frame.getID() + " IR: calculating pmi");
        cache = StatisticsDeps.pairCountsToPMI(frameCounts.pairDistanceCounts(), _domainIDF, null, 5, .7);
        _globalCache.put("ir-pmi-" + frame.getID(), cache);
        cache.printSorted(200);
        // ** Clear the distances from memory! **
        frameCounts.clearPairDistanceCounts();
      } else {
        System.out.println("getCacheForFrame " + frame.getID() + " IR: no IR counts, using MUC counts");
        cache = mucCache;
      }
    }
    
    return cache;
  }
  
  private void addFullSlots(Frame frame, Collection<String> possibleNearby, ScoreCache cache) {
    System.out.println("addFullSlots frame: " + frame);

    //      if( !frame.tokens().contains("v-explode") && !frame.tokens().contains("v-kidnap") && !frame.tokens().contains("v-intensify") )
    //      if( !frame.tokens().contains("v-kidnap") )
    if( frame.getRoles() == null )
      return;

    // Get the special kidnap/bombing cache if relevant.
    cache = getCacheForFrame(frame, cache);

    if( _slotInducer != null ) {
      //        Map<String,Double> nearbyScores = getNearbyWords(frame, possibleNearby, cache);
      Map<String,Double> nearbyScores = getNearbyWordsPrecise(frame, possibleNearby, cache, 20, true);
      if( nearbyScores.size() > 0 ) {
        List<String> sortedNearby = Util.sortKeysByValues(nearbyScores);
        _slotInducer.addTokensToRoles(sortedNearby, frame, false);
      }
      else System.out.println("addFullSlots found no nearby words.");
      System.out.println("addFullSlots done w/ frame: " + frame);
    }
    else System.out.println("ERROR: slot inducer is null!!!!");
    System.out.println("Added full slots in frame: " + frame);
  }
  
  private void addNearbySlots(Frame frame, Collection<String> possibleNearby, ScoreCache cache) {
    System.out.println("addNearbySlots frame: " + frame);

    //      if( !frame.tokens().contains("v-explode") && !frame.tokens().contains("v-kidnap") && !frame.tokens().contains("v-intensify") )
    //      if( !frame.tokens().contains("v-explode") && !frame.tokens().contains("v-kidnap") )
    //      if( !frame.tokens().contains("v-kidnap") )
    if( frame.getRoles() == null )
      //      if( frame.getID() != 1 || frame.getID() != 18 || frame.getID() != 3 )
      return;

    // Get the special kidnap/bombing cache if relevant.
    cache = getCacheForFrame(frame, cache);

    if( _slotInducer != null ) {
      //        Map<String,Double> nearbyScores = getNearbyWords(frame, possibleNearby, cache);
      Map<String,Double> nearbyScores = getNearbyWordsPrecise(frame, possibleNearby, cache, 200, true);
      if( nearbyScores.size() > 0 ) {
        List<String> sortedNearby = Util.sortKeysByValues(nearbyScores);
        _slotInducer.addTokensToRoles(sortedNearby, frame, true);
      }
      else System.out.println("addNearbySlots found no nearby words.");
      System.out.println("addNearbySlots done w/ frame: " + frame);
    }
    else System.out.println("ERROR: addnearby is null!!!!");
    System.out.println("Added nearby slots in frame: " + frame);
  }
  
  private void addNearbyTriggers(Frame[] frames, Collection<String> possibleNearby, ScoreCache cache) {
    System.out.println("addNearbyTriggers top with " + frames.length + " frames.");
    for( Frame frame : frames ) {
      System.out.println("addNearbyTriggers frame: " + frame);
      if( _slotInducer != null ) {
        Map<String,Double> nearbyScores = getNearbyVerbs(frame, possibleNearby, cache);
        if( nearbyScores.size() > 0 ) {
          for( Map.Entry<String, Double> entry : nearbyScores.entrySet() )
            frame.addToken(entry.getKey(), entry.getValue());
        }
        else System.out.println("addNearbyTriggers found no nearby verbs.");
        System.out.println("addNearbyTriggers done w/ frame: " + frame);
      }
      else System.out.println("ERROR: slot inducer is null!!!!");
    }       
  }
    
  /**
   * Find the frame's role that contains this slot. (-1 otherwise)  
   */
  private int selectRole(RelnAndEntity slot, List<FrameRole> roles, Collection<FrameRole.TYPE> validRoleTypes) {
    if( roles == null || slot == null ) return -1;
    
    String strslot = slot.original + ":" + slot.reln;
    int roleid = 0;
    for( FrameRole role : roles ) {
      if( validRoleTypes == null || validRoleTypes.contains(role._type) ) {
        for( String roleslot : role.getSlots() ) {
          if( roleslot.equals(strslot) )
            return roleid;
        }
        System.out.println("role " + roleid + " type match but no slot");
      }
      else if( validRoleTypes != null && !validRoleTypes.contains(role._type) ) {
        System.out.println("skipping role " + roleid + " match because of type mismatch: " + role._type + " and " + validRoleTypes);
      }
      roleid++;
    }
    return -1;
  }
  
  /**
   * Given a list of slots, find the roles that these slots appear in, and count
   * the number of slots that each role contains.  Return the role that matched
   * the most given slots.
   * @return The index of the best role in the given list of roles. 
   */
  private int selectRole(List<RelnAndEntity> slots, List<FrameRole> roles, Collection<FrameRole.TYPE> validRoleTypes) {
    if( roles == null ) return -1;

    Map<Integer,Integer> roleCounts = new HashMap<Integer,Integer>();
    for( RelnAndEntity slot : slots ) {
      int roleIndex = selectRole(slot, roles, validRoleTypes);
      if( roleIndex > -1 ) Util.incrementCount(roleCounts, roleIndex, 1);
    }

    // Get the best.
    int bestrole = -1, bestcount = -1;
    for( Map.Entry<Integer,Integer> entry : roleCounts.entrySet() ) {
      if( entry.getValue() > bestcount ) {
        bestcount = entry.getValue();
        bestrole = entry.getKey();
      }
    }
    return bestrole;
  }

  /**
   * Given a list of tokens, return a map of entity IDs and a list of the verb-reln slots
   * that each ID was seen filling in the text.  Only verb-reln slots with verb roots that
   * are in the list of tokens are considered.
   */
  private Map<Integer,List<RelnAndEntity>> entitiesWithWords(Collection<String> tokens, ProcessedDocument doc) {
    System.out.println("entitiesWithWords tokens=" + tokens);
    Map<Integer,List<RelnAndEntity>> entitySlots = new HashMap<Integer,List<RelnAndEntity>>();
    for( String token : tokens ) {
      List<RelnAndEntity> args = TemplateExtractor.argumentsOfWord(token, doc.trees(), doc.deps, doc.mentions, _wordnet, -1, true);
      System.out.println("  token " + token + " args " + args);
      for( RelnAndEntity arg : args ) {
        List<RelnAndEntity> relns = entitySlots.get(arg.entityMention.entityID());
        System.out.println("  - arg " + arg + " relns " + relns);
        if( relns == null ) {
          relns = new ArrayList<RelnAndEntity>();
          entitySlots.put(arg.entityMention.entityID(), relns);
        }
        relns.add(arg);
      }
    }
    return entitySlots;
  }

  /**
   * Given a cluster (list of words), find all words in the document that are close
   * to that cluster and set their arguments in the Frame object, associated with its roles.
   * @param scoreOnlyTopicWords True if you only extract args of words in the given topic.
   *                            False if you extract from all words, scored by distance to topic.
   */
  public void extractBasedOnTopic(ProcessedDocument doc, Frame topic) {

    // All possible tokens from which to get arguments.
    Set<String> tokens = getKeyTokens(doc);

    // Calculate cluster scores.
    SortableScore[] scores = new SortableScore[tokens.size()];
    int i = 0;
    for( String token : tokens ) {
      double score = TopicsMUC.scoreWithTopic(token, topic, _domainIDF);
      scores[i++] = new SortableScore(score, token);
    }

    // Get all the entities from this document that fill slots of the tokens.
    setFrameEntities(topic, scores, null, doc);
  }

  private void saveSentenceGuesses(List<ScoredFrame[]> sentenceScores) {
    if( _docSentenceGuesses == null ) _docSentenceGuesses = new HashMap<String,List<ScoredFrame[]>>();
    
    if( sentenceScores != null )
      _docSentenceGuesses.put(_currentStory, sentenceScores);
  }
  
  /**
   * Save the frame globally until we process all of MUC.
   * @param storyname The MUC story id.
   * @param frame The guessed frame with roles filled in with entity guesses.
   */
  private void saveFrameGuesses(String storyname, Frame frame) {
    if( _docFrameGuesses == null ) _docFrameGuesses = new HashMap<String, List<Frame>>();

    if( frame != null ) {      
      List<Frame> frames = _docFrameGuesses.get(storyname.toLowerCase());
      if( frames == null ) {
        frames = new ArrayList<Frame>();
        _docFrameGuesses.put(storyname.toLowerCase(), frames);
      }
      System.out.println("Saving to story " + storyname + " the frame: " + frame);
      Util.reportMemory();
      frames.add(frame);

      //      Map<Integer,List<String>> roleGuesses = new HashMap<Integer, List<String>>();
      //      _docRoleGuesses.put(storyname, roleGuesses);
      //
      //      // Save all guesses for each role in the global list.
      //      for( int i = 0; i < frame.getNumRoles(); i++ ) {
      //        List<Integer> ids = frame.getEntityIDsOfRole(i);
      //        if( ids != null ) {
      //          List<String> guesses = new ArrayList<String>();
      //          for( Integer id : ids )
      //            guesses.add(frame.getEntityString(id));
      //
      //          roleGuesses.put(i, guesses);
      //        }
      //      }
    }
  }

  
  private void printSpecificRoleAssignments() {
    if( _docFrameGuesses != null ) {
      for( String doc : _docFrameGuesses.keySet() ) {
        System.out.println("** " + doc);
        List<Frame> frames= _docFrameGuesses.get(doc);
        for( Frame frame : frames )
          System.out.println("  " + frame);
      }
    }
  }

  /**
   * @param falsePos Optional number of "other" false positives for args, cases where we thought 
   *                 we guessed a kidnap template, but no template existed at all.
   * @param falseNeg Optional number of false negatives, docs with kidnap templates, but we didn't
   *                 guess a template at all.                
   */
  private void printArgEvaluationResults(Map<String,List<Integer>> argScores, Map<String,Integer> falsePos, Map<String,Integer> falseNeg) {
    if( argScores == null ) {
      System.out.println("NO ARG SCORES - null scores in printArgEvaluationResults()");
      return;
    }
    
    // Sort the keys.
    String[] keys = new String[argScores.size()];
    int i = 0;
    for( String key : argScores.keySet() ) keys[i++] = key;
    Arrays.sort(keys);

    // Print the results now, sorted by name.
    for( String key : keys ) {
      List<Integer> scores = argScores.get(key);
      System.out.print(key + " :\t");
      if( key.length() < 14 ) System.out.print("\t");
      System.out.print(scores);

      // Numbers include only docs that a template type matches, then score the arg selection.
      float precision = ((float)scores.get(0) / (float)(scores.get(0)+scores.get(1)));
      float recall = ((float)scores.get(0) / (float)(scores.get(0)+scores.get(2)));
      float f1 = 2.0f * (precision*recall) / (precision+recall);
      System.out.printf("\tprec=%.3f\trecall=%.3f\tf1=%.2f\n", precision, recall, f1);

      // Get false positives and negatives.
      Integer fps = falsePos.get(key);
      if( fps == null ) fps = 0;
      Integer fns = falseNeg.get(key);
      if( fns == null ) fns = 0;
      System.out.println("\tfps=" + fps + "\tfns=" + fns);     

      // Partial numbers: include all docs that we guessed a kidnap template for. 
      precision = ((float)scores.get(0) / (float)(scores.get(0)+scores.get(1)+fps));
      recall = ((float)scores.get(0) / (float)(scores.get(0)+scores.get(2)));
      f1 = 2.0f * (precision*recall) / (precision+recall);
      System.out.printf("\tALL-GUESSED\t[%d, %d, %d] prec=%.3f\trecall=%.3f\tf1=%.2f\n", scores.get(0), scores.get(1)+fps, scores.get(2), precision, recall, f1);

      // Full numbers: include docs where we didn't guess a kidnap template at all, and those that we missed.
      precision = ((float)scores.get(0) / (float)(scores.get(0)+scores.get(1)+fps));
      recall = ((float)scores.get(0) / (float)(scores.get(0)+scores.get(2)+fns));
      f1 = 2.0f * (precision*recall) / (precision+recall);
      System.out.printf("\tFULL-DOMAIN\t[%d, %d, %d] prec=%.3f\trecall=%.3f\tf1=%.2f\n", scores.get(0), scores.get(1)+fps, scores.get(2)+fns, precision, recall, f1);
    }  
  }

  public static void printEvaluationResults(Map<String,Integer> truePositives, Map<String,Integer> falsePositives, Map<String,Integer> totals) {
    //for( int i = 0; i < _types.length; i++ ) {
    for( String type : totals.keySet() ) {
      //  String type = _types[i];
      Integer correct = truePositives.get(type);
      Integer falsePos = falsePositives.get(type);
      Integer total = totals.get(type);
      if( total == null ) {
        System.out.println("ERROR in printEvaluationResults: total is null");
        return;
      }
      if( correct == null ) correct = 0;
      if( falsePos == null ) falsePos = 0;

      int guessed = correct + falsePos;
      float precision = 0.0f;
      if( guessed > 0 ) precision = (float)correct/(float)guessed;
      float recall = (float)correct/(float)total;
      float f1 = 2.0f * precision * recall / (precision + recall);
      System.out.printf("EVAL %s:\tP: %d/%d = %.3f\tR: %d/%d = %.3f\t F1: %.3f\n", type,
          correct, (correct+falsePos), precision,
          correct, total, recall,
          f1);
    }
  }

  /**
   * Given a scored set of pairs, we interpolate another score based on the argument
   * similarity of the slots of the pairs.
   * @param cache Scores of predicate pairs.
   */
  private void interpolateArgSimilarity(ScoreCache cache) {
    float lambda = 0.7f;
    System.out.println("interpolateArgSimilarity with lambda=" + lambda);
    Set<String> keyset = cache.keySet();
    String[] arr = new String[keyset.size()];
    arr = keyset.toArray(arr);

    for( int i = 0; i < arr.length-1; i++ ) {
      String key1 = arr[i];
      for( int j = i+1; j < arr.length; j++ ) {
        String key2 = arr[j];
        float score = cache.getScore(key1, key2);
        if( score > 0.0f ) {
          //          System.out.printf("keys: %s %s %.2f\n", key1, key2, score);
          float argscore = _slotInducer.alignSlots(key1, key2);
          score = lambda * score + (1.0f-argscore) * argscore;
          //          System.out.printf("  %s %s %.2f\n", key1, key2, score);
          cache.setScore(key1, key2, score);
        }
      }
    }
  }

  
  private void keepKeyDomainTokens(Map<String,Double> nearby) {
    Set<String> keyTokens = StatisticsDeps.keyDomainTokens(_domainIDF, _generalIDF, _wordnet, _relnCountsDomain, _tokenType);
    Set<String> removal = new HashSet<String>();
    for( String near : nearby.keySet() ) {
      if( !keyTokens.contains(near) )
        removal.add(near);
    }
    for( String near : removal ) {
      System.out.println("removing non-key nearby token " + near);
      nearby.remove(near);
    }
  }

  /**
   * Remove any adjectives in the list.
   */
  private List<String> removeAdjectives(Collection<String> tokens) {
    List<String> newlist = new ArrayList<String>();
    for( String token : tokens )
      if( !token.startsWith("j-") )
        newlist.add(token);
    return newlist;
  }
  
  private void removeSlots(Frame frame) {
    System.out.println("REMOVE SLOTS");
    if( _slotInducer != null && frame.getRoles() != null ) {
      System.out.println("Remove slots check frame " + frame.getID());
      _slotInducer.removeRoles(frame);

      // DEBUG
      System.out.println("Post-Remove " + frame.tokens());
      if( frame.getRoles() != null )
        for( FrameRole role : frame.getRoles() )
          System.out.println("  " + role.getSlots());
    }
    else if( _slotInducer == null )
      System.out.println("ERROR: slot inducer is null!!!!");
  }
  
  private void mergeSlots(Frame frame) {
    System.out.println("MERGE SLOTS");
    if( _slotInducer != null && frame.getRoles() != null ) {
      System.out.println("Merge slots frame " + frame.getID());
      _slotInducer.mergeRoles(frame, 0.3);

      // DEBUG
      System.out.println("Post-Merge " + frame.getID() + "\t" + frame.tokens());
      if( frame.getRoles() != null )
        for( FrameRole role : frame.getRoles() )
          System.out.println("  " + role.getSlots());
    }
    else if( _slotInducer == null )
      System.out.println("ERROR: slot inducer is null!!!!");
  }
    
  /**
   * Run the full gamut of inducing the roles for each frame.
   * @param frames All frames we want to induce slots for.
   * @param keyTokens The tokens we want to be in the roles.
   * @param cache Pairwise scores between slots, used to find nearby slots.
   */
  private void induceSlotsFully(Frame[] frames, Set<String> keyTokens, ScoreCache cache) {
      System.out.println("induceSlotsFully with " + frames.length + " frames.");
    for( Frame frame : frames ) {
      if( Locks.getLock("induceslots-frame-" + frame.getID()) ) {
        System.out.println("Reinducing frame: " + frame.getID());
        frame.clearRoles();
        induceSlots(frame, cache);
        System.out.println("Done initial frame: " + frame.toString(25, true));
        mergeSlots(frame);
        System.out.println("Remerged frame: " + frame.toString(25, true));
        addNearbySlots(frame, keyTokens, cache);
        System.out.println("Renearby frame: " + frame.toString(25, true));
        removeSlots(frame);
        System.out.println("Reremoved frame: " + frame.toString(25, true));
        addFullSlots(frame, keyTokens, cache);
        System.out.println("Reinduced frame: " + frame.toString(25, true));
        writeFrameToFile(frame);
        System.out.println("Finished frame: " + frame.getID());
//        System.out.println("Exiting induceSlotsFully...");
//        System.exit(-1);
      }
    }
  }

  private void continueOrQuit() {
    if( !Locks.getLock("induceslots-continue") ) {
      System.out.println("continueOrQuit quitting...");
      System.exit(0);
    }
  }

  private void induceSlots(Frame frame, ScoreCache cache) {
    if( _slotInducer != null ) {
      if( frame.getRoles() == null ) {
      //            frame.tokens().contains("v-explode") || frame.tokens().contains("v-kidnap") || frame.tokens().contains("v-intensify") ) {
      //            frame.tokens().contains("v-explode") || frame.tokens().contains("v-kidnap") ) {
      //            frame.tokens().contains("v-explode") ) {
      //        if( frame.getID() == 3 || frame.getID() == 18 || frame.getID() == 1 ) {
        System.out.println("Going to induce slots...");

        // Get nearby words for kidnap and bombing.
        Map<String,Double> nearby = null;
        ScoreCache irCache = getCacheForFrame(frame, cache);
        if( irCache != null ) {
          Set<String> keyTokens = StatisticsDeps.keyDomainTokens(_domainIDF, _generalIDF, _wordnet, _relnCountsDomain, _tokenType);
          nearby = getNearbyWordsPrecise(frame, keyTokens, irCache, 100, false);
          // Remove tokens that aren't key to the MUC documents.
          //          keepKeyDomainTokens(nearby);
        } 
        

        _slotInducer.induceSlots(frame, (nearby != null ? nearby.keySet() : null));
        _slotInducer.forceTriggersInSlots(frame);
      }
    }
    else System.out.println("ERROR: slot inducer is null!!!!");
    System.out.println("Induced slots in frame: " + frame);
  }

  public Frame[] extractUnknownFramesWithTopics(ProcessedDocument doc) {

    if( _topicsMUC == null ) {
      _topicsMUC = new TopicsMUC(_topicsModelDir);
      for( ReadingCluster topic : _topicsMUC.getTopics() ) {
        System.out.println("Topic " + topic);
      }
    }

    // Get the learned topic frames (with induced slots).
    Frame[] frames = (Frame[])_globalCache.get("topic-frames");
    if( frames == null ) {
      frames = new Frame[_topicsMUC.numTopics()];
      _globalCache.put("topic-frames", frames);

      // Build a frame and trim off the lesser words in the topic.
      for( int topicid = 0; topicid < _topicsMUC.numTopics(); ++topicid ) {
        Frame frame = new Frame(topicid, 0.0); // topics don't have general "scores"
        frame.setType("topic");
        frame.setTokenScores(TopicsMUC.trimTopicWords(_topicsMUC.topicTokenProbs(topicid)));
        System.out.println("Trimmed topic: " + frame);
        frames[topicid] = frame;
      }
      // Induce the semantic roles (slots) --- only process the key templates, save time...
      for( Frame frame : frames ) induceSlots(frame, null);
    }    

    // Extract document topics.
    List<ScoredFrame> bestTopics = _topicsMUC.labelDocumentWithTMTTopics(frames, doc, _wordnet, _generalIDF, _includeDependents, _maxTopDocClusters, _maxTopSentenceClusters);
    List<ReadingCluster[]> sentenceTopicScores = null;

    System.out.println("Number of relevant topics: " + bestTopics.size());
    for( ScoredFrame scored : bestTopics )
      System.out.println("  Relevant: " + scored);

    // Get the best arguments for each best cluster.
    Frame[] finals = new Frame[bestTopics.size()];
    int j = 0;
    for( ScoredFrame scored : bestTopics ) {
      Frame best = scored.frame();
      Frame filledFrame = new Frame(best.clusterScore(), best);
      if( _doArgs )
        argsFromTopic(filledFrame, sentenceTopicScores, doc);
      System.out.println("FINAL Topic: " + filledFrame);
      finals[j++] = filledFrame;
    }

    return finals;
  }

  private boolean waitForSlots(Frame[] frames) {
    int numFrames = frames.length;
    if( numFrames == 0 ) return false;
    int cycles = 0;
    
    System.out.println("Waiting for " + numFrames + " frames.");
    while( true ) {
      List<String> files = Directory.getFiles(_tempOutputDir);
      if( files.size() == numFrames ) {
        System.out.println();
        return true;
      }
      // sleep for 5 seconds
      try {
        Thread.currentThread().sleep(5000);
        System.out.print(".");
      } catch( Exception ex ) {
        ex.printStackTrace();
        System.exit(-1);
      }
      
      if( cycles % 30 == 29 ) 
        System.out.print("I see " + files.size());
      cycles++;
    }
  }
  
  private String makeCachePath(String base) {
    return base + "-" + _tokenType + "-" + _maxClusterSize + "-" + _doArgs;
  }

  /**
   * Creates Frame objects from their string forms in a file on disk, if the file exists.
   */
  public static Frame[] framesFromDiskCache(String inpath) {
    System.out.println("Looking for frames in cache: " + inpath);
    File infile = new File(inpath);
    if( infile.exists() ) {
      try {
        BufferedReader in = new BufferedReader(new FileReader(infile));

        List<Frame> frames = new ArrayList<Frame>();
        String line = null;
        while( (line = in.readLine()) != null ) {
//          System.out.println("line = " + line);
          Frame frame = Frame.fromString(line);
          System.out.println("Read frame: " + frame);
          frames.add(frame);
        }
        in.close();

        Frame[] arr = new Frame[frames.size()];
        arr = frames.toArray(arr);
        System.out.println("Returning " + arr.length + " frames from disk cache.");
        return arr;
      } catch( Exception ex ) { ex.printStackTrace(); }    
    }
    else System.out.println("Did not find frame cache: " + inpath);
    return null; 
  }

  /**
   * Reads a directory of files where each file is a single frame on a single line.
   * Creates the Frame objects and returns all of them in an array.
   */
  public static Frame[] framesFromDistributedDirectory(String indir) {
    System.out.println("Looking for distributed frames in cache: " + indir);
    List<Frame> frames = new ArrayList<Frame>();
    for( String filename : Directory.getFilesSorted(indir) ) {
      try {
        BufferedReader in = new BufferedReader(new FileReader(indir + File.separator + filename));
        String line = in.readLine();
        if( line != null ) {
          //          System.out.println("line = " + line);
          Frame frame = Frame.fromString(line);
          System.out.println("Read frame: " + frame);
          frames.add(frame);
        } else {
          System.out.println("ERROR: null line in frame file: " + indir + File.separator + filename);
          System.exit(-1);
        }
        in.close();
      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    // Sort by frame ID.
    SortableObject<Frame>[] sf = new SortableObject[frames.size()];
    for( int i = 0; i < frames.size(); i++ )
      sf[i] = new SortableObject<Frame>(frames.get(i).getID(), frames.get(i));
    Arrays.sort(sf);
    
    // Reverse the sort (to ascending)
    Frame[] arr = new Frame[frames.size()];
    for( int i = sf.length-1; i >= 0; i-- ) {
      arr[sf.length-1-i] = sf[i].key();
//      System.out.println("readframe " + arr[sf.length-1-i].getID());
    }

    System.out.println("Returning " + arr.length + " frames from disk cache.");
    return arr;
  }
  
  private void writeFrameToFile(Frame frame) {
    try {
	if( !Directory.fileExists(_tempOutputDir) )
	    Directory.createDirectory(_tempOutputDir);
      String outpath = makeCachePath(_tempOutputDir + File.separator + "frame" + frame.getID());
      System.out.println("Writing frame " + frame.getID() + " to disk: " + outpath);
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outpath, false)));
      writer.print(frame.toString(9999999, true));
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
  
  /**
   * Saves these frames in string form to a file on disk.
   * @param frames The array of frames to save.
   */
  private void framesToDiskCache(Frame[] frames) {
    try {
      String outpath = makeCachePath(_cachePath);
      System.out.println("Writing " + frames.length + " frames to cache: " + outpath);
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outpath, false)));
      for( Frame frame : frames ) {
        writer.print(frame.toString(9999999, true));
        writer.print("\n");
      }
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * This function clusters the domain's tokens into scenarios.  It also induces their slots.
   * It also stores the clusters in a global cache, and a disk cache, so repeated calls
   * to the function do not recompute the clusters every time.
   */
  private Frame[] loadOrLearnClusterFrames() {
    Frame[] frames = (Frame[])_globalCache.get("cluster-frames");
    if( frames == null ) {
      frames = framesFromDiskCache(makeCachePath(_cachePath));
      if( frames != null ) _globalCache.put("cluster-frames", frames);
    }
    
    // OPTIONAL: only cluster tokens with high likelihood ratios?
    Set<String> tokensLemmas = StatisticsDeps.keyDomainTokens(_domainIDF, _generalIDF, _wordnet, _relnCountsDomain, _tokenType);
    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);
    Set<String> remove = new HashSet<String>();
    for( String token : tokensLemmas ) {
      double ratio = verbDetector.likelihoodRatio(token);
      if( ratio < 1.4 && !CountArgumentTypes.isObjectString(token) ) remove.add(token);
    }
    for( String token : remove ) tokensLemmas.remove(token); 
    
    // Build the PMI edges.
    ScoreCache cache = (ScoreCache)_globalCache.get("pairpmi-interp");
    if( cache == null ) {
      System.out.println("PMI Cache is null in loadOrLearn...");

      // Build the cache between all domain tokens.
//      cache = StatisticsDeps.pairCountsToPMI(_tokenPairCounts, _domainIDF, null, 5, .7);
      cache = StatisticsDeps.pairCountsToPMI(_domainTokenPairCounts, _domainIDF, tokensLemmas, 5, .7);
      _globalCache.put("pairpmi-interp", cache);

      // We might have read frames from the disk cache, don't rerun...
      if( frames == null ) {
        System.out.println("Frames are null in loadOrLearn...");
        if( INTERP_ARGS_WITH_PAIRS ) interpolateArgSimilarity(cache);

        // Add strong synonyms links to the cache.
        System.out.println("Adding synonym links to clustering scores...");
        addSynonymLinksToCache(cache);

        // Build the clusters (frames) from the domain.
        ReadingCluster[] clusterScores = clusterDomainTokens(cache);
        System.out.println("Finished clustering.");

        // Convert clusters to general Frame objects.
        int i = 0;
        frames = new Frame[clusterScores.length];
        for( ReadingCluster cluster : clusterScores ) {
          Frame frame = new Frame(cluster.getID(), cluster.getTokenScores(), cluster.score());
          frame.setType("cluster");
          frames[i++] = frame;
          System.out.println("Frame: " + frame.toString(100, false));
        }
                
        induceSlotsFully(frames, tokensLemmas, cache);
        continueOrQuit();
        // Read all in, in case this is a distributed version and others did work for us.
        frames = framesFromDistributedDirectory(_tempOutputDir);

/*
        // Add really high nearby words.
        // NOT IN PAPER SUBMISSION TO ACL.
//        addNearbyTriggers(frames, cache.keySet(), cache);
        // Induce the slots.
        induceSlots(frames);
//        System.out.println("Stopping at end of initial slot induction!");
//        System.exit(-1);
        // Add nearby words to the slots.
        addNearbySlots(frames, tokensLemmas, cache);
        // Now merge frame roles if they are close together.
        mergeSlots(frames);
        // Now delete frame roles that are small and rare.
        removeSlots(frames);
        */
        if( frames != null )
          for( Frame frame : frames )
            System.out.println("A frame: " + frame.toString(25, true));

        _globalCache.put("cluster-frames", frames);
        framesToDiskCache(frames);

        // Reset the score cache (reset the high-scored synonyms and nominalizations).
        cache = StatisticsDeps.pairCountsToPMI(_domainTokenPairCounts, _domainIDF, tokensLemmas, 5, .7);
        _globalCache.put("pairpmi-interp", cache);
      }

      // If we are reinducing the cache's slots, do so, then quit.
      if( _reinduceSlots ) {
        System.out.println("Reinducing all slots!");
        induceSlotsFully(frames, tokensLemmas, cache);
        System.exit(-1);
      }

      if( _waitForSlots ) {
        waitForSlots(frames);
        frames = framesFromDistributedDirectory(_tempOutputDir);
        _globalCache.put("cluster-frames", frames);
        // Now write to the permanent location.
        framesToDiskCache(frames);
      }
      for( Frame frame : frames )
        System.out.println("frameid! " + frame.getID() + " numroles " + frame.getNumRoles());
      System.out.println("PMI now");
      
      // Recompute the PMI cache for *all* tokens, not just the main domain ones.
      cache = StatisticsDeps.pairCountsToPMI(_domainTokenPairCounts, _domainIDF, null, 5, .7);
      System.out.println("token probs now");
      // Now set the per-cluster probabilities for all tokens.
//      setTokenFrameProbsByPMI(frames, removeAdjectives(_domainIDF.getWords()), cache);
      setTokenFrameProbsByLikelihood(frames, removeAdjectives(_domainIDF.getWords()));
      System.out.println("finished token probs.");
      // DEBUG
//      for( Frame frame : frames ) {
//        System.out.println("Frame nearby: " + frame._id);
//        ScoreCache irCache = getCacheForFrame(frame, null);
//        if( irCache != null ) {
//          Set<String> keyTokens = StatisticsDeps.keyDomainTokens(_domainIDF, _generalIDF, _wordnet, _relnCountsDomain, _tokenType);
//          Map<String,Double> nearby = getNearbyWordsPrecise(frame, keyTokens, irCache, 100, false);
//          for( String key : nearby.keySet() )
//            System.out.printf("  Nearby %s\t%.3f\n", key, nearby.get(key));
//        }
//      }
      
    }

    return frames;
  }
  
  /**
   * Use clusters of the domain words (compute if not in the global cache)
   * to identify subpassages relevant to particular clusters.
   * Extract the entities associated with each cluster.
   * Returns the top n frames of a document, trimmed based on global topn settings.
   * Each frame is filled with entities extracted into its roles.
   */
  public Frame[] extractUnknownFramesWithClusters(ProcessedDocument doc) {
    System.out.println("extractUnknownFrames top!");

    // Learn or Load the frames.
    Frame[] frames = loadOrLearnClusterFrames();

    // Now get the pairwise slot cache from the global cache (from the previous function call).
    ScoreCache cache = (ScoreCache)_globalCache.get("pairpmi-interp");
    if( cache == null || frames == null ) {
      System.out.println("cache or frames are null!!  shouldn't happen...");
      System.exit(-1);
    }

    List<ScoredFrame> bestClusters = null;
    // Per sentence scores.
    List<ScoredFrame[]> sentenceScores = ClusterMUC.labelSentencesWithClusters(doc, frames, cache, _wordnet, _generalIDF, _domainIDF, _tokenType, _includeDependents, _sentenceRequireMembership);
//    ScoredFrame[] docScores = ClusterMUC.labelDocumentWithClusters(trees, alldeps, entities, frames, cache, _wordnet, _generalIDF, _tokenType, _includeDependents, _docRequireMembership);
//  bestClusters = ClusterMUC.mostRelevantClusters(docScores, sentenceScores, _maxTopDocClusters, _maxTopSentenceClusters);

    ScoredFrame[] docScores = ClusterMUC.labelDocumentWithClusterProbabilities(doc, frames, _wordnet, _generalIDF, _domainIDF, _tokenType, _includeDependents, _docRequireMembership);
//    bestClusters = ClusterMUC.mostRelevantDocProbClusters(docScores, _docProbCutoff);

    ScoredFrame[] preciseWinners = ClusterMUC.labelDocumentWithPreciseMatches(doc, frames, _wordnet, _generalIDF, _domainIDF, _tokenType, _includeDependents, _docRequireMembership);
//    bestClusters = ClusterMUC.mostRelevantPreciseClusters(preciseWinners);
    
    bestClusters = ClusterMUC.mostRelevantPreciseAndProbs(preciseWinners, docScores, _docProbCutoff);
    
    System.out.println("Number of relevant clusters: " + bestClusters.size());
    for( ScoredFrame frame : bestClusters ) System.out.printf("  Relevant: %.3f\t%d\t%s\n", frame.score(), frame.frame().getID(), Util.collectionToString(frame.frame().tokens(), 10));
    
    // for DEBUG later.
    saveSentenceGuesses(sentenceScores);
//    ScoredFrame[] blendedWinners = ClusterMUC.topBlendedSentenceWins(doc, sentenceScores, 1);
//    _docBlendedGuesses.put(_currentStory.toLowerCase(), Util.arrayToList(blendedWinners));
//    List<ScoredFrame> consistentWinners = ClusterMUC.consistentSentenceWins(sentenceScores);
//    _docConsistentGuesses.put(_currentStory.toLowerCase(), consistentWinners);
//    ScoredFrame[] simvecScores = labelDocumentWithSimilarityVectors(doc, frames, cache, _wordnet, _generalIDF, _domainIDF, _tokenType, _includeDependents, _docRequireMembership);
//    _docSimVecGuesses.put(_currentStory.toLowerCase(), Util.arrayToList(simvecScores));
    _docOnlyGuesses.put(_currentStory.toLowerCase(), Util.arrayToList(docScores));
    _docCentralGuesses.put(_currentStory.toLowerCase(), Util.arrayToList(preciseWinners));
    
    // Get the best arguments in this document for each best cluster.
    Frame[] finals = new Frame[bestClusters.size()];
    int i = 0;
    for( ScoredFrame best : bestClusters ) {
      Frame filledFrame = new Frame(best.score(), best.frame());
      if( _doArgs )
        argsFromCluster(filledFrame, sentenceScores, doc);
      System.out.println("FINAL Cluster: " + filledFrame);
      finals[i++] = filledFrame;
    }
    if( frames.length == 0 ) System.out.println("FINAL: none!!");

    return finals;
  }


  private void argsFromTopic(Frame topic, List<ReadingCluster[]> sentenceScores, ProcessedDocument doc) {
    extractBasedOnTopic(doc, topic);
  }

  private void argsFromCluster(Frame cluster, List<ScoredFrame[]> sentenceScores, ProcessedDocument doc) {
    extractBasedOnCluster(doc, cluster, sentenceScores, true);
  }


  /**
   * @param bestClusters List of the best clusters
   * @param domainClusters The original array of clusters and their scores for the domain.

  private List<String> argsFromClusters(List<SortableObject> bestClusters, SortableObject[] domainClusters,
      Vector<String> parses,
      Vector<Vector<TypedDependency>> alldeps,
      Vector<EntityMention> entities) {
    // Get the best arguments for each cluster.
    List<String> bestargs = null;
    int clusteri = 0;
    for( SortableObject bestScore : bestClusters ) {
      Integer bestid = (Integer)bestScore.key();
      List<String> bestCluster = (List<String>)domainClusters[bestid].key();
      List<String> args = extractBasedOnCluster(parses, alldeps, entities, bestCluster);
      System.out.println("*!*!" + domainClusters[bestid].key());
      System.out.println("  args: " + args);
      if( clusteri++ == 0 ) bestargs = args;
    }
    return bestargs;
  }
   */

  /**
   * This is mainly for topic modeling code ... output only the key tokens in
   * each sentence.
   */
  public void convertToKeyTokens() {
    ProcessedData theDataReader = _trainDataReader;
    
    if( theDataReader != null ) {
       // Load the first story.
      theDataReader.nextStory();

      // Read the documents in this file.
      while( theDataReader.getParseStrings() != null ) {
        System.out.println("\n" + theDataReader.currentStory());

        // Print the sentence's key tokens.
        List<List<String>> sentenceTokens = 
          ClusterMUC.getKeyTokensInDocument(theDataReader.getDocument(), _wordnet, _generalIDF, _domainIDF, _tokenType, _includeDependents, _includeCollocationObjects);
        for( List<String> sent : sentenceTokens ) {
          for( String token : sent ) 
            System.out.print(token + " ");
          System.out.println();
        }

        theDataReader.nextStory();
      } // while parses
    } else {
      System.out.println("No data path given!");
      System.exit(1);
    }
  }

  /**
   * Markup the entity mentions with the NER label if there is one, based on the
   * rightmost word in the entity mention.
   */
  private void addNERToEntities(Collection<EntityMention> entities, List<NERSpan> ners) {
//    System.out.println("addNERToEntities()");
    for( EntityMention entity : entities ) {
      int rightmost = entity.end();
      for( NERSpan span : ners ) {
        if( span.sid() == entity.sid()-1 && span.start() <= rightmost && span.end() > rightmost ) {
//          System.out.println("Adding NER " + span + " to mention " + entity);
          entity.setNamedEntity(span.type());
          break;
        }
      }
    }
  }

  
  /**
   * Open _inputPath and extract templates for each document.
   */
  public void processSingleFiles() {
    ProcessedData theDataReader = _trainDataReader;
    MUCKeyReader answerKey = _trainAnswerKey;
    if( _evaluateOnTest == TestType.test1234 ) {
      theDataReader = _test1234DataReader;
      answerKey = _test1234AnswerKey;
    }
    else if( _evaluateOnTest == TestType.test12 ) {
      theDataReader = _test12DataReader;
      answerKey = _test12AnswerKey;
    }
    else if( _evaluateOnTest == TestType.test34 ) {
      theDataReader = _test34DataReader;
      answerKey = _test34AnswerKey;
    }

    if( theDataReader != null ) {
      int NUMDOCS = 1;

//      printTextMarkup(theDataReader, answerKey, null, _ids, _types);
//      if( true ) System.exit(-1);
      
      // Load the next story.
      System.out.println("Getting first story...");
      theDataReader.nextStory();
      int storyID = theDataReader.currentStoryNum();
      System.out.println(theDataReader.currentStory() + " id=" + storyID);

      // Read the documents in this file.
      while( theDataReader.getParseStrings() != null ) {
        _currentStory = theDataReader.currentStory();
        System.out.print(NUMDOCS + ": (" + theDataReader.currentDoc() + "/??) ");
        System.out.println(_currentStory);

        // Extra merging of coref clusters.
        List<EntityMention> entities = theDataReader.getEntities();
        Coref.mergeNameEntities(entities, _wordnet);
        addNERToEntities(entities, theDataReader.getNER());

        // Templates for this story.
        _currentTemplates = answerKey.getTemplates(_currentStory);

        // Find the clusters!
        if( _doClusters ) {
          Frame[] frames = extractUnknownFramesWithClusters(theDataReader.getDocument());
          for( Frame frame : frames ) saveFrameGuesses(_currentStory, frame);
        }

        if( _doTopics ) {
          Frame[] topicFrames = extractUnknownFramesWithTopics(theDataReader.getDocument());
          for( Frame frame : topicFrames ) saveFrameGuesses(_currentStory, frame);
        }

        // Next story.
        theDataReader.nextStory();
        storyID = theDataReader.currentStoryNum();
        NUMDOCS++;
//        if( NUMDOCS > 500 ) break;
      } // while parses
      theDataReader.close();

      int[] frameids = _ids;
      Frame[] generalFrames = (Frame[])_globalCache.get("cluster-frames");
      if( _doTopics ) {
        frameids = _topicids;
        generalFrames = (Frame[])_globalCache.get("topic-frames");
      }
      
      System.out.println("DEBUG: Print Global Store of Guesses");
      printSpecificRoleAssignments();

      // Create the Evaluation Object.
      EvaluateTemplates evaluator = new EvaluateTemplates(_types, answerKey);
      evaluator.setGuesses(_docFrameGuesses);

      System.out.println("Find Best Frame to MUC Mapping");
      evaluator.mapFramesToMuc(generalFrames, frameids);
      
      System.out.println("** Evaluate Document Classification **");
      evaluator.evaluateDocumentClassification(frameids, _types);

      System.out.println("** Evaluate Bag of Entities BASIC EXTRACTION **");
//      evaluateBagOfEntitiesBasicExtraction(theDataReader, frameids, _types);
      
      System.out.println("** Evaluate Bag of Entities **");
      evaluator.evaluateBagOfEntities(frameids, _types, true);
      for( int i = 0; i < 2; i++ ) {
        System.out.println("** Specific " + _types[i] + " **");
        evaluator.evalSpecific(frameids[i], _types[i], _docBlendedGuesses);
        evaluator.evalSpecific(frameids[i], _types[i], _docConsistentGuesses);
        evaluator.evalSpecific(frameids[i], _types[i], _docSimVecGuesses);
        evaluator.evalSpecific(frameids[i], _types[i], _docOnlyGuesses);
        evaluator.evalSpecific(frameids[i], _types[i], _docCentralGuesses);
      }
      
      System.out.println("generalFrames = ");
      for( Frame frame : generalFrames ) System.out.print(frame.getID() + " ");
      System.out.println();
      
      if( _doArgs ) { // slot filling
        
        List<Pair<Integer,int[]>> thetop = new ArrayList<Pair<Integer,int[]>>();
        List<Integer> ignore = new ArrayList<Integer>();
        for( String type : _types ) {
          System.out.println("Top N " + type + " Evaluation");
          int n = 1;
          if( !type.equals("KIDNAP") && !type.equals("BOMBING") )
            n = 5;
          List<Pair<Integer,int[]>> topn = evaluator.evaluateTopNFrameAssignments(generalFrames, ignore, type, n, false);
          System.out.println("Exited topN " + type);

          if( type.equals("ATTACK") ) {
            System.out.println("TopN - GOLD DOCS ONLY");
            evaluator.evaluateTopNFrameAssignments(generalFrames, ignore, type, n, true);
          }

          for( Pair<Integer,int[]> top : topn )
            ignore.add(top.first());
          thetop.addAll(topn);          
        }
        
//        System.out.println("Top N Attack Evaluation");
//        List<Integer> ignore = new ArrayList<Integer>();
//        ignore.add(3); ignore.add(18);
//        List<Pair<Integer,int[]>> topn = evaluator.evaluateTopNFrameAssignments(generalFrames, ignore, _types[2], 40);
//        System.out.println("Exited topN.");
//
//        List<Pair<Integer,int[]>> topn3 = evaluator.evaluateTopNFrameAssignments(generalFrames, ignore, _types[3], 40);
//        System.out.println("Exited topN3.");
//        List<Pair<Integer,int[]>> topn4 = evaluator.evaluateTopNFrameAssignments(generalFrames, ignore, _types[4], 40);
//        System.out.println("Exited topN4.");
//        List<Pair<Integer,int[]>> topn5 = evaluator.evaluateTopNFrameAssignments(generalFrames, ignore, _types[5], 40);
//        System.out.println("Exited topN5.");
        
//        System.out.println("generalFrames = ");
//        for( Frame frame : generalFrames ) System.out.print(frame.getID() + " ");
//        System.out.println();
//        
        System.out.println("Specific Cluster Role Assignments");
        List<Pair<Integer,int[]>> bestperms = evaluator.evaluateSpecificRoleAssignments(frameids, generalFrames, EvaluateTemplates.F1, false);

        System.out.println("Specific Cluster Role Assignments - GOLD DOCS ONLY");
        evaluator.evaluateSpecificRoleAssignments(frameids, generalFrames, EvaluateTemplates.F1, true);
//        
//        // Add the kidnap and bombing frames to the top attack frames.
//        topn.add(bestperms.get(0));
//        topn.add(bestperms.get(1));
//        System.out.println("Topn: " + topn);
        
        System.out.println("Cross-Template Role Assignments");
//        evaluator.evaluateRoleAssignmentsIgnoringTemplateTypes(topn, generalFrames);
        evaluator.evaluateRoleAssignmentsIgnoringTemplateTypes(thetop, generalFrames, false);

        System.out.println("Cross-Template Role Assignments - GOLD DOCS ONLY");
        evaluator.evaluateRoleAssignmentsIgnoringTemplateTypes(thetop, generalFrames, true);
      } 

      System.out.println("Evaluate Clusters");
      printEvaluationResults(_truePositives, _falsePositives, _totals);
      System.out.println("Evaluate Topics");
      printEvaluationResults(_topicTruePositives, _topicFalsePositives, _topicTotals);
      if( _doArgs ) { // slot filling
        System.out.println("Evaluate Arguments");
        printArgEvaluationResults(_totalArgScores, _falsePositiveArgs, _falseNegativeArgs);
      }

      // DEBUG text output with entity markup.
      printTextMarkup(theDataReader, answerKey, generalFrames, frameids, _types);
      
    } else {
      System.out.println("No data path given!");
      System.exit(1);
    }
  }

  private boolean containsPairID(List<Pair<Integer,int[]>> pairs, int id) {
    for( Pair<Integer,int[]> pair : pairs )
      if( pair.first() == id )
        return true;
    return false;
  }

  public static enum TestType { train, test1234, test12, test34 };
  
  public static void main(String[] args) {
    HandleParameters params = new HandleParameters(args);

//    LabelDocument labeler2 = new LabelDocument();
//    labeler2.framesFromDiskCache();
    
    LabelDocument labeler = new LabelDocument(args);
    System.out.println(Arrays.toString(args));

    // Find in what syntactic slots the gold MUC entities appear.
//    labeler.findSyntacticSlots(labeler._trainDataReader, labeler._trainAnswerKey);
//    System.exit(-1);
    
    if( params.hasFlag("-outputkeys") )
      labeler.convertToKeyTokens();
    else { 
      labeler.processSingleFiles();
    }
  }
}

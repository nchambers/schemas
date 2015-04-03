package nate.args;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.BasicEventAnalyzer;
import nate.CountTokenPairs;
import nate.EntityMention;
import nate.GigaDocReader;
import nate.GigawordDuplicates;
import nate.GigawordHandler;
import nate.GigawordProcessed;
import nate.IDFMap;
import nate.NERSpan;
import nate.WordEvent;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.SortableScore;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * This class reads typed dependencies from parsed text and makes two
 * types of counts: (1) all arguments of verbs, and (2) all arguments of
 * pairs of verbs that are coreferring.  
 *
 * Input: events-dir, dependency-dir, parse-dir
 * Output: 2 files: one of verb arguments, and one of pair arguments
 *
 * -output <directory>
 * Specify an output directory for the files
 * 
 * -type vb|nn|vbnn|vbnom
 * The type of tokens of which to count args.
 * 
 * -nopairs
 * Only counts args of verbs, doesn't also count pairs of coref verbs.
 * 
 * CountArgumentTypes [-idf <idf>] [-nopairs] [-output <output-dir>] -events <events-dir> -deps <deps-dir> -parsed <parse-dir> -ner <ner-dir>
 *
 */
public class CountArgumentTypes {
  private String _parseDir;
  private String _eventsDir;
  private String _depsDir;
  private String _nerDir;
  private String _lockDir = "locks";
  private String _outputDir = ".";
  private String _outputPairFile = "argcounts-pairs.arg";
  private String _outputVerbFile = "argcounts-verbs.arg";
  private String _duplicatesPath = "duplicates";
  private int _pairDistance = 999999;
  int _numStories = 0;
  int _tokenType = CountTokenPairs.VERBS_AND_NOUNS;
  // Individual tokens must have at least this IDF to be counted in a pair.
  float _idfCutoff = 0.9f;
  // Individual tokens must appear at least this many times to be counted in a pair.
  int _docCutoff = 10;
  boolean _fullPrep = true; // true if you want "p_during" and not just "p"
  boolean _doPairs = true; // false if you don't want to count verb pair arguments
  boolean _countObjectCollocations = false; // true if you want to count objects as part of events

  
  private HashSet<String> _ignoreList;
  private HashSet<String> _duplicates;
  private Map<String, Map<String,Integer>> _pairCounts;
  //  private Map<String, Map<String, Map<String,Integer>>> _pairCounts;
  private Map<String, Map<String,Integer>> _verbCounts;

  private WordNet _wordnet;
  private IDFMap _idf;


  CountArgumentTypes(String[] args) {
    handleParameters(args);

    initLexResources();

    _pairCounts = new HashMap<String, Map<String,Integer>>();
    _verbCounts = new HashMap<String, Map<String,Integer>>();
  }

  public CountArgumentTypes(WordNet wordnet, boolean fullPrep, boolean doPairs) {
    _wordnet = wordnet;
    _fullPrep = fullPrep;
    _doPairs = doPairs;
    // Make an empty IDF map ... this is just usd in counting verb pairs to save memory.
    _idf = new IDFMap();

    if( _doPairs ) _pairCounts = new HashMap<String, Map<String,Integer>>();
    _verbCounts = new HashMap<String, Map<String,Integer>>();
  }

  /**
   * CountArgumentTypes <events-dir> <deps-dir>
   */
  private void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    // Set the token type to count.
    if( params.hasFlag("-type") ) 
      _tokenType = CountTokenPairs.getType(params.get("-type"));
    System.out.println("tokenType\t" + CountTokenPairs.getStringType(_tokenType));
    System.out.println("idfCutoff\t" + _idfCutoff);
    System.out.println("pairDocCutoff\t" + _docCutoff);
    System.out.println("fullPrep\t" + _fullPrep);

    if( params.hasFlag("-objects") ) _countObjectCollocations = true;
    System.out.println("objectCollocations\t" + _countObjectCollocations);
    
    _idf = new IDFMap(params.get("-idf"));
    _wordnet = new WordNet(params.get("-wordnet"));

    if( params.hasFlag("-nopairs") )
      _doPairs = false;

    if( params.hasFlag("-output") )
      _outputDir = params.get("-output");

    _parseDir = params.get("-parsed");
    _eventsDir = params.get("-events");
    _depsDir = params.get("-deps");
    _nerDir = params.get("-ner");
  }

  /**
   * Read duplicates list, setup ignore list.
   */
  private void initLexResources() {
    // Duplicate Gigaword files to ignore
    _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);

    // Ignore List (evaluation docs)
    _ignoreList = new HashSet<String>();
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
  }

  /**
   * Find the NE tag that includes part of the given mention.
   * @param mention The mention.
   * @param ners All ner tags for the document (or sentence).
   * @return A single NE tag type.
   */
  private NERSpan.TYPE nerTagOfMention(EntityMention mention, List<NERSpan> ners) {
    for( NERSpan span : ners ) {
      //      System.out.print(" span " + span);
      if( span.sid()+1 == mention.sid() ) {
        //        System.out.print(" SENTMATCH");
        // We match the NE tag if it overlaps *any* single token in the mention.
        // This expression looks weird, but is correct since mentions use weird indexing.
        if( span.start() <= mention.end() && span.end() > mention.start() ) {
          // Add a final check to make sure the mention isn't super long...we probably
          // shouldn't match any NE to it then.
          if( mention.end()-mention.start()+1 <= 2*(span.end()-span.start()) )
            return span.type();
        }
        //        System.out.println();
      }
    }
    return NERSpan.TYPE.NONE;
  }

  /**
   * @param alldeps Vector of dependencies, in sentence order already
   * @param entities A list of entities that have been resolved to each other
   * @param eventVec A Vector of WordEvents seen in the entire document
   */
  public void analyzeDocument(List<Tree> trees, List<List<TypedDependency>> alldeps,
      List<EntityMention> entities, List<NERSpan> ners, int storyID ) {

    // Get all desired tokens from the document. 
    List<WordEvent> events = CountTokenPairs.extractEvents(trees, alldeps, entities, _wordnet, _tokenType, _fullPrep);
//        for( WordEvent event : events ) System.out.println("event: " + event.toStringFull());

    // Index mentions by sentence, and save all strings for each mention ID
    Map<Integer,Set<String>> entityIDToStrings = new HashMap<Integer,Set<String>>();
    Map<Integer, String> entityIDCache = new HashMap<Integer, String>();
    for( EntityMention mention : entities ) {
      Set<String> args = entityIDToStrings.get(mention.entityID());
      if( args == null ) {
        args = new HashSet<String>();
        entityIDToStrings.put(mention.entityID(), args);
      }
      args.add(mention.string());
    }

    // Map entity IDs to the set of NER tags its mentions were assigned.
    Map<Integer,Counter<NERSpan.TYPE>> entityIDToNE = new HashMap<Integer, Counter<NERSpan.TYPE>>();
    for( EntityMention mention : entities ) {
      Counter<NERSpan.TYPE> nercounts = entityIDToNE.get(mention.entityID());
      if( nercounts == null ) {
        nercounts = new IntCounter<NERSpan.TYPE>(); // 4 NER tags (including NONE)
        entityIDToNE.put(mention.entityID(), nercounts);
      }
      NERSpan.TYPE tag = nerTagOfMention(mention, ners);
      //      System.out.println("mention " + mention + " ner " + NERSpan.typeToString(tag));
      nercounts.incrementCount(tag);
    }

    // Count arguments of tokens with their objects. (collocations)
    if( _countObjectCollocations ) {
      List<WordEvent> allNewEvents = new ArrayList<WordEvent>();
      for( WordEvent event : events ) {
        List<WordEvent> newEvents = createCollocations(event, entityIDToStrings, entityIDCache, entityIDToNE);
        if( newEvents != null )
          allNewEvents.addAll(newEvents);        
      }
      events.addAll(allNewEvents);
    }
    
    // Count arguments of our tokens.
    for( WordEvent event : events ) {
//      System.out.println("count event "+ event);
      countSingleArgs(event, entityIDToStrings, entityIDCache, entityIDToNE, storyID);
    }
    
    // Count arguments to event pairs with coreferring arguments.
    if( _doPairs )
      countEventPairs(events, entityIDToStrings, entityIDCache, entityIDToNE, storyID, _pairDistance);
  }


  /**
   * Counts the arguments with this event using the global counts map.
   * @return A cache of entity ID's to their representative strings
   */
  private void countSingleArgs(WordEvent event, Map<Integer,Set<String>> entityIDToStrings,
      Map<Integer, String> entityIDCache, Map<Integer,Counter<NERSpan.TYPE>> entityIDToNE, int fid ) {
    // Get the arguments
    HashMap<Integer,String> args = event.arguments();

    // Tally each argument
    if( args != null ) {
      for( Integer ID : args.keySet() ) {
        String argName = entityIDToString(entityIDCache, entityIDToStrings, entityIDToNE, ID);
        assert argName != null : "Null argument from ID " + ID + "! " + event + " fid=" + fid;

        String reln = args.get(ID);
        String slot = event.token() + ":" + reln;
        countSingleArgType(slot, argName);
      }
    }
  }

  public static boolean isObjectString(String str) {
    if( str != null && str.indexOf("#o#") > -1 ) return true;
    else return false;
  }
  
  public static String connectObject(String token, String object) {
    return token + "#o#" + object;
  }

  public static String getObject(String str) {
    int split = str.indexOf("#o#");
    if( split > -1 ) return str.substring(split+3);
    else return null;
  }
  
  public static String removeObject(String str) {
    int split = str.indexOf("#o#");
    if( split > -1 ) return str.substring(0, split);
    else return null;
  }
  
  /**
   * Creates collocations from the given event, if they are possible.  These are basically
   * duplicate WordEvent objects, but some of the arguments are removed and put into the
   * token itself (e.g. "claim responsibility").
   * Some predicates have more than one object, so we may return more than one WordEvent.
   */
  private List<WordEvent> createCollocations(WordEvent event, Map<Integer,Set<String>> entityIDToStrings,
      Map<Integer, String> entityIDCache, Map<Integer,Counter<NERSpan.TYPE>> entityIDToNE) {
    // Get the arguments
    HashMap<Integer,String> args = event.arguments();

    if( args != null ) {
      // If one of the relations is an object, find it!  Weeeeee!
      Set<Integer> objectEntityIDs = new HashSet<Integer>();
      for( Map.Entry<Integer,String> entry : args.entrySet() ) {
        if( entry.getValue().equals("o") ) {
          objectEntityIDs.add(entry.getKey());
        }
      }
      
      // If the event has an object.
      if( objectEntityIDs.size() > 0 ) {
        List<WordEvent> newEvents = new ArrayList<WordEvent>();
        for( Integer entityID : objectEntityIDs ) {
          // Append the object to the predicate.
          String objectName = entityIDToString(entityIDCache, entityIDToStrings, entityIDToNE, entityID);
          String newname = connectObject(event.token(), objectName);
          // Create the new event.
          WordEvent newEvent = new WordEvent(999, newname, event.position(), event.sentenceID());
          newEvents.add(newEvent);
          // Add the args, except the object.
          for( Map.Entry<Integer,String> entry : args.entrySet() ) {
            if( entry.getKey() != entityID ) {
              newEvent.addArgAsIs(entry.getValue(), entry.getKey());
            }              
          }
        }
        return newEvents;
      }
    }
    return null;
  }


  /**
   * NOTE: Largely copied in form from BasicEventAnalyzer (countEventPairs)
   *
   * Count all pairs of verbs that share an NP argument, saving the argument
   * @param events A Vector of WordEvents in order they appear in a document
   * @param entityIDToString Maps an entity's ID to a set of all strings that
   *                         coref says represent that entity.
   * @param entityIDCache Just a cache of strings already calculated for
   *                      certain entities.
   * @param distance Max sentence distance to consider for pairs
   * @param fid File ID of the story we are counting events in
   */
  private void countEventPairs( List<WordEvent> events, Map<Integer,Set<String>> entityIDToStrings,
      Map<Integer, String> entityIDCache, Map<Integer,Counter<NERSpan.TYPE>> entityIDToNE,
      int fid, int distance ) {
    int numEvents = events.size();
    Set<String> seen = new HashSet<String>();

    for( int i = 0; i < numEvents-1; i++ ) {
      WordEvent e1 = events.get(i);
      String w1 = e1.token();
      seen.clear();

      if( isObjectString(w1) || (_idf.getDocCount(w1) > _docCutoff && _idf.get(w1) > _idfCutoff) ) {
        for( int j = i+1; j < numEvents && j < i+1+distance; j++ ) {
          WordEvent e2 = events.get(j);
          String w2 = e2.token();

          // skip if the same verb
          if( w1.equals(w2) || (e1.sentenceID() == e2.sentenceID() && e1.position() == e2.position()) ) 
            continue;

          if( isObjectString(w2) || (_idf.getDocCount(w2) > _docCutoff && _idf.get(w2) > _idfCutoff) ) {
            // get the shared dependency type
            String shared = e1.sharedArgument(e2.arguments());

            if( shared != null && !seen.contains(w2) ) {
              Integer sharedID = e1.sharedArgumentID(e2.arguments());
              String full = BasicEventAnalyzer.sortBigram(w1,w2,shared);

              String argName = entityIDToString(entityIDCache, entityIDToStrings, entityIDToNE, sharedID);
              countArgTypeForPair(full, argName);

              seen.add(w2);
            }
          }
        }
      }
    }
  }


  /**
   * Increment the verbPair count for this argument in the global hash
   * table 'pairCounts'.
   */
  private void countArgTypeForPair( String verbPair, String argString ) {
    assert verbPair != null;
    assert argString != null;
    Map<String,Integer> args;

    // Find the verb pair, or create a new entry.
    if( _pairCounts.containsKey(verbPair) ) args = _pairCounts.get(verbPair);
    else {
      args = new HashMap<String,Integer>();
      _pairCounts.put(verbPair, args);
    }

    // Find the argument string, or create a new entry.
    if( args.containsKey(argString) )
      args.put(argString, args.get(argString)+1);
    else
      args.put(argString, 1);
  }


  /**
   * Increment the verb count for this argument in the global hash
   * table 'verbCounts'.
   */
  private void countSingleArgType( String verb, String argString ) {
    Map<String,Integer> args;

    if( _verbCounts.containsKey(verb) ) args = _verbCounts.get(verb);
    else {
      args = new HashMap<String,Integer>();
      _verbCounts.put(verb, args);
    }

    // Find the argument string, or create a new entry.
    if( args.containsKey(argString) )
      args.put(argString, args.get(argString)+1);
    else
      args.put(argString, 1);
  }


  /**
   * Given a map from IDs to sets of strings, we choose one representative string
   * for the set and create a new hashmap from IDs to these single strings.

  public Map<Integer,String> condenseEntities(Map<Integer,Set<String>> entityIDToStrings) {
    Map<Integer,String> condensed = new HashMap<Integer,String>();

    // Loop over entity IDs
    for( Map.Entry<Integer, Set<String>> entry : entityIDToStrings.entrySet() ) {
      Integer id = entry.getKey();
      Set<String> argStrings = entry.getValue();

//      System.out.println("id " + id + " args " + argStrings);

      // Get the best word/class for all strings seen with this ID
      String best = getRepresentativeClass(argStrings);
      condensed.put(id, best);
    }

    return condensed;
  }
   */

  /**
   * Given a set of strings, return one string that best represents the set.
   */
  private String getRepresentativeClass(Set<String> argStrings, Counter<NERSpan.TYPE> necounts) {
    Map<String, Integer> counts = new HashMap<String, Integer>();
    String best = null;
    Integer bestTotal = 0;
    int personIndicators = 0;
    boolean capitalized = false;

    // DEBUG
    //    System.out.print("*");
    //    for( String arg : argStrings )
    //      System.out.print(" " + arg);
    //    System.out.println();

    // Is there a dominating NE tag?
    NERSpan.TYPE bestType = NERSpan.TYPE.NONE;
    double bestcount = 0;
    for( NERSpan.TYPE type : necounts.keySet() ) {
      if( necounts.getCount(type) > bestcount ) {
        bestType = type;
        bestcount = necounts.getCount(type);
      }
    }
//    for(int i = 0; i < necounts.size(); i++ ) {
//      if( necounts[i] > bestcount ) {
//        besti = i;
//        bestcount = necounts[i];
//      }
//    }
    
    //    System.out.println(" ner " + Arrays.toString(necounts) + " best " + NERSpan.typeToString(besti));
    // If there is a dominating NE tag, return it now!
    if( bestType != NERSpan.TYPE.NONE ) 
      return bestType.toString();

    // Normal string processing without NE tags...
    for( String arg : argStrings ) {
      Integer total = 1;

      // Get the head word
      String head = getHead(arg);

      // Lowercase the word
      String lowerhead = head.toLowerCase();

      // Stem the word - ASSUME IT IS A NOUN
      // We don't really care about verb/adjective arguments...
      String headLemma= _wordnet.nounToLemma(lowerhead);
      if( headLemma == null ) headLemma = lowerhead;

      //      System.out.println("arg: " + arg + " -> " + headLemma);

      //      System.out.println("Checking *" + lowerhead + "*");
      // Check for pronouns
      if( Ling.isPersonPronoun(headLemma) ) { //|| Ling.isPersonRef(headLemma) ) {
        headLemma = "*properson*";
        personIndicators++;
      }
      else if( Ling.isInanimatePronoun(headLemma) ) {
        headLemma = "*pro*";
      }

      // Tally this word
      if( !counts.containsKey(headLemma) ) {
        total = 1;
        counts.put(headLemma, 1);
      } else {
        total = counts.get(headLemma) + 1;
        counts.put(headLemma, total);
      }

      // Check for capitalized names
      char first = head.charAt(0);
      if( first >= 'A' && first <= 'Z' && !headLemma.startsWith("*pro") ) {
        capitalized = true;
        personIndicators++;
      }

      // Check if this arg head is the most frequent
      if( total > bestTotal && !headLemma.startsWith("*pro") ) {
        bestTotal = total;
        best = headLemma;
      }

      // Just in case all we have is a pronoun, make sure to return it.
      // We don't set the bestTotal so other words can easily override.
      if( best == null && headLemma.startsWith("*pro") )
        best = headLemma;
    }

    //    if( best == "*properson*" ) best = "*per*";

    // See if we had enough person indicators.
    // Choose PERSON if more than 75% of the references were capitalized or person pronouns
    // - constrained that at least one must be capitalized
    // Sometimes there is only one argument, and it is a person pronoun, so we mark it as a person.
    if( (capitalized && personIndicators > 1 && personIndicators > (float)argStrings.size() *.75) ) {
      if( necounts.getCount(NERSpan.TYPE.PERSON) > necounts.getCount(NERSpan.TYPE.LOCATION) && necounts.getCount(NERSpan.TYPE.PERSON) > necounts.getCount(NERSpan.TYPE.ORGANIZATION) )
        best = "PERSON";
      else if( necounts.getCount(NERSpan.TYPE.LOCATION) > necounts.getCount(NERSpan.TYPE.PERSON) && necounts.getCount(NERSpan.TYPE.LOCATION) > necounts.getCount(NERSpan.TYPE.ORGANIZATION) )
        best = "LOCATION";
      else if( necounts.getCount(NERSpan.TYPE.ORGANIZATION) > necounts.getCount(NERSpan.TYPE.PERSON) && necounts.getCount(NERSpan.TYPE.ORGANIZATION) > necounts.getCount(NERSpan.TYPE.LOCATION) )
        best = "ORGANIZATION";
    }

    assert best != null : "We need to return something for this!";

    //    System.out.println(" capitalized=" + capitalized + " best=" + best + " personIndicators=" + personIndicators);
    //    System.out.println(" best = " + best);

    //    System.out.println("**BEST " + best);
    return best;
  }

  /**
   * Returns the head word of a multi-word string
   */
  public static String getHead(String tokens) {
    int i = tokens.lastIndexOf(' ');
    if( i == -1 ) 
      return tokens;
    else {
      String head = tokens.substring(i+1, tokens.length());

      // If the phrase's last token is 's: e.g. "the judge 's"
      if( head.equals("'s") || head.equals("'") ) {
        int j = tokens.lastIndexOf(' ', i-1);
        if( j == -1 )
          return tokens.substring(0, i);
        else
          return tokens.substring(j, i);
      }
      // The last token is the head, and is not "'s"
      else
        return tokens.substring(i+1, tokens.length());
    }
  }

  public Map<String,Map<String,Integer>> getVerbCounts() {
    return _verbCounts;
  }

  /**
   * Returns a single string to represent the entity with the given ID.
   * Each entity may have many mentions in the document, so we choose the
   * mention that occurs the most.  We cache the names in the given map
   * for quicker access.
   *
   */
  private String entityIDToString(Map<Integer,String> cache, Map<Integer,Set<String>> entityIDToStrings, Map<Integer,Counter<NERSpan.TYPE>> entityIDToNE, Integer sharedID) {
    assert sharedID != null;
    //    System.out.println("Looking up ID " + sharedID);

    // If we've already found the name for this entity
    if( cache != null && cache.containsKey(sharedID) ) {
      //      System.out.println("Found it in cache " + cache.get(sharedID));
      return cache.get(sharedID);
    }

    else {
      Set<String> argStrings = entityIDToStrings.get(sharedID);
      Counter<NERSpan.TYPE> nercounts = entityIDToNE.get(sharedID);
      //      System.out.println("Found " + argStrings.size() + " possibles");
      String best = getRepresentativeClass(argStrings, nercounts);
      //      System.out.println("best = " + best);

      // Save for later
      if( cache != null ) cache.put(sharedID, best);
      return best;
    }
  }

  public void countDocument(String parsePath, String depsPath, String eventPath, String nerPath) {
    GigawordHandler parseReader = new GigawordProcessed(parsePath);
    GigaDocReader depsReader = new GigaDocReader(depsPath);
    GigaDocReader corefReader = new GigaDocReader(eventPath);
    GigaDocReader nerReader = new GigaDocReader(nerPath);

    List<Tree> trees = TreeOperator.stringsToTrees(parseReader.nextStory());
    depsReader.nextStory(parseReader.currentStory());
    corefReader.nextStory(parseReader.currentStory());
    nerReader.nextStory(parseReader.currentStory());

    // Read the dependencies.
    while( trees != null ) {
      // Skip duplicate stories.
      if( _duplicates.contains(parseReader.currentStory()) ) {
        System.out.println("duplicate " + parseReader.currentStory());
      } else {
        System.out.println(parseReader.currentStory());
        if( _numStories++ % 100 == 0 ) Util.reportMemory();

        // Count the args.
        int storyID = corefReader.currentStoryNum();
        analyzeDocument(trees, depsReader.getDependencies(), corefReader.getEntities(), nerReader.getNER(), storyID);

        //        if( _countByDistance )
        //          countTokenPairsByDistance(trees, depsReader.getDependencies());
        //        else if( _withCoref )
        //          countTokenPairsWithCoref(trees, depsReader.getDependencies(), corefReader.getEntities());
        //        else 
        //          countTokenPairs(trees, depsReader.getDependencies());
      }

      // Advance to the next story.
      trees = TreeOperator.stringsToTrees(parseReader.nextStory());
      depsReader.nextStory(parseReader.currentStory());
      corefReader.nextStory(parseReader.currentStory());
      nerReader.nextStory(parseReader.currentStory());

//      System.out.println(trees.size() + " trees, " + depsReader.getDependencies().size() + " deps");
      //      if( _numStories == 5 ) return;
    }
  }

  public void processData() {
    if( _depsDir.length() > 0 ) {
      File dir = new File(_parseDir);
      String haveLock = "";

      // Directory of files.  *** never tested
      if( dir.isDirectory() ) {
        int numfiles = 0;
        for( String file : Directory.getFilesSorted(_parseDir) ) {
          if( file.contains("parse") ) {
            System.out.println("file: " + file);
            String year = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(8,12) : "noyear";
            String month = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(12,14) : "nomonth";
            String parseFile = _parseDir + File.separator + file;
            String gzsuffix = "";
            if( parseFile.endsWith(".gz") ) gzsuffix = ".gz";
            String depsFile = _depsDir + File.separator + file.substring(0,file.indexOf(".parse")) + ".deps" + gzsuffix;
            String corefFile = _eventsDir + File.separator + file.substring(0,file.indexOf(".parse")) + ".events" + gzsuffix;
            String nerFile = _nerDir + File.separator + file.substring(0,file.indexOf(".parse")) + ".gz.ner" + gzsuffix;

            // Lock this year for processing.
            if( checkLock(haveLock, year) ) {
              haveLock = year;

              // Count the pairs.
              countDocument(parseFile, depsFile, corefFile, nerFile);

              //              writeCountsToFile(_pairCounts, _outputDir + File.separator + _outputPairFile);
              //              writeCountsToFile(_verbCounts, _outputDir + File.separator + _outputVerbFile);
              //              System.exit(-1);

              // Save to file by year (and at half years).
              if( month.equals("06") || month.equals("12") || 
                  (year.equals("1999") && month.equals("11")) || (year.equals("2004") && month.equals("05")) ) {
                System.out.println("saving to disc...");
                String suffix = "-1";
                if( !month.equals("06") && !month.equals("05") ) suffix = "-2";
                // Trim collocations
                trimVerbObjects(_verbCounts);
                // Write to file.
                if( _doPairs )
                  writeCountsToFile(_pairCounts, _outputDir + File.separator + _outputPairFile + "-" + year + suffix);
                writeCountsToFile(_verbCounts, _outputDir + File.separator + _outputVerbFile + "-" + year + suffix);
                // Now clear the memory.
                if( _doPairs ) _pairCounts.clear();
                _verbCounts.clear();
                System.out.println("Cleared memory.");
              }

              Util.reportMemory();
              numfiles++;
            }
          }
        }
      } else {
        // Count the pairs - single files.
        countDocument(_parseDir, _depsDir, _eventsDir, _nerDir);
        // Trim collocations
        trimVerbObjects(_verbCounts);
        // Write to file.
        if( _doPairs )
          writeCountsToFile(_pairCounts, _outputDir + File.separator + _outputPairFile);
        writeCountsToFile(_verbCounts, _outputDir + File.separator + _outputVerbFile);
      }
    }
  }


  /**
   * Returns true if our current locked year matches the new year,
   * or if we can successfully create a file lock for the new year.
   */
  private boolean checkLock(String currentLock, String newYear) {
    // If the current lock already covers this year, good!
    if( currentLock.equals(newYear) )
      return true;

    // Check to see if the year is already locked by another process
    File yearLock = new File(_lockDir + File.separator + "countArgs-" + newYear + ".lock");
    if( !yearLock.exists() ) {
      try {
        // It wasn't locked, so try creating the lock
        boolean created = yearLock.createNewFile();
        if( created )
          return true;
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
    return false;
  }

  /**
   * Trims away collocations (verb-object instances with arguments).  There are tons of
   * these possibilities, so we are pretty harsh when we trim.  Only the most frequent
   * are kept.
   */
  private void trimVerbObjects(Map<String, Map<String,Integer>> counts) {
    Set<String> removal = new HashSet<String>();
    
    // Find entries that don't have many arguments.
    for( Map.Entry<String, Map<String,Integer>> entry : counts.entrySet() ) {
      // If this is a verb-object special collocation.
      if( isObjectString(entry.getKey()) ) {
        Map<String,Integer> args = entry.getValue();
        if( args != null ) {
          if( args.size() < 10 )
            removal.add(entry.getKey());
        }
      }
    }
    
    // Physically remove the keys.
    for( String remove : removal )
      counts.remove(remove);
  }
  
  /**
   * Output the pairCounts table to file.
   * @param path The file path to create and overwrite if it already exists.
   */
  private void writeCountsToFile(Map<String, Map<String,Integer>> counts, String path) {
    // Find the correct path.
    File outfile = new File(path);

    System.out.println("Writing " + counts.size() + " lines to file " + outfile);

    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(outfile));

      // Sort the keys.
      String[] keys = new String[counts.size()];
      keys = counts.keySet().toArray(keys);
      Arrays.sort(keys);

      for( String key : keys ) {
        out.write(key);

        // Write the argument counts
        Map<String,Integer> argCounts = counts.get(key);

        // Sort the counts.
        SortableScore[] scores = new SortableScore[argCounts.size()];
        int xx = 0;
        for( Map.Entry<String,Integer> entry : argCounts.entrySet() )
          scores[xx++] = new SortableScore(entry.getValue(), entry.getKey());
        Arrays.sort(scores);

        for( SortableScore score : scores )
          out.write("\t" + score.key() + "\t" + (int)score.score());
        out.write("\n");
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); System.exit(1); }
    System.out.println("Finished writing");
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      CountArgumentTypes counter = new CountArgumentTypes(args);
      counter.processData();
    }
  }
}
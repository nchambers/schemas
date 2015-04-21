package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.IntCounter;

import nate.BasicEventAnalyzer;
import nate.CountTokenPairs;
import nate.CountVerbDepCorefs;
import nate.IDFMap;
import nate.NERSpan;
import nate.Pair;
import nate.args.VerbArgCounts;
import nate.args.VerbPairArgCounts;
import nate.cluster.ClusterUtil;
import nate.cluster.HierarchicalClustering;
import nate.cluster.SingleLinkSimilarity;
import nate.narrative.EventPairScores;
import nate.narrative.ScoreCache;
import nate.reading.FrameRole.TYPE;
import nate.reading.ir.IRFrameCounts;
import nate.util.Dimensional;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.SortableScore;
import nate.util.Triple;
import nate.util.Util;
import nate.util.WordNet;

/**
 * This class uses a set of tokens (Frame object), and figures out which grammatical
 * slots represent the same roles, linking similar-slots together.
 *
 * -domainidf
 * IDF counts for the domain.
 * 
 * -domaincoref
 * Pairs of tokens' dependencies that were seen with coreferring args in the domain.
 * 
 * -domaindepcounts
 * Counts of how many times each dependency was seen with each token (e.g. n-shipment poss 2 2 1).
 * Created by CountVerbDepCorefs.java.
 * 
 * -generalcoref
 * Pairs of tokens' dependencies that were seen with coreferring args in a general corpus.
 * 
 * -generalall
 *  Pairs of tokens' dependencies that were seen in the same doc in a general corpus.
 *  
 *  -argcounts
 *  All arg heads of token dependencies that were seen in the general corpus.
 *  
 *  -pairargcounts
 *  All arg heads that were coreferring between two slots in the general corpus.
 *  
 */
public class SlotInducer {
  IDFMap _domainIDF = null;
  IDFMap _generalIDF = null;
  
  CountTokenPairs _domainCorefPairs;
  CountTokenPairs _generalAllPairs;
  CountTokenPairs _generalCorefPairs;
  VerbArgCounts _domainSlotArgCounts;

  Map<Integer,IRFrameCounts> _frameIRCounts;
//  public VerbArgCounts _kidnapIRSlotArgCounts;
//  public CountTokenPairs _kidnapIRCorefPairs;
//  public VerbArgCounts _bombingIRSlotArgCounts;
//  public CountTokenPairs _bombingIRCorefPairs;
  
  Map<String,SlotVectorCache> _argVectorCaches;
  // Frame ID to its slot-type-cache
  Map<Integer,SlotTypeCache> _slotCaches;
  // Frame ID to its word classes (sets of learned synonyms)
  Map<Integer,WordClasses> _wordClasses;
  
  VerbArgCounts _generalSlotArgCounts;
  VerbPairArgCounts _generalPairArgCounts;
  CountVerbDepCorefs _domainDeps;
  HandleParameters _params;
  DomainVerbDetector _detector; // just used to get IDF scores
  String _cacheDir = "/jude0/scr1/natec/";

  String _parseFile;
  String _depsFile;
  String _corefFile;
  WordNet _wordnet;

  // Verbs to a map of arguments.  Argument is true if it occurs as the subject.
  // False is the object.  If not present, then we don't know.
  Map<String,Map<String,Boolean>> _alternations;
  
  final int _domainCountCutoff = 1;

  public SlotInducer(HandleParameters params, DomainVerbDetector detector, WordNet wordnet) {
    _detector = detector;
    _domainIDF = detector._domainIDF;
    _generalIDF = detector._generalIDF;
    _wordnet = wordnet;
    
    _params = params;
    _params.fromFile("reading.properties");
    initFlags(_params);
    initAlternations();
  }

  public SlotInducer(String[] args) {
    _params = new HandleParameters(args);
    _params.fromFile("reading.properties");
    initFlags(_params);
    initAlternations();
  }

  public void initFlags(HandleParameters params) {
//    _generalAllPairs = new CountTokenPairs();
//    _generalAllPairs.intsFromFile(params.get("-generalall"), _domainIDF, false);
//    System.out.println("loaded general corpus all pairs.");
//    BasicEventAnalyzer.reportMemory();

//    _generalCorefPairs = new CountTokenPairs();
//    _generalCorefPairs.intsFromFile(params.get("-generalcoref"), _domainIDF, false);
//    System.out.println("loaded general corpus coref pairs.");
//    BasicEventAnalyzer.reportMemory();

    System.out.println("idf size " + _domainIDF.getWords().size());
    _domainSlotArgCounts = new VerbArgCounts(params.get("-domainargcounts"), 1);
    _generalSlotArgCounts = new VerbArgCounts(params.get("-argcounts"), 1, _domainSlotArgCounts.getAllSlotTokens());
    _generalPairArgCounts = new VerbPairArgCounts(params.get("-pairargcounts"), 1, _domainSlotArgCounts.getAllSlotTokens());
    System.out.println("loaded arg type counts");
    System.out.println("domainslotcounts size " + _domainSlotArgCounts.size());
    System.out.println("generalslotcounts size " + _generalSlotArgCounts.size());
    System.out.println("generalpairslotcounts size " + _generalPairArgCounts.size());

    _domainCorefPairs = new CountTokenPairs();
    _domainCorefPairs.intsFromFile(params.get("-domaincoref"), _domainSlotArgCounts.getAllSlotTokens(), true);
    System.out.println("loaded domain corpus coref pairs!");
    Util.reportMemory();

    _domainDeps = new CountVerbDepCorefs();
    _domainDeps.fromFile(params.get("-domaindepcounts"), _domainSlotArgCounts.getAllSlotTokens());
    System.out.println("loaded domain dep counts.");
    Util.reportMemory();

    // Counts from a large corpus, selected by IR to be similar to learned frames.
//    if( params.hasFlag("-kidnap-argcounts") ) {
//      _kidnapIRSlotArgCounts = new VerbArgCounts(params.get("-kidnap-argcounts"), 1);
//      _kidnapIRSlotArgCounts.addCounts(_domainSlotArgCounts);
//    }
//    if( params.hasFlag("-bombing-argcounts") ) {
//      _bombingIRSlotArgCounts = new VerbArgCounts(params.get("-bombing-argcounts"), 1);
//      _bombingIRSlotArgCounts.addCounts(_domainSlotArgCounts);
//    }
//    if( params.hasFlag("-kidnap-coref") ) {
//      _kidnapIRCorefPairs = new CountTokenPairs();
//      _kidnapIRCorefPairs.intsFromFile(params.get("-kidnap-coref"), _domainSlotArgCounts.getAllSlotTokens(), true);
//      _kidnapIRCorefPairs.addCountsInt(_domainCorefPairs);
//    }
//    if( params.hasFlag("-bombing-coref") ) {
//      _bombingIRCorefPairs = new CountTokenPairs();
//      _bombingIRCorefPairs.intsFromFile(params.get("-bombing-coref"), _domainSlotArgCounts.getAllSlotTokens(), true);
//      _bombingIRCorefPairs.addCountsInt(_domainCorefPairs);
//    }
    
    // We might have already loaded WordNet.
    if( _wordnet == null )
      _wordnet = new WordNet(params.get("-wordnet"));

    _parseFile = params.get("-parsed");
    _depsFile = params.get("-deps");
    _corefFile = params.get("-events");
  }

  /*
  private String createCachePath(String cachename) {
    cachename = cachename.replaceAll(File.separator, "-");
    return _cacheDir + File.separator + cachename + ".cache";
  }

  /**
   * Save the arg counts to file for quick lookup on a later run...
   * @param cacheName name of cache
   * @param argCounts counts of seen arguments

  private void cacheTokenArgs(String cacheName, Map<String,Integer> argCounts) {
    String path = createCachePath(cacheName);
    System.out.println("Saving to cache: " + path);
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(path));
      for( Map.Entry<String,Integer> entry : argCounts.entrySet() )
        out.write(entry.getKey() + "\t" + entry.getValue() + "\n");
      out.close();
    } catch( IOException ex ) { ex.printStackTrace(); }
  }

  /**
   * Check if a cached file exists with the given name, read the args from it.
   * @param cacheName name of cache

  private Map<String,Integer> retrieveCachedTokenArgs(String cacheName) {
    String path = createCachePath(cacheName);
    File file = new File(path);
    if( file.exists() ) {
      System.out.println("Reading cache: " + path);
      Map<String,Integer> argCounts = new HashMap<String, Integer>();
      try {
        BufferedReader in = new BufferedReader(new FileReader(path));
        String line = in.readLine();
        while( line != null ) {
          int tab = line.indexOf('\t');
          String arg = line.substring(0,tab);
          Integer count = Integer.valueOf(line.substring(tab+1));
          argCounts.put(arg, count);
          line = in.readLine();
        }
        in.close();
      } catch (Exception e) {
        System.out.println("Error reading cached file: " + path);
        e.printStackTrace();
        System.exit(-1);
      }
      return argCounts;
    }
    else {
      System.out.println("No cache for " + path);
      return null;
    }
  }
   */

  private void initAlternations() {
    _alternations = new HashMap<String,Map<String,Boolean>>();
    
    Map<String,Boolean> argmap = new HashMap<String,Boolean>();
    argmap.put("bomb", true);
    argmap.put("bomber", true);
    argmap.put("attacker", true);
    argmap.put("militant", true);
    _alternations.put("v-blow_up", argmap);

    argmap = new HashMap<String,Boolean>();
    argmap.put("bomb", true);
    _alternations.put("v-destroy", argmap);

    // Everything is in the subject....object rare so it loses.
    argmap = new HashMap<String,Boolean>();
    argmap.put("bomb", true);
    argmap.put("car", true);
    argmap.put("vehicle", true);
    argmap.put("device", true);
    argmap.put("truck", true);
    _alternations.put("v-explode", argmap);
  }
  
  private Set<String> rootsFromSlots(Set<String> slots) {
    Set<String> bases = new HashSet<String>();
    for( String slot : slots ) {
      String base = slot.substring(0, slot.indexOf(':'));
      bases.add(base);
    }
    return bases;
  }
  
  private void learnAlternations(VerbArgCounts counts) {
    System.out.println("Learning alternations top.");
    _alternations.clear();
    
    Set<String> roots = rootsFromSlots(counts.getAllSlots());
    for( String root : roots ) {
      String subj = VerbArgCounts.buildKey(root, "s");
      String obj = VerbArgCounts.buildKey(root, "o");
      
      // All args in the subject slot.
      Map<String,Integer> subjargcounts = counts.getArgsForSlot(subj);
      Map<String,Integer> objargcounts = counts.getArgsForSlot(obj);
      if( subjargcounts != null && objargcounts != null ) {
        for( Map.Entry<String, Integer> entry : subjargcounts.entrySet() ) {
          Integer subjcount = entry.getValue();
          Integer objcount = objargcounts.get(entry.getKey());
          if( subjcount != null && objcount != null ) {
            if( subjcount > 100 || objcount > 100 ) {
              float diff = (subjcount > objcount ? ((float)subjcount / (float)objcount) : ((float)objcount / (float)subjcount));
              // Difference is great, let's set an alternation.
              if( diff >= 1.5f ) {
                Map<String,Boolean> alts = _alternations.get(root);
                if( alts == null ) alts = new HashMap<String,Boolean>();
                Boolean bool = (subjcount > objcount);
                System.out.println("alter " + root + " " + entry.getKey() + " " + bool);
                alts.put(entry.getKey(), bool);
                _alternations.put(root, alts);
              }
            }
          }
        }
      }
    }
    System.out.println("Learned Alternations numverbs = " + _alternations.size());
  }

    private VerbArgCounts getIRArgCounts(int frameid) {
	if( _frameIRCounts != null && _frameIRCounts.containsKey(frameid) )
	    return _frameIRCounts.get(frameid).slotArgCounts();
	else
	    return _domainSlotArgCounts;
    }

    private CountTokenPairs getIRCorefCounts(int frameid) {
	if( _frameIRCounts != null && _frameIRCounts.containsKey(frameid) )
	    return _frameIRCounts.get(frameid).pairCorefCounts();
	else
	    return _domainCorefPairs;
    }

    private IDFMap getIRIDF(int frameid) {
	if( _frameIRCounts != null && _frameIRCounts.containsKey(frameid) )
	    return _frameIRCounts.get(frameid).idf();
	else
	    return _domainIDF;
    }
  
  private void setAlternations(Frame frame) {
    System.out.println("Set alternations top.");
    VerbArgCounts argcounts = getIRArgCounts(frame.getID());
    learnAlternations(argcounts);
    
//    System.out.println("*Altered v-blow_up:s " + argcounts.getArgsForSlot("v-blow_up:s"));
//    System.out.println("*Altered v-blow_up:o " + argcounts.getArgsForSlot("v-blow_up:o"));

    for( String verb : _alternations.keySet() ) {
      String sslot = VerbArgCounts.buildKey(verb, "s");
      String oslot = VerbArgCounts.buildKey(verb, "o");
      for( Map.Entry<String, Boolean> entry : _alternations.get(verb).entrySet() ) {
        if( entry.getValue() )
          argcounts.removeArgFromSlot(oslot, entry.getKey());
        if( !entry.getValue() )
          argcounts.removeArgFromSlot(sslot, entry.getKey());
      }
    }
    System.out.println("Altered v-blow_up:s " + argcounts.getArgsForSlot("v-blow_up:s"));
    System.out.println("Altered v-blow_up:o " + argcounts.getArgsForSlot("v-blow_up:o"));
    System.out.println("Altered v-destroy:s " + argcounts.getArgsForSlot("v-destroy:s"));
    System.out.println("Altered v-destroy:o " + argcounts.getArgsForSlot("v-destroy:o"));
    System.out.println("Altered v-damage:s " + argcounts.getArgsForSlot("v-damage:s"));
    System.out.println("Altered v-damage:o " + argcounts.getArgsForSlot("v-damage:o"));
  }
  
  public void setIRFrameCounts(Map<Integer,IRFrameCounts> frameIRCounts) {
    _frameIRCounts = frameIRCounts;
  }

  /**
   * Get the average cosine score of each relation with key1, mapped to its best
   * scoring relation in the set of key2's relations.
   * @return The average cosine score of the relations mapped between keys.
   */
  public float alignSlots(String key1, String key2) {
    float bestsum = 0.0f;
    int numrelns = 0;
    Collection<String> relns1 = _domainDeps.getRelns(key1);

    // All relations with the first key.
    if( relns1 != null ) {
      for( String reln1 : relns1 ) {
        if( _domainDeps.getDocCount(key1, reln1) > 5 ) {
          // e.g. v-kidnap:s
          String slot1 = CountTokenPairs.attachRelation(key1, reln1);
          Map<String,Integer> args1 = _domainSlotArgCounts.getArgsForSlot(slot1);
          float best1 = 0.0f;
//          String beststr = "";

          // All relations with the second key.
          Collection<String> relns2 = _domainDeps.getRelns(key2);
          if( relns2 != null ) {
            for( String reln2 : relns2 ) {
              if( _domainDeps.getDocCount(key2, reln2) > 5 ) {

                String slot2 = CountTokenPairs.attachRelation(key2, reln2);
                Map<String,Integer> args2 = _domainSlotArgCounts.getArgsForSlot(slot2);

                // Cosine the two arg vectors.
                float cosine = Dimensional.cosineInteger(args1, args2);
                if( cosine > best1 ) {
                  best1 = cosine;
//                  beststr = reln2;
                }
              }
            }
          }
          bestsum += best1;
          //          System.out.println("  best " + reln1 + " with " + beststr + " = " + best1);
          numrelns++;
        }
      }
    }

    if( numrelns == 0 ) return 0.0f; 
    else return bestsum / (float)numrelns;
  }

  /**
   * Finds all arguments to the given tokens in the domain's text.
   * Uses the precomputed counts of arguments. 
   * @param tokens All arguments seen with the tokens. (e.g. token is 'v-kidnap')
   */
  public Set<String> argsOfTokens(Collection<String> tokens, VerbArgCounts argCounts) {
    Set<String> allArgs = new HashSet<String>();

    for( String token : tokens ) {
      Collection<String> slots = argCounts.keysThatStartWith(token);
      for( String slot : slots ) {
        Set<String> slotArgs = argCounts.getArgsForSlot(slot).keySet();
        allArgs.addAll(slotArgs);
      }
    }
    return allArgs;
  }

  public SortableScore[] argsOfTokensSorted(Collection<String> tokens, VerbArgCounts argCounts) {
    Map<String,Float> probSums = new HashMap<String,Float>();

    for( String token : tokens ) {
      Collection<String> slots = argCounts.keysThatStartWith(token);
      for( String slot : slots ) {
        //        System.out.println("slot " + slot);
        Set<String> slotArgs = argCounts.getArgsForSlot(slot).keySet();
        // Get total count of all args.
        int totalcount = 0;
        for( String slotarg : slotArgs ) {
          int count = argCounts.getCount(slot, slotarg);
          totalcount += count;
        }
        // Probability of each arg, given the slot.
        for( String slotarg : slotArgs ) {
          int count = argCounts.getCount(slot, slotarg);
          float prob = (float)count / (float)totalcount;
          //          System.out.println("  " + slotarg + "\t" + count + "/" + totalcount + "=" + prob);
          Util.incrementCount(probSums, slotarg, prob);
        }
      }
    }

    // Sort and return.
    SortableScore[] scores = new SortableScore[probSums.size()];
    int i = 0;
    for( String arg : probSums.keySet() ) {
      scores[i++]= new SortableScore(probSums.get(arg), arg);
    }
    Arrays.sort(scores);

    return scores;
  }

  private double likelihoodRatio(String key) {
    // NER tags get a low-ish score.
    if( key.equals(NERSpan.TYPE.LOCATION.toString()) ||
        key.equals(NERSpan.TYPE.PERSON.toString()) ||
        key.equals(NERSpan.TYPE.ORGANIZATION.toString()) )
      return 3.0;
    
    double likelihoodRatio = DomainVerbDetector.likelihoodRatio("n-" + key, _domainIDF, _generalIDF);
    if( Double.isNaN(likelihoodRatio) )
      likelihoodRatio = 15.0;
    return Math.min(likelihoodRatio, 15.0);
  }
  
  /**
   * Given the slots in an already clustered "role", score all of the possible arguments
   * that fill the role.
   * @param slots The slots in the cluster (e.g. v-kidnap:s)
   * @param domainArgs A set of tokens that are in the domain and seen with these predicates.
   * @param frame The frame that the slots are members of.
   */
  private SortableScore[] mainArgsOfSlots(Collection<String> slots, Set<String> domainArgs, FrameRole.TYPE type, Frame frame) {
    Map<String,Float> allcounts = new HashMap<String,Float>();
    int numslots = slots.size();
    float pairLambda = 0.0f;
    float domainLambda = 1.0f;
    float generalLambda = 1.0f - pairLambda - domainLambda;
    //    P(arg | slots) = P(arg | slot) * ...

    System.out.println("mainArgsOfSlots " + slots);
    
    // Set the IR counts if relevant to this frame.
    VerbArgCounts domainArgCounts = _domainSlotArgCounts;
    domainArgCounts = getIRArgCounts(frame.getID());
//    if( frame.contains("v-kidnap") ) domainArgCounts = _kidnapIRSlotArgCounts;
//    else if( frame.contains("v-explode") ) domainArgCounts = _bombingIRSlotArgCounts;
    
    // Sum argument counts seen with pairs.
    if( pairLambda > 0.0f ) {
      String[] slotarr = new String[numslots]; 
      int i = 0;
      for( String slot : slots ) slotarr[i++] = slot;
      float sum = 0.0f;
      for( i = 0; i < numslots-1; i++ ) {
        String tokeni = CountTokenPairs.detachToken(slotarr[i]);
        String relni = CountTokenPairs.detachRelation(slotarr[i]);
        for( int j = 0; j < numslots; j++ ) {
          String tokenj = CountTokenPairs.detachToken(slotarr[j]);
          String relnj = CountTokenPairs.detachRelation(slotarr[j]);
          Map<String,Integer> counts = _generalPairArgCounts.getArgsForPair(VerbPairArgCounts.buildKey(tokeni, relni, tokenj, relnj));
          if( counts != null ) {
            sum += (float)Dimensional.sumValues(counts);
            for( Map.Entry<String, Integer> entry : counts.entrySet() ) {
              if( domainArgs.contains(entry.getKey()) )
                Util.incrementCount(allcounts, entry.getKey(), (float)entry.getValue());
            }
          }
        }
      }
      Dimensional.normalize(allcounts, sum * pairLambda);
    }

    // General Corpus arg counts.
    if( generalLambda > 0.0f ) {
      for( String slot : slots ) {
        Map<String,Integer> counts = _generalSlotArgCounts.getArgsForSlot(slot);
        if( counts != null ) {
          float sum = (float)Dimensional.sumValues(counts);
          for( Map.Entry<String, Integer> entry : counts.entrySet() ) {
            if( domainArgs.contains(entry.getKey()) ) {
              Util.incrementCount(allcounts, entry.getKey(), (float)entry.getValue() / sum * generalLambda);
              //            System.out.println("**" + entry.getKey() + " " + ((float)entry.getValue() / sum * generalLambda));
            }
          }
        }
      }
    }

    SlotTypeCache slotTypeCache = new SlotTypeCache(_wordnet);
    
    // Domain arg counts.
    Map<String,Integer> totalcounts = new HashMap<String,Integer>();
    for( String slot : slots ) {
      Map<String,Integer> counts = domainArgCounts.getArgsForSlot(slot);
      // Remove arguments that do not match this role's core type.
      counts = slotTypeCache.trimArgsByType(type, slot, counts);
      if( counts != null ) {
        for( Map.Entry<String, Integer> entry : counts.entrySet() )
          Util.incrementCount(totalcounts, entry.getKey(), entry.getValue());
      }
    }
    double sum = Util.sumValues(totalcounts); 
    
    // Now add the domain arg counts    
    for( Map.Entry<String, Integer> entry : totalcounts.entrySet() ) {
      if( domainArgs.contains(entry.getKey()) ) {
        double likelihoodRatio = likelihoodRatio(entry.getKey());
        double weightedCount = (double)entry.getValue() * likelihoodRatio;
        //            double weightedCount = (double)entry.getValue();
        float prob = (float)(weightedCount / sum);
        Util.incrementCount(allcounts, entry.getKey(), prob * domainLambda);
//        System.out.printf("*arg %s count=%d ratio=%.2f weighted=%.4f prob=%.3f\n", entry.getKey(), entry.getValue(), likelihoodRatio, weightedCount, prob);
      }
    }

    Dimensional.normalize(allcounts, (float)Dimensional.sumValuesFloat(allcounts));
    
    // Sort the total argument counts.
    SortableScore[] scores = new SortableScore[allcounts.size()];
    int i = 0;
    for( Map.Entry<String,Float> entry : allcounts.entrySet() ) {
      scores[i++] = new SortableScore(entry.getValue(), entry.getKey());
    }
    Arrays.sort(scores);

    return scores;
  }

  /**
   * Scores the likelihood that the two slots are filled by the same role.
   * @param slot Slot 1 of the pair
   * @param other Slot 2 of the pair
   * @param corefPairs The counts of slot pairs and how often they were seen coreferring.
   */
  private double scorePair(String slot, String other, CountTokenPairs corefPairs) {
    double best = 0.0f;

    // First see if the domain showed them coreferring.
    int count = corefPairs.getCountInteger(slot, other);
//    if( count >= _domainCountCutoff && count > best )
//      best = (double)count / 2.0;

    if( count >= _domainCountCutoff && count > best ) {
      double doccount1 = _domainIDF.getDocCount(slot.substring(0, slot.indexOf(':')));
      double doccount2 = _domainIDF.getDocCount(other.substring(0, other.indexOf(':')));
      best = (double)count / (doccount1 > doccount2 ? doccount2 : doccount1);
//      System.out.println("best " + best + " doc1 " + doccount1 + " doc2 " + doccount2 + " coref " + count);
    }
    
    // Now check the general corpus' conditional probability of coreferring.
    //    int generalCoref = _generalCorefPairs.getCountInteger(slot, other);
    //    int generalAll = _generalAllPairs.getCountInteger(slot, other);
    //    float prob = 0.0f;
    //    if( generalAll > 0 ) 
    //      prob = (float)generalCoref / (float)generalAll;
    //    if( prob > best )
    //      best = prob;

    return best;
  }

  private Map<String,Integer> removeNELabels(Map<String,Integer> counts) {
    Map<String,Integer> clone = new HashMap<String, Integer>();
    for( Map.Entry<String,Integer> entry : counts.entrySet() ) {
      if( !entry.getKey().equals("PERSON") && 
          !entry.getKey().equalsIgnoreCase("*properson*") && !entry.getKey().equalsIgnoreCase("*pro*") &&
          !entry.getKey().equals("ORGANIZATION") && !entry.getKey().equals("LOCATION") )
        clone.put(entry.getKey(), entry.getValue());
    }
    return clone;    
  }
  
//  private Map<String,Integer> removePerson(Map<String,Integer> counts) {
//    Map<String,Integer> clone = new HashMap<String, Integer>();
//    for( Map.Entry<String,Integer> entry : counts.entrySet() ) {
//      if( !entry.getKey().equalsIgnoreCase("PERSON") && !entry.getKey().equalsIgnoreCase("*properson*") )
//        clone.put(entry.getKey(), entry.getValue());
//    }
//    return clone;
//  }
//
//  /**
//   * Make a new map that is identical to the given one, but with all entries removed
//   * whose keys are not in the keepKeys set.
//   * @param themap the map to copy
//   * @param keepKeys the keys to keep from the map
//   * @return a new hashmap with only the keepKeys
//   */
//  private Map<String,Integer> trimMap(Map<String,Integer> themap, Set<String> keepKeys) {
//    Map<String,Integer> newmap = new HashMap<String, Integer>();
//    if( themap != null && themap.size() > 0 ) {
//      for( Map.Entry<String,Integer> entry : themap.entrySet() ) {
//        if( keepKeys.contains(entry.getKey()) )
//          newmap.put(entry.getKey(), entry.getValue());
//      }
//    }
//    return newmap;
//  }
  
  private Map<String,Integer> trimLowOccurrences(Map<String,Integer> themap, int min) {
    Map<String,Integer> newmap = new HashMap<String, Integer>();
    if( themap != null && themap.size() > 0 ) {
      for( Map.Entry<String,Integer> entry : themap.entrySet() ) {
        if( entry.getValue() >= min )
          newmap.put(entry.getKey(), entry.getValue());
      }
    }
    return newmap;
  }
  
  /**
   * Convert a counts map into a tf-idf map.
   */
  private Map<String,Integer> multiplyByIDF(Map<String,Integer> counts, IDFMap idfs) {
    Map<String,Integer> newcounts = new HashMap<String,Integer>();
    if( counts == null ) return newcounts;
    
    for( Map.Entry<String,Integer> entry : counts.entrySet() ) {
      double idfscore = 10.0;
      String key = "n-" + entry.getKey();
      if( idfs.getDocCount(key) > 0 )
        idfscore = idfs.get(key);
      
      double newscore = (double)entry.getValue() * idfscore;
      newcounts.put(entry.getKey(), (int)newscore);
    }
    return newcounts;
  }
  
  /**
   * Given an array of predicates, find all relevant slots in those predicates, and
   * then match the slots to the given frame's current roles.
   * @param slots An array of tokens (e.g. v-arrest)
   * @param frame A frame with roles already filled in.
   */
  public void addTokensToRoles(Collection<String> tokens, Frame frame, boolean cutoffLowScores) {
    if( frame.getNumRoles() > 0 ) {
      Map<String,FrameRole> mapping = new HashMap<String, FrameRole>();

      // Build the pairwise score cache for this frame and the given tokens.
//      List<String> frameslots = frame.getRoleSlots();
//      Set<String> frametokens = new HashSet<String>(frame.tokens());
//      System.out.println("slotinducer addTokensToRoles with " + frameslots.size() + " slots and " + frametokens.size() + " tokens.");
//      for( String token : tokens ) {
//        List<String> slots = getDesiredSlots(token);
//        if( slots != null ) frameslots.addAll(slots);
//        frametokens.add(token);    
//      }
//      EventPairScores cache = buildScoreCache(frametokens, frameslots, FrameRole.ALL, true);

      // Add the slots to the roles.
      for( String token : tokens ) {
        System.out.println("Adding token " + token);
        
        // Build the pairwise score cache for this frame and the given tokens.
        List<String> frameslots = frame.getRoleSlots();
        Set<String> frametokens = new HashSet<String>(frame.tokens());
        System.out.println("slotinducer addTokensToRoles with " + frameslots.size() + " slots and " + frametokens.size() + " tokens.");
        List<String> tokenSlots = getDesiredSlots(token);
        
        // We already added this token to the frame (just saves time...).
        if( tokenSlots == null || tokenSlots.size() == 0 || frameslots.containsAll(tokenSlots) ) {
          System.out.println("Slots already in roles: " + tokenSlots);
        }
        else {
          System.out.println("addTokensToRoles desired slots = " + tokenSlots);
          frametokens.add(token);
          // ACL paper built this beforehand, no type filtering.
//          EventPairScores cache = buildScoreCache(frametokens, frame.getRoleSlots(), tokenSlots, FrameRole.ALL, true, false);

          // Now add the slots of this token to the roles.
          Map<String,FrameRole> slotMap = addSlotsToRoles(tokenSlots, frame, cutoffLowScores);
          for( Map.Entry<String, FrameRole> entry : slotMap.entrySet() )
            mapping.put(entry.getKey(), entry.getValue());
        }
      }
      
      // Add the slots to the roles.
      for( Map.Entry<String, FrameRole> entry : mapping.entrySet() )
        entry.getValue().addSlot(entry.getKey());
    } else System.out.println("addTokensToRoles got frame with no roles.");
  }
    
  /**
   * Finds the best matching role for each slot based on their clustering scores.
   * @param mainToken The token whose slots we are adding.
   * @param slot The list of slots for the token: e.g. v-hurl:o
   * @param frame A frame with roles already filled in.
   * @param cutoffLowScores If true, don't add slots with low scores.  If false, adds all of them.
   */
  private Map<String,FrameRole> addSlotsToRoles(Collection<String> slots, Frame frame, boolean cutoffLowScores) {
    Map<String,FrameRole> mapping = new HashMap<String, FrameRole>();
    
    if( frame.getRoles() == null ) return mapping;
    List<String> frameslots = frame.getRoleSlots();
    
    for( String slot : slots ) {
      if( frameslots.contains(slot) ) {
        System.out.println("addSlotsToRoles(): slot " + slot + " already in a frame role");
        continue;
      }
      
      List<String> slotWrapper = new ArrayList<String>();
      slotWrapper.add(slot);
      // Add the slot's token so the cache includes its pair scores.
      Set<String> frametokens = new HashSet<String>(frame.tokens());
      frametokens.add(slot.substring(0,slot.indexOf(':')));
      
      // Categorize this slot's main type (person, location, other).
      Map<String,Integer> args = _domainSlotArgCounts.getArgsForSlot(slot);
      boolean isPerson = false, isLocation = false, isOther = false, isEvent = false, isPhysObject = false;
      if( SlotTypeCache.isPerson(args, _wordnet) ) isPerson = true;
      if( SlotTypeCache.isLocation(args, _wordnet) ) isLocation = true;
      if( SlotTypeCache.isPhysObject(args, _wordnet) ) isPhysObject = true;
      if( SlotTypeCache.isOther(args, _wordnet) ) isOther = true;
      if( SlotTypeCache.isEvent(args, _wordnet) ) isEvent = true;
      System.out.println("slot " + slot + "\tper=" + isPerson + "\tloc=" + isLocation + "\tev=" + isEvent + "\tphys=" + isPhysObject + "\toth=" + isOther);
      FrameRole best = null;
      float bestscore = -1.0f;
      
      Map<FrameRole.TYPE,ScoreCache> caches = new HashMap<FrameRole.TYPE,ScoreCache>();
      
      // Find the best role.
      for( FrameRole role : frame.getRoles() ) {
        if( (role._type == FrameRole.TYPE.PERSON && isPerson) ||
            (role._type == FrameRole.TYPE.LOCATION && isLocation) ||
            (role._type == FrameRole.TYPE.EVENT && isEvent) ||
            (role._type == FrameRole.TYPE.PHYSOBJECT && isPhysObject) ||
            (role._type == FrameRole.TYPE.OTHER && isOther) ) {

          // Build the type-specific cache (removes non-typed args from the cache similarity scores).
          // NOT IN THE ACL SUBMISSION.
          ScoreCache cache = caches.get(role._type);
          if( cache == null ) {
            System.out.println("  building cache for " + role._type + " slot " + slotWrapper);
            cache = buildScoreCache(frame.getID(), frametokens, frame.getRoleSlots(), slotWrapper, role._type, true, false);
            caches.put(role._type, cache);
          }
          
          System.out.println("  checking similarity with role: " + role.getSlots());
          float score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(slotWrapper, role.getSlots(), cache, true, false);
          System.out.println("  similarity score = " + score);
          if( score > bestscore ) {
            bestscore = score;
            best = role;
          }
        }
      }
      
      
      // ACL cutoff based on coref * cosine(arg1, arg2)
//      double cutoff = .0001;
      double cutoff = .3;
      
      // Add the slot to the role if it's score is high enough.
      if( bestscore >= cutoff ) System.out.print("*");
      System.out.println("slot " + slot + "\tscore=" + bestscore + "\tmatches role:\t" + best);
      if( best != null && (bestscore >= cutoff || (!cutoffLowScores && bestscore > 0.0)) ) {
        mapping.put(slot, best);
        System.out.println("- adding slot " + slot);
      }
      else if( slot.startsWith("v-") && slot.endsWith(":s") && bestscore > 0.0 ) {
        mapping.put(slot, best);
        System.out.println("- adding SUBJ slot " + slot);        
      }
    }
    
    // Add the slots to the roles.
    return mapping;
//    for( Map.Entry<String, FrameRole> entry : mapping.entrySet() )
//      entry.getValue().addSlot(entry.getKey());
  }
  
  /**
   * Calls scorePair() * cosine() on every pair and stores the scores in a cache (for clustering).
   * @param frameid The ID of the frame.
   * @param tokens The tokens we are scoring (e.g. v-kidnap)
   * @params slots The slots we want scores for (e.g. v-kidnap:s)
   * @param type The type of slots to score (ALL, PERSON, LOCATION, OTHER), ignore all others.
   * @param useDomainArgs If true, then similarity of slots only uses the args seen in the domain.                       
   */
  private EventPairScores buildScoreCache(int frameid, Collection<String> tokens, List<String> slots, FrameRole.TYPE type, boolean useDomainArgs) {
    return buildScoreCache(frameid, tokens, slots, null, type, useDomainArgs, false);
  }
  /**
   * @param tokens The tokens we are scoring (e.g. v-kidnap)
   * @params slots The slots in the main cluster (e.g. v-kidnap:s)
   * @params slotsOptional The slots in the secondary cluster. If null, then we just do all pairwise 
   *                       in the main cluster. If present, we do all pairs crossing the two clusters.
   * @param type The type of slots to score (ALL, PERSON, LOCATION, OTHER), ignore all others.
   * @param useDomainArgs If true, then similarity of slots only uses the args seen in the domain.                       
   * @param argSimOnly If true, then similarity of slots is only the cosine between their argument vectors.
   */
  private EventPairScores buildScoreCache(int frameid, Collection<String> tokens, List<String> slots, List<String> slotsOptional, FrameRole.TYPE type, boolean useDomainArgs, boolean argSimOnly) {
    assert tokens != null : "buildScoreCache() tokens null";
    assert slots != null : "buildScoreCache() slots null";
    VerbArgCounts domainSlotArgCounts = _domainSlotArgCounts;
    CountTokenPairs domainCorefPairs = _domainCorefPairs;
    
    if( _frameIRCounts != null && _frameIRCounts.containsKey(frameid) ) {
      VerbArgCounts argcounts = _frameIRCounts.get(frameid).slotArgCounts();
      CountTokenPairs corefs = _frameIRCounts.get(frameid).pairCorefCounts();
      if( argcounts != null ) domainSlotArgCounts = argcounts;
      if( corefs != null ) domainCorefPairs = corefs;
    }
 // else {
 //      System.err.println("IR lookup failed for frame " + frameid);
 //      System.exit(-1);
 //    }
    
//    if( _kidnapIRSlotArgCounts != null && tokens.contains("v-kidnap") ) {
//      domainSlotArgCounts = _kidnapIRSlotArgCounts;
//      domainCorefPairs = _kidnapIRCorefPairs;
//      System.out.println("Using kidnap IR arg counts (size " + domainSlotArgCounts.size() + ")");
//    }
//    else if( _bombingIRSlotArgCounts != null && tokens.contains("n-explosion") ) {
//      domainSlotArgCounts = _bombingIRSlotArgCounts;
//      domainCorefPairs = _bombingIRCorefPairs;
//      System.out.println("Using bombing IR arg counts (size " + domainSlotArgCounts.size() + ")");
//    }
    
    // Cache of individual slots' type results (is it a PERSON?).
    if( _slotCaches == null ) _slotCaches = new HashMap<Integer,SlotTypeCache>();
    SlotTypeCache slotCache = _slotCaches.get(frameid);
    if( slotCache == null ) {
      slotCache = new SlotTypeCache(_wordnet);
      _slotCaches.put(frameid, slotCache);
    }
    
    EventPairScores cache = new EventPairScores();
    Set<String> args = argsOfTokens(tokens, _domainSlotArgCounts);
    System.out.println("buildScoreCache() " + type + " with " + tokens.size() + " tokens, " + slots.size() + " slots, " + args.size() + " args + useDomainArgs=" + useDomainArgs);
    System.out.println("  tokens: " + tokens);

    int iloopEnd = (slotsOptional == null ? slots.size()-1 : slots.size());
    int jloopEnd = (slotsOptional == null ? slots.size() : slotsOptional.size());
    System.out.println("  iloopEnd " + iloopEnd + " jloopEnd " + jloopEnd);
    
    List<String> allslots = new ArrayList<String>(slots);
    if( slotsOptional != null ) allslots.addAll(slotsOptional);

    // Get the cache of argument vectors we already built.
    if( _argVectorCaches == null ) _argVectorCaches = new HashMap<String,SlotVectorCache>();
    SlotVectorCache slotsCache = _argVectorCaches.get(frameid + "--" + type);
    if( slotsCache == null ) {
      slotsCache = new SlotVectorCache();
      _argVectorCaches.put(frameid + "--" + type, slotsCache);
//      System.out.println("New slots cache for " + frameid + "--" + type);
    }
    Util.reportMemory();
    
    // Create the argument vectors for each slot.
    for( String sloti : allslots ) {//for( int i = 0; i < slots.size(); i++ ) {
      // Argument vectors.
      Map<String,Integer> argVeci = slotsCache.getArgumentVector(sloti);
      if( argVeci == null ) {
        argVeci = domainSlotArgCounts.getArgsForSlot(sloti);
        if( argVeci != null ) argVeci = removeNELabels(argVeci);
        if( argVeci != null ) argVeci = trimLowOccurrences(argVeci, 2);
        argVeci = slotCache.trimArgsByType(type, sloti, argVeci);
        argVeci = multiplyByIDF(argVeci, _generalIDF);
        argVeci = substituteClasses(argVeci, frameid, type);
        slotsCache.setArgumentVector(sloti, argVeci);
//        System.out.println("Now cached " + sloti + " as " + type);
      }
//      else System.out.println("Got cached " + sloti + " as " + type);
      
      // Coref vectors.
      Map<String,Integer> corefVeci = slotsCache.getCorefVector(sloti);
      if( corefVeci == null ) {
        corefVeci = domainCorefPairs.getCountVector(sloti);
        if( corefVeci != null ) corefVeci = trimLowOccurrences(corefVeci, 2);
        slotsCache.setCorefVector(sloti, corefVeci);
//        System.out.println("Now coref cached " + sloti + " as " + type);
      }
//      else System.out.println("Got coref cached " + sloti + " as " + type);
              
      
//      System.out.println("Arg vec for sloti " + sloti);
//      Util.printMapSortedByValue(argVeci, 50);
//      System.out.println("Coref vec for sloti " + sloti);
//      Util.printMapSortedByValue(corefVeci, 50);
//      System.out.println("Both vec for sloti " + slots.get(i));
//      Util.printMapSortedByValue(domainArgCorefVeci, 50);
    }
    
    //for( int i = 0; i < slots.size()-1; i++ ) {
    for( int i = 0; i < iloopEnd; i++ ) {
      String sloti = slots.get(i);
//      String tokeni = sloti.substring(2, sloti.lastIndexOf(':'));
//      String relni = sloti.substring(sloti.lastIndexOf(':')+1);
      Map<String,Integer> domainArgsi = domainSlotArgCounts.getArgsForSlot(sloti);

      // e.g. PERSON, LOCATION, OTHER
      if( slotCache.slotTypeMatches(type, sloti, domainArgsi) ) {
//        System.out.println("  sloti " + sloti);
//        Map<String,Integer> generalArgsi = _generalSlotArgCounts.getArgsForSlot(sloti);
//        Map<String,Integer> trimmedArgsi = trimMap(generalArgsi, args);
//        System.out.println("Trimmed " + sloti + " to " + trimmedArgsi.size() + " args in buildScoreCache()");
        
        // Build the coref vector.
//        Map<String,Integer> domainCorefVeci = domainCorefPairs.getCountVector(sloti);
//        if( domainCorefVeci != null && domainArgsi != null ) domainCorefVeci.putAll(domainArgsi);
//        Map<String,Integer> domainArgCorefVeci = domainArgCorefVecs.get(i);
        Map<String,Integer> domainArgVeci = slotsCache.getArgumentVector(sloti);
        Map<String,Integer> domainCorefVeci = slotsCache.getCorefVector(sloti);
                
        
        int jloopStart = (slotsOptional == null ? i+1 : 0);
        for( int j = jloopStart; j < jloopEnd; j++ ) {
          String slotj = (slotsOptional == null ? slots.get(j) : slotsOptional.get(j));
//          String tokenj = slotj.substring(2, slotj.lastIndexOf(':'));
//          String relnj = slotj.substring(slotj.lastIndexOf(':')+1);
          Map<String,Integer> domainArgsj = domainSlotArgCounts.getArgsForSlot(slotj);
        
          // e.g. PERSON, LOCATION, OTHER
//          System.out.println("   checking slotj " + slotj + " type " + type + " args " + domainArgsj);
          if( slotCache.slotTypeMatches(type, slotj, domainArgsj) ) {
//            System.out.println("   slotj " + slotj);
//            Map<String,Integer> generalArgsj = _generalSlotArgCounts.getArgsForSlot(slotj);
//            Map<String,Integer> trimmedArgsj = trimMap(generalArgsj, args);
            
            // Build the coref vector.
//            Map<String,Integer> domainCorefVecj = domainCorefPairs.getCountVector(slotj);
//            if( domainCorefVecj != null && domainArgsj != null ) domainCorefVecj.putAll(domainArgsj);
//            Map<String,Integer> domainArgCorefVecj = (slotsOptional == null ? domainArgCorefVecs.get(j) : domainCorefVecsOptional.get(j));
            Map<String,Integer> domainArgVecj = slotsCache.getArgumentVector(slotj);
            Map<String,Integer> domainCorefVecj = slotsCache.getCorefVector(slotj);

            // Score the pair.  -- probability of coreferring in general corpus.
            double score = 1.0;
            if( !argSimOnly ) score = scorePair(sloti, slotj, domainCorefPairs);
            if( score == 0.0 ) {
              score = .001;
              /*
              // Give synonyms a boost (must start with the same POS tag, use the same relation, and be different words).
              if( sloti.charAt(0) == slotj.charAt(0) && relni.equals(relnj) && !tokeni.equals(tokenj)) {
                if( _wordnet.areSiblings(tokeni, tokenj, (sloti.charAt(0) == 'v' ? POS.VERB : POS.NOUN)) ) {
                  score = 0.5;
                  System.out.println("boosting synonyms " + sloti + " and " + slotj + " from " + tokeni + "," + tokenj);
                }
              }
              */
            }
//            System.out.printf("  %s %s coref score = %.10f\n", sloti, slotj, score);
            
/*
            // Cosine similarity of arguments?
            double cosine1 = 0.0;
            if( generalArgsi != null && generalArgsj != null )
              cosine1 = Dimensional.cosineInteger(generalArgsi, generalArgsj);
            //        System.out.printf("   arg cosine = %.4f\n", cosine);
            double cosine2 = 0.0;
            if( generalArgsi != null && generalArgsj != null )
              cosine2 = Dimensional.cosineInteger(removePerson(generalArgsi), removePerson(generalArgsj));
            //            System.out.printf("   general cosine = %.4f\n", cosine2);

            // This score really does seem useless, everything compares well!
            double cosine3 = 0.0;
            if( trimmedArgsi != null && trimmedArgsj != null )
              cosine3 = Dimensional.cosineInteger(trimmedArgsi, trimmedArgsj);
            //            System.out.printf("   general trimmed/domain cosine = %.4f\n", cosine3);
            //            if( cosine3 > .7 ) System.out.println("     high cosine " + sloti + " " + slotj);

            double cosine4 = 0.0;
            if( domainArgsi != null && domainArgsj != null )
//              cosine4 = Dimensional.cosineInteger(removePerson(domainArgsi), removePerson(domainArgsj));
              cosine4 = Dimensional.cosineInteger(removeNELabels(domainArgsi), removeNELabels(domainArgsj));
*/
            
//            double cosineArgsAndCoref = 0.0;
//            if( domainArgCorefVeci != null && domainArgCorefVecj != null )
//              cosineArgsAndCoref = Dimensional.cosineInteger(domainArgCorefVeci, domainArgCorefVecj);
            
            double cosineArgs = 0.0;
            if( domainArgVeci != null && domainArgVecj != null )
              cosineArgs = Dimensional.cosineInteger(domainArgVeci, domainArgVecj);
            double cosineCoref = 0.0;
            if( domainCorefVeci != null && domainCorefVecj != null )
              cosineCoref = Dimensional.cosineInteger(domainCorefVeci, domainCorefVecj);
            
            //            System.out.printf("   domain arg cosine w/person = %.4f\n", Dimensional.cosineInteger(domainArgsi, domainArgsj));
            //            System.out.printf("   domain arg cosine = %.4f\n", Dimensional.cosineInteger(removePerson(domainArgsi), removePerson(domainArgsj)));
//                        System.out.printf("   score " + score + " * " + cosine4);

            /*
            System.out.printf("  %s %s\tcoref = %.10f\tcosine = %.10f\n", sloti, slotj, score, cosine4);
            if( useDomainArgs )
              score *= cosine4;
            else
              score *= cosine3;
  */

//            if( sloti.equals("v-damage:o") || slotj.equals("v-damage:o") || 
//                sloti.equals("v-blow_up:o") || slotj.equals("v-blow_up:o") ||
//                sloti.equals("v-lay:s") || slotj.equals("v-lay:s") ||
//                sloti.equals("v-kidnap:s") || slotj.equals("v-kidnap:s") ||
//                sloti.equals("v-kidnap:o") || slotj.equals("v-kidnap:o") ||
//                sloti.equals("v-saw:o") || slotj.equals("v-saw:o") ) {
//              System.out.print("  \t" + sloti + " args ");
//              Util.printMapSortedByValue(domainArgVeci);
//              System.out.print("  \t" + slotj + " args ");
//              Util.printMapSortedByValue(domainArgVecj);
//              System.out.print("  \t" + sloti + " coref ");
//              Util.printMapSortedByValue(domainCorefVeci);
//              System.out.print("  \t" + slotj + " coref ");
//              Util.printMapSortedByValue(domainCorefVecj);
//            }
            
            System.out.printf("  \tcosineArgs = %.10f\n", cosineArgs);
            System.out.printf("  \tcosineCoref = %.10f\n", cosineCoref);
            System.out.printf("  \tcosineBlend = %.10f\n", (cosineCoref+cosineArgs)/2.0);
            score = (cosineCoref+cosineArgs)/2.0;

            if( cosineArgs > 0.7 ) score = Math.max(cosineArgs, score);
            if( cosineCoref > 0.7 ) score = Math.max(cosineCoref, score);

            // Override with just the max.
            score = Math.max(cosineArgs, cosineCoref);

            // Override with the harmonic mean.
//            score = 2.0 * (1.0-cosineArgs) * (1.0-cosineCoref) / ((1.0-cosineArgs) + (1.0-cosineCoref));
//            score = 1.0 - score;
            
            // Penalize same-verb slots.
            if( sloti.startsWith(slotj.substring(0,slotj.indexOf(':'))) )
              score /= 2;
            
//            score = cosineArgs;
//            score = cosineCoref;
            
//            System.out.printf("  \tcosineArgsCoref = %.10f\n", cosineArgsAndCoref);
//            score = cosineArgsAndCoref;
            
            // Set to zero so clustering can track the low scorers.
//            if( score < (.001 * .2) ) score = 0.0;
            if( score < .1 ) score = 0.0;

            // Cache the final score.
            cache.addScoreSorted(sloti, slotj, (float)score);
            if( score > 0.0 ) System.out.printf("cached\t%s\t%s\t%.6f\n", sloti, slotj, score);
            else System.out.printf("cached\t%s\t%s\t0\n", sloti, slotj);
          }
        }
      }
    }
    
//    System.out.print("Created cache, scores: ");
//    cache.printSorted(2000);
    System.out.println("**created cache scores done**");

    return cache;
  }

  /**
   * Creates an overall count of arguments summed across all of the given predicates.
   * The predicates are a list of IDs, indexing positions in the given names list.
   * @param ids A set of indices into the names list.
   * @param names The list of tokens that have arguments in our corpus.
   * @param argCounts The map from token to argument counts.
   */
  private Counter<String> sumArgs(Collection<String> names, VerbArgCounts argCounts) {
    Counter<String> sum = new IntCounter<String>();
    if( names != null && argCounts != null ) {
      for( String slotname : names ) {
        Map<String,Integer> subcounts = argCounts.getArgsForSlot(slotname);
        if( subcounts != null ) {
          for( Map.Entry<String,Integer> entry : subcounts.entrySet() )
            sum.incrementCount(entry.getKey(), entry.getValue());
        }
      }
    }
    return sum;
  }
  
  /**
   * Count how many verbs in the two sets appear in both with alternating subj/obj.
   * @return The number of clashes across sets.
   */
  private int subjectObjectClash(Collection<String> slots1, Collection<String> slots2) {
    Set<String> subjects1 = new HashSet<String>();
    Set<String> objects1 = new HashSet<String>();
    int numClashes = 0;
    
    // Store all subjects/objects in the first set.
    for( String arg1 : slots1 ) {
      if( arg1.endsWith(":s") )
        subjects1.add(arg1.substring(0, arg1.length()-2));
      else if( arg1.endsWith(":o") )
        objects1.add(arg1.substring(0, arg1.length()-2));
    }
    
    System.out.println("slots1 = " + slots1);
    System.out.println("subj1 = " + subjects1);
    System.out.println("objs1 = " + objects1);
    
    for( String arg2 : slots2 ) {
      if( arg2.endsWith(":s") ) {
        String base = arg2.substring(0, arg2.length()-2);
        if( objects1.contains(base) )
          numClashes++;
      }
      if( arg2.endsWith(":o") ) {
        String base = arg2.substring(0, arg2.length()-2);
        if( subjects1.contains(base) )
          numClashes++;
      }
    }
    return numClashes;
  }
  
  private boolean argVectorsDiffer(Counter<String> args1, Counter<String> args2) {
    System.out.println("argVectorsDiffer top!");
    Distribution<String> dist1 = Distribution.getDistribution(args1);
    Distribution<String> dist2 = Distribution.getDistribution(args2);

    Set<String> argdiffs = new HashSet<String>();

    for( String token : dist1.keySet() ) {
      double prob1 = dist1.getCount(token);
      if( dist1.getCount(token) > 0.02 ) {
        double prob2 = dist2.getCount(token);
        double ratio = (prob1 < prob2 ? prob1 / prob2 : prob2 / prob1);
        System.out.printf("- %s\t%.4f\t%.4f\tratio=%.4f\n", token, prob1, prob2, ratio);
        if( ratio < 0.2 ) {
          argdiffs.add(token);
          System.out.println("  arg differs: " + token);
        }
      }
    }

    if( argdiffs.size() >= 2 ) {
      System.out.println("Arg vectors differ!!");
      return true;
    }

    return false;
  }
  
  private List<Set<Integer>> reconstructClusters(List<Triple> order, List<String> names, int printFrequency, int stopAtClusterSize, 
      CountTokenPairs corefCounts, VerbArgCounts argCounts, FrameRole.TYPE type) {
    Map<Integer, Set<Integer>> clusters = new HashMap<Integer, Set<Integer>>();
    float lastMergeScore = -1.0f;
    
    int i = 0;
    for( Triple merge : order ) {
      float mergeScore = (Float)merge.third();
      float percentChange = (lastMergeScore == -1.0 ? 0.0f : ((lastMergeScore - mergeScore) / lastMergeScore));
      System.out.println("mergeScore " + mergeScore + " % change " + percentChange);
      boolean checkStoppage = false;
      if( mergeScore < 0.7f && percentChange > 0.05f ) {
        checkStoppage = true;
        System.out.println("checkStoppage yes!");
      }
      boolean newElement = false;
      
      Set<Integer> cluster = clusters.get(merge.first());
      if( cluster == null ) {
        newElement = true;
        cluster = new HashSet<Integer>();
        cluster.add((Integer)merge.first());
        clusters.put((Integer)merge.first(), cluster);
      }

      Set<Integer> cluster2 = clusters.get(merge.second());
      if( cluster2 == null ) {
        newElement = true;  
        cluster.add((Integer)merge.second());
      }
      else {
        if( checkStoppage && !newElement && argCounts != null ) {
          System.out.println("checkStoppage...");
          List<String> names1 = ClusterUtil.clusterIdsToStrings(cluster, names);
          List<String> names2 = ClusterUtil.clusterIdsToStrings(cluster2, names);
          Counter<String> clusterArgs = sumArgs(names1, argCounts);
          Counter<String> cluster2Args = sumArgs(names2, argCounts);
          SlotTypeCache.trimArgsByType(type, clusterArgs, _wordnet);
          SlotTypeCache.trimArgsByType(type, cluster2Args, _wordnet);
          if( subjectObjectClash(names1, names2) > 0 && argVectorsDiffer(clusterArgs, cluster2Args) ) {
            System.out.println("  cluster1: " + cluster);
            System.out.println("  cluster2: " + cluster2);
            break;
          }
        }
        // Copy the elements in the second cluster to the first one.
        cluster.addAll(cluster2);
        // Copy the main element of the second cluster too!
        cluster.add((Integer)merge.second());
        // Now get rid of the entire second cluster.
        clusters.remove((Integer)merge.second());
      }

      // Stop now if the cluster is bigger than our max size.
      if( stopAtClusterSize > 0 && cluster.size() >= stopAtClusterSize )
        break;

      
      // Print them for debugging.
      if( printFrequency > 0 && i % printFrequency == 0 ) {
        System.out.println("--");
        for( Map.Entry<Integer,Set<Integer>> entry : clusters.entrySet() ) {
          ClusterUtil.printCluster(names, entry.getValue());

          // DEBUGGING: sum the args
          if( argCounts != null && corefCounts != null ) {
            Map<String,Integer> sum = new HashMap<String,Integer>();
            for( Integer slotid : entry.getValue() ) {
              Map<String,Integer> vec = corefCounts.getCountVector(names.get(slotid));
              if( vec != null )
                for( Map.Entry<String, Integer> entry2 : vec.entrySet() )
                  Util.incrementCount(sum, entry2.getKey(), entry2.getValue());
            }
            for( Integer slotid : entry.getValue() ) {
              for( Map.Entry<String, Integer> entry2 : argCounts.getArgsForSlot(names.get(slotid)).entrySet() )
                Util.incrementCount(sum, entry2.getKey(), entry2.getValue());
            }

            // print the sum for this cluster
            Util.printMapSortedByValue(sum, 40);
          }
        }
        System.out.println(merge.third());
        lastMergeScore = mergeScore;
      }
      i++;
    }

    // Add the key maps to their own clusters.
    List<Set<Integer>> finalClusters = new ArrayList<Set<Integer>>();
    for( Map.Entry<Integer, Set<Integer>> entry : clusters.entrySet() ) {
      Set<Integer> cluster = entry.getValue();
      cluster.add(entry.getKey());
      finalClusters.add(cluster);
    }
    return finalClusters;
  }
  
  /**
   * Remove any roles from the frame that are small and low occurring.
   */
  public void removeRoles(Frame frame) {
    List<FrameRole> roles = frame.getRoles();
    if( roles != null ) {
      Set<FrameRole> removal = new HashSet<FrameRole>();
      
      // Look for small roles.
      for( FrameRole role : roles ) {
//        if( role.getSlots().size() < 4 ) {
          int sum = 0;
          for( String slot : role.getSlots() ) {
            Map<String,Integer> argCounts = _domainSlotArgCounts.getArgsForSlot(slot);
            sum += Util.sumValues(argCounts);
          }
          
          System.out.println("sum = " + sum + "\t" + role.getSlots());
          // TODO: don't have a cutoff, just find a gap in the roles' sums themselves....
          //       ...a few roles will have a lot, the rest will have less.
          if( sum < 200 ) {
            removal.add(role);
            System.out.println("Removing role " + role);
          }
//        }
      }
      
      // Remove the roles
      for( FrameRole role : removal )
        frame.removeRole(role);
    }
  }
  
  /**
   * Merges roles in the frame if they take similar arguments.
   * @param frame
   * @param cache
   */
  public void mergeRoles(Frame frame, double cutoff) {
    mergeRoles(frame, cutoff, null);
  }
  private void mergeRoles(Frame frame, double cutoff, Map<FrameRole.TYPE, ScoreCache> caches) {
      //    VerbArgCounts argCounts = _frameIRCounts.get(frame.getID()).slotArgCounts();
    VerbArgCounts argCounts = getIRArgCounts(frame.getID());
    
    List<FrameRole> roles = frame.getRoles();
    if( roles != null ) {
      int numRoles = roles.size();
      int mergei = -1, mergej = -1;

      // Pre-compute the scores between slots for each role type.
      if( caches == null ) {
        caches = new HashMap<TYPE, ScoreCache>();
        caches.put(FrameRole.TYPE.PERSON, buildScoreCache(frame.getID(), frame.tokens(), frame.getRoleSlots(), FrameRole.TYPE.PERSON, true));
        caches.put(FrameRole.TYPE.OTHER, buildScoreCache(frame.getID(), frame.tokens(), frame.getRoleSlots(), FrameRole.TYPE.OTHER, true));
        caches.put(FrameRole.TYPE.EVENT, buildScoreCache(frame.getID(), frame.tokens(), frame.getRoleSlots(), FrameRole.TYPE.EVENT, true));
        caches.put(FrameRole.TYPE.PHYSOBJECT, buildScoreCache(frame.getID(), frame.tokens(), frame.getRoleSlots(), FrameRole.TYPE.PHYSOBJECT, true));
      }

      System.out.println("Merging " + numRoles + " frame roles");
      for( int i = 0; i < numRoles-1; i++ ) {
        FrameRole rolei = roles.get(i);
        System.out.println("A: " + rolei.getSlots());
        for( int j = i+1; j < numRoles; j++ ) {
          FrameRole rolej = roles.get(j);
          // If the two roles have compatible types.
          if( rolei._type == rolej._type ) {
            ScoreCache cache = caches.get(rolei._type);
            if( cache == null ) {
              System.err.println("No cache for role type: " + rolei._type);
              System.exit(-1);
            }
            float score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(rolei.getSlots(), rolej.getSlots(), cache, true, false);
            System.out.printf("\t%.5f\tB: %s\n", score, roles.get(j).getSlots());

            if( score >= cutoff ) {
              System.out.println("Score merge-worthy!!! " + i + " with " + j);

              // Make sure we aren't clobbering different roles...
              Counter<String> clusterArgsi = sumArgs(rolei.getSlots(), argCounts);
              Counter<String> clusterArgsj = sumArgs(rolej.getSlots(), argCounts);
              SlotTypeCache.trimArgsByType(rolei._type, clusterArgsi, _wordnet);
              SlotTypeCache.trimArgsByType(rolej._type, clusterArgsj, _wordnet);
              if( subjectObjectClash(rolei.getSlots(), rolej.getSlots()) == 0 || !argVectorsDiffer(clusterArgsi, clusterArgsj) ) {
                System.out.println("MERGING!!! " + i + " with " + j);
                System.out.println("  - i " + rolei);
                System.out.println("  - j " + rolej);
                mergei = i;
                mergej = j;
                break;
              }
            }
          }
        }
        if( mergei > -1 ) break;
      }
      
      // Merge the roles, and then recurse, check all role pairs again.
      if( mergei > -1 ) {
        frame.mergeRoles(mergei, mergej);
        mergeRoles(frame, cutoff, caches);
      }
    }
  }
  
  /**
   * Just print out the scores between the resulting clusters...
   */
  public void debugClusters(FrameRole.TYPE type, Vector<Triple> mergeHistory, List<String> slots, EventPairScores cache) {
    List<Set<Integer>> clusters = ClusterUtil.reconstructClusters(mergeHistory, slots, 10);
    
    List<Set<String>> strclusters = new ArrayList<Set<String>>();
    for( Set<Integer> cluster : clusters ) {
      Set<String> clusty = new HashSet<String>();
      for( Integer index : cluster ) clusty.add(slots.get(index));
      strclusters.add(clusty);
    }
    
    System.out.println("Final inter-cluster scores!");
    for( int i = 0; i < strclusters.size()-1; i++ ) {
      System.out.println("A: " + strclusters.get(i));
      for( int j = i+1; j < strclusters.size(); j++ ) {
        float score = SingleLinkSimilarity.computeNewLinksClusterSimilarity(strclusters.get(i), strclusters.get(j), cache, true, false);
        System.out.printf("\t%.5f\tB: %s\n", score, strclusters.get(j));
      }
    }
    
  }
    
  /**
   * Takes the merge history from a clustering algorithm and rebuilds the final clusters.
   * Sets these final clusters as the roles in the given frame argument.
   * @param type The type of semantic roles (e.g. person, non-person)
   * @param mergeHistory The history from the clustering algorithm, a vector of merge pairs.
   * @param slots The original slots in the order given to the clustering algorithm, so we can
   *              recover the slots from the indices in the merge history output.
   * @param args The arguments seen in all slots of the given frame's tokens.
   * @param frame The frame to which we add the semantic roles.
   */
  private void addClustersAsRoles(FrameRole.TYPE type, List<Triple> mergeHistory, List<String> slots, Set<String> args, Frame frame) {
    // DEBUG output.
//    System.out.println("Slot Merges!  (type=" + type + ")");
//    for( Triple merge : mergeHistory ) {
//      System.out.println(slots.get((Integer)merge.first()) + "\t" + slots.get((Integer)merge.second())
//          + "\t" + merge.third());
//    }

    // Reconstruct the clusters.
    System.out.println("Slot Clusters!");
//    Collection<Set<Integer>> clusters = ClusterUtil.reconstructClusters(mergeHistory, slots, 1);
    Collection<Set<Integer>> clusters = reconstructClusters(mergeHistory, slots, 1, 0, getIRCorefCounts(frame.getID()), getIRArgCounts(frame.getID()), type);
    
    // Add each cluster as a role.
    System.out.println("Final Slot Clusters!");
    for( Set<Integer> cluster : clusters ) {
      Set<String> strcluster = new HashSet<String>();
      for( Integer item : cluster ) {
        System.out.print(" " + slots.get(item));
        strcluster.add(slots.get(item));
      }
      System.out.println();

      // Get the top argument heads.
      SortableScore[] mainargs = mainArgsOfSlots(strcluster, args, type, frame);
      System.out.print(" -> theargs -> ");
      for( int i = 0; i < mainargs.length && i < 30; i++ )
        System.out.print(" " + mainargs[i]);
      System.out.println();

      // Save this cluster of slots as a semantic role.
      FrameRole newrole = new FrameRole(type, strcluster, mainargs);
      frame.addRole(newrole);
      System.out.println(" -> new frame role: " + newrole);
    }
    System.out.println("Frame now: " + frame);
  }

  /**
   * Checks that the subjects and objects of all trigger words are assigned to
   * some slot.  They might not participate in the initial clustering, but should
   * be added later.
   */
  public void forceTriggersInSlots(Frame frame) {
    System.out.println("Force triggers into slots: " + frame.tokens());
    List<String> currentSlots = frame.getRoleSlots();
    
    // Add any subject or object that was not yet assigned to a role.
    Set<String> forceSlots = new HashSet<String>();
    for( String token : frame.tokens() ) {
      if( token.startsWith("v-") ) {
        String subj = token + ":s";
        String obj = token + ":o";
        if( currentSlots == null || !currentSlots.contains(subj) ) forceSlots.add(subj);
        if( currentSlots == null || !currentSlots.contains(obj) ) forceSlots.add(obj);
      }
    }
    
    // Add the slots.
    if( forceSlots.size() > 0 ) {
      System.out.println("Forcing addition of trigger slots: " + forceSlots);
      Map<String,FrameRole> slotMap = addSlotsToRoles(forceSlots, frame, false);
      for( Map.Entry<String, FrameRole> entry : slotMap.entrySet() ) {
        System.out.println(" - adding " + entry.getKey() + " \tval " + entry.getValue());
        entry.getValue().addSlot(entry.getKey());
      }
    }
  }
  
  /**
   * Get all of the seen dependencies with the given token, returning those that
   * were seen more than twice.
   * @param token A predicate e.g. v-arrest
   * @return e.g. v-arrest:s
   */
  private List<String> getDesiredSlots(String token) {
    List<String> tokenSlots = new ArrayList<String>(); 
    Set<String> matches = _domainCorefPairs.tokensThatStartWith(token + ":");
    System.out.println("Desired " + token + " : " + matches);
    for( String match : matches ) {
      String basetoken = CountTokenPairs.detachToken(match);
      String relation= CountTokenPairs.detachRelation(match);
      if( _domainDeps.getDocCount(basetoken, relation) > 5 )
        tokenSlots.add(match);
      else 
//        System.out.println("Skipping low occurring relation " + match);
        System.out.println("Skipping low occurring relation " + match + " basetoken " + basetoken + " relation " + relation);
    }
    return tokenSlots;
  }
  
  /**
   * Creates a new list of slots that only keeps those which were seen often.
   * @param frameid The frame which the slots are for.
   * @param slots   The list of desired slots.
   * @param cutoff  The number of times a slot needs to be seen in the corpus.
   * @return A new list, a subset of the given list of slots.
   */
  private List<String> filterLowOccurringSlots(int frameid, List<String> slots, int cutoff) {
    List<String> subset = new ArrayList<String>();
    VerbArgCounts argcounts = getIRArgCounts(frameid);
      
    for( String slot : slots ) {

//      System.out.print("Slot " + slot + " dist: ");
      Map<String,Integer> counts = argcounts.getArgsForSlot(slot);
      Map<String,Double> dist = Dimensional.normalizeInt(counts);
//      Util.printDoubleMapSortedByValue(dist, 100);
//      List<String> topkeys = Dimensional.topKeysByValue(dist, 0.1);
      List<String> topkeys = Dimensional.topKeysByValue(dist, 0.05);
//      System.out.println("\n\ttop keys: " + topkeys);
      
      int size = (counts == null ? 0 : Dimensional.sumValues(counts));
      if( size > cutoff && topkeys.size() > 0 )
        subset.add(slot);
//      else System.out.println("filtered " + slot + " size=" + size + " topkeysize=" + topkeys.size());
    }
    return subset;
  }

  private Map<String,Integer> substituteClasses(Map<String,Integer> argVec, int frameid, FrameRole.TYPE type) {
    WordClasses wordClasses = _wordClasses.get(frameid);
    
    Map<String,Integer> newvec = new HashMap<String,Integer>();
    for( Map.Entry<String,Integer> entry : argVec.entrySet() ) {
      String arg = entry.getKey();
      String argClass = wordClasses.getWordClass(arg);
      FrameRole.TYPE argType = wordClasses.getClassType(argClass);
      // If the word isn't in a known synonymous class, keep it.
      if( argClass == null )
        Util.incrementCount(newvec, arg, entry.getValue());
      // If the word is in a class, and its type matches.
      else if( argType == type ) {
        Util.incrementCount(newvec, argClass, entry.getValue());
      }
    }
    return newvec;
  }
  
  private EventPairScores buildArgScoreCache(int frameid, List<String> args) {
    // Get the slot/arg counts for this frame.
      VerbArgCounts slotargcounts = getIRArgCounts(frameid);
    
    EventPairScores cache = new EventPairScores();
    
    for( int ii = 0; ii < args.size()-1; ii++ ) {
      String freqii = args.get(ii);
      Map<String,Integer> slotsii = slotargcounts.getSlots(freqii);
      Map<String,Integer> slotsTrimii = new HashMap<String,Integer>();
      slotsTrimii.putAll(slotsii);
      slotsTrimii.remove(Dimensional.topKeyByValue(slotsii));
//      System.out.print("argi " + args.get(ii) + "\t");
//      Util.printMapSortedByValue(slotsii);
//      System.out.print("argi2 " + args.get(ii) + "\t");
//      Util.printMapSortedByValue(slotsTrimii);
      for( int jj = ii+1; jj < args.size(); jj++ ) {
        String freqjj = args.get(jj);
        Map<String,Integer> slotsjj = slotargcounts.getSlots(freqjj);
        Map<String,Integer> slotsTrimjj = new HashMap<String,Integer>();
        slotsTrimjj.putAll(slotsjj);
        slotsTrimjj.remove(Dimensional.topKeyByValue(slotsjj));
        double cosine = Dimensional.cosineInteger(slotsii, slotsjj);
        double cosineTrim = Dimensional.cosineInteger(slotsTrimii, slotsTrimjj);
//        int overlap = Dimensional.numKeyOverlap(slotsii, slotsjj);
        
        // If a huge negative drop, then penalize it.
        if( cosine - cosineTrim < -.2 )
          cosine = cosineTrim;
        
//        System.out.print("argj " + args.get(ii) + "\t");
//        Util.printMapSortedByValue(slotsii);
//        System.out.printf("cosine %s %s %.3f %.3f %d\n", args.get(ii), args.get(jj), cosine, cosineTrim, overlap);
        cache.setScore(freqii, freqjj, (float)cosine);
      }
    }

    return cache;
  }
  
  /**
   * Looks at all arguments seen in the given slots, isolates the most frequently
   * occurring ones, and calculates clusters of synonyms based on their syntactic
   * constructs in the frame's IR corpus.  Stores the clusters globally.
   * @param frameid The frame whose slots we are looking at.
   * @param slots The slots from the frame that we think are most important.
   */
  private void createArgumentSimilarityClasses(int frameid, List<String> slots) {
      VerbArgCounts slotargcounts = getIRArgCounts(frameid);
      IDFMap idf = getIRIDF(frameid);
    
    // Count all arguments in all given slots.
    Map<String,Integer> argCounts = new HashMap<String,Integer>();
    for( String slot : slots ) {
      Map<String,Integer> args = slotargcounts.getArgsForSlot(slot);
      for( Map.Entry<String, Integer> entry : args.entrySet() ) {
        Util.incrementCount(argCounts, entry.getKey(), entry.getValue());
      }
    }
    // Sort arguments by frequency.
    List<String> frequentArgs = Util.sortKeysByIntValues(argCounts);
//    for( String freq : frequentArgs )
//      System.out.println("freqarg: " + freq + "\t" + argCounts.get(freq));
    
    // Save the argument heads that occur often enough.
    List<String> topFreqArgs = new ArrayList<String>();
    // 50 was used with IR counts. Using only domain should be much smaller...
//    int cutoff = 50 + ((idf.numDocs() / 2000) * 25);
    int cutoff = 5 + ((idf.numDocs() / 2000) * 25);
    for( String freq : frequentArgs )
      if( argCounts.get(freq) > cutoff )
        if( !Ling.isCapitalized(freq) && freq.charAt(0) != '*' )
          topFreqArgs.add(freq);
      
    // Calculate the similarity between all argument pairs.
    EventPairScores cache = buildArgScoreCache(frameid, topFreqArgs);
    
    // Clustering algorithm.
    System.out.println("Clustering args!");
    HierarchicalClustering clusterer = new HierarchicalClustering();
    clusterer.setMinInitialSimilarityScore(.3f);
    clusterer.setMinClusteringScore(.4f);
    List<Triple> history = clusterer.efficientCluster(topFreqArgs, cache, ClusterUtil.NEW_LINK_WITH_CONNECTION_PENALTY, null);
    reconstructClusters(history, topFreqArgs, 1, 0, null, null, null);
    System.out.println("arg clustering finished.");
    
    // Store the clusters globally, and disambiguate the words' types (e.g. person, event, location).
    if( _wordClasses == null ) _wordClasses = new HashMap<Integer,WordClasses>();
    WordClasses classes = WordClasses.fromClusterHistory(history, topFreqArgs);
    classes.calculateWordTypes(_wordnet);
    _wordClasses.put(frameid, classes);
    System.out.println("Word Classes!!\n\t" + classes);
  }
  
  // DEBUGGING
  private void topArgSlotSimilarity(int frameid, List<String> slots) {
      VerbArgCounts argcounts = getIRArgCounts(frameid);

    for( int i = 0; i < slots.size()-1; i++ ) {
      Map<String,Double> disti = Dimensional.normalizeInt(argcounts.getArgsForSlot(slots.get(i)));
      List<String> topkeys = Dimensional.topKeysByValue(disti, 0.1);
      for( int j = i+1; j < slots.size(); j++ ) {
        Map<String,Double> distj = Dimensional.normalizeInt(argcounts.getArgsForSlot(slots.get(j)));
        List<String> topkeysj = Dimensional.topKeysByValue(distj, 0.05);

        for( String top : topkeys ) 
          if( topkeysj.contains(top) )
            System.out.println("argsim\t" + slots.get(i) + " and " + slots.get(j) + " top " + top);
      }
    }
  }

  
  /**
   * Main function that links the slots of a frame's tokens together.
   */
  public void induceSlots(Frame frame, Set<String> nearby) {
    List<String> slots = new ArrayList<String>();
    List<Pair<String,String>> clusterConstraints = new ArrayList<Pair<String,String>>();
    System.out.println("****\nInduce Slots: " + frame.tokens() + "\tnearby " + nearby);
    frame.clearRoles();
        
    Set<String> targetTokens = new HashSet<String>(frame.tokens());
    if( nearby != null ) {
      System.out.println("frame tokens " + frame.tokens() + " and nearby size " + nearby.size());
//      for( String near : nearby ) {
//        System.out.println("adding nearby " + near);
//        targetTokens.add(near);          
//      }
      targetTokens.addAll(nearby);
    }
    
    // For each token, find which grammatical relations have been seen with it.
    for( String token : targetTokens ) {
      List<String> tokenSlots = getDesiredSlots(token);
      slots.addAll(tokenSlots);

      // Build cluster constraints - subjects and objects can't match within this predicate.
      for( int i = 0; i < tokenSlots.size()-1; i++ ) {
        for( int j = i+1; j < tokenSlots.size(); j++ ) {
          String sloti = tokenSlots.get(i);
          String slotj = tokenSlots.get(j);
          if( sloti.endsWith(":s") || sloti.endsWith(":o") ||
              slotj.endsWith(":s") || slotj.endsWith(":o") )            
            clusterConstraints.add(new Pair<String,String>(sloti, slotj));
        }
      }
    }
    for( Pair<String,String> pair : clusterConstraints )
      System.out.println("constraint: " + pair);
    
    System.out.println("induceSlots slots:\t" + slots);
    // ACL 2011 paper had the following commented line:
    //    int cutoff = 50 + ((_frameIRCounts.get(frame.getID()).idf().numDocs() / 1000) * 30);
    // Without an IR stage:
    int cutoff = 10 + 30 * getIRIDF(frame.getID()).numDocs() / 1000;
    slots = filterLowOccurringSlots(frame.getID(), slots, cutoff);
//    slots = filterLowOccurringSlots(frame.getID(), slots, 250);
    System.out.println("filtered slots:\t" + slots);
//    topArgSlotSimilarity(frame.getID(), slots);
    createArgumentSimilarityClasses(frame.getID(), slots);

    setAlternations(frame);
    
    // Get the args of the tokens in this domain.
    Set<String> args = argsOfTokens(targetTokens, _domainSlotArgCounts);
    //    System.out.println("***");
    //    for( String arg : args ) System.out.print(" " + arg);
    //    System.out.println();

    //    SortableScore[] argsScored = argsOfTokensSorted(frame.tokens(), _domainSlotArgCounts);
    //    System.out.println("***");
    //    for( SortableScore arg : argsScored ) System.out.print(" " + arg);
    //    System.out.println();

    // Build the clusterer class.
    HierarchicalClustering clusterer = new HierarchicalClustering();
    // These were the scores in the EMNLP submission: coref-count * arg-cosine-sim
//    clusterer.setMinInitialSimilarityScore(.001f);
//    clusterer.setMinClusteringScore(.001f);
    clusterer.setMinInitialSimilarityScore(.45f);
    clusterer.setMinClusteringScore(.45f);

    final FrameRole.TYPE[] roleTypes = { FrameRole.TYPE.PERSON, FrameRole.TYPE.PHYSOBJECT };
    for( FrameRole.TYPE roleType : roleTypes ) {
      System.out.println("Induce slots for type " + roleType);
      EventPairScores cache = buildScoreCache(frame.getID(), targetTokens, slots, roleType, true);
      List<Triple> history = clusterer.efficientCluster(slots, cache, ClusterUtil.NEW_LINK_WITH_CONNECTION_PENALTY, clusterConstraints);
      if( frame.tokens().contains("v-kidnap") || frame.tokens().contains("v-explode") || frame.tokens().contains("v-kill") )
	reconstructClusters(history, slots, 1, 0, getIRCorefCounts(frame.getID()), getIRArgCounts(frame.getID()), roleType);
//      history = trimHistory(history, _frameIRCounts.get(frame.getID())._slotArgCounts);
      // Merge more?
//      debugClusters(roleType, history, slots, cache);
      // Reconstruct the cluster and add to the frame.
      addClustersAsRoles(roleType, history, slots, args, frame);
    }
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      SlotInducer inducer = new SlotInducer(args);

      // TEST
      Set<String> tokens = new HashSet<String>();
      tokens.add("v-kidnap");
      tokens.add("v-abduct");
      tokens.add("v-release");
      tokens.add("n-release");
      tokens.add("n-kidnapping");
      tokens.add("v-board");
      Frame frame = new Frame();
      frame.setTokens(tokens);
      inducer.induceSlots(frame, null);
      System.out.println("FINAL INDUCING: " + frame);

      //      v-kidnap|v-abduct|v-release|n-kidnapping|v-board|v-explode|v-detonate|v-blow_up|n-explosion|v-damage|n-damage|v-cause|v-locate|n-location|n-cause|n-device|v-go_off|v-defuse|v-shatter|n-blast|v-hurl|v-load|v-plant|v-set_off|v-destroy|n-residence|v-assert|v-arrive

      // TEST 2
      tokens.clear();
      tokens.add("v-explode");
      tokens.add("v-detonate");
      tokens.add("v-blow_up");
      tokens.add("n-explosion");
      tokens.add("n-damage");
      tokens.add("v-damage");
      tokens.add("v-cause");
      tokens.add("n-cause");
      tokens.add("v-locate");
      tokens.add("n-location");
      tokens.add("n-device");
      tokens.add("v-go_off");
      tokens.add("v-defuse");
      tokens.add("v-shatter");
      tokens.add("n-blast");
      tokens.add("v-load");
      tokens.add("v-plant");
      tokens.add("v-set_off");
      tokens.add("v-destroy");
      tokens.add("v-hurl");
      tokens.add("v-assert");
      tokens.add("v-arrive");
      tokens.add("n-residence");

      String[] nearby = { "n-bomb", "v-injure", "n-avenue", "n-casualty", "v-place", "n-attack", "n-fire", "v-wound", 
          "v-occur", "n-charge", "n-morning", "n-path", "v-die", "v-confirm", "v-attack", "n-result", "v-claim", "n-patrol", 
          "n-ministry", "n-place", "v-kill" };
      
      Frame frame2 = new Frame();
      frame2.setTokens(tokens);
      inducer.induceSlots(frame2, null);
      System.out.println("FINAL INDUCING: " + frame2);
 
      // Build the pairwise score cache between nearby and frame tokens.
//      List<String> frameslots = frame2.getRoleSlots();
//      Set<String> frametokens = new HashSet<String>(frame2.tokens());
//      for( String token : nearby ) {
//        List<String> tokenSlots = inducer.getDesiredSlots(token);
//        for( String targetSlot : tokenSlots ) frameslots.add(targetSlot);
//        frametokens.add(token);
//      }
//      EventPairScores cache = inducer.buildScoreCache(frametokens, frameslots, FrameRole.ALL, true);
      
      List<String> list = new ArrayList<String>();
      for( String near : nearby ) list.add(near);
      inducer.addTokensToRoles(list, frame2, true);
      System.out.println("FINAL INDUCING WITH NEARBY: " + frame2);
    }
  }
}

// New kidnap/bomb clusters:
// v-kidnap|v-release|n-release|n-suspension|n-kidnapping|n-shipment|n-bomb|n-device|v-go_off|n-damage|v-explode|v-defuse|v-damage|n-building|n-blast|n-avenue|v-destroy|v-injure|v-set_off|n-bank
// NEARBY: n-bomb|v-injure|n-avenue|n-casualty|v-place|n-attack|n-fire|v-wound|v-occur|n-charge|n-morning|n-path|v-die|v-confirm|v-attack|n-result|v-claim|n-patrol|n-ministry|n-place|v-kill

// Just the kidnap and bomb tokens:
// v-kidnap|v-release|n-release|n-surrender|n-release|v-announce|n-suspension|n-kidnapping|n-treatment|n-shipment|n-pipeline|n-bomb|n-device|v-go_off|n-damage|v-explode|v-defuse|v-damage|n-building|n-blast|n-avenue|n-enterprise|v-destroy|v-plant|v-detonate|v-cause|v-injure|v-set_off|v-blow_up

// To get all tokens in all clusters:
// grep 'Cluster id=' outty3 | sed 's/Cluster id=[0-9]* score=[0-9.]* //' | sed 's/\[//' | sed 's/\]//' | sed 's/[0-9.]//g' | tr '\n' ' ' | sed 's/  / /g' | sed 's/  / /g' | sed 's/  / /g' | sed 's/ /|/g'
// This was the result of the above at one point:
// n-proposal|n-solution|v-negotiate|n-conflict|n-delegation|n-assembly|n-issue|n-negotiation|n-constitution|n-meeting|n-discussion|v-submit|v-agree|v-propose|v-find|n-government|v-approve|v-meet|v-talk|v-suspend|n-round|v-discuss|n-side|v-sit_down|n-decision|v-end|n-approval|n-pipeline|n-bomb|n-device|v-go_off|n-damage|v-explode|v-defuse|v-damage|n-building|n-blast|n-avenue|n-bank|n-enterprise|v-destroy|v-plant|v-detonate|v-cause|v-injure|v-set_off|v-blow_up|n-section|v-estimate|v-place|n-office|v-locate|n-escalation|n-right|n-federation|n-repression|n-violation|v-violate|n-union|n-increase|v-condemn|v-denounce|n-labor|n-massacre|n-terror|n-violence|v-warn|v-perpetrate|n-death|n-organization|n-threat|v-lead|n-action|n-arrest|v-murder|v-create|n-act|n-demand|n-danger|n-extradition|n-court|n-trial|v-extradite|v-punish|n-investigation|n-crime|n-case|n-request|v-charge|n-justice|n-murder|v-authorize|v-issue|v-request|v-investigate|n-charge|v-commit|n-assassination|n-sentence|v-clarify|v-involve|v-conduct|n-statement|v-send|v-accuse|v-serve|n-casualty|n-ambush|v-inflict|n-clash|n-patrol|v-sustain|n-fighting|v-wound|v-ambush|n-guard|v-seize|v-result|v-last|v-kill|v-die|v-report|v-clash|n-place|v-suffer|n-injury|n-result|v-crash|n-landing|n-accident|v-land|n-pilot|v-fly|n-flight|v-transport|v-reveal|n-reception|n-break|n-satisfaction|v-govern|n-condemnation|v-exercise|n-communication|v-express|v-elect|v-fulfil|n-presidency|n-vote|v-cast|n-coalition|v-vote|n-convergence|n-election|n-party|n-alliance|n-march|n-ruling|n-opposition|v-read|v-participate|v-urge|v-rule|v-prove|v-follow|n-demobilization|n-agreement|n-reconciliation|n-democratization|n-implementation|n-compliance|v-postpone|v-sign|v-demobilize|v-reflect|n-process|v-comply|n-commission|n-resistance|v-base|v-impose|v-benefit|n-trade|n-land|n-banking|n-reform|v-deserve|n-institution|n-respect|n-faith|v-respect|n-credit|v-hope|n-development|v-build|n-activity|v-believe|v-own|v-raid|n-intelligence|v-confiscate|v-arrest|n-services|n-operations|v-discover|v-dismantle|v-capture|n-training|n-center|n-connection|v-surround|v-belong|n-doctor|n-wound|n-bullet|n-condition|v-hit|v-treat|v-shoot|n-head|n-times|v-save|n-stoppage|n-transportation|n-broadcast|v-decree|n-sabotage|n-wake|n-transmission|n-stations|v-ratify|n-bolivia|v-grant|n-facility|v-coordinate|n-producer|v-promise|v-share|n-protest|n-disappearance|n-torture|v-combat|n-amnesty|n-method|v-publish|v-undertake|n-stand|n-mechanism|n-consultation|v-adopt|v-eradicate|n-measure|v-require|v-make_up|v-grow|v-infiltrate|v-shout|n-tension|n-demonstration|v-detain|v-accompany|n-presence|v-throw|v-expel|v-break|n-withdrawal|v-invade|v-concentrate|v-sit|n-invasion|v-withdraw|v-repel|v-demand|n-consumption|n-production|n-distribution|n-program|v-develop|n-education|v-implement|v-invite|v-assign|v-fire|n-shot|n-residence|v-gather|n-scene|v-machinegunned|n-morning|n-incident|v-occur|n-vice|v-found|v-stage|v-confirm|n-persecution|v-clear_up|n-punishment|v-unleash|v-justify|v-cover|n-campaign|v-finance|n-confrontation|v-identify|n-resignation|v-resign|n-strike|n-association|v-protest|n-protection|n-use|v-overcome|n-difficulty|n-crisis|v-experience|v-face|v-enable|v-demonstrate|v-stress|v-solve|v-honor|n-inauguration|n-ceremony|n-project|v-sponsor|v-attend|v-cooperate|n-surrender|n-release|v-kidnap|n-shipment|n-suspension|n-kidnapping|n-treatment|v-release|v-announce|n-path|v-shine|n-shining|v-attribute|v-allege|v-oppose|n-movement|n-determination|v-voice|v-reaffirm|n-surprise|v-fill|n-commitment|v-struggle|v-free|n-access|n-location|v-define|v-gain|v-prepare|v-enforce|v-fulfill|n-duty|n-procedure|n-law|n-accordance|v-bring|v-continue|n-passage|v-omit|v-indistinct|n-words|n-word|n-thing|v-concern|v-assure|n-safety|v-rest|v-ensure|v-guarantee|v-respond|v-protect|v-arm|v-load|n-road|v-block|v-set|n-fire|v-hurl|v-assert|v-link|n-end|v-enjoy|n-recording|v-put|v-refer|v-win|v-mean|v-lose|n-return|v-mention|v-exist|n-uprising|v-massacre|v-organize|v-strike|v-join|n-occupation|v-prevent|n-task|v-close|n-shooting|v-hear|v-overfly|v-witness|v-interrupt|n-explosion|n-air|n-traffic|n-departure|v-coincide|n-analysis|v-subject|n-arrival|v-wait|v-remain|n-class|n-reference|n-advance|v-head|n-destruction|n-rest|v-complete|v-view|v-dislodge|n-entrance|n-bridge|v-guard|v-permit|n-position|v-maintain|v-attack|v-enter|n-reply|v-await|n-resumption|n-factor|n-circle|v-populate|v-bomb|n-offensive|n-population|n-bombing|v-launch|v-drop|n-figure|v-admit|n-artillery|n-deployment|n-incursion|n-installation|n-piece|v-indicate|v-order|n-report|n-surveillance|v-step_up|v-neighbor|n-alert|v-connect|v-station|n-voting|n-identification|v-deploy|n-detachment|v-weaken|v-discredit|v-pressure|v-strengthen|v-eliminate|v-support|n-effort|n-coup|v-transfer|v-consist|v-intend|n-attempt|v-attempt|n-involvement|n-defeat|n-revolution|v-emerge|v-defeat|n-aggression|n-genocide|n-occasion|v-broadcast|n-speech|v-address|n-news|n-service|n-medium|v-appear|v-explain|n-press|n-trap|v-fall|v-set_up|n-mistake|n-failure|v-exert|n-pressure|n-reaction|v-provoke|n-intention|v-blame|n-accord|n-independence|v-reach|v-consolidate|n-help|n-conclusion|n-progress|n-hostilities|v-cease|n-refuge|v-return|v-seek|v-resume|v-insist|v-please|n-establishment|n-warning|n-raid|n-clip|n-business|n-enforcement|n-agency|n-channels|v-produce|v-generate|v-process|n-seizure|v-emphasize|v-term|v-saw|v-add|n-responsibility|v-claim|n-call|n-claim|v-threaten|v-raise|v-receive|n-attack|v-point_out|v-wage|n-difference|n-battle|n-field|n-origin|n-struggle|v-determine|v-manage|v-appoint|n-post|v-run|v-remove|v-escape|v-improve|v-annihilate|v-worsen|v-sabotage|n-interference|n-participation|n-turn|n-sacrifice|v-entail|v-contribute|v-question|v-establish|v-obtain|v-praise|v-endanger|v-name|v-shed|v-register|n-license|v-intercept|n-indication|v-travel|v-steal|v-observe|n-explanation|v-display|n-initiative|v-catch|v-listen|v-prefer|v-consult|v-find_out|v-comment|v-hide|v-draft|n-mediation|v-contain|v-apply|n-maneuver|n-continuation|n-bloodshed|v-count|v-encourage|v-promote|v-reduce|n-supply|v-limit|v-supply|n-consequence|v-expect|n-question|n-answer|v-answer|n-session|v-like|n-shock|n-wave|v-shake|v-pass|v-recover|v-proceed|n-counteroffensive|v-check|v-review|v-total|n-recognition|v-recognize|v-defend|v-feel|n-addition|v-resort|v-lie|v-lift|n-siege|v-control|v-live|n-version|n-second|n-medicine|v-wear|v-carry|n-behavior|v-remind|v-harm|n-step|n-following|n-regard|n-command|n-view|v-inform|v-wish|n-attitude|n-liberation|v-expand|v-succeed|v-isolate|v-depend|v-cut|n-sign|n-aid|n-debate|v-show|n-practice|v-instal|v-characterize|n-achievement|n-administration|v-divide|v-comprise|v-confuse|v-give_up|n-harassment|v-harass|n-experience|n-reduction|v-occupy|v-consider|n-fruit|n-gun|v-export|v-presume|v-replace|n-crop|n-liberty|v-choose|v-fight|v-represent|n-emergency|v-extend|v-declare|n-control|n-war|v-affect|n-situation|v-unite|n-instrument|n-limitation|n-tie|v-reject|n-smuggling|n-corruption|v-constitute|v-stop|n-promise|v-ignore|v-restore|v-keep|v-trust|n-option|n-impact|v-change|v-move|n-change|v-deal|n-blow|n-mission|n-contact|n-test|v-design|v-study|n-series|v-verify|v-go_on|n-obligation|v-form|n-example|v-cite|v-turn_over|n-capacity|v-suspect|n-assault|n-proof|v-favor|n-left|v-advocate|n-line|n-instance|v-stand|n-offer|v-initiate|v-resolve|v-reiterate|n-rejection|v-disclose|v-disrupt|v-halt|v-destabilize|n-order|n-ransom|v-pay|n-no|n-attention|v-lay_down|n-cause|v-disappear|n-weapon|v-host|n-talk|n-course|v-offer|n-assistance|n-thanks|n-cooperation|v-thank|v-provide|v-forget|v-we|n-behalf|v-let|v-act|v-mark|n-beginning|n-loss|v-fail|n-fate|v-reply|v-ask|v-convince|v-pick_up|n-funeral|v-evacuate|v-pursue|v-handle|v-criticize|v-relate|n-opinion|v-rule_out|v-detect|v-train|v-reinforce|n-security|v-attain|n-goal|v-search|n-means|n-justification|n-expression|v-hinder|v-happen|v-bear|v-confront|n-combat|n-name|v-flee|v-burn|v-force|n-house|v-arrive|n-economy|v-quote|v-direct|v-acknowledge|n-direction|n-leadership|v-react|v-point|n-role|v-assume|v-describe|n-response|v-increase|n-exchange|v-intensify|n-number
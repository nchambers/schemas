package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.BasicEventAnalyzer;
import nate.CalculateIDF;
import nate.Coref;
import nate.CountTokenPairs;
import nate.CountVerbDepCorefs;
import nate.CountVerbDeps;
import nate.EntityMention;
import nate.IDFMap;
import nate.NERSpan;
import nate.Pair;
import nate.WordEvent;
import nate.args.CountArgumentTypes;
import nate.args.VerbArgCounts;
import nate.cluster.ClusterUtil;
import nate.cluster.HierarchicalClustering;
import nate.narrative.EventPairScores;
import nate.narrative.ScoreCache;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.SortableObject;
import nate.util.SortableScore;
import nate.util.TreeOperator;
import nate.util.Triple;
import nate.util.Util;
import nate.util.WordNet;
import nate.util.WordPosition;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;




/**
 * This class provides functions that return a set of Entity Mentions that
 * are the *most relevant* to a document's content.
 *
 * A baseline returns the most frequent entities based on coref cluster sizes.
 *
 * One finds all event pairs in a single document,
 * and then return the set of pairs that seem to form a coherent template
 * using pairwise pmi scores learned by Nate's Schema learning algorithm.
 *
 * -domainpmi
 * The event pairwise PMI scores for the domain.
 *
 * -corpuspmi
 * The event pairwise PMI scores for a larger corpus. (gigaword)
 *
 * -domaincounts
 * Counts of verb-deps within the MUC domain.
 *
 * -corpuscounts
 * Counts of verb-deps over an entire corpus.
 *
 * -domainidf
 * -generalidf
 * Paths to the IDF scores for the domain and general corpus.
 *
 * -paircounts
 * Path to the token pair counts from CountTokenPairs.
 * 
 * -argcounts
 * Counts of the seen argument heads of verb slots in this domain.
 * 
 */
public class TemplateExtractor {
  EventPairScores _domainPairScores;
  EventPairScores _generalPairScores;
  WordNet _wordnet;
  int _docCutoff = 50;
  float _IDFcutoff = 0.1f;
  IDFMap _domainIDF;
  IDFMap _generalIDF;

  VerbArgCounts _domainSlotArgCounts;
  CountVerbDepCorefs _relnCountsDomain;
  CountVerbDeps _relnCountsGeneral;
  CountTokenPairs _tokenPairCounts;
  Map<String,Boolean> _validArgCache;

  List<String> _topWordsCacheFila = null;
  List<String> _topWordsCacheChambers = null;
  List<String> _topWordsCacheLikelihood = null;
  List<String> _topWordsCacheSalience = null;
  List<SortableScore> _topScoresCacheSalienceAndRatio = null;
  Map<String,Object> _globalCache = new HashMap<String, Object>();

  public List<MUCEntity> _goldEntities = null;


  public TemplateExtractor(String args[]) {
    HandleParameters params = new HandleParameters(args);

    _wordnet = new WordNet(params.get("-wordnet"));
    _domainIDF = new IDFMap(params.get("-domainidf"));
    _generalIDF = new IDFMap(params.get("-generalidf"));
    _domainPairScores = new EventPairScores(params.get("-domainpmi"));
    _generalPairScores = new EventPairScores(params.get("-corpuspmi"));
    _domainSlotArgCounts = new VerbArgCounts(params.get("-argcounts"), 1);

    // Load the verb-dep counts.
    _relnCountsGeneral = new CountVerbDeps(params.get("-corpuscounts"));
    _relnCountsDomain = new CountVerbDepCorefs(params.get("-domaincounts"));
    // Load token pair counts.
    _tokenPairCounts = new CountTokenPairs(params.get("-paircounts"));
    // Cache for speedup purposes...
    _validArgCache = new HashMap<String, Boolean>();
  }

  public TemplateExtractor(IDFMap domainIDF, IDFMap generalIDF, CountVerbDepCorefs relnCountsDomain, VerbArgCounts domainArgs, WordNet wordnet) {
    _wordnet = wordnet;
    _domainIDF = domainIDF;
    _generalIDF = generalIDF;
    _relnCountsDomain = relnCountsDomain;
    _domainSlotArgCounts = domainArgs;
  }

  private List<EntityMention> mentionsWithNER(Vector<EntityMention> entities,
      List<NERSpan> ners) {
    List<EntityMention> mentionsWithNER = new ArrayList<EntityMention>();

    // Get all Entity IDs that have at least one mention with an NER label.
    for( NERSpan ner : ners ) {
      for( EntityMention mention: entities ) {
        if( mentionSubsumesNER(mention, ner) )
          mentionsWithNER.add(mention);
      }
    }
    return mentionsWithNER;
  }


  /**
   * Get the NER's ordered by document position.
   */
  public List<String> extractOrderedNER(Vector<EntityMention> entities,
      List<NERSpan> ners) {
    List<EntityMention> mentionsWithNER = mentionsWithNER(entities, ners);

    // Find the single mention for each Entity ID that appears earliest.
    Map<Integer,EntityMention> earliest = new HashMap<Integer, EntityMention>();
    for( EntityMention mention: mentionsWithNER ) {
      EntityMention seen = earliest.get(mention.entityID());
      if( seen == null )
        earliest.put(mention.entityID(), mention);
      else if( seen.sid() > mention.sid() || 
          (seen.sid() == mention.sid() && seen.start() > mention.start()) )
        earliest.put(mention.entityID(), mention);
    }

    // Return IDs with highest count.
    SortableScore[] scores = new SortableScore[earliest.size()];
    int i = 0;
    for( Integer id : earliest.keySet() ) {
      EntityMention mention = earliest.get(id);
      float score = (float)(mention.sid()*500 + mention.start());
      scores[i++] = new SortableScore(score, entityBestString(id, entities));
    }
    Arrays.sort(scores);

    // Return the sorted objects!
    List<String> best = new ArrayList<String>();
    for( SortableScore score : scores )
      best.add(score.key());
    return best;
  }


  /**
   * Get the most frequent NER's, find their coref classes, and order by 
   * the size of their classes.
   */
  public List<String> extractFrequentNER(Vector<EntityMention> entities,
      List<NERSpan> ners) {
    Set<Integer> entitiesWithNER = new HashSet<Integer>();

    // Get Entity IDs of coref entities with NER mentions.
    List<EntityMention> mentionsWithNER = mentionsWithNER(entities, ners);
    for( EntityMention mention: mentionsWithNER )
      entitiesWithNER.add(mention.entityID());

    // Return IDs with highest count.
    SortableScore[] scores = new SortableScore[entitiesWithNER.size()];
    int i = 0;
    for( Integer id : entitiesWithNER ) {
      int count = 0;
      for( EntityMention mention : entities )
        if( mention.entityID() == id ) count++;
      scores[i++] = new SortableScore((float)count, entityBestString(id, entities));
    }
    Arrays.sort(scores);

    // Return the sorted objects!
    List<String> best = new ArrayList<String>();
    for( SortableScore score : scores )
      best.add(score.key());
    return best;
  }


  /**
   * Baseline extraction: use coref and count how many times each entity occurs
   * in the document.  Return them sorted by most frequent.
   */
  public List<String> extractMostFrequentEntities(ProcessedData dataReader) {
    // Find all possible events.
    List<WordEvent> events = BasicEventAnalyzer.extractEvents(dataReader.getParseStrings(), dataReader.getDependencies(), dataReader.getEntities(), 
        true, _wordnet, 0);

    // Count the entity occurrences.    
    Map<Integer,Integer> entityCounts = new HashMap<Integer,Integer>();
    for( WordEvent event : events ) {
      for( Integer id : event.arguments().keySet() )
        Util.incrementCount(entityCounts, id, 1);
    }

    // Sort the counts.
    SortableObject<Integer>[] sorted = new SortableObject[entityCounts.size()];
    int i = 0;
    for( Integer id : entityCounts.keySet() )
      sorted[i++] = new SortableObject<Integer>(entityCounts.get(id), id);
    Arrays.sort(sorted);

    // Print the occurrence counts.
    System.out.println("---Most Frequent Entity Occurrence Counts---");
    List<String> bestEntities = new LinkedList<String>();
    for( SortableObject<Integer> obj : sorted ) {
      Integer id = obj.key();
      if( obj.score() > 0.0f )
        bestEntities.add( entityBestString(id, dataReader.getEntities()) );
      System.out.println("most freq " + id + " " + obj.score());
    }

    return bestEntities;
  }


  /**
   * Best we can do with our entities...recall 94.1%
   *
   */
  public List<String> findCeilingWithEntities(List<MUCEntity> golds, ProcessedData dataReader) {
    List<String> matches = new ArrayList<String>();
    if( golds != null ) {
      for( MUCEntity gold : golds ) {
        for( EntityMention mention : dataReader.getEntities() ) {
          if( TemplateTester.stringMatchToMUCEntity(gold, mention.string()) ) {
            matches.add(mention.string());
            break;
          }
        }
      }
    }
    return matches;
  }

  /**
   * Looking at just verbs: 74.7% recall.
   *         - add possessive nouns with prep_of: 82.1% recall
   * Looking at just nouns: 58.7% recall.
   *
   */
  public List<String> findCeilingFromDeps(List<MUCEntity> golds, ProcessedData dataReader,
      char posType, boolean includeNominatives) {
    List<EntityMention> entities = dataReader.getEntities();
    List<Tree> trees = TreeOperator.stringsToTrees(dataReader.getParseStrings());
    List<String> matches = new ArrayList<String>();

    if( golds != null ) {
      for( MUCEntity gold : golds ) {
        boolean foundmatch = false;
        boolean matchedOtherPOS = false;
        String outty = null;
        int sid = 0;
        for( List<TypedDependency> sentDeps : dataReader.getDependencies() ) {
          for( TypedDependency dep : sentDeps ) {

            //	    String reln = CountVerbDeps.normalizeRelation(dep.reln().toString());
            int govIndex = dep.gov().index();
            //	    String gov = dep.gov().label().value().toString().toLowerCase();
            Tree subtree = TreeOperator.indexToSubtree(trees.get(sid), govIndex);
            String govPOSTag = subtree.label().value();
            char normalPOS = CalculateIDF.normalizePOS(govPOSTag);
            String govLemma = _wordnet.lemmatizeTaggedWord(dep.gov().value().toString().toLowerCase(), govPOSTag);

            EntityMention mention = mentionAtIndex(entities, sid,
                dep.dep().index(), dep.gov().index());
            if( mention != null ) {
              if( TemplateTester.stringMatchToMUCEntity(gold, mention.string()) ) {

                // Check POS type of governor.		
                if( normalPOS == posType || posType == 'a' || 
                    // NOTE: having a possessive check barely helps, the nominatives are big.
                    //       Most possessives we want are already nominatives.
                    (includeNominatives && Ling.isNominative(govLemma, normalPOS, _wordnet)) ) {
                  matches.add(mention.string());
                  foundmatch = true;
                  break;
                }
                // Don't save this match if it isn't our desired POS type.
                else {
                  String output = "*" + posType + " skip pos=" + normalPOS + " " + dep + " w/mention " + mention;
                  if( outty != null ) outty += "\n\t" + output;
                  else outty = output;
                  //		  System.out.println(output);
                  matchedOtherPOS = true;
                }
              }
            }
          }
          if( foundmatch ) break;
          sid++;
        }
        if( !foundmatch && matchedOtherPOS ) {
          System.out.println("**other POS matched: " + outty);
        }
      }
    }
    return matches;
  }


  /**
   * @return The longest string representing each entity ID.
   */
  public static List<String> entitiesBestStrings(List args, Vector<EntityMention> entities) {
    if( args == null ) return null;
    else if( args.size() == 0 ) return new ArrayList<String>();
    else {
      if( args.get(0) instanceof Integer )
        return bestStringsFromInteger(args, entities);
      else if( args.get(0) instanceof RelnAndEntity )
        return bestStringsFromRelns(args, entities);
      else
        System.err.println("ERROR: unknown type");
    }
    return null;
  }
  public static List<String> bestStringsFromInteger(List<Integer> ids, Vector<EntityMention> entities) {
    List<String> best = new ArrayList<String>();
    for( Integer id : ids ) best.add(entityBestString(id, entities));
    return best;
  }
  public static List<String> bestStringsFromRelns(List<RelnAndEntity> args, Vector<EntityMention> entities) {
    List<String> best = new ArrayList<String>();
    for( RelnAndEntity arg : args ) 
      best.add(entityBestString(arg.entityMention.entityID(), entities));
    return best;
  }

  /**
   * @return The longest string representing an entity ID.
   */
  public static String entityBestString(Integer id, Collection<EntityMention> entities ) {
    String longest = "";

    for( EntityMention entity : entities ) {
      if( entity.entityID() == id && entity.string().length() > longest.length() )
        longest = entity.string();
    }

    //    System.out.println("entityBest " + longest);
    //    System.out.println("entityBest " + Ling.trimLeadingDeterminer(longest));
    return Ling.trimLeadingDeterminer(longest);
  }


  /**
   * Gets all verbs and noun nominals in one sentence, returns as a set.
   */
  public static Set<String> getKeyTokens(int sid,
      Tree tree,
      List<TypedDependency> sentDeps,
      List<EntityMention> entities,
      WordNet wordnet,
      IDFMap generalIDF) {
    Set<String> tokens = new HashSet<String>();
    Set<Integer> seenIndices = new HashSet<Integer>();

    // Find any particles ahead of time - O(n)
    Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);

    for( TypedDependency dep : sentDeps ) {
      int govIndex = dep.gov().index();
      if( !seenIndices.contains(govIndex) ) {

        EntityMention mention = mentionAtIndex(entities, sid, 
            dep.dep().index(), dep.gov().index());
        // Arguments must be part of an entity mention.
        if( mention != null ) {
          seenIndices.add(govIndex);
          String gov = dep.gov().label().value().toString().toLowerCase();
          Tree subtree = TreeOperator.indexToSubtree(tree, dep.gov().index());
          String govPOSTag = subtree.label().value();
          String govLemma = wordnet.lemmatizeTaggedWord(gov, govPOSTag);
          if( CountVerbDeps.isNumber(gov) ) {
            gov = CountVerbDeps.NUMBER_STRING;
            govLemma = CountVerbDeps.NUMBER_STRING;
          }
          String particle = particles.get(govIndex);
          if( particle != null ) {
            gov = gov + "_" + particle;
            govLemma = govLemma + "_" + particle;
          }
          char normalPOS = CalculateIDF.normalizePOS(govPOSTag);

          // Only accept nouns, verbs, adjectives.
          if( normalPOS == 'v' || Ling.isNominative(govLemma, normalPOS, wordnet) ) {
            String govlemmakey = CalculateIDF.createKey(govLemma, normalPOS);

            // Skip words with low IDF scores from the general corpus.
            if( generalIDF.get(govlemmakey) < 2.1f ) {
              //	      System.out.println(" skip token " + govlemmakey);
            }
            // Add the word.
            else if( !tokens.contains(govlemmakey) ) 
              tokens.add(govlemmakey);
          }
          //	  else System.out.println(" (skipping getkeytokens " + normalPOS + "-" + govLemma + ")");
        }
      }
    }
    return tokens;
  }

  /**
   * Gets all verbs and noun nominals, returns as a set.
   */
  public static Set<String> getKeyTokens(List<Tree> trees,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      WordNet wordnet,
      IDFMap generalIDF) {
    Set<String> tokens = new HashSet<String>();
    int sid = 0;

    // Get all words that have arguments.
    for( List<TypedDependency> sentDeps : alldeps ) {
      Set<String> sentTokens = getKeyTokens(sid, trees.get(sid), sentDeps, entities, 
          wordnet, generalIDF);
      tokens.addAll(sentTokens);
      sid++;
    }
    return tokens;
  }

  private static List<String> stripIndices(List<WordPosition> words) {
    List<String> strs = new ArrayList<String>();
    if( words != null ) {
      for( WordPosition word : words )
        strs.add(word.token);
    }
    return strs;
  }
  
  /**
   * Given a word (and its POS tag), return all dependencies in which it is
   * the governor.  Also accepts constraints on the governor (v-kidnap#o#person).
   * Will only return dependencies if the constraints are met by other depenedencies.
   * @param posWord e.g. "v-kidnap"
   */
  public static List<TypedDependency> depsWithGovernor(String posWord, Tree tree, List<TypedDependency> sentDeps, WordNet wordnet) {
    List<TypedDependency> deps = new ArrayList<TypedDependency>();
    
    // Get the object information e.g. v-kidnap#o#person
    Map<Integer,List<WordPosition>> objects = null;
    String wordObject = null;
    boolean hasObject = CountArgumentTypes.isObjectString(posWord);
    if( hasObject ) {
      wordObject = CountArgumentTypes.getObject(posWord);
      posWord = CountArgumentTypes.removeObject(posWord);
      objects = Ling.objectsInSentence(-1, sentDeps);
      System.out.println("hasObject " + posWord + " obj " + wordObject + " all objects " + objects);
    }
    
    // Get the particles.
    Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);
    
    for( TypedDependency dep : sentDeps ) {
//      System.out.println(" dep " + dep);
      String govLemma = governorLemma(dep, tree, particles, wordnet);
      
//      System.out.println(" govLemma=" + govLemma + " posWord=" + posWord);
      // The governor matches?
      if( govLemma.equals(posWord) ) {
        if( !hasObject )
          deps.add(dep);
        else if( hasObject && objects != null ) {
          List<String> objs = stripIndices(objects.get(dep.gov().index()));
          System.out.println("dep " + dep + " objs " + objs);
          if( objs != null && objs.contains(wordObject) && !dep.dep().value().toString().equals(wordObject) ) {
            deps.add(dep);
            System.out.println("Saving dep " + dep);
          }
        }
      }
    }
    return deps;
  }

  /**
   * @return The lemma form of the governor of the given typed dependency.
   */
  public static String governorLemma(TypedDependency dep, Tree tree, Map<Integer,String> particles, WordNet wordnet) {
    String gov = dep.gov().label().value().toString().toLowerCase();
    //    Tree subtree = TreeOperator.indexToSubtree(tree, dep.gov().index());
    //    String govPOSTag = subtree.label().value();
    //    String lemma = wordnet.lemmatizeTaggedWord(gov, govPOSTag);
    //    System.out.println("governorLemma gov=" + gov + " index=" + dep.gov().index() + " tree=" + tree + " particles=" + particles);
    return CountTokenPairs.buildTokenLemma(gov, dep.gov().index(), tree, particles, wordnet);
  }

  /**
   * This function finds the "best string" representation of the given mention
   * among all mentions it corefers with.  Then we take the head word (last
   * word of the best string and check that it is an ok argument.
   *
   * This is mainly for pronoun mentions...which we don't want...but we want
   * them if they have a coreferring mention that is a name!
   */
  public static boolean corefWithValidArgument(EntityMention mention,
      Collection<EntityMention> entities,
      WordNet wordnet) {
    for( EntityMention men : entities ) {
      if( men != mention && men.entityID() == mention.entityID() ) {
        String beststr = entityBestString(mention.entityID(), entities);
        // Take the last word (not always the head, but good enough!)
        int pos = beststr.lastIndexOf(' ');
        if( pos > -1 ) {
          String lastword = beststr.substring(pos+1);
          return validEntityArgument(lastword, wordnet);
        }
        else
          return validEntityArgument(beststr, wordnet);
      }
    }
    return false;
  }

  /**
   * Determines if the string is the head word of a person, organization, or group.
   */
  public static boolean validEntityArgument(String str, WordNet wordnet) {
    boolean result = false;
    String lowered = str.toLowerCase();
    String setby = "null";
    // Quick cache lookup first!
    //    Boolean cached =  _validArgCache.get(str);
    //    if( cached != null ) return cached;

    // Don't accept numerals.
    if( str.matches("^[0-9]+$") ) {
      //      System.out.println("  validEntityArgument false: pronoun|integer");
      setby = "numeral";
      result = false;
    }
    // High precision, low recall (cyanide, exposives, lake, minerals, rubble, sand)
    else if( wordnet.isMaterial(lowered) ) {
      setby = "material";
//      System.out.println("  validEntityArgument " + lowered + " now true by material, was false by " + setby);
      result = true;
    }
    // Don't accept pronouns or numbers, return false now.
    else if( Ling.isPersonPronoun(lowered) || Ling.isInanimatePronoun(lowered) ||
        wordnet.isInteger(lowered) ) {
      //      System.out.println("  validEntityArgument false: pronoun|integer");
      setby = "pronoun";
      result = false;
    }
    // It's a person or group!
    else if( wordnet.isNounPersonOrGroup(lowered) ) {
      setby = "personorgroup";
      result = true;
    }
    // It's a physical object. --- kidnapping doesn't want these, but bombing does!
    else if( wordnet.isPhysicalObject(lowered) ) {
      //      System.out.println("  validEntityArgument true: physical_object");
      setby = "physobj";
      result = true;
    }
    // Reject time periods (months, days, weeks)
    else if( wordnet.isMeasure(lowered) ) {
      //      System.out.println("  validEntityArgument false: measure");
      setby = "measure";
      result = false;
    }
    // Accept all capitalized words.
    else if( !Character.isLowerCase(str.charAt(0)) ) {
      setby = "uppercased";
      result = true;
    }
    // Accept unknown tokens, it might be a unique name.
    else if( wordnet.isUnknown(lowered) ) {
      setby = "unknown";
      result = true;
    }

    // This matches a lot, but it's mostly general/vague locations (route, road, site, space, speedway, town, workplace). 
    if( result == true && (wordnet.isLocation(lowered) && !wordnet.isStructure(lowered)) ) {
      setby = "location";
//      System.out.println("  validEntityArgument " + lowered + " now false by location, was true by " + setby);
      result = false;
    }

    // If it is unknown, we return true?  NO
    if( result == false && setby.equals("null") ) System.out.println("wordnet falsed " + str);

    System.out.println("  final validEntityArgument " + str + " " + result + " by " + setby);
    return result;
  }


  /**
   * Given a word, find all arguments of the word and return the slot and the entity
   * mention of the argument.
   * Skips any arguments that are event nouns...nouns that have an Event ancestor 
   * in wordnet.  This assumes the caller really just wants traditional nouns 
   * (people, places, things).
   *
   * Only looks at main arguments (prep_of with nouns, subj/obj with verbs).\
   *
   * @param word e.g. "v-kidnap"
   * @param specificsid The ID of a single sentence you want to look in, not all sentences.
   */
  public static List<RelnAndEntity> argumentsOfWord(String word,
      List<Tree> trees,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      WordNet wordnet,
      int specificsid,
      boolean fullprep) {
    System.out.println("argumentsOfWord " + word);
    List<RelnAndEntity> ids = new ArrayList<RelnAndEntity>();

    // Nouns.
    if( word.charAt(0) == 'n' ) {
      // Is it an event?
      boolean isEvent = wordnet.isNounEvent(word.substring(2));
      if( isEvent ) {
//        System.out.println("argumentsOfWord (noun-event)");
        int sid = 0;
        for( List<TypedDependency> sentdeps : alldeps ) {
          if( specificsid == -1 || specificsid == sid ) {
            for( TypedDependency dep : depsWithGovernor(word, trees.get(sid), sentdeps, wordnet) ) {
              String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), fullprep);

              // TESTING
//              if( reln.startsWith("nn") ) {
////                System.out.println("  isevent reln " + word + " with " + reln);
//                // "members of THE ARMY"
//                EntityMention mention = mentionAtIndex(entities, sid, dep.dep().index(), dep.gov().index());    
//                if( mention != null ) {
////                  System.out.println("  -- found mention " + mention);
//                  if( validEntityArgument(dep.dep().value().toString(), wordnet) ||
//                      corefWithValidArgument(mention, entities, wordnet) )
//                    System.out.println("  -- would add!!!");
//                }
//              }
              
              if( reln.startsWith("p_") || reln.equals("poss") ) {
                //	      System.out.println("  isevent reln " + word + " with " + reln);
                //	    System.out.println("  not isevent " + word + " with " + reln);
                // "members of THE ARMY"
                EntityMention mention = mentionAtIndex(entities, sid, dep.dep().index(), dep.gov().index());	  
                if( mention != null ) {
                  String govLemma = governorLemma(dep, trees.get(sid), null, wordnet);
                  String depstr = dep.dep().value().toString();
                  // Don't return event nouns as arguments.
                  if( validEntityArgument(depstr, wordnet) ||
                      corefWithValidArgument(mention, entities, wordnet) )
                    ids.add(new RelnAndEntity(word, govLemma, reln, mention));
                }
              }
            }
          }
          sid++;
        }
      }
    }

    // Verbs.
    else if( word.charAt(0) == 'v' ) {
      System.out.println("argumentsOfWord (verb) with " + alldeps.size() + " sentences.");
      int sid = 0;
      for( List<TypedDependency> sentdeps : alldeps ) {
        //        System.out.println("argumentsOfWord verb sentence! " + specificsid);
        if( specificsid == -1 || specificsid == sid ) {
          //          System.out.println("argumentsOfWord passed sid!");
          Map<Integer, String> particles = Ling.particlesInSentence(sentdeps);
          for( TypedDependency dep : depsWithGovernor(word, trees.get(sid), sentdeps, wordnet) ) {
            String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), fullprep);
//            System.out.println("dep=" + dep + " reln=" + reln);

            //	    if( reln.equals(WordEvent.DEP_SUBJECT) || reln.equals(WordEvent.DEP_OBJECT) ) {
            if( reln.equals(WordEvent.DEP_SUBJECT) || reln.equals(WordEvent.DEP_OBJECT) || reln.startsWith(WordEvent.DEP_PREP) ) {
              EntityMention mention = mentionAtIndex(entities, sid, dep.dep().index(), dep.gov().index());
//              System.out.println("  mention=" + mention);
              if( mention != null ) {
                String govLemma = governorLemma(dep, trees.get(sid), particles, wordnet);
                String depstr = dep.dep().value().toString();
                //  Don't return event nouns as arguments.
                if( validEntityArgument(depstr, wordnet) || 
                    corefWithValidArgument(mention, entities, wordnet) ) {
                  ids.add(new RelnAndEntity(word, govLemma, reln, mention));
                  //		  System.out.println("  adding mention! " + mention);
                }
                //		else System.out.println("  (skipping noun event " + mention + ")");	      
              }
            }
          }
        }
        sid++;
      }
    }

    // Other.
    else {
      System.out.println("ERROR: unsupported POS in entityIDsOfArguments()");
      System.exit(-1);
    }

    return ids;
  }


  private int sidOfDependency(TypedDependency target, Vector<Vector<TypedDependency>> alldeps) {
    int sid = 0;
    for( Vector<TypedDependency> sentdeps : alldeps ) {
      for( TypedDependency dep : sentdeps ) {
        if( dep == target ) 
          return sid;
      }
      sid++;
    }
    return -1;
  }

  /**
   * @return A subset of the given entities, all entities in a particular sentence.
   */
  private Vector<EntityMention> entitiesInSentence(int sid, List<EntityMention> entities) {
    Vector<EntityMention> mentions = new Vector<EntityMention>();
    for( EntityMention mention : entities ) {
      if( mention.sentenceID() == sid )
        mentions.add(mention);
    }
    return mentions;
  }

  private List<TypedDependency> goldMatchDeps(MUCEntity gold, Vector<Vector<TypedDependency>> alldeps, Vector<EntityMention> entities) {
    List<TypedDependency> matches = new ArrayList<TypedDependency>();
    int sid = 0;
    for( Vector<TypedDependency> sentdeps : alldeps ) {
      for( TypedDependency dep : sentdeps ) {
        EntityMention mention = mentionAtIndex(entities, sid, 
            dep.dep().index(), dep.gov().index());
        if( mention != null ) {
          String beststring = entityBestString(mention.entityID(), entities);
          if( TemplateTester.stringMatchToMUCEntity(gold, beststring) )
            matches.add(dep);
        }
      }
      sid++;
    }
    return matches;
  }

  /**
   * Given a list of words and their word scores, find all occurrences of those 
   * words and extract their arguments.  Return the arguments as strings, sorted
   * by the scores of their parent words.
   */
  public static List<String> entityArgumentsOfWords(SortableScore[] wordScores,
      //Collection<String> words,
      List<Tree> trees,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      WordNet wordnet) {
    Map<Integer,Double> idCounts = new HashMap<Integer, Double>();
//    Map<Integer,List<RelnAndEntity>> idRelns = new HashMap<Integer,List<RelnAndEntity>>(); // for debugging

    //    System.out.println("entityArgumentsOfWords top: " + wordScores);
    //    for( SortableScore wordScore : wordScores ) System.out.print(" " + wordScore.key());
    //    System.out.println();

    // THIS LOOP TAKES A DECENT AMOUNT OF CPU TIME!
    // e.g. v-kidnap
    for( SortableScore wordScore : wordScores ) {
      String word = wordScore.key();
      //System.out.printf(" top word=%s\t(%.2f)\n", word, wordScore.score());
      List<RelnAndEntity> args = argumentsOfWord(word, trees, alldeps, entities, wordnet, -1, false);
      //System.out.println("  got args: " + entitiesBestStrings(args, entities));
      for( RelnAndEntity arg : args ) {
        //	Util.incrementCount(idCounts, arg.entityMention.entityID(), 1.0);
        Util.incrementCount(idCounts, arg.entityMention.entityID(), wordScore.score());
        // DEBUG ONLY
        //        List<RelnAndEntity> relns = idRelns.get(arg.entityMention.entityID());
        //        if( relns == null ) {
        //          relns = new ArrayList();
        //          idRelns.put(arg.entityMention.entityID(), relns);
        //        }
        //        relns.add(arg);
      }
      //      if( idCounts.size() > 8 ) break;
    }

    // Get the strings for these IDs and sort by count.
    //    SortableObject[] debug = new SortableObject[idCounts.size()];
    SortableScore[] scores = new SortableScore[idCounts.size()];
    int i = 0;
    for( Integer id : idCounts.keySet() ) {
      //      debug[i] = new SortableObject(idCounts.get(id), idRelns.get(id));
      scores[i++] = new SortableScore(idCounts.get(id), entityBestString(id, entities));
    }
    //    Arrays.sort(debug);
    Arrays.sort(scores);

    /*
    // DEBUG: print golds
    if( _goldEntities != null ) {
      for( MUCEntity gold : _goldEntities ) {
	System.out.println("  gold: " + gold);
	List<TypedDependency> deps = goldMatchDeps(gold, alldeps, entities);
	for( TypedDependency dep : deps )
	  System.out.println("    gold " + dep + " sid=" + sidOfDependency(dep, alldeps));
      }
    }
     */

    // Return the strings.
    List<String> args = new ArrayList<String>();
    i = 0;
    for( SortableScore score : scores ) {
//      boolean match = false;
      //       if( _goldEntities != null ) 
      // 	for( MUCEntity gold : _goldEntities ) {
      // 	  //	System.out.println("  vs " + gold + "\t" + score.key());
      // 	  if( TemplateTester.stringMatchToMUCEntity(gold, score.key()) )
      // 	    match = true;
      // 	}
      //       if( !match ) System.out.print("  NO MATCH\t");
      //       else System.out.print("  MATCH\t");
      //      System.out.println("  arg: " + score);
      //      System.out.println("    from: " + debug[i++]);
      System.out.println("textractor: argscored " + score);
      args.add(score.key());
    }
    return args;
  }


  /**
   * Given a cluster (list of words), find all words in the document that are close
   * to that cluster and return their arguments, sorted by strength of the words.
   */  
  public List<String> extractBasedOnCluster(Vector<String> parses,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      String[] cluster) {
    System.out.println("extractBasedOnCluster top!");
    List<Tree> trees = TreeOperator.stringsToTrees(parses);

    // Extra merging of coref clusters.
    Coref.mergeNameEntities(entities, _wordnet);

    // Build cluster.
    List<String> clusterList = new ArrayList<String>();
    for( String item : cluster ) clusterList.add(item);

    // All possible tokens from which to get arguments.
    Set<String> tokens = getKeyTokens(trees, alldeps, entities, _wordnet, _generalIDF);

    // Calculate PMI scores between pairs.
    ScoreCache cache = (ScoreCache)_globalCache.get("pairpmi");
    if( cache == null ) {
      cache = StatisticsDeps.pairCountsToPMI(_tokenPairCounts, _domainIDF, null, 5, .7);
      _globalCache.put("pairpmi", cache);
    }

    // Calculate cluster scores.
    SortableScore[] scores = new SortableScore[tokens.size()];
    int i = 0;
    for( String token : tokens ) {
      float score = ClusterMUC.scoreWithCluster(token, clusterList, cache, _domainIDF);
      scores[i++] = new SortableScore(score, token);
    }

    // Sort
    Arrays.sort(scores);
    List<String> words = new ArrayList<String>();
    for( SortableScore item : scores ) {
      System.out.println("sorted: " + item);
      words.add(item.key());
    }
    Util.firstN(words, 10);

    // Get the arguments for these top words.
    List<String> bestEntities = entityArgumentsOfWords(scores, trees, alldeps, entities, _wordnet);

    return bestEntities;
  }


  public List<String> extractByTopic(Vector<String> parses,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      String[] cluster,
      int max,
      boolean goldceiling) {
    SortableObject<Integer>[] sentences = findBestSentences(parses, alldeps, entities, cluster);
    ScoreCache cache = (ScoreCache)_globalCache.get("bestsent");
    TreeFactory tf = new LabeledScoredTreeFactory();
    Map<Integer, Double> entityIDScores = new HashMap<Integer, Double>();
    Map<Integer, Double> goldEntityIDScores = new HashMap<Integer, Double>();
    List<Tree> trees = TreeOperator.stringsToTrees(parses);
    Set<MUCEntity> seengolds = new HashSet<MUCEntity>();
    Set<MUCEntity> matchedGolds = new HashSet<MUCEntity>();

    for( SortableObject<Integer> scored : sentences ) {
      Integer sid = (Integer)scored.key();
      System.out.println("sid: " + sid);
      String parse = parses.elementAt(sid);
      Tree tree = TreeOperator.stringToTree(parse, tf);
      List<TypedDependency> sentdeps = alldeps.get(sid);
      Set<String> tokens = getKeyTokens(sid, tree, sentdeps, entities, _wordnet, _generalIDF);

      // ****************************************
      // Find gold matches in this sentence.
      // NOTE: we can get F1 of 0.83 if we choose all available gold entities.
      //       This means our F1 can improve with better entity selection using just these
      //       ordered sentences.
      int goldmatch = 0;
      List<EntityMention> mens = entitiesInSentence(sid+1, entities);
      if( _goldEntities != null ) {
        for( MUCEntity gold : _goldEntities ) {
          if( !seengolds.contains(gold) ) {
            for( EntityMention men : mens ) {
              if( TemplateTester.stringMatchToMUCEntity(gold, entityBestString(men.entityID(), entities)) ) {
                goldmatch++;
                Util.incrementCount(goldEntityIDScores, men.entityID(), 1.0f);
                seengolds.add(gold);
                System.out.println(" gold " + gold);
                break;
              }
            }
          }
        }
        System.out.println(" Gold Matched: " + goldmatch + " / " + _goldEntities.size());
      }
      // ****************************************

      for( String token : tokens ) {
        float score = ClusterMUC.scoreWithCluster(token, (List<String>)Util.arrayToList(cluster), cache, _domainIDF);
        System.out.println(" topic: " + token + "\t" + score);
        //	scores[i++] = new SortableScore(score, token);

        if( score <= 0.02f )
          System.out.println("  ignoring zero scored");
        else {
          List<Tree> treeWrapper = new ArrayList<Tree>();
          treeWrapper.add(tree);
          List<List<TypedDependency>> depsWrapper = new ArrayList<List<TypedDependency>>();
          depsWrapper.add(sentdeps);
          //	List<RelnAndEntity> relns = argumentsOfWord(token, treeWrapper, depsWrapper, entities);
          List<RelnAndEntity> relns = argumentsOfWord(token, trees, alldeps, entities, _wordnet, sid, false);
          if( relns != null )
            for( RelnAndEntity reln : relns ) {
              System.out.println("  reln: " + reln);
              System.out.print("    -> adding entity " + reln.entityMention + " *" + entityBestString(reln.entityMention.entityID(), entities));
              MUCEntity match = TemplateTester.stringMatchToMUCEntities(_goldEntities, entityBestString(reln.entityMention.entityID(), entities));
              if( match != null ) {
                System.out.println("  GOOD");
                matchedGolds.add(match);
              }
              else System.out.println("  PRECISION ERROR");
              Util.incrementCount(entityIDScores, reln.entityMention.entityID(), 1.0f);
            }
        }
      }

      if( entityIDScores.size() >= max ) break;
    }

    // DEBUG: missed gold entities
    for( MUCEntity gold : matchedGolds )
      System.out.println("MATCHED: " + gold);
    for( MUCEntity gold : seengolds ) {
      if( !matchedGolds.contains(gold) ) {
        System.out.println("RECALL ERROR: " + gold);
      }
    }

    // Get the strings of the entities.
    Map<Integer, Double> thescores = null;
    if( goldceiling ) thescores = goldEntityIDScores;
    else thescores = entityIDScores;
    List<String> bestEntities = new ArrayList<String>();
    for( Integer id : thescores.keySet() ) {
      bestEntities.add(entityBestString(id, entities));
      System.out.println("byTopic add: " + entityBestString(id, entities));
    }
    return bestEntities;
  }

  /**
   * Given a cluster of words ... score each sentence based on its words' proximity to the
   * given cluster.  
   * @return An array of scores, one for each sentence.  The array is sorted by score, and 
   * each key() field is the sentence ID.
   */
  public SortableObject<Integer>[] findBestSentences(Vector<String> parses,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      String[] cluster) {
    System.out.println("findBestSentence top!");
    int sid = 0;
    Set<Integer> seenIndices = new HashSet<Integer>();

    // Build cluster.
    List<String> clusterList = new ArrayList<String>();
    for( String item : cluster ) clusterList.add(item);

    // Calculate Conditional Probabilities between pairs.
    ScoreCache cache = null;
    if( !_globalCache.containsKey("bestsent") ) {
      cache = StatisticsDeps.pairCountsToConditionalProb(_tokenPairCounts, _domainIDF, null, 2);
      _globalCache.put("bestsent", cache);
    }
    else cache = (ScoreCache)_globalCache.get("bestsent");

    // Parse tree objects.
    List<Tree> trees = TreeOperator.stringsToTrees(parses);
    SortableObject<Integer>[] scores = new SortableObject[alldeps.size()];
    int i = 0;

    // Loop over each sentence.    
    for( List<TypedDependency> sentDeps : alldeps ) {
      float scoreSum = 0.0f;
      int numScored = 0;
      seenIndices.clear();

      // Get the particles.
      Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);

      for( TypedDependency dep : sentDeps ) {
        int govIndex = dep.gov().index();
        if( !seenIndices.contains(govIndex) ) {
          seenIndices.add(govIndex);
          String gov = dep.gov().label().value().toString().toLowerCase();
          Tree subtree = TreeOperator.indexToSubtree(trees.get(sid), dep.gov().index());
          String govPOSTag = subtree.label().value();
          String govLemma = _wordnet.lemmatizeTaggedWord(gov, govPOSTag);
          if( CountVerbDeps.isNumber(gov) )
            govLemma = CountVerbDeps.NUMBER_STRING;
          String particle = particles.get(dep.gov().index());
          if( particle != null )
            govLemma = govLemma + "_" + particle;
          char normalPOS = CalculateIDF.normalizePOS(govPOSTag);
          String govlemmakey = CalculateIDF.createKey(govLemma, normalPOS);

          // Filter low IDF words from the general corpus.
          //	  double wordProb = (double)_domainIDF.getDocCount(govlemmakey) / (double)_domainIDF.numDocs();
          //	  double idfCutoff = 1.0f * ( 1.0f + 2.0f * wordProb );
          float idf = _generalIDF.get(govlemmakey);
          if( idf == 0.0f || idf > 1.3f ) {
            // Score this token.
            float score = ClusterMUC.scoreWithCluster(govlemmakey, clusterList, cache, _domainIDF);
            //	    System.out.println(govlemmakey + " " + score + " idfcut=" + idfCutoff + " prob=" + wordProb);
            System.out.printf("%s %.4f ", govlemmakey, score);
            scoreSum += score * 100.0f;  // 100.0 just makes scores easier to read
            numScored++;
          }
          //	  else System.out.println(govlemmakey + " skipped ");
        }
      }
      float sentenceScore = scoreSum / (float)numScored;
      System.out.println("\nsid: " + sid + "\t" + sentenceScore);
      scores[i++] = new SortableObject<Integer>(sentenceScore, new Integer(sid));
      sid++;
    }

    Arrays.sort(scores);
    return scores;
  }


  /**
   * Find the pairs of words that are highly correlated based on pmi scores.
   */
  public List<String> extractBestPairs(Vector<String> parses,
      Vector<Vector<TypedDependency>> alldeps,
      Vector<EntityMention> entities) {
    System.out.println("extractBestPairs top!");
    TreeFactory tf = new LabeledScoredTreeFactory();
    Set<Integer> seenIndices = new HashSet<Integer>();
    int sid = 0;
    List<String> tokensLemmas = new ArrayList<String>();
    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);
    List<SortableObject<String>> saliences = new ArrayList<SortableObject<String>>();

    // Get all words that have arguments.
    for( Vector<TypedDependency> sentDeps : alldeps ) {
      seenIndices.clear();

      // Find any particles ahead of time - O(n)
      Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);

      for( TypedDependency dep : sentDeps ) {
        int govIndex = dep.gov().index();
        if( !seenIndices.contains(govIndex) ) {

          EntityMention mention = mentionAtIndex(entities, sid, 
              dep.dep().index(), dep.gov().index());
          // Arguments must be part of an entity mention.
          if( mention != null ) {
            Tree tree = TreeOperator.stringToTree(parses.elementAt(sid), tf);
            seenIndices.add(govIndex);
            String gov = dep.gov().label().value().toString().toLowerCase();
            Tree subtree = TreeOperator.indexToSubtree(tree, dep.gov().index());
            String govPOSTag = subtree.label().value();
            String govLemma = _wordnet.lemmatizeTaggedWord(gov, govPOSTag);
            if( CountVerbDeps.isNumber(gov) ) {
              gov = CountVerbDeps.NUMBER_STRING;
              govLemma = CountVerbDeps.NUMBER_STRING;
            }
            String particle = particles.get(govIndex);
            if( particle != null ) {
              gov = gov + "_" + particle;
              govLemma = govLemma + "_" + particle;
            }
            char normalPOS = CalculateIDF.normalizePOS(govPOSTag);

            // Only accept nouns, verbs, adjectives.
            if( normalPOS != 'o' ) {
              String govlemmakey = CalculateIDF.createKey(govLemma, normalPOS);

              // Skip words with low IDF scores from the general corpus.
              if( _generalIDF.get(govlemmakey) < 2.1f )
                System.out.println("cluster skip token " + govlemmakey);
              else {

                // Add the word.
                if( !tokensLemmas.contains(govlemmakey) ) tokensLemmas.add(govlemmakey);

                // Get the token salience scores.
                double salience = verbDetector.salienceScore(govlemmakey, 10, _relnCountsDomain);
                saliences.add(new SortableObject<String>(salience, govlemmakey));
                //	      System.out.println(govlemmakey + "\t" + salience);
              }
            }
          }
        }
      }
      //      System.out.println();
      sid++;
    }

    // Sort the tokens by salience score.
    SortableObject<String>[] salienceArray = new SortableObject[saliences.size()];
    salienceArray = saliences.toArray(salienceArray);
    Arrays.sort(salienceArray);

    // Collect all token pairs.
    EventPairScores cache = new EventPairScores();
    int size = tokensLemmas.size();
    List<SortableObject<String>> pairs = new ArrayList<SortableObject<String>>();
    for( int i = 0; i < size-1; i++ ) {
      //      System.out.println("Counting: " + i + ". " + tokensLemmas.get(i));
      for( int j = i+1; j < size; j++ ) {
        String lemma1 = tokensLemmas.get(i);
        String lemma2 = tokensLemmas.get(j);
        if( !lemma1.equals(lemma2) ) {

          // If pairs seen at least 10 times!
          double pairCount = (double)_tokenPairCounts.getCount(lemma1, lemma2);
          if( pairCount > 10.0 ) {
            int allPairsCount = _tokenPairCounts.getTotalCount();
            double joint = (double)pairCount / (double)allPairsCount;
            int count1 = _generalIDF.getFrequency(lemma1);
            int count2 = _generalIDF.getFrequency(lemma2);
            double indep = (double)count1 / (double)_generalIDF.totalCorpusCount() *
            (double)count2 / (double)_generalIDF.totalCorpusCount();
            double pmi = joint / indep;
            System.out.println("pair: " + lemma1 + " " + lemma2 + "\t" + pairCount + "/" + allPairsCount +
                "\t=" + joint + "\t" + indep + "\t" + pmi);
            pairs.add(new SortableObject<String>(pmi, lemma1 + " " + lemma2));
            cache.addScore(lemma1, lemma2, (float)pmi);
            cache.addScore(lemma2, lemma1, (float)pmi);
          }
        }
      }
    }

    HierarchicalClustering clusterer = new HierarchicalClustering();
    List<Triple> history = clusterer.efficientCluster(tokensLemmas, cache, ClusterUtil.NEW_LINK);
    Collection<Set<Integer>> clusters = ClusterUtil.reconstructClusters(history, tokensLemmas, 4);
    System.out.println("Clusters!");
    for( Triple merge : history ) {
      System.out.println(tokensLemmas.get((Integer)merge.first()) + "\t" + tokensLemmas.get((Integer)merge.second())
          + "\t" + merge.third());
    }
    for( Set<Integer> cluster : clusters ) {
      for( Integer item : cluster ) {
        System.out.print(" " + tokensLemmas.get(item));
      }
      System.out.println();
    }
    System.exit(-1);

    // Sort the pairs by score.
    SortableObject<String>[] arr = new SortableObject[pairs.size()];
    arr = pairs.toArray(arr);
    Arrays.sort(arr);

    for( SortableObject<String> obj : arr )
      System.out.println("sorted pair: " + obj);

    return null;
  }


  /**
   * Find the top entities by selecting predicates with shared arguments, returning
   * the arguments that have received narrative scores between the two predicates.
   */  
  public List<String> extractNarrative(Vector<String> parses,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities) {

    // Create sentence based entity mentions array.
//    Vector<EntityMention> mentions[] = new Vector[parses.size()];
//    for( int i = 0; i < parses.size(); i++ )  mentions[i] = new Vector<EntityMention>();
//    for( EntityMention mention : entities )   mentions[mention.sentenceID()-1].add(mention);

    // Find all possible events.
    //    List<WordEvent> events = possibleEvents(parses, alldeps);
    List<WordEvent> events = BasicEventAnalyzer.extractEvents(parses, alldeps, entities, true, _wordnet, 0);

    // Find pairs of events (pairs of WordEvent objects).
    List<Pair<WordEvent,WordEvent>> pairs = getEventPairs(events);

    // Sort by our narrative relation scores.
    SortableObject<Pair<WordEvent,WordEvent>>[] sorted = sortPairsByPMI(pairs);
    Arrays.sort(sorted);

    // Get the entities that have received scores.
    Map<Integer,Float> totals = new HashMap<Integer, Float>();
    for( SortableObject<Pair<WordEvent,WordEvent>> score : sorted ) {
      if( score.score() > 0.0f ) {
        Pair<WordEvent,WordEvent> pair = score.key();
        Integer entityID = pair.first().sharedArgumentID(pair.second().arguments());
        //	System.out.println("entity: " + entityID);
        Util.incrementCount(totals, entityID, (float)score.score());
      }
    }

    // SORT Entities
    SortableObject<Integer>[] sorted2 = new SortableObject[totals.size()];
    int i = 0;
    for( Integer id : totals.keySet() )
      sorted2[i++] = new SortableObject<Integer>(totals.get(id), id);
    Arrays.sort(sorted2);

    // DEBUG OUTPUT
    System.out.println("---Narrative Entities---");
    for( SortableObject<Integer> obj : sorted2 )
      System.out.println(obj.key() + " " + obj.score());

    // Build the entity strings
    List<String> bestEntities = new ArrayList<String>();
    for( SortableObject<Integer> obj : sorted2 ) {
      if( obj.score() > 0.0f ) {
        bestEntities.add( entityBestString(obj.key(), entities) );
      }
    }

    return bestEntities;
  }


  /**
   * This function looks up the PMI scores in the loaded PMI file for the given
   ** list of event pairs.
   * @param pairs A list of pairs of WordEvent objects.  This function builds a lookup
   *              string from each WordEvent ... assumes the POS tag is present.
   *              e.g. Lookup "arrest*0" not "arrest"
   * @return An array of objects containing scores and the original pair objects.
   */
  private SortableObject<Pair<WordEvent,WordEvent>>[] sortPairsByPMI(List<Pair<WordEvent,WordEvent>> pairs) {
    SortableObject<Pair<WordEvent,WordEvent>>[] scores = new SortableObject[pairs.size()];
    int i = 0;

    for( Pair<WordEvent,WordEvent> pair : pairs ) {
      // Two events
      WordEvent e1 = (WordEvent)pair.first();
      WordEvent e2 = (WordEvent)pair.second();

      // Find the shared argument.
      String shared = e1.sharedArgument(e2.arguments());

      // Get the dependencies filled by the shared argument.
      int colon = shared.indexOf(':');
      String dep1 = shared.substring(0, colon);
      String dep2 = shared.substring(colon+1);

      // Building e.g. "army*1-poss"
      String k1 = EventPairScores.buildKey(WordEvent.stringify(e1.token(), e1.posTag()), dep1);
      String k2 = EventPairScores.buildKey(WordEvent.stringify(e2.token(), e2.posTag()), dep2);

      // Lookup the score for this pair.
      float domainScore = _domainPairScores.getScore(k1, k2);
      float generalScore = _generalPairScores.getScore(k1, k2);
      System.out.println("Looked up " + k1 + " and " + k2);
      System.out.printf(" = %s d=%.4f c=%.4f", pair, domainScore, generalScore);
      double score = 0.9 * domainScore + 0.1 * generalScore;
      System.out.printf(" interpolated=%.5f\n", score);
      scores[i++] = new SortableObject<Pair<WordEvent,WordEvent>>(score, pair);
    }

    return scores;
  }


  /**
   * @param events List of WordEvent events that have their arguments already labeled.
   * @return A list of event pairs ... pairs that share entity arguments.
   *         A Pair is two WordEvent objects.
   */
  private List<Pair<WordEvent,WordEvent>> getEventPairs(List<WordEvent> events) {
    List<Pair<WordEvent,WordEvent>> pairs = new LinkedList<Pair<WordEvent,WordEvent>>();
    int listSize = events.size();

    // Loop 1.
    for( int i = 0; i < listSize; i++ ) {
      WordEvent e1 = events.get(i);
      String idfkey1 = CalculateIDF.createKey(e1.token(), e1.posTag());
      //      System.out.println("-- " + e1);

      if( _generalIDF.getDocCount(idfkey1) > _docCutoff && _generalIDF.get(idfkey1) > _IDFcutoff ) {

        // Loop 2.
        for( int j = i+1; j < listSize; j++ ) {
          WordEvent e2 = events.get(j);
          String idfkey2 = CalculateIDF.createKey(e2.token(), e2.posTag());

          if( _generalIDF.getDocCount(idfkey2) > _docCutoff && _generalIDF.get(idfkey2) > _IDFcutoff ) {

            // Compare arguments, do they share the same one?
            if( e1.sharedArgument(e2.arguments()) != null ) {
              pairs.add(new Pair<WordEvent,WordEvent>(e1, e2));
            }
          }
        }
      }
    }

    return pairs;
  }


  /**
   * This is an implementation of Filatova's 2006 paper.
   * Finds syntactic relations that have high tf*idf scores where the tf
   * is from the desired domain, and the idf is from a general corpus.
   * @param verbLookupType Determines how we look up the key verbs: fila, likelihood, salience, chambers
   * @param scoreRelationWithArg If true, then domain patterns are found with their relations: kidnap-s:terrorist
   *                             If false, then patterns are just scored as: kidnap-s
   *                             Filatova did the first one...found the most frequent pattern+arg.
   * @param resolveEntities If true, seen args are replaced with their entity class' best string.
   *                        Filatova didn't do this, just used the base arg.                            
   */
  public List<String> filatovaTopVerbs(ProcessedData dataReader, String verbLookupType, boolean scoreRelationWithArg, boolean resolveEntities) {
    Vector<String> parses = dataReader.getParseStrings();
    List<EntityMention> entities = dataReader.getEntities();
    List<SortableObject<RelnAndEntity>> slots = new ArrayList<SortableObject<RelnAndEntity>>();
    TreeFactory tf = new LabeledScoredTreeFactory();

    System.out.println("filatovaTopVerbs verblookup=" + verbLookupType + " scoreWithArg=" + scoreRelationWithArg);

    // Get the most unique words for this domain - Filatova approach.
    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);

    List<String> topWords = null;    
    if( verbLookupType.equals("fila") ) {
      if( _topWordsCacheFila == null ) 
        _topWordsCacheFila = verbDetector.detectWordsFilatova(true);
      topWords = _topWordsCacheFila;
    }
    else if( verbLookupType.equals("chambers") ) {
      if( _topWordsCacheChambers == null ) 
        _topWordsCacheChambers = verbDetector.detectWordsChambers(_generalPairScores, true);
      topWords = _topWordsCacheChambers;
    }
    else if( verbLookupType.equals("likelihood") ) {
      if( _topWordsCacheLikelihood == null ) 
        _topWordsCacheLikelihood = verbDetector.detectWordsOnlyRelativeFrequencyRatio(true);
      topWords = _topWordsCacheLikelihood;
    }
    else if( verbLookupType.equals("salience") ) {
      if( _topWordsCacheSalience == null ) 
        _topWordsCacheSalience = verbDetector.detectWordsOnlyDiscourseSalience(_relnCountsDomain, true);
      topWords = _topWordsCacheSalience;
    }
    // Filatova's paper takes the top 50.
    Util.firstN(topWords, 50);

    for( String word : topWords )
      System.out.println("top: " + word + " idf=" + _generalIDF.get(word));
    System.out.println("numdeps = " + dataReader.getDependencies().size());

    // Find and score all verb-dep relations in this single document.
    int sid = 0;
    for( List<TypedDependency> sentenceDeps : dataReader.getDependencies() ) {
      Tree tree = TreeOperator.stringToTree(parses.elementAt(sid), tf);
      Map<Integer, String> particles = Ling.particlesInSentence(sentenceDeps);

      for( TypedDependency dep : sentenceDeps ) {
        EntityMention mention = mentionAtIndex(entities, sid, dep.dep().index(), dep.gov().index());
        if( mention != null ) {
          NERSpan ner = nerWithMention(dataReader.getNER(), mention);
          //	  if( ner != null ) {

          String gov = dep.gov().label().value().toString().toLowerCase();
          String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), true);
          gov = CountTokenPairs.buildTokenLemma(gov, dep.gov().index(), tree, particles, _wordnet);

          // Continue if the governor is one of our top domain words.
          if( topWords.contains(gov) ) {
            System.out.println("Found in Fila " + dep + " -- " + mention);

            // Score with domain document frequency.
            // **Filatova doesn't score things, but filters patterns out for a small
            //   set of "good" patterns that aren't ranked.

            // Score just based on the verb and relation type (no arg type).
            double finalScore = (double)_relnCountsDomain.getDocCount(gov, reln);
            System.out.println("  - reln score = " + finalScore);

            // Score with the arg: ***this is what Filatova did***
            if( scoreRelationWithArg ) {
              String arg = mention.string();
              if( ner != null ) arg = ner.type().toString();//NERSpan.typeToString(ner.type());
              else {
                arg = CountArgumentTypes.getHead(arg.toLowerCase());
                String headLemma = _wordnet.nounToLemma(arg);
                if( headLemma != null ) arg = headLemma;
              }
              finalScore = (double)_domainSlotArgCounts.getCount(CountTokenPairs.attachRelation(gov, reln), arg);
              System.out.println("  - arglookup " + CountTokenPairs.attachRelation(gov, reln) + " with arg " + arg + " = " + finalScore);
            }

            RelnAndEntity relnEntity = new RelnAndEntity(gov, gov, reln, mention);
            slots.add(new SortableObject<RelnAndEntity>(finalScore, relnEntity));
          }
          //	  }
        }
      }
      sid++;
    }

    // Sort the scores.
    SortableObject<RelnAndEntity>[] sorted = new SortableObject[slots.size()];
    sorted = slots.toArray(sorted);
    Arrays.sort(sorted);

    // Now find the entities filling these top scored verb-dep slots.
    System.out.println("---Filatova Arguments '" + verbLookupType + "'---");
    List<String> bestEntities = new LinkedList<String>();
    Set<String> added = new HashSet<String>();
    for( SortableObject<RelnAndEntity> obj : sorted ) {
      if( obj.score() > 0.0f ) {
        RelnAndEntity relnEntity = obj.key();
        String fullname = relnEntity.entityMention.string();
        if( resolveEntities ) 
          fullname = entityBestString(relnEntity.entityMention.entityID(), entities);
        if( !added.contains(fullname) ) {
          System.out.println("Fila saving " + relnEntity + " = " + fullname);
          bestEntities.add(fullname);
          added.add(fullname);
        }
      }
    }

    return bestEntities;
  }


  /**
   * This function ignores the syntactic relations, and solely looks at the verb.
   * If an argument is in a relation, any relation, we just sum the likelihood score 
   * of the verbs it is seen with and don't care if we've seen that particular relation.
   */
  public List<String> likelihoodTopVerbs(ProcessedData dataReader) {
    System.out.println("--likelihoodTopVerbs--");
    List<Tree> trees = TreeOperator.stringsToTrees(dataReader.getParseStrings());
    List<List<TypedDependency>> alldeps = dataReader.getDependencies();
    List<EntityMention> entities = dataReader.getEntities();

    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);
    List<String> topwords = (List<String>)_globalCache.get("ratio-vbnn");
    if( topwords == null ) {
      topwords = verbDetector.detectWordsOnlyRelativeFrequencyRatio(false);
      _globalCache.put("ratio-vbnn", topwords);
    }

    // Count all of the seen arguments.
    Map<Integer,Double> counts = new HashMap<Integer, Double>();
    for( String topword : topwords ) {
      if( topword.charAt(0) == 'v' || topword.charAt(0) == 'n' ) {
        double score = verbDetector.likelihoodRatio(topword);
        List<RelnAndEntity> args = argumentsOfWord(topword, trees, alldeps, entities, _wordnet, -1, false);
        for( RelnAndEntity arg : args ) {
          System.out.println("**likelihood " + topword + "\t" + score);
          System.out.println("  arg=" + arg);
          Util.incrementCount(counts, arg.entityMention.entityID(), score);
        }
      }
    }

    // Sort the scores.
    SortableScore[] scores = new SortableScore[counts.size()];
    int i = 0;
    for( Integer id : counts.keySet() )
      scores[i++] = new SortableScore(counts.get(id), entityBestString(id, entities));
    Arrays.sort(scores);

    List<String> bestEntities = new ArrayList<String>();
    for( SortableScore score : scores ) {
      System.out.println("Likelihood saving " + score);
      bestEntities.add(score.key());
    }

    return bestEntities;
  }


  /**
   * Gets all arguments of verbs that are sorted by their salience score.
   * Salience is how often the words had coref arguments in the domain.
   * @param sumscores If true, then each argument in a salient slot sums its salience scores
   *                  across all slots it appears in.  If false, it saves the highest score.
   */
  public List<String> salienceTopVerbs(ProcessedData dataReader, boolean sumscores) {
    System.out.println("--salienceTopVerbs-- (sumscores " + sumscores + ")");
    List<Tree> trees = TreeOperator.stringsToTrees(dataReader.getParseStrings());
    List<List<TypedDependency>> alldeps = dataReader.getDependencies();
    List<EntityMention> entities = dataReader.getEntities();

    // Get the most unique words for this domain by coref tendencies.
    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);
    if( _topWordsCacheSalience == null ) 
      _topWordsCacheSalience = verbDetector.detectWordsOnlyDiscourseSalience(_relnCountsDomain, false);
    //    Util.firstN(_topWordsCacheSalience, 50);

    // Count all of the seen arguments.
    Map<Integer,Double> counts = new HashMap<Integer, Double>();
    for( String topword : _topWordsCacheSalience ) {
      if( topword.charAt(0) == 'v' || topword.charAt(0) == 'n' ) {
        //        double salienceScore = verbDetector.salienceScore(topword, 0, _relnCountsDomain);
        double salienceScore = DomainVerbDetector.discourseSalienceScore(topword, 20, _relnCountsDomain, _domainIDF);
        System.out.println("**salience " + topword + "\t" + salienceScore);

        List<RelnAndEntity> args = argumentsOfWord(topword, trees, alldeps, entities, _wordnet, -1, false);
        for( RelnAndEntity arg : args ) {
          Integer id = arg.entityMention.entityID();
          System.out.println("  arg=" + arg + " " + salienceScore);
          if( sumscores )
            Util.incrementCount(counts, id, salienceScore);
          else {
            if( !counts.containsKey(id) || counts.get(id) < salienceScore )
              counts.put(id, salienceScore);
          }
          System.out.println("  arg score now " + counts.get(id));
        }
      }
    }

    // Sort the scores.
    SortableScore[] scores = new SortableScore[counts.size()];
    int i = 0;
    for( Integer id : counts.keySet() )
      scores[i++] = new SortableScore(counts.get(id), entityBestString(id, entities));
    Arrays.sort(scores);

    List<String> bestEntities = new ArrayList<String>();
    for( SortableScore score : scores ) {
      bestEntities.add(score.key());
      System.out.println("salience best " + score.key());
    }

    return bestEntities;
  }


  /**
   * Get all arguments attached to top scoring words.
   * Words are scored by their likelihood and salience scores.
   */
  public List<String> salienceAndRatioTopVerbs(ProcessedData dataReader) {
    System.out.println("--salienceTopVerbs--");
    List<Tree> trees = TreeOperator.stringsToTrees(dataReader.getParseStrings());
    List<List<TypedDependency>> alldeps = dataReader.getDependencies();
    List<EntityMention> entities = dataReader.getEntities();

    // Get the most unique words for this domain by coref tendencies.
    DomainVerbDetector verbDetector = new DomainVerbDetector(_domainIDF, _generalIDF);
    if( _topScoresCacheSalienceAndRatio == null ) 
      _topScoresCacheSalienceAndRatio = verbDetector.detectWordsSalienceAndRatio(_relnCountsDomain, false);

    // Count all of the seen arguments.
    Map<Integer,Double> counts = new HashMap<Integer, Double>();
    for( SortableScore topscore : _topScoresCacheSalienceAndRatio ) {
      String topword = topscore.key();
      if( topword.charAt(0) == 'v' || topword.charAt(0) == 'n' ) {
        double mergedScore = topscore.score();
        System.out.println("**salience " + topword + "\t" + mergedScore);

        List<RelnAndEntity> args = argumentsOfWord(topword, trees, alldeps, entities, _wordnet, -1, false);
        for( RelnAndEntity arg : args ) {
          System.out.println("  arg=" + arg);
          Util.incrementCount(counts, arg.entityMention.entityID(), mergedScore);
        }
      }
    }

    // Sort the scores.
    SortableScore[] scores = new SortableScore[counts.size()];
    int i = 0;
    for( Integer id : counts.keySet() )
      scores[i++] = new SortableScore(counts.get(id), entityBestString(id, entities));
    Arrays.sort(scores);

    List<String> bestEntities = new ArrayList<String>();
    for( SortableScore score : scores )
      bestEntities.add(score.key());

    return bestEntities;
  }


  /**
   * This is an implementation of Sudo's 2002 paper.
   * Finds syntactic relations that have high tf*idf scores where the tf
   * and idf are from the domain.
   */
  public List<String> sudoTopIDFs(Vector<String> parses,
      Vector<Vector<TypedDependency>> alldeps,
      Vector<EntityMention> entities) {
    List<SortableObject<RelnAndEntity>> slots = new ArrayList<SortableObject<RelnAndEntity>>();
//    Set<String> seen = new HashSet<String>();
    TreeFactory tf = new LabeledScoredTreeFactory();
    int sid = 0;

    // Find and score all verb-dep relations in this single document.
    for( Vector<TypedDependency> sentenceDeps : alldeps ) {
      Tree tree = TreeOperator.stringToTree(parses.elementAt(sid), tf);

      for( TypedDependency dep : sentenceDeps ) {
        EntityMention mention = mentionAtIndex(entities, sid, 
            dep.dep().index(), dep.gov().index());

        if( mention != null ) {
          String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), false);
          int govIndex = dep.gov().index();
          String gov = dep.gov().label().value().toString().toLowerCase();
          Tree subtree = TreeOperator.indexToSubtree(tree, govIndex);
          String govPOSTag = subtree.label().value();
          gov = _wordnet.lemmatizeTaggedWord(gov, govPOSTag);
          if( CountVerbDeps.isNumber(gov) ) 
            gov = CountVerbDeps.NUMBER_STRING;

          System.out.println("Found " + dep + " -- " + mention);
//          String govreln = gov + "-" + reln;

          // Lookup the IDF score for this slot. (Sudo's score).
          double idf = 12.0;
          if( _relnCountsDomain.getDocCount(gov, reln) > 0 )
            idf = Math.log((double)_relnCountsDomain.getTotalDocs() / 
                (double)_relnCountsDomain.getDocCount(gov, reln));

          // Domain term frequency * General corpus IDF
          double finalScore = (double)_relnCountsDomain.getCount(gov, reln) * idf;
          RelnAndEntity relnEntity = new RelnAndEntity(gov, gov, reln, mention);
          slots.add(new SortableObject<RelnAndEntity>(finalScore, relnEntity));
        }
      }
      sid++;
    }

    // Sort the scores.
    SortableObject<RelnAndEntity>[] sorted = new SortableObject[slots.size()];
    sorted = slots.toArray(sorted);
    Arrays.sort(sorted);

    // Now find the entities filling these top scored verb-dep slots.
    System.out.println("---Sudo IDF Reln Entities---");
    List<String> bestEntities = new ArrayList<String>();
    Set<String> added = new HashSet<String>();
    for( SortableObject<RelnAndEntity> obj : sorted ) {
      RelnAndEntity relnEntity = obj.key();
      //      System.out.println("IDF: " + relnEntity + " " + obj.score() + " --> " + entityBestString(relnEntity.entityMention.entityID(), entities));
      String fullname = entityBestString(relnEntity.entityMention.entityID(), entities);
      if( !added.contains(fullname) ) {
        bestEntities.add(fullname);
        added.add(fullname);
      }
    }

    return bestEntities;
  }


  /**
   * Look at the likelihood ratio of each slot in the document, sort by highest scoring
   * slots.
   */
  public List<String> extractUsingLikelihood(ProcessedData dataReader) {
    List<EntityMention> entities = dataReader.getEntities();
    List<NERSpan> ners = dataReader.getNER();
    List<Tree> trees = TreeOperator.stringsToTrees(dataReader.getParseStrings());
    List<SortableObject<RelnAndEntity>> slots = new ArrayList<SortableObject<RelnAndEntity>>();

    // Find and score all verb-dep relations in this single document.
    int sid = 0;
    for( List<TypedDependency> sentenceDeps : dataReader.getDependencies() ) {
      Tree tree = trees.get(sid);

      for( TypedDependency dep : sentenceDeps ) {
        EntityMention mention = mentionAtIndex(entities, sid, dep.dep().index(), dep.gov().index());
        if( mention != null ) {
          NERSpan ner = nerWithMention(ners, mention);
          if( ner != null ) {

            String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), false);
            int govIndex = dep.gov().index();
            String gov = dep.gov().label().value().toString().toLowerCase();
            Tree subtree = TreeOperator.indexToSubtree(tree, govIndex);
            String govPOSTag = subtree.label().value();
            gov = _wordnet.lemmatizeTaggedWord(gov, govPOSTag);
            if( CountVerbDeps.isNumber(gov) ) 
              gov = CountVerbDeps.NUMBER_STRING;

            System.out.println("Likelihood with " + dep + " -- " + mention + " -- " + ner);
//            String govreln = gov + "-" + reln;

            // Calculate likelihood ratio.
            int domainCount = _relnCountsDomain.getCount(gov, reln);
            int corpusCount = _relnCountsGeneral.getCount(gov, reln);
            double domainProb = (double)domainCount / (double)_relnCountsDomain.getTotalCount();
            double corpusProb = (double)corpusCount / (double)_relnCountsGeneral.getTotalCount();
            double likelihoodRatio = 0.0;
            if( domainProb > 0.0 )
              likelihoodRatio = domainProb / (corpusProb > 0.0 ? corpusProb : 100.0*domainProb);

            RelnAndEntity relnEntity = new RelnAndEntity(gov, gov, reln, mention);
            slots.add(new SortableObject<RelnAndEntity>(likelihoodRatio, relnEntity));
          }
        }
      }
      sid++;
    }

    // Sort the scores.
    SortableObject<RelnAndEntity>[] sorted = new SortableObject[slots.size()];
    sorted = slots.toArray(sorted);
    Arrays.sort(sorted);

    // Now find the entities filling these top scored verb-dep slots.
    System.out.println("---Likelihood Ratio Reln Entities---");
    List<String> bestEntities = new ArrayList<String>();
    Set<String> added = new HashSet<String>();
    for( SortableObject<RelnAndEntity> obj : sorted ) {
      RelnAndEntity relnEntity = obj.key();
      String fullname = entityBestString(relnEntity.entityMention.entityID(), entities);
      if( !added.contains(fullname) ) {
        System.out.println("- " + fullname);
        bestEntities.add(fullname);
        added.add(fullname);
      }
    }

    return bestEntities;
  }


  /**
   * @param sid The sentence index, starting at zero.
   * @param index The index of the mention we desire, it should include this.
   * @param avoidIndex Usually the index of the governor of a relation, and
   *                   we don't want the mention to include this in its span.
   *                   For instance, we don't want noun-noun relations.
   */
  public static EntityMention mentionAtIndex(Collection<EntityMention> mentions,
      int sid, int index, int avoidIndex) {
    for( EntityMention mention : mentions ) {
      // EntityMentions start at sentence 1, not 0.
      if( mention.sentenceID()-1 == sid &&
          mention.start() <= index && mention.end() >= index &&
          (mention.start() > avoidIndex || mention.end() < avoidIndex) )
        return mention;
    }
    return null;
  }

  private NERSpan nerWithMention(Collection<NERSpan> ners, EntityMention mention) {
    for( NERSpan ner : ners ) {
      if( mentionSubsumesNER(mention, ner) ) {
        return ner;
      }
    }
    return null;
  }

  /**
   * @return True if the NER span is completely contained within the
   *         EntityMention's text span.
   */
  public static boolean mentionSubsumesNER(EntityMention mention, NERSpan ner) {
    if( ner.sid() == mention.sentenceID()-1 && 
        ner.start() >= mention.start() && ner.end() <= mention.end()+1 )
      return true;
    else return false;
  }


  //  public static void main(String[] args) {
  //    TemplateExtractor extractor = new TemplateExtractor(args);
  //    extractor.searchDocument();
  //  }
}

package nate;

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

import nate.EntityMention;
import nate.args.CountArgumentTypes;
import nate.reading.ProcessedData;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.SortableScore;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;
import nate.util.WordPosition;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * Count all pairs of (NN or VB or JJ) tokens seen together in documents. (v-arrest)
 * Use "-relations" to count pairs with specific dependencies. (v-arrest:s)
 * Use "-withcoref" to count pairs with deps that corefer (v-arrest:s)
 *
 * -deps
 * Typed dependencies file.
 *
 * -parsed
 * Parses file.
 * 
 * -events
 * Events file. (only needed if -withcoref flag is on)
 * 
 * -ner
 * NER file (only needed if -withner flag is on, for collocations)
 *
 * -type [base | vb | nn | vbnn | vbnom]
 * Which class of tokens to count.
 * (all, verbs, nouns, verbs+nouns, verbs+nominals)
 *
 * -dependents
 * If present, we count dependents with the governors.  Default is just governors.
 * 
 * -relations
 * If present, this class counts tokens with their seen dependencies, not just plain tokens. 
 * 
 * -nodistance
 * Disables counting by distance within a doc, and simply counts by existence in the same doc.
 * 
 * -withcoref
 * Count pairs with coreferring args only.
 * 
 * -output
 * The directory to write the token counts file to.
 */
public class CountTokenPairs {
  String _depsPath;
  String _parsePath;
  String _corefPath;
  String _nerPath;
  String _outDirectory = ".";
  String _outFileLemmas = "token-pairs-lemmas.counts";
//  String _outfile = "token-pairs.counts";
  int _totalCount = 0;
  boolean _includeDependents = false;
  boolean _includeRelations = false;
  boolean _countByDistance = true;
  boolean _withCoref = false;
  boolean _fullPrep = false; // true if you want "p_during" and not just "p"
  boolean _countObjectCollocations = false; // true if you want to count collocations too
  
  public static final int BASE = 0;
  public static final int VERBS = 1;
  public static final int NOUNS = 2;
  public static final int VERBS_AND_NOUNS = 3;
  public static final int VERBS_AND_NOMINALS = 4;
  public static final int ALL = 5;
  int _tokenType = VERBS_AND_NOMINALS;

  private String _lockDir = "locks";
  private String _duplicatesPath = "duplicates";
  private Set<String> _duplicates;
  Map<String,Map<String,Integer>> _counts;
  Map<String,Map<String,Integer>> _countsLemmas;
  Map<String,Map<String,Float>> _countsFloat;
  Map<String,Map<String,Float>> _countsLemmasFloat;
  IDFMap _idf; // lemma idf
  // Individual tokens must have at least this IDF to be counted in a pair.
  float _idfCutoff = 0.9f;
  // Individual tokens must appear at least this many times to be counted in a pair.
  int _docCutoff = 10;
  WordNet _wordnet;
  int _numStories = 0;
  Set<String> _ignore = null;
  boolean _loadedInversesInt = false;

  
  CountTokenPairs(String args[]) {
    HandleParameters params = new HandleParameters(args);
    _depsPath = params.get("-deps");
    _parsePath = params.get("-parsed");
    _corefPath = params.get("-events");
    _nerPath = params.get("-ner");
    
    // Set the token type to count.
    if( params.hasFlag("-type") ) 
      _tokenType = getType(params.get("-type"));
    System.out.println("tokenType\t" + getStringType(_tokenType));

    // Are we counting dependents and not just governors?
    if( params.hasFlag("-dependents") ) 
      _includeDependents = true;
    System.out.println("dependents\t" + _includeDependents);

    if( params.hasFlag("-relations") ) 
      _includeRelations = true;
    System.out.println("relations\t" + _includeRelations);

    if( params.hasFlag("-objects") ) 
      _countObjectCollocations = true;
    System.out.println("objCollocations\t" + _countObjectCollocations);

    if( params.hasFlag("-withcoref") ) 
      _withCoref = true;
    System.out.println("withcoref\t" + _withCoref);
    
    if( _includeDependents && _includeRelations ) {
      System.out.println("ERROR: including relations can't include dependents too.");
      System.exit(-1);
    }
    
    if( params.hasFlag("-fullprep") ) 
      _fullPrep = true;
    System.out.println("fullprep\t" + _fullPrep);
    
    if( params.hasFlag("-nodistance") ) 
      _countByDistance = false;
    System.out.println("countByDistance\t" + _countByDistance);

    if( params.hasFlag("-output") )
      _outDirectory = params.get("-output");
    else {
      System.out.println("ERROR: no -output directory given");
      System.exit(-1);
    }
    
    if( params.hasFlag("-idfcut") ) 
      _idfCutoff = Float.parseFloat(params.get("-idfcut"));
    System.out.println("idfcut\t" + _idfCutoff);

    if( params.hasFlag("-doccut") ) 
      _docCutoff = Integer.parseInt(params.get("-doccut"));
    System.out.println("doccut\t" + _docCutoff);
    
    // Sanity check.
    if( _countByDistance && _withCoref ) {
      System.out.println("We can't count by distance and by coref at the same time...both are set to true.");
      System.exit(-1);
    }
    
    // Load WordNet
    _wordnet = new WordNet(WordNet.findWordnetPath());

    // Initialize count map
    _counts = new HashMap<String, Map<String, Integer>>();
    _countsLemmas = new HashMap<String, Map<String, Integer>>();
//    _countsFloat = new HashMap<String, Map<String, Float>>();
    _countsLemmasFloat = new HashMap<String, Map<String, Float>>();

    // Duplicate Gigaword files to ignore
    if( new File(_duplicatesPath).exists() )
      _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);

    // IDF scores (lemmas)
    _idf = new IDFMap(params.get("-idf"));

    // Ignore list.
//    _ignore = new HashSet<String>();
//    String[] arr = { "n-fmln", "n-salvador", "n-front", "n-force", "n-army", "n-guerrilla",
//        "n-eln", "n-group", "n-department" };
//    for( String word : arr ) _ignore.add(word);
  }

  /**
   * Only use this constructor if you want transitive verbs loaded
   * from file, and not actually counting verb-deps from a corpus.
   */
  public CountTokenPairs() {
    _counts = null;
    _countsLemmas = null;
    _countsFloat = null;
    _countsLemmasFloat = null;
  }

  public CountTokenPairs(String filename, boolean integers) {
    if( integers ) intsFromFile(filename, false);
    else fromFile(filename, null);
  }
  
  public CountTokenPairs(String filename) {
    this();
    fromFile(filename, null);
  }

  public static int getType(String type) {
    if( type.equals("all") ) return ALL;
    else if( type.equals("base") ) return BASE;
    else if( type.equals("vb") ) return VERBS;
    else if( type.equals("nn") ) return NOUNS;
    else if( type.equals("vbnn") ) return VERBS_AND_NOUNS;
    else if( type.equals("vbnom") ) return VERBS_AND_NOMINALS;
    else {
      System.out.println("Unknown token type: " + type);
      System.exit(-1);
    }
    return BASE;
  }
  
  public static String getStringType(int type) {
    if( type == ALL ) return "ALL";
    else if( type == BASE ) return "BASE";
    else if( type == VERBS ) return "VERBS";
    else if( type == NOUNS ) return "NOUNS";
    else if( type == VERBS_AND_NOUNS ) return "VERBS_AND_NOUNS";
    else if( type == VERBS_AND_NOMINALS ) return "VERBS_AND_NOMINALS";
    else {
      System.out.println("Unknown int type: " + type);
      System.exit(-1);
    }
    return null;
  }

  /**
   * Tally a verb/dep into our global _counts hashmaps.
   */
  private void incrementCount(Map<String,Map<String,Integer>> counts, String token1, String token2, int amount) {
    // Alphabetize.
    if( token1.compareTo(token2) > 0 ) {
      String temp = token1;
      token1 = token2;
      token2 = temp;
    }

    // Find the first token.
    Map<String,Integer> tokenMap;
    if( !counts.containsKey(token1) ) {
      tokenMap = new HashMap<String, Integer>(8);
      counts.put(token1, tokenMap);
    } else 
      tokenMap = counts.get(token1);

    // Increment the pair.
    if( !tokenMap.containsKey(token2) ) tokenMap.put(token2, amount);
    else {
      Integer count = tokenMap.get(token2);
      tokenMap.put(token2, count+amount);
    }
  }

  private void incrementFloatCount(Map<String,Map<String,Float>> counts,
      String token1, String token2, float amount) {
    // Alphabetize.
    if( token1.compareTo(token2) > 0 ) {
      String temp = token1;
      token1 = token2;
      token2 = temp;
    }

    //    System.out.println("incrementing " + token1 + " " + token2 + " " + amount);

    // Find the first token.
    Map<String,Float> tokenMap;
    if( !counts.containsKey(token1) ) {
      tokenMap = new HashMap<String, Float>(8);
      counts.put(token1, tokenMap);
    } else 
      tokenMap = counts.get(token1);

    // Increment the pair.
    if( !tokenMap.containsKey(token2) ) tokenMap.put(token2, amount);
    else {
      Float count = tokenMap.get(token2);
      tokenMap.put(token2, count+amount);
    }
  }

  private void setCount(Map<String,Map<String,Integer>> counts,
      String token1, String token2, int amount) {
    // Alphabetize.
    if( token1.compareTo(token2) > 0 ) {
      String temp = token1;
      token1 = token2;
      token2 = temp;
    }

    // Find the first token.
    Map<String,Integer> tokenMap;
    if( !counts.containsKey(token1) ) {
      tokenMap = new HashMap<String, Integer>(8);
      counts.put(token1, tokenMap);
    } else 
      tokenMap = counts.get(token1);

    // Increment the pair.
    tokenMap.put(token2, amount);
  }

  /**
   * Print the token pairs with their counts.
   */
  private void countsToFile(Map<String,Map<String,Integer>> counts, int numDocs, String outfile) {
    System.out.println("tofile " + outfile + " with " + counts.size() + " entries.");
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(outfile));
      out.write("NUMDOCS " + numDocs + "\n");

      // Sort the keys.
      String[] keys = new String[counts.size()];
      keys = counts.keySet().toArray(keys);
      Arrays.sort(keys);

      // Print the keys in sorted order.
      for( String key : keys ) {
        Map<String,Integer> tokenCounts = counts.get(key);

        if( tokenCounts != null && tokenCounts.size() > 0 ) {
          out.write(key);

          // Sort the counts.
          SortableScore[] scores = new SortableScore[tokenCounts.size()];
          int i = 0;
          for( Map.Entry<String,Integer> entry2 : tokenCounts.entrySet() )
            scores[i++] = new SortableScore(entry2.getValue(), entry2.getKey());
          Arrays.sort(scores);

          for( SortableScore score : scores )
            out.write("\t" + score.key() + "\t" + (int)score.score());
          out.write("\n");
        }
      }

      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * Print the token pairs with their counts.
   */
  private void floatsToFile(Map<String,Map<String,Float>> counts, int numDocs,
      String outfile) {
    System.out.println("tofile " + outfile + " with " + counts.size() + " entries.");
    try {
      PrintWriter out = new PrintWriter(new FileWriter(outfile));
      //      BufferedWriter out = new BufferedWriter(new FileWriter(outfile));
      out.write("NUMDOCS " + numDocs + "\n");

      for( Map.Entry<String,Map<String,Float>> entry : counts.entrySet() ) {
        Map<String,Float> tokenCounts = entry.getValue();

        if( tokenCounts != null && tokenCounts.size() > 0 ) {
          out.write(entry.getKey());

          SortableScore[] scores = new SortableScore[tokenCounts.size()];
          int i = 0;
          for( Map.Entry<String,Float> entry2 : tokenCounts.entrySet() )
            scores[i++] = new SortableScore(entry2.getValue(), entry2.getKey());
          Arrays.sort(scores);

          for( SortableScore score : scores ) {
            out.printf("\t%s\t%.2f", score.key(), score.score());
          }
          out.write("\n");
        }
      }

      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * @param reln The full Stanford relation string.
   * @param fullprep True if you want 'p_during', False if you just want 'p'.
   * @return A (possibly) shortened version of the relation, merging several
   *         types into a smaller set of relations.
   */
  public static String normalizeRelation(String reln, boolean fullprep) {
    if( reln.contains("conj") ) return "conj";
    // ccomp, acomp, xcomp
    else if( reln.contains("comp") && !reln.equals("complm") ) return "comp";
    else if( reln.contains("mod") && 
        !reln.equals("advmod") && !reln.equals("tmod") && !reln.equals("quantmod") ) 
      return "mod";
    else {
      if( fullprep ) return WordEvent.normalizeRelationFullPrep(reln);
      else return WordEvent.normalizeRelation(reln);
    }
  }


  // PMI = P(x,y) / P(x)P(y)
  //     = (C(x,y) / C(all-pairs))  /  (C(x)*C(y)/C(all-tokens)^2)
  /**
   * Read a counts file into memory - reads integers.
   */
  public void intsFromFile(String path, boolean loadInverseCounts) {
    intsFromFile(path, null, loadInverseCounts);
  }
  public void intsFromFile(String path, Collection<String> include, boolean loadInverseCounts) {
    String line = null;
    if( _counts == null ) _counts = new HashMap<String, Map<String, Integer>>();
    else _counts.clear();
    _totalCount = 0;

    System.out.println("CountTokenPairs fromFile " + path);
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      int numlines = 0;
      line = in.readLine();

      // Save the total number of documents these counts are from.
      if( line != null && line.startsWith("NUMDOCS ") )
        _numStories = Integer.valueOf(line.substring(8));

      // e.g. "spearhead o 16 p 4 s 12"
      while( (line = in.readLine()) != null ) {
        String parts[] = line.split("\\s+");

        if( parts.length < 3 || (parts.length % 2 != 1) ) {
          System.out.println("Strange line format (skipping): " + line);
          System.exit(1);
        }
        else {
          // Extract the verb
          String mainToken = parts[0];
          String base = mainToken;
          if( include != null ) {
            int colon = mainToken.indexOf(':');
            if( colon > -1 ) base = mainToken.substring(0,colon);
          }
          if( include == null || include.contains(base) ) {
            Map<String,Integer> tokenMap = _counts.get(mainToken);
            if( tokenMap == null ) {
              tokenMap = new HashMap<String, Integer>(8);
              _counts.put(mainToken, tokenMap);
            }
            // Extract the dependent types
            for( int i = 1; i < parts.length; i += 2 ) {
              base = parts[i];
              if( include != null ) {
                int colon = base.indexOf(':');
                if( colon > -1 ) base = base.substring(0,colon);
              }
              if( include == null || include.contains(base) ) {
                String token2 = parts[i];
                Integer count = Integer.valueOf(parts[i+1]);
                Util.incrementCount(tokenMap, token2, count);
//                tokenMap.put(parts[i], count);
                                
                // Now add this count to the second token's map ... we have this count
                // twice in memory for quicker lookup later of entire vectors.
                if( loadInverseCounts ) {
                  Map<String,Integer> tokenMap2 = _counts.get(token2);
                  if( tokenMap2 == null ) {
                    tokenMap2 = new HashMap<String, Integer>(8);
                    _counts.put(token2, tokenMap2);
                  }
                  Util.incrementCount(tokenMap2, mainToken, count);
                }
                
                _totalCount += count;
              }
            }
            numlines++;
          }
        }
      }
      System.out.println("read " + numlines + " lines. num pair heads " + _counts.size());
    } catch( Exception ex ) { 
      System.out.println("Error on line: " + line);
      ex.printStackTrace(); 
      System.exit(-1); 
    }
    _loadedInversesInt = loadInverseCounts;
  }
  
  /**
   * Read a counts file into memory - reads floats.
   */
  public void fromFile(String path) {
    fromFile(path, null);
  }
  
  public void fromFile(String path, Collection<String> include) {
    String line = null;
    if( _countsFloat == null ) _countsFloat = new HashMap<String, Map<String, Float>>();
    else _countsFloat.clear();
    _totalCount = 0;

    System.out.println("CountTokenPairs fromFile " + path);
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      int numlines = 0;
      line = in.readLine();

      // Save the total number of documents these counts are from.
      if( line != null && line.startsWith("NUMDOCS ") )
        _numStories = Integer.valueOf(line.substring(8));

      // e.g. "spearhead o 16 p 4 s 12"
      while( (line = in.readLine()) != null ) {
        String parts[] = line.split("\\s+");

        if( parts.length < 3 || (parts.length % 2 != 1) ) {
          System.out.println("Strange line format (skipping): " + line);
          System.exit(1);
        }
        else {
          // Extract the verb
          String mainToken = parts[0];
          String base = mainToken;
          if( include != null ) {
            int colon = mainToken.indexOf(':');
            if( colon > -1 ) base = mainToken.substring(0,colon);
          }
          if( include == null || include.contains(base) ) {
            Map<String,Float> tokenMap = new HashMap<String, Float>(8);
            _countsFloat.put(mainToken, tokenMap);
            // Extract the dependent types
            for( int i = 1; i < parts.length; i += 2 ) {
              base = parts[i];
              if( include != null ) {
                int colon = base.indexOf(':');
                if( colon > -1 ) base = base.substring(0,colon);
              }
              if( include == null || include.contains(base) ) {
                Float count = Float.valueOf(parts[i+1]);
                //	    System.out.println("pairs fromFile " + parts[i+1] + " = " + count);
                Util.incrementCount(tokenMap, parts[i], count);
                //	    System.out.println("  retrieved " + getCount(token1, parts[i]));
                _totalCount += count;
              }
            }
            numlines++;
          }
        }
      }
      System.out.println("read " + numlines + " lines");
    } catch( Exception ex ) { 
      System.out.println("Error on line: " + line);
      ex.printStackTrace(); 
      System.exit(-1); 
    }
  }

  /**
   * Find all tokens in the pair counts that start with the given string.
   */
  public Set<String> tokensThatStartWith(String start) {
    Set<String> tokens = new HashSet<String>();
    for( Map.Entry<String, Map<String,Integer>> entry : _counts.entrySet() ) {
      if( entry.getKey().startsWith(start) )
        tokens.add(entry.getKey());
      for( String token : entry.getValue().keySet() ) {
        if( token.startsWith(start) )
          tokens.add(token);
      }
    }
    return tokens;
  }

  public Map<String,Integer> getCountsInt(String token) {
    return _counts.get(token);
  }
  
  /**
   * Get the number of times a token and a relation were seen.
   * Returns zero if token-reln is not found.
   */
  public float getCount(String token1, String token2) {
    return getCountFloat(token1, token2, _countsFloat);
  }
  public float getCountFloat(String token1, String token2, Map<String,Map<String,Float>> counts) {
    // Alphabetize before lookup.
    if( token1.compareTo(token2) > 0 ) {
      String temp = token1;
      token1 = token2;
      token2 = temp;
    }

    if( counts != null ) {
      Map<String,Float> tokenMap = counts.get(token1);
      if( tokenMap != null ) {
        Float count = tokenMap.get(token2);
        if( count == null ) return 0.0f;
        else return count.floatValue();
      }
      else return 0.0f;
    }
    else return 0.0f;
  }

  public int getCountInteger(String token1, String token2) {
    return getCountInteger(token1, token2, _counts);
  }
  public int getCountInteger(String token1, String token2, Map<String,Map<String,Integer>> counts) {
    // Alphabetize before lookup.
    if( token1.compareTo(token2) > 0 ) {
      String temp = token1;
      token1 = token2;
      token2 = temp;
    }

    if( counts != null ) {
      Map<String,Integer> tokenMap = counts.get(token1);
      if( tokenMap != null ) {
        Integer count = tokenMap.get(token2);
        if( count == null ) return 0;
        else return count.intValue();
      }
      else return 0;
    }
    else return 0;
  }

  /**
   * This function searches all pairs and builds a vector for the given token that contains
   * all other tokens it appears with, and their counts.  This is O(n) because our
   * index is memory-friendly and everything is in ABC order.  We have to search the whole
   * thing to see where our token appears...
   * @param token The desired token (e.g. v-run).
   * @return All tokens and their counts with our desired token.
   */
  public Map<String,Integer> getCountVector(String token) {
    if( _loadedInversesInt ) {
//      System.out.println("getCountVector " + token + " simply returns!");
      return _counts.get(token);
    }
    // The pairs are stored in ABC order to save space, so we have to reconstruct it.
    else {
      Map<String,Integer> vector = new HashMap<String,Integer>();
      Map<String,Integer> initial = _counts.get(token);
      if( initial != null ) vector.putAll(initial);

      // Now get all other tokens that appear before it alphabetically...
      for( Map.Entry<String, Map<String,Integer>> entry : _counts.entrySet() ) {
        Integer count = entry.getValue().get(token);
        if( count != null && count > 0 )
          vector.put(entry.getKey(), count);
      }
      return vector;
    }
  }  
  
  public int getTotalCount() { return _totalCount; }
  public int getTotalDocs() { return _numStories; }

  
  public Set<String> intKeySet() {
    return _counts.keySet();
  }

  public Set<String> floatKeySet() {
    return _countsFloat.keySet();
  }
  
  public Set<String> intKeySet(String lemma1) {
    return _counts.get(lemma1).keySet();
  }

  public Set<String> floatKeySet(String lemma1) {
    return _countsFloat.get(lemma1).keySet();
  }

  public static boolean tokenMatchesDesiredType(String lemma, char normalPOS, 
      WordNet wordnet, int type) {
    if( type == ALL ) { // anything and everything
      return true;
    }
    if( type == BASE ) { // nouns, verbs, adjectives
      if( normalPOS != 'o' ) return true;
    }
    else if( type == VERBS ) {
      if( normalPOS == 'v' ) return true;
    }
    else if( type == NOUNS ) {
      if( normalPOS == 'n' ) return true;
    }
    else if( type == VERBS_AND_NOUNS ) {
      if( normalPOS == 'n' || normalPOS == 'v' ) return true;
    }
    else if( type == VERBS_AND_NOMINALS ) {
      // All verbs.
      if( normalPOS == 'v' ) return true;
      // Nouns that are events.
      //      if( isNominative(lemma, normalPOS, wordnet) ) {
      if( normalPOS == 'n' && wordnet.isNounEvent(lemma) ) {
        //	System.out.println("nominal " + lemma);
        return true;
      }
    }
    return false;
  }

  
  /**
   * Find the NE tag that includes part of the given mention.
   * @param sid The sentence id (indexed from 0)
   * @param index The word position in the sentence (indexed from 1)
   * @param ners List of all NER labels in the document.
   * @return A single NE tag type.
   */
  public static NERSpan.TYPE nerTagOfIndex(int sid, int index, List<NERSpan> ners) {
    for( NERSpan span : ners ) {
      if( span.sid() == sid ) {
        if( span.start() <= index && span.end() > index ) {
          return span.type();
        }
      }
    }
    return NERSpan.TYPE.NONE;
  }
   
  
  public static List<String> getKeyTokens(int sid, Tree tree, List<TypedDependency> sentDeps, List<NERSpan> ners, WordNet wordnet,
      Set<String> ignore, int type, boolean includeDependents, boolean includeRelations, boolean fullPrep, boolean countCollocationObjects) {
    return getKeyTokensSkipIDF(sid,tree,sentDeps,ners,wordnet,null,0.0f,0,ignore,type,includeDependents,includeRelations,fullPrep,countCollocationObjects);
  }    
  
  public static List<String> getKeyTokensSkipIDF(int sid, Tree tree, List<TypedDependency> sentDeps, List<NERSpan> ners, WordNet wordnet, IDFMap idf,
      float idfcutoff, int docCutoff, Set<String> ignore, int type, boolean includeDependents, boolean includeRelations, boolean fullPrep, boolean countCollocationObjects) {
    List<WordPosition> tokens = getKeyTokenObjectsSkipIDF(sid,tree,sentDeps,ners,wordnet,idf,idfcutoff,docCutoff,ignore,type,includeDependents,includeRelations,fullPrep,countCollocationObjects);
    List<String> strs = new ArrayList<String>(tokens.size());
    for( WordPosition token : tokens ) strs.add(token.token);
    return strs;
  }
  
  public static List<WordPosition> getKeyTokenObjects(int sid, Tree tree, List<TypedDependency> sentDeps, List<NERSpan> ners, WordNet wordnet,
      Set<String> ignore, int type, boolean includeDependents, boolean includeRelations, boolean fullPrep, boolean countCollocationObjects) {
    return getKeyTokenObjectsSkipIDF(sid,tree,sentDeps,ners,wordnet,null,0.0f,0,ignore,type,includeDependents,includeRelations,fullPrep,countCollocationObjects);
  }      
  
  
  /**
   * Get all of the tokens in the sentence that are of interest, determined by the 'type'
   * parameter.  Ignore all others.
   * Returns strings with POS tags (e.g. n-arrest) or with deps (e.g. n-arrest:pp).
   *
   * @param ignore Optional set of words to always ignore. Set to null as default.
   * @param type The type of tokens we want, use the options BASE, NOUNS, etc.
   * @param includeDependents True if you want governors and dependents, false if just governors.
   * @param includeRelations If true, will attach subjects and objects (":s") to the strings.
   * @param fullPrep If true, we normalize preps to 'p_during', otherwise it is 'p'. This only gets
   *                 looked at if includeRelations is true.   
   */
  public static List<WordPosition> getKeyTokenObjectsSkipIDF(int sid, Tree tree, List<TypedDependency> sentDeps, List<NERSpan> ners, WordNet wordnet, IDFMap idf,
      float idfcutoff, int docCutoff, Set<String> ignore, int type, boolean includeDependents, boolean includeRelations, boolean fullPrep, boolean countCollocationObjects) {
    List<WordPosition> tokens = new ArrayList<WordPosition>();
    Set<String> added = new HashSet<String>();
    Set<Integer> seenIndices = new HashSet<Integer>();

    // Get the particles.
    Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);
    // Get the objects.
//    Map<Integer, List<String>> objects = Ling.objectsInSentence(sentDeps);

    for( TypedDependency dep : sentDeps ) {
      int govIndex = dep.gov().index();
      // Temporary fix? The dependencies from objects that were in conjuncts are
      // created new, and 1000 is added to the original verb's ID.  We subtract this,
      // making the original verb the real match.
      if( govIndex > 1000 ) govIndex -= 1000;
      
      if( includeRelations || !seenIndices.contains(govIndex) ) {
        if( !includeRelations ) seenIndices.add(govIndex);
        
        String gov = dep.gov().label().value().toString().toLowerCase();
        String govlemmakey = buildTokenLemma(gov, govIndex, tree, particles, wordnet);
//        System.out.print("check " + govlemmakey);
        if( govlemmakey != null && tokenMatchesDesiredType(govlemmakey.substring(2), govlemmakey.charAt(0), wordnet, type) ) {
          // Check the ignore list first.   
          if( ignore == null || !ignore.contains(govlemmakey) ) {
            if( idf == null || (idf.get(govlemmakey) > idfcutoff && idf.getDocCount(govlemmakey) > docCutoff) ) {
              // If we are attaching relations.
              if( includeRelations ) {
                String reln = normalizeRelation(dep.reln().toString(), fullPrep);
//                System.out.println("reln " + reln + " from " + dep.reln().getShortName() + " from " + dep.reln().toString());
                if( reln.equals("s") || reln.equals("o") || reln.equals("p") || reln.startsWith("p_") ||
                    reln.equals("poss") ) {
                  govlemmakey = attachRelation(govlemmakey, reln);
                  if( !added.contains(govlemmakey) ) {
                    tokens.add(new WordPosition(sid, govIndex, govlemmakey));//WordEvent(govlemmakey, govIndex, sid+1));
                    added.add(govlemmakey);
                  }
                }
              }
              else {
                // Don't double-add the same word.
                if( !added.contains(govlemmakey) ) {
                  tokens.add(new WordPosition(sid, govIndex, govlemmakey));//tokens.add(new WordEvent(govlemmakey, govIndex, sid+1));
                  added.add(govlemmakey);
                }
              }
            }
            // Add objects
//            if( countCollocationObjects ) {
//              List<String> objStrings = objects.get(govIndex);
//              if( objStrings != null ) {
//                for( String objString : objStrings ) {
//                  String lemma = wordnet.nounToLemma(objString);
//                  if( lemma != null ) objString = lemma;
//                  String newkey = CountArgumentTypes.connectObject(govlemmakey, objString);
//                  if( !tokens.contains(newkey) )
//                    tokens.add(newkey);
//                }
//              }
//            }
          }
        }
//        System.out.println();
      }
      
      // Dependent. (don't bother if we include relations, you skip dependents)
      if( includeDependents && !includeRelations ) {
        int depIndex = dep.dep().index();
        // Temporary fix? The dependencies from objects that were in conjuncts are
        // created new, and 1000 is added to the original verb's ID.  We subtract this,
        // making the original verb the real match.
        if( depIndex > 1000 ) depIndex -= 1000;
        if( !seenIndices.contains(depIndex) ) {
          seenIndices.add(depIndex);
          String depstr = dep.dep().label().value().toString().toLowerCase();
          String deplemmakey = buildTokenLemma(depstr, depIndex, tree, particles, wordnet);
          if( deplemmakey != null && tokenMatchesDesiredType(deplemmakey.substring(2), deplemmakey.charAt(0), wordnet, type) ) {
            if( ignore == null || !ignore.contains(deplemmakey) ) {
              if( idf == null || (idf.get(deplemmakey) > idfcutoff && idf.getDocCount(deplemmakey) > docCutoff) ) {
                // Don't double-add the same word.
                if( !tokens.contains(deplemmakey) ) {
                  tokens.add(new WordPosition(sid, depIndex, deplemmakey));//tokens.add(new WordEvent(deplemmakey, depIndex, sid+1));
                  added.add(deplemmakey);
                }
              }
              // Add objects
//              if( countCollocationObjects ) {
//                List<String> objStrings = objects.get(depIndex);
//                if( objStrings != null ) {
//                  for( String objString : objStrings ) {
//                    String lemma = wordnet.nounToLemma(objString);
//                    if( lemma != null ) objString = lemma;
//                    String newkey = CountArgumentTypes.connectObject(deplemmakey, objString);
//                    if( !tokens.contains(newkey) )
//                      tokens.add(newkey);
//                  }
//                }
//              }
            }
          }
        }
      }
    }
    
    if( countCollocationObjects ) {
//      System.out.println("CountTokenPairs before collocs: " + tokens);
      List<WordPosition> collocs = createCollocations(tokens, sentDeps, ners, wordnet);
      tokens.addAll(collocs);
    }
    
    return tokens;
  }

  /**
   * Finds all tokens that match the given 'type' (verbs, nouns, etc).
   * Event objects are created with their arguments that are entity mentions as decided
   * by the coref engine. Tokens without any args are not returned.
   * @param type The type of tokens we are looking for.
   * @param fullprep If true, prep relations are "p_during", if false they are "p".
   * @return Event objects with the coreferring entity IDs filled in.
   */
  public static List<WordEvent> extractEvents(List<Tree> trees,
      List<List<TypedDependency>> alldeps,
      List<EntityMention> entities,
      WordNet wordnet,
      int type,
      boolean fullprep) {
    List<WordEvent> finalEvents = new ArrayList<WordEvent>();
    List<WordEvent> events = new ArrayList<WordEvent>();

    if( trees.size() != alldeps.size() ) {
      System.out.println("Deps size not parse size! " + trees.size() + " " + alldeps.size());
      System.exit(1);
    }

    // Create sentence indexed events
    Set<Integer> seen = new HashSet<Integer>();
    int sid = 0;
    for( List<TypedDependency> sentDeps : alldeps ) {
      Tree parseTree = trees.get(sid);
      Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);
      seen.clear();
      try {
        for( TypedDependency dep : sentDeps ) {
//          System.out.println("dep: " + dep);
          int govindex = dep.gov().index();
          if( govindex > 1000 ) govindex -= 1000;
          if( govindex == 0 ) continue; // ROOT-0 dependency, skip it
          if( !seen.contains(govindex) ) {
            seen.add(govindex);
            String gov = dep.gov().label().value().toString().toLowerCase();
            String govlemmakey = buildTokenLemma(gov, govindex, parseTree, particles, wordnet);
//            System.out.println("key: " + govlemmakey);
            
            if( tokenMatchesDesiredType(govlemmakey.substring(2), govlemmakey.charAt(0), wordnet, type) ) {
              WordEvent event = new WordEvent(govlemmakey, govindex, sid+1, govlemmakey.substring(0,1));
              events.add(event);
//              System.out.println("prelim: " + event);
            }
          }
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
      sid++;
    }

    // Create sentence indexed entity mentions
    List<List<EntityMention>> mentions = new ArrayList<List<EntityMention>>();
    for( int i = 0; i < trees.size(); i++ )
      mentions.add(new ArrayList<EntityMention>());
    for( EntityMention mention : entities ) {
      if( mention.sentenceID()-1 > mentions.size() ) {
        System.out.println("ERROR: mention sid large:: " + mention + " with num sentences " + trees.size());
      }
      mentions.get(mention.sentenceID()-1).add(mention);
    }

    // Finally, annotate all of the events with entity mentions.
    for( WordEvent event : events ) {
      List<TypedDependency> deps = alldeps.get(event.sentenceID()-1);
//      System.out.println("checking: " + event);
      
      // Add entity arguments to the verb event
      boolean addedArgument = false;
      for( EntityMention mention : mentions.get(event.sentenceID()-1) ) {
//        System.out.println("  check mention " + mention);
        if( !mention.string().equals("_") ) { 
          // Find the syntactic relation to the verb
          Pair<String,Integer> relationIndex = BasicEventAnalyzer.depVerbRelationWithIndex(deps, event, mention);
//          System.out.println("  got relation " + relation);
          if( relationIndex != null && BasicEventAnalyzer.acceptableDependencyAll(relationIndex.first()) ) {
//            System.out.println("  ...adding reln " + relation);
//            event.addArg(relation, mention.entityID(), fullprep);
            event.addArg(relationIndex.first(), relationIndex.second(), mention, fullprep);
            addedArgument = true;
          }
        }
      }
      if( addedArgument ) 
        finalEvents.add(event);
    }

    return finalEvents;
  }

  /**
   * Pulls out all of the syntactic objects that are in the given event object.
   * @param event An event token with syntactic arguments in the object.
   */
  private static List<Integer> getObjects(WordEvent event) {
    // Map from Event ID to the argument's Entity ID.
    List<Integer> objectIDs = null;

    if( event.arguments() != null ) {
      for( Map.Entry<Integer, String> entry : event.arguments().entrySet() )
        if( entry.getValue().equals(WordEvent.DEP_OBJECT) ) {
          if( objectIDs == null ) 
            objectIDs = new ArrayList<Integer>();
          objectIDs.add(entry.getKey());
        }
    }
    return objectIDs;
  }
  
  private static String getDependentAtIndex(int index, Collection<TypedDependency> sentDeps) {
    for( TypedDependency dep : sentDeps )
      if( dep.dep().index() == index )
        return dep.dep().value();
    return null;
  }
  
  /**
   * Given the tokens we extracted from a sentence, create new "tokens" that include their objects
   * within the given sentence.
   * @return A list of new token#o#object strings.
   */
  public static List<WordEvent> getCollocations(List<WordEvent> tokens, Collection<TypedDependency> sentDeps, List<NERSpan> ners, WordNet wordnet) {
    List<WordEvent> collocations = new ArrayList<WordEvent>();
  
    // Add objects
    for( WordEvent token : tokens ) {
      List<WordEvent> collocs = getCollocations(token, sentDeps, ners, wordnet);
      if( collocs != null ) collocations.addAll(collocs);
    }    
    return collocations;
  }

  public static List<WordEvent> getCollocations(WordEvent token, Collection<TypedDependency> sentDeps, List<NERSpan> ners, WordNet wordnet) {
    List<WordEvent> collocations = null;

    List<Integer> objectIDs = getObjects(token);
    
//    System.out.println("getCollocations objectIDs " + objectIDs);
    if( objectIDs != null ) {
      for( Integer objectID : objectIDs ) {
        int headIndex = token.getArgIndex(objectID);
        String head = getDependentAtIndex(headIndex, sentDeps);
        if( head != null) {
          String newEventName = null;
//          System.out.println("  lookup ner sid " + token.sentenceID() + " head index " + headIndex);
          NERSpan.TYPE ner = nerTagOfIndex(token.sentenceID()-1, headIndex, ners);
          if( ner == NERSpan.TYPE.NONE ) {
            String lemma = wordnet.nounToLemma(head);
            if( lemma != null ) head = lemma;
            newEventName = CountArgumentTypes.connectObject(token.token(), head);
          } else
            newEventName = CountArgumentTypes.connectObject(token.token(), ner.name());

//          System.out.println("getCollocations newname " + newEventName + " from head " + head + " dep " + token.getArgMention(objectID));
          
          if( collocations == null || !collocations.contains(newEventName) ) {
            if( collocations == null ) collocations = new ArrayList<WordEvent>();
            WordEvent newEvent = new WordEvent(999, newEventName, token.position(), token.sentenceID());
            collocations.add(newEvent);
            // Add the args, except the object.
            for( Map.Entry<Integer,String> entry : token.arguments().entrySet() ) {
              if( entry.getKey() != objectID )
                newEvent.addArgAsIs(entry.getValue(), entry.getKey());
            }
          }
        }
      }  
    }
    return collocations;
  }
  
  
  private static List<WordPosition> createCollocations(List<WordPosition> tokens, List<TypedDependency> sentDeps, List<NERSpan> ners, WordNet wordnet) {
    List<WordPosition> newTokens = new ArrayList<WordPosition>();
    
    for( WordPosition token : tokens ) {
      for( TypedDependency dep : sentDeps ) {
        // If the governor is the same word index as the token.
        if( dep.gov().index() == token.wordIndex ) {
          // Check if the relation is a grammatical object.
          String reln = normalizeRelation(dep.reln().toString(), false);
          if( reln.equals(WordEvent.DEP_OBJECT) ) {
            String newEventName = null;
            
            // Check if there is a NER label.
            NERSpan.TYPE ner = nerTagOfIndex(token.sentIndex, dep.dep().index(), ners);
            if( ner == NERSpan.TYPE.NONE ) {
              String depstr = dep.dep().label().value().toString().toLowerCase();
              String depLemma = wordnet.nounToLemma(depstr);
              if( depLemma == null ) depLemma = depstr;
              newEventName = CountArgumentTypes.connectObject(token.token, depLemma);
            } else
              newEventName = CountArgumentTypes.connectObject(token.token, ner.name());
            
            newTokens.add(new WordPosition(token.sentIndex, token.wordIndex, newEventName));
          }
        }
      }
    }

    return newTokens;
  }
  
  /**
   * Adds all the token pair counts from the given counts to this instance's current counts.
   * @param counts Assumes the counts are integers.
   */
  public void addCountsInt(CountTokenPairs counts) {
    for( String token1 : counts.intKeySet() ) {
      Map<String,Integer> myCounts = _counts.get(token1);
      if( myCounts == null ) {
        myCounts = new HashMap<String,Integer>();
      }
      
      for( String token2 : counts.intKeySet(token1) )
        Util.incrementCount(myCounts, token2, counts.getCountInteger(token1, token2));
    }
  }
  
  /**
   * Adds all the token pair counts from the given counts to this instance's current counts.
   * @param counts Assumes the counts are floats.
   */
  public void addCountsFloat(CountTokenPairs counts) {
    for( String token1 : counts.floatKeySet() ) {
      Map<String,Float> myCounts = _countsFloat.get(token1);
      if( myCounts == null ) {
        myCounts = new HashMap<String,Float>();
      }
      
      for( String token2 : counts.floatKeySet(token1) )
        Util.incrementCount(myCounts, token2, counts.getCount(token1, token2));
    }
  }
  
  public static String attachRelation(String token, String reln) {
    return token + ":" + reln;
  }
  
  public static String detachToken(String str) {
    int colon = str.indexOf(':');
    if( colon > -1 ) return str.substring(0,colon);
    else return null;
  }
  
  public static String detachRelation(String str) {
    int colon = str.indexOf(':');
    if( colon > -1 ) return str.substring(colon+1);
    else return null;
  }
  
  /**
   * Finds the token in the tree, and uses its POS Tag to lookup the lemma in WordNet.
   * Also attaches a particle if there is one for the token.
   */
  public static String buildTokenLemma(String token, int index, Tree tree, Map<Integer, String> particles, WordNet wordnet) {
    if( index == 0 )
      return null;
    
    Tree subtree = TreeOperator.indexToSubtree(tree, index);
    if( subtree == null ) {
      System.out.println("null subtree " + token + " index " + index + " tree=" + tree);
//      System.exit(-1);
      return null;
    }
    String posTag = subtree.label().value();
    String govLemma = wordnet.lemmatizeTaggedWord(token, posTag);
    if( CountVerbDeps.isNumber(token) )
      govLemma = CountVerbDeps.NUMBER_STRING;
    // Attach particle.
    if( particles != null && particles.size() > 0 ) {
      String particle = particles.get(index);
      if( particle != null )
        govLemma = govLemma + "_" + particle;
    }
    char normalPOS = CalculateIDF.normalizePOS(posTag);

    return CalculateIDF.createKey(govLemma, normalPOS);
  }

  /**
     * Given a list of events with their entity arguments, find all possible pairs that
     * have the same entity arg and increment the global _countsLemmas.
     */
    private void countEventPairs(List<WordEvent> events, int distance) {
      int numEvents = events.size();
      HashSet<String> seen = new HashSet<String>();
  
      for( int i = 0; i < numEvents-1; i++ ) {
        WordEvent e1 = events.get(i);
        String w1 = e1.token();
        seen.clear();
//        System.out.println("e1 " + e1 + " w1=" + w1 + " count=" + _idf.getDocCount(w1) + " idf=" + _idf.get(w1));
  
        if( CountArgumentTypes.isObjectString(w1) || (_idf.getDocCount(w1) > _docCutoff && _idf.get(w1) > _idfCutoff) ) {
          for( int j = i+1; j < numEvents && j < i+1+distance; j++ ) {
            WordEvent e2 = events.get(j);
            String w2 = e2.token();
            //    if( e2.particle() != null ) w2 += "_" + e2.particle();
            //    System.out.println("  " + j + " e2= " + e2);
  
            // skip if the same verb
            if( w1.equals(w2) || (e1.sentenceID() == e2.sentenceID() && e1.position() == e2.position()) )
              continue;
  
            if( CountArgumentTypes.isObjectString(w2) || (_idf.getDocCount(w2) > _docCutoff && _idf.get(w2) > _idfCutoff) ) {
  
  //            System.out.println("check " + w1 + " " + w2);
              // Do they share an argument?
              String shared = e1.sharedArgument(e2.arguments());
  
              // If w1 hasn't already paired with a similar w2.
              if( shared != null && !seen.contains(w2) ) {
                int colon = shared.indexOf(':');
                String key1 = attachRelation(w1, shared.substring(0,colon));
                String key2 = attachRelation(w2, shared.substring(colon+1));
                incrementCount(_countsLemmas, key1, key2, 1);
//              System.out.println("adding " + key1 + " " + key2);
//              System.out.println("Count\t" + e1 + "\t" + e2 + "\t" + key1 + "\t" + key2);
                
                seen.add(w2);
              }
            }
          }
        }
      }
    }

  /**
   * Count all pairs of tokens with coref arguments in the document.
   * This counts token pairs with their relations, not just tokens.
   */
  private void countTokenPairsWithCoref(List<Tree> trees, List<List<TypedDependency>> deps, List<EntityMention> mentions, List<NERSpan> ners) {
     // Now get the "token:arg" events whose arg may corefer elsewhere.
    List<WordEvent> events = extractEvents(trees, deps, mentions, _wordnet, _tokenType, _fullPrep);
    
//    for( WordEvent event : events ) System.out.println("event: " + event.toStringFull());
    
    // Count arguments of tokens with their objects. (collocations)
    if( _countObjectCollocations ) {
      List<WordEvent> allNewEvents = new ArrayList<WordEvent>();
      for( WordEvent event : events ) {
        List<WordEvent> newEvents = getCollocations(event, deps.get(event.sentenceID()-1), ners, _wordnet);
        if( newEvents != null ) {
//          for( WordEvent newEvent : newEvents ) System.out.println("NEW event: " + newEvent.toStringFull());
          allNewEvents.addAll(newEvents);
        }
      }
      events.addAll(allNewEvents);
    }
    
    // Count the pairs.
    countEventPairs(events, 10000);
  }

  public static String buildCollocationString(int sid, String govlemma, String object, int objectIndex, List<NERSpan> ners, WordNet wordnet) {
    NERSpan.TYPE ner = nerTagOfIndex(sid, objectIndex, ners);
    if( ner == NERSpan.TYPE.NONE ) {
      String objlemma = wordnet.nounToLemma(object);
      if( objlemma == null ) objlemma = object;
      return CountArgumentTypes.connectObject(govlemma, objlemma);
    }
    else return CountArgumentTypes.connectObject(govlemma, ner.name());
  }
  
  /**
   * Counts all pairs of tokens, but counts are based on sentence distance.  If in the same sentence,
   * they get a normal count of 1.  If different, it is a smaller (1-log(distance)) number.
   */
  private void countTokenPairsByDistance(List<Tree> trees, List<List<TypedDependency>> deps, List<NERSpan> ners) {
    Set<Integer> seenIndices = new HashSet<Integer>();
    int sid = 0;
    List<WordPosition> tokensLemmas = new ArrayList<WordPosition>();

    for( List<TypedDependency> sentDeps : deps ) {
      seenIndices.clear();

      // Get the particles.
      Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);
      // Get the objects.
      Map<Integer, List<WordPosition>> objects = Ling.objectsInSentence(sid, sentDeps);

      for( TypedDependency dep : sentDeps ) {
        // Governor.
        int govIndex = dep.gov().index();
        if( !seenIndices.contains(govIndex) ) {
          seenIndices.add(govIndex);
          String gov = dep.gov().label().value().toString().toLowerCase();
          String govlemmakey = buildTokenLemma(gov, govIndex, trees.get(sid), particles, _wordnet);
          if( govlemmakey != null && tokenMatchesDesiredType(govlemmakey.substring(2), govlemmakey.charAt(0), _wordnet, _tokenType) ) {
            if( _ignore == null || !_ignore.contains(govlemmakey) ) {
              tokensLemmas.add(new WordPosition(sid, govIndex, govlemmakey));
              //	      System.out.println("Adding " + govlemmakey);
              if( _countObjectCollocations && objects.containsKey(govIndex) ) {
                for( WordPosition object : objects.get(govIndex) ) {
                  tokensLemmas.add(new WordPosition(sid, govIndex, buildCollocationString(sid, govlemmakey, object.token, object.wordIndex, ners, _wordnet)));
                }
              }
            }
          }
        }

        // Dependent.
        if( _includeDependents ) {
          int depIndex = dep.dep().index();
          if( !seenIndices.contains(depIndex) ) {
            seenIndices.add(depIndex);
            String depstr = dep.dep().label().value().toString().toLowerCase();
            String deplemmakey = buildTokenLemma(depstr, depIndex, trees.get(sid), particles, _wordnet);
            if( deplemmakey != null && tokenMatchesDesiredType(deplemmakey.substring(2), deplemmakey.charAt(0), _wordnet, _tokenType) ) {
              if( _ignore == null || !_ignore.contains(deplemmakey) ) {
                tokensLemmas.add(new WordPosition(sid, depIndex, deplemmakey));
                if( _countObjectCollocations && objects.containsKey(depIndex) ) {
                  for( WordPosition object : objects.get(depIndex) ) {
                    tokensLemmas.add(new WordPosition(sid, depIndex, buildCollocationString(sid, deplemmakey, object.token, object.wordIndex, ners, _wordnet)));
                  }
                }
              }
            }
          }
        }
      }
      sid++;
    }

    Map<String,Map<String,Integer>> localCounts = new HashMap<String,Map<String,Integer>>();
    int size = tokensLemmas.size();

    //     // Count word pairs: If same pair appears more than once, save the shortest
    //     //                   distance between the two.
    //     for( int i = 0; i < size-1; i++ ) {
    //       for( int j = i+1; j < size; j++ ) {
    // 	WordPosition word1 = tokens.get(i);
    // 	WordPosition word2 = tokens.get(j);
    // 	int distance = 1 + Math.abs(word1.docIndex - word2.docIndex);
    // 	int prevDistance = getCountInteger(word1.token, word2.token, localCounts);
    // 	if( prevDistance == 0 || distance < prevDistance )
    // 	  setCount(localCounts, word1.token, word2.token, distance);
    //       }
    //     }

    //     // Scale the distance.
    //     for( Map.Entry<String, Map<String,Integer>> entry : localCounts.entrySet() ) {
    //       String token1 = entry.getKey();
    //       for( Map.Entry<String,Integer> entry2 : entry.getValue().entrySet() ) {
    // 	String token2 = entry2.getKey();
    // 	int distance = entry2.getValue();
    // 	// Scale distance by a log distance.
    // 	double scaledDistance = Math.max(0.05, (1.0 - (Math.log(distance)/Math.log(4))));
    // 	//	System.out.println(token1 + "\t" + token2 + "\t" + distance + "\t" + scaledDistance);
    // 	incrementDoubleCount(_countsDouble, token1, token2, scaledDistance);
    //       }
    //     }

    // Count word pairs: If same pair appears more than once, save the shortest
    //                   distance between the two.
    localCounts.clear();
    for( int i = 0; i < size-1; i++ ) {
      for( int j = i+1; j < size; j++ ) {
        WordPosition word1 = tokensLemmas.get(i);
        WordPosition word2 = tokensLemmas.get(j);
        // Don't count a pair if it is really the same token (v-claim and v-claim#o#responsibility).
        if( word1.sentIndex != word2.sentIndex || word1.wordIndex != word2.wordIndex ) {
          int distance = 1 + Math.abs(word1.sentIndex - word2.sentIndex);
          int prevDistance = getCountInteger(word1.token, word2.token, localCounts);
          if( prevDistance == 0 || distance < prevDistance )
            setCount(localCounts, word1.token, word2.token, distance);
        }
      }
    }

    // Scale the distance.
    for( Map.Entry<String, Map<String,Integer>> entry : localCounts.entrySet() ) {
      String token1 = entry.getKey();
      for( Map.Entry<String,Integer> entry2 : entry.getValue().entrySet() ) {
        String token2 = entry2.getKey();
        int distance = entry2.getValue();
        // Scale distance by a log distance.
        double scaledDistance = Math.max(0.05f, (1.0f - (Math.log(distance)/Math.log(4))));
        //	System.out.println(token1 + "\t" + token2 + "\t" + distance + "\t" + scaledDistance);
        incrementFloatCount(_countsLemmasFloat, token1, token2, (float)scaledDistance);
      }
    }
  }

  /**
     * Count all pairs of tokens in the document.
     */
    private void countTokenPairs(List<Tree> trees, List<List<TypedDependency>> deps) {
      int sid = 0;
      Map<String,Integer> localCounts = new HashMap<String, Integer>();
  
      for( List<TypedDependency> sentDeps : deps ) {
        List<String> sentLemmas = getKeyTokensSkipIDF(sid, trees.get(sid), sentDeps, null, _wordnet, _idf, _idfCutoff, _docCutoff, 
            _ignore, _tokenType, _includeDependents, _includeRelations, _fullPrep, _countObjectCollocations);
  //      System.out.print("sent tokens:");
  //      for( String token : sentLemmas ) System.out.print(" " + token);
  //      System.out.println();
        
        for( String token : sentLemmas )
          Util.incrementCount(localCounts, token, 1);
        sid++;
      }
  
      // Count the lemma pairs.
      int i = 0;
      for( Map.Entry<String,Integer> entry : localCounts.entrySet() ) {
        int j = 0;
        for( Map.Entry<String,Integer> entry2 : localCounts.entrySet() ) {
          if( j > i ) {
            // Increment by the max number of times either token appeared.
            int count = Math.max(entry.getValue(), entry2.getValue());
            incrementCount(_countsLemmas, entry.getKey(), entry2.getKey(), count);
  //          System.out.println("pair " + entry.getKey() + "," + entry2.getKey() + " " + Math.max((Integer)entry.getValue(), (Integer)entry2.getValue()));
          }
          j++;
        }
        i++;
      }
    }

  /**
   * Count token pairs in a given set of parses/deps.
   */
  private void countPairs(ProcessedData reader) {
    reader.nextStory();
    List<Tree> trees = TreeOperator.stringsToTrees(reader.getParseStrings());

    // Read the dependencies.
    while( trees != null ) {
      // Skip duplicate stories.
      if( _duplicates != null && _duplicates.contains(reader.currentStory()) ) {
        System.out.println("duplicate " + reader.currentStory());
      } else {
        System.out.println(reader.currentStory());
        if( _numStories++ % 100 == 0 ) Util.reportMemory();

        // Trim for memory savings, this can huge in size.
        if( !_withCoref && _numStories > 10000 && _numStories % 2000 == 0 ) {
          System.out.println("Trimming mid-way at " + _numStories + " docs.");
          if( _countsLemmasFloat.size() > 15000 ) trimFloatPairs(1.5f);
          if( _countsLemmas.size() > 15000 ) trimIntegerPairs(2);
        }
        
        // Count the args.
        if( _countByDistance )
          countTokenPairsByDistance(trees, reader.getDependencies(), reader.getNER());
        else if( _withCoref )
          countTokenPairsWithCoref(trees, reader.getDependencies(), reader.getEntities(), reader.getNER());
        else 
          countTokenPairs(trees, reader.getDependencies());
      }

      // Advance to the next story.
      reader.nextStory();
      trees = TreeOperator.stringsToTrees(reader.getParseStrings());
    }
  }


  /**
   * Trim away pairs appearing too sparse, to save memory
   */
  private void trimIntegerPairs(int cutoff) {
    if( _countsLemmas != null ) {
      System.out.println("Trimming pairs at " + cutoff);
      for( Map.Entry<String,Map<String,Integer>> entry : _countsLemmas.entrySet() ) {
        Util.trimIntegerCounts(entry.getValue(), cutoff);
      }
    }
  }
   
  /**
   * Trim away pairs appearing too sparse, to save memory
   */
  private void trimFloatPairs(float cutoff) {
    if( _countsLemmasFloat != null ) {
      System.out.println("Trimming pairs at " + cutoff);
      for( Map.Entry<String,Map<String,Float>> entry : _countsLemmasFloat.entrySet() ) {
        Util.trimFloatCounts(entry.getValue(), cutoff);
      }
    }
  }
  
  public void clear() {
    if( _countsLemmasFloat != null ) _countsLemmasFloat.clear();
    if( _countsLemmas != null ) _countsLemmas.clear();
    if( _counts != null ) _counts.clear();
    if( _countsFloat != null ) _countsFloat.clear();
    _countsLemmasFloat = null;
    _countsLemmas = null;
    _countsFloat = null;
    _counts = null;
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
    File yearLock = new File(_lockDir + File.separator + "counttokenpairs-coref" + _withCoref + "-" + newYear + ".lock");
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
   * Read a directory of dependency files and count each one.
   */
  public void process() {
    if( _depsPath.length() > 0 ) {
      File dir = new File(_parsePath);
      String haveLock = "";

      // Directory of files.  *** never tested
      if( dir.isDirectory() ) {
        int numfiles = 0;
        for( String file : Directory.getFilesSorted(_parsePath) ) {
          if( file.contains("parse") ) {
            System.out.println("file: " + file);
            String year = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(8,12) : "noyear";
            String month = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(12,14) : "nomonth";
            String parseFile = _parsePath + File.separator + file;
            String depsFile = _parsePath + File.separator + Directory.nearestFile(file, _depsPath);
            String corefFile  = _corefPath + File.separator + Directory.nearestFile(file, _corefPath);
            String nerFile  = _corefPath + File.separator + Directory.nearestFile(file, _nerPath);
            ProcessedData dataReader = new ProcessedData(parseFile, depsFile, corefFile, nerFile);
            
            // Lock this year for processing.
            if( checkLock(haveLock, year) ) {
              haveLock = year;

              // Count the pairs.
              countPairs(dataReader);
              numfiles++;

              // Save to file by year (and at half years).
              if( month.equals("06") || month.equals("12") || 
                  (year.equals("1999") && month.equals("11")) || (year.equals("2004") && month.equals("05")) ) {
                System.out.println("saving to disc...");
                if( !_withCoref ) {
                  trimIntegerPairs(2);
                  trimFloatPairs(2.0f);
                }
                String suffix = "-1";
                if( !month.equals("06") && !month.equals("05") ) suffix = "-2";
                if( _countByDistance )
                  floatsToFile(_countsLemmasFloat, _numStories, _outDirectory + File.separator + _outFileLemmas + "-" + year + suffix);
                else        
                  countsToFile(_countsLemmas, _numStories, _outDirectory + File.separator + _outFileLemmas + "-" + year + suffix);
                // Now clear the memory!
                if( _countsLemmas != null ) _countsLemmas.clear();
                if( _countsLemmasFloat != null ) _countsLemmasFloat.clear();
              }

              Util.reportMemory();
            }
          }
        }
      }

      // Single text file input.
      else {
        System.out.println("file: " + _depsPath);
        ProcessedData dataReader = new ProcessedData(_parsePath, _depsPath, _corefPath, _nerPath);
        countPairs(dataReader);
        // NEW: for larger processing, we need to trim...
        if( !_withCoref ) {
          if( _countsLemmasFloat.size() > 15000 ) trimFloatPairs(1.0f);
          if( _countsLemmas.size() > 15000 ) trimIntegerPairs(2);
        }
        if( _countByDistance )
          floatsToFile(_countsLemmasFloat, _numStories, _outDirectory + File.separator + _outFileLemmas + "-dist");
        else        
          countsToFile(_countsLemmas, _numStories, _outDirectory + File.separator + _outFileLemmas);
      }
    }
  }


  public static void main(String[] args) {
    CountTokenPairs count = new CountTokenPairs(args);
    count.process();
  }
}

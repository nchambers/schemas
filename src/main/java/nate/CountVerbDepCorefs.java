package nate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.args.CountArgumentTypes;
import nate.reading.ProcessedData;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;
import nate.util.WordPosition;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * This class uses Typed Dependency files and Coref files to create a set of
 * token-dep counts.  It records three different counts:
 *   1. Number of documents a token-dep appeared in.
 *   2. Number of total occurrences of a token-dep.
 *   3. Number of occurrences a token-dep has a corefferent argument.
 * 
 * The number of times they have an argument that is coreferrent is a rough 
 * measure of how "important" or "in focus" the arguments are to documents.
 *
 * One file is output:
 *    dep-coref-lemmas.counts - counts of verb lemmas and dependencies
 *    
 * The code commented out the section that used to do non-lemmas:
 *     dep-coref.counts - counts of verbs and dependencies
 * 
 * Output counts format, one verb per line with its deps counts:
 *   v-justify  o 32 35 4  s 23 24 8  p 11 13 2
 *
 * "java CountVerbDepCorefs -deps <deps-file> -events <events-file> -parsed <parse-file> [-fullprep]"
 * 
 * -fullprep
 * True if you want prep relations expanded e.g. p_during
 * False if you just want one general relation e.g. p
 * 
 * -output
 * Directory to put the generated files.
 * 
 */
public class CountVerbDepCorefs {
  int _totalCount = 0;
  String _depsPath;
  String _corefPath;
  String _parsePath;
  String _nerPath;
  String _outdir = ".";
  String _outfileLemmas = "dep-coref-lemmas.counts";
  String _outfile = "dep-coref.counts"; // doesn't normally output this anymore.

  boolean _fullPrep = false; // true if you want "p_during" and not just "p"
  boolean _countObjectCollocations = false; // true if you want to count collocations too
  private String duplicatesPath = "duplicates";
  private Set<String> _duplicates;
  Map<String,Map<String,Count>> _counts;
  Map<String,Map<String,Count>> _countsLemmas;
  WordNet _wordnet;
  int _numStories = 0;


  public CountVerbDepCorefs(String args[]) {
    HandleParameters params = new HandleParameters(args);
    _depsPath = params.get("-deps");
    _corefPath = params.get("-events");
    _parsePath = params.get("-parsed");
    _nerPath = params.get("-ner");

    if( params.hasFlag("-fullprep") ) _fullPrep = true;
    System.out.println("fullprep\t" + _fullPrep);

    if( params.hasFlag("-objects") ) _countObjectCollocations = true;
    System.out.println("objectCollocations\t" + _countObjectCollocations);

    if( params.hasFlag("-output") )
      _outdir = params.get("-output");

    // Load WordNet.
    _wordnet = new WordNet(WordNet.findWordnetPath());

    // Initialize count maps.
    _counts = new HashMap<String, Map<String, Count>>();
    _countsLemmas = new HashMap<String, Map<String, Count>>();

    // Duplicate Gigaword files to ignore.
    if( Directory.fileExists(duplicatesPath) )
      _duplicates = GigawordDuplicates.fromFile(duplicatesPath);
  }

  /** 
   * Only use this constructor if you are counting verb-deps dynamically and
   * not from a file as it does not set pathnames.
   * @param wordnet
   */
  public CountVerbDepCorefs(WordNet wordnet) {
    // Initialize count maps.
    _counts = new HashMap<String, Map<String, Count>>();
    _countsLemmas = new HashMap<String, Map<String, Count>>();
    _wordnet = wordnet;
  }

  /**
   * Only use this constructor if you want transitive verbs loaded
   * from file, and not actually counting verb-deps from a corpus.
   */
  public CountVerbDepCorefs() {
    _counts = null;
    _countsLemmas = null;
  }

  public CountVerbDepCorefs(String filename) {
    this();
    fromFile(filename);
  }

  /**
   * Tally a verb/dep into our global _counts hashmaps.
   */
  private void incrementCount(Map<String,Map<String,Count>> counts, String verb, String dep, boolean iscoref) {
    // Find the verb
    Map<String,Count> verbMap;
    if( !counts.containsKey(verb) ) {
      verbMap = new HashMap<String, Count>(8);
      counts.put(verb, verbMap);
    } else {
      verbMap = counts.get(verb);
    }

    // Increment the argtype for the verb
    if( !verbMap.containsKey(dep) ) verbMap.put(dep, new Count(0,1,(iscoref ? 1 : 0)));
    else {
      Count count = verbMap.get(dep);
      count.occurrences++;
      if( iscoref ) count.corefs++;
    }
  }

  /**
   * Tally a verb/dep into our global _counts hashmaps.
   */
  private void incrementDocCount(Map<String,Map<String,Count>> counts, String verb, String dep) {
    // Find the verb
    Map<String,Count> verbMap;
    if( !counts.containsKey(verb) ) {
      verbMap = new HashMap<String, Count>(8);
      counts.put(verb, verbMap);
    } else {
      verbMap = counts.get(verb);
    }

    // Increment the argtype for the verb
    if( !verbMap.containsKey(dep) ) verbMap.put(dep, new Count(1,0,0));
    else {
      Count count = verbMap.get(dep);
      count.docCount++;
    }
  }

  /**
   * Print the verbs with their dependency-count pairs.
   */
  private void countsToFile(Map<String,Map<String,Count>> counts, int numDocs,
      String outfile) {
    System.out.println("Writing to file " + outfile);
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(outfile));
      out.write("NUMDOCS " + numDocs + "\n");

      for( Map.Entry<String,Map<String,Count>> entry : counts.entrySet() ) {
        if( entry.getKey() != null ) {
          out.write(entry.getKey());

          Map<String,Count> verbCounts = entry.getValue();
          for( Map.Entry<String,Count> entry2 : verbCounts.entrySet() ) {
            if( entry2.getKey() != null ) {
              Count count = entry2.getValue();
              out.write("\t" + entry2.getKey() + "\t" + count.docCount + "\t" +
                  count.occurrences + "\t" + count.corefs);
            }
          }
          out.write("\n");
        }
      }

      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * @return The number of mentions in the coref class of the given mention.
   */
  private int corefClassSize(int entityID, Collection<EntityMention> mentions) {
    int size = 0;
    for( EntityMention men : mentions ) {
      //      System.out.println("   corefsize checking " + men);
      if( men.entityID() == entityID ) {
        size++;
        //	System.out.println("   corefsize match!");
      }
    }
    return size;
  }

  /**
   * Find all entity mentions whose text span ends on the given index.
   * This helps identify dependencies with governors or dependents that are mentions.
   * @param sid The sentence index, starting from index 0.
   * @param index The token position, starting from position 1.
   * @return The list of mentions that end exactly on the given token index.
   */
  private List<EntityMention> findMentionsEndingAtIndex(int sid, int index, Collection<EntityMention> entities) {
    List<EntityMention> mentions = null;

    for( EntityMention mention : entities ) {
      // Sentence occurrences must match (entitymention sentences start at 1).
      if( mention.sentenceID() == sid+1 ) {
        // The entity must subsume the desired span completely.
        if( mention.end() == index ) {
          //	  System.out.println("Mention Matched! " + mention);
          if( mentions == null ) mentions = new ArrayList<EntityMention>();
          mentions.add(mention);
        }
      }
    }
    //    System.out.println(" Coref found " + (mentions == null ? 0 : mentions.size()) + " mentions");
    return mentions;
  }  

  public void countCorefs(List<Tree> trees, List<List<TypedDependency>> deps, Collection<EntityMention> entities, List<NERSpan> ners) {
//    System.out.println("countCorefs with " + deps.size() + " deps.");
    //    Set<String> seen = new HashSet<String>();
    Set<String> seenlemmas = new HashSet<String>();
    int sid = 0;

    for( List<TypedDependency> sentDeps : deps ) {
      // Get the particles.
      Map<Integer, String> particles = Ling.particlesInSentence(sentDeps);
      // Get the objects.
      Map<Integer, List<WordPosition>> objects = Ling.objectsInSentence(sid, sentDeps);

      for( TypedDependency dep : sentDeps ) {
        int depIndex = dep.dep().index();
        int govIndex = dep.gov().index();
        if( govIndex > 1000 ) govIndex -= 1000;
        String gov = dep.gov().label().value().toString().toLowerCase();
        String reln = CountTokenPairs.normalizeRelation(dep.reln().toString(), _fullPrep);
        String govlemmakey = CountTokenPairs.buildTokenLemma(gov, govIndex, trees.get(sid), particles, _wordnet);

        boolean iscoref = false;

        // Are any mentions rooted at this dependent?
        List<EntityMention> mentions = findMentionsEndingAtIndex(sid, depIndex, entities);
        if( mentions != null ) {

          // Find the size of the largest coref class for this dependent.
          int maxSize = 0;
          for( EntityMention mention : mentions ) {
            int size = corefClassSize(mention.entityID(), entities);
            if( size > maxSize ) maxSize = size;
          }

          // Add one to the count of this class.
          if( maxSize > 1 ) {
            //            System.out.println(" -> coreferring " + govlemmakey + " " + reln + " from " + dep);
            iscoref = true;
          }
        }

        //        incrementCount(_counts, govkey, reln, iscoref);
        incrementCount(_countsLemmas, govlemmakey, reln, iscoref);

        //        if( !seen.contains(govkey + "-" + reln) ) {
        //          seen.add(govkey + "-" + reln);
        //          incrementDocCount(_counts, govkey, reln);
        //        }
        if( !seenlemmas.contains(govlemmakey + "-" + reln) ) {
          seenlemmas.add(govlemmakey + "-" + reln);
          incrementDocCount(_countsLemmas, govlemmakey, reln);
        }

//        System.out.println(dep + " objects " + objects.get(govIndex));

        if( _countObjectCollocations ) {
          // Special counts for objects.  e.g. v-claim#o#responsibility
          List<WordPosition> objs = objects.get(govIndex);
          countObjects(sid, govIndex, govlemmakey, reln, iscoref, objs, seenlemmas, ners);
        }
      }
      sid++;
    }
  }

  /**
   * Increments the count for this relation 'reln' and the verb-object collocation.
   */
  private void countObjects(int sid, int index, String govkey, String reln, boolean iscoref, List<WordPosition> objs, Set<String> seen, List<NERSpan> ners) {
    if( objs != null ) {
      if( !reln.equals(WordEvent.DEP_OBJECT) ) {
        for( WordPosition token : objs ) {
          String govobjectkey = CountTokenPairs.buildCollocationString(sid, govkey, token.token, token.wordIndex, ners, _wordnet);
          incrementCount(_countsLemmas, govobjectkey, reln, iscoref);

          if( !seen.contains(govobjectkey + "-" + reln) ) {
            seen.add(govobjectkey + "-" + reln);
            incrementDocCount(_countsLemmas, govobjectkey, reln);
          }
        }
      }
    }
  }

  /**
   * Count verb/dep occurrences by reading each input line.
   * Doesn't build actual dependency objects from JavaNLP...just straight 
   * from strings.
   */
  private void countDeps(ProcessedData reader) {
    reader.nextStory();
    List<Tree> trees = TreeOperator.stringsToTrees(reader.getParseStrings());

    // Read the dependencies.
    while( trees != null ) {
      _numStories++;

      // Count the args.
      if( _duplicates != null && !_duplicates.contains(reader.currentStory()) ) {
        countCorefs(trees, reader.getDependencies(), reader.getEntities(), reader.getNER());
      }
      // Skip duplicate stories.
      else System.out.println("Duplicate " + reader.currentStory());

      // Advance to the next story.
      reader.nextStory();
      trees = TreeOperator.stringsToTrees(reader.getParseStrings());
//      if( _numStories > 10 ) break;
    }
  }

  /**
   * Removes any key (with an object) from the counts map whose relation counts are 
   * not seen more than or equal to the given cutoff.
   */
  private void trimObjects(Map<String,Map<String,Count>> counts, int freqCutoff) {
    Set<String> removal = new HashSet<String>();
    for( Map.Entry<String, Map<String,Count>> entry : counts.entrySet() ) {
      if( CountArgumentTypes.isObjectString(entry.getKey()) ) {
        int sum = 0;
        for( Map.Entry<String, Count> entry2 : entry.getValue().entrySet() )
          sum += entry2.getValue().occurrences;
        if( sum <= freqCutoff )
          removal.add(entry.getKey());
      }
    }
    
    for( String remove : removal )
      counts.remove(remove);
  }

  /**
   * Read a counts file into memory
   */
  public void fromFile(String path) {
    fromFile(path, null);
  }
  public void fromFile(String path, Collection<String> include) {
    String line = null;
    if( _counts == null ) _counts = new HashMap<String, Map<String, Count>>();
    else _counts.clear();
    _totalCount = 0;

    System.out.println("CountVerbDepCorefs fromFile " + path);
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      line = in.readLine();

      // Save the total number of documents these counts are from.
      if( line != null && line.startsWith("NUMDOCS ") )
        _numStories = Integer.valueOf(line.substring(8));

      // e.g. "spearhead o 16 20 8 s 4 7 1"
      while( (line = in.readLine()) != null ) {
        String parts[] = line.split("\\s+");

        if( parts.length < 5 || (parts.length % 2 != 1) ) {
          System.out.println("Strange line format (skipping): " + line);
          System.exit(1);
        }
        else {
          // Extract the verb
          String verb = parts[0];
          // Save to memory if it is in our desired list.
          if( include == null || include.contains(verb) ) {
            Map<String,Count> verbMap = new HashMap<String, Count>(8);
            _counts.put(verb, verbMap);
            // Extract the dependent types
            for( int i = 1; i < parts.length; i += 4 ) {
              Count count = new Count(Integer.valueOf(parts[i+1]), 
                  Integer.valueOf(parts[i+2]),
                  Integer.valueOf(parts[i+3]));
              verbMap.put(parts[i], count);
              _totalCount += count.occurrences;
            }
          }
        }
      }
    } catch( Exception ex ) { 
      System.out.println("Error on line: " + line);
      ex.printStackTrace(); 
      System.exit(-1); 
    }
    System.out.println("...loaded " + _counts.size() + " words.");
  }

  /**
   * Get the number of times a token and a relation were seen.
   * Returns zero if token-reln is not found.
   */
  public int getCount(String token, String reln) {
    //    System.out.println("getCount " + token + " -- " + reln);
    if( _counts != null ) {
      Map<String,Count> relnMap = _counts.get(token);
      if( relnMap != null ) {
        //	System.out.println("  got relnMap size " + relnMap.size());
        Count count = relnMap.get(reln);
        if( count == null ) return 0;
        else return count.occurrences;
      }
      else return 0;
    }
    else return 0;
  }

  /**
   * Get the number of times a token and a relation were seen with a coreferring argument.
   * Returns zero if token-reln is not found.
   */
  public int getCorefCount(String token, String reln) {
    //    System.out.println("getCount " + token + " -- " + reln);
    if( _counts != null ) {
      Map<String,Count> relnMap = _counts.get(token);
      if( relnMap != null ) {
        //	System.out.println("  got relnMap size " + relnMap.size());
        Count count = relnMap.get(reln);
        if( count == null ) return 0;
        else return count.corefs;
      }
      else return 0;
    }
    else return 0;
  }

  /**
   * Get the number of times a token and a relation were seen with a coreferring argument.
   * Returns zero if token-reln is not found.
   */
  public int getDocCount(String token, String reln) {
    //    System.out.println("getCount " + token + " -- " + reln);
    if( _counts != null ) {
      Map<String,Count> relnMap = _counts.get(token);
      if( relnMap != null ) {
        //	System.out.println("  got relnMap size " + relnMap.size());
        Count count = relnMap.get(reln);
        if( count == null ) return 0;
        else return count.docCount;
      }
      else return 0;
    }
    else return 0;
  }

  public Set<String> getRelns(String token) {
    Map<String,Count> relnMap = _counts.get(token);
    if( relnMap != null )
      return relnMap.keySet();
    else return null;
  }

  public Set<String> getWords() {
    return _counts.keySet();
  }

  public int getTotalCount() { return _totalCount; }
  public int getTotalDocs() { return _numStories; }

  public void setLemmasAsMainCounts() {
    _counts = _countsLemmas;
  }
  
  /**
   * Read a directory of dependency files and count each one.
   */
  public void process() {
    //    int numDocs = 0;
    //    int numFiles = 0;

    if( _depsPath.length() > 0 ) {
      File dir = new File(_depsPath);

      // Directory of files.
      if( dir.isDirectory() ) {
        System.out.println("dir: " + _depsPath);
        for( String file : Directory.getFilesSorted(_depsPath) ) {
          if( file.contains("deps") ) {
            System.out.println("file: " + file);
            String deps   = _depsPath + File.separator + file;
            String parses = _parsePath + File.separator + Directory.nearestFile(file, _parsePath);
            String coref  = _corefPath + File.separator + Directory.nearestFile(file, _corefPath);
            String ner  = _corefPath + File.separator + Directory.nearestFile(file, _nerPath);
            System.out.println(" deps: " + deps);
            System.out.println(" parses: " + parses);
            System.out.println(" coref: " + coref);

            ProcessedData dataReader = new ProcessedData(parses, deps, coref, ner);
            countDeps(dataReader);
            Util.reportMemory();
          }
        }
      }

      // Single text file input.
      else {
        System.out.println("file: " + _depsPath);
        ProcessedData dataReader = new ProcessedData(_parsePath, _depsPath, _corefPath, _nerPath);
        countDeps(dataReader);
        System.out.println("_countsLemmas!!! has " + _countsLemmas.size() + " elements.");
      }

      // Trim collocations?
      if( _countObjectCollocations ) trimObjects(_countsLemmas, 25);
      
      // Output the full counts!
      countsToFile(_countsLemmas, _numStories, _outdir + File.separator + _outfileLemmas);
      //        countsToFile(_counts, _numStories, _outdir + File.separator + _outfile);
    }
  }

  private class Count {
    public int docCount = 0;
    public int occurrences = 0;
    public int corefs = 0; // # of occurrences that had coreferring arguments

    Count(int doc, int occ, int co) { 
      docCount = doc;
      occurrences = occ;
      corefs = co;
    }
  }

  public static void main(String[] args) {
    CountVerbDepCorefs count = new CountVerbDepCorefs(args);
    count.process();
  }
}

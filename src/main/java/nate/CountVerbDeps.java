package nate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.WordNet;

/**
 *
 * ********** DEPRECATED **********
 *
 * This functionality is in CountVerbDepCorefs.java now.
 * That code does everything this did, but also counts coreferring arguments.
 *
 * ********** DEPRECATED **********
 *
 *
 * This class uses Typed Dependency files as input and processes an entire
 * directory of files.  It counts all verbs and their dependencies, outputting
 * full counts of what we have seen, both tokens and lemmas.
 *
 * Four files are output:
 *    deps.counts - counts of verbs and dependencies
 *    deps-lemmas.counts - counts of verb lemmas and dependencies
 *    verbs.trans - lists of transitive/intransitive verbs based on the counts
 *    verbs-lemmas.trans - same as above, but verb lemmas
 * 
 * Output counts format, one verb per line:
 *   justify o 23 s 76 p 34
 *
 * Output transitive format, one verb per line:
 *   t justify
 *   i fall
 *
 * "java CountVerbDeps <deps-directory>"
 */
public class CountVerbDeps {
  String _dataPath;
  String _outfileLemmas = "deps-lemmas.counts";
  String _outfile = "deps.counts";
  String _outfileFillerLemmas = "deps-lemmas-fillers.counts";
  String _outfileFiller = "deps-fillers.counts";
  String outTransitive = "verbs.trans";
  String outTransitiveLemmas = "verbs-lemmas.trans";
  int _totalCount = 0;

  private String duplicatesPath = "duplicates";
  private Set<String> duplicates;
  Map<String,Map<String,Count>> _counts;
  Map<String,Map<String,Count>> _countsLemmas;
  Map<String,Map<String,Count>> _fillerCounts;
  Map<String,Map<String,Count>> _fillerCountsLemmas;
  Set<String> transitives;
  private String wordnetPath;
  private HashMap<String,String> wordnetHash;
  WordNet _wordnet;
  boolean _countFillers = false;
  int _numStories = 0;

  public final static String NUMBER_STRING = "*NUMBER*";


  CountVerbDeps(String args[]) {
    HandleParameters params = new HandleParameters(args);
    _dataPath = args[args.length-1];

    // Load WordNet
    _wordnet = new WordNet(params.get("-wordnet"));

    // Initialize count map
    _counts = new HashMap();
    _countsLemmas = new HashMap();
    _fillerCounts = new HashMap();
    _fillerCountsLemmas = new HashMap();

    // Duplicate Gigaword files to ignore
    duplicates = GigawordDuplicates.fromFile(duplicatesPath);
  }

  /**
   * Only use this constructor if you want transitive verbs loaded
   * from file, and not actually counting verb-deps from a corpus.
   */
  public CountVerbDeps() {
    _counts = null;
    _countsLemmas = null;
  }

  public CountVerbDeps(String filename) {
    this();
    fromFile(filename);
  }

  /**
   * @returns True if the string is a number. It can have hyphens or commas.
   *          False otherwise.
   */
  public static boolean isNumber(String str) {
    // Must start with a number, and then can have commas.
    if( str.matches("[0-9][0123456789,-]*") )
      return true;
    else return false;
  }

  /**
   * Tally a verb/dep into our global _counts hashmaps.
   */
  private void incrementCount(Map<String,Map<String,Count>> counts,
      String verb, String dep) {
    // Find the verb
    Map<String,Count> verbMap;
    if( !counts.containsKey(verb) ) {
      verbMap = new HashMap(8);
      counts.put(verb, verbMap);
    } else {
      verbMap = counts.get(verb);
    }

    // Increment the argtype for the verb
    if( !verbMap.containsKey(dep) ) verbMap.put(dep, new Count(0,1));
    else {
      Count count = verbMap.get(dep);
      count.occurrences++;
    }
  }


  /**
   * Tally a verb/dep into our global _counts hashmaps.
   */
  private void incrementDocCount(Map<String,Map<String,Count>> counts,
      String verb, String dep) {
    // Find the verb
    Map<String,Count> verbMap;
    if( !counts.containsKey(verb) ) {
      verbMap = new HashMap(8);
      counts.put(verb, verbMap);
    } else {
      verbMap = counts.get(verb);
    }

    // Increment the argtype for the verb
    if( !verbMap.containsKey(dep) ) verbMap.put(dep, new Count(1,0));
    else {
      Count count = verbMap.get(dep);
      count.docCount++;
    }
  }

  private boolean isVerbal(String reln) {
    if( BasicEventAnalyzer.acceptableDependency(reln) )
      return true;
    return false;
  }

  /**
   * Print the verbs with their dependency-count pairs.
   */
  private void countsToFile(Map<String,Map<String,Count>> counts, int numDocs,
      String outfile) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(outfile));
      out.write("NUMDOCS " + numDocs + "\n");

      for( Map.Entry<String,Map<String,Count>> entry : counts.entrySet() ) {
        out.write(entry.getKey());

        Map<String,Count> verbCounts = entry.getValue();
        for( Map.Entry<String,Count> entry2 : verbCounts.entrySet() ) {
          Count count = entry2.getValue();
          out.write("\t" + entry2.getKey() + "\t" + 
              count.docCount + "\t" + count.occurrences);
        }
        out.write("\n");
      }

      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Print lists of verbs that are transitive, and ones that are intransitive.
   */
  private void outputTransitive(Map<String,Map<String,Count>> counts, String outfile) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(outfile));

      // Loop over the verbs!
      for( String verb : counts.keySet() ) {
        if( isTransitiveFromCounts(counts, verb) )
          out.write("t " + verb);
        else out.write("i " + verb);
        out.write("\n");
      }

      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Count verb/dep occurrences by reading each input line.
   * Doesn't build actual dependency objects from JavaNLP...just straight 
   * from strings.
   */
  private void plainTextCount(String path) {
    try {
      // Open the gzipped or normal text file
      BufferedReader in;
      if( path.endsWith(".gz") )
        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(path))));
      else in = new BufferedReader(new FileReader(path));

      Set<String> seen = new HashSet();
      Set<String> seenLemma = new HashSet();
      Set<String> seenFiller = new HashSet();
      Set<String> seenFillerLemma = new HashSet();
      _numStories = 0;

      // Read the lines
      String line;
      boolean skipping = false;
      while( (line = in.readLine()) != null ) {

        // e.g. <DOC id="NYT_ENG_20060901.0001" num="0" type="story" >
        if( line.startsWith("<DOC ") ) {	
          String story = line.substring(9, line.indexOf('"',9));
          if( duplicates.contains(story) ) skipping = true;
          else {
            skipping = false;
            _numStories++;
            seen.clear();
            seenLemma.clear();
            if( _countFillers ) {
              seenFiller.clear();
              seenFillerLemma.clear();
            }
          }
          if( skipping ) System.out.print("skipping ");
          System.out.println(story);
        }

        // e.g. <D>nsubj has-18 GTC-3</D>
        else if( !skipping && line.startsWith("<D>") ) {
          String parts[] = line.split("\\s+");
          if( parts.length != 3 ) {
            System.out.println("ERROR: bad dependency line: " + line);
            //	    System.exit(-1);
          }
          else {
            String reln = parts[0].substring(3);
            String token  = parts[1].substring(0, parts[1].lastIndexOf('-'));
            String filler = parts[2].substring(0, parts[2].lastIndexOf('-'));
            token = token.toLowerCase();
            filler = filler.toLowerCase();

            // Lemmatize as a verb if possible.
            String lemma = lemmatize(token, reln);
            String lemmaFiller = lemmatizeFiller(filler, reln);

            // Change numerals into a single string.	    
            if( isNumber(token) ) {
              token = NUMBER_STRING;
              lemma = NUMBER_STRING;
            }
            if( isNumber(filler) ) {
              filler = NUMBER_STRING;
              lemmaFiller = NUMBER_STRING;
            }

            // Merge relations to a smaller set of relations.
            reln = CountTokenPairs.normalizeRelation(reln, false);

            //	  System.out.println(token + "\t" + lemma + "\t" + filler + "\t" + lemmaFiller + 
            //			     "\t" + reln);

            // Increment the counts.
            incrementCount(_counts, token, reln);
            incrementCount(_countsLemmas, lemma, reln);
            if( _countFillers ) incrementCount(_fillerCounts, filler, reln);
            if( _countFillers ) incrementCount(_fillerCountsLemmas, lemmaFiller, reln);

            // Increment document counts.
            if( !seen.contains(token + "-" + reln) ) {
              seen.add(token + "-" + reln);
              incrementDocCount(_counts, token, reln);
            }
            if( !seenLemma.contains(lemma + "-" + reln) ) {
              seenLemma.add(lemma + "-" + reln);
              incrementDocCount(_countsLemmas, lemma, reln);
            }
            if( _countFillers ) {
              if( !seenFiller.contains(filler + "-" + reln) ) {
                seenFiller.add(filler + "-" + reln);
                incrementDocCount(_fillerCounts, filler, reln);
              }
              if( !seenFillerLemma.contains(lemmaFiller + "-" + reln) ) {
                seenFillerLemma.add(lemmaFiller + "-" + reln);
                incrementDocCount(_fillerCountsLemmas, lemmaFiller, reln);
              }
            }
          }
        }

        //	if( _numStories > 2 ) break;
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * @param token A string in the governor position of a relation.
   * @param reln The relation.
   */
  private String lemmatize(String token, String reln) {
    String lemma = null;
    if( isVerbal(reln) ) {
      lemma = _wordnet.verbToLemma(token);
    }
    // Lemmatize as nouns, verbs, adjectives, and then just the string.
    if( lemma == null ) {
      lemma = _wordnet.nounToLemma(token);
      if( lemma == null )
        lemma = _wordnet.verbToLemma(token);
      if( lemma == null )
        lemma = _wordnet.adjectiveToLemma(token);
      if( lemma == null )
        lemma = token;
    }
    return lemma;
  }

  /**
   * @param token A string in the dependent slot of a relation.
   * @param reln The relation with the slot.
   */
  private String lemmatizeFiller(String token, String reln) {
    String lemma = null;

    // Guess noun first.
    lemma = _wordnet.nounToLemma(token);

    // Lemmatize as verb, adjective, and then just the string.
    if( lemma == null ) {
      lemma = _wordnet.verbToLemma(token);
      if( lemma == null )
        lemma = _wordnet.adjectiveToLemma(token);
      if( lemma == null )
        lemma = token;
    }

    return lemma;
  }

  /**
   * Read a counts file into memory
   */
  public void fromFile(String path) {
    String line = null;
    if( _counts == null ) _counts = new HashMap();
    else _counts.clear();
    _totalCount = 0;

    System.out.println("CountVerbDeps fromFile " + path);
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      line = in.readLine();

      // Save the total number of documents these counts are from.
      if( line != null && line.startsWith("NUMDOCS ") )
        _numStories = Integer.valueOf(line.substring(8));

      // e.g. "spearhead o 16 p 4 s 12"
      while( (line = in.readLine()) != null ) {
        String parts[] = line.split("\\s+");

        //	if( parts.length < 3 || (parts.length % 2 != 1) ) {
        if( parts.length < 4 ) {
          System.out.println("Strange line format (skipping): " + line);
          //	  System.exit(1);
        }
        else {
          // Extract the verb
          String verb = parts[0];
          Map<String,Count> verbMap = new HashMap(8);
          _counts.put(verb, verbMap);
          // Extract the dependent types
          for( int i = 1; i < parts.length; i += 3 ) {
            Count count = new Count(Integer.valueOf(parts[i+1]), 
                Integer.valueOf(parts[i+2]));
            verbMap.put(parts[i], count);
            _totalCount += count.docCount;
          }
        }
      }
    } catch( Exception ex ) { 
      System.out.println("Error on line: " + line);
      ex.printStackTrace(); 
      System.exit(-1); 
    }
  }


  /**
   * Read a list of transitive verbs from file into memory
   */
  public void transitivesFromFile(String path) {
    if( transitives != null ) transitives.clear();
    else transitives = new HashSet();

    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;

      // t spearhead
      // i fall
      while( (line = in.readLine()) != null ) {
        if( line.startsWith("t ") )
          transitives.add(line.substring(2));
        else if( line.startsWith("i ") ) {
          // do nothing
        }
      }

    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * Determine if object arguments have been seen often enough for this
   * verb to make a determination of transitivity.
   *
   * Assume transitive if no evidence to the contrary.
   * Mark as intransitive if subject+object is more than 8, and there are more than
   * 40% subjects than objects.
   */
  private boolean isTransitiveFromCounts(Map<String,Map<String,Count>> counts, 
      String verb) {
    Map<String,Count> verbMap = counts.get(verb);

    Count sub = verbMap.get(WordEvent.DEP_SUBJECT);
    Count obj = verbMap.get(WordEvent.DEP_OBJECT);

    int sCounts = 0;
    if( sub != null ) sCounts = sub.occurrences;
    int oCounts = 0;
    if( obj != null ) oCounts = obj.occurrences;

    int diff = sCounts - oCounts;
    if( diff <= 0 || (sCounts + oCounts < 8) || (float)diff/(float)sCounts < 0.4 )
      return true;
    return false;
  }

  /**
   * Returns true if we know the verb is transitive.  False otherwise, which means
   * it is either intransitive or we just don't know about it.  Use the loaded set
   * of transitive decisions, not based on counts in memory.
   */
  public boolean isTransitive(String verb) {  
    if( transitives.contains(verb) )
      return true;
    return false;
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

  public Set<String> getRelns(String token) {
    Map<String,Count> relnMap = _counts.get(token);
    if( relnMap != null )
      return relnMap.keySet();
    else return null;
  }

  /**
   * Get the number of documents a token and a relation were seen in.
   * Returns zero if token-reln is not found.
   */
  public int getDocCount(String token, String reln) {
    if( _counts != null ) {
      Map<String,Count> relnMap = _counts.get(token);
      if( relnMap != null ) {
        Count count = relnMap.get(reln);
        if( count == null ) return 0;
        else return count.docCount;
      }
      else return 0;
    }
    else return 0;
  }

  public int getTotalCount() { return _totalCount; }
  public int getTotalDocs() { return _numStories; }

  /**
   * Read a directory of dependency files and count each one.
   */
  public void processDir() {
    int numDocs = 0;
    int numFiles = 0;

    if( _dataPath.length() > 0 ) {
      File dir = new File(_dataPath);

      // Directory of files.
      if( dir.isDirectory() ) {
        for( String file : Directory.getFilesSorted(_dataPath) ) {
          if( file.contains("deps") ) {
            System.out.println("file: " + file);
            plainTextCount(_dataPath + File.separator + file);
          }
        }
      }
      // Else single text file input.
      else {
        System.out.println("file: " + _dataPath);
        plainTextCount(_dataPath);
      }

      // Output the full counts!
      countsToFile(_countsLemmas, _numStories, _outfileLemmas);
      countsToFile(_counts, _numStories, _outfile);
      // SKIP THESE TO SAVE MEMORY IF THEY'RE NOT NEEDED.
      if( _countFillers ) {
        countsToFile(_fillerCountsLemmas, _numStories, _outfileFillerLemmas);
        countsToFile(_fillerCounts, _numStories, _outfileFiller);
      }

      // NOTE: needs to be tested since I've changed this class quite a bit.
      // Output verbs with transitive/intransitive distinctions
      //      outputTransitive(_counts, outTransitive);
      //      outputTransitive(_countsLemmas, outTransitiveLemmas);
    }
  }

  private class Count {
    public int docCount = 0;
    public int occurrences = 0;

    Count() { }
    Count(int doc, int occ) { 
      docCount = doc;
      occurrences = occ;
    }
  }

  public static void main(String[] args) {
    CountVerbDeps count = new CountVerbDeps(args);
    count.processDir();
  }
}

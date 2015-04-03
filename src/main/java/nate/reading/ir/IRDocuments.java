package nate.reading.ir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;

import nate.CalculateIDF;
import nate.DirectoryParser;
import nate.GigawordDuplicates;
import nate.GigawordParser;
import nate.IDFMap;
import nate.reading.Frame;
import nate.reading.FrameRole;
import nate.reading.LabelDocument;
import nate.reading.ProcessedData;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.SortableObject;
import nate.util.SortableScore;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;


/**
 * Takes a file of frames (frame words and scores), and searches a text corpus for the 
 * files that have those words...basic IR.  There is a scoring function that weights the 
 * words by their scores and by coverage over all words.
 * 
 * IRDocuments -input [giga|muc|enviro] -idf <idf-file> -frames <saved-frames> <corpus-dir,corpus-dir>
 * 
 * -distributed
 * If true, only processes one file in the input directories, and sets a lock for it.
 *
 * IRDocuments -reduce -frames <saved-frames> <mapper-output-files>
 * Read the mapper outputs into memory, trim the outliers, and output the best docs per frame.
 * - This is the "reduce" part of the mapreduce.
 * 
 */
public class IRDocuments {
  Map<String,Double> _searchWords;
  String _corpusPaths;
  String[] _dataDirectories;
  WordNet _wordnet;
  int _docType = 0;
  Set<String> _duplicates;
  String _duplicatesPath = "duplicates";
  IDFMap _idfs;
  String _outDir = "irresults-new-dist";
  boolean _distributed = false;
  final String _lockDir = "locks";
  final String _outputDistributed = "/u/natec/scr/irdist";

  Map<Integer,List<ScoredDocument>> _frameToMatchedDocuments = new HashMap<Integer,List<ScoredDocument>>();

  // Don't save really really short snippets.
  int _minTokensPerDocument = 100;

  // with normalizing [0,1]
  //  double _minGoodScore = 0.025;

  // with normalizing [0.5,1]
  //  double _minGoodScore = 0.125;

  // scoreCushioned with [0.5,1]
  double _minGoodScore = 0.013;



  public IRDocuments(String[] args) {
    HandleParameters params = new HandleParameters(args);
    handleParameters(params);

    // Duplicate Gigaword files to ignore
    _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);

    // We can have more than one directory, separated by commas.
    _corpusPaths = args[args.length-1];
    _dataDirectories = _corpusPaths.split(",");
    System.out.println("Processing directories: " + Arrays.toString(_dataDirectories));
  }


  private void handleParameters(HandleParameters params) {
    //    if( !params.hasFlag("-wordnet") || !params.hasFlag("-input") ) {
    //      System.err.println("IRDocuments -wordnet <path> -input giga|env|muc <search-words> <corpus-dir>");
    //      System.exit(-1);
    //    }
    if( !params.hasFlag("-wordnet") && !params.hasFlag("-reduce") ) {
      System.err.println("IRDocuments -wordnet <path> -frames <path> <corpus-dir>");
      System.exit(-1);
    }    

    if( params.hasFlag("-distributed") )
      _distributed = true;
    System.out.println("-distributed\t" + _distributed);    
    
    if( !params.hasFlag("-reduce") ) {
      _idfs = new IDFMap(params.get("-idf"));
      _wordnet = new WordNet(params.get("-wordnet"));
    }

    // Set corpus type
    if( params.hasFlag("-input") ) {
      if( params.get("-input").startsWith("giga") )
        _docType = DirectoryParser.GIGAWORD;
      else if( params.get("-input").startsWith("env") )
        _docType = DirectoryParser.ENVIRO;
      else if( params.get("-input").startsWith("muc") )
        _docType = DirectoryParser.MUC;
      else {
        System.out.println("Unknown text input type: " + params.get("-input"));
        System.exit(1);
      }
    }
  }
  
  /**
   * Given strings of sentences, split them into tokens and lemmatize, then return the
   * overall counts of the lemmas.
   * @param sentences
   * @return
   */
  private Counter<String> countWordsFromParseTrees(Collection<Tree> trees) {
    if( trees != null ) {
      IntCounter<String> tokenCounts = ParsesToCounts.countPOSTokens(trees);
      IntCounter<String> lemmaCounts = new IntCounter<String>();

      for( String token : tokenCounts.keySet() ) {
        String baseToken = token.substring(2); 
        String lemma = null;
        char tag = token.charAt(0);
        if( tag == 'v' )
          lemma = _wordnet.verbToLemma(baseToken);
        else if( tag == 'n' )
          lemma = _wordnet.nounToLemma(baseToken);
        else if( tag == 'j' )
          lemma = _wordnet.adjectiveToLemma(baseToken);

        if( lemma == null ) lemma = baseToken;

        // Only count nouns, verbs, and adjectives.
        if( tag == 'n' || tag == 'v' || tag == 'j' ) {
          if( lemma != null && lemma.length() > 2 ) {
            lemma = CalculateIDF.createKey(lemma, tag);
            // Only count tokens that aren't super frequent (low IDF scores).
            if( _idfs.get(lemma) > 1.0f )
              lemmaCounts.incrementCount(lemma, tokenCounts.getIntCount(token));
//            else System.out.println("Skipping1 " + token);
          } 
          //          else System.out.println("Skipping2 " + token);
        }
      }
      lemmaCounts.setCount("*TOTAL*", tokenCounts.totalCount());
//      System.out.println("size " + lemmaCounts.size());
      return lemmaCounts;
    }
    else return null;
  }

  /**
   * Simple score of the presence of key tokens in a map of token counts.
   * @param frameID The frame's ID, for debugging output.
   * @param keyTokens The tokens we are looking for.
   * @param numCoreTokens The number of core tokens in the keyTokens set. (v-play, not v-play#o#sport)
   * @param tokenCounts The tokens in the document and how many times each appears.
   * @return
   */
  private float scoreDocument(int frameID, Set<String> keyTokens, Integer numCoreTokens, Counter<String> tokenCounts, boolean debug) {
    Set<String> seen = new HashSet<String>();
    int score = 0;

    for( String key : keyTokens ) {
      double count = tokenCounts.getCount(key);
      if( count > 0.0 ) {
        score += count;
        seen.add(key.substring(2)); // add "release" from v-release or n-release
      }
    }

    //    System.out.println("  count=" + score + "\tnumkeys=" + keyTokens.size());
    float finalscore = (float)score / numCoreTokens.floatValue();
    // Coverage is the number of unique tokens seen (not counting words with both noun and verb).
    int coverage = seen.size();

    if( debug && coverage > 0 )
      System.out.printf("Frame %d\tcoverage %d\t%s\tfinalscore %.3f\n", frameID, coverage, seen, finalscore);

    if( coverage > 3 || coverage > Math.max(1, numCoreTokens/4) )
      return finalscore;
    else
      return 0.0f;
  }


  public static boolean validFilename(String path) {
    if( (path.matches(".*nyt.*") || path.matches(".*ap.*")) && !path.endsWith("~") )
      return true;
    else return false;
  }

  private void saveDocWithFrame(int frameID, ScoredDocument doc) {
    List<ScoredDocument> docs = _frameToMatchedDocuments.get(frameID);
    if( docs == null ) {
      docs = new ArrayList<ScoredDocument>();
      _frameToMatchedDocuments.put(frameID, docs);
    }
    docs.add(doc);
  }
  
  private void saveDocWithFrame(int frameID, String docname, double docScore, Counter<String> tokenCounts) {
    // Pull out the length of the document from the hash.
    int numTokensInDoc = (int)tokenCounts.getCount("*TOTAL*");
    tokenCounts.remove("*TOTAL*");
    saveDocWithFrame(frameID, new ScoredDocument(docname, docScore, numTokensInDoc, tokenCounts));
  }

  /**
   * Returns true if our current locked year matches the new year,
   * or if we can successfully create a file lock for the new year.
   */
  private boolean checkLock(String filename) {
    // Check to see if the year is already locked by another process
    File lockFile = new File(_lockDir + File.separator + "irdoc-" + filename + ".lock");
    if( !lockFile.exists() ) {
      try {
        // It wasn't locked, so try creating the lock
        boolean created = lockFile.createNewFile();
        if( created )
          return true;
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
    return false;
  }
  
  /**
   * Given the list of tokens for each frame, make a new map that stores the *core size* of each
   * list. This avoids double-counting words like v-play and v-play#o#sport.  We want the true base
   * size of the list based on just the tokens.
   * @param frameToDesired
   * @return
   */
  private Map<Integer,Integer> getDesiredSizes(Map<Integer,Set<String>> frameToDesired) {
    Map<Integer,Integer> map = new HashMap<Integer,Integer>();
    for( Map.Entry<Integer,Set<String>> entry : frameToDesired.entrySet() ) {
      Set<String> roots = new HashSet<String>();
      for( String token : entry.getValue() ) {
        int pound = token.indexOf('#');
        if( pound == -1 )
          roots.add(token);
        else roots.add(token.substring(0,pound));
      }
      map.put(entry.getKey(), roots.size());
//      map.put(entry.getKey(), entry.getValue().size());
      System.out.println("Entry size " + map.get(entry.getKey()) + " from " + frameToDesired.get(entry.getKey()));
    }
    return map;
  }
  
  private void findBestDocumentsInFile(String filepath, Set<String> keyTokens, Frame[] frames, Map<Integer,Set<String>> frameToDesired) {
    int numFiles = 0;

    // Frame ID to size of desired token list (minus any object collocations)
    Map<Integer,Integer> frameToDesiredSize = getDesiredSizes(frameToDesired);
    
    System.out.println("reading " + filepath);
    int numstories = 0;

    // Open the file.
    ProcessedData process = new ProcessedData(filepath, null, null, null);

    // Read the documents one by one.
    process.nextStory();
    Collection<String> parseStrings = process.getParseStrings();
    while( parseStrings != null ) {
      if( _duplicates.contains(process.currentStory()) ) {
        // Nothing.
      }
      else {
        System.out.println(process.currentStory());

        //            Counter<String> tokenCounts = ParsesToCounts.countPOSTokens(TreeOperator.stringsToTrees(parseStrings));
        Counter<String> tokenCounts = countWordsFromParseTrees(TreeOperator.stringsToTrees(parseStrings));
//        System.out.println("Token Counts: " + tokenCounts);

        // Score the frames.
        SortableObject<Frame>[] scored = new SortableObject[frames.length];
        int i = 0;
        for( Frame frame : frames ) {
          float score = 0.0f;
          if( frame.tokens().contains("v-kidnap") || frame.tokens().contains("v-explode") || frame.tokens().contains("v-kill") )
            score = scoreDocument(frame.getID(), frameToDesired.get(frame.getID()), frameToDesiredSize.get(frame.getID()), tokenCounts, true);
          else
            score = scoreDocument(frame.getID(), frameToDesired.get(frame.getID()), frameToDesiredSize.get(frame.getID()), tokenCounts, false);
          scored[i++] = new SortableObject<Frame>(score, frame);
        }
        Arrays.sort(scored);

        // Print some scores.
        for( i = 0; i < 4; i++ ) {
          if( scored[i].score() > 0.0f )
            System.out.printf("  --%d\t%.3f\t%s\n", ((Frame)scored[i].key()).getID(), scored[i].score(), Util.collectionToString(((Frame)scored[i].key()).tokens(), 10));
        }

        // Trim the token counts for space savings...
        trimSingletons(tokenCounts);

        // Is the top frame awesome?  (formerly * 2.0f)
        //              if( scored[0].score() > 0.3f && scored[0].score() > scored[1].score()*1.5f ) {

        for( int xx = 0; xx < scored.length; xx++ ) {
          if( scored[xx].score() >= 0.4f ) {
            Frame topFrame = (Frame)scored[xx].key();
            // Just output bombing and kidnapping for now...
            //                if( topFrame.getID() == 32 || topFrame.getID() == 6 ) {
            System.out.println("AWESOME: " + topFrame.getID() + "\t" + frameToDesired.get(topFrame.getID()));
            System.out.println("  - score = " + scored[xx].score());
            // Store the document's token counts for later doc comparison.
            saveDocWithFrame(topFrame.getID(), process.currentStory(), scored[xx].score(), tokenCounts);
            //                  System.out.println("  - frame found " + _frameToMatchedDocuments.get(topFrame.getID()).size() + " docs.");
          }
        }
        numstories++;
      }

      process.nextStory();
      parseStrings = process.getParseStrings();
//      if( numstories == 50 ) break;
    }
    numFiles++;
    //          if( numFiles == 1 ) break;
    Util.reportMemory();
  }
  
  /**
   * Save to disk the globally saved documents that matched frames.
   * @param path File path in which to save the documents.
   */
  private void saveDocsToFile(String path) {
    // Open the output file.
    PrintWriter writer = null;
    try {
      writer = new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
    } catch( IOException ex ) { 
      ex.printStackTrace();
      System.exit(-1);
    }

    // Print the scored documents for each frame.
    for( Map.Entry<Integer, List<ScoredDocument>> entry : _frameToMatchedDocuments.entrySet() ) {
      writer.println(entry.getKey());
      for( ScoredDocument doc : entry.getValue() )
        writer.println(doc);
    }
    writer.close();
  }
  
  public static String stripGZ(String path) {
    if( path.endsWith(".gz") )
      return path.substring(0,path.length()-3);
    else return path;
  }
  
  /**
   * Remove all keys that have a count of 1 or less.
   */
  private void trimSingletons(Counter<String> tokenCounts) {
    Set<String> removal = new HashSet<String>();
    for( Map.Entry<String, Double> entry : tokenCounts.entrySet() ) {
      if( entry.getValue() <= 1.0 )
        removal.add(entry.getKey());
    }
    for( String remove : removal )
      tokenCounts.remove(remove);
  }

  /**
   * 
   * @param docHandler The reader that gives us documents (lists of sentences).
   * @param keyTokens All tokens in all frames that we care about.
   * @param frames All of the frames we want to score.
   * @param frameToDesired A map from frame ID to the set of tokens most important to that frame.
   *                       We match documents for IR using these sets.
   * @return
   */
  private void findBestDocuments(Set<String> keyTokens, Frame[] frames, Map<Integer,Set<String>> frameToDesired) {
    // Loop over each file in the directory.
    for( String dirPath : _dataDirectories ) {
      String[] files = Directory.getFilesSorted(dirPath);
      for( String file : files ) {
        System.out.println("check " + file);
        if( validFilename(file) ) {
          System.out.println(file);
          
          if( !_distributed )
            findBestDocumentsInFile(dirPath + File.separator + file, keyTokens, frames, frameToDesired);
          else if( _distributed && checkLock(file) ) {
            System.out.println("Distributed on file " + file);
            findBestDocumentsInFile(dirPath + File.separator + file, keyTokens, frames, frameToDesired);
            saveDocsToFile(_outputDistributed + File.separator + stripGZ(file));
            _frameToMatchedDocuments.clear();
//            return;
          }
        }
      }
    }
  }

  private double cosine(Counter<String> x, Counter<String> y) {
    double dot = 0.0;
    double xmag = 0.0;
    double ymag = 0.0;

    if( x == null || y == null ) return 0.0;

    if( x.size() > y.size() ) { 
      Counter<String> temp = x;
      x = y;
      y = temp;
    }

    // loop over x's features
    for( Map.Entry<String,Double> entry : x.entrySet() ) {
      Double yvalue = y.getCount(entry.getKey());
      if( yvalue != null )
        dot += entry.getValue() * yvalue;
//      System.out.println(entry.getKey() + "\t" + entry.getValue() + "\t" + yvalue);
      xmag += entry.getValue() * entry.getValue();
    }

    // loop over y's features
    for( Map.Entry<String,Double> entry : y.entrySet() )
      ymag += entry.getValue() * entry.getValue();

    System.out.println("xmag = " + xmag + " ymag = " + ymag);
    
    if( xmag != 0.0 && ymag != 0.0 ) {
      double denom = Math.sqrt(xmag) * Math.sqrt(ymag);
      return dot / denom;
    } 
    else return 0.0;
  }

  /**
   * Calculate the average vector of all vectors in the list.
   * @param scoredDocs
   * @return
   */
  private Counter<String> average(List<ScoredDocument> scoredDocs) {
    Counter<String> average = new ClassicCounter<String>();
    for( ScoredDocument scored : scoredDocs ) {
      for( Map.Entry<String,Double> entry : scored.tokenCounts.entrySet() ) {
        if( !entry.getKey().equals("*TOTAL*") )
          average.incrementCount(entry.getKey(), entry.getValue());
      }
    }

    double numDocs = (double)scoredDocs.size();
    for( Map.Entry<String,Double> entry : average.entrySet() )
      entry.setValue(entry.getValue() / numDocs);

    return average;
  }

  private double standardDeviation(double average, double[] scores) {
    double sumDiffs = 0.0;
    for( double score : scores ) sumDiffs += (average - score) * (average - score);
    double stddev = Math.sqrt(sumDiffs / (double)scores.length);
    return stddev;
  }

  /**
   * Remove all tokens from the Counter that have a score below 0.1.
   */
  private void trimLowScores(Counter<String> vec) {
    Set<String> removal = new HashSet<String>();
    for( String token : vec.keySet() )
      if( vec.getCount(token) <= 0.1 )
        removal.add(token);

    for( String remove : removal )
      vec.remove(remove);
  }

  /**
   * Save each frame's documents to a seperate file in a seperate directory.
   */
  private void saveToFileDocNames(Map<Integer,List<ScoredDocument>> frameToDocuments, Map<Integer,Frame> idToFrame) {
    System.out.println("-- saving to files --");

    for( Integer frameID : frameToDocuments.keySet() ) {
      System.out.println(" - frame " + frameID + " has " + frameToDocuments.get(frameID).size() + " docs.");
      List<ScoredDocument> scoredDocs = frameToDocuments.get(frameID);
      
      String dirpath = _outDir + File.separatorChar + frameID;
      File dir = new File(dirpath);
      boolean success = dir.mkdirs();
      System.out.println("mkdir " + dirpath + " " + success);
      String outpath = dirpath + File.separatorChar + "docs.txt";
      PrintWriter writer = null;
      try {
        writer = new PrintWriter(new BufferedWriter(new FileWriter(outpath, false)));
        // Write the frame's tokens as the first line.
        writer.write(idToFrame.get(frameID).tokens().toString());
        writer.write("\n");
        // Write the retrieved document names, one per line.
        int num = 0;
        for( ScoredDocument doc : scoredDocs ) {
          writer.write("<DOC id=\"" + doc.docname + "\" type=" + doc.score + "\n");
          num++;
        }
        System.out.println("Wrote " + num + " docs to " + outpath);
      } catch( Exception ex ) { ex.printStackTrace(); }
      if( writer != null ) 
        writer.close();     
    }
  }
  
  /**
   * Remove any documents that are far away from the average token vector.
   * @param frameToDocuments
   */
  private void trimOutliers(Map<Integer,List<ScoredDocument>> frameToDocuments) {
    System.out.println("-- trimming outliers --");

    for( Integer frameID : frameToDocuments.keySet() ) {
      List<ScoredDocument> scoredDocs = frameToDocuments.get(frameID);
      Set<ScoredDocument> removal = new HashSet<ScoredDocument>();

      System.out.println("Frame " + frameID + " has " + scoredDocs.size() + " docs.");

      Counter<String> averageVector = average(scoredDocs);
      System.out.println("AVG: " + toStringSortedDouble(averageVector));

      // Store all cosine similarity values.
      double sumCosines = 0.0;
      double[] cosines = new double[scoredDocs.size()]; 
      int i = 0;
      for( ScoredDocument scoredDoc : scoredDocs ) {
        double cos = cosine(scoredDoc.tokenCounts, averageVector);
        cosines[i] = cos;
        sumCosines += cos;
        System.out.println("doc " + i + " cosine\t" + cos);
        System.out.println("  : " + scoredDoc);
        i++;
      }

      // Calculate the stddev.
      double averageCosine = sumCosines / scoredDocs.size();
      double sumDiffs = 0.0;
      for( double cos : cosines ) sumDiffs += (averageCosine - cos) * (averageCosine - cos);
      double stddev = Math.sqrt(sumDiffs / (double)scoredDocs.size());
      System.out.println("Got average cosine = " + averageCosine);
      System.out.println("Got standard dev = " + stddev);

      int lessthan = 0;
      for( double cos : cosines ) {
        if( cos < averageCosine - stddev )
          lessthan++;
      }
      System.out.println("Number less than one deviation below the mean:  " + lessthan);

      lessthan = 0;
      for( double cos : cosines ) {
        if( cos < averageCosine - (2.0*stddev) )
          lessthan++;
      }
      System.out.println("Number less than two deviations below the mean: " + lessthan);

      // Mark documents scoring below the average as removable.
      i = 0;
      for( double cosine : cosines ) {
//        System.out.println("Comparing cos " + cosine + " <= " + averageCosine);
        if( cosine <= averageCosine )
          removal.add(scoredDocs.get(i));
        i++;
      }
      // Physically remove them.
      for( ScoredDocument remove : removal ) {
        System.out.println("Removing " + remove.docname);
        scoredDocs.remove(remove);
      }

      System.out.println("Post-removal: frame " + frameID + " has " + scoredDocs.size() + " docs.");
      averageVector = average(scoredDocs);
      System.out.println("AVG (post-removal): " + toStringSortedDouble(averageVector));
      for( ScoredDocument doc : scoredDocs ) System.out.println("post: " + doc.docname + "\t" + doc.score);
    }
  }

  private String toStringSortedDouble(Counter<String> counter) {
    return toStringSorted(counter, false);
  }

  private String toStringSortedInt(Counter<String> counter) {
    return toStringSorted(counter, true);
  }

  /**
   * Builds a Counter from the saved string version.
   */
  private Counter<String> counterFromString(String str) {
    String[] items = str.split(", ");
    Counter<String> counts = new IntCounter<String>();
    for( String item : items ) {
      int equalsign = item.indexOf('=');
      int num = Integer.valueOf(item.substring(equalsign+1));
      if( num > 0 )
        counts.setCount(item.substring(0,equalsign), num);
    }
    return counts;
  }
  
  /**
   * Print the counter in order of the highest counts first.
   * @param outputIntegers If true, print ints, if false, print a single decimal place.
   */
  private String toStringSorted(Counter<String> counter, boolean outputIntegers) {
    if( counter == null ) return null;

    // Sort the counts.
    SortableScore[] counts = new SortableScore[counter.size()];
    int i = 0;
    for( Map.Entry<String, Double> entry : counter.entrySet() )
      counts[i++] = new SortableScore(entry.getValue(), entry.getKey());
    Arrays.sort(counts);

    // Build the string.
    StringBuffer sb = new StringBuffer();
    int xx = 0;
    for( SortableScore score : counts ) {
      if( xx++ > 0 ) sb.append(", ");
      if( outputIntegers ) sb.append(score.key() + "=" + (int)score.score());
      else sb.append(score.key() + "=" + String.format("%.1f", score.score()));
    }
    return sb.toString();
  }

  /**
   * Search the entire corpus and compare document vectors to this target vector,
   * saving any documents that score higher than the given minScore.
   * @param targetVector A list of target vectors to compare against.
   * @param minScore A document must have a cosine similarity higher than this.
   */
  private Map<Integer,List<ScoredDocument>> searchCorpusByVector(Map<Integer,Counter<String>> targetVectors, Map<Integer,Double> minScores) {
    if( targetVectors.size() != minScores.size() ) {
      System.err.println("ERROR searchCorpusByVector: target vectors have diff size than min scores: " + targetVectors.size() + " and " + minScores.size());
      System.exit(-1);
    }
    System.out.println("-- searching the corpus by vector!! --");
    for( Integer targetID : targetVectors.keySet() ) {
      System.out.println("  with frame " + targetID + " minscore=" + minScores.get(targetID));
      System.out.println("  -- vector: " + toStringSorted(targetVectors.get(targetID), false));
    }

    // Put all the matching documents in here.
    Map<Integer,List<ScoredDocument>> matches = new HashMap<Integer,List<ScoredDocument>>();
    for( Integer targetID : targetVectors.keySet() ) matches.put(targetID, new ArrayList<ScoredDocument>());

    // Loop over the directory files.
    int numFiles = 0;
    for( String dirPath : _dataDirectories ) {
      String[] files = Directory.getFilesSorted(dirPath);
      for( String file : files ) {
        System.out.println("check " + file);
        if( validFilename(file) ) {        
          System.out.println(file);

          // Open the file.
          ProcessedData process = new ProcessedData(dirPath + File.separator + file, null, null, null);

          // Read the documents one by one.
          process.nextStory();
          Collection<String> parseStrings = process.getParseStrings();
          while( parseStrings != null ) {

            if( _duplicates.contains(process.currentStory()) ) {
              // Nothing.
            }
            else {
              System.out.println(process.currentStory());
              Counter<String> tokenCounts = countWordsFromParseTrees(TreeOperator.stringsToTrees(parseStrings));
              tokenCounts.remove("*TOTAL*");

              //            System.out.println("doc vec: " + toStringSortedInt(tokenCounts));
              for( Integer targetID : targetVectors.keySet() ) {
                double cosine = cosine(targetVectors.get(targetID), tokenCounts);
                System.out.println("  id " + targetID + "\tcos " + cosine);

                if( cosine > minScores.get(targetID) ) {
                  matches.get(targetID).add(new ScoredDocument(process.currentStory(), cosine, (int)tokenCounts.totalCount(), null));
                  System.out.println("  - top match " + targetID + "\t" + process.currentStory() + "\t" + cosine);
                }
              }
            }

            process.nextStory();
            parseStrings = process.getParseStrings();
          }
          numFiles++;
          //        if( numFiles == 2 ) break;
          Util.reportMemory();
        }
      }
    }

    return matches;
  }

  /**
   * Given a map of frame IDs to their list of scored documents, we build an average vector
   * for each frame, and then research the corpus using that average vector, saving all
   * documents that score highly with the average.
   * @param frameToDocuments Map from frame ID to a list of scored documents that match.
   */
  private void searchCorpusByVector(Map<Integer,List<ScoredDocument>> frameToDocuments) {
    Map<Integer,Counter<String>> meanVectors = new HashMap<Integer,Counter<String>>();
    Map<Integer,Double> minScores = new HashMap<Integer,Double>();

    System.out.println("-- search corpus by vector --");

    // Build the mean document vector for each frame.
    for( Integer frameID : frameToDocuments.keySet() ) {
      List<ScoredDocument> scoredDocs = frameToDocuments.get(frameID);
      System.out.println("Frame " + frameID + " has " + scoredDocs.size() + " docs.");

      Counter<String> averageVector = average(scoredDocs);
      trimLowScores(averageVector);
      System.out.println("AVG: " + toStringSortedDouble(averageVector));

      // Calculate the cosine average and standard deviation. 
      double sumCosines = 0.0;
      double[] cosines = new double[scoredDocs.size()]; 
      int i = 0;
      for( ScoredDocument scoredDoc : scoredDocs ) {
        double cos = cosine(scoredDoc.tokenCounts, averageVector);
        cosines[i] = cos;
        sumCosines += cos;
        System.out.println("doc " + i + " cosine\t" + cos);
        System.out.println("  : " + scoredDoc);
        i++;
      }
      double averageCosine = sumCosines / scoredDocs.size();
      double stddev = standardDeviation(averageCosine, cosines);
      System.out.println("Frame " + frameID + "\tavgcos=" + averageCosine + "\tstddev=" + stddev);
      System.out.println("  -- min=" + (averageCosine+stddev));

      meanVectors.put(frameID, averageVector);
      minScores.put(frameID, averageCosine + stddev);
    }

    // Find all documents that are very similar to the average ones.
    Map<Integer,List<ScoredDocument>> results = searchCorpusByVector(meanVectors, minScores);
    for( Integer frameID : results.keySet() ) {
      System.out.println("FINAL Frame " + frameID);
      for( ScoredDocument doc : results.get(frameID) )
        System.out.println(doc.docname + "\t" + doc.score);
    }
  }

  /**
   * Reads a list of frames that we want to find relevant documents for, and then
   * we count all words in all documents.  We then process those counts for each
   * document and score the documents, returning the best documents for each frame.
   * @param framesPath
   */
  public void searchCorpusByFrames(String framesPath) {
    Frame[] frames = LabelDocument.framesFromDiskCache(framesPath);
    Map<Integer,Set<String>> frameToDesired = new HashMap<Integer,Set<String>>();
    Map<Integer,Frame> idToFrame = new HashMap<Integer,Frame>();

    // Make a set of all tokens that we want to track.
    Set<String> desired = new HashSet<String>();
    for( Frame frame : frames ) {
      // Main frame's tokens.
      Set<String> tokens = frame.tokens();
      Set<String> frameDesired = new HashSet<String>(tokens);
      idToFrame.put(frame.getID(), frame);

      // Main frame's argument tokens. (adds tokens like n-bomb that are always filling a role).
      if( frame.getRoles() != null ) {
        for( FrameRole role : frame.getRoles() ) {
          for( Map.Entry<String,Double> entry : role.getArgs().entrySet() ) {
            if( entry.getValue() > 0.3 ) {
              // Don't match NER-like arguments.
              if( !entry.getKey().equalsIgnoreCase("person") && !entry.getKey().equalsIgnoreCase("organization") &&
                  !entry.getKey().startsWith("*pro") ) {
                frameDesired.add(CalculateIDF.createKey(entry.getKey(), 'n'));
                System.out.println("Adding high scored argument to desired list: " + entry.getKey());
              }
            }
          }
        }
      }

      desired.addAll(frameDesired);
      frameToDesired.put(frame.getID(), frameDesired);
      System.out.println("Frame desired " + frame.getID() + " : " + frameDesired);
    }

    // Get all word counts of all documents.
    //    DocumentHandler handler = getDocumentHandler();

    System.out.println("Desired: " + desired);
    findBestDocuments(desired, frames, frameToDesired);

    // If this is a distributed run, we don't trim and save. The "reduce" stage will do that.
    if( !_distributed ) {
      trimOutliers(_frameToMatchedDocuments);
      saveToFileDocNames(_frameToMatchedDocuments, idToFrame);
      //    searchCorpusByVector(_frameToMatchedDocuments);
    }
  }

  /**
   * Given a directory, we expect the directory to hold all the files from the output of
   * the various "mappers". Each should have a list of frame IDs with all of the documents
   * that were matched with that frame. We read them all into a global hash map.
   */
  public void reduce(String dirPath, String framesPath) {
    // Clear the global map from frame ID to matched documents.
    _frameToMatchedDocuments.clear();

    // Load the frames from a file.
    Frame[] frames = LabelDocument.framesFromDiskCache(framesPath);
    Map<Integer,Frame> idToFrame = new HashMap<Integer,Frame>();
    for( Frame frame : frames )
      idToFrame.put(frame.getID(), frame);

    // Read the output mapper files one by one.
    String[] files = Directory.getFilesSorted(dirPath);
    for( String file : files ) {
      System.out.println("Opening " + file);
      Util.reportMemory();
      
      BufferedReader in = null;
      try {
        in = new BufferedReader(new FileReader(dirPath + File.separator + file));

        String line = null;
        int currentFrameID = -1;
        
        while( (line = in.readLine()) != null ) {
          // If the line is just one number, it is a new frame.
          if( line.matches("^\\d+\\s*$") )
            currentFrameID = Integer.valueOf(line);
          // Else the line is a single document's token counts.
          else {
            ScoredDocument doc = scoredDocFromString(line);
            if( doc != null )
              saveDocWithFrame(currentFrameID, doc);
          }
        }
      } catch( IOException ex ) {
        ex.printStackTrace();
        System.exit(-1);
      }
      
      // DEBUG
      for( Integer frameID : _frameToMatchedDocuments.keySet() )
        System.out.println("  Frame " + frameID + " has " + _frameToMatchedDocuments.get(frameID).size() + " docs.");
    }
    
    // Now trim the outliers for each frame.
    trimOutliers(_frameToMatchedDocuments);
    // Save the resulting list of document names for each frame.
    saveToFileDocNames(_frameToMatchedDocuments, idToFrame);
  }
  
  private ScoredDocument scoredDocFromString(String str) {
    String[] parts = str.split("\t");
    if( parts.length == 4 ) {
      ScoredDocument doc = new ScoredDocument(parts[0], Double.valueOf(parts[1]), Integer.valueOf(parts[2]),
          counterFromString(parts[3]));
      return doc;
    }
    else { 
      System.out.println("ERROR: weird doc line: " + str);
      return null;
    }
  }

  /**
   * Helper class to hold a document's name, score, and token counts.
   */
  private class ScoredDocument {
    public String docname;
    public double score;
    public Counter<String> tokenCounts;
    public int documentLength;

    ScoredDocument(String docname, double score, int docLength, Counter<String> tokenCounts) {
      this.docname = docname;
      this.score = score;
      this.tokenCounts = tokenCounts;
      this.documentLength = docLength;
    }

    public String toString() {
      return docname + "\t" + score + "\t" + documentLength + "\t" + toStringSortedInt(tokenCounts);
    }    
  }

  public static void main(String[] args) {
    HandleParameters params = new HandleParameters(args);

    IRDocuments ir = new IRDocuments(args);

    if( params.hasFlag("-reduce") )
      ir.reduce(args[args.length-1], params.get("-frames"));
    else if( params.hasFlag("-frames") )
      ir.searchCorpusByFrames(params.get("-frames"));
    
  }
}

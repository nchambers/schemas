package nate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import nate.util.Directory;


public class IDFMap {
//  private HashMap<String,Integer> _wordToDocAppearances;
//  private HashMap<String,Integer> _wordToFrequencies;
//  private HashMap<String,Float> _wordToIDF;
  private Map<String,WordCounts> _wordToCounts;
  private int _numDocs = 0;
  private int _totalCorpusCount = 0;

  private class WordCounts {
    public int docFrequency;
    public int tokenFrequency;
    public float idfScore;
    public float informationContent;
    public String toString() { return docFrequency + " " + tokenFrequency + " " + idfScore + " " + informationContent; }
  }
  
  public IDFMap() {
    this(20000);
  }

  public IDFMap(int capacity) {
//    _wordToDocAppearances = new HashMap<String, Integer>(capacity);
//    _wordToFrequencies = new HashMap<String, Integer>(capacity);
//    _wordToIDF = new HashMap<String, Float>(capacity);
    _wordToCounts = new HashMap<String, WordCounts>(capacity);
  }

  public IDFMap(String filename) {
    this(20000);
    fromFile(filename);
  }

  public static String findIDFPath() {
  	String[] paths = { "/home/nchamber/Projects/muc/tokens-idfs-ap/tokens-lemmas.idf-2006", 
  	                   "/home/nchamber/scr/tokens-idfs-ap/tokens-lemmas.idf-2006",
  	                   "/Users/mitts/Projects/stanford/eventparser/tokens-lemma.idf-2004",
  	                   "C:\\cygwin\\home\\sammy\\projects\\muc\\tokens-lemmas.idf-2006" };
  	for( String path : paths )
  		if( Directory.fileExists(path) )
  			return path;
    return null;
  }
  
  /**
   * @param filename The name of a file to create for the IDF scores
   */  
  public void saveToFile(String filename) {
    Set<String> keys = _wordToCounts.keySet();
    System.out.println("key size " + keys.size());
    TreeSet<String> treeset = new TreeSet<String>(keys);
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      out.write("NUMDOCS " + _numDocs + "\n");
      for( String word : treeset ) {
        //	out.write( word + " : " + wordToDocAppearances.get(word) + " : " +
        //		   wordToIDF.get(word) + "\n");
        String outstr = String.format("%s\t%d\t%d\t%.2f", word, getFrequency(word), getDocCount(word), get(word));
//        out.write( word + "\t" + 
//            _wordToFrequencies.get(word) + "\t" +
//            _wordToDocAppearances.get(word) + "\t" +
//            _wordToIDF.get(word) + "\n");
        out.write(outstr);
        float ic = getInformationContent(word); 
        if( ic == 0 ) out.write("\t0");
        else out.write(String.format("\t%.2f", ic));
        out.write("\n");
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * @param filename The name of the IDF file
   * @desc Read in a list of words and their IDF counts
   */
  public void fromFile(String filename) {
    if( filename == null || filename.length() > 0 ) {
      System.out.println("...reading IDF file " + filename);
      String line = null;

      BufferedReader in = null;
      try {
        in = new BufferedReader(new FileReader(filename));
      } catch( Exception ex ) { 
        System.out.println("(IDFMap.java) Error opening file: " + filename);
        ex.printStackTrace();
        System.exit(-1);
      }

      try {
        line = in.readLine();
        // number of docs
        _numDocs = Integer.valueOf(line.substring(line.indexOf(' ')+1));
        System.out.println("...based on " + _numDocs + " docs");
        // word list
        while( (line = in.readLine()) != null ) {
          String parts[] = line.split("\t");
          int freq = Integer.valueOf(parts[1]);
          _totalCorpusCount += freq;

          setFrequency(parts[0], freq);
          setDocCount(parts[0], Integer.valueOf(parts[2]));
          setIDFScore(parts[0], Float.valueOf(parts[3]));
          if( parts.length > 4 ) setInformationContent(parts[0], Float.valueOf(parts[4]));
//          System.out.println("idf\t" + parts[0] + "\t" + getCounts(parts[0]));
        }
      } catch( Exception ex ) { 
        System.out.println("Exception reading line: " + line);
        ex.printStackTrace(); 
        System.exit(1);
      }

    } else {
      System.out.println("WARNING: IDFMap fromFile() got an empty path");
      System.exit(1);
    }
  }

  public void setFrequency(String token, int freq) {
    WordCounts counts = getCounts(token);
    counts.tokenFrequency = freq;
//    _wordToFrequencies.put(token, freq);
  }
  
  public void setDocCount(String token, int count) {
    WordCounts counts = getCounts(token);
    counts.docFrequency = count;
//    _wordToDocAppearances.put(token, count);
  }
  
  public void setIDFScore(String token, float score) {
    WordCounts counts = getCounts(token);
    counts.idfScore = score;
//    _wordToIDF.put(token, score);
  }
  
  public void setInformationContent(String token, float score) {
    WordCounts counts = getCounts(token);
    counts.informationContent = score;
  }
  
  private WordCounts getCounts(String token) {
    WordCounts counts = _wordToCounts.get(token);
    if( counts == null ) {
      counts = new WordCounts();
      _wordToCounts.put(token, counts);
    }
    return counts;
  }
  
  public void printIDF() {
    Set<String> keys = _wordToCounts.keySet();
    TreeSet<String> treeset = new TreeSet<String>(keys);
    //    Object words[] = keys.toArray();
    //    Arrays.sort(words);
    for( String word : treeset )
      System.out.println( word + " " + get(word) );
  }


  /**
   * @desc Calculate the IDF score for all verbs
   */
  public void calculateIDF() {
    System.out.println("Calculating IDF");
    _totalCorpusCount = 0;
    double numDocs = (double)_numDocs;
    
    for( Map.Entry<String,WordCounts> entry : _wordToCounts.entrySet() ) {
      WordCounts counts = entry.getValue();
      double seenInDocs = (double)counts.docFrequency;
      float idf = (float)Math.log(numDocs / seenInDocs);
      counts.idfScore = idf;
      _totalCorpusCount += counts.tokenFrequency;
    }
  }
  
  /**
   * @return True if the given word is in our map, false otherwise.
   */
  public boolean contains(String word) {
    return _wordToCounts.containsKey(word);
  }
  
  /**
   * @param word A string word
   * @return The IDF of the word, or 0 if the word is unknown.
   */
  public float get(String word) {
    WordCounts counts = _wordToCounts.get(word);   
    if( counts == null )
      return 0;
    else
      return counts.idfScore;
  }

  /**
   * @param word A string word
   * @return The number of documents in which the word appears
   */
  public int getDocCount(String word) {
    WordCounts counts = _wordToCounts.get(word);   
    if( counts == null )
      return 0;
    else
      return counts.docFrequency;
  }

  /**
   * @param word A string word
   * @return The number of times the word appears (term frequency over corpus)
   */
  public int getFrequency(String word) {
    WordCounts counts = _wordToCounts.get(word);   
    if( counts == null )
      return 0;
    else
      return counts.tokenFrequency;
  }

  public float getInformationContent(String word) {
    WordCounts counts = _wordToCounts.get(word);   
    if( counts == null )
      return 0;
    else
      return counts.informationContent;
  }
  
  /**
   * @return The set of words
   */
  public Set<String> getWords() {
    return _wordToCounts.keySet();
  }
  
  /**
   * Make a new vector of just frequency counts. 
   * @param cutoff Return all tokens that occur at least this many times.
   * @return A vector of frequency counts.
   */
  public Map<String,Integer> getFrequencyVector(int cutoff) {
    Map<String,Integer> freq = new HashMap<String,Integer>();
    for( Map.Entry<String,WordCounts> entry : _wordToCounts.entrySet() ) {
      if( entry.getValue().tokenFrequency >= cutoff )
        freq.put(entry.getKey(), entry.getValue().tokenFrequency);
    }
    return freq;
  }

  /**
   * @desc Increase the number of documents a single word appears in by one.
   * @param word A string word
   */
  public void increaseDocCount(String word) {
    WordCounts counts = _wordToCounts.get(word);   
    if( counts == null ) {
      counts = new WordCounts();
      _wordToCounts.put(word, counts);
      counts.docFrequency = 1;
    }
    else counts.docFrequency++;
  }

  /**
   * @desc Increase the number of documents a single word appears in by one.
   * @param word A string word
   */
  public void increaseTermFrequency(String word) {
    WordCounts counts = _wordToCounts.get(word);   
    if( counts == null ) {
      counts = new WordCounts();
      _wordToCounts.put(word, counts);
      counts.tokenFrequency = 1;
    }
    else counts.tokenFrequency++;
  }

  /**
   * @return The sum of all term frequency counts of all words.
   */
  public int totalCorpusCount() {
    return _totalCorpusCount;
  }

  /**
   * @desc Increase the total # of documents
   */
  public void increaseDocCount() {
    //    System.out.println("Increasing total doc count ");
    _numDocs++;
  }

  public int numDocs() { return _numDocs; }
  
  public void clear() {
  	_wordToCounts.clear();
  	_wordToCounts = null;
  }
}
package nate.order;

import java.util.HashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.BufferedWriter;

import nate.GigaDocReader;
import nate.WordEvent;
import net.didion.jwnl.JWNL;


/**
 * Reads a directory of events files, pulling out all event
 * strings.  It creates numeric indices for unique strings
 * and for their lemmas and WordNet synsets.
 *
 * Used as a class, it reads and accesses the word-indices
 *
 * -preload
 * Load the index files and increment their current values.
 */
public class FeatureIndices {
  String wordnetPath;
  HashMap<String,String[]> wordnetHash;
  String directory;
  boolean preload = false; // true if we load indices ourselves
  int w = 1;
  int l = 1;
  int s = 1;
  int t = 1;
  HashMap<String,String> words   = new HashMap(100000);
  HashMap<String,String> lemmas  = new HashMap(80000);
  HashMap<String,String> synsets = new HashMap(40000);
  HashMap<String,String> times   = new HashMap(40000);

  public FeatureIndices() { }

  public FeatureIndices(String[] args) {
    handleParameters(args);

    // Load WordNet first
    try {
      JWNL.initialize(new FileInputStream(wordnetPath)); // WordNet
    } catch( Exception ex ) { ex.printStackTrace(); }

    // Load indices if -preload flag was given
    if( preload ) loadIndices();
  }
  
  public void handleParameters(String[] args) {
    int i = 0;
    while( i < args.length ) {
      // specifies the location of the WordNet .xml file
      if( args[i].equalsIgnoreCase("-wordnet") && args.length > i+1 ) {
	wordnetPath = args[i+1];
	i++;
      }
      else if( args[i].equalsIgnoreCase("-preload") && args.length > i+1 ) {
	preload = true;
      }
      i++;
    }
    directory = args[i-1];
  }

  /**
   * Loads indices files from a preset location
   */
  private void loadIndices() {
    setWordIndex(indexFromFile("wordIndex.txt"));
    setLemmaIndex(indexFromFile("lemmaIndex.txt"));
    setSynsetIndex(indexFromFile("synsetIndex.txt"));
  }

  private boolean isNumeral(String token) {
    if( token.matches("[\\d\\,\\-\\.$%]+") )
      return true;
    else return false;
  }

  /**
   * @param token A lowercased string...
   */
  private boolean isTimeNumeral(String token) {
    if( token.matches("[\\d\\,\\-\\.$%]+") )
      return true;

    if( token.equals("one") || token.equals("two") || token.equals("three") ||
	token.equals("four") || token.equals("five") || token.equals("six") ||
	token.equals("seven") || token.equals("eight") || token.equals("nine") )
      return true;

    return false;
  }


  /**
   * @return The string with numbers converted to "<NUM>"
   */
  private String normalizeTime(String str) {
    String newstr = "";
    str = str.toLowerCase();

    String parts[] = str.split("\\s+");
    int i = 0;
    for( String word : parts ) {
      if( i > 0 ) newstr += " ";
      if( isTimeNumeral(word) ) newstr += "<NUM>";
      else newstr += word;
      i++;
    }
    return newstr;
  }


  /**
   * Get the lemma and synset, and index all three
   */
  private void index(String word) {
    String[] types = null;
    word = word.toLowerCase();

    // Normalize all numerals
    if( isNumeral(word) ) {
      types = new String[3];
      types[0] = "<NUM>";
      types[1] = "<NUM>";
      types[2] = "<NUM>";
    } 

    else {
      // Check lookup table
      if( wordnetHash == null ) wordnetHash = new HashMap();

      if( wordnetHash.containsKey(word) ) 
	types = wordnetHash.get(word);
      else {
	types = EventParser.stringTypes(word);
	wordnetHash.put(word,types);
      }
    }

    // Save the indices
    if( !words.containsKey(types[0]) )   words.put(types[0], String.valueOf(++w));
    if( !lemmas.containsKey(types[1]) )  lemmas.put(types[1], String.valueOf(++l));
    if( !synsets.containsKey(types[2]) ) synsets.put(types[2], String.valueOf(++s));
  }


  /**
   * index the time expression - normalize the string before calling!
   */
  private void indexTime(String str) {
    // Save the index
    if( !times.containsKey(str) )  times.put(str, String.valueOf(++t));
  }


  /**
   * Write a String->String mapping to a file
   * @param index HashMap of Strings to Strings
   * @param filename The file to write to
   */
  public void indexToFile(HashMap<String,String> index, String filename) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      for( String str : index.keySet() ) {
	out.write(str + " " + index.get(str) + "\n");
      }
      out.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * Read an index from a text file
   * @return Mapping from strings to strings (a stringed integer)
   */
  public HashMap<String,String> indexFromFile(String filename) {
    HashMap<String,String> index = new HashMap();
    String line;

    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      while( (line = in.readLine()) != null ) {
	//	String parts[] = line.split("\\s+");
	//	index.put(parts[0],parts[1]);
	int pos = line.lastIndexOf(' ');
	index.put(line.substring(0,pos), line.substring(pos+1));
      }
      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }

    return index;
  }


  /**
   * Read a directory and process all *events* files.
   * Index every event string.
   */
  public void processInput() {
    System.out.println("Directory " + directory);

    File dir = new File(directory);
    if( dir.isDirectory() ) {
      String files[] = dir.list();
      
      for( String file : files ) {
	// Only look at *.gz files
        //	if( !file.startsWith(".") && file.contains("events") ) {
        if( file.contains("_1997") && file.contains(".events") ) {
	  System.out.println(file + "...");
	  GigaDocReader eventDoc = new GigaDocReader(directory + File.separator + file);
	  while( eventDoc.nextStory() ) {
	    for( WordEvent ev : eventDoc.getEvents() ) {
	      index(ev.token());
	    }
	  }
	}
      }

    } else System.out.println("Not a directory: " + directory);

    // Write out the index files
    System.out.println("Writing index files...");
    indexToFile(words,   "new-wordIndex.txt");
    indexToFile(lemmas,  "new-lemmaIndex.txt");
    indexToFile(synsets, "new-synsetIndex.txt");
  }


  public void setWordIndex(HashMap index) {
    words = index;
    w = 1;
    for( String i : words.values() ) {
      Integer val = Integer.parseInt(i);
      if( val > w ) w = val;
    }
    System.out.println("Set words max: " + w);
  }

  public void setLemmaIndex(HashMap index) {
    lemmas = index;
    l = 1;
    for( String i : lemmas.values() ) {
      Integer val = Integer.parseInt(i);
      if( val > l ) l = val;
    }
    System.out.println("Set lemmas max: " + l);
  }

  public void setSynsetIndex(HashMap index) {
    synsets = index;
    s = 1;
    for( String i : synsets.values() ) {
      Integer val = Integer.parseInt(i);
      if( val > s ) s = val;
    }
    System.out.println("Set synsets max: " + s);
  }

  public void setTimeIndex(HashMap index) {
    times = index;
    t = 1;
    for( String i : times.values() ) {
      Integer val = Integer.parseInt(i);
      if( val > t ) t = val;
    }
    System.out.println("Set times max: " + t);
  }

  /**
   * Get the index of a word
   */
  public String getWordIndex(String word) {
    if( isNumeral(word) ) word = "<NUM>";
    else word = word.toLowerCase();

    if( !words.containsKey(word) ) {
      System.out.println("ERROR: new word " + word);
      index(word);
    }
    return words.get(word);
  }

  /**
   * Get the index of a lemma
   */
  public String getLemmaIndex(String lemma) {
    if( isNumeral(lemma) ) lemma = "<NUM>";
    else lemma = lemma.toLowerCase();

    if( !lemmas.containsKey(lemma) )
      System.out.println("ERROR: new lemma ." + lemma + ".");
    return lemmas.get(lemma);
  }

  /**
   * Get the index of a synset
   */
  public String getSynsetIndex(String syn) {
    if( !synsets.containsKey(syn) )
      System.out.println("ERROR: new synset " + syn);
    return synsets.get(syn);
  }

  /**
   * Get the index of a time expression
   */
  public String getTimeIndex(String time) {
    time = normalizeTime(time);

    if( !times.containsKey(time) ) {
      System.out.println("ERROR: new time " + time);
      indexTime(time);
    }
    return times.get(time);
  }


  /**
   * Get the index of a preposition
   * ** Commented out prepositions have not been seen with time expressions
   */
  public static String getPrepositionIndex(String prep) {
    int pid = 1;
    if( prep.equalsIgnoreCase("during") ) pid = 2;
    else if( prep.equalsIgnoreCase("with") ) pid = 3;
    else if( prep.equalsIgnoreCase("on") ) pid = 4; 
    else if( prep.equalsIgnoreCase("among") ) pid = 5;
    else if( prep.equalsIgnoreCase("of") ) pid = 6;
    else if( prep.equalsIgnoreCase("for") ) pid = 7;
    else if( prep.equalsIgnoreCase("at") ) pid = 8;
    else if( prep.equalsIgnoreCase("in") ) pid = 9;
    else if( prep.equalsIgnoreCase("as") ) pid = 10;
    else if( prep.equalsIgnoreCase("by") ) pid = 11;
    else if( prep.equalsIgnoreCase("before") ) pid = 12;
    else if( prep.equalsIgnoreCase("into") ) pid = 13;
    else if( prep.equalsIgnoreCase("about") ) pid = 14;
    else if( prep.equalsIgnoreCase("from") ) pid = 15;
    else if( prep.equalsIgnoreCase("after") ) pid = 16;
    else if( prep.equalsIgnoreCase("since") ) pid = 17;
    else if( prep.equalsIgnoreCase("over") ) pid = 18;
    /*
    else if( prep.equalsIgnoreCase("without") ) pid = 19;
    else if( prep.equalsIgnoreCase("out") ) pid = 20;
    else if( prep.equalsIgnoreCase("despite") ) pid = 21;
    */
    else if( prep.equalsIgnoreCase("between") ) pid = 22;
//    else if( prep.equalsIgnoreCase("behind") ) pid = 23;

    else if( prep.equalsIgnoreCase("through") ) pid = 24;
    else if( prep.equalsIgnoreCase("under") ) pid = 25;
    /*
    else if( prep.equalsIgnoreCase("amid") ) pid = 26;
    else if( prep.equalsIgnoreCase("because") ) pid = 27;
    else if( prep.equalsIgnoreCase("besides") ) pid = 28;
    else if( prep.equalsIgnoreCase("if") ) pid = 29;
    else if( prep.equalsIgnoreCase("toward") ) pid = 30;
    else if( prep.equalsIgnoreCase("while") ) pid = 31;
    */
    else if( prep.equalsIgnoreCase("against") ) pid = 32;
    else if( prep.equalsIgnoreCase("throughout") ) pid = 33;
    else if( prep.equalsIgnoreCase("than") ) pid = 34;
    /*
    else if( prep.equalsIgnoreCase("beyond") ) pid = 35;
    else if( prep.equalsIgnoreCase("up") ) pid = 36;
    else if( prep.equalsIgnoreCase("like") ) pid = 37;
    else if( prep.equalsIgnoreCase("upon") ) pid = 38;
    else if( prep.equalsIgnoreCase("inside") ) pid = 39;
    */
    else if( prep.equalsIgnoreCase("around") ) pid = 40;

    // mostly with time expressions
    else if( prep.equalsIgnoreCase("within") ) pid = 41;
    else if( prep.equalsIgnoreCase("below") ) pid = 42;
    else if( prep.equalsIgnoreCase("until") ) pid = 43;
    else if( prep.equalsIgnoreCase("past") ) pid = 44;
    else System.out.println("UNKNOWN PREP: " + prep);

    return String.valueOf(pid);
  }

  public HashMap wordIndex() { return words; }
  public HashMap lemmaIndex() { return lemmas; }
  public HashMap synsetIndex() { return synsets; }
  public HashMap timeIndex() { return times; }

  public static void main(String[] args) {
    if( args.length < 1 ) System.err.println("No input directory given");
    else {
      // Run the System
      FeatureIndices ep = new FeatureIndices(args);
      ep.processInput();
    }
  }
}

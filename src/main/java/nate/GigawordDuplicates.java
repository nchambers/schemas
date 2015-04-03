package nate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;


/**
 * Class to process Gigaword files and look for duplicate documents.
 * Outputs a list of duplicates to a file named "duplicates".
 * @author Nate Chambers
 */
public class GigawordDuplicates {
  String _dataPath = "";
  HashMap<String,String> _initial;
  HashMap<String,String> _other;
  Vector<String> _duplicates;
  String _outputFile = "duplicates-new";


  // Constructor for finding the duplicates
  GigawordDuplicates(String[] args) {
    handleParameters(args);
    _initial = new HashMap<String, String>(20000);
    _other = new HashMap<String, String>(20000);
    _duplicates = new Vector<String>();
  }


  /**
   * @desc Read a list of document names from a file, return them
   * @return A set of document names of duplicate docs
   */
  public static HashSet<String> fromFile(String filename) {
    HashSet<String> dups = new HashSet<String>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;

      // each line is a document name
      while ( (line = in.readLine()) != null ) {
        line = line.trim();
        if( line.length() > 0 ) dups.add(line);
      }
    } catch( Exception ex ) { ex.printStackTrace(); }

    return dups;
  }


  private void handleParameters(String[] args) {
    _dataPath = args[args.length - 1];
  }


  private String cleanup(String str) {
    str = str.trim().toLowerCase();
    str = str.replaceAll("\\s+"," ");
    return str;
  }

  private String makeOther(Vector<String> sentences) {
//    return makeOtherStricter(sentences);
    if( sentences.size() >= 2 )
      return cleanup(sentences.elementAt(1));
    else return "";
  }

  /**
   * Use this if you want the first 3 sentences to match.
   */
  private String makeOtherStricter(Vector<String> sentences) {
    if( sentences.size() > 2 )
      return cleanup(sentences.elementAt(1)) + " " +
      cleanup(sentences.elementAt(2));
    else if( sentences.size() == 2 )
      return cleanup(sentences.elementAt(1));
    else return "";
  }
  
  private void saveStamp(String docname, Vector<String> sentences) {
    if( sentences != null && sentences.size() > 0 ) {
      _initial.put(docname, cleanup(sentences.elementAt(0)));
      _other.put(docname, makeOther(sentences));
    }
  }

  private boolean checkOriginality(String docname, Vector<String> sentences) {
    if( sentences != null && sentences.size() > 0 ) {
//      System.out.println("Checking " + docname);

      // Yes, check first sentence of every doc before it
      String first = cleanup(sentences.elementAt(0));
      String second = null;

      for( Map.Entry<String,String> entry : _initial.entrySet() ) {
        String doc = entry.getKey();
        // Check if the first sentence matches
        if( first.equals(entry.getValue()) ) {
//          System.out.println("Prelim match! " + first + "\n" + entry.getValue());
          if( second == null ) second = makeOther(sentences);
//          System.out.println("1. " + second);
//          System.out.println("2. " + _other.get(doc));
          // Check if the second sentence matches too
          if( second.equals(_other.get(doc)) ) {
//            System.out.println("Same doc! " + docname + " to " + doc);
            return false;
          }
        }
      }
    }
    return true;
  }


  private void printDuplicates() {
    System.out.println("Printing duplicates to file " + _outputFile);
    try {
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(_outputFile)));
      for( String docname : _duplicates ) writer.println(docname);
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }


  /**
   * @desc Parse each sentence and save to another file
   */
  public void processData() {
    int numDocs = 0;
    if( _dataPath.length() > 0 ) {

      File dir = new File(_dataPath);
      if( dir.isDirectory() ) {
        String files[] = dir.list();

        for( String file : files ) {
          // Only look at *.gz files
          if( !file.startsWith(".") && file.endsWith(".gz") ) {
//          if( file.startsWith("nyt_eng_200412") ) {
            System.out.println("file: " + file);
            GigawordHandler giga = new GigawordHandler(_dataPath + File.separator + file);

            // Read the documents in this file 
            Vector<String> sentences = giga.nextStory();
            while( sentences != null ) {
              numDocs++;
              System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") " + giga.currentStory);
              //              if( numDocs % 100 == 0 ) GigawordParser.reportMemory();

              boolean original = checkOriginality(giga.currentStory(), sentences);
              if( original ) saveStamp(giga.currentStory(), sentences);
              else {
                _duplicates.add(giga.currentStory());
                System.out.println("dupe " + giga.currentStory());
              }

              sentences = giga.nextStory();

              //	      if( numDocs == 12 ) return;
            }
            _initial.clear();
            _other.clear();
          }
        }
      }
    }

    printDuplicates();
  }


  public static void main(String[] args) {
    GigawordDuplicates dup = new GigawordDuplicates(args);
    dup.processData();
  }
}

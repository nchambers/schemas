package nate;

import java.util.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.BufferedReader;


/**
 * Class to process Gigaword files and look for duplicate documents.
 * Outputs a list of duplicates to a file named "duplicates".
 * @author Nate Chambers
 */
public class GigawordDuplicateSearch {
  String dataPath = "";
  Vector<String> documents;
  PrintWriter writer;
  String words[] = null;

  private HashSet<String> duplicates;
  private String duplicatesPath = "duplicates";



  // Constructor for finding the duplicates
  GigawordDuplicateSearch(String[] args) {
    handleParameters(args);
    documents = new Vector();
    try {
      writer = new PrintWriter(new BufferedWriter(new FileWriter("documents")));
    } catch( Exception ex ) { ex.printStackTrace(); }

    // Duplicate Gigaword files to ignore
    duplicates = GigawordDuplicates.fromFile(duplicatesPath);
  }


  /**
   * @desc Read a list of document names from a file, return them
   * @return A set of document names of duplicate docs
   */
  public static HashSet<String> fromFile(String filename) {
    HashSet<String> dups = new HashSet();
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
    int i = 0;
    if( args[i].equals("-words") ) {
      String words = args[i+1];
      parseSearchWords(words);
    } else { 
      System.err.println("No -words given");
      System.exit(1);
    }
    dataPath = args[args.length - 1];
  }


  private void parseSearchWords(String str) {
    words = str.split(",");
    System.out.println("Search: " + Arrays.toString(words));
  }


  private String cleanup(String str) {
    str = str.trim().toLowerCase();
    str = str.replaceAll("\\s+"," ");
    return str;
  }


  public void printAndClose() {
    System.out.println("Matched documents: " + documents.size());
    for( String docname : documents ) {
      System.out.println("printing: " + docname);
      writer.println(docname);
    }
    writer.close();
  }


  private boolean searchDoc(Vector<String> sentences) {
    for( String sentence : sentences ) {
      for( int i = 0; i < words.length; i++ ) {
	if( sentence.matches(".*[^a-zA-Z\\-]" + words[i] + "[^a-zA-Z\\-].*") ) {
	  return true;
	}
      }
    }
    return false;
  }



  /**
   * @desc Parse each sentence and save to another file
   */
  public void processData() {
    int numDocs = 0;
    if( dataPath.length() > 0 ) {
      
      File dir = new File(dataPath);
      if( dir.isDirectory() ) {
	String files[] = dir.list();

	for( String file : files ) {
	  // Only look at *.gz files
	  if( !file.startsWith(".") && file.endsWith(".gz") ) {
	    System.out.println("file: " + file);
	    GigawordHandler giga = new GigawordHandler(dataPath + File.separator + file);

	    // Read the documents in this file 
	    Vector<String> sentences = giga.nextStory();
	    while( sentences != null ) {
	      if( duplicates.contains(giga.currentStory()) ) { 
		System.out.println("Duplicate " + giga.currentStory());
	      } else {
		
		numDocs++;
		System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") " + giga.currentStory);
		//              if( numDocs % 100 == 0 ) GigawordParser.reportMemory();
		
		boolean found = searchDoc(sentences);
		if( found ) {
		  System.out.println("Match: " + giga.currentStory());
		  documents.add(giga.currentStory());
		}
	      }
		
	      if( numDocs == 120 ) return;
	      sentences = giga.nextStory();
	    }
	  }
	}
      }
    }

    printAndClose();
  }
  

  public static void main(String[] args) {
    GigawordDuplicateSearch search = new GigawordDuplicateSearch(args);
    search.processData();
    search.printAndClose();
  }
}

package nate;

import java.util.Vector;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;


/**
 * Reads a parsed file of documents. The parses probably came from running
 * GigawordParser over a Gigaword file.
 */
public class GigawordFiltered extends GigawordProcessed {
  Vector<String> documents;

  GigawordFiltered(String filename, String documentsFilename) {
    super(filename);
    readFilterList(documentsFilename);
  }
  

  /**
   * Create the list of document names to keep, filter the rest
   */
  private void readFilterList(String filename) {
    try {
      String line;
      BufferedReader in = new BufferedReader(new FileReader(filename));
      
      documents = new Vector();
      while ( (line = in.readLine()) != null ) {
	documents.add(line.trim());
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * @return True if the filename is in the list of filenames
   */
  private boolean inFilterList(String filename) {
    if( documents.contains(filename) ) return true;
    return false;
  }

  /**
   * @return A vector of strings representing parse trees from the
   * next document in the current parsed gigaword file.
   */  
  public Vector<String> nextStory() {
    Vector<String> sentences = super.nextStory();

    while( sentences != null && !inFilterList(super.currentStory()) ) {
      sentences = super.nextStory();
    }

    return sentences;
  }
}

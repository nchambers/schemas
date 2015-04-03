package nate;

import java.util.Vector;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;


/**
 * Reads a text file of documents.
 * This is exactly the same as GigawordFiltered, but it extends the basic
 * GigawordHandler so that we read non-parsed files.
 */
public class GigawordFilteredText extends GigawordHandler {
  Vector<String> documents;

  GigawordFilteredText(String filename, String documentsFilename) {
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
    System.out.println("Filtered next story...");
    Vector<String> sentences = super.nextStory();

    while( sentences != null && !inFilterList(super.currentStory()) ) {
      sentences = super.nextStory();
      System.out.println("Got " + super.currentStory());
    }

    return sentences;
  }

}

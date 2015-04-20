package nate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;

// This was in the older CoreNLP code, not the official dist.
//import edu.stanford.nlp.mt.tools.LanguageModelTrueCaser;

/**
 * Class that reads a text file with one sentence per line.
 * It prints the parse trees to a new text file.
 * 
 * * NOTE: not actually tested yet!
 */
public class TextHandler implements DocumentHandler {
  BufferedReader _in;
  int _numDocs = 0;
  int _currentDoc = 0;
  String _currentFilename;
  String _currentStory = "mainfile-only";
  int _currentStoryNum = 0;
//  LanguageModelTrueCaser _trueCaser;

  // dummy constructor for extended classes
  public TextHandler() { }

  public TextHandler(String filename) {
    initialize(filename);
  }

  public void initialize(String filename) {
    _currentFilename = filename;
    _numDocs = getNumDocs(filename);
    _currentDoc = 0;
    try {
      _in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { ex.printStackTrace(); }

    /*
    System.out.println("Loading True Caser!");
    _trueCaser = new LanguageModelTrueCaser();
    System.out.println("Loaded!!!");
    String[] tokens = { "general", "butisto", "said", "that", "he", "was", "happy" };
    String result[] = _trueCaser.trueCase(tokens, 3);
    System.out.println(Arrays.toString(result));
    System.exit(1);
     */
  }

  /**
   * This function is a convenience to calculate the total number of documents
   * contained in the file.
   * @return The number of documents listed in the given file
   */
  protected int getNumDocs(String filename) {
    return 1;
  }

  public Vector<String> nextStory() {
    return nextStory(false);
  }

  /**
   * @return A vector of strings representing sentences from the document.
   * Each document is just from newswire, so we use blank lines as
   * rough separators, and append everything else.
   */  
  public Vector<String> nextStory(boolean keepLineBreaks) {
    _currentDoc++;
    Vector<String> paragraphs = null;

    if( _in != null ) {
      paragraphs = new Vector<String>();

      try {
        String line = "";

        while ( (line = _in.readLine()) != null ) {
          // Skip empty lines.
          while( line != null && line.matches("^\\s*$") )
            line = _in.readLine();

          if( line != null && line.trim().length() > 0 )
            paragraphs.add(line.trim());
        }

        return paragraphs;
      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    // should have returned earlier if a valid document
    return paragraphs;
  }

  public void reset() { 
    try {
      _in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
    initialize(_currentFilename);
  }

  public boolean validFilename(String file) {
    if( file.contains(".txt") )
      return true;
    else return false;
  }

  public int currentDoc() { return _currentDoc; }
  public String currentStory() { return _currentStory; }
  public int numDocs() { return _numDocs; }
}
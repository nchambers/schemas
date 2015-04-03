package nate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Vector;

// Not in the official dist. In the old CoreNLP.
//import edu.stanford.nlp.mt.tools.LanguageModelTrueCaser;

/**
 * Class that reads a MUC file containing concatenated news stories.
 * Provides functions to read the sentences from each story in order.
 */
public class MUCHandler implements DocumentHandler {
  BufferedReader _in;
  int _numDocs = 0;
  int _currentDoc = 0;
  String _currentFilename;
  String _currentStory = "mainfile-only";
  int _currentStoryNum = 0;
//  LanguageModelTrueCaser _trueCaser;

  // dummy constructor for extended classes
  public MUCHandler() { }

  public MUCHandler(String filename) {
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
    StringBuffer sb = new StringBuffer();
    _currentDoc++;
    Vector<String> paragraphs = null;

    if( _in != null ) {
      try {
        String line;

        while ( (line = _in.readLine()) != null ) {
          // Beginning the next story!
          if( line.matches("^.+-[mM][uU][cC]\\d?-.*") ) {
            int space = line.indexOf(' ');
            if( space == -1 )
              _currentStory = line;
            else
              _currentStory = line.substring(0, line.indexOf(' '));

            // Get the story ID number
            _currentStoryNum = parseStoryNum(line);

            paragraphs = new Vector<String>();

            sb.setLength(0);
            boolean continuing = false;

            // Loop until the beginning of the next story is found
            while ((line = _in.readLine()) != null) {
              if( line.matches("^.+-[mM][uU][cC]\\d?-.*") ) {
                _in.reset();
                int trim = sb.length();
                while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
                if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
                // ** RETURN from the function! **
                return paragraphs;
              }

              if( sb.length() == 0 )
                line = removeTimestamp(line);

              // MUC text has annotations we remove: "[TEXT]"
              if( line.indexOf('[') >= 0 )
                line = removeBrackets(line);

              // empty line
              if( line.length() == 0 || line.matches("^\\s+$") ) {
                int trim = sb.length();
                while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
                if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
                // reset the buffer
                sb.setLength(0);
                continuing = false;
              }
              // line with text
              else {
                line = line.trim();
                // lowercase (if not true cased!)
                //		line = line.toLowerCase();

                if (continuing) sb.append(' ');
                else continuing = true;
                sb.append(line);
                // save the line break (for parse trees)
                if( keepLineBreaks ) sb.append("\n");
              }
              _in.mark(200);
            } // while line

            // Final return for last document.
            int trim = sb.length();
            while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
            if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
            return paragraphs;
          } // if DEV-MUC
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    // Add the last remaining sb string.
    if( sb.length() > 0 ) {
      int trim = sb.length();
      while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
      if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
      paragraphs.add(sb.substring(0,trim));
    }

    // should have returned earlier if a valid document
    return paragraphs;
  }

  public static int parseStoryNum(String line) {
    int num = -1;

    int hyphen = line.indexOf('-');
    hyphen = line.indexOf('-', hyphen+1); 
    // Get the story ID number
    int numstart = hyphen+1;
    int numend   = line.indexOf(' ',numstart);
    // e.g. DEV-MUC3-0002 (NOSC)  
    if( numend != -1 )
      num = Integer.parseInt(line.substring(numstart,numend));
    // e.g. DEV-MUC3-0002  
    else
      num = Integer.parseInt(line.substring(numstart));
    return num;
  }

  protected static String removeBrackets(String line) {
    return line.replaceAll("\\[.*\\]","");
  }

  /**
   * Removes the leading location/time from a MUC document.
   *     "SAN SALVADOR, 12 DEC 89 (ACAN - EFE) -- [TEXT] MANY POLITICAL,"
   */
  protected static String removeTimestamp(String line) {
    int position = line.indexOf("-- [");
    if( position == -1 ) position = line.indexOf("-- (");

    if( position > -1 ) {
      //      System.out.println("Removed: " + line.substring(0,position+2));
      return line.substring(position+2);
    }
    else return line;
  }

  public void reset() { 
    try {
      _in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
    initialize(_currentFilename);
  }

  public boolean validFilename(String file) {
    if( file.contains("-muc") || file.contains("-MUC") ) 
      return true;
    else return false;
  }

  public int currentDoc() { return _currentDoc; }
  public String currentStory() { return _currentStory; }
  public int numDocs() { return _numDocs; }
}
package nate;

import java.util.Vector;
import java.io.CharArrayReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;


public class EnviroHandler implements DocumentHandler {
  BufferedReader in;
  int numDocs = 0, largestStoryID = 0;
  int currentDoc = 0, currentStoryNum = 0;
  String currentFilename;
  String currentStory = "mainfile-only";

  // dummy constructor for extended classes
  public EnviroHandler() { }

  public EnviroHandler(String filename) {
    initialize(filename);
  }

  public void initialize(String filename) {
    currentFilename = filename;
    numDocs = getNumDocs(filename);
    currentDoc = 0;
    try {
      in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { ex.printStackTrace(); }
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
    currentDoc++;
    Vector<String> paragraphs = null;

    if( in != null ) {
      try {
        String line;
        boolean continuing = false;

        while ( (line = in.readLine()) != null ) {
          //	  System.out.println("line: " + line);

          if( paragraphs == null ) paragraphs = new Vector<String>();

          // empty line
          if( line.length() == 0 || line.matches("^\\s+$") ) {
            int trim = sb.length();
            while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
            //	    if( trim > 0 ) System.out.println("Saving: " + sb.substring(0,trim));
            if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
            // reset the buffer
            sb.setLength(0);
            continuing = false;
          }
          // line with text
          else {
            line = line.trim();

            // new paragraph
            if ( line.startsWith("<P>") || line.startsWith("<p>") ||
                line.startsWith("<H>") || line.startsWith("<h>") ) {
              continuing = false;
              int trim = sb.length();
              while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
              //	      if( trim > 0 ) System.out.println("Saving: " + sb.substring(0,trim));
              if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
              // reset the buffer
              sb.setLength(0);
              line = line.substring(3);
            }

            // save this line
            if (continuing) sb.append(' ');
            else continuing = true;
            sb.append(line.indexOf('&') >= 0 ? removeEscapes(line) : line);
            // save the line break (for parse trees)
            if( keepLineBreaks ) sb.append("\n");
          } 
        }

      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    // Add the last remaining sb string.
    if( sb.length() > 0 ) {
      int trim = sb.length();
      while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
      paragraphs.add(sb.substring(0,trim));
    }

    // should have returned earlier if a valid document
    return paragraphs;
  }

  protected static String removeEscapes(String line) {
    return line.replaceAll("&(amp|AMP);","&");
  }

  public void reset() { 
    try {
      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
    initialize(currentFilename);
  }


  public boolean validFilename(String file) {
    if( file.endsWith(".txt") ) 
      return true;
    else return false;
  }

  public int currentDoc() { return currentDoc; }
  public String currentStory() { return currentStory; }
  public int currentStoryNum() { return currentStoryNum; }
  public int numDocs() { return numDocs; }
  public int largestStoryID() { return largestStoryID; }
}
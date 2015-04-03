package nate;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.zip.GZIPInputStream;


public class GigawordHandler implements DocumentHandler {
  private final String TEXT_OPEN = "<TEXT>";
  BufferedReader in;
  int numDocs = -1, largestStoryID = 0;
  int currentDoc = 0, currentStoryNum = 0;
  String currentFilename;
  String currentStory;
  String currentHeadline;

  // dummy constructor for extended classes
  public GigawordHandler() { }

  public GigawordHandler(String filename) {
    initialize(filename);
  }

  public void initialize(String filename) {
    currentFilename = filename;
    //    numDocs = getNumDocs(filename);
    currentDoc = 0;
    try {
      if( filename.endsWith(".gz") )
        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
      else in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * This function is a convenience to calculate the total number of documents
   * contained in the file.
   * @return The number of documents listed in the given file
   */
  protected int getNumDocs(String filename) {
    System.out.println("getNumDocs(" + filename + ")");
    int total = 0;
    BufferedReader localin = null;

    try {
      if( filename.endsWith(".gz") )
        localin = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
      else localin = new BufferedReader(new FileReader(filename));

      String line;
      while ( (line = localin.readLine()) != null ) {
        // find the doc line of the next "story"
        if ( line.startsWith("<DOC ") && line.indexOf("type=\"story") >= 0 ) {
          total++;
          int storyID = parseStoryNum(line);
          if( storyID > largestStoryID ) largestStoryID = storyID;
        }
      }
    } catch( Exception ex ) { ex.printStackTrace(); }

    return total;
  }


  public Vector<String> nextStory() {
    return nextStory(false);
  }
  /**
   * I added this so you can close when you are done
   * Yates
   */
  public void closeFile()
  {
	  try {
		this.in.close();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }

  /**
   * Make sure these stay in sync with the other nextStory() functions!
   *
   * @return A vector of strings representing paragraphs <p> from the
   * next document in the current gigaword file.
   */  
  public Vector<String> nextStory(boolean keepLineBreaks) {
    StringBuffer sb = new StringBuffer();
    Vector<String> paragraphs = null;
    currentDoc++;
    currentStory = null;
    currentHeadline = null;

    if( in != null ) {
      try {
        String line;
        while ( (line = in.readLine()) != null ) {

          // find the doc line of the next "story"
          if ( !line.startsWith("<DOC ") || line.indexOf("type=\"story") < 0 ) continue;
          int namestart = line.indexOf('"')+1;
          currentStory = line.substring(namestart, line.indexOf('"',namestart));

          // Get the story ID number
          currentStoryNum = parseStoryNum(line);
          currentHeadline = null;

          paragraphs = new Vector<String>();

          // find the start of the headline
          while( !line.startsWith("<HEADLINE>") && !line.startsWith(TEXT_OPEN) ) {
            if( (line = in.readLine()) == null ) 
              return null;
          }
          // read the headline if there was one
          if( !line.startsWith(TEXT_OPEN) ) {
            currentHeadline = in.readLine();
            line = in.readLine();
            while( !line.startsWith("</HEADLINE>") && !line.startsWith(TEXT_OPEN) ) {
              currentHeadline += " " + line.trim();
              if( (line = in.readLine()) == null ) 
                return null;
            }
          }

          // find the start of the text
          while( !line.startsWith(TEXT_OPEN) ) {
            if( (line = in.readLine()) == null ) 
              return null;
          }

          if( line.startsWith(TEXT_OPEN) ) {
            sb.setLength(0);
            boolean continuing = false;

            // Loop until the end of the TEXT is found
            while( (line = in.readLine()) != null ) {

              if( line.startsWith("</TEXT>")) {
                int trim = sb.length();
                while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
                if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
                // ** RETURN from the function! **
                return paragraphs;
              }
              // new paragraph
              if( line.startsWith("<P>") || line.startsWith("<SENT") || line.startsWith("<S>") ) {
                continuing = false;
                int trim = sb.length();
                while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
                if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
                // reset the buffer
                sb.setLength(0);
              } 
              // continuing the paragraph
              else if( !line.startsWith("</P>") && !line.startsWith("</SENT>") && !line.startsWith("</S>") ) {
                if (continuing) sb.append(' ');
                else continuing = true;
                sb.append(line.indexOf('&') >= 0 ? removeEscapes(line) : line);
                // save the line break (for parse trees)
                if( keepLineBreaks ) sb.append("\n");
              }
              // headline
//              else if( line.startsWith("<HEADLINE>") ) {
//                System.out.println("headlines");
//                // Read the actual headline now, should be on the next line.
//                line = in.readLine();
//                System.out.println("line = " + line);
//                // Loop, in rare/impossible case that the headline is multiple lines.
//                while( line != null && !line.startsWith("<") ) {
//                  if( currentHeadline == null ) currentHeadline = line.trim();
//                  else currentHeadline += " " + line.trim();
//                  line = in.readLine();
//                  System.out.println("now line = " + line);
//                }
//              }
            }
          }
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    // should have returned earlier if a valid document
    return paragraphs;
  }

  /**
   * Make sure these stay in sync with the other nextStory() functions!
   */
  public Vector<String> nextStory(String storyName) {
    return nextStory(storyName, false);
  }

  public Vector<String> nextStory(String storyName, boolean keepLineBreaks) {
    StringBuffer sb = new StringBuffer();
    Vector<String> paragraphs = null;
    currentDoc++;
    currentStory = null;

    if( in != null ) {
      try {
        String line;
        while ( (line = in.readLine()) != null ) {
          // find the doc line of the next story "storyName"
          if ( !line.startsWith("<DOC ") || line.indexOf(storyName) < 0 ) continue;
          int namestart = line.indexOf('"')+1;
          currentStory = line.substring(namestart, line.indexOf('"',namestart));

          // Get the story ID number
          currentStoryNum = parseStoryNum(line);

          paragraphs = new Vector<String>();

          // find the start of the text
          while ( !line.startsWith(TEXT_OPEN) ) {
            if ((line = in.readLine()) == null) 
              return null;
          }

          if ( line.startsWith(TEXT_OPEN) ) {
            sb.setLength(0);
            boolean continuing = false;

            // Loop until the end of the TEXT is found
            while ((line = in.readLine()) != null) {
              if (line.startsWith("</TEXT>")) {
                int trim = sb.length();
                while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
                if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
                // ** RETURN from the function! **
                return paragraphs;
              }
              // new paragraph
              if ( line.startsWith("<P>") || line.startsWith("<SENT") || line.startsWith("<S>") ) {
                continuing = false;
                int trim = sb.length();
                while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
                if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
                // reset the buffer
                sb.setLength(0);
              } 
              // continuing the paragraph
              else if ( !line.startsWith("</P>") && !line.startsWith("</SENT>") && !line.startsWith("</S>") ) {
                if (continuing) sb.append(' ');
                else continuing = true;
                sb.append(line.indexOf('&') >= 0 ? removeEscapes(line) : line);
                // save the line break (for parse trees)
                if( keepLineBreaks ) sb.append("\n");
              }
            }
          }
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    // should have returned earlier if a valid document
    return paragraphs;
  }

  /**
   * Make sure these stays in sync with the above nextStory() functions!
   */
  public List<String> processStoryRandomAccess(DataInput in) {
    StringBuffer sb = new StringBuffer();
    List<String> paragraphs = null;
    currentDoc++;
    currentStory = null;

    if( in != null ) {
      try {
        String line = in.readLine();
        // Assume line is a <DOC id="..." />         
        int namestart = line.indexOf('"')+1;
        currentStory = line.substring(namestart, line.indexOf('"',namestart));

        // Get the story ID number
        currentStoryNum = parseStoryNum(line);

        paragraphs = new ArrayList<String>();

        // find the start of the text
        while ( !line.startsWith(TEXT_OPEN) ) {
          if ((line = in.readLine()) == null) 
            return null;
        }

        if ( line.startsWith(TEXT_OPEN) ) {
          sb.setLength(0);
          boolean continuing = false;

          // Loop until the end of the TEXT is found
          while ((line = in.readLine()) != null) {
            if (line.startsWith("</TEXT>")) {
              int trim = sb.length();
              while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
              if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
              // ** RETURN from the function! **
              return paragraphs;
            }
            // new paragraph
            if ( line.startsWith("<P>") || line.startsWith("<SENT") || line.startsWith("<S>") ) {
              continuing = false;
              int trim = sb.length();
              while( trim > 0 && Character.isSpaceChar(sb.charAt(trim-1)) ) trim--;
              if( trim > 0 ) paragraphs.add(sb.substring(0,trim));
              // reset the buffer
              sb.setLength(0);
            } 
            // continuing the paragraph
            else if ( !line.startsWith("</P>") && !line.startsWith("</SENT>") && !line.startsWith("</S>") ) {
              if (continuing) sb.append(' ');
              else continuing = true;
              sb.append(line.indexOf('&') >= 0 ? removeEscapes(line) : line);
              // save the line break (for parse trees)
              //                if( keepLineBreaks ) sb.append("\n");
            }
          }
        }
        else System.out.println("ERROR (GigawordHandler): didn't find an opening TEXT node.");
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
    else System.out.println("ERROR (GigawordHandler): file handler was null.");

    // should have returned earlier if a valid document
    return paragraphs;
  }
  
  public String getHeadline() {
    return currentHeadline;
  }
  
  /**
   * Jump to one specific story and return its full text
   */
  public String getStory(String name) {
    String full = "";
    currentDoc++;

    if( in != null ) {
      try {
        String line;
        while ( (line = in.readLine()) != null ) {

          // find the doc line of the desired "story"
          if ( line.indexOf(name) < 0 ) continue;

          int namestart = line.indexOf('"')+1;
          currentStory = line.substring(namestart, line.indexOf('"',namestart));

          // Get the story ID number
          currentStoryNum = parseStoryNum(line);

          // find the start of the text
          while ( !line.startsWith("<TEXT>") ) {
            if ((line = in.readLine()) == null) 
              return null;
          }

          if ( line.startsWith("<TEXT>") ) {
            StringBuffer sb = new StringBuffer();
            boolean continuing = false;

            // Loop until the end of the TEXT is found
            while ((line = in.readLine()) != null) {
              if (line.startsWith("</TEXT>")) {
                //		full += sb.toString().trim();
                // ** RETURN from the function! **
                //		return full;
                return sb.toString().trim();
              }
              // new paragraph
              if ( line.startsWith("<P>") || line.startsWith("<SENT") || line.startsWith("<S>") || line.startsWith("<DEP") ) {
                continuing = false;
                //		temp = sb.toString().trim();
                //		if( temp.length() > 0 ) full += temp + "\n\n";
                //		sb = new StringBuffer();
                sb.append('\n'); sb.append('\n');
              } 
              // continuing the paragraph
              else if (!line.startsWith("</P>") && !line.startsWith("</SENT>") && !line.startsWith("</S>") && !line.startsWith("</DEP") ) {
                if (continuing) sb.append(' ');
                else continuing = true;
                sb.append(line.indexOf('&') >= 0 ? removeEscapes(line) : line);
              }
            }
          }
        }
      } catch( Exception ex ) { ex.printStackTrace(); }
    }

    // should have returned earlier if a valid document
    return full;
  }

  /**
   * Parses a document line for the storynum <br>
   * <DOC id="nyt..." num="123" type="story">
   * @return The story id
   */
  public static int parseStoryNum(String line) {
    int num = -1;

    // Get the story ID number
    int numstart = line.indexOf("num=\"") + 5;
    int numend   = line.indexOf('"',numstart);
    if( numstart == 4 ) {
      numstart = line.indexOf("num=") + 4;
      numend   = line.indexOf(' ',numstart);
    }
    if( numstart > 3 )
      num = Integer.parseInt(line.substring(numstart,numend));
    return num;
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
    if( file.endsWith(".gz") || file.endsWith(".txt") ) 
      return true;
    else return false;
  }

  public int currentDoc() { return currentDoc; }
  public String currentStory() { return currentStory; }
  public int currentStoryNum() { return currentStoryNum; }
  public int largestStoryID() { return largestStoryID; }
  public int numDocs() { 
    if( numDocs == -1 ) numDocs = getNumDocs(currentFilename);
    return numDocs; 
  }
}
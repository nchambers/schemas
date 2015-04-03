package nate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

/**
 * Reads a file of parse trees, one parse per line.
 * (originally created to process the BNC as parsed with Charniak's parser)
 */
public class BNCParseHandler implements DocumentHandler {
  BufferedReader _in;
  int _currentDoc;
  
  public BNCParseHandler() { }

  public BNCParseHandler(String filename) {
    initialize(filename);
  }
  
  public void initialize(String filename) {
    _currentDoc = 0;
    try {
      if( filename.endsWith(".gz") )
        _in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
      else _in = new BufferedReader(new FileReader(filename));
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
  
  /**
   * There are no stories in the BNC Parses, just one parse per line.
   * This function returns 100 parses at a time, calling them "stories".
   * This is just to prevent reading in all gigabytes of parses at once.
   */
  public Vector<String> nextStory() {
    Vector<String> parseStrings = null;
    if( _in != null ) {
      try {
        String line;
        int count = 0;

        while ( (line = _in.readLine()) != null ) {
          if( parseStrings == null )
            parseStrings = new Vector<String>();
          parseStrings.add(line);
          count++;
          if( count == 100 ) break;
        }
        _currentDoc++;
      } catch( Exception ex ) { ex.printStackTrace(); }
    }
    return parseStrings;
  }

  public Vector<String> nextStory(boolean keepLineBreaks) {
    return nextStory();
  }

  public boolean validFilename(String filename) {
    if( filename != null && filename.contains("charniak") )
      return true;
    else return false;
  }
  
  // The Charniak parsed BNC is not separated into documents, so it's essentially
  // one huge file of parses.
  public String currentStory() { return "fulldoc"; }
  public int numDocs() { return 1; }
  public int currentDoc() { return _currentDoc; }
  
  /**
   * Reads the BNC format (one parse tree per line), and saves it in my own GigaDoc
   * format so the GigawordProcessed.java code can easily read it in.
   */
  public void convertLinesToGigadoc(String outputPath) {
    // Create the output file of full markup
    GigaDoc doc = null;
    try {
      doc = new GigaDoc(outputPath);
    } catch( Exception ex ) {
      System.out.println("Couldn't open file for writing: " + outputPath);
      System.exit(-1);
    }

    // Read the documents in this file 
    Vector<String> sentences = nextStory();
    int storyID = 0;
    int numDocs = 0;
    while( sentences != null ) {
      numDocs++;

      System.out.println(numDocs + ": " + currentDoc());

      // Put the parses into my XML gigadoc format.
      doc.openStory("doc" + storyID, storyID);
      for( String parse : sentences ) {
        // Replace charniak's S1 with stanford's ROOT.
        if( parse.startsWith("(S1 ") ) parse = "(ROOT " + parse.substring(4);
        doc.addParse(parse);
      }
      doc.closeStory();

      sentences = nextStory();
      storyID++;
    }
  }
  
  public static void main(String[] args) {
    if( args.length == 2 ) {
      BNCParseHandler bnc = new BNCParseHandler(args[0]);
      bnc.convertLinesToGigadoc(args[1]);
    } 
    else System.out.println("BNCParseHandler <input-filename> <output-path>");
  }
}

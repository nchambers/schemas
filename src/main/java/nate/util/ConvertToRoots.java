package nate.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileInputStream;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.dictionary.Dictionary;


/**
 * This class reads verbs from a file and converts them to their root forms.
 * It treats every token on a line as a verb and tries to find its base form.
 * If the token contains a number, it is not stemmed and just printed out
 * in its original form.
 * 
 * INPUT:  gold=1 NYT19980402.0453 ei2245 ei2248 said beatings
 * OUTPUT: gold=1 NYT19980402.0453 ei2245 ei2248 say beat
 */
public class ConvertToRoots {
  private static String newline = System.getProperty("line.separator");
  String wordnetPath = "";
  String inputPath = "";

  ConvertToRoots(String args[]) {
    handleParameters(args);

    // Load WordNet
    try {
      JWNL.initialize(new FileInputStream(wordnetPath)); // WordNet
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  public void handleParameters(String[] args) {
    wordnetPath = args[0];
    inputPath = args[1];
  }

  public void processInput() {
    try {
      String line;
      BufferedReader in = new BufferedReader(new FileReader(inputPath));
      
      while ( (line = in.readLine()) != null ) {

	// Split the line into its words
	String tokens[] = line.split(" ");

	// Get the base form of each word
	for( int i = 0; i < tokens.length; i++ ) {
	  if( i > 0 ) System.out.print(" ");

	  // If the token has a number, then just output it.
	  if( tokens[i].matches(".*\\d.*") ) {
	    System.out.print(tokens[i]);
	  } else {
	    String[] types = WordNet.stringTypes(tokens[i]);
	    System.out.print(types[1]);
	  }
	}
	System.out.println();
      }
    } catch( Exception ex ) { ex.printStackTrace(); }

  }

  public static void main(String[] args) {
    if( args.length < 1 ) System.err.println("No input file given");
    else {

      // Run the System
      ConvertToRoots conv = new ConvertToRoots(args);
      conv.processInput();
    }
  }
}

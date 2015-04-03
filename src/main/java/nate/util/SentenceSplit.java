package nate.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.ling.HasWord;


/**
 * Outputs to STDOUT the file with one sentence per line.
 *
 * -lower
 * Lowercase the words too.
 */
public class SentenceSplit {
  String _dataPath = "";
  String _serializedGrammar = "";
  boolean _lowercase = false;


  public SentenceSplit(String[] args) {
    handleParameters(args);
  }

  private void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-grammar") ) {
      _serializedGrammar = params.get("-grammar");
      System.out.println("Grammar " + _serializedGrammar);
    }
    if( params.hasFlag("-lower") ) {
      _lowercase = true;
    }
    _dataPath = args[args.length - 1];
  }

  private String normalizeJavaNLPToken(HasWord token) {
    String str = token.toString();
    if( str.equals("-LRB-") ) return "(";
    else if( str.equals("-RRB-") ) return ")";
    else return str;
  }
  
  /**
   * @desc Splits strings into sentences.  Prints one per line.
   * @param paragraphs List of strings of sentences
   */
  private void analyzeSentences(List<String> paragraphs) {
    // Paragraphs may be multiple sentences
    for( String fragment : paragraphs ) {
      String lowered = fragment.toLowerCase();
      
      // Don't process MUC document headers.
      if( lowered.startsWith("dev-muc") || (lowered.startsWith("tst") && lowered.contains("-muc")) ) {
        System.out.println("\n" + fragment + "\n");
      }

      else {
        // Split and loop over sentences.
        for( List<HasWord> sentence : Ling.getSentencesFromText(fragment) ) {
          int i = 0;
          for( HasWord token : sentence ) {
            if( i++ > 0 ) System.out.print(" ");
            System.out.print(normalizeJavaNLPToken(token));
          }
          System.out.println();
        }
      }
    }
  }

  /**
   * @desc Parse each sentence and save to another file
   */
  public void processFile() {
    if( _dataPath.length() > 0 ) {
      List<String> sentences = new ArrayList<String>();

      try {
        BufferedReader in = null;
        if( _dataPath.endsWith(".gz") )
          in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(_dataPath))));
        else in = new BufferedReader(new FileReader(_dataPath));

        // Read the lines from the text file. 
        String line;
        String text = "";
        while( (line = in.readLine()) != null ) {
          // Remove whitespace from ends.
          line = line.trim();

          // If the line is empty, then assume everything before it ended a sentence.
          if( line.length() == 0 ) {
            if( _lowercase )
              text = text.toLowerCase();
            if( text.length() > 0 ) {
              sentences.add(text);
              text = "";
            }
          } 
          // Append this line to the running text.
          else {
            if( text.length() == 0 ) text += line;
            else text += " " + line;
          }
        }
      } catch( Exception ex ) { ex.printStackTrace(); }

      // Split the text into sentences.
      analyzeSentences(sentences);
    }
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      SentenceSplit splitter = new SentenceSplit(args);
      splitter.processFile();
    }
  }
}

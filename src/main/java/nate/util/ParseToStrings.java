package nate.util;

import java.util.Collection;
import java.util.List;

import nate.GigawordHandler;
import nate.GigawordProcessed;

import edu.stanford.nlp.trees.Tree;


/**
 * Input is a GigaProcessed file of parses.
 * Outputs to STDOUT the file with tokenized sentences.  Default is one sentence
 * per line, but the -singleline flag outputs one document per line.
 *
 * -lower
 * Lowercase the words too.
 * 
 * -singleline
 * Output the sentences in each document in one long line.
 * Each document has its own line.
 * (does not output the story IDs)
 * 
 */
public class ParseToStrings {
  String _dataPath = "";
  String _serializedGrammar = "";
  boolean _lowercase = false;
  boolean _singleline = false;


  public ParseToStrings(String[] args) {
    handleParameters(args);
  }

  private void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-grammar") ) {
      _serializedGrammar = params.get("-grammar");
      System.out.println("Grammar " + _serializedGrammar);
    }
    if( params.hasFlag("-lower") )
      _lowercase = true;
    if( params.hasFlag("-singleline") )
      _singleline = true;
    
    _dataPath = args[args.length - 1];
  }

  private String normalizeJavaNLPToken(String str) {
    if( str.equals("-LRB-") ) return "(";
    else if( str.equals("-RRB-") ) return ")";
    else return str;
  }
  
   /**
   * @desc Parse each sentence and save to another file
   */
  public void processParses() {
    if( _dataPath.length() > 0 ) {

      GigawordHandler parseReader = new GigawordProcessed(_dataPath);
      List<Tree> trees = TreeOperator.stringsToTrees(parseReader.nextStory());

      while( trees != null ) {
        // Story headers.
        if( !_singleline ) {
          System.out.println();
          System.out.println(parseReader.currentStory());
        }
        
        for( Tree tree : trees ) {
          StringBuffer sb = new StringBuffer();
          Collection<Tree> leaves = TreeOperator.leavesFromTree(tree);
          for( Tree leaf : leaves ) {
            String token = normalizeJavaNLPToken(leaf.firstChild().value());
            if( sb.length() > 0 ) sb.append(' ');
            if( _lowercase )
              sb.append(token.toLowerCase());
            else
              sb.append(token);
          }
          if( _singleline )
            System.out.print(sb.toString() + " ");
          else
            System.out.println(sb.toString());
        }
        trees = TreeOperator.stringsToTrees(parseReader.nextStory());
        if( _singleline ) System.out.println();
      }
    }
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      ParseToStrings splitter = new ParseToStrings(args);
      splitter.processParses();
    }
  }
}

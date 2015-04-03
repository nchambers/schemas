package nate.reading;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nate.util.HandleParameters;


/**
 * Class was never used ... meant to read the output from DocLabel, and feed it back into filatova's 
 * approach.  
 *
 */
public class SpecificTester {
  Map<String,Set<String>> _doclist;
  TemplateTester _tester;
  String _guessFile;
  
  String _kidnapKeys = "/u/natec/corpora/muc34/TASK/CORPORA/key-kidnap.muc4";
  String _bombingKeys = "/u/natec/corpora/muc34/TASK/CORPORA/key-bombing.muc4";
  String _attackKeys = "/u/natec/corpora/muc34/TASK/CORPORA/key-attack.muc4";
  
  
  SpecificTester(String[] args) {
    HandleParameters params = new HandleParameters(args);
    _doclist = new HashMap<String,Set<String>>();
    _guessFile = params.get("-guessfile");
  }

  /**
   * Reads the output of running LabelDocument, which shows which documents were labeled
   * with which template type (either with LDA or clustering).
   * We look for "EVAL: KIDNAP" text and pull out the documents that our algorithms guessed.
   * Save the documents.
   * @param filename The path to the output file.
   */
  private void readGuessesFromOutputFile(String filename) {
    
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String line;
      String currentDoc = null; 
      
      while ((line = in.readLine()) != null) {
        if( line.contains("DEV-MUC3-") ) {
          int start = line.indexOf("DEV-MUC3-");
          currentDoc = line.substring(start, start+13);
        }
        
        // e.g. "EVAL: KIDNAP false match!"
        if( line.startsWith("EVAL: ") ) {
          if( line.contains(" match") ) {
            int space = line.indexOf(" ", 6);
            String type = line.substring(6, space);
            
            Set<String> docs = _doclist.get(type);
            if( docs == null ) {
              docs = new HashSet<String>();
              _doclist.put(type, docs);
            }
            docs.add(currentDoc);
            System.out.println("put " + type + " with " + currentDoc);

          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public void runtest() {
    readGuessesFromOutputFile(_guessFile);
//    
//    for( String domain : _doclist.keySet() ) {
//      System.out.println("** Testing Domain " + domain + " **");
//      // Setup the subset of documents for this domain.
//      _tester._filterList = _doclist.get(domain);
//      
//      // Set the MUC keys to evaluate on.
//      if( domain.equalsIgnoreCase("kidnap") )  _tester._answerKey = new MUCKeyReader(_kidnapKeys);
//      if( domain.equalsIgnoreCase("bombing") ) _tester._answerKey = new MUCKeyReader(_bombingKeys);
//      if( domain.equalsIgnoreCase("attack") )  _tester._answerKey = new MUCKeyReader(_attackKeys);
//
//      // Run the evaluation.
//      _tester.processSingleFiles();
//    }
  }
  
  public static void main(String[] args) {
    SpecificTester tester = new SpecificTester(args);
    tester.runtest();
  }
}

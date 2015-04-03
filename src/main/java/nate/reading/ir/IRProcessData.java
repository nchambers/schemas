package nate.reading.ir;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;

import nate.CalculateIDF;
import nate.GigawordDuplicates;
import nate.reading.ProcessedData;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Locks;
import nate.util.TreeOperator;
import nate.util.WordNet;

/**
 * Converts files of parse trees into files of token counts.
 */
public class IRProcessData {
  String[] _dataDirectories;
  WordNet _wordnet;
  String _corpusPaths;
  Set<String> _duplicates;
  String _duplicatesPath = "duplicates";
  String _outDir = "gigaTokenCounts";
  
  public IRProcessData(String[] args) {
    HandleParameters params = new HandleParameters(args);
    _wordnet = new WordNet(params.get("-wordnet"));

    // Duplicate Gigaword files to ignore
    _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);
    
    // We can have more than one directory, separated by commas.
    _corpusPaths = args[args.length-1];
    _dataDirectories = _corpusPaths.split(",");
    System.out.println("Processing directories: " + Arrays.toString(_dataDirectories));
  }

  /**
   * Given strings of sentences, split them into tokens and lemmatize, then return the
   * overall counts of the lemmas.
   * @param sentences
   * @return
   */
  public static IntCounter<String> countWordsFromParseTrees(Collection<Tree> trees, WordNet wordnet) {
    if( trees != null ) {
      IntCounter<String> tokenCounts = ParsesToCounts.countPOSTokens(trees);
      IntCounter<String> lemmaCounts = new IntCounter<String>();

      for( String token : tokenCounts.keySet() ) {
        String baseToken = token.substring(2); 
        String lemma = null;
        char tag = token.charAt(0);
        if( tag == 'v' )
          lemma = wordnet.verbToLemma(baseToken);
        else if( tag == 'n' )
          lemma = wordnet.nounToLemma(baseToken);
        else if( tag == 'j' )
          lemma = wordnet.adjectiveToLemma(baseToken);

        if( lemma == null ) lemma = baseToken;

        // Only count nouns, verbs, and adjectives.
        if( tag == 'n' || tag == 'v' || tag == 'j' ) {
          if( lemma != null && lemma.length() > 2 ) {
            lemma = CalculateIDF.createKey(lemma, tag);
            lemmaCounts.incrementCount(lemma, tokenCounts.getIntCount(token));
          } 
        }
      }
      lemmaCounts.setCount("*TOTAL*", tokenCounts.totalCount());
//      System.out.println("size " + lemmaCounts.size());
      return lemmaCounts;
    }
    else return null;
  }
  
  /**
   * Given a file of stories with parse trees, we count all of the tokens and output
   * the counts, one story per line, to a new file.
   * @param dir The directory with our parses file.
   * @param filename The parses filename.
   */
  private void countTokensInFile(String dir, String filename) {
    String inpath = dir + File.separator + filename; 
    String outpath = _outDir + File.separator + filename;
    if( outpath.endsWith(".gz") ) outpath = outpath.substring(0, outpath.length()-3);
    
    // Open the file.
    ProcessedData process = new ProcessedData(inpath, null, null, null);
    
    try {
      // Create the output file.
      System.out.println("Writing counts to " + outpath);
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outpath, false)));

      // Read the documents one by one.
      process.nextStory();
      Collection<String> parseStrings = process.getParseStrings();
      while( parseStrings != null ) {
        if( _duplicates.contains(process.currentStory()) ) {
          // Nothing.
        }
        else {
          System.out.print(process.currentStory());
          IntCounter<String> tokenCounts = countWordsFromParseTrees(TreeOperator.stringsToTrees(parseStrings), _wordnet);
          String stringCounter = ParsesToCounts.counterToString(tokenCounts);
          writer.write(process.currentStory() + "\t");
          writer.write(stringCounter);
          writer.write("\n");
          System.out.println("...wrote");
        }
        // Next story.
        process.nextStory();
        parseStrings = process.getParseStrings();
      }
      writer.close();
    } catch( IOException ex ) { 
      ex.printStackTrace();
      System.exit(-1);
    }
  }
  
  private void countTokensInDirectory() {
    // Loop over each file in the directory.
    for( String dirPath : _dataDirectories ) {
      String[] files = Directory.getFilesSorted(dirPath);
      for( String file : files ) {
        System.out.println("check " + file);
        if( validFilename(file) ) {
          System.out.println(file);
          
          if( Locks.getLock("irprocessdata-" + file) )
            countTokensInFile(dirPath, file);
        }
      }
    }
  }
  
  private boolean validFilename(String path) {
    if( (path.matches(".*nyt.*") || path.matches(".*ap.*")) && !path.endsWith("~") )
      return true;
    else return false;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub
    IRProcessData process = new IRProcessData(args);
    process.countTokensInDirectory();
  }

}

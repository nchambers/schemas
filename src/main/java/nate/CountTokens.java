package nate;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.util.Ling;
import nate.util.Util;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;


/**
 * Count how many tokens are in the Gigaword documents.
 * Loops over the given corpus and outputs token counts for each
 * year to STDOUT.
 * 
 * Also counts individual tokens, and prints them in a new file "token-counts.txt"
 * 
 */
public class CountTokens {
  private String _dataPath = "";
  Counter<String> tokens;
  String outPath = "token-counts.txt";
  
  DocumentPreprocessor dp;
  Options options;

  private String _duplicatesPath = "duplicates";
  private Set<String> _duplicates;


  public CountTokens(String[] args) {
    _dataPath = args[0];
    tokens = new ClassicCounter<String>();
    
    options = new Options();
    //    dp = new DocumentPreprocessor(options.tlpParams.treebankLanguagePack().getTokenizerFactory());

    // Duplicate Gigaword files to ignore
    _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);
  }

  private void sortAndPrint() {
    try {
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outPath)));

      System.out.println("Writing counts to file " + outPath);
      for( String key : Util.sortCounterKeys(tokens) ) {
        writer.println(key + "\t" + tokens.getCount(key));
      }
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
  
  private int countTokens(Vector<String> paragraphs) {
    int count = 0;

    // Paragraphs may be multiple sentences
    for( String fragment : paragraphs ) {
      // Replace underscores (gigaword has underscores in many places commas should be)
      if( fragment.contains(" _ ") ) fragment = fragment.replaceAll(" _ ", " , ");
      // Split sentences
      List<List<HasWord>> list = Ling.getSentencesFromText(fragment);

      // Loop over each sentence
      for( List<HasWord> sentence : list ) {
        int precount = count;
        for( HasWord word : sentence ) {
          if( word.word().matches(".*[a-zA-Z0-9].*") ) {
            //	    System.out.println(word + " *");
            count++;
            
            // Only store token counts for words with just letters.
            if( word.word().matches("[a-zA-Z]+") )
              tokens.incrementCount(word.word().toLowerCase());
          }
          //	  else System.out.println(word);
        }
        //	System.out.println((count-precount) + " " + sentence);
        //	count += sentence.size();
      }
    }
    return count;
  }

  /**
   * @desc Uses the global dataPath directory and reads every .gz file
   * in it, processing each sentence in each document in each file.
   */
  public void processData() {
    Map<String, Integer> yearCounts = new HashMap();
    int numDocs = 0;

    System.out.println("processData()");
    if( _dataPath.length() > 0 ) {
      File dir = new File(_dataPath);
      if( dir.isDirectory() ) {
        System.out.println("is dir()");
        int fid = 0;
        String files[] = dir.list();
        Arrays.sort(files);

        for( String file : files ) {
          System.out.println("file " + file);
          // Only look at *.gz files
          if( !file.startsWith(".") && file.endsWith(".gz") ) {
            String year = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(8,12) : "noyear";
            String month = (file.length() > 15 && file.matches(".*\\d\\d\\d\\d.*")) ? file.substring(12,14) : "nomonth";
            System.out.println("file: " + file);
            System.out.println("year: " + year);

            GigawordHandler giga;
            giga = new GigawordHandler(_dataPath + File.separator + file);

            // Read the documents in this file 
            Vector<String> sentences = giga.nextStory();
            while( sentences != null ) {
              numDocs++;
              System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + 
                  giga.numDocs() + ") " + giga.currentStory);

              // Count tokens in the document.
              int count = countTokens(sentences);
              Util.incrementCount(yearCounts, year, count);

              sentences = giga.nextStory();
            }
            
//            break;
          }
        }
      }
      else System.err.println("Path is not a directory: " + _dataPath);
    }

    // Print total count
    int totalCount = 0;
    for( String year : yearCounts.keySet() ) {
      System.out.println(year + " " + yearCounts.get(year));
      totalCount += yearCounts.get(year);
    }
    System.out.println("Total: " + totalCount);
    
    // Sort and print token counts.
    sortAndPrint();
  }


  public static void main(String[] args) {
    CountTokens count = new CountTokens(args);
    count.processData();
  }
}

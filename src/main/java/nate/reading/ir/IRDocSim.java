package nate.reading.ir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;

import nate.IDFMap;
import nate.reading.ProcessedData;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Locks;
import nate.util.TreeOperator;
import nate.util.WordNet;

/**
 * Input is a file with parse trees by story (for MUC, but could be anything).
 * This code then searches all of gigaword for similar documents based on vector similarity
 * of the files in the input and the files in gigaword.
 *
 * IRDocSim -wordnet <wordnet> -idf <idfs> -input <parses> <giga-token-counts-dir>
 */
public class IRDocSim {
  String _inputPath;
  String _gigaCountsDir;
  double _minCosine = 0.9;
  IDFMap _idfs;
  WordNet _wordnet;
  String _outputDistributed = "/u/natec/scr/irvecdist";
  
  List<Counter<String>> _mucVectors;
  List<IntCounter<String>> _docVectors;
  
  
  public IRDocSim(String[] args) {
    HandleParameters params = new HandleParameters(args);
    _wordnet = new WordNet(params.get("-wordnet"));
    _idfs = new IDFMap(params.get("-idf"));

    _inputPath = params.get("-input");
    System.out.println("Input: " + _inputPath);
    
    // We can have more than one directory, separated by commas.
    _gigaCountsDir = args[args.length-1];
    System.out.println("Giga dir: " + _gigaCountsDir);
    
    // Read the core document (MUC) vectors in.
    loadParsesAsVectors(_inputPath);
  }

  /**
   * Multiply the token counts in the given counter by their IDF scores.
   * @param counter A counter (probably just integer counts)
   * @return A new counter with IDF-multiplied counts.
   */
  private Counter<String> multiplyByIDF(Counter<String> counter) {
    Counter<String> newcounts = new ClassicCounter<String>();
    if( counter == null ) return newcounts;
    
    for( Map.Entry<String,Double> entry : counter.entrySet() ) {
      double idfscore = 10.0;
      if( _idfs.getDocCount(entry.getKey()) > 0 )
        idfscore = _idfs.get(entry.getKey());
      
      double newscore = (double)entry.getValue() * idfscore;
      newcounts.setCount(entry.getKey(), newscore);
    }
    return newcounts;
  }
  
  /**
   * Read MUC data in, a file of parse trees.  Convert it into IntCounters
   * of the tokens in each MUC story.
   */
  private void loadParsesAsVectors(String path) {
    ProcessedData process = new ProcessedData(path, null, null, null);
    process.nextStory();
    Collection<String> parseStrings = process.getParseStrings();
    while( parseStrings != null ) {
      // Count lemmas in parse trees.
      List<Tree> trees = TreeOperator.stringsToTrees(parseStrings);
      IntCounter<String> counter = IRProcessData.countWordsFromParseTrees(trees, _wordnet);
      
      // Multiply by IDF scores.
      Counter<String> tfidfCounter = multiplyByIDF(counter);
      if( _mucVectors == null ) _mucVectors = new ArrayList<Counter<String>>();
      _mucVectors.add(tfidfCounter);
      
      // Next story.
      process.nextStory();
      parseStrings = process.getParseStrings();
    }
    System.out.println("Loaded " + (_mucVectors == null ? 0 : _mucVectors.size()) + " vectors from the input parses file.");
  }
  
  /**
   * Traverses the lines in the given file, each line is an IntCounter representing
   * a single document from Gigaword.  Compare each line to all files in the global
   * MUC input, see if it is close to a MUC document.
   * @param path Path to a file of IntCounters.
   * @return A list of document names that we matched.
   */
  private List<String> findBestDocumentsInFile(String path) {
    List<String> matches = new ArrayList<String>();
    
    BufferedReader in = null;
    try {
      if( path.endsWith(".gz") )
        in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(path))));
      else in = new BufferedReader(new FileReader(path));
    
      String line;
      while( (line = in.readLine()) != null ) {
        // e.g. nyt_eng_199411 kidnap 40 release 23 ...
        int tab = line.indexOf('\t');
        
        IntCounter<String> gigadoc = ParsesToCounts.fromString(line.substring(tab+1));
        Counter<String> gigavec = multiplyByIDF(gigadoc);
        for( Counter<String> mucdoc : _mucVectors ) {
          double cosine = Counters.cosine(gigavec, mucdoc);
          if( cosine > _minCosine ) {
            String docname = line.substring(0,tab);
            System.out.println("Matched " + docname);
            matches.add(docname);
          }
        }
      }
    } catch( Exception ex ) { 
      System.err.println("Error opening " + path);
      ex.printStackTrace();
    }
    
    return matches;
  }
  
  public void search() {
    String dirPath = _gigaCountsDir;
    List<String> alldocmatches = new ArrayList<String>();
    String lastfile = null;
    
    String[] files = Directory.getFilesSorted(dirPath);
    for( String file : files ) {
      System.out.println("check " + file);
      if( IRDocuments.validFilename(file) ) {
        System.out.println(file);

        if( Locks.getLock("irdocsim-" + file) ) {
          List<String> docnames = findBestDocumentsInFile(dirPath + File.separator + file);
          alldocmatches.addAll(docnames);
          lastfile = file;
        }
      }
    }
    
    if( lastfile != null)
      saveDocsToFile(alldocmatches, _outputDistributed + File.separator + IRDocuments.stripGZ(lastfile));
  }
  
  /**
   * Save to disk the globally saved documents that matched frames.
   * @param path File path in which to save the documents.
   */
  private void saveDocsToFile(List<String> names, String path) {
    // Open the output file.
    PrintWriter writer = null;
    try {
      writer = new PrintWriter(new BufferedWriter(new FileWriter(path, false)));
    } catch( IOException ex ) { 
      ex.printStackTrace();
      System.exit(-1);
    }

    // Print the scored documents for each frame.
    for( String name : names ) {
      writer.println(name);
    }
    writer.close();
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) {
    IRDocSim ir = new IRDocSim(args);

    ir.search();
  }
}

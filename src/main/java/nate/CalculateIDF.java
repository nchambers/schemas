package nate;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.TreeOperator;
import nate.util.WordNet;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;


/**
 * This class takes a directory of parse trees and counts all the
 * lemmas of words in leaves to calculate IDF scores for POS-word pairs.
 * This code outputs counts of words with their POS tags, so the 
 * output file has nouns prefixed by "n-" and verbs "v-".  Everything
 * is lemmatized unless the -countall flag is used.
 *
 * The input path can be a single file, or a directory of gigaword files.
 * The code checks and processes appropriately.
 *
 * Output files: tokens.idf and tokens-lemma.idf
 *
 * -countall
 * Don't just count nouns/verbs/adjs, but every single token.
 * This does not lemmatize the words.
 *
 * -firstsentences
 * If the document is shorter than 4 sentences, skip it entirey.
 * Only count tokens in the first 2 sentences of the remaining docs.
 * -- used to judge "importance" for humans
 * 
 * -outdir
 * The directory in which to save the .idf output files.
 *
 */
public class CalculateIDF {
  private String _parsesDir = "";
  private String _outputDir = ".";
  private String _outputPath = "tokens.idf";
  private String _outputLemmaPath = "tokens-lemmas.idf";
  private String _duplicatesPath = "duplicates";
  private Set<String> _duplicates;

  private boolean _firstSentences = false;
  private boolean _ignorepostags = false;
  public WordNet _wordnet;
  private TreeFactory _treeFactory;

  // ugly globals...
  private Map<String,Boolean> _seen = new HashMap<String, Boolean>(100);
  private Map<String,Boolean> _seenLemmas = new HashMap<String, Boolean>(100);

  public IDFMap _idf;
  public IDFMap _idfLemmas;

  public CalculateIDF(String[] args) {
    init();
    HandleParameters params = new HandleParameters(args);

    // Initialize WordNet
    if( params.hasFlag("-wordnet") ) _wordnet = new WordNet(params.get("-wordnet"));
    else if( WordNet.findWordnetPath() != null )
      _wordnet = new WordNet(WordNet.findWordnetPath());
    
    // Check for count-all-tags flag
    if( params.hasFlag("-countall") ) _ignorepostags = true;
    System.out.println("countall=" + _ignorepostags);

    // Output directory.
    if( params.hasFlag("-outdir") ) _outputDir = params.get("-outdir");
    System.out.println("outputDir=" + _outputDir);
    if( !Directory.fileExists(_outputDir) ) {
      System.out.println("ERROR: output dir does not exist.");
      System.exit(1);      
    }
    
    // If set, only count words in the first 2 sentences.
    if( params.hasFlag("-firstsentences") ) _firstSentences = true;
    System.out.println("firstsentences=" + _firstSentences);

    // Duplicate Gigaword files to ignore
    if( Directory.fileExists(_duplicatesPath))
      _duplicates = GigawordDuplicates.fromFile(_duplicatesPath);
    _parsesDir = args[args.length - 1];
  }

  /**
   * This constructor is meant to be called not from the main() function, but some
   * other class that wants to dynamically count tokens.
   * @param wordnet
   */
  public CalculateIDF(WordNet wordnet) {
    init();
    _wordnet = wordnet;
  }
  
  private void init() {
    // Setup the IDF maps.
    _idf = new IDFMap(75000);
    _idfLemmas = new IDFMap(75000);

    _treeFactory = new LabeledScoredTreeFactory();
  }

  /**
   * Return a single character to represent verb, noun, or adjective
   */
  public static char normalizePOS(String pos) {
    if( pos == null ) return 'o';
    else if( pos.startsWith("VB") ) return 'v';
    else if( pos.startsWith("NN") ) return 'n';
    else if( pos.startsWith("J") ) return 'j';
    else if( pos.startsWith("PRP") ) return 'r';
    else return 'o'; // other
  }

  public static String createKey(String token, char pos) {
    return pos + "-" + token;
  }

  public static String createKey(String token, int pos) {
    if( pos == WordEvent.VERB ) return createKey(token, 'v');
    else if( pos == WordEvent.NOUN ) return createKey(token, 'n');
    else if( pos == WordEvent.JJ ) return createKey(token, 'j');
    else return createKey(token, 'o');
  }
  
  public static Pair splitKey(String key) {
    char ch = key.charAt(0);
    return new Pair(ch, key.substring(2));
  }

  /**
   * Recurses over a tree and adds an IDF counts leaves
   * Leaves must be Nouns, Verbs or Adjectives to be counted
   */
  public void calculateTreeIDF(Tree tree, Tree fullTree) {
    // if tree is a leaf
    if( tree.isPreTerminal() ) {
      String wordString = tree.firstChild().value().toLowerCase();

      // If we are counting every single token
      if( _ignorepostags ) {
        // Don't count numerals or weird starting punctuation tokens
        if( wordString.matches("[a-zA-Z].*") && !wordString.matches(".*\\d.*") )  {
          // Count the documents the word appears in, for IDF score
          if( !_seen.containsKey(wordString) ) {
            _seen.put(wordString, true);
            // add the count of documents
            _idf.increaseDocCount(wordString);
          }
          _idf.increaseTermFrequency(wordString);
        }
      } 

      // Else we are lemmatizing and only counting Verbs, Nouns, and Adjectives.
      else {
        char normPostag = normalizePOS(tree.label().value().toString());

        // if a verb, look for a particle
        if( normPostag == 'v' ) {
//          System.out.println(wordString + " tree " + tree.parent(fullTree));
          String particle = BasicEventAnalyzer.findParticleInTree(tree.parent(fullTree));
//          System.out.println(wordString + " particle " + particle);
          if( particle != null ) wordString += "_" + particle.toLowerCase();
        }

        // Verbs, Nouns, Adjectives
        if( normPostag == 'v' || normPostag == 'n' || normPostag == 'j' || normPostag == 'r' ) {
          String key = createKey(wordString, normPostag);

          // Don't count numerals or weird starting punctuation tokens
          if( wordString.matches("[a-zA-Z].*") && !wordString.matches(".*\\d.*") )  {
            // Count the documents the word appears in, for IDF score
            if( !_seen.containsKey(key) ) {
              _seen.put(key, true);
              // add the count of documents
              _idf.increaseDocCount(key);
            }
            _idf.increaseTermFrequency(key);

            // LEMMA counts
            String lemmaString = null;
            if( normPostag == 'v' ) lemmaString = _wordnet.verbToLemma(wordString);
            else if( normPostag == 'n' ) lemmaString = _wordnet.nounToLemma(wordString);
            else if( normPostag == 'j' ) lemmaString = _wordnet.adjectiveToLemma(wordString);
            else if( normPostag == 'r' ) lemmaString = _wordnet.nounToLemma(wordString);
            if( lemmaString == null ) lemmaString = wordString;
            key = createKey(lemmaString, normPostag);
            if( !_seenLemmas.containsKey(key) ) {
              _seenLemmas.put(key, true);
              _idfLemmas.increaseDocCount(key);
            }
            _idfLemmas.increaseTermFrequency(key);
          }
        }
      }
    }

    // else recurse on the tree
    else {
      List<Tree> children = tree.getChildrenAsList();
      for( Tree child : children ) 
        calculateTreeIDF(child, fullTree);
    }
  }



  /**
   * @desc Process chunks (sentences) of text for event information
   * @param IDFonly Set to true if you want to update IDF counts and not
   * track verb pairs.  False does not update IDFs and only tracks verb pairs.
   */
  private void calculateIDF( Collection<String> parses ) {
    int sid = 0;
    int numSentences = parses.size();

    // clear the global seen list for this new story
    _seen.clear();
    _seenLemmas.clear();

    // Loop over each parse tree
    for( String parse : parses ) {

      // Count all, or only the first two sentences.
      if( !_firstSentences || (numSentences > 4 && sid < 2) ) {

        // Count the leaves	
        Tree tree = TreeOperator.stringToTree(parse, _treeFactory);
        if( tree != null )
          calculateTreeIDF(tree, tree);
      }
      sid++;
    }
  }


  /**
   * Checks if the input path is a directory or a file, and calls the appropriate
   * function to process it as such.
   */
  public void count() {
    if( _parsesDir.length() > 0 ) {
      File dir = new File(_parsesDir);

      // If the path is a directory.
      if( dir.isDirectory() )
        countDir();
      // If the path is a single file.
      else
        countSingleFile();
    }
    else {
      System.out.println("No file given");
      System.exit(-1);
    }
  }


  /**
   * This function loops over .parse files in a directory and counts 
   * the words for IDF scores.
   */
  public void countDir() {
    System.out.println("Calculating over a directory of files.");

    File dir = new File(_parsesDir);
    if( dir.isDirectory() ) {
      String files[] = dir.list();
      Arrays.sort(files);

      for( String file : files ) {
        // Only look at *.gz files
        if( !file.startsWith(".") && file.contains("parse") &&
            (file.endsWith(".gz") || file.endsWith(".parse")) ) {
          String year = file.substring(8,12);
          String month = "";
          if( file.length() > 14 ) file.substring(12,14);
          System.out.println("file: " + file);

          // Open the file
          GigawordHandler giga = new GigawordProcessed(_parsesDir + File.separator + file);
          Vector<String> sentences = giga.nextStory();

          // Loop over each story
          while( sentences != null ) {
            if( _duplicates != null && _duplicates.contains(giga.currentStory()) )
              System.out.println("Duplicate " + giga.currentStory());
            else {
              _idf.increaseDocCount();
              _idfLemmas.increaseDocCount();

              // Count the words in this story	      
              calculateIDF( sentences );
            }
            sentences = giga.nextStory();
          }

          // Save IDFs to file!
          if( month.equals("12") ) {
            _idf.calculateIDF();
            _idf.saveToFile(_outputDir + File.separatorChar + _outputPath + "-" + year);
            if( !_ignorepostags ) {
              _idfLemmas.calculateIDF();
              _idfLemmas.saveToFile(_outputDir + File.separatorChar + _outputLemmaPath + "-" + year);
            }
          }
        }
      }
      
      _idf.calculateIDF();
      _idf.saveToFile(_outputDir + File.separatorChar + _outputPath + "-all");
      if( !_ignorepostags ) {
        _idfLemmas.calculateIDF();
        _idfLemmas.saveToFile(_outputDir + File.separatorChar + _outputLemmaPath + "-all");
      }
    }
  }


  /**
   * This function loops over a single .parse file and counts 
   * the words for IDF scores.
   */
  public void countSingleFile() {
    System.out.println("Calculating for a single file.");

    if( _parsesDir.length() > 0 ) {
      String file = _parsesDir;
      System.out.println("file: " + file);

      // Open the file.
      GigawordHandler giga = new GigawordProcessed(file);
      Vector<String> sentences = giga.nextStory();

      // Loop over each story.
      while( sentences != null ) {
        if( _duplicates != null && _duplicates.contains(giga.currentStory()) )
          System.out.println("Duplicate " + giga.currentStory());
        else {
          _idf.increaseDocCount();
          _idfLemmas.increaseDocCount();

          // Count the words in this story.
          calculateIDF( sentences );
        }
        sentences = giga.nextStory();
        //        System.out.println(giga.currentStory() + " numsent " + ((sentences == null) ? 0 : sentences.size()));
      }

      // Save to file.
      _idf.calculateIDF();
      _idf.saveToFile(_outputDir + File.separator + _outputPath);
      if( !_ignorepostags ) {
        _idfLemmas.calculateIDF();
        _idfLemmas.saveToFile(_outputDir + File.separator + _outputLemmaPath);
      }
    }
  }

  /**
   * Increments the word counts for a single story, given the list
   * of parse trees in string form from it.
   * This is intended to incrementally build an IDFMap online.
   * @param parseStrings String form of parse trees.
   */
  public void countStory(Collection<String> parseStrings) {
    System.out.println("Calculating for a single story: numsentences = " + parseStrings.size());

    _idf.increaseDocCount();
    _idfLemmas.increaseDocCount();

    // Count the words in this story.
    calculateIDF(parseStrings);
  }
  
  public void calculateIDF() {
    if( _idf != null ) _idf.calculateIDF();
    if( _idfLemmas != null ) _idfLemmas.calculateIDF();
  }


  public static void main(String[] args) {
      if( args.length < 1 ) {
	  System.out.println("CalculateIDF [-outdir <dir>] <parse-file|dir>");
	  System.exit(-1);
      }

    CalculateIDF idf = new CalculateIDF(args);
    idf.count();
  }
}
package nate;

import java.io.File;
import java.util.List;
import java.util.Vector;

import nate.util.HandleParameters;
import nate.util.Ling;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;


/**
 * Creates two files as output: parses and dependencies
 *
 * -output
 * Directory to create parsed and dependency files.
 *
 * -input GIGA|ENVIRO|MUC
 * The type of text we are processing, Gigaword or Environment.
 */
public class TextToSentences {
  public int MAX_SENTENCE_LENGTH = 400;
  String _dataPath = "";
  String _outputDir = ".";
  String _serializedGrammar = "";
  Options options;
  LexicalizedParser parser;
  GrammaticalStructureFactory gsf;
  // One file name to continue parsing.
  // Usually the previous run was interrupted.
  String _continueFile = null; 

  public static final int GIGAWORD = 0;
  public static final int ENVIRO = 1;
  public static final int MUC = 2;
  private int _docType = GIGAWORD;


  public TextToSentences(String[] args) {
    handleParameters(args);
    initLexResources();
  }

  private void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-grammar") ) {
      _serializedGrammar = params.get("-grammar");
      System.out.println("Grammar " + _serializedGrammar);
    }

    if( params.hasFlag("-input") ) {
      _docType = docTypeToInt(params.get("-input"));
    }

    _dataPath = args[args.length - 1];
  }

  public static int docTypeToInt(String str) {
    str = str.toLowerCase();
    if( str.startsWith("giga") )
      return GIGAWORD;
    else if( str.startsWith("env") )
      return ENVIRO;
    else if( str.startsWith("muc") )
      return MUC;
    else {
      System.out.println("Unknown text input type: " + str);
      System.exit(1);
    }
    return -1;
  }

  private void initLexResources() {
    options = new Options();

    try {
      // Parser
      parser = Ling.createParser(_serializedGrammar);
    } catch( Exception ex ) { ex.printStackTrace(); }

    // Dependency tree info
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    gsf = tlp.grammaticalStructureFactory();
  }


  /**
   * @desc Parses input sentences and prints the parses to the given doc.
   * @param paragraphs Vector of strings of sentences
   * @param doc The current document we're printing to.
   * @param depdoc The document of dependencies that we're printing to.
   */
  private void analyzeSentences( Vector<String> paragraphs ) {
    int sid = 0;

    // Paragraphs may be multiple sentences
    for( String fragment : paragraphs ) {
      // Replace underscores (gigaword has underscores in many places commas should be)
      if( fragment.contains(" _ ") ) fragment = fragment.replaceAll(" _ ", " , ");

      // Loop over each sentence
      for( List<HasWord> sentence : Ling.getSentencesFromText(fragment) ) {
        System.out.println(sentence.size() + ": **" + sentence + "**");

//        if( sentence.size() > MAX_SENTENCE_LENGTH )
//          System.out.println("Sentence far too long: " + sentence.size());
//        else if( !parser.parse(sentence) )
//          System.out.println("Failed to parse: " + sentence);
//        else {
//          Tree ansTree = parser.getBestParse();
//
//          // Save to InfoFile
//          // Build a StringWriter, print the tree to it, then save the string
//          StringWriter treeStrWriter = new StringWriter();
//          TreePrint tp = new TreePrint("penn");
//          tp.printTree(ansTree, new PrintWriter(treeStrWriter,true));
//
//          
//
//          sid++;
//        }

      }
    }
  }

  /**
   * @desc Parse each sentence and save to another file
   */
  public void parseData() {
    int numDocs = 0;
    int numFiles = 0;

    if( _dataPath.length() > 0 ) {

      File dir = new File(_dataPath);
      if( dir.isDirectory() ) {
        String files[] = dir.list();

        for( String file : files ) {
          if( validFilename(file) ) {
            //	  if( file.contains("_2005") ) {

            String parseFile = _outputDir + File.separator + file + ".parse";
            String depsFile = _outputDir + File.separator + file + ".deps";

            //	      if( GigaDoc.fileExists(parseFile) || similarFileExists(file, _outputDir) ) {
            if( GigaDoc.fileExists(parseFile) ) {
              System.out.println("File exists: " + parseFile);
            } else {
              System.out.println("file: " + file);

              // Open the text file to parse.
              DocumentHandler giga = null;
              if( _docType == GIGAWORD )
                giga = new GigawordHandler(_dataPath + File.separator + file);
              else if( _docType == ENVIRO )
                giga = new EnviroHandler(_dataPath + File.separator + file);
              else if( _docType == MUC )
                giga = new MUCHandler(_dataPath + File.separator + file);
              //		giga = new GigawordFilteredText(_dataPath + File.separator + file,
              //						filterList);

              // Read the documents in the text file. 
              Vector<String> sentences = giga.nextStory();
              int storyID = 0;
              while( sentences != null ) {
                numDocs++;
                System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") " + giga.currentStory());

                analyzeSentences(sentences);

                sentences = giga.nextStory();
                storyID++;
              }

            }
            numFiles++;
          }
        }
      }
      else System.err.println("Path is not a directory: " + _dataPath);
    }
  }

  /**
   * @param file A filename, not the complete path.
   * @returns True if the given filename matches the requirements of whatever type
   *          of documents we are currently processing.  False otherwise.
   */
  private boolean validFilename(String file) {
    if( !file.startsWith(".") ) {
      if( _docType == GIGAWORD ) {
        if( file.endsWith(".gz") || file.endsWith(".txt") ) return true;
      }
      else if( _docType == ENVIRO ) {
        if( file.endsWith(".txt") ) return true;
      }
      else if( _docType == MUC ) {
        if( file.startsWith("dev-muc") ) return true;
      }
    }
    return false;
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      TextToSentences parser = new TextToSentences(args);
      parser.parseData();
    }
  }
}

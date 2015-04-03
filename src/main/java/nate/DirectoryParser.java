package nate;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.Util;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * Creates two files as output: parses and dependencies
 *
 * -output
 * Directory to create parsed and dependency files.
 *
 * -continue
 * Part of a file name of a file to start parsing again.
 * Usually it was interrupted and we need to continue.
 *
 * -input GIGA|ENVIRO
 * The type of text we are processing, Gigaword or Environment.
 */
public class DirectoryParser {
  public int MAX_SENTENCE_LENGTH = 400;
  String _dataPath = "";
  String _outputDir = ".";
  String _serializedGrammar;
  Options options;
  LexicalizedParser parser;
  GrammaticalStructureFactory gsf;
  // One file name to continue parsing.
  // Usually the previous run was interrupted.
  String _continueFile = null; 

  public static final int GIGAWORD = 0;
  public static final int ENVIRO = 1;
  public static final int MUC = 2;
  public static final int TEXT = 3;
  
  private int _docType = GIGAWORD;


  public DirectoryParser(String[] args) {
    if( args.length < 2 ) {
      System.out.println("DirectoryParser [-output <dir>] -grammar <path> -input giga|muc <text-directory>");
      System.exit(-1);
    }
    handleParameters(args);
    initLexResources();
  }

  private void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-grammar") )
      _serializedGrammar = params.get("-grammar");
    else _serializedGrammar = findGrammar();
    
    if( params.hasFlag("-continue") ) {
      _continueFile = params.get("-continue");
      System.out.println("Continuing on file " + _continueFile);
    }
    if( params.hasFlag("-output") ) {
      _outputDir = params.get("-output");
    }
    if( params.hasFlag("-input") ) {
      _docType = docTypeToInt(params.get("-input"));
    }

    System.out.println("Grammar " + _serializedGrammar);

    _dataPath = args[args.length - 1];
  }

  private String findGrammar() {
    if( Directory.fileExists("/home/nchamber/code/resources/englishPCFG.ser.gz") )
      return "/home/nchamber/code/resources/englishPCFG.ser.gz";
    if( Directory.fileExists("/Users/mitts/Projects/stanford/stanford-parser/englishPCFG.ser.gz") )
      return "/Users/mitts/Projects/stanford/stanford-parser/englishPCFG.ser.gz";
    if( Directory.fileExists("/home/sammy/code/resources/englishPCFG.ser.gz") )
      return "/home/sammy/code/resources/englishPCFG.ser.gz";
    if( Directory.fileExists("C:\\cygwin\\home\\sammy\\code\\resources\\englishPCFG.ser.gz") )
      return "C:\\cygwin\\home\\sammy\\code\\resources\\englishPCFG.ser.gz";
    if( Directory.fileExists("englishPCFG.ser.gz") )
      return "englishPCFG.ser.gz";
    else System.out.println("WARNING (DirectoryParser): grammar englishPCFG.ser.gz not found!");
    return null;
  }
  
  public static int docTypeToInt(String str) {
    str = str.toLowerCase();
    if( str.startsWith("giga") )
      return GIGAWORD;
    else if( str.startsWith("env") )
      return ENVIRO;
    else if( str.startsWith("muc") )
      return MUC;
    else if( str.equals("text") )
      return TEXT;
    else {
      System.out.println("Unknown text input type: " + str);
      System.exit(1);
    }
    return -1;
  }

  private void initLexResources() {
    try {
      options = new Options();
      options.testOptions.verbose = true;
      // Parser
      parser = LexicalizedParser.loadModel(_serializedGrammar);
      //parser = new LexicalizedParser(_serializedGrammar, options);
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
  private void analyzeSentences( Vector<String> paragraphs, GigaDoc doc, GigaDoc depdoc) {
    int sid = 0;

    // Paragraphs may be multiple sentences
    for( String fragment : paragraphs ) {
//      System.out.println("* " + fragment);
      // Replace underscores (gigaword has underscores in many places commas should be)
      if( fragment.contains(" _ ") ) fragment = fragment.replaceAll(" _ ", " , ");

      // Loop over each sentence
      for( List<HasWord> sentence : Ling.getSentencesFromText(fragment) ) {
        Tree ansTree;
        
//        System.out.println("Calling parse on: " + sentence);

        //	System.out.println(sentence.size() + ": **" + sentence + "**");
        if( sentence.size() > MAX_SENTENCE_LENGTH )
          System.out.println("Sentence far too long: " + sentence.size());
        else if( (ansTree = parser.parseTree(sentence)) == null )
          System.out.println("Failed to parse: " + sentence);
        else {
          // Build a StringWriter, print the tree to it, then save the string
          StringWriter treeStrWriter = new StringWriter();
          TreePrint tp = new TreePrint("penn");
          tp.printTree(ansTree, new PrintWriter(treeStrWriter,true));
          doc.addParse(treeStrWriter.toString());

          // Create the dependency tree - CAUTION: DESTRUCTIVE to parse tree
          try {
            GrammaticalStructure gs = gsf.newGrammaticalStructure(ansTree);
            //	    Collection<TypedDependency> deps = gs.typedDependenciesCollapsed();
            Collection<TypedDependency> deps = gs.typedDependenciesCCprocessed(true);
            depdoc.addDependencies(deps, sid);
          } catch( Exception ex ) { 
            ex.printStackTrace();
            System.out.println("WARNING: dependency tree creation failed...adding null deps");
            depdoc.addDependencies(null, sid);
          }
          sid++;
        }
      }
    }
  }


  private void continueParsing(String files[], String continueFile) {

    for( String file : files ) {
      if( file.contains(continueFile) ) {

        String parseFile = _outputDir + File.separator + file + ".parse";
        String depsFile = _outputDir + File.separator + file + ".deps";

        GigaDocReader parsed = new GigaDocReader(parseFile);
        int stoppedNum = parsed.largestStoryID(parseFile);
        String stoppedStory = parsed.lastStoryName(parseFile);

        System.out.println("Recovering at " + stoppedStory);

        // If we couldn't find any parses in this file
        if( stoppedNum == 0 ) {
          System.err.println("No parses in the file " + file);
        } else {
          parsed.close();

          // Open the gigaword file
          DocumentHandler giga;
          giga = new GigawordHandler(_dataPath + File.separator + file);
          //	  giga = new GigawordFilteredText(_dataPath + File.separator + file,
          //					    filterList);

          // Find the document at which we last stopped
          giga.nextStory();
          while( giga.currentStory() != null &&
              !giga.currentStory().equals(stoppedStory) ) {
            giga.nextStory();
            System.out.println("checking " + giga.currentStory());
          }
          // Now read the next story
          Vector<String> sentences = giga.nextStory();	  

          GigaDoc doc = null;
          GigaDoc depdoc = null;
          try {
            // Create the parse output file.
            doc = new GigaDoc(parseFile, true);
            // Create the dependency output file.
            depdoc = new GigaDoc(depsFile, true);
          } catch( Exception ex ) {
            System.out.println("Skipping to next file...");
            continue;
          }

          // Process the remaining sentences
          int storyID = stoppedNum + 1;
          while( sentences != null ) {
            System.out.println(giga.currentDoc() + "/" + giga.numDocs() + " " + giga.currentStory());

            doc.openStory(giga.currentStory(), storyID);
            depdoc.openStory(giga.currentStory(), storyID);
            analyzeSentences(sentences, doc, depdoc);
            doc.closeStory();
            depdoc.closeStory();

            sentences = giga.nextStory();
            storyID++;
          }

          doc.closeDoc();
          depdoc.closeDoc();
        }
      }
    }
  }

  /**
   * @desc Parse each sentence and save to another file
   */
  public void parseData() {
//    String line = "I am a happy man.";
//    
//    List<Word> allWords = (PTBTokenizer.newPTBTokenizer(new StringReader(line))).tokenize();
//    ArrayList<Word> sentence = new ArrayList<Word>(allWords);
//    System.out.println("allWords: " + allWords);
//    System.out.println("sentence: " + sentence);
//    parser.getOp().testOptions.maxLength = 80;
//    parser.getOp().testOptions.verbose = true;
//    Tree t = parser.parseTree(sentence);
//    System.out.println("treE: " + t);
//
//    
//    String[] options = {"-retainNPTmpSubcategories"};
//    LexicalizedParser lp = LexicalizedParser.loadModel("/home/nchamber/code/resources/englishPCFG.ser.gz");
////    LexicalizedParser lp = new LexicalizedParser("/home/nchamber/code/resources/englishPCFG.ser.gz", options);
//    PTBTokenizer<Word> ptb = PTBTokenizer.newPTBTokenizer(new StringReader(line));
//    List<Word> words = ptb.tokenize();
//    Tree parseTree = lp.parseTree(words);
//    System.out.println("tree: " + parseTree);
//    
//    System.exit(-1);
    
    int numDocs = 0;
    int numFiles = 0;

    if( _dataPath.length() > 0 ) {

      File dir = new File(_dataPath);
      if( dir.isDirectory() ) {
        String files[] = dir.list();

        // Special handling of a continuation parse.
        if( _continueFile != null ) {
          continueParsing(files, _continueFile);
          return;
        }

        for( String file : files ) {
          System.out.println("file: " + file);
          if( validFilename(file) ) {
            //	  if( file.contains("_2005") ) {

            String parseFile = _outputDir + File.separator + file + ".parse";
            String depsFile = _outputDir + File.separator + file + ".deps";

            Directory.createDirectory(_outputDir);
            
            if( GigaDoc.fileExists(parseFile) ) {
              System.out.println("File exists: " + parseFile);
            } else {
              System.out.println("file: " + file);

              GigaDoc doc = null;
              GigaDoc depdoc = null;

              try {
                // Create the parse output file.
                doc = new GigaDoc(parseFile);
                // Create the dependency output file.
                depdoc = new GigaDoc(depsFile);
              } catch( Exception ex ) {
                System.out.println("Skipping to next file...");
                continue;
              }

              // Open the text file to parse.
              DocumentHandler giga = null;
              if( _docType == GIGAWORD )
                giga = new GigawordHandler(_dataPath + File.separator + file);
              else if( _docType == ENVIRO )
                giga = new EnviroHandler(_dataPath + File.separator + file);
              else if( _docType == MUC )
                giga = new MUCHandler(_dataPath + File.separator + file);
              else if( _docType == TEXT )
                giga = new TextHandler(_dataPath + File.separator + file);
              //						filterList);

              // Read the documents in the text file.               else if( _docType == MUC )
//              giga = new MUCHandler(_dataPath + File.separator + file);
              Vector<String> sentences = giga.nextStory();
              int storyID = 0;
              while( sentences != null ) {
                numDocs++;
                System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") " + giga.currentStory());
                if( numDocs % 100 == 0 ) Util.reportMemory();

                //		  for( String sentence : sentences ) 
                //		    System.out.println("**" + sentence + "**");

                doc.openStory(giga.currentStory(), storyID);
                depdoc.openStory(giga.currentStory(), storyID);
                analyzeSentences(sentences, doc, depdoc);
                doc.closeStory();
                depdoc.closeStory();


                sentences = giga.nextStory();
                storyID++;
              }

              doc.closeDoc();
              depdoc.closeDoc();
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
        if( file.contains("-muc") ) return true;
      }
      else if( _docType == TEXT ) {
        if( file.contains("txt") ) return true;
      }
    }
    return false;
  }


  public static void main(String[] args) {
    DirectoryParser parser = new DirectoryParser(args);
    parser.parseData();
  }
}

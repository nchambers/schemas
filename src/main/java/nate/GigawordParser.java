package nate;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import nate.util.Ling;
import nate.util.Util;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * **DEPRECATED**
 * Use DirectoryParser.java instead!
 *
 * Creates two files as output: parses and dependencies
 *
 * -output
 * Directory to create parsed and dependency files.
 *
 * -continue
 * Part of a file name of a file to start parsing again.
 * Usually it was interrupted and we need to continue.
 *
 */
public class GigawordParser {
  public int MAX_SENTENCE_LENGTH = 400;
  String dataPath = "";
  String outputDir = ".";
  String filterList = null;
  String serializedGrammar = "";
  Options options;
  LexicalizedParser parser;
  GrammaticalStructureFactory gsf;
  // One file name to continue parsing.
  // Usually the previous run was interrupted.
  String continueFile = null; 


  GigawordParser(String[] args) { 
    handleParameters(args);
    initLexResources();
  }

  private void handleParameters(String[] args) {
    int i = 0;
    while( i < args.length ) {
      // specifies the location of the parser grammar
      if( args[i].equalsIgnoreCase("-grammar") && args.length > i+1 ) {
        serializedGrammar = args[i+1];
        System.out.println("Grammar " + serializedGrammar);
        i++;
      }
      else if( args[i].equalsIgnoreCase("-filter") && args.length > i+1 ) {
        filterList = args[i+1];
        System.out.println("Filter");
        i++;
      }
      else if( args[i].equalsIgnoreCase("-continue") && args.length > i+1 ) {
        continueFile = args[i+1];
        System.out.println("Continuing on file " + continueFile);
        i++;
      }
      else if( args[i].equalsIgnoreCase("-output") && args.length > i+1 ) {
        outputDir = args[i+1];
        i++;
      }
      i++;
    }

    dataPath = args[args.length - 1];
  }

  private void initLexResources() {
    options = new Options();

    try {
      // Parser
      parser = Ling.createParser(serializedGrammar);
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
  private void analyzeSentences( Vector<String> paragraphs, GigaDoc doc, 
      GigaDoc depdoc) {
    int sid = 0;

    // Paragraphs may be multiple sentences
    for( String fragment : paragraphs ) {
      // Replace underscores (gigaword has underscores in many places commas should be)
      if( fragment.contains(" _ ") ) fragment = fragment.replaceAll(" _ ", " , ");
      // Split sentences
      List<List<HasWord>> list = Ling.getSentencesFromText(fragment);

//      System.out.println("From fragment: " + fragment);
//      for( List sent : list )
//        System.out.println("  -> " + sent);
      
      // Loop over each sentence
      for( List<HasWord> sentence : list ) {
        Tree ansTree;
        
        //        System.out.println(sentence.size() + ": " + sentence);
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

        String parseFile = outputDir + File.separator + file + ".parse";
        String depsFile = outputDir + File.separator + file + ".deps";

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
          GigawordHandler giga;
          if( filterList == null )
            giga = new GigawordHandler(dataPath + File.separator + file);
          else 
            giga = new GigawordFilteredText(dataPath + File.separator + file,
                filterList);

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
            doc = new GigaDoc(parseFile);
            // Create the dependency output file.
            depdoc = new GigaDoc(depsFile);
          } catch( Exception ex ) {
            System.out.println("Skipping to next file...");
            continue;
          }

          // Process the remaining sentences
          int storyID = stoppedNum + 1;
          while( sentences != null ) {
            System.out.println(giga.currentDoc() + "/" + giga.numDocs() + " " + giga.currentStory);

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

  private boolean similarFileExists(String file, String dirPath) {
    File dir = new File(dirPath);
    if( dir.isDirectory() ) {
      String files[] = dir.list();

      for( String f : files ) {
        if( f.contains(file) || file.contains(f) )
          return true;
      }
    }
    return false;
  }

  /**
   * @desc Parse each sentence and save to another file
   */
  public void parseData() {
    int numDocs = 0;
    int numFiles = 0;

    if( dataPath.length() > 0 ) {

      File dir = new File(dataPath);
      if( dir.isDirectory() ) {
        String files[] = dir.list();

        // Special handling of a continuation parse.
        if( continueFile != null ) {
          continueParsing(files, continueFile);
          return;
        }

        for( String file : files ) {
          // Only look at *.gz files
          if( !file.startsWith(".") && file.endsWith(".gz") ) {
            //	  if( file.contains("_2005") ) {

            String parseFile = outputDir + File.separator + file + ".parse";
            String depsFile = outputDir + File.separator + file + ".deps";

            if( GigaDoc.fileExists(parseFile) || similarFileExists(file, outputDir) ) {
              System.out.println("File exists: " + parseFile);
            } else {
              System.out.println("file: " + file);

              GigawordHandler giga;
              if( filterList == null )
                giga = new GigawordHandler(dataPath + File.separator + file);
              else 
                giga = new GigawordFilteredText(dataPath + File.separator + file,
                    filterList);

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

              // Read the documents in this file 
              Vector<String> sentences = giga.nextStory();
              int storyID = 0;
              while( sentences != null ) {
                numDocs++;
                System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") " + giga.currentStory);
                if( numDocs % 100 == 0 ) Util.reportMemory();

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
      else System.err.println("Path is not a directory: " + dataPath);
    }
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      GigawordParser parser = new GigawordParser(args);
      parser.parseData();
    }
  }
}

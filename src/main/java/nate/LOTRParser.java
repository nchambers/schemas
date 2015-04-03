package nate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

import nate.util.Ling;
import nate.util.Util;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.process.DocumentPreprocessor;
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
 */
public class LOTRParser {
  public int MAX_SENTENCE_LENGTH = 400;
  String dataPath = "";
  String outputDir = ".";
  String serializedGrammar = "";
  Options options;
  DocumentPreprocessor dp;
  LexicalizedParser parser;
  GrammaticalStructureFactory gsf;


  LOTRParser(String[] args) { 
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
    //    dp = new DocumentPreprocessor(options.tlpParams.treebankLanguagePack().getTokenizerFactory());

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
  private void analyzeSentences( List<List<HasWord>> sentences, GigaDoc doc, 
      GigaDoc depdoc) {
    int sid = 0;

    // Loop over each sentence
    for( List<HasWord> sentence : sentences ) {
      Tree ansTree;

      if( sid % 5 == 0 ) { System.out.print("."); }
      if( sid % 50 == 0 ) { System.out.println("\n" + sentence); }

      //      System.out.println(sentence.size() + ": " + sentence);
      if( sentence.size() > MAX_SENTENCE_LENGTH )
        System.out.println("Sentence far too long: " + sentence.size());
      else if( (ansTree = parser.parseTree(sentence)) == null )
        System.out.println("Failed to parse: " + sentence);
      else {
        // Save to InfoFile
        // Build a StringWriter, print the tree to it, then save the string
        StringWriter treeStrWriter = new StringWriter();
        TreePrint tp = new TreePrint("penn");
        tp.printTree(ansTree, new PrintWriter(treeStrWriter,true));

        //	  System.out.println(sentence);
        //	  System.out.println(treeStrWriter);
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


  /**
   * @desc Parse each sentence and save to another file
   */
  public void parseData() {
    int chapterID = 0;

    if( dataPath.length() > 0 ) {

      File dir = new File(dataPath);
      if( dir.isDirectory() ) {
        String files[] = dir.list();

        for( String file : files ) {
          // Only look at *.gz files
          if( !file.startsWith(".") && file.endsWith(".txt") ) {
            String parseFile = outputDir + File.separator + file + ".parse";
            String depsFile = outputDir + File.separator + file + ".deps";

            if( GigaDoc.fileExists(parseFile) ) {
              System.out.println("File exists: " + parseFile);
            } else {
              System.out.println("file: " + file);

              GigaDoc doc = null;
              GigaDoc depdoc = null;
              try {
                // Create the parse output file
                doc = new GigaDoc(parseFile);
                // Create the dependency output file
                depdoc = new GigaDoc(depsFile);
              } catch( Exception ex ) {
                System.out.println("Skipping to next file...");
                continue;
              }

              // Read in all the text
              System.out.println("Reading in text...");
              String line, text="", chapter = null;
              try {
                BufferedReader in = new BufferedReader(new FileReader(dataPath + File.separator + file));
                while( (line = in.readLine()) != null ) { 

                  // Break at chapter boundaries
                  if( line.startsWith("Chapter") || line.startsWith("*") ) {
                    if( text.length() > 0 ) {

                      // Split at sentence boundaries
                      System.out.println("Splitting on boundaries...");
                      List<List<HasWord>> list = Ling.getSentencesFromText(text);

                      System.out.println("Parsing " + chapter + "...");
                      doc.openStory(chapter, chapterID);
                      depdoc.openStory(chapter, chapterID);
                      analyzeSentences(list, doc, depdoc);
                      doc.closeStory(); depdoc.closeStory();

                      text = "";
                      chapterID++;
                    }

                    chapter = line;
                    Util.reportMemory();
                    System.out.println("Reading chapter: " + chapter);
                  } 
                  // append the text
                  else text += " " + line; 

                }
              } catch( Exception ex ) { ex.printStackTrace(); }

              // Parse the sentences
              System.out.println("Parsing...");
              doc.closeDoc(); depdoc.closeDoc();
            }
          }
        }
      }
    }
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      LOTRParser parser = new LOTRParser(args);
      parser.parseData();
    }
  }
}

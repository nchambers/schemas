package nate;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Locks;
import nate.util.TreeOperator;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;


/**
 * This class can run two coreference systems: OpenNLP and Stanford's.
 * Creates one output file (.events) of events and coref mentions.
 *
 * ParsedToCoref -type <open|stanford> [-opennlp <dir>] -output <dir>
 *
 * 
 * -type [open|stanford]
 * Chooses the coref system to run.
 *
 * -opennlp <dir>
 * Directory of the OpenNLP data modules. Only needed if running OpenNLP.
 *
 * -output
 * Directory to save the .events files.
 *
 */
public class ParsedToCoref {
  private String parseDir = ".";
  private String opennlpPath;
  private String outputDir = ".";
  String type = "open"; // or "stanford"

  ParsedToCoref(String[] args) {
    handleParameters(args);
  }

  private void handleParameters(String[] args) {
    opennlpPath = findCorefPath();
    
    HandleParameters params = new HandleParameters(args);
    if( params.hasFlag("-opennlp") ) opennlpPath = params.get("-opennlp");
    if( params.hasFlag("-output") ) outputDir = params.get("-output");
    if( params.hasFlag("-type") ) type = params.get("-type");

    parseDir = args[args.length - 1];
  }

  private String findCorefPath() {
    if( Directory.fileExists("/home/nchamber/code/resources/opennlp-tools-1.3.0/models/english/coref/") )
      return "/home/nchamber/code/resources/opennlp-tools-1.3.0/models/english/coref/";
    else if( Directory.fileExists("C:\\cygwin\\home\\sammy\\code\\resources\\opennlp-tools-1.3.0\\models\\english\\coref\\") )
      return "C:\\cygwin\\home\\sammy\\code\\resources\\opennlp-tools-1.3.0\\models\\english\\coref\\";
    else return null;
  }

  private void analyzeParses( GigaDoc doc, Collection<String> parses, Collection<EntityMention> entities ) {
    if( entities != null ) {
      TreeFactory tf = new LabeledScoredTreeFactory();

      // Read in all the parse trees
      Tree trees[] = new Tree[parses.size()];
      int i = 0;
      for( String parse : parses ) {
        try {
          PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), tf);
          trees[i] = ptr.readTree();
        } catch( Exception ex ) { ex.printStackTrace(); }
        i++;
      }

      // Convert all entity spans from character spans to word-based
      for( EntityMention mention : entities ) {
        mention.convertCharSpanToIndex(TreeOperator.toRaw(trees[mention.sentenceID()-1]));
        doc.addEntity(mention);
        //	mentions[mention.sentenceID()-1].add(mention);
      }

      // Save the verbs in each parse
      int sid = 0, eid = 0;
      for( Tree tree : trees ) {
        if( tree != null ) {
          // Look for the verbs in the tree
          Vector<Tree> parseVerbs = TreeOperator.verbTreesFromTree(tree);
          for( Tree verb : parseVerbs ) {
            //            System.out.println("  verb: " + verb + " index: " + (TreeOperator.wordIndex(tree,verb)+1));
            doc.addEvent(new WordEvent(eid, verb.firstChild().value(), TreeOperator.wordIndex(tree,verb)+1, sid+1));
            eid++;
          }
          sid++;
        }
      }
    }
  }

  public void processData() {
    Coref opennlpCoref = null;
    CorefStanford stanfordCoref = null;
    
    // Initialize the Stanford or OpenNLP coref system.
    if( type.startsWith("open") ) {
      System.out.println("Using OpenNLP Coref");
      if( opennlpPath.length() > 0 ) opennlpCoref = new Coref(opennlpPath);
      else { System.err.println("No opennlp coref found"); System.exit(1); }
    }
    else {
      System.out.println("Using Stanford Coref");
      stanfordCoref = new CorefStanford();
    }
    
    // Process the directory.
    File dir = new File(parseDir);
    if( dir.isDirectory() ) {
      for( String file : Directory.getFilesSorted(parseDir) ) {
        // Only look at *.gz files
        if( !file.startsWith(".") && file.contains("parse") ) {
          String core = file.substring(0,file.indexOf('.'));
          String newfile = outputDir + File.separator + core + ".events";
          
          // Skip locked or files already existing.
          if( !Locks.getLock("coref-" + core) ) continue;
          if( Directory.fileExists(newfile) ) continue;

          System.out.println("file: " + file);

          // Read the file of parses    
          GigawordHandler giga = new GigawordProcessed(parseDir + File.separator + file);

          // Create the output file of full markup
          GigaDoc doc = null;
          try {
            doc = new GigaDoc(newfile);
          } catch( Exception ex ) {
            System.out.println("Skipping to next file...");
            continue;
          }

          // Read the documents in this file 
          Collection<String> parseStrings = giga.nextStory();
          int numDocs = 0;
          while( parseStrings != null ) {
            numDocs++;

            System.out.print(numDocs + ": ");
            System.out.print("(" + giga.currentDoc() + "/" + giga.numDocs() + ") ");
            //        System.out.println(giga.currentStory().substring(10));
            System.out.println(giga.currentStory());

            // Identify entities and coref information
            List<EntityMention> entities = null;
            if( type.startsWith("open") )
              entities = opennlpCoref.processParses( parseStrings );
            else
              entities = stanfordCoref.processParses( TreeOperator.stringsToTrees(parseStrings) );

            // Analyze the parses with entities
            doc.openStory(giga.currentStory(), giga.currentStoryNum());
            analyzeParses( doc, parseStrings, entities );
            doc.closeStory();

            parseStrings = giga.nextStory();
          }
        }
      }
    } else System.out.println("ERROR: given path not a directory: parseDir");
  }

  public static void main(String[] args) {
    if( args.length > 0 ) {
      ParsedToCoref pc = new ParsedToCoref(args);
      pc.processData();
    } else 
      System.out.println("ParsedToCoref -type <open|stanford> [-opennlp <dir>] -output <dir>");
  }
}

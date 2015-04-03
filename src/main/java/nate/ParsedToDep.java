package nate;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.Collection;
import java.util.Vector;

import nate.util.Directory;
import nate.util.HandleParameters;

import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * Creates one output file (.deps) of typed dependency graphs
 *
 * ParsedToDep -output <output-dir> <parses-dir>
 *
 * -type GIGA | BNC
 * GIGA is the normal input, stanford trees in XML format
 * BNC is different, one parse per line, no XML
 * 
 * -output
 * Directory to save the .events files
 *
 */
public class ParsedToDep {
  TreebankLanguagePack _tlp;
  GrammaticalStructureFactory _gsf;
  TreeFactory _tf;
  String _dataPath = "";
  String _outputDir = ".";
  String _inputType = "giga";

  ParsedToDep(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-output") )
      _outputDir = params.get("-output");
    if( params.hasFlag("-type") )
      _inputType = params.get("-type").toLowerCase();
    _dataPath = args[args.length - 1];

    System.out.println("outputdir= " + _outputDir);
    System.out.println("inputtype= " + _inputType);

    _tlp = new PennTreebankLanguagePack();
    _gsf = _tlp.grammaticalStructureFactory();
    _tf = new LabeledScoredTreeFactory();
  }


  private void analyzeParses( GigaDoc doc, Vector<String> parses ) {
    // Read in all the parse trees
    Tree trees[] = new Tree[parses.size()];
    int i = 0;
    for( String parse : parses ) {
      try {
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), _tf);
        trees[i] = ptr.readTree();
      } catch( Exception ex ) { ex.printStackTrace(); }
      i++;
    }

    // Convert each parse to a dependency graph.
    int sid = 0;
    for( Tree tree : trees ) {
      if( tree != null ) {

        // Create the dependency tree - CAUTION: DESTRUCTIVE to parse tree
        try {
          GrammaticalStructure gs = _gsf.newGrammaticalStructure(tree);
          //	  Collection<TypedDependency> deps = gs.typedDependenciesCollapsed();
          // We may want this one now, it collapses everything PLUS handles
          // some verbal conjuncts we need.
          Collection<TypedDependency> deps = gs.typedDependenciesCCprocessed(true);
          doc.addDependencies(deps, sid);
        } catch( Exception ex ) { 
          System.out.println("WARNING: dependency tree creation failed...adding null deps");
          doc.addDependencies(null, sid);
        }

        sid++;
      }
    }
  }

  private boolean validFilename(String file) {
    if( _inputType.equals("bnc") && (file.contains("charniak") || file.contains("_eng")) ) 
      return true;
    else if( _inputType.equals("giga") && file.contains("parse") )
      return true;
    else return false;
  }

  /**
   * @desc Uses the global dataPath directory and reads every .gz file
   * in it, processing each sentence in each document in each file.
   */
  public void processData() {
    int numDocs = 0;

    File dir = new File(_dataPath);
    if( dir.isDirectory() ) {
//      String files[] = dir.list();

      for( String file : Directory.getFilesSorted(_dataPath) ) {
//      for( String file : files ) {
        // Only look at *.gz files
        if( validFilename(file) ) {
          String newfile = _outputDir + File.separator + file + ".deps";
          if( GigaDoc.fileExists(newfile) || GigaDoc.fileExists(newfile + ".gz") ) {
            System.out.println("File exists: " + newfile);
          } else {
            System.out.println("file: " + file);

            // Read the file of parses	  
            DocumentHandler parseReader = null;
            if( _inputType.equals("giga") )
              parseReader = new GigawordProcessed(_dataPath + File.separator + file);
            else if( _inputType.equals("bnc") )
              parseReader = new BNCParseHandler(_dataPath + File.separator + file);

            System.out.println("Reading input type " + _inputType);

            // Create the output file of full markup
            GigaDoc doc = null;
            try {
              doc = new GigaDoc(newfile);
            } catch( Exception ex ) {
              System.out.println("Skipping to next file...");
              continue;
            }

            // Read the documents in this file 
            Vector<String> sentences = parseReader.nextStory();
            int storyID = 0;
            while( sentences != null ) {
              numDocs++;

              System.out.print(numDocs + ": ");
              System.out.print("(" + parseReader.currentDoc() + "/" + parseReader.numDocs() + ") ");
              System.out.println(parseReader.currentStory());

              // Analyze the parses with entities
              doc.openStory(parseReader.currentStory(), storyID);
              analyzeParses( doc, sentences );
              doc.closeStory();

              sentences = parseReader.nextStory();
              storyID++;
            }
          }
        }
      }
    }
  }



  public static void main(String[] args) {
    if( args.length > 0 ) {
      ParsedToDep pd = new ParsedToDep(args);
      pd.processData();
    }
  }

}

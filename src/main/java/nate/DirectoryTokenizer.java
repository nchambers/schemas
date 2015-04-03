package nate;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.Util;
import edu.stanford.nlp.ling.HasWord;

/**
 * Takes gigaword files, which are paragraphs of multiple sentences, and creates 
 * a new file that separates sentences, and tokenizes them.
 *
 * DirectoryTokenizer -output <out-dir> -input giga|enviro <input-dir>
 * 
 * -output
 * Directory to create parsed and dependency files.
 *
 * -input GIGA|ENVIRO
 * The type of text we are processing, Gigaword or Environment.
 */
public class DirectoryTokenizer {
  public int MAX_SENTENCE_LENGTH = 400;
  String _dataPath = "";
  String _outputDir = ".";

  public static final int GIGAWORD = 0;
  public static final int ENVIRO = 1;
  public static final int MUC = 2;
  private int _docType = GIGAWORD;


  public DirectoryTokenizer(String[] args) {
    handleParameters(args);
  }

  private void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-output") ) {
      _outputDir = params.get("-output");
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

  /**
   * @desc Tokenizes the input paragraphs and prints the split sentences to the given doc.
   * @param paragraphs Vector of strings of paragraphs.
   * @param doc The current document we're printing to.
   */
  private void tokenizeSentences(Collection<String> paragraphs, GigaDoc doc) {
    // Paragraphs may be multiple sentences
    for( String fragment : paragraphs ) {
      // Replace underscores (gigaword has underscores in many places commas should be)
      if( fragment.contains(" _ ") ) fragment = fragment.replaceAll(" _ ", " , ");

      // Loop over each sentence
      for( List<HasWord> sentence : Ling.getSentencesFromText(fragment) ) {
        //	System.out.println(sentence.size() + ": **" + sentence + "**");
        doc.addParse(Ling.appendTokens(sentence));
      }
    }
  }

  /**
   * @desc Parse each sentence and save to another file
   */
  public void tokenizeData() {
    int numDocs = 0;
    int numFiles = 0;

    if( _dataPath.length() > 0 ) {

      File dir = new File(_dataPath);
      if( dir.isDirectory() ) {

        // Loop over the files.
        for( String file : Directory.getFiles(_dataPath) ) {
          if( validFilename(file) ) {
            //	  if( file.contains("_2005") ) {

            String tokenFile = _outputDir + File.separator + file + ".tokenized";

            if( GigaDoc.fileExists(tokenFile) ) {
              System.out.println("File exists: " + tokenFile);
            } else {
              System.out.println("file: " + file);

              GigaDoc doc = null;

              // Create the tokenized output file.
              try {
                doc = new GigaDoc(tokenFile);
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
              //		giga = new GigawordFilteredText(_dataPath + File.separator + file,
              //						filterList);

              // Read the documents in the text file. 
              Vector<String> sentences = giga.nextStory();
              int storyID = 0;
              while( sentences != null ) {
                numDocs++;
                System.out.println(numDocs + ": (" + giga.currentDoc() + "/" + giga.numDocs() + ") " + giga.currentStory());
                if( numDocs % 100 == 0 ) Util.reportMemory();

                //		  for( String sentence : sentences ) 
                //		    System.out.println("**" + sentence + "**");

                doc.openStory(giga.currentStory(), storyID);
                tokenizeSentences(sentences, doc);
                doc.closeStory();

                sentences = giga.nextStory();
                storyID++;
              }

              doc.closeDoc();
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
    }
    return false;
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      DirectoryTokenizer parser = new DirectoryTokenizer(args);
      parser.tokenizeData();
    }
  }
}

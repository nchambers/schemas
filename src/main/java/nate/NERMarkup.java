package nate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import nate.reading.ProcessedData;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.Locks;
import nate.util.TreeOperator;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
//import edu.stanford.nlp.pipeline.NERMergingAnnotator;
//import edu.stanford.nlp.pipeline.OldNERAnnotator;
import edu.stanford.nlp.pipeline.PTBTokenizerAnnotator;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;


/**
 * WARNING: Someone in JavaNLP edited this so it wouldn't crash with latest
 *          JavaNLP changes, but I'm sure they didn't test it. Their code
 *          looks sketchy at best...
 * 
 * Reads a directory of parse tree files and outputs the NER tags into
 * the directory "nertemp".  There is also code to process raw text files
 * and not parse trees, but it isn't hooked up to the main() function.
 * It is safer to run on parse trees because you are guaranteed the same
 * tokenization as in the trees.
 * 
 * -input
 * Type of document to tag: "giga", "muc", or "env".
 * 
 * -output
 * The directory in which to write the NER tags.
 * 
 * NERMarkup -input giga|muc|env [-output <dir>] <parses-dir>
 * 
 */
public class NERMarkup {
  String _outputDir = "nertemp";
  String _dataPath;
  private int _docType = DirectoryParser.GIGAWORD;
//  private TokenizerFactory<CoreLabel> _factory;
//  private WordToSentenceProcessor wts = new WordToSentenceProcessor();
  private boolean VERBOSE = false;
  private String _nerPath = "/home/nchamber/code/resources/all.3class.distsim.crf.ser.gz";

//  OldNERAnnotator _nerAnnotator;
//  NERMergingAnnotator _nerMergingAnnotator;
  PTBTokenizerAnnotator _ptbAnnotator;
  WordToSentenceProcessor _proc;


  public NERMarkup(String[] args) {
    if( args.length < 2 ) {
      System.out.println("NERMarkup -input giga|muc|corporate [-output <dir>] <parses-dir>");
      System.exit(-1);
    }
    
    HandleParameters params = new HandleParameters(args);
    if( params.hasFlag("-input") )
      _docType = DirectoryParser.docTypeToInt(params.get("-input"));
    if( params.hasFlag("-output") )
      _outputDir = params.get("-output");
    _dataPath = args[args.length-1];

//    System.out.println("document type:\t" + _docType);
    System.out.println("input:\t\t" + _dataPath);
    System.out.println("output:\t\t" + _outputDir);
    System.out.println("type:\t\t" + _docType);

    System.setProperty("ner.model", _nerPath);
    // Load Tokenizer.
//    _factory = PTBTokenizer.factory(false, new CoreLabelTokenFactory());

    // Load NER.
//    try {
//      _nerAnnotator = new OldNERAnnotator(VERBOSE);
//      _ptbAnnotator = new PTBTokenizerAnnotator(VERBOSE);
//    } catch( Exception ex ) { ex.printStackTrace(); }
//    _proc = new WordToSentenceProcessor();
//    _nerMergingAnnotator = new NERMergingAnnotator(VERBOSE);
  }


  private void outputNER(List<? extends CoreLabel> tokens, int sid, GigaDoc output) {
    String prevType = null;
    int prevStart = -1;
    int i = 1;

    for( CoreLabel token : tokens ) {
//      System.out.print("\t" + token.current());
      String nerType = token.ner();
      //      System.out.println("nerType=" + nerType + " prevType=" + prevType + 
      //			 " prevStart=" + prevStart + " i=" + i);
      // No NER.
      if( nerType.equals("O") ) {
        if( prevType != null ) {
          output.addNER(new NERSpan(NERSpan.stringToType(prevType), sid, prevStart, i));
          prevType = null;
          prevStart = -1;
        }
      }
      // Yes NER.
      else {
        if( prevType == null ) {
          prevType = nerType;
          prevStart = i;
        }
        // Previous type is different, add previous now.
        else if( !prevType.equals(nerType) ) {
          output.addNER(new NERSpan(NERSpan.stringToType(prevType), sid, prevStart, i));
          prevType = nerType;
          prevStart = i;
        }
        // Previous same as current. Do nothing.
        else { }
      }
      i++;
    }
    // Add remaining NER.
    if( prevType != null ) {
      output.addNER(new NERSpan(NERSpan.stringToType(prevType), sid, prevStart, i));
    }
//    System.out.println();
  }
  
  /**
   * This function tokenizes and splits the strings into sentences, and
   * then annotates them for NER information.
   * @param txtSentences Strings of sentences, each string is a paragraph.
   */
  private void analyzeSentences(Collection<String> txtSentences, GigaDoc output) {
    Annotation a = new Annotation();
    List<List<? extends CoreLabel>> allsentences = new ArrayList<List<? extends CoreLabel>>();

    for( String txt : txtSentences ) {
//      List<String> texts = new ArrayList<String>();
//      texts.add(txt);
//      a.set(OriginalStringPLAnnotation.class, texts);
      a.set(TextAnnotation.class, txt);

      // Tokenize.
      _ptbAnnotator.annotate(a);
      List<CoreLabel> tokens = a.get(CoreAnnotations.TokensAnnotation.class);
      List<List<HasWord>> sentences = Ling.getSentencesFromText(txt);
    
      // Split into sentences.
      List<List<? extends CoreLabel>> split = _proc.process(tokens);
      for( List<? extends CoreLabel> sent : split )
        System.out.println("\tcores: " + sent);
      
      // My original line.
      //a.set(WordsPLAnnotation.class, split);
      
      // ** Someone in JavaNLP wrote this section to replace my one line.
      List<CoreMap> splitFormatted = new ArrayList<CoreMap>(split.size());
      for(List<? extends CoreLabel> words : split){
        CoreMap sent = new ArrayCoreMap(1);
        List<CoreLabel> copiedWords = new ArrayList<CoreLabel>();
        for(CoreLabel l : words){ copiedWords.add(l); }
        sent.set(CoreAnnotations.TokensAnnotation.class, copiedWords);
        splitFormatted.add(sent); // JavaNLP peeps didn't have this line...I think it is what they meant.
      }
      a.set(CoreAnnotations.SentencesAnnotation.class, splitFormatted);
      // **
      
      allsentences.addAll(split);
 
      // NER.
      //      System.out.println("NER annotating.");
//      _nerAnnotator.annotate(a);
      
      // This merges consecutive NER types, plain and simple.
      //      _nerMergingAnnotator.annotate(a);
      //      System.out.println("** " + a.get(WordsPLAnnotation.class));
    }
    
    // Output the NER tags.
    int sid = 0;
    for( List<? extends CoreLabel> sentence : allsentences ) {
      System.out.print("sid " + sid);
      outputNER(sentence, sid, output);
      sid++;
    }
  }
  

  /**
   * This function uses parse trees to rebuild the original sentence, and then runs the NER
   * labeler over these sentences.  You want to use parse trees because the Stanford tokenizer
   * might split your tree into multiple sentences, and your parse trees won't line up with
   * the NER output anymore.
   */
  private void analyzeTrees(List<Tree> trees, GigaDoc output) {
    CoreLabelTokenFactory factory = new CoreLabelTokenFactory();
    Annotation a = new Annotation();
    List<List<CoreLabel>> allsentences = new ArrayList<List<CoreLabel>>();

    for( Tree tree : trees ) {
      // The new stuff: someone in JavaNLP wrote this
      List<CoreMap> sentences = new ArrayList<CoreMap>();
      List<CoreLabel> sentence = new ArrayList<CoreLabel>();
      CoreMap sent = new ArrayCoreMap(1);
      sent.set(CoreAnnotations.TokensAnnotation.class,sentence);
      sentences.add(sent);
      allsentences.add(sentence);
      a.set(CoreAnnotations.SentencesAnnotation.class, sentences);
      
      // My old stuff
//      List<List<? extends CoreLabel>> sentences = new ArrayList<List<? extends CoreLabel>>();
//      List<CoreLabel> sentence = new ArrayList<CoreLabel>();
//      sentences.add(sentence);
//      allsentences.add(sentence);
//      a.set(WordsPLAnnotation.class, sentences);
      
      List<String> strs = TreeOperator.stringLeavesFromTree(tree);
      int start = 0;
      for( String str : strs ) {
        CoreLabel label = factory.makeToken(str, start, start+str.length());
        start += str.length() + 1;
        sentence.add(label);
      }

      // NER.
//      _nerAnnotator.annotate(a);
      // This merges consecutive NER types, plain and simple.
      //      _nerMergingAnnotator.annotate(a);
      //      System.out.println("** " + a.get(WordsPLAnnotation.class));
    }

    // Output the NER tags.
    int sid = 0;
    for( List<? extends CoreLabel> sentence : allsentences ) {
      outputNER(sentence, sid, output);
      sid++;
    }

    // Sanity check.
    if( sid != trees.size() ) {
      System.out.println("ERROR: NER output " + sid + " sentences but was given " + trees.size() + " trees.");
      System.exit(-1);
    }
  }
  
  public void process() {
    if( _dataPath.length() > 0 ) {
      File dir = new File(_dataPath);
      if( dir.isDirectory() )
        processDir(_dataPath);
      else {
        String outfile = _outputDir + File.separator + dir.getName() + ".ner";
        processSingleFile(_dataPath, outfile, 0);
      }
    }
  }

  /**
   * Process the given text file, output NER labels to the given outfile.
   * @param file The path to the input text.
   * @param outfile The path to the NER output file we should create.
   * @param numDocs A debugging number indicating how many stories we've processed
   *                up until this file.
   */
  public int processSingleFileText(String file, String outfile, int numDocs) {
    System.out.println("single file: " + file);

    // Create the NER output file.
    GigaDoc outputDoc = null;
    try {
      outputDoc = new GigaDoc(outfile);
    } catch( Exception ex ) {
//      ex.printStackTrace();
      System.out.println("Skipping file...");
      return 0;
    }

    // Open the text file to parse.
    DocumentHandler giga = null;
    if( _docType == DirectoryParser.GIGAWORD )
      giga = new GigawordHandler(file);
    else if( _docType == DirectoryParser.ENVIRO )
      giga = new EnviroHandler(file);
    else if( _docType == DirectoryParser.MUC )
      giga = new MUCHandler(file);

    // Read the documents in the text file. 
    Vector<String> sentences = giga.nextStory();
    int storyID = 0;
    while( sentences != null ) {
      System.out.println((numDocs+storyID) + ": (" + giga.currentDoc() + ") " + giga.currentStory());

      outputDoc.openStory(giga.currentStory(), storyID);
      analyzeSentences(sentences, outputDoc);
      outputDoc.closeStory();

      sentences = giga.nextStory();
      storyID++;
    }
    outputDoc.closeDoc();

    // Return the number of stories we processed.
    return storyID;
  }

  /**
   * Input file is a file of parse trees.
   */
  public int processSingleFile(String file, String outfile, int numDocs) {
    System.out.println("single file: " + file);

    // Create the NER output file.
    GigaDoc outputDoc = null;
    try {
      outputDoc = new GigaDoc(outfile);
    } catch( Exception ex ) {
//      ex.printStackTrace();
      System.out.println("Skipping file...");
      return 0;
    }

    // Open the text file to parse.
    ProcessedData reader = new ProcessedData(file, null, null, null);
    reader.nextStory();
    
    // Read the documents in the text file. 
    Vector<String> parseStrings = reader.getParseStrings();
    int storyID = 0;
    while( parseStrings != null ) {
      System.out.println((numDocs+storyID) + ": (" + reader.currentDoc() + ") " + reader.currentStory());

      List<Tree> trees = TreeOperator.stringsToTrees(parseStrings);
      outputDoc.openStory(reader.currentStory(), storyID);
      analyzeTrees(trees, outputDoc);
      outputDoc.closeStory();

      reader.nextStory();
      parseStrings = reader.getParseStrings();
      storyID++;

//      if( storyID > 100 ) break;
    }
    outputDoc.closeDoc();

    // Return the number of stories we processed.
    return storyID;
  }

  /**
   * @desc Parse each sentence and save to another file
   */
  public void processDir(String path) {
    int numDocs = 0;
//    File dir = new File(path);
//    String files[] = dir.list();

    for( String file : Directory.getFilesSorted(path) ) {
      if( validFilename(file) ) {
//      if( file.contains("199702") ) {

        if( Locks.getLock("nermarkup-" + file) ) {
          // Process the single text file!
          String infile = path + File.separator + file;
          String outfile = _outputDir + File.separator + file + ".ner";
          numDocs += processSingleFile(infile, outfile, numDocs);
        }
          
      }
    }
  }


  private boolean validFilename(String file) {
    if( !file.startsWith(".") && file.contains("parse") ) {
      return true;
    }
    return false;
  }

  public static void main(String[] args) {
//    NERMarkup ann = new NERMarkup(args);
//    List<String> strings = new ArrayList<String>();
//    strings.add("Hello there my friend Nate Chambers, how are you?");
//    strings.add("Hello there my friend Nate Chambers. How are you?");
//    ann.newAnalyzeSentences(strings, null);
    
      NERMarkup ann = new NERMarkup(args);
      ann.process();
  }
}

package nate.order;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import nate.Coref;
import nate.EntityMention;
import nate.order.tb.InfoFile;
import nate.order.tb.TLink;
import nate.order.tb.EventEventLink;
import nate.util.Ling;
import net.didion.jwnl.JWNL;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.dictionary.Dictionary;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * This was the code from my ACL 2007 (and 2008?) paper...as I now remember in 2012.
 * 
 * This class reads an InfoFile and creates feature vectors for the tlinks or events.
 * Directories of features are created.
 *
 */
public class EventParser {
  static int NORMAL = 1;
  static int DIRECTORY = 3;
  private boolean parserPrint = false;  // true causes parse trees to print
  private boolean onlyEvents = false;  // true causes Event features, not time relations
  private boolean DEBUG = false;
  private static String newline = System.getProperty("line.separator");

  Coref coref;
  Closure closure;
  String outputDirectory = "the-feats";
  String outputDirectoryClosed = "the-feats-closed";
  String eventOnlyOutputDir = "the-efeats";
  LexicalizedParser _parser;
  DocumentPreprocessor dp;
  String serializedGrammar = "/Users/nchambers/Projects/stanford/stanford-parser/englishPCFG.ser.gz";
  Options _options = new Options();
  FeatureIndices indices;
  String infoPath = "";
  String wordnetPath = "";
  String corefPath = "";
  HashMap events;
  Vector<TLink> tlinks = new Vector();
  HashMap eventIDs = new HashMap(); // event IDs -> instance IDs

  String sentenceDelimiter = null; // set to "\n" for one sentence per line
  int tagDelimiter = -1; // POS tag delimiter

  int[] entities = new int[150]; // arbitrary large array
  int[] postags  = new int[150]; // arbitrary large array

  InfoFile infoFile = new InfoFile();


  EventParser(String args[]) {
    handleParameters(args);

    events = new HashMap();
    //    dp = new DocumentPreprocessor(options.tlpParams.treebankLanguagePack().getTokenizerFactory());

    // Read word-indices
    indices = new FeatureIndices();
    indices.setWordIndex(indices.indexFromFile("wordIndex.txt"));
    indices.setLemmaIndex(indices.indexFromFile("lemmaIndex.txt"));
    indices.setSynsetIndex(indices.indexFromFile("synsetIndex.txt"));

    //Test.outputFormat = "wordsAndTags,penn,typedDependenciesCollapsed";
    closure = new Closure();
    coref = new Coref(corefPath);
  }


  public void handleParameters(String[] args) {
    int i = 0;
    while( i < args.length ) {
      // print the parse trees
      if( args[i].equalsIgnoreCase("-p") && args.length > i+1 ) {
        parserPrint = true;
      }
      // extract features for events, not relations between events
      else if( args[i].equalsIgnoreCase("-events") && args.length > i+1 ) {
        onlyEvents = true;
      }
      // specifies the location of the info file
      else if( args[i].equalsIgnoreCase("-info") && args.length > i+1 ) {
        infoPath = args[i+1];
        i++;
      }
      // specifies the location of the WordNet .xml file
      else if( args[i].equalsIgnoreCase("-wordnet") && args.length > i+1 ) {
        wordnetPath = args[i+1];
        i++;
      }
      // specifies the location of the parser grammar
      else if( args[i].equalsIgnoreCase("-grammar") && args.length > i+1 ) {
        serializedGrammar = args[i+1];
        i++;
      }
      // specifies the location of the coref data file
      else if( args[i].equalsIgnoreCase("-coref") && args.length > i+1 ) {
        corefPath = args[i+1];
        i++;
      }
      // print debugging messages
      else if( args[i].equalsIgnoreCase("-debug") ) {
        DEBUG = true;
      }
      else System.err.println("No input info file given");
      i++;
    }
    System.out.println("coref " + corefPath);
  }


  /**
   * Save the four POS tags: 2 before, 1 of the event, and 1 after
   */
  public static void setPOSTags(TextEvent event, int[] postags) {
    int[] tags1 = new int[4];
    int i = event.index();

    if( i-2 >= 1 && postags[i-2] != -1 ) tags1[0] = postags[i-2]; else tags1[0] = 1;
    if( i-1 >= 1 && postags[i-1] != -1 ) tags1[1] = postags[i-1]; else tags1[1] = 1;
    if( postags[i] != -1 ) tags1[2] = postags[i]; else tags1[2] = 1;
    if( i+1 < postags.length && postags[i+1] != -1 ) tags1[3] = postags[i+1]; else tags1[3] = 1;

    event.setPOSTags(tags1);
  }

  /**
   * Save the four POS tags: 3 before and 1 of the event
   */
  public static void setPOSTagsBefore(TextEvent event, int[] postags) {
    int[] tags1 = new int[4];
    int i = event.index();

    if( i-3 >= 1 && postags[i-3] != -1 ) tags1[0] = postags[i-3]; else tags1[0] = 1;
    if( i-2 >= 1 && postags[i-2] != -1 ) tags1[1] = postags[i-2]; else tags1[1] = 1;
    if( i-1 >= 1 && postags[i-1] != -1 ) tags1[2] = postags[i-1]; else tags1[2] = 1;
    if( postags[i] != -1 ) tags1[3] = postags[i]; else tags1[3] = 1;

    //    System.out.println(event.id());
    //    System.out.println("Setting " + tags1[0] + " " + tags1[1] + " " + tags1[2] + " " + tags1[3] + " ");

    event.setPOSTags(tags1);
  }

  public static void setEventWords(TextEvent event, List words, SingleFeatureVec vec) {
    int z = 1; // positions start at 1, not 0
    int loc = event.index();
    String modal = null, have = null, be = null, neg = null;

    //    String str = ((Text)event.element().getFirstChild()).getData();
    String str = event.string();
    String[] parts = str.split("\\s+");
    z = z - (parts.length-1);

    Iterator iter = words.iterator();
    while( iter.hasNext() ) {
      // look at the three words before the event
      if( z >= (loc-3) && z <loc ) {
        TaggedWord tw = (TaggedWord) iter.next();
        //	System.out.print(tw.word() + " ");
        // save modal words
        if( tw.tag().equals("MD") || tw.tag().equals("TO") ) { modal = tw.word(); }
        // save "have" words
        else if( Features.isAuxHave(tw.word()) > 1 ) { have = tw.word(); }
        // save "be" words
        else if( Features.isAuxBe(tw.word()) > 1 ) { be = tw.word(); }
        // save "not" words
        else if( Features.isNegation(tw.word()) ) { neg = tw.word(); }

        // if we reach a determiner, the event is a nominal, so remove everything
        // we just saw...could look for alot more tags...length 1 means punctuation
        if( tw.tag().equals("DT") || tw.tag().length()==1 ) {
          modal = null; have = null; be = null; neg = null;
        }
      }
      // save in the feature vector if we're done
      else if( z == loc ) {
        if( modal != null ) vec.setModalWord(modal);
        if( have != null ) vec.setHaveWord(have);
        if( be != null ) vec.setBeWord(be);
        if( neg != null ) vec.setNotWord(true);

        TaggedWord tw = (TaggedWord) iter.next();
        String[] types = stringTypes(tw.word());
        vec.setWord(types[0]);
        vec.setLemma(types[1]);
        vec.setSynset(types[2]);
        //	System.out.println(tw.word() + "->" + Arrays.toString(types));
        return;
      }
      else iter.next();
      z++;
    }
  }


  private void createElementFeatures(TextEvent te1, TextEvent te2, FeatureVector vec) {
    String str,str2;
    int int1,int2;

    // ** Set tense **
    int1 = FeatureVector.tenseToNum(te1.getTense());
    int2 = FeatureVector.tenseToNum(te2.getTense());
    vec.setTenses(int1,int2);

    // ** Set aspect **
    int1 = FeatureVector.aspectToNum(te1.getAspect());
    int2 = FeatureVector.aspectToNum(te2.getAspect());
    vec.setAspects(int1,int2);

    // ** Set modality **
    int1 = FeatureVector.modalityToNum(te1.getModality());
    int2 = FeatureVector.modalityToNum(te2.getModality());
    vec.setModalities(int1,int2);

    // ** Set class **
    int1 = FeatureVector.classToNum(te1.getTheClass());
    int2 = FeatureVector.classToNum(te2.getTheClass());
    vec.setClasses(int1,int2);

    // ** Set polarity **
    int1 = FeatureVector.polarityToNum(te1.getPolarity());
    int2 = FeatureVector.polarityToNum(te2.getPolarity());
    vec.setPolarities(int1,int2);
  }

  /**
   * Saves the tense/aspect/etc. attributes from each element inside the vector
   */
  private void createElementFeatures(Element el1, Element el2, FeatureVector vec) {
    String str,str2;
    int int1,int2;

    // ** Set tense **
    str = el1.getAttribute("tense");
    str2 = el2.getAttribute("tense");
    int1 = FeatureVector.tenseToNum(str);
    int2 = FeatureVector.tenseToNum(str2);
    vec.setTenses(int1,int2);

    // ** Set aspect **
    str = el1.getAttribute("aspect");
    str2 = el2.getAttribute("aspect");
    int1 = FeatureVector.aspectToNum(str);
    int2 = FeatureVector.aspectToNum(str2);
    vec.setAspects(int1,int2);

    // ** Set modality **
    str = el1.getAttribute("modality");
    str2 = el2.getAttribute("modality");
    int1 = FeatureVector.modalityToNum(str);
    int2 = FeatureVector.modalityToNum(str2);
    vec.setModalities(int1,int2);

    // ** Set class **
    str = el1.getAttribute("class");
    str2 = el2.getAttribute("class");
    int1 = FeatureVector.classToNum(str);
    int2 = FeatureVector.classToNum(str2);
    vec.setClasses(int1,int2);

    // ** Set polarity **
    str = el1.getAttribute("polarity");
    str2 = el2.getAttribute("polarity");
    int1 = FeatureVector.polarityToNum(str);
    int2 = FeatureVector.polarityToNum(str2);
    vec.setPolarities(int1,int2);
  }


  private void createEventFeatures(TextEvent ev, SingleFeatureVec vec) {
    String str;
    int int1;

    // ** Set tense **
    int1 = Features.tenseToNum(ev.getTense());
    vec.setTense(int1);

    // ** Set aspect **
    int1 = Features.aspectToNum(ev.getAspect());
    vec.setAspect(int1);

    // ** Set modality **
    int1 = Features.modalityToNum(ev.getModality());
    vec.setModality(int1);

    // ** Set class **
    int1 = Features.classToNum(ev.getTheClass());
    vec.setClass(int1);

    // ** Set polarity **
    int1 = Features.polarityToNum(ev.getPolarity());
    vec.setPolarity(int1);
  }

  /*
  private void createEventFeatures(Element el1, SingleFeatureVec vec) {
    String str;
    int int1;

    // ** Set tense **
    str = el1.getAttribute("tense");
    int1 = Features.tenseToNum(str);
    vec.setTense(int1);

    // ** Set aspect **
    str = el1.getAttribute("aspect");
    int1 = Features.aspectToNum(str);
    vec.setAspect(int1);

    // ** Set modality **
    str = el1.getAttribute("modality");
    int1 = Features.modalityToNum(str);
    vec.setModality(int1);

    // ** Set class **
    str = el1.getAttribute("class");
    int1 = Features.classToNum(str);
    vec.setClass(int1);

    // ** Set polarity **
    str = el1.getAttribute("polarity");
    int1 = Features.polarityToNum(str);
    vec.setPolarity(int1);
  }
   */

  private void printEvents() {
    Iterator iter = events.entrySet().iterator();
    while( iter.hasNext() ) {
      Map.Entry en = (Map.Entry)iter.next();
      System.out.println(en.getValue());
    }
  }


  public static Object findTreeLocation(Tree tree, int leafPos) {
    return findTreeLocation(tree, leafPos, new Integer(1));
  }
  public static Object findTreeLocation(Tree tree, int leafPos, Integer currentPos) {
    if( tree.isLeaf() ) {
      if( leafPos == currentPos.intValue() ) {
        return tree;
      }
      return new Integer(currentPos.intValue()+1);
    } else {
      List<Tree> children = tree.getChildrenAsList();
      for( Tree child : children ) {
        Object obj = findTreeLocation(child, leafPos, currentPos);
        if( obj != null && obj instanceof Integer ) {
          currentPos = (Integer)obj;
        } else return obj; // found the tree
      }
      return currentPos;
    }
  }

  public static Tree findStringInTree(String target, Tree ansTree, int leafPos) {
    Tree tree;
    Object obj = findTreeLocation(ansTree, leafPos);

    int xx = -4;
    while( xx < 5 && (obj instanceof Integer || !target.endsWith(((Tree)obj).value()))) {
      //      if( obj instanceof Integer ) System.out.println("...got " + obj);
      //     else System.out.println(target + "...got *" + ((Tree)obj).value() +"*");
      //      System.out.println("...trying " + xx);

      obj = findTreeLocation(ansTree, leafPos-xx);
      xx++;
    }

    // Integer means no tree was found    
    if( obj instanceof Integer ) {
      System.out.println("WARNING: couldn't find event in sentence tree");
      tree = null;
    } else tree = (Tree)obj;

    // Print appropriate warnings    
    if( tree == null || !target.equalsIgnoreCase(tree.value()) ) {
      if( tree != null && target.endsWith(tree.value()) ) { }
      else {
        System.out.println("WARNING: leaf doesn't match '" + target + "' '");
        if( tree != null ) System.err.println(tree.value() + "'");
      }
    }

    return tree;
  }

  /*
    public Tree findEventInTree(Tree tree, String event) {
    return findEventInTree(tree,event,1);
    }
    /**
   * Search a tree for a string, return the tree rooted at the string
   * @returns The leaf node of the given event word

    public Tree findEventInTree(Tree tree, String event, int x) {
    System.out.println("size of tree " + tree.size());
    if( tree.isLeaf() ) {
    if( tree.value().equalsIgnoreCase(event) ) {
    System.out.println("found " + event + " at " + x);
    return tree;
    }

    }
    List<Tree> children = tree.getChildrenAsList();
    for( Tree child : children ) {
    Tree pos = findEventInTree(child, event,x);
    if( child.isLeaf() ) { System.out.println("at leaf"); x++; }
    if( pos != null ) return pos;
    }
    return null;
    }
   */

  /**
   * @param treeroot The root of the parse tree
   * @param t1 The root of the subtree that dominates t2
   * @param t2 The root of the subtree that is dominated
   * @returns A path up the tree from t2 to t1
   */
  public String path(Tree treeroot, Tree t1, Tree t2) {
    // Find grandparent of event1, check dominance
    Tree p = t1.parent(treeroot); // parent is POS tag
    Tree gp = p.parent(treeroot); // gp is actual parent
    while( gp != null ) {
      List<Tree> path = gp.dominationPath(t2);
      if( path != null ) {
        String spath = "::";
        for( Tree node : path ) {	       
          if( node.isPreTerminal() )
            return spath;
          else spath += node.label() + " ";
        }
      }
      gp = gp.parent(treeroot);
    }	
    return null;
  }

  /**
   * Checks if one event syntactically dominates the other.
   * @param tree The root of the parse tree
   * @param e1 The first event's word
   * @param e2 The second event's word
   * @returns 1 if no dominance, 2 if e1 dominates, or 3 if e2 dominates
   */
  public static int eventRelationship(Tree tree, Tree tree1, Tree tree2) {
    if( tree1 != null && tree2 != null ) {
      // Find grandparent of event1, check dominance
      Tree p = tree1.parent(tree); // parent is POS tag
      Tree gp = p.parent(tree); // gp is actual parent
      if( gp.dominates(tree2) ) return Features.DOMINATES;

      // Find grandparent of event2, check dominance
      p = tree2.parent(tree);
      gp = p.parent(tree);
      if( gp.dominates(tree1) ) return Features.DOMINATED;
    }
    //	else System.out.println("WARNING: no tree1 or no tree2 (" + e1 + "," + e2 + ")");
    else System.out.println("WARNING: no tree1 or no tree2");

    return 1;
  }

  public static TextEvent findEvent(String name, int index, List localEvents) {
    Iterator iter = localEvents.iterator();
    while( iter.hasNext() ) {
      TextEvent event = (TextEvent)iter.next();
      //	    if( name.equalsIgnoreCase(event.string()) ) return event;
      if( index == event.index() ) return event;
    }
    return null;
  }


  /**
   * Creates a list of entities that are involved with each event and attaches
   * them to the TextEvent.
   * @param deps List of typed dependencies
   * @param localEvents Vector of all events in one sentence
   * @param entities Array signalling which words are part of an Entity
   */
  public static void extractEntityEvents(Collection<TypedDependency> deps, 
      List localEvents, int[] entities) {
    for (TypedDependency td : deps) {
      String gov = td.gov().label().value();
      String dep = td.dep().label().value();
      int govi = td.gov().index();
      int depi = td.dep().index();
      // Temporary fix? The dependencies from objects that were in conjuncts are
      // created new, and 1000 is added to the original verb's ID.  We subtract this,
      // making the original verb the real match.
      if( depi > 1000 ) depi -= 1000;

      //	    System.out.println("gov  = " + gov + " " + govi);
      //	    System.out.println("dep  = " + dep + " " + depi);
      //	    System.out.println("full = " + td);

      Vector pairs = new Vector();

      // See if the governor is an event
      TextEvent event = findEvent(gov,govi,localEvents);
      if( event != null ) {
        // Check if the dependent is a known entity
        if( entities[depi] != -1 ) {
          event.addEntityRelation(entities[depi],td.reln().toString());
          //System.out.println("full = " + td);
          //System.out.println(event.id() + "(" + event.string() + ") - " + entities[depi]);
        }
      }
    }
  }

  /**
   * @desc Check if the tree is a clause in a prepositional phrase.
   * @returns The string preposition that heads the PP
   */
  public static String isPrepClause(Tree root, Tree tree) {
    if( tree != null ) {
      Tree p = tree.parent(root).parent(root);
      String pos = p.label().toString();

      // Keep moving up the tree till we hit a new type of POS
      while( p.label().toString().equals(pos) ) p = p.parent(root);

      // We can hit one sentence, but the S must be the PP clause
      if( p.label().toString().equals("S") ) {
        p = p.parent(root);
        if( !p.label().toString().equals("PP") ) return null;
      }

      // We found the PP, return the preposition
      if( p.label().toString().equals("PP") ) {
        List<Tree> list = p.getChildrenAsList();
        for( Tree node : list ) {
          if( node.label().toString().equals("IN") ) 
            return node.firstChild().toString();
        }
      }
    }
    return null;
  }

  public void processInput() {
    if( infoPath.length() <= 0 ) 
      System.err.println("No info file given");
    else {
      // Load WordNet first
      try {
        JWNL.initialize(new FileInputStream(wordnetPath)); // WordNet
      } catch( Exception ex ) { ex.printStackTrace(); }
      // Read the Parser
      _parser = Ling.createParser(serializedGrammar);
      _options = _parser.getOp();

      System.out.println("Processing info file " + infoPath);
      infoFile.readFromFile(new File(infoPath));

      if( onlyEvents ) infoToEventFeatures(infoFile);
      else infoToRelationFeatures(infoFile);

      // Save the indices, we may have added new words
      indices.indexToFile(indices.wordIndex(),"new-wordIndex.txt");
      indices.indexToFile(indices.lemmaIndex(),"new-lemmaIndex.txt");
      indices.indexToFile(indices.synsetIndex(),"new-synsetIndex.txt");
    }
  }

  /** 
   * Find a tlink between two events
   * -- ugly linear search
   */
  private TLink findTLink(String e1, String e2) {
    for( TLink tlink : tlinks ) {
      if( (tlink.event1().equals(e1) && tlink.event2().equals(e2)) )
        return tlink;	    
    }
    return null;
  }


  /**
   * Takes the commmand line arguments and the starting index of the first
   * file to parse.
   */
  public void infoToRelationFeatures(InfoFile infoFile) {
    // These two declarations are just for pretty printing parse trees...
    PrintWriter pwOut = _parser.getOp().tlpParams.pw();
    TreePrint treePrint = _parser.getTreePrint();

    // Make sure the directory to output to exists
    createDirectory(outputDirectory);
    createDirectory(outputDirectoryClosed);

    Vector fileFeatures = new Vector(); // Vector of Vector of FeatureVectors
    Vector fileFeaturesClosed = new Vector(); // Vector of Vector of FeatureVectors
    Collection<String> filenames = infoFile.getFiles();
    int numFiles = filenames.size();
    int x = 1;



    // Loop over the files
    for(Iterator fit = filenames.iterator(); fit.hasNext(); ) {
      String filename = (String)fit.next();
      System.out.println("\n--------------------------------------------------");
      System.out.println("File " + filename + "(" + x + " of " + numFiles + ")");
      x++;
      // Initialize all the variables for this file
      List<Sentence> sentences = infoFile.getSentences(filename);
      coref.reset();
      //      tlinks = infoFile.getTlinks(filename,true); // don't get closed tlinks
      tlinks = infoFile.getTlinks(filename);
      Vector featureVectors = null;
      Vector closedFeatureVectors = null;
      String[] posStrings  = new String[150]; // arbitrary large array
      events.clear();
      System.out.println(sentences.size() + " sentences");
      System.out.println(tlinks.size() + " tlinks");


      // Pre-process sentences in Coref module
      for( Sentence s : sentences ) coref.processParse(s.parse());
      Collection<EntityMention> mentions = coref.getEntities();
      if( mentions != null )
        for( EntityMention mention : mentions ) 
          mention.convertCharSpanToIndex(sentences.get(mention.sentenceID()-1).sentence());


      // Loop over each sentence
      int sentenceID = 0;
      for( Sentence s : sentences ) {
        // reset the position arrays
        for( int j = 0; j < 150; j++ ) entities[j] = -1;
        for( int j = 0; j < 150; j++ ) postags[j]  = -1;

        // save all the events in this Sentence
        List localEvents = s.events();
        for (Iterator it = localEvents.iterator(); it.hasNext();) {
          TextEvent ev = (TextEvent)it.next();
          events.put(ev.id(),ev);
          eventIDs.put(ev.eiid(),ev.id());
        }


        // Read in the parse
        TreeFactory tf = new LabeledScoredTreeFactory();
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(s.parse())), tf);
        Tree ansTree = null;
        try {
          ansTree = ptr.readTree();
          if( parserPrint ) {
            treePrint.printTree(ansTree, Integer.toString(sentenceID), pwOut);
          }
        } catch( Exception ex ) { ex.printStackTrace(); }

        // --------------------------------------------------
        // Save the POS tags
        // --------------------------------------------------
        int z = 1;
        List tagwords = ansTree.taggedYield();
        if( tagwords == null ) System.out.println("words null");
        for (Iterator it = tagwords.iterator(); it.hasNext();) {
          TaggedWord tw = (TaggedWord) it.next();
          //		    System.out.print(z + ":" + tw.tag() + "\\" + tw.word() + " ");
          postags[z] = FeatureVector.posToNum(tw.tag());
          posStrings[z] = tw.tag();
          z++;
        }
        //		for( int xx = 1; xx < postags.length; xx++ ) System.out.print(postags[xx] + " ");
        //		System.out.println();


        // --------------------------------------------------
        // Perform Entity Resolution and Coref
        // --------------------------------------------------
        //	System.out.println("-------------\n" + s.sentence());
        if( mentions != null ) {
          for( EntityMention mention : mentions ) {
            // This entity mention was in this sentence
            if( mention.sentenceID() == sentenceID+1 ) {
              for( int pos = mention.start(); pos <= mention.end(); pos++ )
                entities[pos] = mention.entityID();
            }
          }
        }
        //	System.out.println("Entities:");
        //	for( int xx = 1; xx < entities.length; xx++ ) System.out.print(entities[xx] + " ");
        //	System.out.println();



        // --------------------------------------------------
        // Find events that dominate other events
        // --------------------------------------------------
        if( DEBUG ) System.out.println("\nEvent-Event Dominance");
        for( int j = 0; j < localEvents.size(); j++ ) {
          Tree tree1,tree2;
          TextEvent event1 = (TextEvent)localEvents.get(j);
          String node1 = event1.string();
          int numwords = numWordsOf(node1);

          tree1 = findStringInTree(node1, ansTree, event1.index()+numwords-1);

          // save preposition clauses
          String prep = isPrepClause(ansTree,tree1);
          if( prep != null ) event1.addPrepConstraint(prep);

          for( int k = j+1; k < localEvents.size(); k++ ) {
            TextEvent event2 = (TextEvent)localEvents.get(k);
            String node2 = event2.string();
            numwords = numWordsOf(node2);

            tree2 = findStringInTree(node2, ansTree, event2.index()+numwords-1);

            // rel = {0,1,2}
            int rel = eventRelationship(ansTree, tree1, tree2);

            // --------------------------------------------------
            // Find events that dominate other events
            // --------------------------------------------------
            if( rel == Features.DOMINATES )
              event1.addDominance(event2.id());
            else if( rel == Features.DOMINATED )
              event2.addDominance(event1.id());
          }

          // --------------------------------------------------
          // Set the POS tags for the event
          // --------------------------------------------------
          setPOSTags(event1,postags);
        }

        // --------------------------------------------------
        // Save entities that are arguments to the events
        // --------------------------------------------------
        TreebankLanguagePack tlp = new PennTreebankLanguagePack();
        GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
        // CREATE XML list of dependencies
        GrammaticalStructure gs = gsf.newGrammaticalStructure(ansTree);
        //		    TreePrint.print(gs.typedDependenciesCollapsed(), "xml", pwOut);
        extractEntityEvents(gs.typedDependenciesCollapsed(), localEvents, entities);

        // Print events 
        if( DEBUG ) {
          Iterator iter = localEvents.iterator();
          while( iter.hasNext() ) System.out.println(iter.next());
        }
        sentenceID++;
      }


      if( DEBUG ) { System.out.println("--events--"); printEvents(); }

      // --------------------------------------------------
      // Scale all vectors and add (1) preposition features and (2) Entity Matches
      // --------------------------------------------------

      if( DEBUG ) System.out.println("----------------FEATURES----------");
      if( tlinks != null ) {
        featureVectors = new Vector();
        closedFeatureVectors = new Vector();
        //	closure.computeClosure(tlinks); // info file should already have closed links
        //	System.out.println(tlinks.size() + " tlinks after closure");

        for( TLink tlink : tlinks ) {
          String eid1 = tlink.event1();
          String eid2 = tlink.event2();

          // Only save Event-Event links
          if( tlink instanceof EventEventLink ) {

            if( DEBUG ) System.out.println("tlink e1=" + eid1 + " e2=" + eid2);
            String e1 = (String)eventIDs.get(eid1);
            String e2 = (String)eventIDs.get(eid2);
            if( DEBUG ) System.out.println("now e1=" + e1 + " e2=" + e2);
            TextEvent te1 = (TextEvent)events.get(e1);
            TextEvent te2 = (TextEvent)events.get(e2);
            if( DEBUG ) System.out.println("and e1=" + te1 + " e2=" + te2);

            // Make sure we found this event
            if( te1 == null ) {
              if( e1 != null && e1.charAt(0) != 't' ) // don't complain about times
                System.out.println("ERROR: event not recorded (eid " + eid1 + ") " + e1);
            } else if( te2 == null ) {
              if( e2 != null && e2.charAt(0) != 't')
                System.out.println("ERROR: event not recorded (eid " + eid2 + ") " + e2);
            }
            else {

              FeatureVector vec = new FeatureVector(eid1,eid2,String.valueOf(tlink.relation()));
              if( !tlink.isFromClosure() ) featureVectors.add(vec);
              else closedFeatureVectors.add(vec);

              // ** Set Dominance **
              if( te1.dominates(te2.id()) ) vec.setDominance(Features.DOMINATES);
              else if( te2.dominates(te1.id()) ) vec.setDominance(Features.DOMINATED);
              // ** Set Prepositions **
              if( te1.prep() != null ) vec.setPrep(eid1,te1.prep());
              if( te2.prep() != null ) vec.setPrep(eid2,te2.prep());
              // ** Set Entity matches **
              Integer[] ints = te1.getEntities();
              if( ints != null ) {
                for( int k = 0; k < ints.length; k++ ) {
                  if( te2.getEntity(ints[k]) != null ) {
                    vec.setEntityMatch(2);
                    //		    System.out.println("Entity Match " + eid1 + " " + eid2 + " at " + ints[k]);
                  }
                }
              }

              // ** Set event words **
              Element el1 = te1.element();
              Element el2 = te2.element();
              try {
                // Event 1
                String[] types = stringTypes(te1.string());
                vec.setWordOne(types[0]);
                vec.setLemmaOne(types[1]);
                vec.setSynsetOne(Long.parseLong(types[2]));

                // Event 2
                types = stringTypes(te2.string());
                vec.setWordTwo(types[0]);
                vec.setLemmaTwo(types[1]);
                vec.setSynsetTwo(Long.parseLong(types[2]));
              } catch( Exception ex) { ex.printStackTrace(); }

              // ** Set features from EVENT tag **
              createElementFeatures(te1,te2,vec);

              // ** Set POS tags **
              vec.setPOS1(te1.pos());
              vec.setPOS2(te2.pos());

              // ** Set Before/After **
              if( te1.sid() == te2.sid() ) {
                vec.setSameSentence(true);
                if( te1.index() < te2.index() ) vec.setBefore(true);
                else vec.setBefore(false);
              } else {
                vec.setSameSentence(false);
                if( te1.sid() < te2.sid() ) vec.setBefore(true);
                else vec.setBefore(false);
              }
            }
          }
        } // loop over sentences
      }

      if( DEBUG ) 
        for( Iterator it=featureVectors.iterator(); it.hasNext(); )
          System.out.println(it.next());

      // Save features for later
      fileFeatures.add(featureVectors);
      fileFeaturesClosed.add(closedFeatureVectors);

      //      if( x == 15 ) break;
    } // loop over files

    // Add more features
    addDependentFeatures(fileFeatures, fileFeaturesClosed, indices);
    // Save the features to their files
    featuresToFiles(filenames, fileFeatures, fileFeaturesClosed);
    System.out.println("Saved to file...");
  }

  /**
   * Adds additional features that combine current features into join dependencies.
   * Such as, "do the tenses agree?" and POS bigrams
   * @param all Vector of feature vectors
  public static void addDependentFeatures(Vector<Vector<FeatureVector>> all) {
    HashMap<String,String> words = new HashMap<String,String>();
    HashMap<String,String> lemmas = new HashMap<String,String>();
    HashMap<String,String> synsets = new HashMap<String,String>();

    // build the numeric indices for all string words, lemmas and synsets
    int w = 1;
    int l = 1;
    int s = 1;
    for( Iterator it = all.iterator(); it.hasNext(); ) {
      Vector feats = (Vector)it.next();
      for( Iterator it2 = feats.iterator(); it2.hasNext(); ) {
	FeatureVector vec = (FeatureVector)it2.next();
	if( !words.containsKey(vec.get(FeatureVector.WORD1)) )
	  words.put(vec.get(FeatureVector.WORD1), String.valueOf(w++));
	if( !words.containsKey(vec.get(FeatureVector.WORD2)) )
	  words.put(vec.get(FeatureVector.WORD2), String.valueOf(w++));
	if( !lemmas.containsKey(vec.get(FeatureVector.LEMMA1)) )
	  lemmas.put(vec.get(FeatureVector.LEMMA1), String.valueOf(l++));
	if( !lemmas.containsKey(vec.get(FeatureVector.LEMMA2)) )
	  lemmas.put(vec.get(FeatureVector.LEMMA2), String.valueOf(l++));
	if( !synsets.containsKey(vec.get(FeatureVector.SYNSET1)) )
	  synsets.put(vec.get(FeatureVector.SYNSET1), String.valueOf(s++));
	if( !synsets.containsKey(vec.get(FeatureVector.SYNSET2)) )
	  synsets.put(vec.get(FeatureVector.SYNSET2), String.valueOf(s++));
      }
    }

    // Now change the words/lemmas/synsets to their numeric indices
    for( Iterator it = all.iterator(); it.hasNext(); ) {
      Vector feats = (Vector)it.next();
      for( Iterator it2 = feats.iterator(); it2.hasNext(); ) {
	FeatureVector vec = (FeatureVector)it2.next();
	vec.set(FeatureVector.WORD1,   words.get(vec.get(FeatureVector.WORD1)));
	vec.set(FeatureVector.WORD2,   words.get(vec.get(FeatureVector.WORD2)));
	vec.set(FeatureVector.LEMMA1,  lemmas.get(vec.get(FeatureVector.LEMMA1)));
	vec.set(FeatureVector.LEMMA2,  lemmas.get(vec.get(FeatureVector.LEMMA2)));
	vec.set(FeatureVector.SYNSET1, synsets.get(vec.get(FeatureVector.SYNSET1)));
	vec.set(FeatureVector.SYNSET2, synsets.get(vec.get(FeatureVector.SYNSET2)));
      }
    }

    addDependentFeaturesHelper(all);
  }
   */


  /**
   * Adds additional features that combine current features into join dependencies.
   * Such as, "do the tenses agree?" and POS bigrams
   * @param all Vector of feature vectors
   * @param indices Premade mappings from strings to integers
   */
  public static void addDependentFeatures(Vector<Vector<FeatureVector>> all, FeatureIndices indices) {

    // Now change the words/lemmas/synsets to their numeric indices
    for( Vector<FeatureVector> feats : all ) {
      for( FeatureVector vec : feats ) {
        vec.setWordOne(indices.getWordIndex(vec.get(FeatureType.WORD1)));
        vec.setWordTwo(indices.getWordIndex(vec.get(FeatureType.WORD2)));
        vec.setLemmaOne(indices.getLemmaIndex(vec.get(FeatureType.LEMMA1)));
        vec.setLemmaTwo(indices.getLemmaIndex(vec.get(FeatureType.LEMMA2)));
        vec.setSynsetOne(indices.getSynsetIndex(vec.get(FeatureType.SYNSET1)));
        vec.setSynsetTwo(indices.getSynsetIndex(vec.get(FeatureType.SYNSET2)));
      }
    }

    addDependentFeaturesHelper(all);
  }


  public static void addDependentFeaturesHelper(Vector<Vector<FeatureVector>> all) {
    for( Vector<FeatureVector> feats : all ) {
      for( FeatureVector vec : feats ) {

        // Add the dependent features
        if( vec.get(FeatureType.TENSE1).equals(vec.get(FeatureType.TENSE2)) )
          vec.set(FeatureType.TENSE_MATCH, 2);
        else vec.set(FeatureType.TENSE_MATCH, 1);
        if( vec.get(FeatureType.ASPECT1).equals(vec.get(FeatureType.ASPECT2)) )
          vec.set(FeatureType.ASPECT_MATCH, 2);
        else vec.set(FeatureType.ASPECT_MATCH, 1);
        if( vec.get(FeatureType.CLASS1).equals(vec.get(FeatureType.CLASS2)) )
          vec.set(FeatureType.CLASS_MATCH, 2);
        else vec.set(FeatureType.CLASS_MATCH, 1);

        // Add the pair features
        vec.set(FeatureType.TENSE_PAIR, 
            (Integer.valueOf(vec.get(FeatureType.TENSE1))-1)*Features.TENSE_SIZE
            + Integer.valueOf(vec.get(FeatureType.TENSE2)) );
        vec.set(FeatureType.ASPECT_PAIR, 
            (Integer.valueOf(vec.get(FeatureType.ASPECT1))-1)*Features.ASPECT_SIZE
            + Integer.valueOf(vec.get(FeatureType.ASPECT2)) );
        vec.set(FeatureType.CLASS_PAIR, 
            (Integer.valueOf(vec.get(FeatureType.CLASS1))-1)*Features.CLASS_SIZE
            + Integer.valueOf(vec.get(FeatureType.CLASS2)) );

        // Add the bigram features
        vec.set(FeatureType.POS_BIGRAM1,
            (Integer.valueOf(vec.get(FeatureType.POS1_1))-1)*Features.POS_SIZE
            + Integer.valueOf(vec.get(FeatureType.POS1_2)) );
        vec.set(FeatureType.POS_BIGRAM2,
            (Integer.valueOf(vec.get(FeatureType.POS2_1))-1)*Features.POS_SIZE
            + Integer.valueOf(vec.get(FeatureType.POS2_2)) );
        vec.set(FeatureType.POS_BIGRAM,
            (Integer.valueOf(vec.get(FeatureType.POS1_2))-1)*Features.POS_SIZE
            + Integer.valueOf(vec.get(FeatureType.POS2_2)) );
      }
    }
  }


  public static void addDependentFeatures(Vector<Vector<FeatureVector>> fileFeatures, 
      Vector<Vector<FeatureVector>> fileFeaturesClosed,
      FeatureIndices indices) {
    Vector<Vector<FeatureVector>> all = new Vector(fileFeatures);
    all.addAll(fileFeaturesClosed);
    addDependentFeatures(all,indices);
  }


  /**
   * Converts string features of EVENTS into numerical indices
   * @param fileFeatures A Vector of Vectors of SingleFeatureVecs.
   * Each Vector represents a document and it contains a vector of events.
  public static void addNumericIndices(Vector fileFeatures) {
    HashMap<String,String> modalWords = new HashMap<String,String>();
    HashMap<String,String> haveWords = new HashMap<String,String>();
    HashMap<String,String> beWords = new HashMap<String,String>();
    HashMap<String,String> words = new HashMap<String,String>();
    HashMap<String,String> lemmas = new HashMap<String,String>();
    HashMap<String,String> synsets = new HashMap<String,String>();

    // build the numeric indices for all string words, lemmas and synsets
    int w = 1, l = 1, s = 1, h = 1, b = 1, m = 1;
    for( Iterator it = fileFeatures.iterator(); it.hasNext(); ) {
      Vector feats = (Vector)it.next();
      for( Iterator it2 = feats.iterator(); it2.hasNext(); ) {
	SingleFeatureVec vec = (SingleFeatureVec)it2.next();
	if( !words.containsKey(vec.word()) )
	  words.put(vec.word(), String.valueOf(w++));
	if( !lemmas.containsKey(vec.lemma()) )
	  lemmas.put(vec.lemma(), String.valueOf(l++));
	if( !synsets.containsKey(vec.synset()) )
	  synsets.put(vec.synset(), String.valueOf(s++));
	if( !haveWords.containsKey(vec.haveWord()) )
	  haveWords.put(vec.haveWord(), String.valueOf(h++));
	if( !beWords.containsKey(vec.beWord()) )
	  beWords.put(vec.beWord(), String.valueOf(b++));
	if( !modalWords.containsKey(vec.modalWord()) )
	  modalWords.put(vec.modalWord(), String.valueOf(m++));
      }
    }

    // Now change the words/lemmas/synsets to their numeric indices
    for( Iterator it = fileFeatures.iterator(); it.hasNext(); ) {
      Vector feats = (Vector)it.next();
      for( Iterator it2 = feats.iterator(); it2.hasNext(); ) {
	SingleFeatureVec vec = (SingleFeatureVec)it2.next();
	vec.setWord(words.get(vec.word()));
	vec.setLemma(lemmas.get(vec.lemma()));
	vec.setSynset(synsets.get(vec.synset()));
	vec.setHaveWord(haveWords.get(vec.haveWord()));
	vec.setBeWord(beWords.get(vec.beWord()));
	vec.setModalWord(modalWords.get(vec.modalWord()));
      }
    }
  }
   */



  /**
   * Converts string features of EVENTS into numerical indices
   * @param fileFeatures A Vector of Vectors of SingleFeatureVecs
   * @param indices Class containing all the word/synset index mappings
   * Each Vector represents a document and it contains a vector of events.
   */
  public static void addNumericIndices(Vector<Vector<SingleFeatureVec>> fileFeatures, 
      FeatureIndices indices) {
    // Change the words/lemmas/synsets to their numeric indices
    for( Vector<SingleFeatureVec> feats : fileFeatures ) {
      for( SingleFeatureVec vec : feats ) {
        vec.setWord(indices.getWordIndex(vec.word()));
        vec.setLemma(indices.getLemmaIndex(vec.lemma()));
        vec.setSynset(indices.getSynsetIndex(vec.synset()));
        vec.setHaveWord(String.valueOf(Features.isAuxHave(vec.haveWord())));
        vec.setBeWord(String.valueOf(Features.isAuxBe(vec.beWord())));
        vec.setModalWord(String.valueOf(Features.isModal(vec.modalWord())));
      }
    }
  }



  /**
   * Saves the features to files
   */
  private void featuresToFiles(Collection<String> filenames, Vector fileFeatures, Vector closedFileFeatures) {
    int i = 0;
    // for each file
    for( Iterator it = filenames.iterator(); it.hasNext() && i < fileFeatures.size(); ) {
      String filename = (String)it.next();
      Vector featureVectors = (Vector)fileFeatures.get(i);
      Vector closedFeatureVectors = (Vector)closedFileFeatures.get(i);

      if( filename.endsWith(".tml.xml") )
        filename = filename.substring(0,filename.length()-8);
      else if( filename.endsWith(".tml") )
        filename = filename.substring(0,filename.length()-4);

      featureVecsToFile(outputDirectory + File.separatorChar + "allfeatures-" + filename + ".txt",
          featureVectors);
      featureVectors.addAll(closedFeatureVectors);
      featureVecsToFile(outputDirectoryClosed + File.separatorChar + "allfeatures-" + filename + ".txt",
          featureVectors);
      i++;
    }
  }

  /**
   * Uses the globally set timebankFile to extract all the events from the XML
   */
  private void infoToEventFeatures(InfoFile infoFile) {
    // These two declarations are just for pretty printing parse trees...
    PrintWriter pwOut = _parser.getOp().tlpParams.pw();
    TreePrint treePrint = _parser.getTreePrint();

    // Make sure the directory to output to exists
    createDirectory(eventOnlyOutputDir);

    Vector fileFeatures = new Vector();

    for( String filename : infoFile.getFiles() ) {
      System.out.println("\n------------------------------");
      System.out.println("File " + filename);
      List<Sentence> sentences = infoFile.getSentences(filename);
      Vector featureVecs = new Vector();
      events.clear();

      System.out.println(sentences.size() + " sentences");

      for( int i = 0; i < sentences.size(); i++ ) {
        Sentence s = sentences.get(i);
        // reset the position arrays
        for( int j = 0; j < 150; j++ ) entities[j] = -1;
        for( int j = 0; j < 150; j++ ) postags[j]  = -1;

        // save all the events in this Sentence
        List localEvents = s.events();
        //	if( localEvents == null || localEvents.isEmpty() ) System.out.println("EMPTY EVENTS");
        for (Iterator it = localEvents.iterator(); it.hasNext();) {
          TextEvent ev = (TextEvent)it.next();
          events.put(ev.id(),ev);
          //	  System.out.println("Putting " + ev.eiid() + " -> " + ev.id());
          eventIDs.put(ev.eiid(),ev.id());
        }

        // Read in the parse
        TreeFactory tf = new LabeledScoredTreeFactory();
        //	    PennTreeReader ptr = new PennTreeReader(new StringReader(s.parse()));
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(s.parse())),
            tf);
        Tree ansTree = null;
        try {
          ansTree = ptr.readTree();
          if( parserPrint ) {
            treePrint.printTree(ansTree, Integer.toString(i), pwOut);
          }
        } catch( Exception ex ) { ex.printStackTrace(); }

        // --------------------------------------------------
        // Save the POS tags
        // --------------------------------------------------
        int z = 1;
        List tagwords = ansTree.taggedYield();
        if( tagwords == null ) System.out.println("words null");
        for (Iterator it = tagwords.iterator(); it.hasNext();) {
          postags[z] = FeatureVector.posToNum(((TaggedWord)it.next()).tag());
          z++;
        }

        // --------------------------------------------------
        // Loop over events in this sentence
        // --------------------------------------------------
        //	System.out.println(localEvents.size() + " events");
        Iterator iter = localEvents.iterator();
        while( iter.hasNext() ) {
          TextEvent event = (TextEvent)iter.next();
          //	  System.out.println("Event " + event.id());
          setPOSTagsBefore(event,postags);
          // Create the Feature Vector
          SingleFeatureVec featvec = new SingleFeatureVec(event.eiid(),event.pos());
          createEventFeatures(event, featvec);
          setEventWords(event,tagwords,featvec);

          //	  System.out.println("  Vec: " + featvec);
          featureVecs.add(featvec);
        }
      } // sentences iteration

      //      System.out.println("Saving vecs of size " + featureVecs.size());
      fileFeatures.add(featureVecs);
    } // file iteration

    // Convert string features to indices
    addNumericIndices(fileFeatures, indices);
    eventFeaturesToFiles(infoFile.getFiles(), fileFeatures);
  }


  /**
   * Saves the event features to files
   * @param filenames The list of file paths
   * @param fileFeatures A Vector of Vectors of event features
   */
  private void eventFeaturesToFiles(Collection<String> filenames, Vector fileFeatures) {
    int i = 0;
    // for each file
    for( String filename : filenames ) {
      Vector featureVectors = (Vector)fileFeatures.get(i);
      System.out.println("Printing file " + filename);

      if( filename.endsWith(".tml.xml") )
        filename = filename.substring(0,filename.length()-8);
      else if( filename.endsWith(".tml") )
        filename = filename.substring(0,filename.length()-4);

      featureVecsToFile(eventOnlyOutputDir + File.separatorChar + "allfeatures-" + filename + ".txt",
          featureVectors);
      i++;
    }
  }


  /**
   * Lookup the word in WordNet
   * @returns Array of three strings: word, lemma, synset
   */
  public static String[] stringTypes(String str) {
    try {
      String[] types = new String[3];
      String[] parts = str.split("\\s+");
      IndexWord iword = Dictionary.getInstance().lookupIndexWord(POS.VERB, parts[parts.length-1]);
      if( iword == null ) 
        iword = Dictionary.getInstance().lookupIndexWord(POS.NOUN, parts[parts.length-1]);
      if( iword == null ) {
        types[1] = parts[parts.length-1];
        types[2] = "-1";
      }
      else {
        String lemma = iword.getLemma();
        if( lemma.indexOf(' ') != -1 ) // Sometimes it returns a two word phrase
          lemma = lemma.trim().replace(' ','-');
        types[1] = lemma;
        types[2] = Long.toString(iword.getSense(1).getOffset());
      }
      types[0] = parts[parts.length-1];
      return types;
    } catch( Exception ex ) { ex.printStackTrace(); return null; }
  }


  /**
   * Lookup the word in WordNet
   * @param TYPE The type to lookup: POS.VERB or POS.NOUN
   * @returns Array of three strings: word, lemma, synset
   */
  public static String[] stringTypes(String str, POS TYPE) {
    try {
      String[] types = new String[3];
      String[] parts = str.split("\\s+");
      IndexWord iword = Dictionary.getInstance().lookupIndexWord(TYPE, parts[parts.length-1]);
      if( iword == null ) {
        types[1] = parts[parts.length-1];
        types[2] = "-1";
      }
      else {
        String lemma = iword.getLemma();
        if( lemma.indexOf(' ') != -1 ) // Sometimes it returns a two word phrase
          lemma = lemma.trim().replace(' ','-');
        types[1] = lemma;
        types[2] = Long.toString(iword.getSense(1).getOffset());
      }
      types[0] = parts[parts.length-1];
      return types;
    } catch( Exception ex ) { ex.printStackTrace(); return null; }
  }


  /**
   * Prints a Vector of FeatureVectors to a file
   */
  public static void featureVecsToFile(String filename,Vector vecs) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      Iterator iter = vecs.iterator();
      while( iter.hasNext() ) {
        String svec = ((Features)iter.next()).features();
        // Check for null events ... events that couldn't be captured from the XML
        // (usually from nesting tags)
        if( !svec.contains("null") ) out.write(svec + "\n");
        else System.err.println("skipping..." + svec);
      }
      out.close();
    } catch (Exception e) { e.printStackTrace(); }
  }

  public static int numWordsOf(String str) {
    if( str.matches(".*[\\s\\$\\%].*") ) {
      List<Word> words = Ling.getWordsFromString(str);
      return words.size();
    }
    return 1;
  }

  /**
   * Check if the given path exists and is a directory. If does not exist,
   * create the directory.
   */
  public static void createDirectory(String dir) {
    File fdir = new File(dir);
    if( !fdir.exists() ) {
      fdir.mkdir();
    } else if( !fdir.isDirectory() ) {
      System.err.println("ERROR: " + dir + " is not a directory");
    }
  }

  public static String timeString(long milli) {
    long seconds = milli / 1000;
    long minutes = seconds/60;
    long hours = minutes/60;
    minutes = minutes%60;
    seconds = seconds%60;
    return (hours + "h " + minutes + "m " + seconds + "s");
  }

  private void printEvent(Element el) {
    Node n = el.getFirstChild();
    if( n.getNodeType() == Node.TEXT_NODE ) System.out.println(((Text)n).getData());
    else System.out.println(n);
  }


  public static void main(String[] args) {
    if( args.length < 1 ) System.err.println("No input file given");
    else {

      // CHOOSE THE TAGSET
      //TLink.changeMode(TLink.REDUCED_MODE);
      //      TLink.changeMode(TLink.FULL_MODE);
      TLink.changeMode(TLink.MODE.SYMMETRY);

      // Run the System
      EventParser ep = new EventParser(args);
      ep.processInput();
    }
  }
}

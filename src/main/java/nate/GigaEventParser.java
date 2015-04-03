package nate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import nate.order.tb.EventEventLink;
import nate.order.tb.TLink;
import nate.order.EventParser;
import nate.order.FeatureIndices;
import nate.order.FeatureVector;
import nate.order.Features;
import nate.order.SingleFeatureVec;
import nate.order.TextEvent;
import nate.util.Ling;
import nate.util.Util;
import net.didion.jwnl.JWNL;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;



/**
 * Process Gigaword data files into feature vectors
 */
public class GigaEventParser {
  private boolean onlyEvents = false;  // true causes Event features, not time relations
  private boolean DEBUG = false;
  private static String newline = System.getProperty("line.separator");

  String tlinkOutputDir = "feats";
  String eventOnlyOutputDir = "efeats";
  LexicalizedParser parser;
  DocumentPreprocessor dp;
  String serializedGrammar = "";// /Users/nchambers/Projects/stanford/stanford-parser/englishPCFG.ser.gz";
  Options options = new Options();

  String eventfile = "";
  String depfile = "";
  String parsefile = "";
  String tlinkfile = "";
  GigaDocReader eventDoc;
  GigaDocReader depDoc;
  GigawordProcessed parseDoc;
  FeatureIndices indices;

  String wordnetPath = "";
  String corefPath = "";
  private String duplicatesPath = "duplicates";
  HashMap events;
  Vector<TLink> tlinks[]; // indexed by story id
  Vector<Vector<FeatureVector>> tlinkFeatures = new Vector(); // indexed by story
  private HashSet<String> duplicates;

  String sentenceDelimiter = null; // set to "\n" for one sentence per line
  int tagDelimiter = -1; // POS tag delimiter


  GigaEventParser(String args[]) {
    handleParameters(args);
    checkParameters();

    events = new HashMap();
    //    dp = new DocumentPreprocessor(options.tlpParams.treebankLanguagePack().getTokenizerFactory());

    //Test.outputFormat = "wordsAndTags,penn,typedDependenciesCollapsed";

    // Duplicate Gigaword files to ignore
    duplicates = GigawordDuplicates.fromFile(duplicatesPath);
  }


  public void handleParameters(String[] args) {
    int i = 0;
    while( i < args.length ) {
      // extract features for events, not relations between events
      if( args[i].equalsIgnoreCase("-events") && args.length > i+1 ) {
        eventfile = args[i+1];	i++;
      }
      // extract features for events, not relations between events
      else if( args[i].equalsIgnoreCase("-deps") && args.length > i+1 ) {
        depfile = args[i+1]; i++;
      }
      // extract features for events, not relations between events
      else if( args[i].equalsIgnoreCase("-parsed") && args.length > i+1 ) {
        parsefile = args[i+1]; i++;
      }
      // extract features for events, not relations between events
      else if( args[i].equalsIgnoreCase("-tlinks") && args.length > i+1 ) {
        tlinkfile = args[i+1];	i++;
      }
      // specifies the location of the WordNet .xml file
      else if( args[i].equalsIgnoreCase("-wordnet") && args.length > i+1 ) {
        wordnetPath = args[i+1]; i++;
      }
      // specifies the location of the parser grammar
      else if( args[i].equalsIgnoreCase("-grammar") && args.length > i+1 ) {
        serializedGrammar = args[i+1]; i++;
      }
      // print debugging messages
      else if( args[i].equalsIgnoreCase("-debug") ) {
        DEBUG = true;
      }
      else System.err.println("No input info file given");
      i++;
    }
  }

  /**
   * Sanity check that we got the paths we need
   */
  public void checkParameters() {
    // Check that we have all the paths we need
    String bad = null;
    if( parsefile.length() == 0 ) bad = "parse";
    if( eventfile.length() == 0 ) bad = "events";
    if( depfile.length() == 0 )   bad = "deps";
    if( tlinkfile.length() == 0 ) bad = "tlinks";
    if( bad != null ) {
      System.out.println("Missing datafile type: " + bad);
      System.exit(1);
    }
  }

  /**
   * Read lines of verb pairs ("tlinks") and story IDs ("12 push:shove")
   * Creates an array, one cell for each story.  Each cell is a Vector
   * of tlinks: (id1, id2)
   *
   * @param filename File containing the tlink info
   * @param numStories The number of stories listed in the file
   */
  private void readTlinks(String filename, int numStories) {
    System.out.println("Read tlinks story size " + numStories);
    // Initialize array of Sets for the tlinks
    tlinks = new Vector[numStories];
    for( int i = 0; i < numStories; i++ ) tlinks[i] = new Vector();

    try {
      String line;
      BufferedReader in = new BufferedReader(new FileReader(filename));

      while( (line = in.readLine()) != null ) {
        String parts[] = line.split("\\s+");
        if( parts.length == 5 ) {
          int story = Integer.parseInt(parts[0]);
          // just picked default BEFORE...not used for anything later
          tlinks[story].add(new EventEventLink(story+"."+parts[1], story+"."+parts[2], TLink.TYPE.BEFORE));
        } else {
          System.out.println("Bad tlink line: " + line);
          System.exit(1);
        }
      }

      in.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
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


  private void dummyEventFeatures(TextEvent ev, SingleFeatureVec vec) {
    int int1 = 0;

    vec.setTense(int1);
    vec.setAspect(int1);
    vec.setModality(int1);
    vec.setClass(int1);
    vec.setPolarity(int1);
  }


  private void printEvents() {
    Iterator iter = events.entrySet().iterator();
    while( iter.hasNext() ) {
      Map.Entry en = (Map.Entry)iter.next();
      System.out.println(en.getValue());
    }
  }


  /**
   * Checks if one event syntactically dominates the other.
   * @param tree The root of the parse tree
   * @param e1 The first event's word
   * @param e2 The second event's word
   * @returns 1 if no dominance, 2 if e1 dominates, or 3 if e2 dominates
   */
  public int eventRelationship(Tree tree, Tree tree1, Tree tree2) {
    //	System.out.println("rel? " + e1 + " " + e2);
    //	Tree tree1 = (Tree)findEventInTree(tree, e1);
    //	Tree tree2 = (Tree)findEventInTree(tree, e2);

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


  /**
   * Read the datafiles
   */
  public void loadData() {
    // Read word-indices
    indices = new FeatureIndices();
    indices.setWordIndex(indices.indexFromFile("wordIndex.txt"));
    indices.setLemmaIndex(indices.indexFromFile("lemmaIndex.txt"));
    indices.setSynsetIndex(indices.indexFromFile("synsetIndex.txt"));

    // Read parse/dep/event docs
    parseDoc = new GigawordProcessed(parsefile);
    depDoc = new GigaDocReader(depfile);
    eventDoc = new GigaDocReader(eventfile);
    //    readTlinks(tlinkfile,parseDoc.numDocs());
    readTlinks(tlinkfile,parseDoc.largestStoryID()+1);
    System.out.println("Finished loading data...");
  }

  public void processInput() {
    // Load WordNet first
    try {
      JWNL.initialize(new FileInputStream(wordnetPath)); // WordNet
    } catch( Exception ex ) { ex.printStackTrace(); }

    // Start the Parser
    parser = Ling.createParser(serializedGrammar);
    options = parser.getOp();

    // Load the datafiles
    loadData();

    // Create the features
    String shorty = parsefile.substring(parsefile.lastIndexOf("/")+1,
        parsefile.length());
    createEventFeatures(shorty);
    //    createRelationFeatures(infoFile);
  }



  public void createTLinkFeatures( Vector<Tree> trees, Vector<TextEvent> allEvents[], Vector<TLink> tlinks ) {
    // events hashmap is global (eid -> TextEvent)
    Vector<FeatureVector> featureVectors = null;


    // --------------------------------------------------
    // Find events that dominate other events
    // --------------------------------------------------
    for( int i = 0; i < allEvents.length; i++ ) {
      Vector<TextEvent> localEvents = allEvents[i];
      Tree ansTree = trees.get(i);
      //      System.out.println(ansTree);

      for( int j = 0; j < localEvents.size(); j++ ) {
        Tree tree1,tree2;
        TextEvent event1 = localEvents.get(j);
        String node1 = event1.string();
        int numwords = EventParser.numWordsOf(node1);

        //	System.out.println("(1) " + node1 + " " + (event1.index()+numwords-1));
        tree1 = EventParser.findStringInTree(node1, ansTree, event1.index()+numwords-1);

        // save preposition clauses
        String prep = EventParser.isPrepClause(ansTree,tree1);
        if( prep != null ) event1.addPrepConstraint(prep);

        for( int k = j+1; k < localEvents.size(); k++ ) {
          TextEvent event2 = localEvents.get(k);
          String node2 = event2.string();
          numwords = EventParser.numWordsOf(node2);

          //	  System.out.println("(2)");
          tree2 = EventParser.findStringInTree(node2, ansTree, event2.index()+numwords-1);

          // rel = {0,1,2}
          //	  System.out.println("(R)");
          int rel = eventRelationship(ansTree, tree1, tree2);

          // --------------------------------------------------
          // Find events that dominate other events
          // --------------------------------------------------
          if( rel == Features.DOMINATES )
            event1.addDominance(event2.id());
          else if( rel == Features.DOMINATED )
            event2.addDominance(event1.id());
        }
      }
    }

    if( DEBUG ) { System.out.println("--events--"); printEvents(); }


    // --------------------------------------------------
    // Scale all vectors and add (1) preposition features and (2) Entity Matches
    // --------------------------------------------------

    if( DEBUG ) System.out.println("----------------FEATURES----------");
    if( tlinks != null ) {
      featureVectors = new Vector();

      for( TLink tlink : tlinks ) {
        String eid1 = tlink.event1();
        String eid2 = tlink.event2();
        //	System.out.println("Tlink " + eid1 + " " + eid2);

        TextEvent te1 = (TextEvent)events.get(eid1);
        TextEvent te2 = (TextEvent)events.get(eid2);
        if( te1 == null || te2 == null ) {
          System.out.println("ERROR: tlink contains event not in events file");
          System.out.println("ERROR: " + te1 + " " + te2);
          System.out.println("FROM:  " + tlink);
          System.exit(1);
        }

        FeatureVector vec = new FeatureVector(eid1,eid2,String.valueOf(tlink.relation()));
        featureVectors.add(vec);

        // ** Set Dominance **
        if( te1.dominates(te2.id()) ) vec.setDominance(Features.DOMINATES);
        else if( te2.dominates(te1.id()) ) vec.setDominance(Features.DOMINATED);
        // ** Set Prepositions **
        if( te1.prep() != null ) vec.setPrep(eid1,te1.prep());
        if( te2.prep() != null ) vec.setPrep(eid2,te2.prep());
        // ** Set Entity matches **
        /*
	Integer[] ints = te1.getEntities();
	if( ints != null ) {
	  for( int k = 0; k < ints.length; k++ ) {
	    if( te2.getEntity(ints[k]) != null ) {
	      vec.setEntityMatch(2);
	    }
	  }
	}
         */
        vec.setEntityMatch(2);

        // ** Set event words **
        Element el1 = te1.element();
        Element el2 = te2.element();
        try {
          // Event 1
          String[] types = EventParser.stringTypes(te1.string());
          vec.setWordOne(types[0]);
          vec.setLemmaOne(types[1]);
          vec.setSynsetOne(Long.parseLong(types[2]));

          // Event 2
          types = EventParser.stringTypes(te2.string());
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

      //      if( DEBUG ) 
      //	for( Iterator it=featureVectors.iterator(); it.hasNext(); )
      //	  System.out.println(it.next());
    }

    // Save features for later
    tlinkFeatures.add(featureVectors);
  }


  /**
   * Uses the globally set depDoc,eventDoc,parseDoc to extract the events
   */
  private void createEventFeatures(String docname) {
    int[] entities = new int[400]; // arbitrary large array
    int[] postags  = new int[400]; // arbitrary large array

    // Make sure the directory to output to exists
    EventParser.createDirectory(eventOnlyOutputDir);

    Vector<Tree> trees = new Vector();
    // Initialize event features
    Vector<SingleFeatureVec> featureVecs = new Vector();
    // Initialize global tlinkfeatures (by story)
    tlinkFeatures.clear();

    // Load the next story
    Vector<String> parses = parseDoc.nextStory();
    eventDoc.nextStory(parseDoc.currentStory());
    depDoc.nextStory(parseDoc.currentStory());

    int fid = 0;
    while( parses != null ) {
      String storyname = parseDoc.currentStory();

      if( duplicates.contains(storyname) ) { 
        System.out.println("Duplicate " + storyname);
      } else {
        System.out.println(fid + ": " + storyname);
        if( fid % 100 == 0 ) Util.reportMemory();
        events.clear();
        trees.clear();
        int storyID = parseDoc.currentStoryNum();
        if( storyID != eventDoc.currentStoryNum() )
          System.out.println("ERROR: Parse storyid not equal to event storyid");

        // --------------------------------------------------
        // EVENT and COREF ORDERING
        //	Vector<EntityMention> sidMentions[] = new Vector[parses.size()];
        Vector<TextEvent> sidEvents[] = new Vector[parses.size()];
        // Create sentence based arrays
        for( int i = 0; i < parses.size(); i++ ) {
          //	  sidMentions[i] = new Vector();
          sidEvents[i] = new Vector();
        }
        // Create sentence indexed events and entity mentions
        for( WordEvent ev : eventDoc.getEvents() ) {
          //	TextEvent event = new TextEvent(ev.token(), Integer.toString(ev.eventID()),
          TextEvent event = new TextEvent(ev.token(), storyID+"."+ev.eventID(),
              ev.sentenceID()-1, ev.position());
          sidEvents[ev.sentenceID()-1].add(event);
          events.put(event.id(),event);
        }
        //	for( EntityMention mention : eventDoc.getEntities() ) {
        //	  sidMentions[mention.sentenceID()-1].add(mention);
        //	}
        // --------------------------------------------------

        // Get the typed dependencies for all parses
//        List<List<TypedDependency>> deps = depDoc.getDependencies();

        int sid = 0;
        for( String parse : parses ) {

          // reset the position arrays
          for( int j = 0; j < 400; j++ ) entities[j] = -1;
          for( int j = 0; j < 400; j++ ) postags[j]  = -1;

          // Read in the parse
          TreeFactory tf = new LabeledScoredTreeFactory();
          PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(parse)), tf);
          Tree ansTree = null;
          try {
            ansTree = ptr.readTree();
          } catch( Exception ex ) { ex.printStackTrace(); }
          trees.add(ansTree);

          // --------------------------------------------------
          // Save the POS tags
          // --------------------------------------------------
          int z = 1;
          List tagwords = ansTree.taggedYield();
          if( tagwords == null ) System.out.println("words null");
          // don't bother with huge "sentences" - just skip the garbage
          if( tagwords.size() >= 400 ) continue;
          // iterate over the POS tags
          for (Iterator it = tagwords.iterator(); it.hasNext();) {
            postags[z] = FeatureVector.posToNum(((TaggedWord)it.next()).tag());
            z++;
            if( z >= 400 ) break;
          }

          // --------------------------------------------------
          // Loop over events in this sentence
          // --------------------------------------------------
          for( TextEvent event : sidEvents[sid] ) {
            EventParser.setPOSTagsBefore(event,postags);

            // Create the Feature Vector
            SingleFeatureVec featvec = new SingleFeatureVec(event.id(), event.pos());
            dummyEventFeatures(event,featvec);
            EventParser.setEventWords(event,tagwords,featvec);

            //	  System.out.println(ev);
            featureVecs.add(featvec);
          }


          // **** This is currently pointless.  By definition of my groupings,
          // all TLinks share entities. You're not in a tlink unless you share
          // an entity...  All features set to on...
          // --------------------------------------------------
          // Build the Entity Mention array
          // --------------------------------------------------
          /*
	  for( EntityMention mention : sidMentions[sid] ) {
	    for( int pos = mention.start(); pos <= mention.end(); pos++ )
	      entities[pos] = mention.entityID();
	  }
	  //	System.out.println("Entities:");
	  //	for( int xx = 1; xx < entities.length; xx++ ) System.out.print(entities[xx] + " ");
	  //	System.out.println();
	  EventParser.extractEntityEvents(deps.get(sid), sidEvents[sid], entities);
           */

          // --------------------------------------------------
          // Set the POS tags for the event
          // --------------------------------------------------
          for( TextEvent event : sidEvents[sid] )
            EventParser.setPOSTags(event,postags);

          sid++;
        } // parses iteration

        // --------------------------------------------------
        // Create TLinks over Event Pairs
        // --------------------------------------------------
        createTLinkFeatures( trees, sidEvents, tlinks[storyID] );
        fid++;
      }

      // Load the next story
      parses = parseDoc.nextStory();
      eventDoc.nextStory(parseDoc.currentStory());
      depDoc.nextStory(parseDoc.currentStory());

      //      if( fid == 10 ) break;
    } // story iteration


    System.out.println("Adding features and saving to file...");

    // Convert string features to indices
    Vector temp = new Vector();
    temp.add(featureVecs); // silly wrapper to reuse addNumericIndices code
    System.out.println("Adding event indices...");
    EventParser.addNumericIndices(temp, indices);
    System.out.println("Saving event features...");
    eventFeaturesToFile(docname, featureVecs);

    // Add more tlink features
    System.out.println("Adding tlink indices...");
    EventParser.addDependentFeatures(tlinkFeatures, indices);
    System.out.println("Saving tlink features...");
    tlinkFeaturesToFile(docname, tlinkFeatures);
  }


  /**
   * Saves the event features to files
   * @param filenames The list of file paths
   * @param fileFeatures A Vector of Vectors of event features
   */
  private void eventFeaturesToFile(String docname, Vector<SingleFeatureVec> vecs) {
    String filename = eventOnlyOutputDir + File.separatorChar + docname + ".txt";
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      for( Features vec : vecs ) {
        String svec = vec.features();
        // Check for null events ... events that couldn't be captured from the XML
        // (usually from nesting tags)
        if( !svec.contains("null") ) out.write(svec + "\n");
        else System.err.println("skipping..." + svec);
      }
      out.close();
    } catch (Exception e) { e.printStackTrace(); }
  }


  /**
   * Saves the features to files
   */
  private void tlinkFeaturesToFile(String docname, 
      Vector<Vector<FeatureVector>> tlinkFeatures ) {
    // Make sure the directory to output to exists
    EventParser.createDirectory(tlinkOutputDir);
    String filename = tlinkOutputDir + File.separatorChar + docname + ".txt";

    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));

      // for each story
      for( Vector<FeatureVector> story : tlinkFeatures ) {
        for( Features vec : story ) {
          //      for( Features vec : tlinkFeatures ) {
          String svec = vec.features();
          // Check for null events ... events that couldn't be captured from the XML
          // (usually from nesting tags)
          if( !svec.contains("null") ) out.write(svec + "\n");
          else System.err.println("skipping..." + svec);
        }
      }
      out.close();
    } catch (Exception e) { e.printStackTrace(); }
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
      //TLink.changeMode(TLink.MODE.REDUCED);
      TLink.changeMode(TLink.MODE.FULL);

      // Run the System
      GigaEventParser ep = new GigaEventParser(args);
      ep.processInput();
    }
  }
}

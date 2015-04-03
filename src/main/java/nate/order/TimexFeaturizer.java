package nate.order;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.FileReader;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.HashMap;
import java.util.Iterator;

import nate.order.tb.InfoFile;
import nate.order.tb.TLink;
import nate.util.Ling;


public class TimexFeaturizer {
  String infoPath = null;
  String eventsDir = null;
  String outputDirectory = "etfeats";
  String outputDirectoryClosed = "etfeats-closed";
  boolean DEBUG = false;
  String serializedGrammar = "/Users/nchambers/Projects/stanford/stanford-parser/englishPCFG.ser.gz";
  Options options = new Options();
  LexicalizedParser _parser;
  FeatureIndices indices;

  InfoFile infoFile = new InfoFile();

  /**
   * Constructor
   */
  TimexFeaturizer(String[] args) {
    handleParameters(args);

    // Read word-indices
    indices = new FeatureIndices();
    indices.setTimeIndex(indices.indexFromFile("timeIndex.txt"));
  }


  public void handleParameters(String[] args) {
    int i = 0;
    while( i < args.length ) {
      // specifies the location of the info file
      if( args[i].equalsIgnoreCase("-info") && args.length > i+1 ) {
        infoPath = args[i+1];
        i++;
      }
      // specifies the location of the info file
      else if( args[i].equalsIgnoreCase("-events") && args.length > i+1 ) {
        eventsDir = args[i+1];
        i++;
      }
      // specifies the location of the parser grammar
      else if( args[i].equalsIgnoreCase("-grammar") && args.length > i+1 ) {
        serializedGrammar = args[i+1];
        i++;
      }
      // print debugging messages
      else if( args[i].equalsIgnoreCase("-debug") ) {
        DEBUG = true;
      }
      else System.err.println("Unknown arg " + args[i]);
      i++;
    }

    if( infoPath == null || eventsDir == null ) {
      System.err.println("No -info or no -events given");
      System.exit(1);
    }
  }


  /**
   * @desc Read all the event-time links from the infofile, and create feature
   *       vectors. The event features are read directly from a pre-created 
   *       file of event features.  The time features are created in this function.
   *       The final event-time tlinks are saved to file.
   */
  private void infoToEventTimeLinks() {

    // Make sure the directory to output to exists
    EventParser.createDirectory(outputDirectory);
    EventParser.createDirectory(outputDirectoryClosed);

    Vector fileFeatures = new Vector(); // Vector of Vector of FeatureVectors
    int numFiles = infoFile.getFiles().size();
    int x = 1;

    // Loop over the files
    for(String filename : infoFile.getFiles() ) {
      Vector featureVectors = null;
      Vector closedFeatureVectors = null;
      String[] posStrings  = new String[150]; // arbitrary large array
      System.out.println("\n--------------------------------------------------");
      System.out.println("File " + filename + "(" + x + " of " + numFiles + ")");
      x++;

      // Get the sentences and tlinks in this document
      Collection<Sentence> sentences = infoFile.getSentences(filename);
      Vector<TLink> tlinks = infoFile.getTlinksOfType(filename,TLink.EVENT_TIME); // don't get closed tlinks
      System.out.println(sentences.size() + " sentences");
      System.out.println(tlinks.size() + " tlinks");
      HashMap<String,SingleFeatureVec> eventMap = new HashMap();
      HashMap<String,TextEvent> texteventMap = new HashMap();
      HashMap<String,Timex> timexMap = new HashMap();

      // Read in the event features
      try {
        String featfile = "allfeatures-" + baseFilename(filename) + ".txt";
        BufferedReader in = new BufferedReader(new FileReader(eventsDir + File.separator + featfile));
        while( in.ready() ) {
          String line = in.readLine();
          if( !line.matches("ei\\d+ \\d.*") ) {
            System.out.println("Bogus line in events file " + featfile + ": " + line);
            System.exit(1);
          }

          // Recreate the event feature vector
          String parts[] = line.split(" ");
          SingleFeatureVec vec = new SingleFeatureVec(parts[0]);
          for( int i = 1; i < parts.length; i++ )
            vec.set(i-1, parts[i]);

          // Save the events indexed by eid
          eventMap.put(parts[0],vec);
        }
      } catch( Exception ex ) { ex.printStackTrace(); continue; }


      // Loop over the sentences and extract time expressions
      Vector<Tree> parsetrees = new Vector();
      for( Sentence s : sentences ) {
        // Read in the parse
        TreeFactory tf = new LabeledScoredTreeFactory();
        PennTreeReader ptr = new PennTreeReader(new BufferedReader(new StringReader(s.parse())), tf);
        Tree ansTree = null;
        try {
          ansTree = ptr.readTree();
          parsetrees.add(Integer.valueOf(s.sid()), ansTree);
        } catch( Exception ex ) { ex.printStackTrace(); }

        // Read in the event objects (need them for domination checks...)
        List<TextEvent> textevents = s.events();
        for( TextEvent ev : textevents ) {
          texteventMap.put(ev.eiid(),ev);
        }

        // Create the timex features
        List<Timex> timexes = s.timexes();
        for( Timex time : timexes ) {
          Tree tree = EventParser.findStringInTree(time.text(), ansTree, time.offset());
          String prep = EventParser.isPrepClause(ansTree, tree);
          if( prep != null ) time.setPrep(prep);

          time.setSID(Integer.valueOf(s.sid()));

          timexMap.put(time.tid(), time);
        }
      }

      // Don't forget the document time stamp(s)
      List<Timex> stamps = infoFile.getDocstamp(filename);
      for( Timex stamp : stamps ) {
        stamp.setText("docstamp");
        stamp.setSID(-1);
        timexMap.put(stamp.tid(), stamp);
      }

      // Create the tlink feature vectors
      if( tlinks != null ) {
        featureVectors = new Vector();
        closedFeatureVectors = new Vector();

        for( TLink tlink : tlinks ) {
          String ee = tlink.event1();
          String tt;
          int order = ETFeatureVec.EVENT_TIME;
          if( ee.charAt(0) == 't' ) { 
            tt = ee;
            ee = tlink.event2();
            order = ETFeatureVec.TIME_EVENT;
          } else tt = tlink.event2();

          Timex time = timexMap.get(tt);
          SingleFeatureVec event = eventMap.get(ee);
          if( event == null ) System.out.println("EVENT DOESN'T EXIST " + ee);
          else if( time == null ) System.out.println("TIMEX DOESN'T EXIST " + tt);
          else {
            ETFeatureVec vec = new ETFeatureVec(ee, tt);
            if( !tlink.isFromClosure() ) featureVectors.add(vec);
            else closedFeatureVectors.add(vec);

            vec.setRelation(String.valueOf(tlink.relation()), order);
            TextEvent ev = texteventMap.get(event.event());
            //	    System.out.println("-- " + tlink);
            //	    System.out.println("** " + event);
            //	    System.out.println("** " + time);

            // EVENT features
            vec.set(ETFeatureType.POS0, event.get(EFeatureType.POS0));
            vec.set(ETFeatureType.POS1, event.get(EFeatureType.POS1));
            vec.set(ETFeatureType.POS2, event.get(EFeatureType.POS2));
            vec.set(ETFeatureType.POS3, event.get(EFeatureType.POS3));
            vec.set(ETFeatureType.WORD, event.get(EFeatureType.WORD));
            vec.set(ETFeatureType.LEMMA, event.get(EFeatureType.LEMMA));
            vec.set(ETFeatureType.SYNSET, event.get(EFeatureType.SYNSET));
            vec.set(ETFeatureType.TENSE, event.get(EFeatureType.TENSE));
            vec.set(ETFeatureType.ASPECT, event.get(EFeatureType.ASPECT));
            vec.set(ETFeatureType.MODAL, event.get(EFeatureType.MODAL));
            vec.set(ETFeatureType.POLARITY, event.get(EFeatureType.POLARITY));
            vec.set(ETFeatureType.CLASS, event.get(EFeatureType.CLASS));

            // SAME SENTENCE feature
            if( ev.sid() == time.sid() ) {
              vec.set(ETFeatureType.SAME_SENTENCE, "2");
              vec.set(ETFeatureType.EVENT_DOMINATES, "1");

              // DOMINATED feature
              Tree parse = parsetrees.elementAt(ev.sid());
              Tree ttree = EventParser.findStringInTree(time.text(), parse, time.offset());
              if( ttree != null ) {
                ttree = ttree.parent(parse);
                ttree = ttree.parent(parse);
              }
              Tree etree = EventParser.findStringInTree(ev.string(), parse, ev.index());
              if( etree != null ) {
                etree = etree.parent(parse);
                etree = etree.parent(parse);
              }
              if( etree.dominates(ttree) )
                vec.set(ETFeatureType.EVENT_DOMINATES, "2");

            } else if( time.text().equals("docstamp") ) {
              vec.set(ETFeatureType.SAME_SENTENCE, "3"); // 2 for docstamp
              vec.set(ETFeatureType.EVENT_DOMINATES, "1");
            } else {
              vec.set(ETFeatureType.SAME_SENTENCE, "1");
              vec.set(ETFeatureType.EVENT_DOMINATES, "1");
            }

            // PREP feature
            String prep = time.prep();
            if( prep == null ) vec.set(ETFeatureType.PREP, "1");
            else vec.set(ETFeatureType.PREP, FeatureIndices.getPrepositionIndex(prep.toLowerCase()));
            // TIME TEXT feature
            //	    vec.set(ETFeatureType.TIME_TEXT, time.text());
            vec.set(ETFeatureType.TIME_TEXT, indices.getTimeIndex(time.text()));

          } // end creation of ET vector
        } // for( TLink tlink : tlinks )

        // Output new feature vectors to file
        vecsToFile(filename, featureVectors);
        featureVectors.addAll(closedFeatureVectors);
        vecsToFileClosed(filename, featureVectors);

      } // if( tlinks != null)
    }
  }


  private String baseFilename(String filename) {
    if( filename.endsWith(".tml.xml") )
      filename = filename.substring(0,filename.length()-8);
    else if( filename.endsWith(".tml") )
      filename = filename.substring(0,filename.length()-4);
    return filename;
  }

  private void vecsToFile(String filename, Vector<ETFeatureVec> featureVectors) {
    filename = baseFilename(filename);

    EventParser.featureVecsToFile(outputDirectory + File.separatorChar + "allfeatures-" + filename + ".txt",
        featureVectors);
  }

  private void vecsToFileClosed(String filename, Vector<ETFeatureVec> featureVectors) {
    filename = baseFilename(filename);

    EventParser.featureVecsToFile(outputDirectoryClosed + File.separatorChar + "allfeatures-" + filename + ".txt",
        featureVectors);
  }


  public void processInput() {
    if( infoPath.length() <= 0 ) 
      System.err.println("No info file given");
    else {
      // Read the Parser
      _parser = Ling.createParser(serializedGrammar);
      options = _parser.getOp();

      System.out.println("Processing info file " + infoPath);
      infoFile.readFromFile(new File(infoPath));

      infoToEventTimeLinks();

      indices.indexToFile(indices.timeIndex(),"new-timeIndex.txt");
    }
  }


  public static void main(String[] args) {
    if( args.length < 1 ) System.err.println("No input file given");
    else {

      // CHOOSE THE TAGSET
      //TLink.changeMode(TLink.REDUCED_MODE);
      //      TLink.changeMode(TLink.FULL_MODE);
      TLink.changeMode(TLink.MODE.SYMMETRY);

      // Run the System
      TimexFeaturizer tf = new TimexFeaturizer(args);
      tf.processInput();
    }
  }

}

package nate.order.tb;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import nate.order.TextEvent;
import nate.order.Timex;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.Ling;
import nate.util.Util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import nate.Pair;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
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
 * This class reads a directory of files for the Timebank Corpus, parses, and
 * extracts the Events, TLinks, and Time expressions from the files.  It puts all
 * of the parse trees and condensed information into a single XML file so our later
 * featurizers do not need to rerun this language preprocessing.
 * 
 * Input: directory where the TimeBank lives
 * Output: An XML file (default): events.info.xml
 * 
 * No flags are needed by default, unless you need to specify locations other than the
 * defaults on the NLP machines:
 * : -grammar <path>
 *   The English Grammar location.
 * : -closure <path>
 *   The closure rules file location.
 * : -mode [full | tempeval]
 *   What relation set to use in the final infofile.
 *
 * : -output <path>
 *   The .info file that is created.
 * : -tempeval <dir-path>
 *   The directory containing TempEval annotated files.
 * : -bethard <file-path>
 *   The file containing Bethard's tlink annotations.
 * : -turk <file-path>
 *   The file containing event pairs with relations pulled from Turker annotations. (turk-to-timebank.pl)
 * : -turkhappened <file-path>
 *   The file containing events marked as happened or not, pulled from Turker annotations. (turkhappened-to-timebank.pl)
 *   
 */
public class TimebankParser {
  boolean DEBUG = false;
  boolean _parserPrint = false;
  int _onesentence = -1;
  String _tempevaldir = null;
  String _bethardDir = null;
  String _turkDir = null;
  String _turkHappenedDir = null;
  String _directory = null;
  String _outputInfoFile = "events.info.xml";
  String _infopath = null; // only used to preload one, then merge maybe with TempEval data.
  
  String _serializedGrammar = "/home/nchamber/code/resources/englishPCFG.ser.gz";
  Options _options = new Options();
  LexicalizedParser _parser;
  GrammaticalStructureFactory _gsf;
  Map<String,Element> _makeInstances = new HashMap<String,Element>(); // event IDs -> MakeInstance XML Elementss
  
  TimebankParser() {
    init();
  }
  
  TimebankParser(String[] args) {
    handleParameters(args);
    init();
    System.out.println("Running with TLink mode = " + TLink.currentMode);
  }

  private void init() {
    // Phrase structure.
    _parser = Ling.createParser(_serializedGrammar);
    // Dependency trees.
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    _gsf = tlp.grammaticalStructureFactory();
  }
  
  public void handleParameters(String[] args) {
    HandleParameters params = new HandleParameters(args);

    if( params.hasFlag("-s") )
      _onesentence = Integer.parseInt(params.get("-s"));
    if( params.hasFlag("-p") )
      _parserPrint = true;
    if( params.hasFlag("-grammar") )
      _serializedGrammar = params.get("-grammar");
    if( params.hasFlag("-debug") )
      DEBUG = true;
    if( params.hasFlag("-tempeval") )
      _tempevaldir = params.get("-tempeval");
    if( params.hasFlag("-bethard") )
      _bethardDir = params.get("-bethard");
    if( params.hasFlag("-turk") )
      _turkDir = params.get("-turk");
    if( params.hasFlag("-turkhappened") )
      _turkHappenedDir = params.get("-turkhappened");
    if( params.hasFlag("-output") )
      _outputInfoFile = params.get("-output");
    if( params.hasFlag("-info") )
      _infopath = params.get("-info");
    if( params.hasFlag("-mode") )
      changeMode(params.get("-mode"));
    
    _directory = args[args.length-1];
  }

  public void changeMode(String flagval) {
    if( flagval.equals("full") )
      TLink.changeMode(TLink.MODE.FULL);
    else if( flagval.equals("tempeval") )
      TLink.changeMode(TLink.MODE.TEMPEVAL);
    else {
      System.out.println("ERROR: unknown tlink mode " + flagval);
      System.exit(-1);
    }
  }

  public void processInput() {
    if( _directory != null ) {
      InfoFile infoFile = new InfoFile();
      
      if( _infopath != null )
        infoFile.readFromFile(new File(_infopath));
      else {
        processDirectory(_directory, infoFile);
        // Write to file (in case TempEval crashes).
        infoFile.writeToFile(new File(_outputInfoFile));
      }
      System.out.println("Done building infofile!");
      
      // Check for Bethard data to merge!
      if( _bethardDir != null )
        TimebankUtil.mergeBethard(_bethardDir, infoFile);

      // Check for Amazon Mech Turk data to merge!
      if( _turkDir != null )
        mergeTurk(_turkDir, infoFile);
      
      // Check for Amazon Mech Turk data to merge!
      if( _turkHappenedDir != null )
        mergeTurkHappenedData(_turkHappenedDir, infoFile);

      // Write to file again, with TempEval.
      infoFile.writeToFile(new File(_outputInfoFile));
    }
  }
    
  /**
   * This code is almost a duplicate of mergeBethard.
   * 
   * The input file is created by my script (turk-to-timebank.pl) which reads in a .csv file
   * that the JHU students created as the output from Turk. The .csv file contains all of the
   * original information, but this function takes my single text file that reconstructed
   * the event pair for each label. 
   * 
   * Adds these Amazon Mech Turk tlink annotations into the full TimeBank corpus.
   * The given infoFile receives the new tlinks destructively.
   * @param path The path to my Turk single file containing all new tlinks.
   * @param infoFile The current TimeBank info file.
   */
  private void mergeTurk(String path, InfoFile infoFile) {
    TurkAnnotation turk = new TurkAnnotation(path);
    for( String doc : infoFile.getFiles() ) {
      List<TLink> newlinks = new ArrayList<TLink>();
      List<TLink> turkLinks = turk.getTLinks(doc);
      List<TLink> tbLinks = infoFile.getTlinks(doc);
      
      // We might not always have links in every document. Turkers might have disagreed, so
      // those links aren't in the annotated file.
      if( turkLinks != null ) {
        for( TLink turkLink : turkLinks ) {
          if( turkLink.event1() == null )
            System.out.println("ERROR: Turk event1 id null " + turkLink.event1());
          if( turkLink.event2() == null )
            System.out.println("ERROR: Turk event2 id null " + turkLink.event2());
          // Create a new tlink with the event IDs
          TLink newlink = new EventEventLink(turkLink.event1(), turkLink.event2(), turkLink.relation());
          newlink.setOrigin("turk");
          
          // Now make sure it is a new tlink.
          boolean duplicate = false;
          for( TLink current : tbLinks ) {
            // We happily replace NONE links without batting an eye.
            if( current.relation() != TLink.TYPE.NONE ) {
              // Conflicts should be skipped.
              if( newlink.conflictsWith(current) ) {
                System.out.println("CONFLICTS Turk: " + newlink + " with previous " + current);
                duplicate = true;
              }
              // Exact duplicates should be skipped.
              if( newlink.compareToTLink(current) ) {
                System.out.println("Duplicate Turk: " + newlink + " with timebank's " + current);
                duplicate = true;
              }
            }
          }
         
          // Now add the link.
          if( !duplicate ) {
            newlinks.add(newlink);
            System.out.println("Adding Turk: " + newlink);
          }
        }

        infoFile.addTlinks(doc, newlinks);
      }
    }
  }
  
  
  /**
   * The input file is created by my script (turkhappened-to-timebank.pl) which reads in a .csv file
   * that the JHU students created as the output from Turk. The .csv file contains all of the
   * original information, but this function takes my single text file that reconstructed
   * the event labels.
   * 
   * @param path The path to my Turk single file containing all events with happened/not-happened labels.
   * @param infoFile The current TimeBank info file.
   */
 private void mergeTurkHappenedData(String path, InfoFile infoFile) {
   TurkHappenedAnnotation turk = new TurkHappenedAnnotation(path);
   for( String doc : infoFile.getFiles() ) {
     List<Pair<String,String>> turkEvents = turk.getEvents(doc);
          
     // We might not always have links in every document. Turkers might have disagreed, so
     // those links aren't in the annotated file.
     if( turkEvents != null ) {
       System.out.println("Adding " + turkEvents.size() + " happened events to " + doc);
       for( Pair<String,String> labeledEvent : turkEvents ) {
         System.out.println("labeled: " + labeledEvent);
         
         if( labeledEvent.first() == null )
           System.out.println("ERROR: Turk event id null " + labeledEvent.first());

         if( !infoFile.setEventAttribute(doc, labeledEvent.first(), TextEvent.HAPPENED_ELEM, labeledEvent.second()) )
           System.out.println("ERROR: couldn't set Turk happened attribute for " + labeledEvent + " in doc " + doc);     
       }
     }
   }
 }
  
  /**
   * Reads all .xml files in the given directory path, parses, and extracts the TimeBank object
   * information into the given infoFile.
   * @param sdir The path to the TimeBank file directory.
   * @param infoFile The infofile to put all of the parsing information into.
   */
  private void processDirectory(String sdir,InfoFile infoFile) {
    try {
      File dir = new File(sdir);
      String path = dir.getAbsolutePath();
      String[] files = Directory.getFilesSorted(sdir);
      int count = 1, total = 0;;

      System.out.println("Processing directory " + path);

      for( int i = 0; i < files.length; i++ )
        if( files[i].contains(".tml") )
          total++;

      System.out.println(total + " files");
      for( String file : files ) {
        if( file.contains(".tml") && !file.endsWith("~") ) {
//        if( file.contains("wsj_0575") ) {
          if( count > -1 ) {
            System.err.println("Processing: " + file + " (" + count + " of " + total + ")");
            processXML(path + File.separator + file, infoFile);
          }
          count++;
//          if( count > 14 ) return;
        }
      }
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  /**
   * Takes the commmand line arguments and the starting index of the first
   * file to parse.
   */
  public void processXML(String timebankFile, InfoFile infoFile) {
    // These two declarations are just for pretty printing parse trees...
    PrintWriter pwOut = _parser.getOp().tlpParams.pw();
    TreePrint treePrint = _parser.getTreePrint();
    //	TreePrint treePrint = new TreePrint("penn");
    List<TextEvent> allEvents = new ArrayList<TextEvent>();

    // PARSE the input XML document of events
    Document doc = TimebankUtil.getXMLDoc(timebankFile);
    if( doc == null ) {
      System.err.println("Woah couldn't read doc " + timebankFile);
      return;
    }
    NodeList sentences = doc.getElementsByTagName("s");

    _makeInstances.clear();
    extractMakeInstances(doc);

    // LOOP over each sentence
    int start = 0, stop = sentences.getLength();
    if( _onesentence > -1 ) {
      stop = _onesentence+1;
      start = _onesentence;
    }

    for( int i = start; i < stop; i++ ) {
      Node s = sentences.item(i);

      // Root sentences should be an Element
      if(s.getNodeType() == Node.ELEMENT_NODE){
        List<TextEvent> localEvents = new ArrayList<TextEvent>();
        List<Timex> localTimex = new ArrayList<Timex>();
        String text = processSentence((Element)s, localEvents, localTimex, i);

        // Make sure there is only one sentence.
        List<List<HasWord>> textsentences = Ling.getSentencesFromText(text);
        if( textsentences.size() > 1 ) {
          System.out.println("ERROR: too many sentences, expected only one! " + text);
          for( List<HasWord> sent : textsentences ) System.out.println("   -- " + sent);
//          System.exit(-1);
        }
        List<HasWord> sentence = textsentences.get(0);
        // Combine sentences because we think this should be one sentence (TimeBank splits them for us).
        for( int xx = 1; xx < textsentences.size(); xx++ ) sentence.addAll(textsentences.get(xx));
        
        // PARSE the sentence
        Tree ansTree = _parser.parseTree(sentence);
        if( ansTree == null )
          System.out.println("Sentence " + i + " failed to parse.");
        else {
          if( _parserPrint ) {
            treePrint.printTree(ansTree, Integer.toString(i), pwOut);
          }

          // Save to InfoFile
          // Build a StringWriter, print the tree to it, then save the string
          StringWriter treeStrWriter = new StringWriter();
          TreePrint tp = new TreePrint("penn");
          tp.printTree(ansTree, new PrintWriter(treeStrWriter,true));

          // Create the dependency tree - CAUTION: DESTRUCTIVE to parse tree
          String depString = "";
          try {
            GrammaticalStructure gs = _gsf.newGrammaticalStructure(ansTree);
            List<TypedDependency> deps = gs.typedDependenciesCCprocessed(true);
            for( TypedDependency dep : deps )
              depString += dep + "\n";
          } catch( Exception ex ) { 
            ex.printStackTrace();
            System.out.println("ERROR: dependency tree creation failed...");
            System.exit(-1);
          }
            
          // Add makeinstance information to events
          for( TextEvent ev : localEvents ) {
            Element temp = _makeInstances.get(ev.id());
            if( temp == null ) System.err.println("ERROR: no event found at " + ev.id());
            ev.saveFeats(temp);
          }

          infoFile.addSentence(timebankFile, i, text, treeStrWriter.toString(), depString.trim(), localEvents, localTimex);
        }
        allEvents.addAll(localEvents);
      }
    }

    // Turn the mode back to full mode, extract tlinks in full mode
//    TLink.MODE DESIRED_MODE = TLink.currentMode;
//    TLink.changeMode(TLink.MODE.FULL);

    // compute closure over the tlinks
    List<TLink> tlinks = extractTlinks(doc, allEvents);
    for( TLink link : tlinks ) link.setRelationToOneDirection();
    Closure closure = new Closure();
    closure.computeClosure(tlinks);

    // Create a reverse EIID to EID lookup table.
    Map<String,String> eiidToID = TimebankUtil.eiidToID(allEvents);

    // add NONE links
    Collection<TLink> nones = closure.addNoneLinks(tlinks, allEvents, eiidToID);
    tlinks.addAll(nones);

    // convert back to the desired mode
//    for( TLink link : tlinks )  link.changeFullMode(DESIRED_MODE);
//    TLink.changeMode(DESIRED_MODE);

    infoFile.addTlinks(timebankFile, tlinks);

    // Get the document creation time
    System.out.println("Extracting creation time...");
    Vector<Timex> created = extractCreationTime(doc);
    for( Timex create : created ) {
      infoFile.addCreationTime(timebankFile,create);
      System.out.println("Adding " + create);
    }
    if( created.size() == 0 ) {
      System.out.println("NO CREATION FOUND!");
      //      System.exit(1);
    }
  }


  /**
   * Parse the XML sentence, save the Events, and return the original text sentence.
   * Stores the positions of each Entity word in the global array.  Also saves each
   * Event in the global array, as well as the passed localEvents parameter.
   * @param s An Element from the XML containing a single sentence
   * @param localEvents A vector in which to store the Events
   * @param localTimex A vector in which to store the TIMEX tags
   * @returns Vanilla string of text from the XML, all tags removed
   */
  public String processSentence(Element s, List<TextEvent> localEvents, List<Timex> localTimex, int sid) {
    List<Word> words;
    int loc = 1; // Stanford Parser counts tokens from 1, not 0
    String text = "";

    String str_sid = s.getAttribute("i");
//    System.out.println("\n\n----------------------------------------");
    if( str_sid.length() > 0 ) System.out.println("Parsing sid = " + str_sid);
    else System.out.println("Parsing sid = " + sid);

    // Each object in the sentence
    NodeList children = s.getChildNodes();
    for( int j = 0; j < children.getLength(); j++ ) {
      Node child = children.item(j);
      Vector<Node> stuff = new Vector<Node>();
      stuff.add(child);

      while( !stuff.isEmpty() ) {
        child = stuff.remove(0);

        // If child is a tagged node
        if( child.getNodeType() == Node.ELEMENT_NODE ) {
          Element el = (Element)child;

          // Save the event strings
          if( el.getTagName().equals("EVENT") ) {
            //			System.out.println(el.getFirstChild());
            TextEvent event = new TextEvent(el.getAttribute("eid"),sid,loc,el);
            localEvents.add(event);
            words = getWords(el);
            for( Word word : words ) {
              text = text + " " + word;
            }

            // RARE: There are nested EVENT tags...we take the innermost
            // ... sometimes 3 are nested!
            while( el.getFirstChild() instanceof Element &&
                ((Element)el.getFirstChild()).getTagName().equalsIgnoreCase("EVENT") ) {
              el = (Element)el.getFirstChild();
              // save the nested event
              event = new TextEvent(el.getAttribute("eid"),sid,loc,el);
              localEvents.add(event);
            }

            loc = loc + words.size();
          }
          // Save the entity tags
          else if( el.getTagName().equals("ENAMEX") ) {
//            System.out.println("PRE-ENAMEX text=" + text);
            String id = el.getAttribute("ID");
            words = getWords(el);
            int k = 0;
            // pad the entities entries for each word
            for( Word word : words ) {
              text = text + " " + word;
              k++;
            }
            // Check for EVENTs inside the ENAMEX...
            findEventInEnamex(el,localEvents,loc,sid);
            loc = loc + words.size();
//            System.out.println("POST-ENAMEX text=" + text);
          }
          // Save TIMEX tags
          else if( el.getTagName().equals("TIMEX3") ) {
            Timex timex = new Timex();
            words = getWords(el);
            timex.setSpan(loc, loc + words.size());
            timex.saveAttributes(el);
            localTimex.add(timex);

            String thetime = "";
            for( Word word : words ) thetime += " " + word;
            timex.setText(thetime.trim());
            text += thetime;
            loc += words.size();
          }
          else if( el.getTagName().equals("SIGNAL") || el.getTagName().equals("CARDINAL") ||
              el.getTagName().equals("NUMEX") ) {
            
            // Sometimes the NUMEX contains an event (a state like "30% full").
            if( el.getFirstChild() instanceof Element &&
                ((Element)el.getFirstChild()).getTagName().equalsIgnoreCase("EVENT") ) {
              Element tempel = (Element)el.getFirstChild();
              // save the nested event
              TextEvent event = new TextEvent(tempel.getAttribute("eid"),sid,loc,tempel);
              localEvents.add(event);
              System.out.println("NEW: added numex event: " + event);
            }
            
            words = getWords(el);
            for( Word word : words ) text = text + " " + word;
            loc = loc + words.size();
          }
          else {//if( sub.equals("NG") || sub.equals("VG") || sub.equals("JG") || sub.equals("PG") ) {
            // add all the children to the vector
            NodeList nodes = el.getChildNodes();
            for( int k = 0; k < nodes.getLength(); k++ ) stuff.add(nodes.item(k));
          }
        }

        // Else child is plain text
        else if( child.getNodeType() == Node.TEXT_NODE ) {
          String str = ((Text)child).getData();

          if( !str.matches("\\s+") ) {
            words = Ling.getWordsFromString(str);
            // pad the entities entries for each word
            for( Word word : words ) {
              text = text + " " + word;
              loc++;
            }
          }
        }
      }
    }
    //    System.out.println("text: " + text);
    //    text = text.replaceAll("\\.\\s*\\.\\s*\\.", "...");
    //    System.out.println("now : " + text);
    return text;
  } 

  /**
   * @returns The text of all text node leafs appended together
   */
  public static String stringFromElement(Node node) {
    if( node instanceof Text ) {
      return ((Text)node).getData().trim();
    } else {
      String str = "";
      NodeList list = node.getChildNodes();
      for( int i = 0; i < list.getLength(); i++ ) {
        if( i == 0 ) str = stringFromElement(list.item(i));
        else str += " " + stringFromElement(list.item(i));
      }
      return str;
    }
  }
  
  public static String removeNewlines(String str) {
    if( str.indexOf('\n') > -1 )
      str = str.replaceAll("\\s+", " ");
    return str;
  }
  
  public static List<Word> getWords(Element el) {
    String str = stringFromElement(el);
    // Some elements have newlines in them, and we don't want these :)
    str = removeNewlines(str);
    List<Word> words = Ling.getWordsFromString(str);
    
    // The Stanford tokenizer sometimes adds a period where it thinks a sentence ends, particularly
    // if something like "Corp." was the last token, then it will change this to "Corp. ."
    // We want to remove that period since it is not in the actual text.
    if( words.size() > 1 && 
        words.get(words.size()-1).value().equals(".") &&
        words.get(words.size()-2).value().endsWith(".") ) {
//      System.out.println("getWords() removing period from: " + words);
      words.remove(words.size()-1);
//      System.out.println("\t-> " + words);
    }
        
//    System.out.println("getWords(Element) str = " + str);
    return words;
  }


  /**
   * Searches an ENAMEX XML Element for nested EVENT Elements to save
   */
  public void findEventInEnamex(Element enamex, List<TextEvent> localEvents, int loc, int sid) {
    NodeList children = enamex.getChildNodes();
    for( int j = 0; j < children.getLength(); j++ ) {
      Node child = children.item(j);
      if( child.getNodeType() == Node.ELEMENT_NODE ) {
        Element el = (Element)child;
        // Save the EVENT
        if( el.getTagName().equals("EVENT") ) {
          int numwords = 0;
          // Count all the words that appear before the EVENT
          for( int k = 0; k < j; k++ ) {
            List<Word> words = getWords(el);
            numwords += words.size();
          }
          // Save the EVENT
          TextEvent event = new TextEvent(el.getAttribute("eid"),sid,loc+numwords,el);
          localEvents.add(event);
          //		    events.put(el.getAttribute("eid"), event);
        }
      }
    }
  }


  /** 
   * Save the mapping from event IDs to tlink event IDs
   */
  private void extractMakeInstances(Document doc) {
    //	Document doc = getXMLDoc(timebankFile);
    NodeList ms = doc.getElementsByTagName("MAKEINSTANCE");

    for( int j = 0; j < ms.getLength(); j++ ) {
      Element child = (Element)ms.item(j);
      // <MAKEINSTANCE aspect="NONE" eiid="ei223" tense="FUTURE" eventID="e1" />
      _makeInstances.put(child.getAttribute("eventID"),child);
    }
  }

  /**
   * Read all the TLINKs from the XML (event-event, event-time, time-time)
   */
  private Vector<TLink> extractTlinks(Document doc, List<TextEvent> docEvents) {
    //	Document doc = getXMLDoc(timebankFile);
    NodeList links = doc.getElementsByTagName("TLINK");
    Vector<TLink> tlinks = new Vector<TLink>();

    // Create a reverse EIID to EID lookup table.
    Map<String,String> eiidToID = TimebankUtil.eiidToID(docEvents);
    
    System.out.println("Found tlinks " + links.getLength());
    for( int j = 0; j < links.getLength(); j++ ) {
      Element child = (Element)links.item(j);

      TLink link = elementToTLink(child, eiidToID);
      if( link != null )
        tlinks.add(link);
    }

    return tlinks;
  }

  public static TLink elementToTLink(Element el) {
    return elementToTLink(el, null);
  }
  
  /**
   * Take an XML Element from a TimeML formatted xml file and create a TLink object.
   * If an EIID to ID map is given, then it creates TLink objects using the main IDs and not
   * the annoying EIIDs in TimeBank (the TempEval contests only use main IDs). If the map
   * is null, then TLinks just use the given Element IDs.
   * @param el The xml element.
   * @param eiidToID Map from TimeBank's specific event instance ids to the main event IDs.
   * @return The TLink object, or null if it is a different format than expected.
   */
  public static TLink elementToTLink(Element el, Map<String,String> eiidToID) {
    if( el == null ) return null;
    
    String eventInstance = null, relatedToEvent = null, time = null, relatedToTime = null;
    String relType = el.getAttribute("relType");
    
    // Read the event IDs and time references. 
    if( el.hasAttribute("eventInstanceID") )
      eventInstance = el.getAttribute("eventInstanceID");
    if( el.hasAttribute("relatedToEventInstance") )
      relatedToEvent = el.getAttribute("relatedToEventInstance");
    if( el.hasAttribute("timeID") )
      time = el.getAttribute("timeID");
    if( el.hasAttribute("relatedToTime") )
      relatedToTime = el.getAttribute("relatedToTime");
    // TempEval attributes
    if( el.hasAttribute("eventID") )
      eventInstance = el.getAttribute("eventID");
    if( el.hasAttribute("relatedToEvent") )
      relatedToEvent = el.getAttribute("relatedToEvent");
//    System.out.println("e" + eventInstance + " r" + relatedToEvent + " t" + time + " rt" + relatedToTime);
    
    // Check that we have event objects for these event IDs.
    if( eiidToID != null && eventInstance != null ) {
      String id = eiidToID.get(eventInstance);
      if( id == null ) {
        System.out.println("ERROR: unknown EIID " + eventInstance + " has no map to ID: " + el);
        //System.exit(-1);
      }
      eventInstance = id;
    }
    if( eiidToID != null && relatedToEvent != null ) {
      String id = eiidToID.get(relatedToEvent);
      if( id == null ) {
        System.out.println("ERROR: unknown EIID " + relatedToEvent + " has no map to ID: " + el);
        //System.exit(-1);
      }
      relatedToEvent = id;
    }
    
    // Create the correct type of TLink object.
    if( eventInstance != null ) {
      if( relatedToEvent != null )
        return new EventEventLink(eventInstance, relatedToEvent, relType);
      else if( relatedToTime != null )
        return new EventTimeLink(eventInstance, relatedToTime, relType);
      else
        System.out.println("ERROR: tlink " + el + " does not have the goods! " + eventInstance);
    } 
    else if( time != null ) {
      System.out.println("time found");
      if( relatedToEvent != null ) {
        System.out.println("relatedEvent found");
        return new EventTimeLink(time, relatedToEvent, relType);
      }
      else if( relatedToTime != null ) {
        System.out.println("relatedTime found");
        return new TimeTimeLink(time, relatedToTime, relType);
      }
      else
        System.out.println("ERROR: time tlink " + el + " " + time + " does not have the goods!");
    } 
    else
      System.out.println("ERROR: tlink does not have eventInstanceID or timeID! " + el);
    
    // Shouldn't reach here if it was a valid TLink.
    return null;
  }

  /**
   * @desc Pull out the document creation time.  Timebank is so inconsistent, we search SIX
   *       different tags to find it...
   * @return A list of creation times.  Yes, timebank sometimes has two different ones.
   */
  private Vector<Timex> extractCreationTime(Document doc) {
    Vector<Timex> localTimex = new Vector<Timex>();
    String tags[] = {"DATE_TIME", "DOCNO", "DD", "FILEID", "DATELINE", "DATE"};

    for( int x = 0; x < tags.length; x++ ) {
      NodeList els = doc.getElementsByTagName(tags[x]);

      if( els.getLength() > 0 ) {
        for( int i = 0; i < els.getLength(); i++ ) {
          Element child = (Element)els.item(i);
          NodeList times = child.getElementsByTagName("TIMEX3");

          for( int j = 0; j < times.getLength(); j++ ) {
            Element el = (Element)times.item(j);
            Timex timex = new Timex();
            List<Word> words = getWords(el);
            timex.setSpan(0, words.size()); // there's really no span to monitor here...
            timex.setText(""); // there's also no text...
            timex.saveAttributes(el);
            localTimex.add(timex);
          }
        }
      }
    }

    return localTimex;
  }



  public static void main(String[] args) {
    if( args.length < 1 ) System.err.println("No input file given");
    else {
      // CHOOSE THE TAGSET
      //      TLink.changeMode(TLink.REDUCED_MODE);
      //      TLink.changeMode(TLink.FULL_MODE);
      TLink.changeMode(TLink.MODE.TEMPEVAL);

      TimebankParser tb = new TimebankParser(args);
      tb.processInput();
    }
  }
}
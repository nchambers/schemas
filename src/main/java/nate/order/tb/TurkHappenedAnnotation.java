package nate.order.tb;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nate.Pair;

/**
 * This class reads an input file created by my script (turkhappened-to-timebank.pl) which reads in 
 * a .csv file that the JHU students created as the output from Turk. The .csv file contains
 * all of the original information, but this function takes my single text file that
 * reconstructed the events and their Turk labels (happened or not).
 * 
 * Event labels are represented as a Pair object: event ID and the label
 */
public class TurkHappenedAnnotation {
  Map<String,List<Pair<String,String>>> _docEvents;
  
  public TurkHappenedAnnotation(String path) {
    fromFile(path);
  }

  /**
   * Get all of the events in the Turkers' annotations for a single TimeBank document.
   * An event is a pair, where the first String is the event ID and the second is the happened/not-happened label.
   * @param docname The document name in TimeBank.
   * @return The list of events for that document, each with a happened/not-happened label.
   */
  public List<Pair<String,String>> getEvents(String docname) {
    System.out.println("(Turk) getEvents with " + _docEvents.size() + " docs from doc " + docname);
    String docbase = docname;
    if( docbase.endsWith(".tml") )
      docbase = docbase.substring(0, docname.lastIndexOf(".tml"));
    if( docbase.endsWith(".xml") )
      docbase = docbase.substring(0, docname.lastIndexOf(".xml"));
    if( docbase.endsWith(".tml") )
      docbase = docbase.substring(0, docname.lastIndexOf(".tml"));
    if( docbase.endsWith(".xml") )
      docbase = docbase.substring(0, docname.lastIndexOf(".xml"));
    
    System.out.println("basename = " + docbase);
    return _docEvents.get(docbase);
  }

  /**
   * Read my Turker's file format into memory, creating event pairs. A pair is (1) event ID and (2) its label.
   * @param path The path to the Turk annotation file.
   */
  public void fromFile(String path) {
    System.out.println("Loading Turkers' happened-event data from " + path);
    _docEvents = new HashMap<String,List<Pair<String,String>>>();
    
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;
      while ((line = in.readLine()) != null) {
        if( !line.startsWith("#") ) {
          String[] parts = line.split("\\s+");
          
          // Create the document's list if need be.
          List<Pair<String,String>> docevents = _docEvents.get(parts[0]);
          if( docevents == null ) {
            docevents = new ArrayList<Pair<String,String>>();
            _docEvents.put(parts[0], docevents);
          }

          // Add the event to the document's list.
          Pair<String,String> labeledEvent = new Pair<String,String>(parts[1], parts[2]);
          docevents.add(labeledEvent);          
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);      
    }
  }
}

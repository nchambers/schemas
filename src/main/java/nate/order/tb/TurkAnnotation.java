package nate.order.tb;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class reads an input file created by my script (turk-to-timebank.pl) which reads in 
 * a .csv file that the JHU students created as the output from Turk. The .csv file contains
 * all of the original information, but this function takes my single text file that
 * reconstructed the event pair for each label. 
 */
public class TurkAnnotation {
  Map<String,List<TLink>> _docTLinks;
  
  public TurkAnnotation(String path) {
    fromFile(path);
  }

  /**
   * Get all of the TLinks in the Turkers' annotations for a single TimeBank document.
   * @param docname The document name in TimeBank.
   * @return The list of TLinks for that document.
   */
  public List<TLink> getTLinks(String docname) {
    System.out.println("(Turk) getTLinks with " + _docTLinks.size() + " docs");
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
    return _docTLinks.get(docbase);
  }

  /**
   * Read my Turker's file format into memory, creating TLink objects.
   * @param path The path to the Turk annotation file.
   */
  public void fromFile(String path) {
    System.out.println("Loading Turkers' data from " + path);
    _docTLinks = new HashMap<String,List<TLink>>();
    
    try {
      BufferedReader in = new BufferedReader(new FileReader(path));
      String line;
      while ((line = in.readLine()) != null) {
        if( !line.startsWith("#") ) {
          String[] parts = line.split("\\s+");
          TLink tlink = new TLink(parts[1], parts[2], parts[3]);
          
          // Add the new link to the document's list.
          List<TLink> doclinks = _docTLinks.get(parts[0]);
          if( doclinks == null ) {
            doclinks = new ArrayList<TLink>();
            _docTLinks.put(parts[0], doclinks);
          }
          doclinks.add(tlink);          
        }
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);      
    }
  }
}

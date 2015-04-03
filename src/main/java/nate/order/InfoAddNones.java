package nate.order;

import java.io.File;

import java.util.List;
import java.util.Vector;

import nate.order.tb.InfoFile;
import nate.order.tb.TLink;
import nate.order.tb.EventEventLink;


/**
 * This class takes a current InfoFile and adds extra NONE tlinks to it.
 * The NONE links are all combos of events in pairwise sentences.
 * -- The assumption is that annotators of Timebank tagged all relevant
 *    tlinks in adjacent sentences.  This probably isn't true, but it's
 *    the best we can do to get accurate NONE labels.
 */
public class InfoAddNones {
  InfoFile infoFile = new InfoFile();
  public static String outputfile = "new-withnones.info.xml";
  public static String outputAllFile = "new-withnones-all.info.xml";

  
  public InfoAddNones(String args[]) {
    String infoPath = args[0];
    init(infoPath);
  }

  private void init(String infoPath) {
    System.out.println("Processing info file " + infoPath);
    infoFile.readFromFile(new File(infoPath));
  }

  /**
   * Add NONE tlinks to the infofile
   * Only add event pairs that are in neighboring or the same sentences
   */
  public void addNonesNeighbors() {
    // Loop over the files
    for(String filename : infoFile.getFiles() ) {

      // Get the file's sentences and tlinks
      List<Sentence> sentences = infoFile.getSentences(filename);
      Vector<TLink> tlinks = infoFile.getTlinks(filename);

      for( int i = 0; i < sentences.size()-1; i++ ) {
        Sentence s1 = sentences.get(i);
        Sentence s2 = sentences.get(i+1);

        // Add none links within the sentence
        //	if( isTlink(tlinks)
        Vector<TextEvent> all = new Vector(s1.events());	
        all.addAll(s2.events());
        addLinks(filename, all, tlinks);
      }
    }
  }


  /**
   * Add NONE tlinks to the infofile
   * Add all possible tlinks
   */
  public void addNonesAllPossible() {
    // Loop over the files
    for(String filename : infoFile.getFiles() ) {

      // Get the file's sentences and tlinks
      List<Sentence> sentences = infoFile.getSentences(filename);
      Vector<TLink> tlinks = infoFile.getTlinks(filename);

      // Create a vector of all events
      Vector<TextEvent> all = new Vector();	
      for( int i = 0; i < sentences.size()-1; i++ ) {
        Sentence s = sentences.get(i);
        all.addAll(s.events());
      }

      // Add all NONE links
      addLinks(filename, all, tlinks);
    }
  }


  /**
   * Look up all combination of events, add missing tlinks
   */
  private void addLinks(String filename, Vector<TextEvent> events, Vector<TLink> tlinks) {
    Vector newlinks = new Vector();

    for( int i = 0; i < events.size()-1; i++ ) {
      TextEvent e1 = events.elementAt(i);
      for( int j = i+1; j < events.size(); j++ ) {
	TextEvent e2 = events.elementAt(j);
	// new link!  add as a NONE relation
	if( !islink(tlinks, e1.eiid(), e2.eiid()) ) {
	  newlinks.add(new EventEventLink(e1.eiid(), e2.eiid(), "none"));
	}
      }
    }
    // Add them
    System.out.println("Adding " + newlinks.size() + " links to " + filename);
    infoFile.addTlinks(filename, newlinks);	    
  }


  /**
   * @return True if the id1-id2 or id2-id1 tlink exists
   */
  private boolean islink(Vector<TLink> tlinks, String id1, String id2) {
    for( TLink link : tlinks ) {
      //      System.out.println("link = " + link);
      if( link.event1().equals(id1) && link.event2().equals(id2) )
	return true;
      if( link.event1().equals(id2) && link.event2().equals(id1) )
	return true;
    }
    return false;
  }


  /**
   * Save the new infofile to a real file
   */
  public void toFile(String path) {
    infoFile.writeToFile(new File(path));
  }


  public static void main(String[] args) {
    if( args.length < 1 ) System.err.println("No input file given");
    else {
      InfoAddNones inf = new InfoAddNones(args);
      inf.addNonesNeighbors();
      inf.toFile(InfoAddNones.outputfile);
      inf = new InfoAddNones(args);
      inf.addNonesAllPossible();
      inf.toFile(InfoAddNones.outputAllFile);
    }
  }
}

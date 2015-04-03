package nate.order;


import nate.order.tb.InfoFile;
import nate.order.tb.TLink;

import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import java.util.*;
import java.lang.String;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;


/**
 * This class reads an InfoFile, and then computes closure over it.
 * A new output file is created: new-closed-events.info
 */
public class InfoClose {
  InfoFile info;
  public static String OUTPUT_PATH = "new-closed-events.info";


  public InfoClose(String infoFilePath) {
    info = new InfoFile();
    info.readFromFile(new File(infoFilePath));
  }

  /**
   * Compute closure over all the TLinks in the InfoFile
   */
  public void computeClosure() {

    // compute closure over all the tlinks
    for( String file : info.getFiles() ) {

      // Turn the mode back to full mode, extract tlinks in full mode
      TLink.MODE DESIRED_MODE = TLink.currentMode;
      TLink.changeMode(TLink.MODE.FULL);

      // Duplicate the vector of tlinks
      Vector<TLink> tlinks = info.getTlinks(file);
      Vector<TLink> origLinks = new Vector(tlinks);
      int presize = tlinks.size();

      // Compute the closure
      Closure closure = new Closure();
      closure.computeClosure(tlinks);
      System.out.println("Closure expanded " + presize + " links to " + tlinks.size());

      // now add the new ones
      Vector<TLink> newlinks = new Vector();
      for( TLink link : tlinks ) {
        if( !origLinks.contains(link) )
          newlinks.add(link);
      }
      info.addTlinks(file, newlinks);

      // convert back to the desired mode
      for( TLink link : tlinks )  link.changeFullMode(DESIRED_MODE);
      TLink.changeMode(DESIRED_MODE);
    }
  }

  /**
   * Save the infofile to disk
   */
  public void writeToFile(String outpath) {
    info.writeToFile(new File(outpath));
  }

  
  /**
   * This main function is only here to print out automatic time-time links!
   */
  public static void main(String[] args) {
    if( args.length < 1 ) System.err.println("No input file given");
    else {
      InfoClose infoClose = new InfoClose(args[0]);
      infoClose.computeClosure();

      infoClose.writeToFile(InfoClose.OUTPUT_PATH);
    }
  }
}

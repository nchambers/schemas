package nate.order;


import nate.order.tb.InfoFile;
import nate.order.tb.TLink;
import java.util.*;
import java.lang.String;
import java.io.File;


/**
 * This class reads an InfoFile, and then flips all TLinks to a new mode.
 * It assumes the InfoFile is in FULL_MODE !!!
 *
 * A new output file is created: new-mode-events.info
 */
public class InfoChangeMode {
  InfoFile info;
  public static String OUTPUT_PATH = "new-mode-events.info";
  TLink.MODE DESIRED_MODE = TLink.MODE.BEFORE; // default mode


  public InfoChangeMode(String mode, String infoFilePath) {
    info = new InfoFile();
    info.readFromFile(new File(infoFilePath));

    // Figure out what mode is wanted
    if( mode.equalsIgnoreCase("before") ) DESIRED_MODE = TLink.MODE.BEFORE;
    else if( mode.startsWith("sym") ) DESIRED_MODE = TLink.MODE.SYMMETRY;
    else if( mode.startsWith("fullsym") ) DESIRED_MODE = TLink.MODE.FULLSYMMETRY;
    else if( mode.startsWith("basic") ) DESIRED_MODE = TLink.MODE.BASIC;
    else if( mode.startsWith("reduced") ) DESIRED_MODE = TLink.MODE.REDUCED;
  }


  /**
   * Compute closure over all the TLinks in the InfoFile
   */
  public void changeMode() {
    // Loop over each document in the InfoFile
    for( String file : info.getFiles() ) {
      // Get the tlinks
      Vector<TLink> tlinks = info.getTlinks(file);
      // Convert to the desired mode
      for( TLink link : tlinks )  link.changeFullMode(DESIRED_MODE);

      // Delete current links
      info.deleteTlinks(file);

      // Add changed mode links
      info.addTlinks(file, tlinks);
    }
  }

  /**
   * Save the infofile to disk
   */
  public void writeToFile(String outpath) {
    info.writeToFile(new File(outpath));
  }

  
  public static void main(String[] args) {
    if( args.length < 2 ) {
      System.err.println("[before|sym] <infofile>");
    }
    else {
      InfoChangeMode infoChangeMode = new InfoChangeMode(args[0], args[1]);
      infoChangeMode.changeMode();

      infoChangeMode.writeToFile(InfoChangeMode.OUTPUT_PATH);
    }
  }
}

package nate;

import java.util.Vector;


/**
 * Reads a parsed file of documents. The parses probably came from running
 * GigawordParser over a Gigaword file.
 */
public class GigawordProcessed extends GigawordHandler {

  public GigawordProcessed(String filename) {
    initialize(filename);
  }


  public Vector<String> nextStory() {
    return super.nextStory(true);
  }
}
package nate.narrative;

import java.util.Collection;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;


/**
 * Convenience class that holds a bunch of Narrative Schemas.
 * It can read them all in from a text file.
 */
public class NarrativeDB {
  private Vector<Narrative> db;
  //  private Map<String,Vector<Integer>> verbLookup;
  private Map<String,Vector<Narrative>> verbLookup;

  NarrativeDB(String filename) {
    this();
    loadFile(filename);
  }

  NarrativeDB() {
    db = new Vector();
    verbLookup = new HashMap();
  }

  public Vector<Narrative> narratives() {
    return db;
  }

  /**
   * @return A Vector of indices of narratives that contain this verb.
   */
  //  public Vector<Integer> narrativesWithVerb(String verb) {
  public Collection<Narrative> narrativesWithVerb(String verb) {
    return verbLookup.get(verb);
  }

  /**
   * Read a bunch of narratives from a single file.
   * It assumes each new narrative starts with a line: "score=...."
   * Chains must follow on subsequent lines: "[ event-s event2-o ... ]"
   */
  public void loadFile(String filename) {
    BufferedReader in;
    String line;
    String datum = null;
    int index = 0;

    try {
      in = new BufferedReader(new FileReader(filename));
      while( (line = in.readLine()) != null ) {
	// Reset the datum with a new narrative string.
	if( line.startsWith("score=") ) {
	  // Create the last narrative from the string.
	  if( datum != null ) {
	    //	    System.out.println("String for new schema: " + datum);
	    Narrative schema = Narrative.fromString(datum);
	    db.add(schema);

	    // Index the verbs
	    for( String verb : schema.verbs() ) {
	      //	    Vector<Integer> indices;
	      Vector<Narrative> indices;
	      if( verbLookup.containsKey(verb) )
		indices = verbLookup.get(verb);
	      else {
		indices = new Vector();
		verbLookup.put(verb, indices);
	      }
	      //	    indices.add(index);
	      indices.add(schema);
	    }
	  }

	  // Start a new narrative.
	  datum = line;
	  index++;
	}
	// Continue this datum.
	else if( datum != null )
	  datum += "\n" + line;
      }
    } catch( Exception ex ) { ex.printStackTrace(); System.exit(1); }
  }
  
}

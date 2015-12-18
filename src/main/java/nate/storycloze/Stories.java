package nate.storycloze;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 * Holds a list of Stories (a story is a list of ordered sentences in a story).
 * Reads stories from an excel dump, one story per line.
 *
 * LINE FORMAT: storyID, workerID, storyTitle, sent0, sent1, ..., sent4
 * 
 */
public class Stories {
  private List<Story> stories;

  /**
   * Constructors
   */
  public Stories() { }
  
  public Stories(String filepath) {
    readStories(filepath);
  }

  /**
   * 
   * @param path
   */
  public void readStories(String path) {
    // Initialize global stories list.
    stories = new ArrayList<>();
    
    //
    // Read one story per line from Excel dump: 
    // LINE FORMAT: storyID, workerID, storyTitle, sent0, sent1, ..., sent4
    //
    BufferedReader reader;
    try {
      reader = new BufferedReader(new FileReader(path));
      String line;
      while( (line = reader.readLine()) != null ) {
        String[] parts = line.split("\t");
        if( parts.length != 7 )
          System.err.println("ERROR: line not 7 parts: " + line);
        else {
          List<String> lines = new ArrayList<>(5);
          for( int i = 0; i < 5; i++ )
            lines.add(parts[i+3]);
          Story story = new Story(lines);
          stories.add(story);
        }
      }
      reader.close();
    } catch( IOException ex ) { ex.printStackTrace(); }
  }
  
  
  public List<Story> getStories() { return stories; }
  
}

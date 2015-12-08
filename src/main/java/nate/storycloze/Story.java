package nate.storycloze;

import java.util.List;
import java.util.ArrayList;

/**
 * Holds a list of ordered sentences in a story.
 */
public class Story {
  private List<String> rawsents;

  /**
   * Constructors
   */
  public Story(List<String> sents) {
    rawsents = new ArrayList<>(sents);
  }

  public List<String> getSentences() { return rawsents; }
  
}

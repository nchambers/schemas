package nate.storycloze;

import java.util.List;
import java.util.ArrayList;

/**
 * Holds a list of ordered sentences in a story, but also
 * a list of possible endings for the story. The test is to guess
 * which one of the endings is the correct ending.
 */
public class StoryClozeTest {
  private List<String> rawsents;
  private List<String> possibleEndings;
  private int correctEnding = 0;

  /**
   * Give 4 story sentences, list of possible endings, and the correct ending's index in the list.
   */
  public StoryClozeTest(List<String> sents, List<String> endings, int correctEnding) {
    this.rawsents = new ArrayList<>(sents);
    this.possibleEndings = new ArrayList<>(endings);
    this.correctEnding = correctEnding;
  }

  public List<String> getStorySentences() {
    return rawsents;
  }
  
  public int numEndings() {
    return possibleEndings.size();
  }
  
  public String getEnding(int index) { 
    return possibleEndings.get(index);
  }
  
  public boolean isCorrect(int guessIndex) {
    return guessIndex == correctEnding;
  }  
}

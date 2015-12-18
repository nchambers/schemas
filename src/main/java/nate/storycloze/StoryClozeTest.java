package nate.storycloze;

import java.util.List;
import java.util.ArrayList;

import nate.reading.ProcessedDocument;

/**
 * Holds a list of ordered sentences in a story, but also
 * a list of possible endings for the story. The test is to guess
 * which one of the endings is the correct ending.
 */
public class StoryClozeTest {
  private List<String> rawsents;
  private List<String> possibleEndings;
  private int correctEnding = 0; // index in the possibleEndings list of the correct answer

  public ProcessedDocument option0;
  public ProcessedDocument option1;

  
  public StoryClozeTest(ProcessedDocument doc0, ProcessedDocument doc1) {
    if( doc0 != null && doc1 != null ) {
      option0 = doc0;
      option1 = doc1;
      if( doc0.storyname.endsWith("correct") && doc1.storyname.endsWith("incorrect") )
        correctEnding = 0;
      else
        correctEnding = 1;
    }
    else
      System.err.println("ERROR in StoryClozeTest, null docs.");
  }
  
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

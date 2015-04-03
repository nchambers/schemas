package nate;

import java.util.Vector;


public interface DocumentHandler {

  public void initialize(String filename);

  public Vector<String> nextStory();
  public Vector<String> nextStory(boolean keepLineBreaks);

  public boolean validFilename(String filename);
  public String currentStory();
  public int numDocs();
  public int currentDoc();
}

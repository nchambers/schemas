package nate.util;

public class WordPosition {
  public int sentIndex = 0;
  public int wordIndex = 1;
  public String token;

  public WordPosition(int sid, int wid, String t) {
    sentIndex = sid;
    wordIndex = wid;
    token = t;
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(token);
    sb.append(' ');
    sb.append(sentIndex);
    sb.append(' ');
    sb.append(wordIndex);
    return sb.toString();
  }
}

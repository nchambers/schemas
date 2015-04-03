package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nate.util.SortableScore;

/**
 * Class that holds the tokens in a single cluster, as well as their individual scores
 * within that cluster.
 * @author chambers
 *
 */
public class ReadingCluster implements Comparable<ReadingCluster> {
  Map<String,Double> _tokenScores;
  List<String> _orderedByScore; // the tokens in the cluster, but ordered by score.
  double _score; // overall clusters score.
  int _id; // a unique ID for this cluster
  
  public ReadingCluster() {
  }

  public ReadingCluster(int id, Map<String,Double> scores) {
    _id = id;
    setTokenScores(scores);
  }
  
  /**
   * Constructs an object that clones the other's token score list.
   * Sets a new score.
   */
  public ReadingCluster(double score, ReadingCluster other) {
    _score = score;
    _id = other.getID();
    setTokenScores(other.getTokenScores());
  }
  
  /**
   * Constructs an object that clones the other's token score list.
   */
  public ReadingCluster(ReadingCluster other) {
    _score = other.score();
    _id = other.getID();
    setTokenScores(other.getTokenScores());
  }
  
  /**
   * Uses the global token scores and sorts the key by their scores. Saves
   * the order in the token list _orderedByScore.
   */
  private void setTheTokenOrder() {
    if( _orderedByScore == null )
      _orderedByScore = new ArrayList<String>();
    else
      _orderedByScore.clear();

    SortableScore[] sorted = new SortableScore[_tokenScores.size()];
    int i = 0;
    for( String token : _tokenScores.keySet() )
      sorted[i++] = new SortableScore(_tokenScores.get(token), token);
    Arrays.sort(sorted);
    
    for( SortableScore scored : sorted )
      _orderedByScore.add(scored.key());
  }
  
  public void setClusterScore(double score) {
    _score = score;
  }
  
  public void setTokenScore(String token, double score) {
    if( _tokenScores == null )
      _tokenScores = new HashMap<String, Double>();
    _tokenScores.put(token, score);
    setTheTokenOrder();
  }
  
  public void setTokenScores(Map<String,Double> scores) {
    _tokenScores = scores;
    if( _tokenScores != null )
      setTheTokenOrder();
  }
  
  public List<String> getTokens() {
    if( _orderedByScore == null )
      return null;
    else
      return _orderedByScore;
  }
  
  public Map<String,Double> getTokenScores() {
    return _tokenScores;
  }

  public double getTokenScore(String token) {
    if( _tokenScores == null ) return 0.0;
    else {
      Double score = _tokenScores.get(token);
      if( score == null) return 0.0;
      else return score;
    }
  }
  
  public void remove(String token) {
    _tokenScores.remove(token);
    _orderedByScore.remove(token);
  }
  
  public boolean contains(String token) {
    if( _tokenScores == null )
      return false;
    else
      return _tokenScores.containsKey(token);
  }
  
  public double score() {
    return _score;
  }
  
  public void setID(int id) {
    _id = id;
  }
  
  public int getID() {
    return _id;
  }
  
  public int size() {
    return getTokens().size();
  }
  
  /**
   * Reads the output string of a cluster back into memory.
   */
  public static ReadingCluster fromString(String str) {
    ReadingCluster cluster = new ReadingCluster();
    
    try {
      int idpos = str.indexOf("id=");
      int space = str.indexOf(" ", idpos);
      cluster.setID(Integer.valueOf(str.substring(idpos+3, space)));
      
      int scorepos = str.indexOf("score=");
      space = str.indexOf(" ", scorepos);
      cluster.setClusterScore(Double.valueOf(str.substring(scorepos+6, space)));
      
      int brace = str.indexOf("[ ");
      String parts[] = str.substring(brace+2).split("\\s+");
      for( int i = 0; i < parts.length; i += 2 ) {
        if( parts[i].equals("...") || parts[i].equals("]") )
          break;
        cluster.setTokenScore(parts[i], Double.valueOf(parts[i+1]));
      }
    } catch (NumberFormatException e) {
      e.printStackTrace();
      System.exit(-1);
    }
    
//    System.out.println("cluster = " + cluster);
    return cluster;
  }
  
  public String toString() {
    String str = "id=" + _id;
    str += " score=" + _score;
    
    if( _tokenScores == null ) return str + " [ ]";
    
    str += " [";
    Collection<String> tokens = _orderedByScore;
    if( tokens == null ) tokens = _tokenScores.keySet();
    int i = 0;
    for( String token : tokens ) {
      str += " " + token + " " + _tokenScores.get(token);
      if( i++ > 25 ) {
        str += " ...";
        break;
      }
    }
    str += " ]";
    return str;
  }

  // INTERFACE for Comparable.
  public int compareTo(ReadingCluster b) {
    if( b == null ) return -1;
    if( _score < ((ReadingCluster)b).score() ) return 1;
    else if( ((ReadingCluster)b).score() > _score ) return -1;
    else return 0;
  }
}

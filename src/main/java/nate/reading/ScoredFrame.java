package nate.reading;


public class ScoredFrame implements Comparable<ScoredFrame> {
  double _score;
  double _maxProb = -999;   // best single token probability
  int _numTokensScored = 0; // the number of tokens used to calculate the score.
  Frame _frame;
  
  public ScoredFrame(double score, Frame frame, double maxprob) {
    _score = score;
    _frame = frame;
    _maxProb = maxprob;
  }

  public ScoredFrame(double score, Frame frame) {
    _score = score;
    _frame = frame;
  }

  public void setScore(double score) {
    _score = score;
  }
  public void setMaxProb(double prob) {
    _maxProb = prob;
  }
  public void setNumTokensScored(int num) {
    _numTokensScored = num;
  }
  
  public Frame frame() { return _frame; }
  public double score() { return _score; }
  public double maxProb() { return _maxProb; }
  public int numTokensScored() { return _numTokensScored; }
  
  public int compareTo(ScoredFrame b) {
    if( b == null ) return -1;
    if( _score < ((ScoredFrame)b).score() ) return 1;
    else if( ((ScoredFrame)b).score() > _score ) return -1;
    else return 0;
  }
  
  public String toString() {
    return _score + " " + _frame.toString();
  }
}

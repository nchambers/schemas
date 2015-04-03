package nate.reading;

import java.util.Set;

import nate.EntityMention;

public class EntityFiller {
  private int _entityID;
  private String _string;
  private double _scoredInRole;
  public EntityMention _mainMention;
  public Set<FrameRole.TYPE> _entityTypes;
  
  private String _matchedPattern = null;    // debugging, the pattern that extracted this entity
  private String _filledMethod = "UNK";     // for debugging, the approach that was used to find this entity
  private boolean _isCorrect = false;       // debugging
  private boolean _isFalsePositive = false; // debugging
  
  
  public EntityFiller(int entityID, String bestDescription, double scoredInRole, EntityMention mentionFilled, Set<FrameRole.TYPE> entityTypes,
      String fillType, String pattern) {
    _entityID = entityID;
    _string = bestDescription;
    _scoredInRole = scoredInRole;
    _mainMention = mentionFilled;
    _entityTypes = entityTypes;
    _filledMethod = fillType;
    _matchedPattern = pattern;
  }

  public void setCorrect(boolean value){
    _isCorrect = value;
  }
  public void setFalsePositive(boolean value) {
    _isFalsePositive = value;
  }
  
  public int id() { return _entityID; }
  public String string() { return _string; }
  public double score() { return _scoredInRole; }
  public String filledMethod() { return _filledMethod; }
  public String matchedPattern() { return _matchedPattern; }
  public boolean isCorrect() { return _isCorrect; }
  public boolean isFalsePositive() { return _isFalsePositive; }

  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(_entityID + " " + _string);
    sb.append(' ');
    sb.append(String.format("%.1f", _scoredInRole));
    sb.append(" '");
    sb.append(_mainMention);
    sb.append("'");
    return sb.toString();
  }
}

package nate.reading;

import java.util.LinkedList;
import java.util.List;

/**
 * An object that holds a list of names or descriptions that represent
 * this entity.  There is also a function to ask if a description matches
 * one for this entity.
 */
public class MUCEntity {
  String _templateType; // The type of MUC template (e.g. kidnap, bombing)
  String _slotType; // The MUC slot name.
  List<String> _mentions = null;
  boolean _optional = false; // If true, this entity is from an optional template.

  public MUCEntity(String templateType) {
    _templateType = templateType;
  }

  public boolean isOptional() {
    return _optional;
  }
  
  public void setOptional(boolean opt) {
    _optional = opt;
  }
  
  public void addMention(String mention) {
    if( _mentions == null )
      _mentions = new LinkedList<String>();
    _mentions.add(mention);
  }

  public List<String> getMentions() {
    return _mentions;
  }
  
  public String getTemplateType() { return _templateType; }

  public boolean equals(Object obj) {
    if( this == obj ) return true;
    if( !(obj instanceof MUCEntity) ) return false;
    MUCEntity other = (MUCEntity)obj;

    if( other.getMentions().size() != _mentions.size() )
      return false;

    for( String mention : other.getMentions() ) {
      if( !this._mentions.contains(mention) )
        return false;
    }
    return true;
  }

  public void setType(String t) { _slotType = t; }
  public String type() { return _slotType; }

  public String toString() {
    String str = null;
    if( _mentions != null ) {
      for( String mention : _mentions ) {
        if( str == null ) str = mention;
        else str += " -- " + mention;
      }
      if( _optional ) str += "(opt)";
    }
    return str;
  }
}

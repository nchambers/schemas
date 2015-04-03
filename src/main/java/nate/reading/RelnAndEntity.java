package nate.reading;

import nate.EntityMention;


public class RelnAndEntity {
  public String original; // v-kidnap#o#person
  public String token;    // v-kidnap
  public String reln;     // s
  //    public int entityID;
  public EntityMention entityMention;
  
  public RelnAndEntity(String o, String t, String r, EntityMention men) {
    original = o;
    token = t;
    reln = r;
    entityMention = men;
  }
  
  public String original() { return original; }
  
  public String governor() { return token; }
  
  public String govAndReln() { return token + "-" + reln; }
  
  public String toString() {
    return token + "-" + reln + " " + entityMention;
  }
}

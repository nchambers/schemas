package nate.schemas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nate.EntityMention;
import nate.NERSpan;

/**
 * This is a data structure to represent an entity in text, made up of
 * entity mentions. This class stores the mentions.
 *
 */
public class TextEntity {
  public static enum TYPE { PERSON, ORG, LOCATION, EVENT, PHYSOBJECT, TIME, OTHER };
  public int entityID;
  
  // These two lists should always line up, the token at index i should be
  // with the dependency relation at index i.
  public List<EntityMention> mentions;
  public List<String> rawTokens;
  public List<String> tokens; // usually lemmatized
  public List<String> deps;
  public List<NERSpan.TYPE> ners; // NE label from the NER system per mention.
  public Set<TYPE> types; // Top-level entity types for this entity (person, physobj, location, etc).

  private String coreToken;    // represents the entity, lemmatized, not a pronoun, but the core word
  private String coreTokenRaw; // represents the entity, not a pronoun, but the core word
  private Set<Integer> labels = null; // The assigned role/slot/topic if inference ran on this entity
  
  public TextEntity() {
    mentions  = new ArrayList<EntityMention>();
    rawTokens = new ArrayList<String>();
    tokens    = new ArrayList<String>();
    deps      = new ArrayList<String>();
    ners      = new ArrayList<NERSpan.TYPE>();
  }
  
  public TextEntity(int id) {
  	this();
  	entityID = id;
  }

  public void setEntityTypes(Set<TYPE> types) {
    this.types = types; 
  }
  
  public void setCoreToken(String coreLemma, String coreRaw) {
    coreToken = coreLemma;
    coreTokenRaw = coreRaw;
  }

  public String getCoreToken() { return coreToken; }
  public String getCoreTokenRaw() { return coreTokenRaw; }

  // Label functions.
  public void clearLabels() { if( labels != null ) labels.clear(); }
  public boolean hasLabel(int label) { return (labels != null && labels.contains(label)); }
  public boolean hasALabel() { return (labels != null && labels.size() > 0); }
  public Set<Integer> getLabels() { return labels; }
  public void addLabel(int label) {
  	if( labels == null ) labels = new HashSet<Integer>();
  	labels.add(label); 
  }
  
  public void addMention(EntityMention mention, String rawtoken, String lemmatoken, String dep, NERSpan.TYPE ner) {
    mentions.add(mention);
    rawTokens.add(rawtoken);
    tokens.add(lemmatoken);
    deps.add(dep);
    ners.add(ner);
  }
  
  /**
   * Remove the ith mention from this entity.
   * @param index The ith mention we want to remove.
   */
  public void removeMention(int index) {
    if( index >= numMentions() ) {
      System.out.println("ERROR: attempt to remove mention " + index + " with only " + numMentions() + " mentions.");
      System.exit(-1);
    }
    rawTokens.remove(index);
    tokens.remove(index);
    deps.remove(index);
    ners.remove(index);
  }
  
  public EntityMention getMention(int index) {
    return mentions.get(index);
  }
  
  public String getMentionToken(int index) {
    return tokens.get(index);
  }
  
  public String getMentionDependency(int index) {
    return deps.get(index);
  }
  
  public int numMentions() {
    if( tokens == null )
      return 0;
    else
      return tokens.size();    
  }
  
  /**
   * @return Summary string for the entity, only the main parts.
   */
  public String toString() {
    StringBuffer buf = new StringBuffer("{ ");
    if( tokens != null ) {
      buf.append(coreToken + " (");
      for( TextEntity.TYPE type : types )
        buf.append(" " + type);
      buf.append(" ): ");
      for( int ii = 0; ii < tokens.size(); ii++ ) {
        buf.append(tokens.get(ii) + "/" + deps.get(ii) + " ");
      }
    }
    buf.append("}");
    return buf.toString();
  }
  
  /**
   * @return Full detailed string version of all data in this entity.
   */
  public String toFullString() {
    StringBuffer buf = new StringBuffer("");
    if( tokens != null ) {
      buf.append(coreToken + "\t\t");
      buf.append(coreTokenRaw + "\t\t");
      if( labels == null || labels.size() == 0 )
      	buf.append("null\t\t");
      else {
      	for( Integer ii : labels )  /// NOT ACTUALLY TESTED YET!
      		buf.append(ii + "\t");
      	buf.append("\t\t");
      }
      for( String str : rawTokens )
        buf.append(str + "\t");
      buf.append("\t"); // double tabs, given previous for loop's ending tab
      for( String str : tokens )
        buf.append(str + "\t");
      buf.append("\t");
      for( String str : deps )
        buf.append(str + "\t");
      buf.append("\t");
      for( NERSpan.TYPE type : ners )
        buf.append(type.toString() + "\t");
      buf.append("\t");
      for( TYPE type : types )
        buf.append(type.toString() + "\t");
    }
    return buf.toString();
  }
  
  /**
   * Build an entity from the string version created by toFullString().
   * @param str The string from toFullString()
   * @return The new TextEntity object.
   */
  public static TextEntity fromFullString(String str) {
//    System.out.println("TextEntity.fromFullString\t" + str);
    String parts[] = str.split("\t\t");
    
    // Core tokens.
    TextEntity entity = new TextEntity();
    entity.setCoreToken(parts[0], parts[1]);
    
    if( !parts[2].equalsIgnoreCase("null") ) {
    	String[] labels = parts[2].split("\t");
    	for( String label : labels ) entity.addLabel(Integer.parseInt(label));
    }
    
    List<String> raw = new ArrayList<String>();
    List<String> tokens = new ArrayList<String>();
    List<String> deps = new ArrayList<String>();
    List<NERSpan.TYPE> ners = new ArrayList<NERSpan.TYPE>();
    Set<TYPE> types = new HashSet<TYPE>();
        
    String[] bits = parts[3].split("\t");
    for( String bit : bits ) raw.add(bit);

    bits = parts[4].split("\t");
    for( String bit : bits ) tokens.add(bit);

    bits = parts[5].split("\t");
    for( String bit : bits ) deps.add(bit);

    bits = parts[6].split("\t");
    for( String bit : bits ) ners.add(NERSpan.TYPE.valueOf(bit));

    bits = parts[7].split("\t");
    for( String bit : bits ) types.add(TYPE.valueOf(bit));
    entity.setEntityTypes(types);

    for( int ii = 0; ii < raw.size(); ii++ )
      entity.addMention(null, raw.get(ii), tokens.get(ii), deps.get(ii), ners.get(ii));

//    System.out.println("Read entity:\t\t" + entity.toFullString());
//    System.out.println("Read entity:\t\t" + entity.toString());
    return entity;
  }
}

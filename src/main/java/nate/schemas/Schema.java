package nate.schemas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nate.util.TreeOperator;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;


public class Schema implements Serializable {
	static final long serialVersionUID = 1;
  private String docname; // origin of this schema
	private List<Relation> relations;
	private Map<Integer,Set<TextEntity.TYPE>> entityNER;
	private Counter<String> tokenCounts;
	private Set<Integer> sentences; // set of sentence IDs where relations came from
	
	// Constructors
	public Schema() { 
		tokenCounts = new ClassicCounter<String>();
		sentences = new HashSet<Integer>();
	}
	
  public Schema(List<Relation> rels) {
  	this();
  	relations = rels;
  }

  public void setDocname(String doc) { 
    docname = doc;
  }
  public String getDocname() {
    return docname;
  }
  
  /**
   * Assign a list of NER types to a specific entity ID.
   * @param entityID An entity ID, presumably one that appears in this schema's relations.
   * @param ners A list of NER types.
   */
  public void setNER(int entityID, Set<TextEntity.TYPE> ners) {
    if( entityNER == null )
      entityNER = new HashMap<Integer,Set<TextEntity.TYPE>>();
    entityNER.put(entityID, ners);
  }
  
  public int length() {
    if( relations != null )
      return relations.size();
    else
      return 0;
  }
  
  public void addRelation(Relation rel) {
    if( relations == null )
      relations = new ArrayList<Relation>();
    relations.add(rel);
    
    // Save the word counts in the relation's sentence.
    if( rel.sentenceParse != null && !sentences.contains(rel.sentenceID) ) {
    	sentences.add(rel.sentenceID);
    	
    	// Count all nouns, verbs, adjectives.
    	int ii = 0;
    	List<String> tags = TreeOperator.posTagsFromTree(rel.sentenceParse);
      for( String token : TreeOperator.stringLeavesFromTree(rel.sentenceParse) ) {
        String tag = tags.get(ii);
        if( tag.startsWith("NN") || tag.startsWith("VB") || tag.startsWith("JJ") || tag.startsWith("PRP") ) {
          token = cleanToken(token);
          if( token != null )
            tokenCounts.incrementCount(token);
        }
        ii++;
      }
    	
//      for( String token : TreeOperator.stringLeavesFromTree(rel.sentenceParse) ) {
//    		token = cleanToken(token);
//    		if( token != null )
//    			tokenCounts.incrementCount(token);
//    	}
    }
  }

  public void addRelation(Relation rel, int entityID) {
    if( relations == null )
      relations = new ArrayList<Relation>();
    relations.add(rel);
  }

  public Counter<String> getTokenCounts() {
  	return tokenCounts;
  }
  
  public String cleanToken(String token) {
  	if( token.matches(".*[a-zA-Z].*") )
  		return token.toLowerCase();
  	else
  		return null;
  }
  
  /**
   * @return The first relation found with the given predicate and particle
   */
  public Relation getPredicateRelation(String pred, String particle) {
    if( relations != null ) 
      for( Relation rel : relations )
        if( rel.predicate.equals(pred) && (particle == null || (rel.particle != null && rel.particle.equals(particle))) )
          return rel;
    return null;
  }

  /**
   * Finds all relations in this schema that have an entity ID filling the given pred/arg pair.
   * @param pred
   * @param particle
   * @param arg Either the string 's' or 'o' for subject or object.
   */
  public List<Integer> getEntitiesFillingArg(String pred, String particle, String arg) {
    if( relations != null ) {
      List<Integer> found = new ArrayList<Integer>();
      
      for( Relation rel : relations )
        if( rel.predicate.equals(pred) && (particle == null || (rel.particle != null && rel.particle.equals(particle))) ) {
          if( arg.equals("s") && rel.leftEntityID != null )
            found.add(rel.leftEntityID);
          else if( arg.equals("o") && rel.rightEntityID != null )
            found.add(rel.rightEntityID);
        }

      if( found.size() == 0 ) return null;
      else return found;
    }
    return null;
  }
  
  public List<Relation> getRelationsWithLeftEntityID(int id) {
    if( relations != null ) {
      List<Relation> found = new ArrayList<Relation>();
      for( Relation rel : relations )
        if( rel.leftEntityID != null && rel.leftEntityID.intValue() == id )
          found.add(rel);
      return found;
    }
    return null;
  }

  public List<Relation> getRelationsWithRightEntityID(int id) {
    if( relations != null ) {
//      System.out.println("rightrel = " + relations);
      List<Relation> found = new ArrayList<Relation>();
      for( Relation rel : relations ) {
//        System.out.println("rel = " + rel);
        if( rel.rightEntityID != null && rel.rightEntityID.intValue() == id ) 
          found.add(rel);
      }
      return found;
    }
    return null;
  }

  public int getMaxEntityID() {
    if( relations == null )
      return 0;
    
    int max = 0;
    for( Relation rel : relations ) {
      if( rel.leftEntityID != null && rel.leftEntityID > max )
        max = rel.leftEntityID;
      if( rel.rightEntityID != null && rel.rightEntityID > max )
        max = rel.rightEntityID;
    }
    return max;
  }
  
  public List<Relation> getRelations() { return relations; }
  
  public static Schema fromString(String str) {
    try {
      BufferedReader reader = new BufferedReader(new StringReader(str));
      Schema schema = new Schema();
      
      // Read the first line.
      String line = reader.readLine();
      // If the "--SCHEMA--" header, skip to the next line.
      if( line.startsWith("--SCHEMA") )
        line = reader.readLine();
      
      // If it is a story name, set the schema's origin.
      if( line.startsWith("APW_ENG") || line.startsWith("NYT_ENG") )
        schema.setDocname(line);
      // error
      else {
        System.out.println("Unexpected line in schema fromString: " + line);
        System.exit(1);
      }
      
      // Should be the first entity/event line.
      while( line.startsWith("--SCHEMA") || line.startsWith("APW_ENG") || line.startsWith("NYT_ENG") )
      	line = reader.readLine();
      //System.out.println("line=" + line);
      
      while( line != null ) {
      	
      	// If line isn't the word counts.
      	if( line.charAt(0) != '[' ) {
      		//        System.out.println("line=" + line);
      		String[] parts = line.split("\\s+");

      		// If reading entity NER labels.
      		if( parts[1].matches("^[A-Z]+$") ) {
      			Integer entityID = Integer.parseInt(parts[0]);
      			Set<TextEntity.TYPE> ners = new HashSet<TextEntity.TYPE>(); 
      			for( int ii = 1; ii < parts.length; ii++ )
      				ners.add(TextEntity.TYPE.valueOf(parts[ii]));
      			schema.setNER(entityID, ners);
      		}

      		// Else reading a relation
      		else {
      			String particle = null;
      			String pred = parts[1];

      			if( parts[1].contains("_") ) {
      				int ii = parts[1].indexOf('_');
      				pred = parts[1].substring(0,ii);
      				particle = parts[1].substring(ii+1);
      			}

      			Relation rel = new Relation(-1, -1, pred);
      			if( particle != null ) rel.particle = particle;

      			//          System.out.println("line: " + line);
      			//          System.out.println("parts length = " + parts.length);
      			if( parts.length == 4 )
      				rel.rightDep = "prep_" + parts[2];

      			if( !parts[0].equals("null") )
      				rel.leftEntityID = Integer.parseInt(parts[0]);
      			else 
      				rel.leftEntityID = null;

      			if( !parts[parts.length-1].equals("null") )
      				rel.rightEntityID = Integer.parseInt(parts[parts.length-1]);
      			else 
      				rel.rightEntityID = null;

      			schema.addRelation(rel);
      		}
      	}
      	
      	// Line with counts of tokens e.g., [the=3, a=5, ...]
      	else {
      		Counter<String> counts = new ClassicCounter<String>();
      		
      		line = line.substring(1,line.length()-1); // strip off brackets
      		String[] parts = line.split(", ");
      		for( String part : parts ) {
      			int ei = part.lastIndexOf('=');
      			counts.incrementCount(part.substring(0,ei), Double.valueOf(part.substring(ei+1)));
      		}
      		schema.tokenCounts = counts;
      	}
      	
        line = reader.readLine();
      }
      
      return schema;
    } catch( IOException ex ) { ex.printStackTrace(); }
    return null;
  }
  
  public String toString() {
  	StringBuffer str = new StringBuffer("--SCHEMA--\n");
  	str.append(docname + "\n");
  	if( entityNER != null )
  	  for( Map.Entry<Integer, Set<TextEntity.TYPE>> entry : entityNER.entrySet() ) {
  	    str.append(entry.getKey());
  	    for( TextEntity.TYPE type : entry.getValue() )
  	      str.append("\t" + type);
  	    str.append("\n");
  	  }
  	
  	for( Relation rel : relations )
  		str.append(rel + "\n");
  	
  	str.append(Counters.toString(tokenCounts, 200));
  	str.append("\n");
  	
    return str.toString();
  }

  public String toStringDebug() {
  	StringBuffer str = new StringBuffer("--SCHEMA--\n");
  	for( Relation rel : relations )
  		str.append(rel.toStringDebug() + "\n");
    return str.toString();
  }
}

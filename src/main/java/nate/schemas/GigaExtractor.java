package nate.schemas;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TypedDependency;
import nate.util.Directory;
import nate.util.Ling;
import nate.EntityMention;
import nate.GigawordDuplicates;
import nate.IDFMap;
import nate.NERSpan;
import nate.Pair;
import nate.reading.ProcessedData;
import nate.reading.SlotTypeCache;
import nate.util.Locks;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;

/**
 * This class takes parse trees and dependency graphs, and strips them into just
 * tokens and core dependency relations that can be used later for learning.
 *
 * Main functions: getEntityList(), getCachedEntityList()
 * 
 */
public class GigaExtractor {
  IDFMap generalIDF;
  TreeFactory _tf;
  WordNet _wordnet = null;
  boolean _changeTokensToNELabel = false; // if true, then words that are labeled by NER are changed to that NE label.
  private final String _cacheDir = "cache";
  private final String _cacheGigaDir = "cachegiga";
  private double _minDepCounts = 10; // number of times a dep must be seen
  private int _minDocCounts = 10;    // number of docs a verb must occur in
  public boolean debug = false;

  Set<String> duplicates;
  
  public GigaExtractor(int minDepCount, int minDocCount) {
    this();
    _minDepCounts = minDepCount;
    _minDocCounts = minDocCount;
    System.out.println("GigaExtractor minDepCounts = " + minDepCount);
    System.out.println("GigaExtractor minDocCounts = " + minDocCount);
  }
  
  public GigaExtractor() {
    _tf = new LabeledScoredTreeFactory();
    System.out.println("Loading Wordnet from: " + WordNet.findWordnetPath());
    _wordnet = new WordNet(WordNet.findWordnetPath());
    System.out.println("Loading IDF from: " + IDFMap.findIDFPath());
    generalIDF = new IDFMap(IDFMap.findIDFPath());
    System.out.println("Loading duplicate story names from: duplicates");
    duplicates = GigawordDuplicates.fromFile("duplicates");
  }

  /**
   * Process all of Gigaword ahead of time and save to disk.
   */
  public void processGigaword(String parseDir, String depDir, String entityDir, String nerDir) {
    for( String file : Directory.getFilesSorted(parseDir) ) {
      if( file.startsWith("apw_eng") || file.startsWith("nyt_eng") ) {
        if( !isDuplicate(file) ) {
          String corefile = file.substring(0, 14);
          if( Locks.getLock("simplify-" + corefile) ) {
            System.out.println("Now " + corefile);
            String pPath = parseDir + File.separator + file;
            String dPath = depDir + File.separator + Directory.nearestFile(corefile, depDir);
            String ePath = entityDir + File.separator + Directory.nearestFile(corefile, entityDir);
            String nPath = nerDir + File.separator + Directory.nearestFile(corefile, nerDir);
            ProcessedData data = new ProcessedData(pPath, dPath, ePath, nPath);
            data.nextStory();

            try {
              PrintWriter writer = initializeCache(_cacheGigaDir + File.separator + corefile);
              extractSchemas(data, writer, Integer.MAX_VALUE);
              writer.close();
            } catch( IOException ex ) { ex.printStackTrace(); }

            //          writeToCache(_cacheGigaDir + File.separator + corefile, schemas);
          }
        }
        else System.out.println("Skipping duplicate " + file);
      }
    }
  }
  
  /**
   * Determines if the story is a duplicate story from a previous story. 
   * @param storyname Name of story to check.
   * @return True if the story is a duplicate, false otherwise.
   */
  private boolean isDuplicate(String storyname) {
    int dot = storyname.indexOf('.');
    if( dot > -1 )
      storyname = storyname.substring(0, dot);
    return duplicates.contains(storyname.toUpperCase());
  }
  
  private TextEntity getEntity(int entityID, Collection<TextEntity> entities) {
  	if( entities != null )
  		for( TextEntity entity : entities )
  			if( entity.entityID == entityID )
  				return entity;
  	return null;
  }
  
  /**
   * Takes a given relation, and assumes the relation has entity mentions already set for its
   * left and right arguments. This method takes those mentions, finds their entity objects,
   * and then sets the left/right arguments according to the core head word of the overall entity.
   * @param rel A relation with left/right entity mentions filled in.
   * @param entities All the entities in the document.
   * @return True if at least one argument matches an entity, false otherwise.
   */
  private boolean normalizeArguments(Relation rel, List<TextEntity> entities) {
  	boolean entityMatched = false;
//  	System.out.println("top norm: " + rel);
  	if( rel.leftMention != null ) {
//  	  System.out.println("   left id: " + rel.leftMention.entityID());
			TextEntity entity = getEntity(rel.leftMention.entityID(), entities);
			if( entity != null ) {
//				System.out.println("Changing left arg " + rel.leftArg + " to " + entity.getCoreToken());
				rel.leftArg = entity.getCoreToken();
				entityMatched = true;
			}
			//    		else System.out.println("No entity for: " + rel.leftArg);
		}
		if( rel.rightMention != null ) {
			TextEntity entity = getEntity(rel.rightMention.entityID(), entities);
			if( entity != null ) {
//				System.out.println("Changing right arg " + rel.rightArg + " to " + entity.getCoreToken());
				rel.rightArg = entity.getCoreToken();
				entityMatched = true;
			}
		}
		return entityMatched;
  }
  
  /**
   * Extracts schemas from one document at a time, and immediately writes them using the given
   * printwriter object.
   * @param data The already opened data files.
   * @param writer The writer to print schemas to.
   * @param numDocs The number of documents to process.
   */
  private void extractSchemas(ProcessedData data, PrintWriter writer, int numDocs) {
//    List<Schema> schemas = new ArrayList<Schema>();
    int ii = 0;

    while( ii < numDocs && data.getParseStrings() != null ) {
      if( !duplicates.contains(data.currentStory()) ) {
        System.out.println("doc: " + data.currentStory());

        // Grab all entities
        List<TextEntity> entities = getEntityListCurrentDoc(data);
        //    Set<Integer> bigEntities = new HashSet<Integer>();
        //    for( TextEntity entity : entities ) bigEntities.add(entity.mentions.get(0).entityID());

        //        for( TextEntity entity : entities ) 
        //          System.out.println("ENTITY " + entity.entityID + ": " + entity);

        // Grab all triples (relations) from the doc.
        List<Relation> relations = extractRelations(data);
        //        for( Relation rel : relations )
        //          System.out.println("-r--> " + rel.toStringDebug());

        // Edit relations to use the entity's main head word, not the mention's head word.
        List<Relation> remove = new ArrayList<Relation>();
        for( Relation rel : relations ) {
          boolean entityMatched = normalizeArguments(rel, entities);
          if( !entityMatched ) remove.add(rel);
        }

        // Remove relations that didn't match entities in args.
        for( Relation rel : remove ) {
          //    		System.out.println("Removing no entity matched relation: " + rel);
          relations.remove(rel);
        }

        // Build chains from the relations
        List<Schema> myschemas = splitIntoProtagSchemas(data.currentStory(), relations, entities, 2);
        //        schemas.addAll(myschemas);
        //        System.out.println("Created schemas: " + myschemas);
        for( Schema schema : myschemas )
          writeOneSchema(schema, writer);

        ii++;
      }
      data.nextStory();
      
      if( ii % 1000 == 999 ) Util.reportMemory();
    }
    
//    return schemas;
  }
  
  /**
   * Helper method for splitIntoProtagSchemas.
   * This adds a relation to a schema, using the given entity mention. The mention is either the left or right
   * argument of the given relation. The code is the same for both arguments, so this method saves on 
   * repetitive code.
   * @param rel A relation.
   * @param mention An entity mention from the relation.
   * @param entityToSchema Map from entity IDs to schemas.
   * @param idToEntity Map from entity IDs to entity objects.
   */
  private void addRelationToSchema(Relation rel, EntityMention mention, Map<Integer,Schema> entityToSchema, Map<Integer,TextEntity> idToEntity) {
    Schema schema = entityToSchema.get(mention.entityID());
    if( schema == null ) {
      schema = new Schema();
      entityToSchema.put(mention.entityID(), schema);
    }
    schema.addRelation(rel);
    
    // Grab the NER types from the entity, and assign to the schema.
    if( rel.leftMention != null ) {
      TextEntity entity = idToEntity.get(rel.leftMention.entityID());
      schema.setNER(entity.entityID, entity.types);
    }
    if( rel.rightMention != null ) {
      TextEntity entity = idToEntity.get(rel.rightMention.entityID());
      if( entity == null ) {
        System.out.println("ERROR: can't find entity ID " + rel.rightMention.entityID() + " for relation " + rel);
        System.exit(-1);
      }
      schema.setNER(entity.entityID, entity.types);
    }
  }
  
  /**
   * This method creates sublists of relations from the given list of relations.
   * It splits the list into chunks where a single entity occurs in all of them.
   * For each entity that occurs at least 'minLength' times, build a schema for its relations.
   * @param docname Name of the current document.
   * @param relations A list of relations in order based on token indices.
   * @param entities All the entities from the document.
   * @return A list of schemas.
   */
  public List<Schema> splitIntoProtagSchemas(String docname, List<Relation> relations, List<TextEntity> entities, int minLength) {

    // Find all frequent entities.
    Map<Integer,TextEntity> idToEntity = new HashMap<Integer,TextEntity>();
    Set<Integer> freqEntities = new HashSet<Integer>();
    for( TextEntity entity : entities ) {
      if( entity.numMentions() >= minLength ) {
        freqEntities.add(entity.entityID);
      }
      idToEntity.put(entity.entityID, entity);
    }

    // Build one schema per frequent entity.
    Map<Integer,Schema> entityToSchema = new HashMap<Integer,Schema>();
    // Loop over relations, add to schemas.
    for( Relation rel : relations ) {
      
      if( rel.leftMention != null && freqEntities.contains(rel.leftMention.entityID()) )
        addRelationToSchema(rel, rel.leftMention, entityToSchema, idToEntity);
      if( rel.rightMention != null && freqEntities.contains(rel.rightMention.entityID()) )
        addRelationToSchema(rel, rel.rightMention, entityToSchema, idToEntity);
    }

    // Set NER values for the schema relations.
    List<Schema> schemas = new ArrayList<Schema>();
    
    // Grab schemas from hash map and put in a simple list.
    for( Schema schema : entityToSchema.values() ) {
      if( schema.length() >= minLength ) {
        schema.setDocname(docname);
        schemas.add(schema);
//        System.out.println("Returning schema: " + schema);
      }      
    }    
    
    return schemas;
  }
  
  /**
   * This method creates sublists of relations from the given list of relations.
   * It splits the list at points where no entities before the split point occur below the split.
   * Entity X will have all its relations together. If this means entities Y and Z are also 
   * in the relations of X, then all of Y and Z's relations are also brought in.
   * 
   * @param relations A list of relations in order based on token indices.
   * @return A list of schemas.
   */
  public List<Schema> splitIntoSchemas(List<Relation> relations) {
    List<Schema> schemas = new ArrayList<Schema>();
    int start = 0;
    int end = 0;
    
    while( start < relations.size() ) {

      Set<Integer> entities = new HashSet<Integer>();
      Relation rel = relations.get(start);
      if( rel.leftMention != null )  entities.add(rel.leftMention.entityID());
      if( rel.rightMention != null ) entities.add(rel.rightMention.entityID());

      int newend = end;
      do {
        end = newend;

//        System.out.println("DO start=" + start + " end=" + end);
        
        // Find the farthest relation that contains one of our entities.
        int ii = 0;
        for( Relation rel2 : relations ) {
          if( rel2.leftMention != null && entities.contains(rel2.leftMention.entityID()) )
            newend = ii;
          else if( rel2.rightMention != null && entities.contains(rel2.rightMention.entityID()) )
            newend = ii;
          ii++;
        }

//        System.out.println("  entities=" + entities);
//        System.out.println("  newend=" + newend);
        
        // Grab all entities in relations from start to end.
        for( int jj = start; jj < relations.size() && jj <= newend; jj++ ) {
          Relation rel2 = relations.get(jj);
          if( rel2.leftMention != null )  entities.add(rel2.leftMention.entityID());
          if( rel2.rightMention != null ) entities.add(rel2.rightMention.entityID());
        }
        
//        System.out.println("  bottom entities=" + entities);
      } while( newend != end );
      
      // Build schema.
//      System.out.println("Building schema from " + start + " to " + end);
      Schema schema = new Schema();
      for( int jj = start; jj <= end; jj++ )
        schema.addRelation(relations.get(jj));
//      System.out.println("schema built: " + schema);
      schemas.add(schema);
      
      start = end+1;
      end = start;
    }
    
    return schemas;
  }
  
  /**
   * Extracts relations from the current document in the given data object.
   * Relations are extracted in document order based on predicate token position.
   * @param data An already opened data object.
   * @return A list of relation objects, in order that they appear in the document.
   */
  public List<Relation> extractRelations(ProcessedData data) {
    List<Relation> relations = new ArrayList<Relation>();
    
    List<Tree> trees = TreeOperator.stringsToTrees(data.getParseStrings());
    List<List<TypedDependency>> alldeps = data.getDependencies();
    List<NERSpan> ners = data.getNER();

    if( trees.size() != alldeps.size() ) {
      System.out.println("Tree/Dep size no match in " + data.currentStory() + "(" + trees.size() + " " + alldeps.size());
    }
    
    // Add NER labels to the entity mentions.
    Collection<EntityMention> mentions = data.getEntities();
    addNERToEntities(mentions, ners);

    // Put the mentions in order of their sentences.
    List<EntityMention>[] mentionsBySentence = new ArrayList[trees.size()];
    for( EntityMention mention : mentions ) {
      if( mention.sid() > trees.size() ) {
//        System.out.println("doc: " + data.currentStory());
//        System.out.println("mention: " + mention);
//        System.out.println("num trees: " + trees.size());        
      }
      if( mentionsBySentence[mention.sid()-1] == null )
        mentionsBySentence[mention.sid()-1] = new ArrayList<EntityMention>();
      mentionsBySentence[mention.sid()-1].add(mention);
    }

    // Remove duplicate entity mentions ("the outcome of the fighting" and "the fighting" both have "fighting" as the rightmost)
    // Proper head word detection would fix this, but no time...
    removeDuplicateMentions(mentionsBySentence);
    
    // Step through the sentences. Step through the entity mentions in each sentence.
    int sentid = 0;
    for( Tree tree : trees ) {
      if( mentionsBySentence[sentid] != null ) {
        Collection<TypedDependency> sentdeps = alldeps.get(sentid);
//        System.out.println("sentdeps = " + sentdeps);
//        System.out.println("mentions = " + mentionsBySentence[sentid]);
        
        Map<Integer,TypedDependency> subjects = new HashMap<Integer,TypedDependency>();
        Map<Integer,TypedDependency> objects  = new HashMap<Integer,TypedDependency>();
        Map<Integer,TypedDependency> preps    = new HashMap<Integer,TypedDependency>();
        Set<Integer> predicates = new HashSet<Integer>();
        
    		for( TypedDependency dep : sentdeps ) {
    			String depname = normalizeDep(dep.reln().getShortName());
    			if( depIsImportant(depname) ) {
            if( depname.startsWith("dobj") || depname.equals("nsubjpass") )
              objects.put(dep.gov().index(), dep);
            else if( depname.equals("nsubj") || depname.equals("xsubj") || depname.equals("agent") )
    					subjects.put(dep.gov().index(), dep);
    				predicates.add(dep.gov().index());
    			}
  				else if( dep.reln().getShortName().startsWith("prep") )
  					preps.put(dep.gov().index(), dep);
    		}
    		
    		// Sort predicates by token order.
    		Integer[] predarray = new Integer[predicates.size()];
    		predicates.toArray(predarray);
    		Arrays.sort(predarray);
    		
    		// Create a relation for each predicate.
    		for( Integer predi : predarray ) {
    			TypedDependency leftDep = subjects.get(predi);
    			TypedDependency rightDep = objects.get(predi);
    			if( rightDep == null )
    				rightDep = preps.get(predi);

    			Relation relation = createRelation(sentid, predi, leftDep, rightDep, sentdeps, mentionsBySentence[sentid]);
    			relation.sentenceParse = tree;
    			relations.add(relation);
    		}

      }
      sentid++;
    }
    
    return relations;
  }

  /**
   * A convenience function that creates a relation object from a given predicate token, and two typed
   * dependencies that represent the dependent to the left and right of the predicate.
   * This method builds the predicate, finds the verb's particle if one exists, and finds the entity
   * mentions for the two arguments.
   * @param sid Sentence index in the doc.
   * @param predicateIndex Token index in the sentence.
   * @param leftDep
   * @param rightDep
   * @param sentDeps
   * @param mentions All entity mentions in the doc.
   * @return A single relation instance.
   */
  private Relation createRelation(int sid, int predicateIndex, TypedDependency leftDep, TypedDependency rightDep, Collection<TypedDependency> sentDeps, List<EntityMention> mentions) {
//  	System.out.println("Create relation from: " + leftDep + " and " + rightDep);

  	String predicate = null;
  	String particle = null;

  	// Find the predicate's token and its particle (if there is one).
  	for( TypedDependency dep : sentDeps ) {
  		if( dep.gov().index() == predicateIndex ) {
  			predicate = dep.gov().nodeString();
  			if( dep.reln().toString().equals("prt") )
  				particle = dep.dep().label().value();
  		}
  	}
  	
  	predicate = predicate.toLowerCase();
  		
  	// Create the relation object.
  	Relation rel = new Relation(sid, predicateIndex, predicate);
  	if( leftDep != null ) rel.setLeft(leftDep.dep().index(), normalizeDep(leftDep.reln().toString()), leftDep.dep().value());
  	if( rightDep != null ) rel.setRight(rightDep.dep().index(), normalizeDep(rightDep.reln().toString()), rightDep.dep().value());

  	// Add particle
  	if( particle != null )
  		rel.particle = particle.toLowerCase();

  	// Find the entity mentions for this relation.
  	int lefti  = (leftDep == null  ? -1 : leftDep.dep().index());
  	int righti = (rightDep == null ? -1 : rightDep.dep().index());
  	for( EntityMention mention : mentions ) {
//  	  if( mention.start() <= lefti && mention.end() >= lefti )
  	  if( mention.end() == lefti )
  			rel.setLeftEntity(mention);
//  	  if( mention.start() <= righti && mention.end() >= righti )
  	  if( mention.end() == righti )
  			rel.setRightEntity(mention);
  	}
  	
//  	System.out.println("\t" + rel);
  	return rel;
  }
  
  
  /**
   * Extract all entities from the given documents, and create their list of entity mentions.
   * This returns a list of all entities, with their mentions represented by a TextEntity
   * object that simply contains the head word and the dependency relation in which it is a dependent.
   * @param data
   * @return
   */
  public List<TextEntity> getEntityListCurrentDoc(ProcessedData data) {
    Map<Integer,TextEntity> idToEntity = new HashMap<Integer,TextEntity>();

    List<Tree> trees = TreeOperator.stringsToTrees(data.getParseStrings());
    List<List<TypedDependency>> alldeps = data.getDependencies();
    List<NERSpan> ners = data.getNER();

    if( trees.size() != alldeps.size() ) {
      System.out.println("Tree/Dep size no match in " + data.currentStory() + " (" + trees.size() + " " + alldeps.size() + ")");
      System.out.println("Last tree: " + trees.get(trees.size()-1));
      System.out.println("Last deps: " + alldeps.get(alldeps.size()-1));
    }
    
    // Add NER labels to the entity mentions.
    Collection<EntityMention> mentions = data.getEntities();
    addNERToEntities(mentions, ners);

    // Put the mentions in order of their sentences.
    List<EntityMention>[] mentionsBySentence = new ArrayList[trees.size()];
    for( EntityMention mention : mentions ) {
      if( mention.sid() > trees.size() ) {
        System.out.println("doc: " + data.currentStory());
        System.out.println("mention: " + mention);
        System.out.println("num trees: " + trees.size());        
      }
      if( mentionsBySentence[mention.sid()-1] == null )
        mentionsBySentence[mention.sid()-1] = new ArrayList<EntityMention>();
      mentionsBySentence[mention.sid()-1].add(mention);
    }

    // Remove duplicate entity mentions ("the outcome of the fighting" and "the fighting" both have "fighting" as the rightmost)
    // Proper head word detection would fix this, but no time...
    removeDuplicateMentions(mentionsBySentence);
    
    // Step through the sentences. Step through the entity mentions in each sentence.
    int sentid = 0;
    for( Tree tree : trees ) {
      if( mentionsBySentence[sentid] != null ) {
        List<String> leaves = TreeOperator.stringLeavesFromTree(tree);
        Collection<TypedDependency> sentdeps = alldeps.get(sentid);
        // Each token index has a list of dependencies in which that index was the dependent.
        List<List<String>> sortedDeps = sortDependenciesByDependent(sentdeps, tree);
        
        for( EntityMention mention : mentionsBySentence[sentid] ) {
//          System.out.println("Checking mention: " + mention);
          String leaf = leaves.get(mention.end()-1);
          List<String> deps = sortedDeps.get(mention.end()-1);
          NERSpan.TYPE ner = mention.namedEntity();

          // Token index starts at 1 for normalizing.
          String leaflemma = normalizeLeaf(leaf, mention.end(), tree);

          if( leaflemma.matches("^\\d+$") )
            leaflemma = intToDate(leaflemma, mention.end(), tree);

          if( deps != null ) {
            for( String dep : deps ) {
            	dep = normalizeDep(dep);
            	// Create the entity if this is the first mention.
            	if( !idToEntity.containsKey(mention.entityID()) )
            		idToEntity.put(mention.entityID(), new TextEntity(mention.entityID()));
            	// Add the mention to the entity's list.
            	idToEntity.get(mention.entityID()).addMention(mention, leaf, leaflemma, dep, ner);
            }
          }
        }
      }
      sentid++;
    }

    // Build the list object that holds all the entity objects.
    List<TextEntity> entities = new ArrayList<TextEntity>();
    for( Integer id : idToEntity.keySet() )
    	entities.add(idToEntity.get(id));

    // Set the core mentions for each entity.
    setCoreEntityMentions(entities);

    // Set the top-level word classes that this entity may belong to.
    setTopLevelAttributes(entities);

    /*
    for( TextEntity entity : entities )
    	System.out.println("--> " + entity);

    // Remove mentions that are reporting verbs.
    removeReportingMentions(entities);
    removeCommonMentions(entities);

    for( TextEntity entity : entities )
    	System.out.println("**> " + entity);

    // Remove entities with one mention that are pronouns.
    removePronounEntities(entities);

    for( TextEntity entity : entities )
    	System.out.println("!!> " + entity);

    // Remove entities with one mention that are just numbers (not years).
    removeNumberEntities(entities);
*/
    
    // Remove any entity with only one mention.
   // removeEntitiesWithNMentions(entities, 1);
//    for( TextEntity entity : entities )
//    	System.out.println(">>> " + entity);

    return entities;
  }

  /**
   * Remove all entities that have n or fewer mentions.
   * @param entities List of entity objects.
   */
  private void removeEntitiesWithNMentions(List<TextEntity> entities, int n) {
    List<TextEntity> remove = new ArrayList<TextEntity>();
    for( TextEntity entity : entities ) {
      if( entity.numMentions() <= n )
        remove.add(entity);
    }
    for( TextEntity entity : remove )
      entities.remove(entity);    
  }
  
  /**
   * Sometimes a digit is the day of a month, so we look for a time unit on either
   * side of the given digit in the sentence. If it exists, we return a new string
   * with both tokens appended;
   * @param token
   * @param index
   * @param tree
   * @return
   */
  private String intToDate(String token, int index, Tree tree) {
    if( token.matches("^\\d+$") ) {
//      System.out.println("intToDate " + token);

      String pre = (index > 1 ? TreeOperator.indexToToken(tree, index-1) : null);
      String pre2 = (index > 2 ? TreeOperator.indexToToken(tree, index-2) : null);
      String post = TreeOperator.indexToToken(tree, index+1);

      if( (pre != null && _wordnet.isTime(pre)) || 
          (post != null && _wordnet.isTime(post)) ) {
        if( debug ) System.out.println("Timedate cal rule matched: " + token);
        return "TIMEDATE";
      }

      // e.g. 0800
      if( token.matches("^0\\d\\d\\d$") ) {
        if( debug ) System.out.println("Timedate 0 rule matched: " + token);
        return "TIMEDATE";
      }

      // e.g. 1700 (5pm)
      if( token.length() < 5 && Integer.parseInt(token) <= 2400 ) {
        if( pre != null && (pre.equalsIgnoreCase("at") || pre.equalsIgnoreCase("at")) ) {
          if( debug ) System.out.println("Timedate 'at' rule matched: " + token);
          return "TIMEDATE";
        }
      }        

    }
    return token;
  }
  
  public Pair<List<String>,List<List<TextEntity>>> getCachedEntityList(String path) {
    return getResolvedCachedEntityList(createCachePath(path));
  }
  
  /**
   * This loads the data from a cached file.
   * @param path
   * @return
   */
  public Pair<List<String>,List<List<TextEntity>>> getResolvedCachedEntityList(String cachePath) {
    System.out.println("Reading from cache: " + cachePath);

    if( Directory.fileExists(cachePath) ) {
      try {
        BufferedReader in = new BufferedReader(new FileReader(cachePath));
        String line = in.readLine();

        // Get the doc names from the first line.
        String[] names = line.split("\t");
        List<String> docnames = new ArrayList<String>();
        for( String name : names ) docnames.add(name);

        // Now read the entity names.
        List<List<TextEntity>> docentities = new ArrayList<List<TextEntity>>();
        List<TextEntity> current = null;
        line = in.readLine();
        while( line != null ) {
          // New document
          if( line.startsWith("DOC") ) {
            if( current != null ) docentities.add(current);
            current = new ArrayList<TextEntity>();
          }
          else current.add(TextEntity.fromFullString(line));
          line = in.readLine();
        }
        // Add the final doc, the remainder.
        if( current != null ) docentities.add(current);

        // Return both the doc names and the entities per doc.
        return new Pair<List<String>,List<List<TextEntity>>>(docnames, docentities);
      } catch( Exception ex ) { 
        System.err.println("Error opening cache file: " + cachePath);
        ex.printStackTrace();
      }
    }
    else System.out.println("Cache file not found.");

    return null;
  }

  /**
   * Read the entities from a single document inside the given file. The file is assumed
   * to be moved to an offset that starts at a document. We read entities until the next
   * document is reached or end of file.
   * @param in An already opened file, offset moved to the doc we want.
   * @return The entities in the doc at the given offset.
   */
  public static List<TextEntity> readSingleDocRandomAccess(RandomAccessFile in) {
    try {
      // Read the line with the document name: "DOC APW_ENG_19990304.1234" (ignore it)
      String line = in.readLine();

      // Now read the entities, one per line.
      List<TextEntity> current = new ArrayList<TextEntity>();
      while( (line = in.readLine()) != null ) {
        // New document, stop and return.
        if( line.startsWith("DOC ") )
          return current;
        else 
          current.add(TextEntity.fromFullString(line));
      }
      // Hit the end of the file, return what we have.
      return current;
    } catch( Exception ex ) { 
      System.err.println("Error reading random access file.");
      ex.printStackTrace();
    }
    return null;
  }

  /**
   * Given the path to a file, this creates a new path to a cache directory that contains
   * the cache based on the given file.
   * @param origFile A file or a directory path.
   * @return A new file path, the cache equivalent location to the given path.
   */
  private String createCachePath(String origFile) {
  	String separator = File.separator;
  	if( separator.equals("\\") ) separator = "\\\\";  		

  	// Create the cache directory if it doesn't yet exist.
  	if( !(new File(_cacheDir).exists()) )
  		(new File(_cacheDir)).mkdir();
  	
  	System.out.println("cache path = " + origFile + " (separator=" + File.separator + ")");
    // Remove leading periods.
    while( origFile.charAt(0) == '.' || origFile.charAt(0) == File.separatorChar )
      origFile = origFile.substring(1);
    // Remove trailing periods.
    while( origFile.charAt(origFile.length()-1) == '.' || origFile.charAt(origFile.length()-1) == File.separatorChar )
      origFile = origFile.substring(0,origFile.length()-1);
    // Replace all double slashes
    origFile = origFile.replaceAll(separator + separator, "-");
    // Replace all single slashes
    origFile = origFile.replaceAll(separator, "-");
    origFile = origFile.replaceAll("/", "-");
    origFile = origFile.replaceAll("--+", "-");
    // Replace any other periods.
    origFile = origFile.replaceAll("\\.", "_");
    
    return _cacheDir + File.separator + origFile;
  }

  /**
   * Write all the schemas to a single file.
   * Then each new doc starts with "DOC <docname>" and there is one entity per line.
   * @param cachepath Full path to a file to create and write to.
   * @param schemas List of schemas.
   */
  private void writeToCache(String cachepath, List<Schema> schemas) {
    
    System.out.println("Writing to cache: " + cachepath);

    try {
      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(cachepath)));

      for( Schema oneschema : schemas ) {
        writer.println(oneschema.getDocname());
        writer.print(oneschema + "\n");
      }
      writer.close();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
  
  private PrintWriter initializeCache(String cachepath) throws IOException {
    System.out.println("Writing to cache: " + cachepath);
    PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(cachepath)));
    return writer;
  }
  
  private void writeOneSchema(Schema oneschema, PrintWriter writer) {
  	//writer.println(oneschema.getDocname());
    writer.print(oneschema + "\n");
  }

  /**
   * Sometimes the coref system has two mentions that end on the same token, usually
   * a complex noun phrase subsumes the other. We want to keep the shorter one.
   * @param mentionsBySentence Array of mentions, each cell is a sentence and its mentions.
   */
  private void removeDuplicateMentions(List<EntityMention>[] mentionsBySentence) {
    for( int ii = 0; ii < mentionsBySentence.length; ii++ ) {
      List<EntityMention> mentions = mentionsBySentence[ii];
      Map<Integer,EntityMention> seenIndices = new HashMap<Integer,EntityMention>();
      Set<EntityMention> remove = new HashSet<EntityMention>();

      if( mentions != null ) {
        for( EntityMention current : mentions ) {
          if( !seenIndices.containsKey(current.end()) )
            seenIndices.put(current.end(), current);
          // Two mentions with same rightmost position in the sentence!
          else {
            EntityMention old = seenIndices.get(current.end());
            // Keep the shorter mention (assume the longer one is needlessly long)
            if( old.end() - old.start() > current.end() - current.start() ) {
              seenIndices.remove(old);
              seenIndices.put(current.end(), current);
              remove.add(old);
            } 
            else remove.add(current);
          }
        }
      }

      // Remove the duplicates.
      for( EntityMention mention : remove ) {
        mentions.remove(mention);
        if( debug ) System.out.println("Removing duplicate mention: " + mention);
      }
    }
  }

  
  /**
   * The TextEntitly objects should have NER labels already assigned per mention from
   * the NER system. Thus function looks at those, but also the top-level WordNet synsets
   * of each entity's core token to find possible attributes.
   * The result is an entity with binary attributes of PERSON, PHYSOBJECT, LOCATION, EVENT, and OTHER.
   * 
   * @param entity The entity itself with NER labels already assigned to it.
   */
  private void setTopLevelAttributes(List<TextEntity> entities) {
    for( TextEntity entity : entities ) {
      Set<TextEntity.TYPE> types = validEntityRoleTypes(entity.ners, entity.getCoreToken());
      entity.setEntityTypes(types);
    }
  }

  private void setCoreEntityMentions(List<TextEntity> entities) {
    for( TextEntity entity : entities ) setCoreEntityMention(entity);
  }

  /**
   * Determines which mention of the entity contains the key phrase.
   * This is done solely by mention string length.
   * @param entity
   */
  private void setCoreEntityMention(TextEntity entity) {
    String bestToken = null;
    int mentioni = 0, besti = 0;
    for( String token : entity.tokens ) {
      if( bestToken == null ) {
        bestToken = token;
        besti = mentioni;
      }
      // Take the longer token as the better mention.
      else if( token.length() > bestToken.length() ) {
        // Don't set the longer token if it is a pronoun.
        // (yes, the current best might be a pronoun, but then it doesn't matter which we choose)
        if( !Ling.isPersonPronoun(token) && !Ling.isInanimatePronoun(token) && 
            !Ling.isAbstractPerson(token) && !Ling.isObjectReference(token) ) {
          bestToken = token;
          besti = mentioni;
        }
      }
      mentioni++;
    }

    entity.setCoreToken(bestToken, entity.rawTokens.get(besti));

    if( bestToken.matches("^\\d+$") ) {
      if( debug ) System.out.println("Number turned from " + bestToken);
      entity.setCoreToken("NUMBER", entity.rawTokens.get(besti));
    }

    // Set this entity's token to be an NER label if it has one.
    if( _changeTokensToNELabel ) {
      NERSpan.TYPE bestNER = entity.ners.get(besti); 
      if( bestNER == NERSpan.TYPE.PERSON )
        entity.setCoreToken("PERSON", entity.rawTokens.get(besti));
      else if( bestNER == NERSpan.TYPE.ORGANIZATION )
        entity.setCoreToken("ORG", entity.rawTokens.get(besti));
    }
  }

  /**
   * Markup the entity mentions with the NER label if there is one, based on the
   * rightmost word in the entity mention.
   * @param mentions All the entity mentions in a document.
   * @param ners All the NER spans in a document. 
   */
  private void addNERToEntities(Collection<EntityMention> mentions, List<NERSpan> ners) {
    //    System.out.println("addNERToEntities()");
    for( EntityMention mention : mentions ) {
      int rightmost = mention.end();
      for( NERSpan span : ners ) {
        if( span.sid() == mention.sid()-1 && span.start() <= rightmost && span.end() > rightmost ) {
          //          System.out.println("Adding NER " + span + " to mention " + entity);
          mention.setNamedEntity(span.type());
          break;
        }
      }
    }
  }

  /**
   * Given an entity (and its NE labels), add to the NE labels based on a lookup of its
   * head word in wordnet to see if we can determine if the entity is a person, location,
   * or other role type. We return the set of possible types.
   * @param mentionNELabels The list of all NE labels assigned to mentions by the NER system.
   * @param mainDescriptor The main string description for the entity.
   */
  private Set<TextEntity.TYPE> validEntityRoleTypes(List<NERSpan.TYPE> mentionNELabels, String mainDescriptor) {
    Set<TextEntity.TYPE> validEntityTypes = new HashSet<TextEntity.TYPE>();

    // Get rid of the NONE labels.
    Set<NERSpan.TYPE> labeledNEs = new HashSet<NERSpan.TYPE>();
    for( NERSpan.TYPE ne : mentionNELabels ) {
      if( ne != NERSpan.TYPE.NONE )
        labeledNEs.add(ne);
    }

    String key = mainDescriptor;
    int space = mainDescriptor.lastIndexOf(' ');
    if( space > -1 ) key = mainDescriptor.substring(space+1);

    // TODO: temporary check
    if( key.equalsIgnoreCase("person") || key.equalsIgnoreCase("people") ) {
      validEntityTypes.add(TextEntity.TYPE.PERSON);
      return validEntityTypes;
    }

    if( key.equalsIgnoreCase("TIMEDATE") ) {
      validEntityTypes.add(TextEntity.TYPE.TIME);
      return validEntityTypes;
    }

    if( Ling.isPersonPronoun(key) ) {
      validEntityTypes.add(TextEntity.TYPE.PERSON);
      return validEntityTypes;
    }

    //    System.out.println("valid lookup key " + key + " ners " + labeledNEs);
    //    System.out.println("  isPerson " + SlotTypeCache.isPerson(key, _wordnet));
    //    System.out.println("  isPhysObj " + SlotTypeCache.isPhysObject(key, _wordnet));
    //    System.out.println("  isLocation " + SlotTypeCache.isLocation(key, _wordnet));
    //    System.out.println("  isEvent " + SlotTypeCache.isEvent(key, _wordnet));
    //    System.out.println("  isUnk " + _wordnet.isUnknown(key));
    //    System.out.println("  isOther " + SlotTypeCache.isOther(key, _wordnet));

    if( labeledNEs.contains(NERSpan.TYPE.LOCATION) || SlotTypeCache.isLocation(key, _wordnet) )
      validEntityTypes.add(TextEntity.TYPE.LOCATION);
    if( labeledNEs.contains(NERSpan.TYPE.PERSON) || SlotTypeCache.isPerson(key, _wordnet) )
      validEntityTypes.add(TextEntity.TYPE.PERSON);
    if( labeledNEs.contains(NERSpan.TYPE.ORGANIZATION) )
      validEntityTypes.add(TextEntity.TYPE.ORG);

    // Don't label things events if they had NER tags.
    if( labeledNEs.size() == 0 && SlotTypeCache.isEvent(key, _wordnet) )
      validEntityTypes.add(TextEntity.TYPE.EVENT);

    // Don't label things as physical objects if they had NER tags.
    if( labeledNEs.size() == 0 && SlotTypeCache.isPhysObject(key, _wordnet) )
      validEntityTypes.add(TextEntity.TYPE.PHYSOBJECT);

    // If no attributes yet, and the key contains non a-z characters, just label it OTHER and return.
    if( !key.matches("^[A-Za-z]+$") ) {
      validEntityTypes.add(TextEntity.TYPE.OTHER);
      return validEntityTypes;
    }

    // Unknown words could be people or locations.
    if( validEntityTypes.size() == 0 && _wordnet.isUnknown(key) ) {
      validEntityTypes.add(TextEntity.TYPE.PERSON);
      validEntityTypes.add(TextEntity.TYPE.ORG);
      validEntityTypes.add(TextEntity.TYPE.LOCATION);
      if( debug ) System.out.println("Totally unknown word: " + key + " (now listed as PERSON or ORG or LOCATION)");
    }    

    // Don't label things events if they had NER tags.
    if( labeledNEs.size() == 0 && SlotTypeCache.isTime(key, _wordnet) )
      validEntityTypes.add(TextEntity.TYPE.TIME);

    //    // Physical objects (non-people) are OTHER.
    //    if( _wordnet.isUnknown(key) || _wordnet.isPhysicalObject(key) || (_wordnet.isMaterial(key) && !_wordnet.isTime(key)) )
    //      validEntityTypes.add(TextEntity.TYPE.OTHER);
    //
    //    // Other is also basically anything that isn't a person or location...
    //    else if( SlotTypeCache.isOther(key, _wordnet) && !_wordnet.isTime(key) )
    //      validEntityTypes.add(TextEntity.TYPE.OTHER);

    if( validEntityTypes.size() == 0 )
      validEntityTypes.add(TextEntity.TYPE.OTHER);

    return validEntityTypes;
  }

  /**
   * Lemmatize a token, given its position in a parse tree.
   * @param leaf The original token in the sentence.
   * @param leafIndex The index of the token in the sentence. First word starts at 1, not 0.
   * @param tree The parse tree of the sentence.
   * @return A lemmatized leaf, or the original if lemmatization fails.
   */
  public String normalizeLeaf(String leaf, int leafIndex, Tree tree) {
    leaf = leaf.toLowerCase();

    Tree subtree = TreeOperator.indexToSubtree(tree, leafIndex);
    if( subtree == null ) {
      return leaf;
    }
    String posTag = subtree.label().value();
    String lemma = _wordnet.lemmatizeTaggedWord(leaf, posTag);
//    System.out.println("normalizeLeaf:\t" + leaf + "\t" + posTag + "\t" + lemma);
    return lemma;
  }

  public String normalizeDep(String dep) {
    if( dep == null )
      return null;
    else if( dep.contains("nsubjpass") ) 
      return dep.replaceFirst("nsubjpass--", "dobj--");
    else if( dep.contains("agent--") ) 
      return dep.replaceFirst("agent--", "nsubj--");
    else if( dep.contains("xsubj--") )
      return dep.replaceFirst("xsubj--", "nsubj--");
    else return dep;
  }

  public boolean depIsImportant(String dep) {
    if( dep.startsWith("nsubj") || dep.startsWith("agent") || dep.startsWith("dobj") )
      return true;
    return false;
  }

  public boolean leafIsImportant(String leaf) {
    if( leaf.equals("this") ||
        Ling.isPersonPronoun(leaf) || Ling.isInanimatePronoun(leaf) )
      return false;
    else
      return true;
  }

  /**
   * Put all of the tokens in the document in order in one long list.
   * @param data The parsed document's file path.
   * @return A list of tokens.
   */
  public List<String> getTokens(ProcessedData data) {
    List<String> tokens = new ArrayList<String>();

    List<String> parseStrings = data.getParseStrings();
    for( String parseString : parseStrings ) {
      Tree tree = TreeOperator.stringToTree(parseString, _tf);
      List<String> leaves = TreeOperator.stringLeavesFromTree(tree);
      for( String leaf : leaves )
        tokens.add(leaf.toLowerCase());
    }

    return tokens;
  }

  /**
   * This looks at the token indices, and finds a dependency relation for each index.
   * It then stringifies these, and returns them in order of index from low to high.
   * The list is as long as the sentence, with null entries for token indices that had
   * no such relation.
   * @param deps The dependencies of the sentence.
   * @param tree  The parse tree of the sentence.
   * @return A list the size of the sentence, and each index contains a list of dependencies of the token
   *         in that index position.
   */
  private List<List<TypedDependency>> sortDependenciesByDependentIndex(Collection<TypedDependency> deps, Tree tree) {
    List<String> tokens = TreeOperator.stringLeavesFromTree(tree);
    int numTokens = tokens.size();

    System.out.println("numTokens = " + numTokens);
    
    List<List<TypedDependency>> ordered = new ArrayList<List<TypedDependency>>(numTokens);
    for( int i = 0; i < numTokens; i++ ) ordered.add(null);
    
    for( TypedDependency dep : deps ) {
    	int depi = dep.dep().index();
    	if( ordered.get(depi) == null )
    		ordered.set(depi, new ArrayList<TypedDependency>());
    	ordered.get(depi).add(dep);
    }

    return ordered;
  }
    
  
  /**
   * This looks at the token indices, and finds a dependency relation for each index.
   * It then stringifies these, and returns them in order of index from low to high.
   * The list is as long as the sentence, with null entries for token indices that had
   * no such relation.
   * @param deps The dependencies of the sentence.
   * @param tokens The tokens of the sentence, pulled from the phrase structure tree.
   * @param tree  The parse tree of the sentence.
   * @return
   */
  private List<List<String>> sortDependenciesByDependent(Collection<TypedDependency> deps, Tree tree) {
    List<String> tokens = TreeOperator.stringLeavesFromTree(tree);
    int numTokens = tokens.size();

    List<List<String>> ordered = new ArrayList<List<String>>();
    for( int ii = 0; ii < numTokens; ii++ ) {
      // Get the relation. Dependency tokens are indexed from 1, not 0.
      List<String> relations = getRelationForDependent(deps, ii+1, tokens.get(ii), tree);
      ordered.add(relations);
    }

    // Add nulls till the sentence ends.
    while( ordered.size() < numTokens )  ordered.add(null);

    return ordered;
  }

  /**
   * Given a token index in the sentence, find the dependency relation that has it as a dependent.
   * Generate a string representing the relation: reln--governor
   *
   *
   * TODO: some tokens might be the dependent of multiple governors (conjunctions, in particular).
   *       This function does nothing for those and only choose the first one!!!  Do something else in the future??
   *       e.g. <D>dobj laying-20 weapons-23</D>
   *            <D>nsubjpass destroyed-29 weapons-23</D>
   *       e.g. Saad, announced, today, that, a, group, of, Colombians, dressed, as, military, men, on, 28, April, Kidnapped, U.S., citizen, Scott, heyndal, ,, killed, a, Colombian, ,, and, wounded, an, Ecuadorean...
   *            
   * @param deps  The sentence's dependency graph.
   * @param index The target token's index.
   * @param token The token itself (for sanity checking the index).
   * @param tree  The parse tree of the sentence.
   * @return A String "feature" of the dependency relation: reln--governor
   */
  private List<String> getRelationForDependent(Collection<TypedDependency> deps, int index, String token, Tree tree) {
    List<String> bestDeps = null;
    for( TypedDependency dep : deps ) {
      if( dep.dep().index() == index ) {
        // Sanity check that the dependency's token is the parse tree's token.
        // Rare: 1979 is the token, (1979) is the dependency token.
        if( !dep.dep().nodeString().equals(token) && !dep.dep().nodeString().contains(token) ) {
          System.out.println("Token positions don't line up. token=" + token + " and dep=" + dep);
          System.exit(-1);
        }

        // Lemmatize and lowercase the governor.
        String governor = dep.gov().nodeString();
        governor = normalizeLeaf(governor, dep.gov().index(), tree);

        // Look for particles.
        Map<Integer,String> particles = Ling.particlesInSentence(deps);
        if( particles.containsKey(dep.gov().index()) ) {
          governor += "_" + particles.get(dep.gov().index());
          //          System.out.println("PARTICLE: " + governor);
        }          

        // Build the feature string and return it.
        if( bestDeps == null ) {
          bestDeps = new ArrayList<String>();
          bestDeps.add(dep.reln() + "--" + governor);
        }
        // If there is a tmod, just add this one tmod and don't add anything else.
        else if( dep.reln().toString().equals("tmod") ) {
          bestDeps.clear();
          bestDeps.add("tmod--" + governor);
          break;
        }
        else {
          bestDeps.add(dep.reln() + "--" + governor);
        }
      }
    }
    return bestDeps;
  }

  /**
   * True if the given token is the lemma of a reporting verb. Assumes lowercase input.
   * @param token The token to check.
   * @return True if a reporting verb, false otherwise.
   */
  public static boolean isReportingVerbLemma(String token) {
    if( token.equals("say") || token.equals("report") || token.equals("reply") || token.equals("tell") || 
    		token.equals("talk") || token.equals("add") )
      return true;
    else
      return false;
  }

  /**
   * True if the given token is the lemma of a reporting verb. Assumes lowercase input.
   * @param token The token to check.
   * @return True if a reporting verb, false otherwise.
   */
  public static boolean isCommonVerbLemma(String token) {
    if( token.equals("be") || token.equals("have") || token.equals("do") )
      return true;
    else
      return false;
  }

  
  /**
   * For pre-processing Gigaword only.
   */
  public static void main(String[] args) {
    String parseDir = null;
    String depDir = null;
    String entityDir = null;
    String nerDir = null;
    
    String baseDir = "/home/nchamber/data/gigaword/charniak-based/apw_eng";
    if( !Directory.fileExists("/home/nchamber/data/gigaword/charniak-based/apw_eng") )
    	baseDir = "C:\\cygwin\\home\\sammy\\data\\gigaword\\charniak-based\\apw_eng";
    
    // data and lore
    if( Directory.fileExists(baseDir) ) {
      parseDir = baseDir + "/phrase";
      depDir = baseDir + "/deps";
      nerDir = baseDir + "/ner";
      //entityDir = "/home/nchamber/data/gigaword/charniak-based/apw_eng/corefOpennlp";
      entityDir = baseDir + "/corefStanford";
    }
    else System.out.println("ERROR: couldn't find data directories. Hardcode it in the code.");
    
    GigaExtractor simp = new GigaExtractor();
    simp.processGigaword(parseDir, depDir, entityDir, nerDir);
  }
}

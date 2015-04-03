package nate.schemas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nate.util.Util;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;

public class GeneralCluster {
  double mincutoff = 1.0; // when to stop adding relations to a schema
  
  public void generalizeSchemas(List<Schema> schemas) {
    Counter<String> preds = new ClassicCounter<String>();
    Counter<RelationPair> pairCounts = new ClassicCounter<RelationPair>();

    // Make basic event counts.
    for( Schema schema : schemas ) {
      List<Relation> relations = schema.getRelations();
      for( int i = 0; i < relations.size()-1; i++ ) {
        Relation rel = relations.get(i);
        preds.incrementCount(rel.predicate + (rel.particle != null ? " " + rel.particle : ""));
        for( int j = i+1; j < relations.size(); j++ ) {
          Relation rel2 = relations.get(j);

          // Put in alphabetical order.
          if( rel.predicate.compareTo(rel2.predicate) >= 0 ) {
            Relation temp = rel;
            rel = rel2;
            rel2 = temp;
          }
          
          if( rel.leftEntityID == rel2.leftEntityID )
            pairCounts.incrementCount(new RelationPair(rel.predicate, "s", rel2.predicate, "s"));
//            pairs.incrementCount("X " + rel.predicate + " -- X " + rel2.predicate);
          if( rel.leftEntityID == rel2.rightEntityID )
            pairCounts.incrementCount(new RelationPair(rel.predicate, "s", rel2.predicate, "o"));
//            pairs.incrementCount("X " + rel.predicate + " -- " + rel2.predicate + " X");
          if( rel.rightEntityID == rel2.leftEntityID )
            pairCounts.incrementCount(new RelationPair(rel.predicate, "o", rel2.predicate, "s"));
//            pairs.incrementCount(rel.predicate + " X -- X " + rel2.predicate);
          if( rel.rightEntityID == rel2.rightEntityID )
            pairCounts.incrementCount(new RelationPair(rel.predicate, "o", rel2.predicate, "o"));
//            pairs.incrementCount(rel.predicate + " X -- " + rel2.predicate + " X");
        }
      }
    }

//    Counters.normalize(preds);

    System.out.println("PREDICATES");
    for( String pred : Util.sortCounterKeys(preds) )
      if( preds.getCount(pred) > 2 )
        System.out.printf("%s\t%.3f\n", pred, preds.getCount(pred));
    
    System.out.println("PAIRS");
    for( RelationPair pair : Util.sortCounterKeys(pairCounts) )
      if( pairCounts.getCount(pair) > 2 )
        System.out.printf("%s\t%.3f\n", pair, pairCounts.getCount(pair));
    
    // Greedy build of the schema.
    Schema schema = new Schema();

    // Score Check if this pair has a high score (must check other combinations of args and subtract from this one)
    boolean goodtogo = true;
    int count = 0;
    while( goodtogo ) {
      Map<RelationPair, Double> scores = new HashMap<RelationPair,Double>();
      Map<RelationPair, Integer> entityIDs = new HashMap<RelationPair,Integer>();

      // Score all pairs with the current schema.
      int tried = 0;
      for( RelationPair pair : Util.sortCounterKeys(pairCounts) ) {
        if( pairCounts.getCount(pair) > 2 ) {
          System.out.println("Scoring pair: " + pair);
          Pair<Double,Integer> scoreAndID = scoreWithSchema(pair, schema, pairCounts);
          System.out.println("\tscore=" + scoreAndID);
          if( scoreAndID.first >= mincutoff ) {
            scores.put(pair, scoreAndID.first);
            entityIDs.put(pair, scoreAndID.second);
          }
        }
        // Don't bother scoring so many, just keep the first 20. One of them is most likely the best.
        if( tried > 20 ) break;
        tried++;
      }
    
      System.out.println("Scored all pairs!");
      System.out.println("scores = " + scores);
      
      // Add the first pair! (if there was a conflict, it wouldn't have a score)
      if( scores.size() > 0 ) {
        RelationPair bestpair = Util.sortKeysByValues(scores).get(0);
        System.out.println("Adding best pair: " + bestpair + " at score=" + scores.get(bestpair));
        addToSchema(bestpair, entityIDs.get(bestpair), schema);
        System.out.println("Schema: " + schema);
        // Remove it so we don't add again.
        pairCounts.remove(bestpair);
      }
      else goodtogo = false;
      count++;
      if( count > 100 ) {
        System.out.println("ERROR: abort!");
        goodtogo=false;
      }
    }
    
    System.out.println("FINAL SCHEMA: " + schema);
  }

  private void addToSchema(RelationPair pair, int entityID, Schema schema) {
    Relation rel1 = pair.getFirstRelation();
    Relation rel2 = pair.getSecondRelation();

    // Set the entity ID in these two relations.
    if( rel1.leftDep == null ) rel1.rightEntityID = entityID;
    else rel1.leftEntityID = entityID;
    if( rel2.leftDep == null ) rel2.rightEntityID = entityID;
    else rel2.leftEntityID = entityID;
    
    List<Integer> matches1 = schema.getEntitiesFillingArg(pair.pred1, null, pair.dep1);
    List<Integer> matches2 = schema.getEntitiesFillingArg(pair.pred2, null, pair.dep2);
    
    if( matches1 == null )
      schema.addRelation(rel1);
    if( matches2 == null )
      schema.addRelation(rel2);
  }
  
  private Pair<Double,Integer> scoreWithSchema(RelationPair pair, Schema schema, Counter<RelationPair> pairCounts ) {
    System.out.println("Scoring pair = " + pair);
    Relation rel1 = pair.getFirstRelation();
    Relation rel2 = pair.getSecondRelation();
    double score = scorePair(pair, pairCounts);
    System.out.println("pair score by itself = " + score);

    // Find relations that contain the first predicate.
    List<Integer> matches1 = schema.getEntitiesFillingArg(pair.pred1, null, pair.dep1);
    
    // Find relations that contain the second predicate.
    List<Integer> matches2 = schema.getEntitiesFillingArg(pair.pred2, null, pair.dep2);
    
    System.out.println("matches1=" + matches1);
    System.out.println("matches2=" + matches2);
        
    boolean leftRel = false;
    boolean rightRel = false;
    
    // No matches, return just this pair's score as the score.
    if( matches1 == null && matches2 == null )
      return new Pair(score, schema.getMaxEntityID()+1);

    // One side matched, but the other didn't
    else if( matches1 == null || matches2 == null ) {
      int entityID = -1;

      if( matches1 != null && matches1.size() > 0 ) {
        entityID = matches1.get(0);
        leftRel = true;
      }
      else if( matches2 != null && matches2.size() > 0 ) {
        entityID = matches2.get(0);
        rightRel = true;
      }

      System.out.println("match: " + entityID);
      
      // Score against all relations with this entity.
      List<Relation> leftRels = schema.getRelationsWithLeftEntityID(entityID);
      List<Relation> rightRels = schema.getRelationsWithRightEntityID(entityID);

      System.out.println("leftRels = " + leftRels);
      System.out.println("rightRels = " + rightRels);
      
      // new pair: X said - X replied
      // schema: X said, X fell, X hurt
      // * need to score: X replied/X fell  and  X replied/X hurt
      double numPairs = 0.0;
      for( Relation schemaRel : leftRels ) {
        if( !leftRel ) {
          score += scorePair(new RelationPair(rel1.predicate, rel1.leftDep!=null ? "s" : "o", schemaRel.predicate, schemaRel.leftEntityID==entityID ? "s" : "o"), pairCounts);
          numPairs++;
        }
        if( !rightRel ) {
          score += scorePair(new RelationPair(rel2.predicate, rel2.leftDep!=null ? "s" : "o", schemaRel.predicate, schemaRel.leftEntityID==entityID ? "s" : "o"), pairCounts);
          numPairs++;
        }
      }
      
      score = score / numPairs;
      return new Pair(score, entityID);
    }
    
    // Both sides matched: CONFLICT or ALREADY IN SCHEMA
    else {
      return new Pair(0.0, -1);
    }
  }
  
  /**
   * VERIFIED: this appears to work as intended
   */
  private double scorePair(RelationPair pair, Counter<RelationPair> pairCounts) {
    // Count(verb1, verb2) / N
    // Count(verb1/dep, verb2/dep) / 
    
    // P(v/d, v/d)  --  most frequent
    // P(v/d, v/d | v,v) -- most confident with its verbs
    double N = pairCounts.totalCount();
    
    double num = 0;
    double numWithPreds = 0;
    for( RelationPair p : pairCounts.keySet() ) {
      if( p.pred1.equals(pair.pred1) && p.pred2.equals(pair.pred2) ) {
        numWithPreds += pairCounts.getCount(p);
        if( p.dep1.equals(pair.dep1) && p.dep2.equals(pair.dep2) )
          num = pairCounts.getCount(p);
      }
      else if( p.pred2.equals(pair.pred1) && p.pred1.equals(pair.pred2) ) {
        numWithPreds += pairCounts.getCount(p);
        if( p.dep2.equals(pair.dep1) && p.dep1.equals(pair.dep2) )
          num = pairCounts.getCount(p);
      }
    }
  
    System.out.println("scorePair " + pair + "\tnum=" + num + " numWithPreds=" + numWithPreds);
    
    // Count(pair) * P(pair | pair-verbs)
    return num * (num / numWithPreds);
  }

  public void generalizeSchemasInFile(String path) {
    List<Schema> schemas = ClusterSchemas.readSchemas(path, Integer.MAX_VALUE);
    System.out.println("Read in " + schemas.size() + " schemas.");
    generalizeSchemas(schemas);
  }

  /**
   * Stores a pair of coref relations.
   */
  private class RelationPair {
    public String pred1, pred2;
    public String dep1, dep2;
    
    public RelationPair(String p1, String d1, String p2, String d2) {
      pred1 = p1;
      pred2 = p2;
      dep1  = d1;
      dep2  = d2;
    }
    
    public Relation getFirstRelation() {
      Relation rel = new Relation();
      rel.predicate = pred1;
      if( dep1.equals("s") ) rel.leftDep = "nsubj";
      else rel.rightDep = "dobj";
      return rel;
    }

    public Relation getSecondRelation() {
      Relation rel = new Relation();
      rel.predicate = pred2;
      if( dep2.equals("s") ) rel.leftDep = "nsubj";
      else rel.leftDep = "dobj";
      return rel;
    }

    @Override
    public int hashCode() {
      return (pred1 + dep1 + pred2 + dep2).hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
      if( obj == null ) return false;
      RelationPair u = (RelationPair)obj;
      return u.pred1.equals(pred1) && u.pred2.equals(pred2) && u.dep1.equals(dep1) && u.dep2.equals(dep2);
    }
    
    public String toString() {
      return pred1 + "-" + dep1 + " " + pred2 + "-" + dep2;
    }
  }
  

  public static void main(String[] args) {
    if( args.length < 1 ) {
      System.out.println("GeneralCluster <path-to-schemas>");
      System.exit(1);
    }
    
    GeneralCluster gen = new GeneralCluster();
    gen.generalizeSchemasInFile(args[args.length-1]);
  }

}

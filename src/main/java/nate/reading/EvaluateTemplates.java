package nate.reading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import nate.CalculateIDF;
import nate.CountVerbDepCorefs;
import nate.EntityMention;
import nate.IDFMap;
import nate.Pair;
import nate.args.CountArgumentTypes;
import nate.args.VerbArgCounts;
import nate.util.TreeOperator;
import nate.util.Util;
import nate.util.WordNet;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;


/**
 * This class contains code to evaluate gold MUC templates against guessed templates.
 * It can run a "bag of entities" evaluation where we just check if extracted entities
 * fill some slot in the gold templates.  It can also run the full MUC slot evaluation
 * where we check if extracted entities for particular slots are also gold slot fillers.
 * 
 * The constructor takes the gold train and test templates as parameters.  You then
 * call setGuesses() with your guessed frames per document. 
 */
public class EvaluateTemplates {
  public static final int PRECISION = 0; 
  public static final int RECALL = 1; 
  public static final int F1 = 2;
  String[] _types;
  MUCKeyReader _answerKey;
  boolean _evaluateOnTest = false;
  Map<String,List<Frame>> _docFrameGuesses;
  List<String> _altKidnapStories;

  public EvaluateTemplates(String[] mucTypes, MUCKeyReader answerKey) {
    _types = mucTypes;
    _answerKey = answerKey;
    
    // MUC training documents that mention a "kidnap"-based word, but aren't labeled with a kidnap template. 
    final String[] arr = { "dev-muc3-0021", "dev-muc3-0111", "dev-muc3-0114", "dev-muc3-0155", "dev-muc3-0185", "dev-muc3-0232", "dev-muc3-0247", "dev-muc3-0249", "dev-muc3-0272", "dev-muc3-0281", "dev-muc3-0296",
        "dev-muc3-0430", "dev-muc3-0457", "dev-muc3-0485", "dev-muc3-0502", "dev-muc3-0506", "dev-muc3-0526", "dev-muc3-0529", "dev-muc3-0553", "dev-muc3-0566", "dev-muc3-0599", "dev-muc3-0603", "dev-muc3-0614", "dev-muc3-0700", "dev-muc3-0726", "dev-muc3-0770",
        "dev-muc3-0920", "dev-muc3-0963", "dev-muc3-0976", "dev-muc3-0982", "dev-muc3-1010", "dev-muc3-1045", "dev-muc3-1052", "dev-muc3-1075", "dev-muc3-1080", "dev-muc3-1081", "dev-muc3-1087", "dev-muc3-1096", "dev-muc3-1097", "dev-muc3-1102",
        "dev-muc3-1124", "dev-muc3-1138", "dev-muc3-1147", "dev-muc3-1154", "dev-muc3-1183", "dev-muc3-1188", "dev-muc3-1189", "dev-muc3-1199", "dev-muc3-1204", "dev-muc3-1236", "dev-muc3-1258", "dev-muc3-1270", "dev-muc3-1290"}; 
    _altKidnapStories = Util.arrayToList(arr);
  }
  
  /**
   * @param guesses A map from MUC document name to a set of Frames with extracted
   *                entities filling its slots. 
   */
  public void setGuesses(Map<String,List<Frame>> guesses) {
    _docFrameGuesses = guesses;
  }
  
  /**
   * Given an array of Frames, we evaluate each frame with each MUC type (e.g. kidnap,
   * bombing, attack) and find the frame that scores highest in F1 for each.  The evaluation
   * uses the guessed frames per document global variable set by calling setGuesses().
   * The given 'mapping' array is then set with the frame IDs of the best scored frames.  
   * @param frames An array of learned frames with induced slots.
   * @param mapping An array of integers that will be destructively set to the best frame
   *                IDs for each MUC type (types in the global _types variable).
   */
  public void mapFramesToMuc(Frame[] frames, int[] mapping) {
    System.out.println("mapFramesToMuc() with " + Arrays.toString(frames) + " generic frames and mapping " + Arrays.toString(mapping));
    assert frames != null : "frames null!";
    assert mapping != null : "mapping null!";
    assert _types != null : "_types null!";
    assert mapping.length == _types.length : "frames length not the mapping length!";
    
    System.out.println("mapFramesToMuc() with " + frames.length + " generic frames.");
    for( int xx = 0; xx < mapping.length; xx++ )
      System.out.println("mapping[" + xx + "] = " + mapping[xx]);
    for( String type : _types ) System.out.println("type: " + type);
      
    int i = 0;
    for( String type : _types ) {
      int thebest = bestFrameForType(frames, type);
      System.out.println("Got for " + type + " best id " + thebest);
      mapping[i] = thebest;
      System.out.println("Frame map " + type + ": " + mapping[i]);
      i++;
    }
  }
  

  /**
   * Scores each frame as if it was the given MUC type, and returns the best scoring frame ID.
   * @param frames The list of general frames we learned.
   * @param mucType The muc type (e.g. BOMBING)
   * @return The frame id of the best frame.
   */
  private int bestFrameForType(Frame[] frames, String mucType) {
    int best = -1;
    double bestScore = -1.0;

    for( Frame frame : frames ) {
//      System.out.println("evaluating frame id " + frame.getID() + " with type " + mucType);

      int[] counts = evaluateSingleFrameGuesses(frame.getID(), mucType, false);
      double[] scored = score(counts[0], counts[1], counts[2]);

      if( scored[2] > bestScore ) {
        bestScore = scored[2];
        best = frame.getID();
        System.out.println("New best " + best + ": " + Arrays.toString(scored));
      }
      System.out.println(frame.getID() + ": " + Arrays.toString(scored));
    }
    System.out.println("Best for " + mucType + " is " + best);
    return best;
  }
  
  
  /**
   * Assumes we already ran the full system and guessed frames for each document: in _docFrameGuesses.
   * This function runs the basic Filatova-style learner that pretends all of our guesses are
   * correct documents, and builds tf-idf scores for all tokens in that subset of documents that
   * we guessed for each domain.  Those words are the key words, and we then run extraction using
   * them on each guessed doc.  This function builds the token counts over all docs, runs the 
   * word learner, and then evaluates the entities that are extracted. 
   * @param dataReader The parse/deps info.
   * @param frameIDs The IDs of the frames that match each MUC type.
   * @param mucTypes The MUC types (e.g. kidnapping)
   */
  public void evaluateBagOfEntitiesBasicExtraction(ProcessedData dataReader, int[] frameIDs, String[] mucTypes, WordNet wordnet, IDFMap generalIDF) {
    MUCKeyReader answerKey = _answerKey;
    int firstN = 5;

    // This loop runs over the entire dataset and counts all relations in the domain for the set of
    // documents that each MUC type was guessed for.
    // This allows us to run the TemplateExtractor code which just takes counts, finds likelihood ratios,
    // and performs basic entity extraction.
    for( int i = 0; i < mucTypes.length; i++ ) {
      int targetFrameID = frameIDs[i];
      String mucType = mucTypes[i];

      CountVerbDepCorefs relnCountsDomain = new CountVerbDepCorefs(wordnet);
      CalculateIDF domainCalcIDF = new CalculateIDF(wordnet);
      CountArgumentTypes countArgs = new CountArgumentTypes(wordnet, true, false);

      // ******************************
      // Count all tokens and relations in all documents that we guessed.
      System.out.println("Counting tokens and relations for " + mucTypes[i]);
      dataReader.reset();
      for( String storyname : answerKey.getStories() ) {
//        System.out.println("Storyname " + storyname);
        storyname = storyname.toUpperCase();
        List<Frame> frames = _docFrameGuesses.get(storyname.toLowerCase());
        Frame guessedFrame = framesContain(targetFrameID, frames);

        // If this frame type guessed this story. 
        if( guessedFrame != null ) {
          // Extract the story.
          dataReader.nextStory(storyname);
          List<Tree> trees = TreeOperator.stringsToTrees(dataReader.getParseStrings());
          List<List<TypedDependency>> deps = dataReader.getDependencies(); 
          List<EntityMention> entities = dataReader.getEntities();
//          System.out.println("story " + storyname + " with " + trees.size() + " trees and " + deps.size() + " deps.");
          // Increment all grammatical relations seen in this document.
          relnCountsDomain.countCorefs(trees, deps, entities, dataReader.getNER());
          // Increment the token counts for IDFs.
          domainCalcIDF.countStory(dataReader.getParseStrings());
          // Increment all reln-arg counts.
          countArgs.analyzeDocument(trees, deps, entities, dataReader.getNER(), -1);
        }
      }
      // ******************************

      // Calculate IDF scores for this set of docs...
      domainCalcIDF.calculateIDF();
      relnCountsDomain.setLemmasAsMainCounts();
      VerbArgCounts domainArgCounts = new VerbArgCounts();
      domainArgCounts.setVerbHash(countArgs.getVerbCounts());

//      System.out.println("Some debug counts:");
//      // DEBUG idf counts:
//      for( String token : domainCalcIDF._idfLemmas.getWords() ) {
//        System.out.println("idf " + token + "\t" + domainCalcIDF._idfLemmas.getFrequency(token) + "\t" +
//            domainCalcIDF._idfLemmas.get(token));        
//      }
//      for( String token : relnCountsDomain.getWords() ) {
//        for( String reln : relnCountsDomain.getRelns(token) ) {
//          System.out.println("relns " + token + "\t" + reln + "\t" + relnCountsDomain.getCount(token, reln));
//        }
//      }
      
      // Build the Entity Extractor!
      TemplateExtractor extractor = new TemplateExtractor(domainCalcIDF._idfLemmas, generalIDF, relnCountsDomain, domainArgCounts, wordnet);
      TemplateTester tester = new TemplateTester();
      
      // Run entity extraction on all documents that we guessed.
      System.out.println("Extracting entities for guessed " + mucTypes[i]);
      dataReader.reset();
      for( String storyname : answerKey.getStories() ) {
        System.out.println("Extract story " + storyname);
        storyname = storyname.toUpperCase();

        // Frame guesses for this story.
        List<Frame> frames = _docFrameGuesses.get(storyname.toLowerCase());
        Frame guessedFrame = framesContain(targetFrameID, frames);
        
        // Gold entities for this story.
        List<Template> templates = answerKey.getTemplates(storyname);
        List<Template> goldTemplates = templatesContain(mucType, templates);
        List<MUCEntity> goldEntities = TemplateTester.getGoldEntities(goldTemplates);
        
        // If this frame type guessed this story. 
        if( guessedFrame != null ) {
          dataReader.nextStory(storyname);
          
          List<String> entities = extractor.filatovaTopVerbs(dataReader, "likelihood", true, true);
          Util.firstN(entities, firstN);
          tester.evaluateEntities("fila-likelihood", goldEntities, entities);
          
          entities = extractor.filatovaTopVerbs(dataReader, "salience", false, true);
          Util.firstN(entities, firstN);
          tester.evaluateEntities("fila-salience-noarg", goldEntities, entities);
          
          entities = extractor.likelihoodTopVerbs(dataReader);
          Util.firstN(entities, firstN);
          tester.evaluateEntities("likelihood", goldEntities, entities);
          
          entities = extractor.salienceTopVerbs(dataReader, true);
          Util.firstN(entities, firstN);
          tester.evaluateEntities("salience", goldEntities, entities);
        }
        
        // Record false negatives - domain templates whose documents we did not process.
        else if( goldEntities != null ) {
          System.out.println("Unseen story in MUC " + mucType + ": " + storyname);
          // Increment the global false negatives for this document.
          tester.recordFalseNegativeArguments(goldTemplates);
        }
      }
      
      // Print the results!!
      System.out.println("Filatova Approach: MUC type " + mucType + " Results !");
      tester.printEvaluationResults();
    }
  }
    
    
  public void evaluateBagOfEntities(int[] frameIDs, String[] mucTypes, boolean debug) {
    System.out.println("evaluateBagOfEntities frameIDs: " + Arrays.toString(frameIDs));
    int[][] allresults = new int[_types.length][];
    
    for( int i = 0; i < mucTypes.length; i++ ) {
      int[] results = evaluateBagOfEntities(frameIDs[i], mucTypes[i], debug);
      allresults[i] = results;
    }
    
    System.out.println("** Bag of Entities Results **");
    for( int i = 0; i < mucTypes.length; i++ ) {
      double[] stats = score(allresults[i][0], allresults[i][1], (allresults[i][0] + allresults[i][2]));
      System.out.printf("%s\t%s\tp=%.2f r=%.2f f1=%.2f\n", mucTypes[i], Arrays.toString(allresults[i]), stats[0], stats[1], stats[2]);
    }
  }
  
  /**
   * @return Three scores: # correct, # incorrect (false positives), # missed (false negatives)
   */
  public int[] evaluateBagOfEntities(int targetFrameID, String mucType, boolean debug) {
    System.out.println("EvalBagEntities top: mutype=" + mucType);
    MUCKeyReader answerKey = _answerKey;
    int[] overall = new int[3];

    // Over all stories...
    for( String storyname : answerKey.getStories() ) {
      if( debug ) System.out.println("story " + storyname);
      List<Frame> frames = _docFrameGuesses.get(storyname.toLowerCase());
      List<Template> templates = answerKey.getTemplates(storyname);

      Frame guessedFrame = framesContain(targetFrameID, frames);
      List<Template> goldTemplates = templatesContain(mucType, templates);

      // Make the bag of guesses.
      Set<String> allguesses = new HashSet<String>();
      if( guessedFrame != null ) {
        for( int rolei = 0; rolei < guessedFrame.getNumRoles(); rolei++ )
          for( Integer entityID : guessedFrame.getEntityIDsOfRole(rolei) )
            allguesses.add(guessedFrame.getEntity(entityID).string());
      }

      // Make the bag of golds.
      List<MUCEntity> allgolds = new ArrayList<MUCEntity>();
      for( List<MUCEntity> slots : getAllGoldSlots(goldTemplates, answerKey.numSlots()) )
        allgolds.addAll(slots);

      int[] results = TemplateTester.evaluateEntities(allgolds, allguesses, debug);
      for( int i = 0; i < overall.length; i++ )
        overall[i] += results[i];
    }
    
    return overall;
  }


  /**
   * Finds the templates in the given list that match the given type.
   * @param type The type of template (e.g. KIDNAP, BOMBING)
   * @param templates List of templates to search.
   * @return A List of matched templates, or null if none matched.
   */
  public static List<Template> templatesContain(String type, List<Template> templates) {
    List<Template> matches = null;
    if( templates != null ) {
      for( Template template : templates ) {
        if( template.get(MUCTemplate.INCIDENT_TYPE).startsWith(type) ) {
          if( matches == null ) matches = new ArrayList<Template>();
          matches.add(template);
        }
      }
    }
    return matches;
  }
  
  
  /**
   * @param id The id of the Frame type we are looking for.
   * @param frames The list of frames to search
   * @return The Frame that matches the given id type, or null if none.
   */
  public static Frame framesContain(int id, Collection<Frame> frames) {
    if( frames != null ) {
      for( Frame frame : frames ) {
        if( frame.getID() == id ) return frame;
      }
    }
    return null;
  }
  

  /**
   * Gets all the gold entities filling each slot in this domain, returning a list
   * where the index in the list is the slot index (e.g. perpetrators are index 0).
   */
  public static List<List<MUCEntity>> getAllGoldSlots(List<Template> templates, int numSlots) {
    List<List<MUCEntity>> slots = new ArrayList<List<MUCEntity>>();
    for( int i = 0; i < numSlots; i++ ) {
      List<MUCEntity> golds = goldEntitiesInSlot(templates, i);
      slots.add(golds);
    }
    return slots;
  }

  public static List<MUCEntity> goldEntitiesInSlot(List<Template> templates, int slotIndex) {
    List<MUCEntity> entities = new ArrayList<MUCEntity>();
    if( templates != null ) {
      for( Template template : templates ) {
        List<MUCEntity> locals = template.getSlotEntities(slotIndex);
        if( locals != null ) 
          for( MUCEntity entity : locals ) {
            int index = entities.indexOf(entity);
            // If a new entity.
            if( index < 0 ) 
              entities.add(entity);
            // Else another template had this entity already.
            else {
              // If the other was optional, but this is not, set it as not optional.
              if( entities.get(index).isOptional() && !entity.isOptional() )
                entities.set(index, entity);
            }
//            if( !entities.contains(entity) ) entities.add(entity);
          }
      }
    }
    return entities;
  }

  public static List<MUCEntity> removeOptionals(List<MUCEntity> entities) {
    if( entities == null ) return null;
    
    List<MUCEntity> newlist = new ArrayList<MUCEntity>();
    for( MUCEntity entity : entities ) {
      if( !entity.isOptional() )
        newlist.add(entity);
    }
    return newlist;
  }

  /**
   * Calculates precision/recall/F1 for you.
   * @param truePositives The number you guessed that were correct.
   * @param falsePositives The number you guessed that were incorrect.
   * @param total The total number of test items *for your specific class*.
   * @return An array of size 3: prec, recall, F1
   */
  public static double[] score(int truePositives, int falsePositives, int total) {
    int guessed = truePositives + falsePositives;
    double precision = 0.0;
    if( guessed > 0 ) precision = (double)truePositives/(double)guessed;
    double recall = (double)truePositives/(double)total;
    double f1 = 2.0f * precision * recall / (precision + recall);
    double[] scores = new double[3];
    scores[0] = precision;
    scores[1] = recall;
    scores[2] = f1;
    return scores;
  }

  /**
   * @param counts # correct, # incorrect, # missed (false negatives)
   * @return
   */
  public static double[] score(int[] counts) {
    return score(counts[0], counts[1], counts[0]+counts[2]);
  }


  /**
   * Use the given frameids as the frames you want to evaluate for doc classification.
   * These ids line up with the given muc types, and we evaluate each with each type.
   * @param frameids frame ids
   * @param types Strings of the muc types e.g. KIDNAPPING
   */
  public void evaluateDocumentClassification(int[] frameids, String[] types) {
    // Array of frames with array of correct/incorrect/missed.
    int[][] allcounts = new int[frameids.length][];

    for( int i = 0; i < frameids.length; i++ )
      allcounts[i] = evaluateSingleFrameGuesses(frameids[i], types[i], true);

    for( int i = 0; i < frameids.length; i++ ) {
      int[] counts = allcounts[i];
      double[] scores = score(counts[0], counts[1], counts[2]);
      System.out.printf("%s\t%s\tp=%.3f\tr=%.3f\tf1=%.3f\n", types[i], Arrays.toString(counts), scores[0], scores[1], scores[2]);
    }
  }

  /**
   * Evaluates document classification performance of a single frame over the MUC corpus.
   * @param frameid The ID of the frame to evaluate.
   * @param mucType The type of MUC templates to evaluate.
   * @param debug If true, print the per-document results.
   * @return 3 scores: true positives, false positives, total gold documents in corpus.
   */
  public int[] evaluateSingleFrameGuesses(int frameid, String mucType, boolean debug) {
    int truePositives = 0, falsePositives = 0, total = 0;
    MUCKeyReader answerKey = _answerKey;
        
    // Evaluate this frame as the mucType on all stories.
//    for( String story : Util.sortStrings(_docFrameGuesses.keySet()) ) {
    for( String story : allStories(answerKey, _docFrameGuesses) ) {
      List<Frame> guesses = _docFrameGuesses.get(story.toLowerCase());
      List<Template> goldTemplates = answerKey.getTemplates(story);

      Frame matchedFrame = framesContain(frameid, guesses);
      List<Template> matchedTemplates = templatesContain(mucType, goldTemplates);

      // Our frame type matches the gold template frame type (e.g. kidnap).
      if( matchedFrame != null && matchedTemplates != null ) {
        truePositives++;
        if( debug ) System.out.println(story + "\nEVAL: " + mucType + " match!");
      }
      // Our frame is a different type than the gold template.
      else if( matchedFrame != null && matchedTemplates == null ) {
        falsePositives++;
        if( debug ) System.out.println(story + "\nEVAL: " + mucType + " false match!");
      }

      if( matchedTemplates != null ) {
        total++;
        if( debug && matchedFrame == null ) System.out.println(story + "\nEVAL: " + mucType + " missed!");
      }
    }

    // Get the precision/recall/f1.
    int[] counts = new int[3];
    counts[0] = truePositives; counts[1] = falsePositives; counts[2] = total;
    return counts;
  }

  /**
   * This is the standard MUC-4 evaluation.  You evaluate all 'perpetrator' extractions,
   * regardless of if it was a kidnap or bombing perpetrator.  The templates are basically
   * ignored, and only slot types evaluated.
   * @param frameIDs The index IDs of our frames that map to each MUC template type. The types do not
   *                 matter though, as we just evaluate slots regardless of template type.
   * @param allframes General frame structure of each learned frame.  The above IDs are indexes to this array.
   * @param goldDocsOnly True if you want to evaluate our performance on only the documents that have a gold
   *                     template for a guessed template type.  It ignores all other docs that we guessed a
   *                     template for, say bombing, but there is no gold bombing doc.  False is all docs.
   */
//  public void evaluateRoleAssignmentsIgnoringTemplateTypes(int[] frameIDs, Frame[] allframes) {
  public void evaluateRoleAssignmentsIgnoringTemplateTypes(List<Pair<Integer,int[]>> frameIDAndPerms, Frame[] allframes, boolean goldDocsOnly) {
//    if( frameIDs.length != _types.length ) {
//      System.err.println("ERROR in evaluateRoleAssignmentsIgnoringTemplateTypes, lengths off: " + frameIDs.length + " vs " + _types.length);
//      System.exit(-1);
//    }
//    System.out.println("evaluateRoleAssignmentsIgnoringTemplateTypes frameIDs=" + Arrays.toString(frameIDs));
    System.out.println("evaluateRoleAssignmentsIgnoringTemplateTypes frameIDs=" + frameIDAndPerms);

    // Map each frame's roles to MUC slots for its MUC type.
    List<int[]> framePerms = new ArrayList<int[]>();
    List<Frame> targetFrames = new ArrayList<Frame>();
//    for( int i = 0; i < _types.length; i++ ) {
//      System.out.println("** Evaluate Slots (across all templates) for " + _types[i] + "**");
//      targetFrames[i] = allframes[frameIDs[i]];
//      Pair<Double,int[]> result = evaluateSpecificRoleAssignments(targetFrames[i], _types[i]);
//      int[] bestRolePermutation = result.second();
//      framePerms.add(bestRolePermutation);
//    }
    for( Pair<Integer,int[]> pair : frameIDAndPerms ) {
      targetFrames.add(allframes[pair.first()]);
      framePerms.add(pair.second());
    }
    
    // Calculate P/R/F1 for each slot (perpetrator) across all template types (bombing).
    List<int[]> allResults = new ArrayList<int[]>();
    List<double[]> allScores = new ArrayList<double[]>();
    for( int sloti = 0; sloti < _answerKey.numSlots(); sloti++ ) {
      System.out.println("Now Evaluating slot " + sloti);
      int[] results = evaluateSlotIgnoringTemplateTypes(sloti, targetFrames, framePerms, goldDocsOnly, true);
      double[] scores = score(results[0], results[1], (results[0]+results[2]));
      allResults.add(results);
      allScores.add(scores);
      System.out.printf("Slot " + sloti + " Counts\tcorrect=%d\tincorrect=%d\tmissed=%d\n", results[0], results[1], results[2]);
      System.out.printf("Slot " + sloti + " Results\tprec=%.3f\trecall=%.3f\tf1=%.2f\n", scores[0], scores[1], scores[2]);
    }

    // Output results.
    System.out.println("RESULTS Across Template Types: " + Arrays.toString(_types));
    int[] sumtotal = new int[3];
    for( int i = 0; i < _answerKey.numSlots(); i++ ) {
      int[] results = allResults.get(i);
      double[] scores = allScores.get(i);
      System.out.printf("Slot " + i + " Counts\tcorrect=%d\tincorrect=%d\tmissed=%d\n", results[0], results[1], results[2]);
      System.out.printf("Slot " + i + " Results\tprec=%.3f\trecall=%.3f\tf1=%.2f\n", scores[0], scores[1], scores[2]);
      for( int xx = 0; xx < sumtotal.length; xx++ ) sumtotal[xx] += results[xx];
    }
    double[] overall = score(sumtotal[0], sumtotal[1], sumtotal[0]+sumtotal[2]);
    System.out.printf("Slots Overall\tprec=%.3f\trecall=%.3f\tf1=%.2f\n", overall[0], overall[1], overall[2]);
  }
  
  /**
   * This function takes all frames that were guessed for a document (targetFrames), and
   * evaluates how well they predict a single MUC template slot (slotIndex) regardless of
   * the frame types.
   * @param slotIndex The MUC template slot index we want to evaluate.
   * @param targetFrames The list of guessed frames
   * @param permutations The list of frame role to template slot mappings, one each for the targets.
   * @param goldDocsOnly True if you want to evaluate our performance on only the documents that have a gold
   *                     template for a guessed template type.  It ignores all other docs that we guessed a
   *                     template for, say bombing, but there is no gold bombing doc.  False is all docs.
   * @param debug True if you want print statements.
   * @return An array of three counts: correct, incorrect, missing
   */
  private int[] evaluateSlotIgnoringTemplateTypes(int slotIndex, List<Frame> targetFrames, List<int[]> permutations, boolean goldDocsOnly, boolean debug) {
    int[] overallCounts = new int[3];
    MUCKeyReader answerKey = _answerKey;
    
    // Find the role index that maps to the desired slotIndex for each frame.
    int i = 0;
    Map<Integer,Integer> frameIDToTargetRole = new HashMap<Integer,Integer>();
    for( Frame targetFrame : targetFrames ) {
      for( int rolei = 0; rolei < permutations.get(i).length; rolei++ ) {
        if( permutations.get(i)[rolei] == slotIndex ) 
          frameIDToTargetRole.put(targetFrame.getID(), rolei);
      }
      i++;
    }

    if( debug ) {
      System.out.println("slotIndex = " + slotIndex);
      for( Map.Entry<Integer,Integer> entry : frameIDToTargetRole.entrySet() )
        System.out.println("   frame " + entry.getKey() + " role index " + entry.getValue());
    }
    
    // Over all stories...
//    for( String storyname : answerKey.getStories() ) {
    for( String storyname : allStories(answerKey, _docFrameGuesses) ) {    
      if( debug ) System.out.println("story " + storyname);
      List<Frame> frameGuesses = _docFrameGuesses.get(storyname.toLowerCase());
      List<Template> goldTemplates = answerKey.getTemplates(storyname);
      if( debug ) {
        System.out.println("  with " + (frameGuesses == null ? 0 : frameGuesses.size())
          + " guessed frames and " + (goldTemplates == null ? 0 : goldTemplates.size()) + " gold templates");
        if( frameGuesses != null ) {
          for( Frame frame : frameGuesses )
            System.out.print(" id=" + frame.getID());
          System.out.println();
        }
      }

      // Get the guessed frames.
      List<Frame> guessedTargets = new ArrayList<Frame>();
      for( Frame targetFrame : targetFrames ) { 
        Frame guess = framesContain(targetFrame.getID(), frameGuesses);
        if( guess != null ) guessedTargets.add(guess);
      }
      
      // Nothing to do.
      if( goldTemplates == null && guessedTargets.size() == 0 ) {  }

      // No gold templates, these are all false positives.
      else if( goldTemplates == null ) {
        if( debug ) System.out.println("No gold templates here.");
        // If only evaluating gold docs, then we ignore these docs with no gold templates.
        if( !goldDocsOnly ) {
          Set<Integer> guessedIDs = new HashSet<Integer>();
          for( Frame guessedFrame : guessedTargets ) {
            if( frameIDToTargetRole.containsKey(guessedFrame.getID()) )
              guessedIDs.addAll(guessedFrame.getEntityIDsOfRole(frameIDToTargetRole.get(guessedFrame.getID())));
          }
          if( debug ) System.out.println("  False positives: " + guessedIDs);
          overallCounts[1] += guessedIDs.size();
        }
      }

      // Gold templates, record the matches.
      else {
        // Gold answers for each slot.
        List<MUCEntity> goldEntities = goldEntitiesInSlot(goldTemplates, slotIndex);
        if( debug ) System.out.println("gold entities: " + goldEntities);

        // False negatives.
        if( guessedTargets.size() == 0 ) {
          if( debug ) System.out.println("False negatives adding " + goldEntities.size() + " false negatives.");
          if( debug ) System.out.println("   - " + removeOptionals(goldEntities).size() + " after removing optionals.");
          overallCounts[2] += removeOptionals(goldEntities).size();
        }

        // Compare all guesses.
        else {
          List<String> guessedStrings = new ArrayList<String>();
          Set<Integer> guessedIDs = new HashSet<Integer>();
          for( Frame guessedFrame : guessedTargets ) {
            Integer roleIndex = frameIDToTargetRole.get(guessedFrame.getID());
            List<Integer> ids = (roleIndex == null ? null : guessedFrame.getEntityIDsOfRole(roleIndex));
            if( ids != null ) { 
              for( Integer id : ids ) {
                if( !guessedIDs.contains(id) ) {
                  guessedIDs.add(id);
                  guessedStrings.add(guessedFrame.getEntity(id).string());
                }
              }
            }
          }
          if( debug ) System.out.println("guesses: " + guessedStrings);
          
          // Evaluate the guesses with the golds!  (last parameter is DEBUG)
          int[] matches = TemplateTester.evaluateEntities(goldEntities, guessedStrings, false);
          if( debug ) System.out.println("   matches... " + Arrays.toString(matches));
          for( int j = 0; j < overallCounts.length; j++ ) overallCounts[j] += matches[j];
        }
      }
    }
    return overallCounts;
  }
  
  /**
   * Find the top N frames whose combined performance is best.
   * @param allframes The general frames we want to evaluate (we just need their IDs in this function).
   * @param ignoreIDs A list of frames to ignore when choosing the top N.
   * @param n The number of frames to match with ATTACK.
   * @param maxn The maximum number of n frames you would want to match.
   * @return A list of N pairs: frame ID, and its role/slot alignment permutation.
   */
  public List<Pair<Integer,int[]>> evaluateTopNFrameAssignments(Frame[] allframes, Collection<Integer> ignoreIDs, String mucType, int maxn, boolean goldDocsOnly) {
    Map<Integer,int[]> framePermutations = new HashMap<Integer,int[]>();
    System.out.println("Top of TopN with " + allframes.length + " frames and ignore list: " + ignoreIDs);
    
    // Answer key.
    MUCKeyReader answerKey = _answerKey;
    
    // Find the best permutation and F1 score for each frame.
    ScoredFrame[] scored = new ScoredFrame[allframes.length];
    System.out.println("evaluateTopNFrameAssignments ignoreIDs=" + ignoreIDs);
    for( int i = 0; i < allframes.length; i++ ) {
      double f1 = -1.0;
      if( ignoreIDs == null || !ignoreIDs.contains(allframes[i].getID()) ) {
        Pair<Double,int[]> result = evaluateSpecificRoleAssignments(allframes[i], mucType, F1, goldDocsOnly);
        if( result != null ) {
          f1 = result.first();
          framePermutations.put(allframes[i].getID(), result.second());
        }
      } 
//      else System.out.println("Ignoring frame " + allframes[i].getID());
      scored[i] = new ScoredFrame(f1, allframes[i]);
//      System.out.println("Scored for attack frame " + allframes[i].getID() + " score=" + f1);
    }

    for( Map.Entry<Integer, int[]> entry : framePermutations.entrySet() )
      System.out.println("topn frame " + entry.getKey() + "\tpermutation " + Arrays.toString(entry.getValue()));      
    
    // Sort based on the best F1 scores, and keep the top n.
    Arrays.sort(scored);
    List<ScoredFrame> topnFrames = new ArrayList<ScoredFrame>();
    double bestnScore = 0.0;
    List<ScoredFrame> bestnFrames = null;
    
    // Try different sizes n.
    for( int nn = 1; nn <= maxn; nn++ ) {
      System.out.println("Trying topn n=" + nn);
      topnFrames.clear();
      for( int i = 0; i < scored.length && i < nn; i++ ) {
        if( scored[i].score() > 0.0f ) { 
          topnFrames.add(scored[i]);
          System.out.println("Adding topn best frame " + scored[i].frame().getID() + " scored " + scored[i].score() + " tokens " + scored[i].frame().tokens());
        }
      }

      // Create the Map to store performance on each slot.
      List<int[]> slotPerformance = new ArrayList<int[]>();
      for( int i = 0; i < _answerKey.numSlots(); i++ ) slotPerformance.add(new int[3]);

      int[] mergedPermutation = new int[_answerKey.numSlots()];
      for( int i = 0; i < mergedPermutation.length; i++)
        mergedPermutation[i] = i;

      // For each story.
      for( String storyname : allStories(answerKey, _docFrameGuesses) ) {
//      for( String storyname : answerKey.getStories() ) {
        System.out.println("topn story " + storyname);
        List<Frame> storyFrameGuesses = _docFrameGuesses.get(storyname.toLowerCase());
        List<Template> goldTemplates = answerKey.getTemplates(storyname);
        goldTemplates = templatesContain(mucType, goldTemplates);

        // Merge the best n frames' guesses and evaluate.
        Frame mergedFrame = new Frame();
        for( int i = 0; i < _answerKey.numSlots(); i++ )
          mergedFrame.addRole(new FrameRole(null, null, null));

        Map<Integer, Counter<Integer>> entityIDToSlots = new HashMap<Integer,Counter<Integer>>();
        Map<Integer, EntityFiller> entityIDToFiller = new HashMap<Integer,EntityFiller>();
        for( ScoredFrame scoredFrame : topnFrames ) {
          int frameID = scoredFrame.frame().getID();
          int[] permutation = framePermutations.get(scoredFrame.frame().getID());
          Frame guessedFrame = framesContain(frameID, storyFrameGuesses);

          // Add the frame's slot guesses.
          if( guessedFrame != null ) {
//            System.out.println("guessed frame " + guessedFrame.getID() + "\troles: " + guessedFrame.entitiesToString());
            for( int rolei = 0; rolei < guessedFrame.getNumRoles(); rolei++ ) {
              int sloti = permutation[rolei];
              if( sloti > -1 ) {
                List<Integer> guessedIDs = guessedFrame.getEntityIDsOfRole(rolei);
                for( Integer entityID : guessedIDs ) {
                  if( !entityIDToSlots.containsKey(entityID) )
                    entityIDToSlots.put(entityID, new IntCounter<Integer>());
//                  mergedFrame.setEntityRole(guessedFrame.getEntity(entityID), sloti);
                  entityIDToSlots.get(entityID).incrementCount(sloti);
                  entityIDToFiller.put(entityID, guessedFrame.getEntity(entityID));
                }
              }
            }
          }
        }
        
        for( Integer entityID : entityIDToSlots.keySet() ) {
          Counter<Integer> slotCounts = entityIDToSlots.get(entityID);
          int maxslot = Counters.argmax(slotCounts);
          mergedFrame.setEntityRole(entityIDToFiller.get(entityID), maxslot);
        }

        // Evaluate this large merged frame.
//        System.out.println("Merged frame: " + mergedFrame);
        evaluateFrameOnDocument(mergedFrame, mergedPermutation, goldTemplates, slotPerformance, true, null, null);      
      }

      // Print results.
      System.out.println("SUBRESULTS TOPN n=" + nn);
      int[] sumperf = new int[3];
      for( int rolei = 0; rolei < _answerKey.numSlots(); rolei++ ) {
        double[] results = score(slotPerformance.get(rolei));
        System.out.printf("Slot %d:\t%s\tp=%.4f\tr=%.4f\tf1=%.4f\n", rolei, Arrays.toString(slotPerformance.get(rolei)), results[0], results[1], results[2]);
        // Don't count this one if it had zero correct. Pretend the learned role did not match the slot at all.
        if( slotPerformance.get(rolei)[0] > 0 )
          for( int jj = 0; jj < 3; jj++ )
            sumperf[jj] += slotPerformance.get(rolei)[jj];
      }
      double[] fullresults = score(sumperf);
      System.out.printf("All:\t%s\tp=%.4f\tr=%.4f\tf1=%.4f\n", Arrays.toString(sumperf), fullresults[0], fullresults[1], fullresults[2]);
      if( fullresults[2] > bestnScore ) {
        bestnScore = fullresults[2];
        bestnFrames = topnFrames;
      }
    }
    System.out.printf("Bestn score = %.6f with n=%d\n", bestnScore, (bestnFrames != null ? bestnFrames.size() : 0));
    
    // Return the top N frame IDs with their permutations.
    List<Pair<Integer,int[]>> topn = new ArrayList<Pair<Integer,int[]>>();
    if( bestnFrames != null ) {
      for( ScoredFrame frame : bestnFrames )
        System.out.println("Bestn frame " + frame.frame().getID());

      for( ScoredFrame scoredFrame : bestnFrames ) {
        int frameID = scoredFrame.frame().getID();
        topn.add(new Pair<Integer,int[]>(frameID, framePermutations.get(frameID)));
      }
    }
    return topn;
  }
  
  /**
   * Prints evaluation results for the frames given in frameIDs.
   * It searches for the best frame role to MUC slot mapping in each frame and
   * prints the P/R/F1 scores. 
   * @param frameIDs Array of frame IDs aligned to MUC types.
   * @param allframes Array of all frames, the IDs should be indices in the array.
   * @return A list the length of the given frameIDs, with pairs: frame ID, and its best role/slot permutation.
   */
  public List<Pair<Integer,int[]>> evaluateSpecificRoleAssignments(int[] frameIDs, Frame[] allframes, int scoreType, boolean goldDocsOnly) {
    if( frameIDs.length != _types.length ) {
      System.err.println("ERROR in evaluateSpecificRoleAssignments, lengths off: " + frameIDs.length + " vs " + _types.length);
      System.exit(-1);
    }

    List<Pair<Integer,int[]>> perms = new ArrayList<Pair<Integer,int[]>>();
    
    System.out.println("evaluateSpecificRoleAssignments frameIDs=" + Arrays.toString(frameIDs));
    for( int i = 0; i < _types.length; i++ ) {
      System.out.println("** Evaluate Slots (single template) for " + _types[i] + "**");
      Pair<Double,int[]> result = evaluateSpecificRoleAssignments(allframes[frameIDs[i]], _types[i], scoreType, goldDocsOnly);
      System.out.println("** got result " + result);
      
      Pair<Integer,int[]> idPerm = new Pair<Integer,int[]>(frameIDs[i], (result != null ? result.second() : null));
      perms.add(idPerm);
    }
    
    for( Pair<Integer,int[]> idPerm : perms )
      System.out.println("pair: " + idPerm.first() + " " + (idPerm.second() != null ? Arrays.toString(idPerm.second()) : "null"));
    return perms;
  }

  /**
   * Evaluates a single Frame's extractions, given by the target frame ID, for a
   * particular MUC type (e.g. kidnap).
   * @param targetFrame The Frame that you want to evaluate.
   * @param mucType The MUC type we think the frame extracts for (e.g. kidnap).
   * @return A pair: The avg F1 score over all MUC slots, and the Frame role to MUC slot mapping.
   */
  public Pair<Double,int[]> evaluateSpecificRoleAssignments(Frame targetFrame, String mucType, int scoreType, boolean goldDocsOnly) {
    List<Integer> slotids = new ArrayList<Integer>();
    for( int i = 0; i < _answerKey.numSlots(); i++ )
      slotids.add(i);

//    Frame frame = allframes[targetFrameID];
    System.out.println("Frame " + targetFrame + " (use score type " + scoreType + ")");
    int[] roleids = new int[targetFrame.getNumRoles()];
    for( int i = 0; i < roleids.length; i++ )
      roleids[i] = -1;

    // Generate permutations of the roles to map to MUC slots.
    System.out.println("Getting permutations for muc slots " + slotids + " and roles " + Arrays.toString(roleids) + " type " + mucType + " " + Arrays.toString(roleids));
    List<int[]> perms = permutations(slotids, roleids);
    if( perms != null ) {
      for( int[] perm : perms ) System.out.println(Arrays.toString(perm));
    } else {
      System.out.println("No permutation found!");
      return null;
    }

    // Test each permutation to find the best.
    int i = 0;
    double bestf1 = -1.0;
    int[] bestperm = null;
    for( int[] perm : perms ) {
      double[] avgPRF1 = evaluateSpecificRoleAssignments(targetFrame.getID(), mucType, perm, goldDocsOnly, false);
      if( avgPRF1[scoreType] > bestf1 ) {
        bestf1 = avgPRF1[scoreType];
        bestperm = perm;
      }
      i++;
      //      if( i == 5 ) break;
    }
    System.out.println("Best permutation for " + mucType + " is " + Arrays.toString(bestperm) + " at avgF1=" + bestf1);
    
    // Do it again for the best for debugging...
    if( bestperm != null ) evaluateSpecificRoleAssignments(targetFrame.getID(), mucType, bestperm, goldDocsOnly, true);
    
    return new Pair<Double,int[]>(bestf1,bestperm);
  }
  
  /**
   * Should be called after all documents have been labeled with frames and those guesses are in 
   * the global _docFrameGuesses story->guesses map.
   * @param targetFrameID The ID of the cluster/topic that corresponds to the givem MUC type.
   * @param mucType The String type of MUC (e.g. KIDNAPPING)
   * @param goldDocsOnly True if you want to evaluate our performance on only the documents that have a gold
   *                     template for a guessed template type.  It ignores all other docs that we guessed a
   *                     template for, say bombing, but there is no gold bombing doc.  False is all docs.
   * @param guessPermutation An array where each index is the MUC slot index for that role index.
   * @return The overall P/R/F1 scores summed over individual MUC slot performances.
   */
  private double[] evaluateSpecificRoleAssignments(int targetFrameID, String mucType, int[] guessPermutation, boolean goldDocsOnly, boolean debug) {
    // Initialize the counts of correct/incorrect/missed.
    List<int[]> slotResults = new ArrayList<int[]>();
    for( int i = 0; i < _answerKey.numSlots(); i++ ) slotResults.add(new int[3]);
    int falseNegativesUnmatchedDocsTotalCount = 0;
    MUCKeyReader answerKey = _answerKey;
    
    // DEBUG: lists of missed entities
    List<MUCEntity>[] falseNegativesUnmatchedDocs = new ArrayList[_answerKey.numSlots()];
    List<MUCEntity>[] falseNegativesMatchedDocs = new ArrayList[_answerKey.numSlots()];
    for( int i = 0; i < _answerKey.numSlots(); i++ ) {
      falseNegativesUnmatchedDocs[i] = new ArrayList<MUCEntity>();
      falseNegativesMatchedDocs[i] = new ArrayList<MUCEntity>();
    }
    int unfilled = 0, unfilledCorrect = 0;
    
    System.out.println("EvalSpecific top: mutype=" + mucType + " perm=" + Arrays.toString(guessPermutation) + " on frame id=" + targetFrameID);

    // Over all stories...
    for( String storyname : allStories(answerKey, _docFrameGuesses) ) {
//    for( String storyname : answerKey.getStories() ) {
      if( debug ) System.out.println("story " + storyname);
      List<Frame> frames = _docFrameGuesses.get(storyname.toLowerCase());
      List<Template> templates = answerKey.getTemplates(storyname);
      if( debug ) {
        System.out.println("  with " + (frames == null ? 0 : frames.size())
          + " guessed frames and " + (templates == null ? 0 : templates.size()) + " gold templates");
        if( frames != null )
          for( Frame frame : frames )
            System.out.println("  guessed id=" + frame.getID());
      }

      // Get the guessed bombing frame and the gold bombing templates.
      Frame guessedFrame = framesContain(targetFrameID, frames);
      List<Template> goldTemplates = templatesContain(mucType, templates);

      if( debug ) System.out.println("guessed frame " + guessedFrame);

      // Nothing to do.
      if( goldTemplates == null && guessedFrame == null ) {  }

      // No gold templates, these are all false positives.
      else if( goldTemplates == null ) {
        if( debug ) System.out.println("No gold templates here.");
        // If only evaluating gold docs, then we ignore these docs with no gold templates.
        if( !goldDocsOnly ) {
          for( int rolei = 0; rolei < guessedFrame.getNumRoles(); rolei++ ) {
            int sloti = guessPermutation[rolei];
            if( sloti > -1 ) {
              if( debug ) System.out.println("False positives sloti=" + sloti);
              List<Integer> guessedIDs = guessedFrame.getEntityIDsOfRole(rolei);
              int[] overall = slotResults.get(sloti);
              if( debug ) System.out.println("  Adding " + guessedIDs.size() + " false positives.");
              //            for( Integer id : guessedIDs )
              //              System.out.println("IsWrong\t" + id + "\t" + guessedFrame.getEntity(id));
              overall[1] += guessedIDs.size();
              // Save debug info.
              for( int j = 0; j < guessedIDs.size(); j++ ) {
                EntityFiller entity = guessedFrame.getEntity(guessedIDs.get(j));
                entity.setCorrect(false);
                entity.setFalsePositive(true);
              }
            }
          }
        }
      }

      // Gold templates, record the matches.
      else {
        // Gold answers for each slot.
        List<List<MUCEntity>> goldSlots = getAllGoldSlots(goldTemplates, _answerKey.numSlots());
        if( debug ) System.out.println("gold slots: ");
        if( debug ) for( List<MUCEntity> slot : goldSlots ) System.out.println("  slot: " + slot);

        // False negatives.
        if( guessedFrame == null ) {
          for( int sloti = 0; sloti < goldSlots.size(); sloti++ ) {
            if( debug ) System.out.println("False negatives sloti=" + sloti);
            if( debug ) System.out.println("  Adding " + goldSlots.get(sloti).size() + " false negatives.");
            if( debug ) System.out.println("   - " + removeOptionals(goldSlots.get(sloti)).size() + " after removing optionals.");
            int[] overall = slotResults.get(sloti);
            overall[2] += removeOptionals(goldSlots.get(sloti)).size();
            falseNegativesUnmatchedDocsTotalCount += goldSlots.get(sloti).size(); 
            // DEBUGGING
            falseNegativesUnmatchedDocs[sloti].addAll(goldSlots.get(sloti));
          }
        }

        // Compare all guesses.
        else {
          Set<Integer> slotsChecked = new HashSet<Integer>();
          for( int rolei = 0; rolei < guessedFrame.getNumRoles(); rolei++ ) {
            int sloti = guessPermutation[rolei];
            if( sloti > -1 ) {
              if( debug ) System.out.println("Comparing rolei=" + rolei + " sloti=" + sloti);
              slotsChecked.add(sloti);
              List<String> guessedStrings = new ArrayList<String>();
              List<Integer> ids = guessedFrame.getEntityIDsOfRole(rolei);
              if( ids != null ) {
                for( Integer id : ids )
                  guessedStrings.add(guessedFrame.getEntity(id).string());
              }
              // Evaluate the guesses with the golds!  (last parameter is DEBUG)
              int[] matches = TemplateTester.evaluateEntities(goldSlots.get(sloti), guessedStrings, false);
              if( debug ) System.out.println("   matches " + Arrays.toString(matches));
              int[] overall = slotResults.get(sloti);
              for( int j = 0; j < overall.length; j++ ) overall[j] += matches[j];
              
              // Count how many slots we didn't guess anything for.
              if( matches[0] == 0 && matches[1] == 0 && matches[2] > 0 ) {
                if( matches[2] > 0)
                  unfilled++;
                else unfilledCorrect++;
              }
              
              // Save debug info.
              for( int j = 0; j < ids.size(); j++ ) {
                EntityFiller entity = guessedFrame.getEntity(ids.get(j));
                if( matches[3+j] == 1 ) entity.setCorrect(true);
                else {
                  entity.setCorrect(false);
                }
                entity.setFalsePositive(false);
              }
              // Save debug info.
              for( int j = 0; j < goldSlots.get(sloti).size(); j++ ) {
                if( matches[3+ids.size()+j] == 0 ) {
                  falseNegativesMatchedDocs[sloti].add(goldSlots.get(sloti).get(j));
//                  System.out.println("adding falseneg " + goldSlots.get(sloti).get(j));
                }
              }
            }
          }

          // Slots that we didn't match with the frame at all...
          for( int sloti = 0; sloti < goldSlots.size(); sloti++ ) {
            if( !slotsChecked.contains(sloti) ) {
              if( debug ) System.out.println("False negatives sloti=" + sloti);
              if( debug ) System.out.println("  Adding " + goldSlots.get(sloti).size() + " false negatives.");
              int[] overall = slotResults.get(sloti);
              overall[2] += goldSlots.get(sloti).size();
              // DEBUGGING
              falseNegativesMatchedDocs[sloti].addAll(goldSlots.get(sloti));
//              System.out.println("adding all falseneg " + goldSlots.get(sloti));
            }
          }
        }
      }
    }

    // Print the results!!
    System.out.println("EvalSpecific Overall Corpus Results (" + mucType + " perm=" + Arrays.toString(guessPermutation) + ")");
    System.out.println("  x = [correct, false-pos, false-neg]");
    for( int i = 0; i < slotResults.size(); i++ )
      System.out.println("  " + i + " = " + Arrays.toString(slotResults.get(i)));

    // Calculate the overall F1 score.
    int[] allscores = new int[slotResults.get(0).length];
    for( int i = 0; i < slotResults.size(); i++ ) {
      int[] scores = slotResults.get(i);

      // Don't count slots that had no gold entities in overall F1.
      if( scores[0] + scores[2] > 0 ) {
        for( int j = 0; j < scores.length; j++ )
          allscores[j] += scores[j];
      }
      
      // Debug output, per slot...
      float precision = ((float)scores[0] / (float)(scores[0]+scores[1]));
      float recall = ((float)scores[0] / (float)(scores[0]+scores[2]));
      float f1 = 2.0f * (precision*recall) / (precision+recall);
      System.out.printf("\tslot " + i + "\tprec=%.3f\trecall=%.3f\tf1=%.2f\n", precision, recall, f1);
    }
    double[] scores = score(allscores[0], allscores[1], allscores[0]+allscores[2]);
    System.out.printf("\tall\tprec=%.3f\trecall=%.3f\tf1=%.2f\n", scores[0], scores[1], scores[2]);
    double[] scoresMinusNegs = score(allscores[0], allscores[1], allscores[0]+allscores[2]-falseNegativesUnmatchedDocsTotalCount);
    System.out.printf("\tall*\tprec=%.3f\trecall=%.3f\tf1=%.2f\n", scoresMinusNegs[0], scoresMinusNegs[1], scoresMinusNegs[2]);
    
    
    // Print entity-level performance stats.
    if( debug ) {
      for( int i = 0; i < falseNegativesUnmatchedDocs.length; i++ )
        System.out.println("  " + i + " false negs from unmatched docs " + falseNegativesUnmatchedDocs[i].size());
      printFillerStats(targetFrameID, guessPermutation);
      System.out.println("False negs within matched docs: ");
      printFalseNegativeStats(falseNegativesMatchedDocs);
      System.out.println("False negs with unmatched docs: ");
      printFalseNegativeStats(falseNegativesUnmatchedDocs);
    }
    
    // Print unfilled counts.
    if( debug ) System.out.println("Unfilled slots that had gold fillers: " + unfilled);
    if( debug ) System.out.println("Unfilled slots that had no gold fillers: " + unfilledCorrect);
    
    return scores;
  }
  
  
  /**
   * Evaluates a single frame's slot guesses on a single document.
   * @param guessedFrame The frame with its role guesses.
   * @param guessPermutation The mapping from roles to MUC slots.
   * @param goldTemplates The list of gold templates for this document.
   * @param slotResults The Map that stores per-slot performance results.
   * @param debug If true, print debugging info.
   * @return Array of ints: (1) # MUC slots that we didn't guess, 
   *                        (2) # MUC slots that we didn't guess, and they are also empty in the gold template
   *                        (3) 1 if our guessedFrame is null, and there is a gold frame.  0 otherwise. (false negatives at the document level)
   */
  private int[] evaluateFrameOnDocument(Frame guessedFrame, int[] guessPermutation, List<Template> goldTemplates, List<int[]> slotResults, boolean debug,
      List<MUCEntity>[] falseNegativesUnmatchedDocs, List<MUCEntity>[] falseNegativesMatchedDocs) {
    if( debug ) System.out.println("guessed frame " + guessedFrame);

    int unfilled = 0, unfilledCorrect = 0, falseNegativesUnmatchedDocsTotalCount = 0;

    // Nothing to do.
    if( goldTemplates == null && guessedFrame == null ) {  }

    // No gold templates, these are all false positives.
    else if( goldTemplates == null ) {
      if( debug ) System.out.println("No gold templates here.");
      for( int rolei = 0; rolei < guessedFrame.getNumRoles(); rolei++ ) {
        int sloti = guessPermutation[rolei];
        if( sloti > -1 ) {
          if( debug ) System.out.println("False positives sloti=" + sloti);
          List<Integer> guessedIDs = guessedFrame.getEntityIDsOfRole(rolei);
          int[] overall = slotResults.get(sloti);
          if( debug ) System.out.println("  Adding " + guessedIDs.size() + " false positives.");
          overall[1] += guessedIDs.size();
          // Save debug info.
          for( int j = 0; j < guessedIDs.size(); j++ ) {
            EntityFiller entity = guessedFrame.getEntity(guessedIDs.get(j));
            entity.setCorrect(false);
            entity.setFalsePositive(true);
          }
        }
      }
    }

    // Gold templates, record the matches.
    else {
      // Gold answers for each slot.
      List<List<MUCEntity>> goldSlots = getAllGoldSlots(goldTemplates, _answerKey.numSlots());
      if( debug ) System.out.println("gold slots: ");
      if( debug ) for( List<MUCEntity> slot : goldSlots ) System.out.println("  slot: " + slot);

      // False negatives.
      if( guessedFrame == null ) {
        for( int sloti = 0; sloti < goldSlots.size(); sloti++ ) {
          if( debug ) System.out.println("False negatives sloti=" + sloti);
          if( debug ) System.out.println("  Adding " + goldSlots.get(sloti).size() + " false negatives.");
          int[] overall = slotResults.get(sloti);
          overall[2] += goldSlots.get(sloti).size();
          falseNegativesUnmatchedDocsTotalCount += goldSlots.get(sloti).size(); 
          // DEBUGGING
          if( falseNegativesUnmatchedDocs != null ) falseNegativesUnmatchedDocs[sloti].addAll(goldSlots.get(sloti));
        }
      }

      // Compare all guesses.
      else {
        Set<Integer> slotsChecked = new HashSet<Integer>();
        for( int rolei = 0; rolei < guessedFrame.getNumRoles(); rolei++ ) {
          int sloti = guessPermutation[rolei];
          if( sloti > -1 ) {
            if( debug ) System.out.println("Comparing rolei=" + rolei + " sloti=" + sloti);
            slotsChecked.add(sloti);
            List<String> guessedStrings = new ArrayList<String>();
            List<Integer> ids = guessedFrame.getEntityIDsOfRole(rolei);
            if( ids != null ) {
              for( Integer id : ids )
                guessedStrings.add(guessedFrame.getEntity(id).string());
            }
            // Evaluate the guesses with the golds!  (last parameter is DEBUG)
            int[] matches = TemplateTester.evaluateEntities(goldSlots.get(sloti), guessedStrings, false);
            if( debug ) System.out.println("   matches " + Arrays.toString(matches));
            int[] overall = slotResults.get(sloti);
            for( int j = 0; j < overall.length; j++ ) overall[j] += matches[j];

            // Count how many slots we didn't guess anything for.
            if( matches[0] == 0 && matches[1] == 0 && matches[2] > 0 ) {
              if( matches[2] > 0)
                unfilled++;
              else unfilledCorrect++;
            }

            // Save debug info.
            for( int j = 0; j < ids.size(); j++ ) {
              EntityFiller entity = guessedFrame.getEntity(ids.get(j));
              if( matches[3+j] == 1 ) entity.setCorrect(true);
              else {
                entity.setCorrect(false);
              }
              entity.setFalsePositive(false);
            }
            // Save debug info.
            for( int j = 0; j < goldSlots.get(sloti).size(); j++ ) {
              if( matches[3+ids.size()+j] == 0 ) {
                if( falseNegativesMatchedDocs != null ) falseNegativesMatchedDocs[sloti].add(goldSlots.get(sloti).get(j));
                //                System.out.println("adding falseneg " + goldSlots.get(sloti).get(j));
              }
            }
          }
        }

        // Slots that we didn't match with the frame at all...
        for( int sloti = 0; sloti < goldSlots.size(); sloti++ ) {
          if( !slotsChecked.contains(sloti) ) {
            if( debug ) System.out.println("False negatives sloti=" + sloti);
            if( debug ) System.out.println("  Adding " + goldSlots.get(sloti).size() + " false negatives.");
            int[] overall = slotResults.get(sloti);
            overall[2] += goldSlots.get(sloti).size();
            // DEBUGGING
            if( falseNegativesMatchedDocs != null ) falseNegativesMatchedDocs[sloti].addAll(goldSlots.get(sloti));
            //            System.out.println("adding all falseneg " + goldSlots.get(sloti));
          }
        }
      }
    }
    
    int[] counts = new int[3];
    counts[0] = unfilled;
    counts[1] = unfilledCorrect;
    counts[2] = falseNegativesUnmatchedDocsTotalCount;
    return counts;
  }
  
  
  private void printFalseNegativeStats(List<MUCEntity>[] falseNegativesMatchedDocs) {
    System.out.println("printFalseNegativeStats top with " + falseNegativesMatchedDocs.length + " slots.");
    for( int sloti = 0; sloti < falseNegativesMatchedDocs.length; sloti++ ) 
      System.out.println("Slot " + sloti + " num instances: " + falseNegativesMatchedDocs[sloti].size());
    
    for( int sloti = 0; sloti < falseNegativesMatchedDocs.length; sloti++ ) {
      Map<String,Integer> counts = new HashMap<String,Integer>();
      for( MUCEntity entity : falseNegativesMatchedDocs[sloti] ) {
        for( String mentionStr : entity.getMentions() ) {
          int space = mentionStr.lastIndexOf(' ');
          if( space > -1 )
            mentionStr = mentionStr.substring(space+1);
          Util.incrementCount(counts, mentionStr, 1);
        }
      }
      System.out.println("FALSE NEGS " + sloti);
      for( String key : counts.keySet() )
        System.out.print(" " + key + " " + counts.get(key));
      System.out.println();
    }
  }
  
  private void printFillerStats(int targetFrameID, int[] guessPermutation) {
    Map<String,int[]> stats = new HashMap<String, int[]>();
    String[] strings = { "TOTAL", "TOTAL0", "TOTAL1", "TOTAL2", "TOTAL3",
        "TOP-ARG", "TOP-ARG0", "TOP-ARG1", "TOP-ARG2", "TOP-ARG3",
        "NEARBY-SLOT", "NEARBY-SLOT0", "NEARBY-SLOT1", "NEARBY-SLOT2", "NEARBY-SLOT3",
        "TRIGGER-SLOT", "TRIGGER-SLOT0", "TRIGGER-SLOT1", "TRIGGER-SLOT2", "TRIGGER-SLOT3" };
    for( String str : strings ) stats.put(str, new int[3]);
    System.out.println("printFillerStats target ID " + targetFrameID);
    System.out.println("printFillerStats [correct, incorrect, guesses-on-incorrect-docs]");
    
    // TOP-ARG1 -> "bomb" -> [3,2,0]
    Map<String,Map<String,int[]>> statsTopArgs = new HashMap<String,Map<String,int[]>>();
    for( String str : strings ) statsTopArgs.put(str, new HashMap<String,int[]>());
    // NEARBY-SLOT1 -> "v-injure:s" -> [3,2,0]
    Map<String,Map<String,int[]>> statsTopPatterns = new HashMap<String,Map<String,int[]>>();
    for( String str : strings ) statsTopPatterns.put(str, new HashMap<String,int[]>());
    
    // For each document.
    for( String storyname : _docFrameGuesses.keySet() ) {
      List<Frame> frames = _docFrameGuesses.get(storyname);
      Frame guessedFrame = framesContain(targetFrameID, frames);
      if( guessedFrame != null ) {
        for( int rolei = 0; rolei < guessedFrame.getNumRoles(); rolei++ ) {
          int sloti = guessPermutation[rolei];
          if( sloti > -1 ) {
            List<Integer> ids = guessedFrame.getEntityIDsOfRole(rolei);
            for( Integer id : ids ) {
              EntityFiller entity = guessedFrame.getEntity(id);

              // arg strings correct
              Map<String,int[]> argMap = statsTopArgs.get(entity.filledMethod() + sloti);
              String head = entity.string().toLowerCase();
              if( head.indexOf(' ') > -1 ) head = head.substring(head.lastIndexOf(' ')+1);
              int[] argCounts = argMap.get(head);
              if( argCounts == null ) {
                argCounts = new int[3];
                argMap.put(head, argCounts);
              }

              // pattern strings correct
              Map<String,int[]> patternMap = statsTopPatterns.get(entity.filledMethod() + sloti);
              String pattern = entity.matchedPattern();
              int[] patternCounts = patternMap.get(pattern);
              if( patternCounts == null ) {
                patternCounts = new int[3];
                patternMap.put(pattern, patternCounts);
              }
              
              if( entity.isCorrect() ) {
                stats.get("TOTAL")[0]++;
                stats.get("TOTAL" + sloti)[0]++;
                stats.get(entity.filledMethod())[0]++;
                stats.get(entity.filledMethod() + sloti)[0]++;
                String name = entity.filledMethod() + sloti;
                if( name.equals("TRIGGER-SLOT0") )
                  System.out.println("Right\t" + storyname + ": " + entity);
                argCounts[0]++;
                patternCounts[0]++;
              }
              else if( !entity.isFalsePositive() ) {
                stats.get("TOTAL")[1]++;
                stats.get("TOTAL" + sloti)[1]++;
                stats.get(entity.filledMethod())[1]++;
                stats.get(entity.filledMethod() + sloti)[1]++;
                String name = entity.filledMethod() + sloti;
                if( name.equals("TRIGGER-SLOT0") )
                  System.out.println("Wrong\t" + storyname + ": " + entity);
                argCounts[1]++;
                patternCounts[1]++;
              }
              else {
//                System.out.println("Other match: slot " + sloti + " rolei " + rolei + " entity "+ entity);
                stats.get("TOTAL")[2]++;
                stats.get("TOTAL" + sloti)[2]++;
                stats.get(entity.filledMethod())[2]++;
                stats.get(entity.filledMethod() + sloti)[2]++;
                argCounts[2]++;
                patternCounts[2]++;
              }
            }
          }
        }
      }
    }
    String[] sorted = new String[stats.size()];
    sorted = stats.keySet().toArray(sorted);
    Arrays.sort(sorted);
    for( String key : sorted )
      System.out.println(key + ": " + Arrays.toString(stats.get(key)));

    // args
    sorted = new String[stats.size()];
    sorted = statsTopArgs.keySet().toArray(sorted);
    Arrays.sort(sorted);
    for( String key : sorted ) {
      System.out.print(key + "\n\t");
      for( String arg : statsTopArgs.get(key).keySet() ) {
        System.out.print(" " + arg + " " + Arrays.toString(statsTopArgs.get(key).get(arg)));
      }
      System.out.println();
    }
    
    // patterns
    sorted = new String[stats.size()];
    sorted = statsTopPatterns.keySet().toArray(sorted);
    Arrays.sort(sorted);
    for( String key : sorted ) {
      System.out.print(key + "\n\t");
      for( String pattern : statsTopPatterns.get(key).keySet() ) {
        System.out.print(" " + pattern + " " + Arrays.toString(statsTopPatterns.get(key).get(pattern)));
      }
      System.out.println();
    }
  }
  
  /**
   * Take the union of the stories in the answer key and those that we guessed.
   * @param answerKey
   * @param docFrameGuesses
   * @return
   */
  private String[] allStories(MUCKeyReader answerKey, Map<String,List<Frame>> docFrameGuesses) {
    // Get all answer and guess stories.
    Collection<String> answerStories = answerKey.getStories();
    Set<String> guessStories = docFrameGuesses.keySet();
    
    // Take the union.
    Set<String> merged = new HashSet<String>(guessStories);
    merged.addAll(answerStories);
    String[] arr = new String[merged.size()];
    arr = merged.toArray(arr);
    Arrays.sort(arr);
    
    return arr;
  }
  
  /**
   * @return True if the two arrays contain equal elements. False otherwise.
   */
  private static boolean permutationMatch(int[] perm1, int[] perm2) {
    if( perm1 == null && perm2 == null)
      return true;
    
    if( perm1 != null && perm2 != null && perm1.length == perm2.length) {
      for( int i = 0; i < perm1.length; i++ )
        if( perm1[i] != perm2[i] )
          return false;
      return true;
    }
    return false;
  }
  
  /**
   * @return True if the list of arrays contains an array equal to the given perm array.
   */
  private static boolean containsPermutation(List<int[]> perms, int[] perm) {
    for( int[] aperm : perms )
      if( permutationMatch(aperm, perm) )
        return true;
    return false;
  }
  
  /**
   * Creates a new List that is identical to the given one, but with duplicate arrays removed.
   * @param perms
   * @return
   */
  private static List<int[]> removeDuplicatPermutations(List<int[]> perms) {
    if( perms == null )
      return null;
    
    List<int[]> all = new ArrayList<int[]>();
    for( int[] perm : perms )
      if( !containsPermutation(all, perm) )
        all.add(perm);
    return all;
  }
  
  public static List<int[]> permutations(List<Integer> domain, int[] mappings) {
//    System.out.println("domain=" + domain + " map=" + Arrays.toString(mappings));

    // Add dummy slots, so we also have permutations that don't use all of the given
    // domain integers.
    if( mappings.length > 1 )
      for( int i = 0; i < mappings.length-1; i++ )
        domain.add(-2);
    
//    System.out.println("domain=" + domain + " map=" + Arrays.toString(mappings));
    
    if( mappings.length < domain.size() )
      return removeDuplicatPermutations(permutationsShortMapping(domain, mappings));
    else
      return removeDuplicatPermutations(permutationsLongMapping(domain, mappings));
  }

  /**
   * This function returns all possible mappings of the domain's slots (MUC template slots)
   * to the semantic roles of frames (induced roles).  The domain list should just be the indices
   * of the MUC template slots, usually just an ordered list: 1,2,3,4.  The mappings should
   * initially all be set to -1, one each for each role in the induced Frame.  This function
   * will return arrays of the same size as the mappings, but with the MUC indices assigned to
   * each role.  If there are more mappings than domains, it returns all permutations including
   * leaving some of the roles unmapped.  These are one-to-one mappings.
   * Mappings should be or longer than the domain.
   * @param domain List of indices of MUC slots e.g. {1,2,3,4}
   * @param mappings List of assignments, initialized {-1,-1,-1,-1}.
   * @return All possible permutations of one-to-one mappings from the mappings array to the domain.
   */
  public static List<int[]> permutationsLongMapping(List<Integer> domain, int[] mappings) {
    // Recursion finished, return null.
    if( domain == null || domain.size() == 0 ) return null;
    //    for( int tomap = 0; tomap < mappings.length; tomap++ ) {
    //      if( mappings[tomap] == -1 ) break;
    //      return mappings;
    //    }


    List<int[]> permutations = new ArrayList<int[]>();

    // Loop over the mappings.
    for( int tomap = 0; tomap < mappings.length; tomap++ ) {
      // If we found an unassigned mapping, assign the next role, then recurse.
      if( mappings[tomap] == -1 ) {

        // Clone the mapping, and put the domain assignment in.
        int[] newmapping = new int[mappings.length];
        for( int j = 0; j < mappings.length; j++ )
          newmapping[j] = mappings[j];
        newmapping[tomap] = domain.get(0);

        // Recursion.
        List<Integer> remaining = domain.subList(1, domain.size());
        List<int[]> subPermutations = permutationsLongMapping(remaining, newmapping);
        if( subPermutations != null )
          permutations.addAll(subPermutations);
        else permutations.add(newmapping);
      }
    }
    return permutations;
  }

  /**
   * Derives all permutations of the domain numbers in the mappings slot, assuming
   * that there are less mappings slots than domain integers.
   * domain = [ 1 2 3 4 ]
   * mapping = [ -1 -1 -1 ]
   */
  public static List<int[]> permutationsShortMapping(List<Integer> domain, int[] mappings) {
    // Recursion finished, return null.
    if( domain == null || domain.size() == 0 ) return null;
    List<int[]> permutations = new ArrayList<int[]>();

    // Find the mapping slot to set.
    int tomap = -1;
    for( int i = 0; i < mappings.length; i++ ) {
      if( mappings[i] == -1 ) {
        tomap = i;
        break;
      }
    }
    if( tomap == -1 ) return null;

    // Loop over the mappings.
    for( Integer domaini : domain ) {
      // Clone the mapping, and put the domain assignment in.
      int[] newmapping = new int[mappings.length];
      for( int j = 0; j < mappings.length; j++ )
        newmapping[j] = mappings[j];
      newmapping[tomap] = domaini;
      // Clone the domain, remove the integer we are currently mapping.
      List<Integer> newdomain = new ArrayList<Integer>(domain);
      newdomain.remove(domaini);

      // Recursion.
      List<int[]> subPermutations = permutationsShortMapping(newdomain, newmapping);
      if( subPermutations != null )
        permutations.addAll(subPermutations);
      else permutations.add(newmapping);
    }
    return permutations;
  }
  
  /**
   * @return True if all the templates are optional, or if the given list is null.
   *         False otherwise.
   */
  private boolean allOptional(List<MUCTemplate> templates) {
    if( templates != null )
      for( MUCTemplate template : templates ) {
        if( !template.isOptional() )
          return false;
      }
    return true;  
  }
  
  private int indexOf(List<ScoredFrame> frames, int frameid) {
    if( frames != null ) {
      int i = 0;
      for( ScoredFrame frame : frames ) {
        if( frame.frame().getID() == frameid )
          return i;
        i++;
      }
    }
    return -1;
  }
  
  // DEBUGGING
  public void evalSpecific(int frameid, String mucType, Map<String,List<ScoredFrame>> guessMap) {
    int truePos2 = 0, falsePos2 = 0, total = 0;
    int truePos4 = 0, falsePos4 = 0;
    int truePos8 = 0, falsePos8 = 0;
    int truePos15 = 0, falsePos15 = 0;
    int altTruePos2 = 0, altTruePos4 = 0, altTruePos8 = 0, altTruePos15 = 0, altTotal = _altKidnapStories.size();
    double goldSum = 0.0, nongoldSum = 0.0;
    int numGoldDocs = 0, numOtherDocs = 0;
    System.out.println("EvalSpecific frame " + frameid + " type " + mucType);
    System.out.println("guessMap size " + guessMap.size());
    
    for( String story : Util.sortStrings(guessMap.keySet()) ) {
      List<ScoredFrame> guesses = guessMap.get(story.toLowerCase());

      List<Template> goldTemplates = _answerKey.getTemplates(story);

      int index = indexOf(guesses, frameid);
      List<Template> matchedTemplates = templatesContain(mucType, goldTemplates);

      // Our frame type matches the gold template frame type (e.g. kidnap).
      if( index > -1 && matchedTemplates != null ) {
        if( index < 2 ) truePos2++;
        else if( index < 4 ) truePos4++;
        else if( index < 8 ) truePos8++;
        else if( index < 15 ) truePos15++;
      }
      // Our frame is a different type than the gold template.
      else if( index > -1 && matchedTemplates == null ) {
        if( index < 2 ) falsePos2++;
        else if( index < 4 ) falsePos4++;
        else if( index < 8 ) falsePos8++;
        else if( index < 15 ) falsePos15++;
      }
   
      if( matchedTemplates != null ) {
        total++;
      }
      
      // Average the scores for the golds and non-golds.
      if( index > -1 ) {
        if( matchedTemplates != null ) goldSum += guesses.get(index).score();
        else nongoldSum += guesses.get(index).score();
      }      
      if( matchedTemplates == null ) numOtherDocs++;
      else numGoldDocs++;
      
      if( _altKidnapStories.contains(story.toLowerCase()) && index > -1 ) {
        if( index < 2 ) altTruePos2++;
        else if( index < 4 ) altTruePos4++;
        else if( index < 8 ) altTruePos8++;
        else if( index < 15 ) altTruePos15++;
      }
    }
    
    System.out.println("Trues top 2  " + truePos2);
    System.out.println("Trues top 4  " + truePos4);
    System.out.println("Trues top 8  " + truePos8);
    System.out.println("Trues top 15 " + truePos15);
    System.out.println("Falses top 2  " + falsePos2);
    System.out.println("Falses top 4  " + falsePos4);
    System.out.println("Falses top 8  " + falsePos8);
    System.out.println("Falses top 15 " + falsePos15);
    System.out.println("Average score on golds " + (goldSum / (double)numGoldDocs));
    System.out.println("Average score on non-golds " + (nongoldSum / (double)numOtherDocs));
    System.out.println("Total Gold " + numGoldDocs);
    System.out.println("Total " + total);
    System.out.println("Falses matching alt-kidnaps " + altTruePos2 + " " + altTruePos4 + " " + altTruePos8 + " " + altTruePos15 + " of " + altTotal);
  }

  
  // TESTING
  public static void main(String[] args) {
    int[] mapping = { -1, -1, -1, -1, -1, -1 };
    List<Integer> mucids = new ArrayList<Integer>();
    mucids.add(1);
    mucids.add(2);
    mucids.add(3);
    mucids.add(4);
    for( int[] perm : EvaluateTemplates.permutations(mucids, mapping) ) {
      System.out.println("perm " + Arrays.toString(perm));
    }
  }
}

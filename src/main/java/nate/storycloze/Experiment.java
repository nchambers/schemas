package nate.storycloze;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import nate.CountTokenPairs;
import nate.CountVerbDepCorefs;
import nate.EntityMention;
import nate.IDFMap;
import nate.WordEvent;
import nate.args.VerbArgCounts;
import nate.narrative.EventPairScores;
import nate.reading.ProcessedData;
import nate.reading.ProcessedDocument;
import nate.util.Directory;
import nate.util.HandleParameters;
import nate.util.PMICalculator;
import nate.util.WordNet;


/**
 * Main class to run the Story Cloze experiment.
 *
 */
public class Experiment {
	List<StoryClozeTest> _clozeTests;
  WordNet wordnet;
	int lastSentenceIndex = 5; // 5 sentence long stories, first sentence is 1 (not 0)
	
	EventPairScores _pairScores;
	
	
	public Experiment(String[] args) {
	  _clozeTests = new LinkedList<>();
	  
	  HandleParameters params = new HandleParameters(args);
	  wordnet = new WordNet(WordNet.findWordnetPath());
	  
	  // IDF
	  IDFMap domainIDF = new IDFMap(params.get("-idf"));

	  // Single event counts
//	  CountVerbDepCorefs _relnCountsDomain = new CountVerbDepCorefs(params.get("-depcounts"));

	  // Arg counts with single events.
//	  VerbArgCounts _domainSlotArgCounts = new VerbArgCounts(params.get("-argcounts"), 1);

	  // Event pair counts
	  CountTokenPairs domainTokenPairCounts = new CountTokenPairs(params.get("-paircounts"));
	  _pairScores = pairCountsToPMI(domainTokenPairCounts, domainIDF, null, 2, .7);

	  // Read the Cloze story tests.
	  readClozeTests(params.get("-stories"));
	  
	  System.out.println("Done constructor.");
	}

	/**
	 * Given the directory where cloze tests were preprocessed, read it all in.
	 * @param filepath
	 */
	public void readClozeTests(String dir) {
	  ProcessedData storyReader = new ProcessedData(dir + File.separator + Directory.nearestFile(".parse", dir), 
        dir + File.separator + Directory.nearestFile(".deps", dir), 
        dir + File.separator + Directory.nearestFile(".events", dir), 
        dir + File.separator + Directory.nearestFile(".ner", dir));
    storyReader.nextStory();
    
    do {
      // Read in two documents at a time to make a cloze test.
      ProcessedDocument doc1 = storyReader.getDocumentCloned();
      storyReader.nextStory();
      ProcessedDocument doc2 = storyReader.getDocumentCloned();
      StoryClozeTest test = new StoryClozeTest(doc1, doc2);
      _clozeTests.add(test);

//      System.out.println("Got doc1 = " + test.option0.storyname + " with " + test.option0.deps.size());
//      System.out.println("Got doc2 = " + doc2.storyname + " with " + doc2.deps.size());

      // Advance to the next story.
      storyReader.nextStory();
    } while( storyReader.currentStory() != null );
    
    System.out.println("Read in " + _clozeTests.size() + " cloze tests.");
	}

	/**
	 * Loops over the cloze tests.
	 * Scores each of the two options in a cloze tests and chooses the higher score.
	 * Prints out accuracy scores.
	 */
	public void runClozeTests() {
	  int predictions[] = new int[_clozeTests.size()];
	  int i = 0;
	  
	  // Make predictions.
	  for( StoryClozeTest test : _clozeTests ) {
	    double score0 = scoreTest(test.option0);
	    double score1 = scoreTest(test.option1);
	    if( score0 > score1 )
	      predictions[i++] = 0;
	    else if( score1 > score0 )
	      predictions[i++] = 1;
	    else // if tied, unknown
	      predictions[i++] = -1;
	  }
	  
	  // Calculate accuracy.
	  int correct = 0, incorrect = 0, skipped = 0, allcorrect = 0, allincorrect = 0;
	  for( int j = 0; j < _clozeTests.size(); j++ ) {
	    // If we made a guess.
	    if( predictions[j] >= 0 ) {
	      if( _clozeTests.get(j).isCorrect(predictions[j]) ) {
	        correct++;
	        allcorrect++;
	      }
	      else {
	        incorrect++;
	        allincorrect++;
	      }
	    }
	    // Unknown. Make a guess!
	    else {
	      skipped++;
	      predictions[j] = (int)(Math.random()*2.0);
	      if( _clozeTests.get(j).isCorrect(predictions[j]) )
          allcorrect++;
        else
          allincorrect++;
	    }
	  }
	  
	  // Write guesses to file for safekeeping.
    try {
      BufferedWriter out;
      out = new BufferedWriter(new FileWriter("experiment-guesses.txt"));
      for( int j = 0; j < _clozeTests.size(); j++ ) {
        out.write((predictions[j]+1) + "\n");
      }
      out.close();
    } catch( IOException ex ) {
      ex.printStackTrace();
    }
    
	  // Print summary stats.
	  int total = _clozeTests.size();
	  System.out.println("Didn't guess on " + skipped + " of " + total + " tests.");
    System.out.printf("Precision = %d/%d = %.2f\n", correct, (correct+incorrect), ((double)correct/(correct+incorrect)));
    System.out.printf("Accuracy  = %d/%d = %.2f\n", allcorrect, total, ((double)allcorrect/total));
	}

	/**
	 * Receives a document with n sentences. Assumes the final sentence is the confounder.
	 * Finds the chain with the highest score that includes an event in the final sentence. 
	 * @param thedoc
	 */
	private double scoreTest(ProcessedDocument thedoc) {
	  List<EntityMention> mentions = thedoc.mentions;
	  double overall = 0.0;
	  
	  System.out.println("***************\nscoreTest with " + thedoc.storyname);
	  
	  // Get all events and entity mentions associated with them.
    List<WordEvent> events = CountTokenPairs.extractEvents(thedoc.trees(), thedoc.deps, thedoc.mentions, wordnet, CountTokenPairs.VERBS_AND_NOMINALS, false);

    // Grab all events in the final sentence.
    Set<WordEvent> lastEvents = new HashSet<>();
    for( WordEvent event : events ) {
      if( event.sentenceID() == lastSentenceIndex )
        lastEvents.add(event);
    }

    // Find events that match with the last sentence.
    for( WordEvent event : events ) {

      // Make sure it's not a final sentence event.
      if( event.sentenceID() < lastSentenceIndex ) {
        for( WordEvent lastEvent : lastEvents ) {

          System.out.println("Checking " + event.token() + " with " + lastEvent.token());
          
          // Do they share an argument?
          String shared = event.sharedArgument(lastEvent.arguments());
          
          // If w1 hasn't already paired with a similar w2.
          if( shared != null ) {

            int colon = shared.indexOf(':');
            String key1 = CountTokenPairs.attachRelation(event.token(), shared.substring(0,colon));
            String key2 = CountTokenPairs.attachRelation(lastEvent.token(), shared.substring(colon+1));
            double score = _pairScores.getScore(key1, key2);
            overall += score;

//            System.out.println("Shared: " + event + " and " + lastEvent + " pattern=" + shared);
            System.out.println("Shared: " + key1 + " and " + key2 + "\tSCORE=" + score);
          }
        }
      }
    }
    System.out.println("SCORE: " + thedoc.storyname + " = " + overall);
    return overall;
	}
	
	/**
	 * Similar function in StatisticsDeps.java, but that only does token pairs. We need token:dep pairs.
	 * This makes the change to handle the token:dep strings.
	 */
  public static EventPairScores pairCountsToPMI(CountTokenPairs paircounts, IDFMap domainIDF, Set<String> keeplist, int docCountCutoff, double pairCountCutoff) {
    EventPairScores cache = new EventPairScores();
    // Calculate PMI scores for pairs.
    System.out.println("calculating pmis...");
    for( String key1 : paircounts.floatKeySet() ) {
      String lemma1 = CountTokenPairs.detachToken(key1);
      if( keeplist == null || keeplist.contains(lemma1) ) {

        if( domainIDF.getDocCount(lemma1) > docCountCutoff ) {
          for( String key2 : paircounts.floatKeySet(key1) ) {
            String lemma2 = CountTokenPairs.detachToken(key2);
            if( keeplist == null || keeplist.contains(lemma2) ) {
              if( domainIDF.getDocCount(lemma2) > docCountCutoff ) {

                double pairCount = paircounts.getCount(key1, key2);
                if( pairCount > pairCountCutoff ) {
                  int freq1 = domainIDF.getDocCount(lemma1);
                  int freq2 = domainIDF.getDocCount(lemma2);
                  if( freq1 != 0 && freq2 != 0 ) {
                    double pmi = PMICalculator.calculatePMI(pairCount, paircounts.getTotalCount(),
                        freq1, freq2, domainIDF.totalCorpusCount(), false);
                    int min = (freq1 < freq2 ? freq1 : freq2);
                    pmi *= (double)((double)min / (double)(min + 10));
                    cache.addScore(key1, key2, (float)pmi);
                  }
                }
              }
            }
          }
        }
      }
    }
    return cache;
  }
	
	public static void main(String[] args) {
	  Experiment exp = new Experiment(args);
	  exp.runClozeTests();
	}

}

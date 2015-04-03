package nate.util;

import java.util.Random;
import java.util.List;
import java.util.LinkedList;


/**
 * This class calculates statistical significance of the difference between
 * two guess lists over the same test set.  This implements the approximate
 * randomization approach, sampling between the two guess lists to see how
 * many of the samples result in score differences greater than the actual
 * difference.  It returns the p-value (the probability that the actual
 * score difference is by chance).  Lower p-value the better!
 *
 * Currently the code just takes two files with one test result per line.
 * 0 means incorrect, 1 means correct.
 *
 * The code can be adapted to take actual guesses that need to be evaluated
 * with a non binary correct decision.
 *
 */
public class SigTestApproxRand {
  int _numShuffles = 1000;


  public SigTestApproxRand() {
  }


  /**
   * A dummy placeholder:
   *   - 1 is a correct guess
   *   - 0 is an incorrect guess
   *
   * We need better evaluate functions if it is a multi-class problem where
   * we have to actually compare guesses to golds.
   */
  private double evaluate(Integer guess) {
    return guess;
  }

  private double score(List<Integer> guesses) {
    double score = 0.0;

    for( Integer guess : guesses ) {
      score += evaluate(guess);
    }
    return score / (double)guesses.size();
  }


  /**
   *  1 is a correct guess
   *  0 is an incorrect guess
   * -1 is no response
   */
  private double scorePrecision(List<Integer> guesses) {
    int score = 0;
    int numGuesses = 0;

    for( Integer guess : guesses ) {
      if( guess > -1 ) {
	score += guess;
	numGuesses++;
      }
    }
    return (double)score / (double)numGuesses;
  }


  /**
   * Calculate the p-value between two guess lists over the same test examples.
   * Both lists should be from the same tests, so the same length in length.
   * Performs approximate randomization to sample the null distribution that 
   * the two lists are from the same system.  A low p-value indicates that the
   * two lists have a low probability in the sampled distribution.
   * A low p-value thus rejects the null hypothesis.
   * @param doPrecision Use Precision as the metric to score, not overall accuracy.
   */
  public double calculatePValue(List<Integer> guess1, List<Integer> guess2, boolean doPrecision) {
    int numTests = guess1.size();

    if( numTests != guess2.size() ) {
      System.out.println("ERROR: guess lists of different sizes!");
      System.exit(-1);
    }

    // Score the difference.
    double score1 = (doPrecision ? scorePrecision(guess1) : score(guess1));
    double score2 = (doPrecision ? scorePrecision(guess2) : score(guess2));
    double actualDiff = Math.abs(score1 - score2);
    System.out.printf("score1=%.2f%% score2=%.2f%%\n", (100*score1), (100*score2));
    
    // Got this from JavaNLP MT code.
    //    Random r = new Random(8682522807148012L);
    Random r = new Random();
    int matched = 0;

    // Sample repeatedly.
    for( int i = 0; i < _numShuffles; i++ ) {

      // Create the new sample lists.
      List<Integer> pseudo1 = new LinkedList();
      List<Integer> pseudo2 = new LinkedList();

      // Randomly samply by flipping each guess.
      for( int j = 0; j < numTests; j++ ) {
	double rand = r.nextDouble();
	if( rand <= 0.5f ) {
	  pseudo1.add(guess1.get(j));
	  pseudo2.add(guess2.get(j));
	} else {
	  pseudo2.add(guess1.get(j));
	  pseudo1.add(guess2.get(j));
	}
      }

      // Calculate the difference in score of the samples.
      score1 = (doPrecision ? scorePrecision(pseudo1) : score(pseudo1));
      score2 = (doPrecision ? scorePrecision(pseudo2) : score(pseudo2));
      if( Math.abs(score1 - score2) >= actualDiff )
	matched++;
    }

    System.out.println("matched or exceeded " + matched + " of " + _numShuffles);
    return (matched + 1.0) / (_numShuffles + 1.0);
  }


  
  public static void main(String args[]) {
    double p = 0.0f;

    if( args.length < 2 ) {
      System.out.println("SigTestApproxRand [-precision] <guess1-file> <guess2-file>");
      System.exit(-1);
    }

    // p-value of precision
    if( args[0].equals("-precision") ) {
      List<Integer> guess1 = Util.slurpIntegers(args[1]);
      List<Integer> guess2 = Util.slurpIntegers(args[2]);
      
      SigTestApproxRand test = new SigTestApproxRand();
      p = test.calculatePValue(guess1, guess2, true);
    }
    // p-value of overall accuracy
    else {
      List<Integer> guess1 = Util.slurpIntegers(args[0]);
      List<Integer> guess2 = Util.slurpIntegers(args[1]);
      
      SigTestApproxRand test = new SigTestApproxRand();
      p = test.calculatePValue(guess1, guess2, false);
    }

    System.out.println("p = " + p);
  }
}

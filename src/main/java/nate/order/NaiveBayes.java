package nate.order;

import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;
import java.util.Arrays;
import java.io.BufferedWriter;


// CAUTION: This implementation does not do smoothing correctly
//
// The older class in acl-paper/ performs better on event classes.
//

public class NaiveBayes {
  Object order[];
  int NUM_FEATURES;
  int NUM_CLASSES;
  int limitsizes[];
  double SMOOTH = .5; // 1 for Laplace
  double ALPHA = 1000.0; // unbalanced smoothing

  int catCounts[];
  int counts[][][];
  double probX[][];
  double probClass[];
  double probGivenY[][][];

  // Used when training two different distributions
  int catCounts2[];
  int counts2[][][];
  double probClass2[];
  double probGivenY2[][][];


  NaiveBayes(Object order[], int limits[], int classes) { 
    this.order = order;
    limitsizes = limits;
    NUM_FEATURES = limits.length;
    NUM_CLASSES = classes;
    initArrays();
  }

  private void initArrays() {
    System.out.println("initArrays with " + NUM_CLASSES + " classes & " +
		       NUM_FEATURES + " features");
    catCounts = new int[NUM_CLASSES];
    counts = new int[NUM_CLASSES][NUM_FEATURES][max(limitsizes)];
    probGivenY = new double[NUM_CLASSES][NUM_FEATURES][max(limitsizes)];
    probX = new double[NUM_FEATURES][max(limitsizes)];
  }

  public void setNumClasses(int num) { 
    NUM_CLASSES = num;
    // rebuild the counts arrays
    initArrays();
  }

  public void initBiDistribution() {
    catCounts2 = new int[NUM_CLASSES];
    counts2 = new int[NUM_CLASSES][NUM_FEATURES][max(limitsizes)];
    probGivenY2 = new double[NUM_CLASSES][NUM_FEATURES][max(limitsizes)];
  }


  public int[] test(Vector<Features> dataset, boolean keep[]) {
    return test(dataset, keep, null);
  }

  public int[] test(Vector<Features> dataset, boolean keep[], BufferedWriter writer) {
    int correct = 0, i = 0;
    int guesses[] = new int[dataset.size()];

    for( Features vec : dataset ) {
      int gold = Integer.valueOf(vec.relation());
      int guess = classify(vec, keep, probGivenY, probClass);
      if( writer != null ) {
	try {
	  writer.write(String.valueOf(guess));
	  writer.newLine();
	} catch( Exception ex ) { ex.printStackTrace(); }  // shhh
      }
      if( guess == gold ) correct++;
      guesses[i] = guess;
      i++;
    }

    return guesses;
  }


  public int[] testBi(Vector<Features> dataset, boolean keep[], boolean keep2[]) {
    int guess, correct = 0, i = 0;
    int guesses[] = new int[dataset.size()];

    for( Features vec : dataset ) {
      //      FeatureVector vec = (FeatureVector)it.next();

      // Make the classification with the correct distribution
      if( vec.get(FeatureType.SAME_SENTENCE).equals("1") )
	guess = classify(vec, keep, probGivenY, probClass);
      else guess = classify(vec, keep2, probGivenY2, probClass2);

      if( guess == Integer.valueOf(vec.relation()) ) correct++;
      guesses[i] = guess;
      i++;
    }

    //    double acc = (double)correct / (double)dataset.size();
    //    return acc;
    return guesses;
  }


  /**
   * Get the highest probability class
   * @return The chosen class
   */
  public int classify(Features vec, boolean keep[], 
		      double probGivenY[][][], double probClass[] ) {
    double probs[] = fullClassify(vec, keep, probGivenY, probClass);
    //for( int i = 0; i < probs.length; i++ ) System.out.print(probs[i] + " ");
    return argmax(probs)+1;
  }


  /**
   * Get the probability of every class for this feature vector
   * @return An array of probabilities the size of the number of classes
   */
  public double[] fullClassify(Features vec, boolean keep[],
			       double probGivenY[][][], double probClass[] ) {
    double probs[] = new double[probClass.length];
    double total = 0;
    double sum = 0;

    for( int j = 0; j < probClass.length; j++ ) {
      double prob = 0;
      double denom = 0;
      for( int k = 0; k < NUM_FEATURES; k++ ) {
	// only use features that are turned on
	if( keep[k] ) {
	  //	  System.out.println("keep " + k);
	  int val = Integer.valueOf(vec.get(order[k]));
	  prob += probGivenY[j][k][val-1]; // MINUS ONE for arrays
	  //	  prob += probGivenY[j][k][val-1] - probX[k][val-1]; // normalize
	  //	  denom += probX[k][val-1];
	  //	  System.out.println("P(x=" + (val-1) + "| y=" + j + ") = " + Math.exp(probGivenY[j][k][val-1]));
	}
      }
      /*
      System.out.println("P(X|Y) = " + Math.exp(prob));
      System.out.println("P(Y) = " + Math.exp(probClass[j]));
      System.out.println("P(X|Y)*P(Y) = " + Math.exp(prob) * Math.exp(probClass[j]));
      System.out.println("P(X) = " + Math.exp(denom));
      */
      probs[j] = prob + probClass[j]; // P(X|Y)*P(Y)
      //      probs[j] = probs[j] - denom; // P(X|Y)*P(Y) / P(X)
      //      System.out.println("P(Y=" + j + "|X) = " + Math.exp(probs[j]));
      //      sum += Math.exp(probs[j]);
     }

    /*
    System.out.println("sum = " + sum);
    if( sum < .999 || sum > 1.00001 ) { 
      System.err.println("P(Y|X) is not a probability.");
      System.exit(1);
    }
    */

    return probs;
  }


  /**
   * @param vec The feature values
   * @param keep The active features you want turned on
   * @param bi Set to true if you've trained a bi model and want to classify
   *           using the second distribution.
   */
  public double[] fullClassify(Features vec, boolean keep[], boolean bi) {
    if( bi ) return fullClassify(vec, keep, probGivenY2, probClass2);
    else return fullClassify(vec, keep, probGivenY, probClass);
  }


  /**
   * Train on a dataset

   * @param data A Vector of FeatureVector objects
   */
  public void train(Vector data, boolean keep[]) {
    // RESET ARRAYS to zero
    Arrays.fill(catCounts, 0);
    for( int i = 0; i < NUM_CLASSES; i++ )
      for( int j = 0; j < NUM_FEATURES; j++ ) {
	//	System.out.println(i + " " + j);
	Arrays.fill(counts[i][j], 0);
	Arrays.fill(probGivenY[i][j], 0);
      }

    // Count feature value occurrences
    for( Iterator it = data.iterator(); it.hasNext(); ) {
      Features vec = (Features)it.next();
      //      System.out.println("Train: " + vec);

      int cat = Integer.valueOf(vec.relation())-1; // MINUS ONE for arrays
      catCounts[cat]++;
      for( int i = 0; i < order.length; i++ ) {
	int val = Integer.valueOf(vec.get(order[i]));
	counts[cat][i][val-1]++; // MINUS ONE for arrays
      }
    }

    // Class probabilities p(y)   ****double-checked, sums to 1
    probClass = new double[NUM_CLASSES];
    for( int i = 0; i < NUM_CLASSES; i++ ) {
      //      probClass[i] = (double)catCounts[i] / (double)data.size();
      probClass[i] = 1;

      /*
      double cc = 1.0;
      for( int j = 0; j < NUM_FEATURES; j++ ) {
	if( keep[j] ) cc *= limitsizes[j];
      }
      probClass[i] = (cc + catCounts[i]) / (cc*NUM_CLASSES + (double)data.size());
      */

      //      System.out.println("P(Y=" + i + ")=" + probClass[i]);
      if( probClass[i] > 0 ) probClass[i] = Math.log(probClass[i]);
      else probClass[i] = -Double.MIN_VALUE;
    }

    // Conditionals p(x|y) + laplace smoothing
    for( int i = 0; i < NUM_CLASSES; i++ ) {
      //      System.out.println("class = " + i);
      for( int j = 0; j < NUM_FEATURES; j++ ) {
	if( keep[j] ) {
	  //	  System.out.println("feature = " + j + " (" + limitsizes[j] + " values)");
	  //	  double sum = 0;

	  for( int k = 0; k < limitsizes[j]; k++ ) {
	    //probGivenY[i][j][k] = Math.log((double)counts[i][j][k] + SMOOTH) - 
	    //  Math.log((double)catCounts[i] + ((double)limitsizes[j]*SMOOTH));
            probGivenY[i][j][k] = Math.log(1 + ((ALPHA*(double)counts[i][j][k]) 
						/ (double)catCounts[i]))
	      - Math.log((double)limitsizes[j] + ALPHA);

	    //	    sum += Math.exp(probGivenY[i][j][k]);
	  }
	  /*
	  System.out.println("sum = " + sum);			       
	  if( sum < .999 || sum > 1.00001 ) { 
	    System.err.println("Conditional P(X|Y) is not a probability.");
	    System.exit(1);
	  }
	  */
	}
      }
    }  

    /*
    // Evidence p(x)  ****double-checked, this is correct, sums to 1
    for( int j = 0; j < NUM_FEATURES; j++ ) {
      System.out.println("feature " + j);
      if( keep[j] ) {
	Arrays.fill(probX[j], 0.0);
	for( int cat = 0; cat < NUM_CLASSES; cat++ ) {
	  for( int k = 0; k < limitsizes[j]; k++ ) {
	    //	    System.out.println("counts " + j + " " + k + " " + counts[cat][j][k]);
	    //            probX[j][k] += counts[cat][j][k] + SMOOTH;
            probX[j][k] += counts[cat][j][k] + SMOOTH;
	  }
	}
	double sum = 0;
	for( int k = 0; k < limitsizes[j]; k++ ) {
	  System.out.println("value " + k);
	  //	  System.out.println("normal prob " + probX[j][k]);
	  //	  System.out.println("divided by " + (data.size()*NUM_CLASSES*limitsizes[j]));
	  //	  System.out.println(" = " + probX[j][k] / (data.size()+ NUM_CLASSES*limitsizes[j]*SMOOTH));

	  // NEED TO SMOOTH THE DEMONIMATOR!
	  probX[j][k] = Math.log(probX[j][k]) - Math.log(data.size() + NUM_CLASSES*limitsizes[j]*SMOOTH);
	  System.out.println(" = " + Math.exp(probX[j][k]));
	  sum += Math.exp(probX[j][k]);
	}
	System.out.println("sum = " + sum);
	if( sum < .999 || sum > 1.00001 ) { 
	  System.err.println("Conditional P(X) is not a probability.");
	  System.exit(1);
	}
      }
    }
    */
  }


  /**
   * Train on a dataset, two distributions (same sentence, and diff sentences)
   * @param data A Vector of FeatureVector objects
   */
  public void trainBi(Vector data, int numClasses, boolean keep[], boolean keep2[]) {
    int numsames = 0;

    // RESET ARRAYS to zero
    Arrays.fill(catCounts, 0);
    Arrays.fill(catCounts2, 0);
    for( int i = 0; i < numClasses; i++ )
      for( int j = 0; j < NUM_FEATURES; j++ ) {
	Arrays.fill(counts[i][j], 0);
	Arrays.fill(probGivenY[i][j], 0);
	Arrays.fill(counts2[i][j], 0);
	Arrays.fill(probGivenY2[i][j], 0);
      }

    // Count feature value occurrences
    for( Iterator it = data.iterator(); it.hasNext(); ) {
      FeatureVector vec = (FeatureVector)it.next();
      boolean same = false;

      if( vec.get(FeatureType.SAME_SENTENCE).equals("1") ) same = true;

      int cat = Integer.valueOf(vec.relation())-1; // MINUS ONE for arrays

      if( same ) {
	catCounts[cat]++;
	numsames++;
      } else catCounts2[cat]++;

      for( int i = 0; i < NUM_FEATURES; i++ ) {
	int val = Integer.valueOf(vec.get(order[i]));
	if( same ) counts[cat][i][val-1]++; // MINUS ONE for arrays
	else counts2[cat][i][val-1]++;
      }
    }

    // Class probabilities
    probClass = new double[numClasses];
    probClass2 = new double[numClasses];
    for( int i = 0; i < numClasses; i++ ) {
      probClass[i] = (double)catCounts[i] / (double)numsames;
      if( probClass[i] > 0 ) probClass[i] = Math.log(probClass[i]);
      else probClass[i] = -Double.MIN_VALUE;
      
      probClass2[i] = (double)catCounts2[i] / (double)(data.size()-numsames);
      if( probClass2[i] > 0 ) probClass2[i] = Math.log(probClass2[i]);
      else probClass2[i] = -Double.MIN_VALUE;
    }
    
    // Conditionals p(x|y) + laplace smoothing
    for( int i = 0; i < numClasses; i++ ) {
      for( int j = 0; j < NUM_FEATURES; j++ ) {
	for( int k = 0; k < limitsizes[j]; k++ ) {
	  if( keep[j] ) 
            probGivenY[i][j][k] = Math.log(counts[i][j][k]+SMOOTH) - Math.log(catCounts[i]+limitsizes[j]);

	  if( keep2[j] )
            probGivenY2[i][j][k] = Math.log(counts2[i][j][k]+SMOOTH) - Math.log(catCounts2[i]+limitsizes[j]);
	}
      }
    }      
  }


  /**
   * Return the max of an array
   */
  private int max(int array[]) {
    int max = Integer.MIN_VALUE;
    for( int i = 0; i < array.length; i++ )
      if( array[i] > max ) max = array[i];
    return max;
  }

  /**
   * Return the max of an array
   */
  private int argmax(double array[]) {
    double max = Integer.MIN_VALUE;
    int maxi = 0;
    for( int i = 0; i < array.length; i++ ) {
      if( array[i] > max ) {
	max = array[i];
	maxi = i;
      }
    }
    return maxi;
  }

}

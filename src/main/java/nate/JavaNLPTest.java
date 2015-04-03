package nate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.classify.LogisticClassifierFactory;
import edu.stanford.nlp.classify.NBLinearClassifierFactory;
import edu.stanford.nlp.classify.SVMLightClassifier;
import edu.stanford.nlp.classify.SVMLightClassifierFactory;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

public class JavaNLPTest {

  String[] one = { "sauce", "oil", "olive", "pepperroni" };
  String[] two = { "cheese", "sauce", "sausage", "pepperroni" };
  String[] three = { "bread", "oil", "pasta", "sauce" };
  String[] four = { "bread", "oil", "pepper", "dip" };
  String[] test = { "sauce", "oil", "sausage", "onion" };
  
  public JavaNLPTest() { }

  public void test() {
    
    // Build your training set.
    Dataset<String,String> trainingset = new Dataset<String, String>();

    // Four Training items.
    Set<String> features = new HashSet<String>();
    for( String feature : one ) features.add(feature);
    BasicDatum<String,String> datum = new BasicDatum<String,String>(features);
    datum.setLabel("PIZZA");
    trainingset.add(datum);

    features = new HashSet<String>();
    for( String feature : two ) features.add(feature);
    datum = new BasicDatum<String,String>(features);
    datum.setLabel("PIZZA");
    trainingset.add(datum);

    features = new HashSet<String>();
    for( String feature : three ) features.add(feature);
    datum = new BasicDatum<String,String>(features);
    datum.setLabel("BREADSTICK");
    trainingset.add(datum);
    
    features = new HashSet<String>();
    for( String feature : four ) features.add(feature);
    datum = new BasicDatum<String,String>(features);
    datum.setLabel("BREADSTICK");
    trainingset.add(datum);

    // TRAIN the classifier.
    NBLinearClassifierFactory<String, String> NBfactory = new NBLinearClassifierFactory<String, String>();
    LinearClassifier<String,String> classy = (LinearClassifier<String, String>) NBfactory.trainClassifier(trainingset);

    SVMLightClassifierFactory<String, String> SVMfactory = new SVMLightClassifierFactory<String, String>();
    SVMLightClassifier<String,String> svmclassy = SVMfactory.trainClassifier(trainingset);

    LogisticClassifierFactory<String,String> logfactory = new LogisticClassifierFactory<String,String>();
    LogisticClassifier<String,String> logclassy = logfactory.trainClassifier(trainingset);


    // TEST the classifier.
    features = new HashSet<String>();
    for( String feature : test ) features.add(feature);
    datum = new BasicDatum<String,String>(features);

    String guess = classy.classOf(datum);
    System.out.println("NB Guessed " + guess);
    
    String svmguess = svmclassy.classOf(datum);
    System.out.println("SVM Guessed " + svmguess);    

    String logguess = logclassy.classOf(datum);
    System.out.println("Logistic Guessed " + logguess);    
}

  
  public static void main(String[] args) {
    JavaNLPTest test = new JavaNLPTest();
    test.test();
  }
}

package nate.schemas;

import java.io.Serializable;

import edu.stanford.nlp.trees.Tree;
import nate.EntityMention;

public class Relation implements Serializable {
	static final long serialVersionUID = 1;
	Tree sentenceParse;
  int sentenceID;
  String predicate;
  String particle;
  int predicateIndex;

  EntityMention leftMention;
  EntityMention rightMention;
  
  Integer leftEntityID;
  String leftDep;
  String leftArg;
  int leftTokenIndex;

  Integer rightEntityID;
  String rightDep;
  String rightArg;
  int rightTokenIndex;
  
  
  public Relation() {
  }

  public Relation(int sid, int predicateIndex, String predicate) {
  	this.sentenceID = sid;
  	this.predicate = predicate;
  	this.predicateIndex = predicateIndex;
  }
  
  public void setLeft(int index, String dep, String arg) {
  	this.leftTokenIndex = index;
  	this.leftDep = dep;
  	this.leftArg = arg;
  }

  public void setRight(int index, String dep, String arg) {
  	this.rightTokenIndex = index;
  	this.rightDep = dep;
  	this.rightArg = arg;
  }
  
  public void setLeftEntity(EntityMention mention) {
  	leftMention = mention;
  	leftEntityID = mention.entityID();
  }
  
  public void setRightEntity(EntityMention mention) {
  	rightMention = mention;
  	rightEntityID = mention.entityID();
  }
  
  public String toString() {
  	String prt = "";
  	if( particle != null ) prt = "_" + particle;

  	//  	return leftArg + "(" + leftDep + ")" + " " + predicate + " " + (rightDep != null && rightDep.startsWith("prep_") ? rightDep.substring(5)+" " : "") + rightArg + "(" + rightDep + ")";
  	return leftEntityID + "  " + predicate + prt + " " + (rightDep != null && rightDep.startsWith("prep_") ? rightDep.substring(5)+" " : "") + " " + rightEntityID;
  }

  public String toStringFriendly() {
  	String prt = "";
  	if( particle != null ) prt = "_" + particle;
  	
    //  return leftArg + "(" + leftDep + ")" + " " + predicate + " " + (rightDep != null && rightDep.startsWith("prep_") ? rightDep.substring(5)+" " : "") + rightArg + "(" + rightDep + ")";
    return leftArg + "  " + predicate + prt + " " + (rightDep != null && rightDep.startsWith("prep_") ? rightDep.substring(5)+" " : "") + " " + rightArg;
  }
  
  public String toStringDebug() {
  	String prt = "";
  	if( particle != null ) prt = "_" + particle;

  	//	return leftArg + "(" + leftDep + ")" + " " + predicate + " " + (rightDep != null && rightDep.startsWith("prep_") ? rightDep.substring(5)+" " : "") + rightArg + "(" + rightDep + ")";
  	return leftArg + " " + leftMention + "  " + predicate + prt + " " + (rightDep != null && rightDep.startsWith("prep_") ? rightDep.substring(5)+" " : "") + " " + rightArg + " " + rightMention;
}
}

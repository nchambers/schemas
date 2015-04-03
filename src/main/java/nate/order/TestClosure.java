package nate.order;

import java.util.Vector;
import nate.order.tb.TLink;
import nate.order.tb.EventEventLink;

/**
 * FOUND ERRORS (Shan also verified these 8)

e1->e2=IBEFORE  e2->e3=INCLUDES
not closed
(e1 BEFORE e3)

e1->e2=BEGINS  e2->e3=IBEFORE
e1->e3=IBEFORE
(e1 BEFORE e3)

e1->e2=ENDS  e2->e3=ENDS
e3->e1=ENDS
(e1 ENDS e3)

e1->e2=IBEFORE  e1->e3=INCLUDES
not closed
(e3 BEFORE e2)

e1->e2=INCLUDES  e1->e3=IBEFORE
not closed
(e2 BEFORE e3)

e1->e2=INCLUDES  e3->e1=IBEFORE
not closed
(e3 BEFORE e2)

e1->e2=IBEFORE  e3->e2=ENDS
not closed
(e1 BEFORE e3)

e1->e2=ENDS  e3->e2=IBEFORE
not closed
(e3 BEFORE e1)
 */

/**
 * Feeds pairs of relations into the closure algorithm,
 * outputs the closed relation (if it exists).
 * Intended for human verification of correct closure rules.
 */
public class TestClosure {
  TLink.TYPE[] types = { TLink.TYPE.BEFORE,TLink.TYPE.IBEFORE,TLink.TYPE.BEGINS,
      TLink.TYPE.ENDS,TLink.TYPE.INCLUDES,TLink.TYPE.SIMULTANEOUS };
  Closure _closure;

  TestClosure() { 
    _closure = new Closure();
  }


  public void runTests() {
    Vector relations = new Vector();

    // A->B and B->C
    for( int i = 0; i < types.length; i++ ) {
      for( int j = 0; j < types.length; j++ ) {
        relations.clear();
        relations.add(new EventEventLink("e1","e2",types[i]));
        relations.add(new EventEventLink("e2","e3",types[j]));
        _closure.computeClosure(relations);
        printClosure(relations);
      }
    }

    // A->B and A->C
    for( int i = 0; i < types.length; i++ ) {
      for( int j = 0; j < types.length; j++ ) {
        relations.clear();
        relations.add(new EventEventLink("e1","e2",types[i]));
        relations.add(new EventEventLink("e1","e3",types[j]));
        _closure.computeClosure(relations);
        printClosure(relations);
      }
    }

    // A->B and C->A
    for( int i = 0; i < types.length; i++ ) {
      for( int j = 0; j < types.length; j++ ) {
        relations.clear();
        relations.add(new EventEventLink("e1","e2",types[i]));
        relations.add(new EventEventLink("e3","e1",types[j]));
        _closure.computeClosure(relations);
        printClosure(relations);
      }
    }

    // A->B and C->B
    for( int i = 0; i < types.length; i++ ) {
      for( int j = 0; j < types.length; j++ ) {
        relations.clear();
        relations.add(new EventEventLink("e1","e2",types[i]));
        relations.add(new EventEventLink("e3","e2",types[j]));
        _closure.computeClosure(relations);
        printClosure(relations);
      }
    }

  }

  private void printClosure(Vector relations) {
    System.out.println();
    System.out.print(relations.elementAt(0) + "  ");
    System.out.println(relations.elementAt(1));
    if( relations.size() > 2 ) {
      System.out.println(relations.elementAt(2));
    } else System.out.println("not closed");
  }

  public static void main(String[] args) {
    TestClosure cp = new TestClosure();
    cp.runTests();
  }
}
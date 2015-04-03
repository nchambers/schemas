package nate;


/**
 * Creates two files as output: parses and dependencies
 *
 * -output
 * Directory to create parsed and dependency files.
 *
 */
public class NatesLongProcess {

  NatesLongProcess(String[] args) { 
    //    GigawordParser parser = new GigawordParser(args);
    //    parser.parseData();
  }


  public static void main(String[] args) {
    if( args.length > 0 ) {
      GigawordParser parser = new GigawordParser(args);
      parser.parseData();
    }
  }
}

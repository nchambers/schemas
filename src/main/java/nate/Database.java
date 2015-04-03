package nate;

/**
 * This uses MySQL Connector/J, JDBC driver for MySQL.  
 * (com.mysql.jdbc.*)
 */


// java sql
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.DriverManager;
import java.sql.Connection;


public class Database {
  private Connection myCon;
  String domain = "mainteach";
  String username = "nchambers";
  Statement myStmt;

  /**
   * @param domain The SQL database name
   */
  Database(String domain) { 
    this.domain = domain;
    try {
      Class.forName("com.mysql.jdbc.Driver").newInstance();
      // Connect to an instance of mysql with the follow details:
      // machine address: pc236nt.napier.ac.uk
      // database       : gisq
      // user name      : scott
      // password       : tiger
      myCon = DriverManager.getConnection("jdbc:mysql://localhost/" + domain + "?user=" + username);
      myStmt = myCon.createStatement();
    }
    catch (Exception sqlEx){
      sqlEx.printStackTrace();
    }
  }

  public ResultSet query(String query) {
    try {
      return myStmt.executeQuery(query);

      /*      
      // format each row of the returned rows
      while (result.next()) {
	// get the value in each column		
	//      for( int i = 0; i < columns.length; i++ ) {
	System.out.println("column = " + result.getString("keywords"));
	//      }
      }
      */
    } catch (Exception sqlEx){
      sqlEx.printStackTrace();
    }
    return null;
  }
  
  public ResultSet insert(String insert, String keys[]) {
    try {
      myStmt.executeUpdate(insert,keys);
      return myStmt.getGeneratedKeys();
    } catch (Exception sqlEx){
      sqlEx.printStackTrace();
    }
    return null;
  }

  public void insert(String insert) {
    try {
      myStmt.executeUpdate(insert);
    } catch (Exception sqlEx){
      sqlEx.printStackTrace();
    }
  }

  public void addBatch(String insert) { 
    try {
      myStmt.addBatch(insert);
    } catch( Exception ex ) { ex.printStackTrace(); }
  }

  public void executeBatch() {
    try {
      myStmt.executeBatch();
    } catch( Exception ex ) { ex.printStackTrace(); }
  }
}

package nate.util;

import java.io.File;

public class Locks {
  private final static String _lockDir = "locks";

  /**
   * Creates a physical file in the locks directory, and returns true
   * if successful.  Otherwise, false if the file already exists, or
   * creation fails.
   * @param name The name of your lock.
   * @param myLockDir The directory to store the files representing locks.
   */
  public static boolean getLock(String name, String myLockDir) {
    if( !Directory.fileExists(myLockDir) ) {
	System.out.println("Locks.java creating directory " + myLockDir);
      new File(_lockDir).mkdir();
      }

    if( !Directory.fileExists(myLockDir) ) {
      System.out.println("Locks.java error: lock directory does not exist (" + myLockDir + ")");
      return false;
    }

    // Replace both types of slashes. Running on Windows we sometimes have Unix separators too.
    name = name.replace(File.separator, "--").replace("\\", "--").replace("/", "--");
    String path = myLockDir + File.separator + name + ".lock";
    
    File fileLock = new File(path);
    if( !fileLock.exists() ) {
      try {
        // It wasn't locked, so try creating the lock
        boolean created = fileLock.createNewFile();
        if( created )
          return true;
	else
	  System.out.println("ERROR: Locks.java couldn't create the new file " + path);
      } catch( Exception ex ) {
      	System.out.println("ERROR: couldn't obtain the lock with file " + path);
      	ex.printStackTrace(); 
      }
    }
    return false;
  }
  
  
  public static boolean getLock(String name) {
    return getLock(name, _lockDir);
  }
}

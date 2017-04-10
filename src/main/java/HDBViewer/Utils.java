package HDBViewer;

import java.util.Date;
import javax.swing.JOptionPane;
import javax.swing.JFrame;
import org.tango.jhdb.Hdb;

/**
 * Utility class
 *
 * @author pons
 */
public class Utils {
  
  final static int FORMAT_SEC=0;
  final static int FORMAT_US=1;
  final static int FORMAT_MS=2;

  private static Date tmpDate = new Date();

  public static void showError(JFrame parent, String errorMsg) {
    JOptionPane.showMessageDialog(parent, errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
  }

  public static void showError(String errorMsg) {
    showError(null, errorMsg);
  }

  public static boolean contains(int[] arr, int item) {

    boolean found = false;
    int i = 0;

    while (i < arr.length && !found) {
      found = (arr[i] == item);
      if (!found) {
        i++;
      }
    }

    return found;

  }
  
  public static String[] makeStringArray(String value) {
    
    // Remove extra \n at the end of the string (not handled by split)
    while (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
    return value.split("\n");
    
  }

  public static String[] formatDateAndTime(long time) {

    tmpDate.setTime(time/1000);
    String dStr =  Hdb.hdbDateFormat.format(tmpDate);
    return dStr.split(" ");

  }
  
  public static String formatTime(long time,int format) {
    
    // Timestamp
    tmpDate.setTime(time/1000);
    int microSec = (int)(time%1000000);
    
    if(format==FORMAT_US) {
      
      String microSecStr = String.format("%06d",microSec);
      return Hdb.hdbDateFormat.format(tmpDate) + "." + microSecStr;
      
    } else if(format==FORMAT_MS) {

      String milliSecStr = String.format("%03d",microSec/1000);
      return Hdb.hdbDateFormat.format(tmpDate) + "." + milliSecStr;
      
    } else {
      
      return Hdb.hdbDateFormat.format(tmpDate);
      
    }
    
  }

}

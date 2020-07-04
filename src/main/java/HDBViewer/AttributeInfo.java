package HDBViewer;

import fr.esrf.tangoatk.widget.util.chart.JLDataView;
import java.util.ArrayList;
import org.tango.jhdb.HdbSigParam;
import org.tango.jhdb.SignalInfo;
import org.tango.jhdb.data.HdbData;
import org.tango.jhdb.data.HdbDataSet;

/**
 *
 * @author pons
 */
public class AttributeInfo {

  public static final int SEL_NONE = 0;
  public static final int SEL_Y1 = 1;
  public static final int SEL_Y2 = 2;
  public static final int SEL_IMAGE = 3;

  public String  host;          // Tango HOST
  public String  name;          // 4 fields attribute name
  private String  type = "";          // HdbType of the signal
  public String  unit;          // Unit
  public double  A1;            // Conversion factor
  public SignalInfo sigInfo;    // Signal info struct
  public boolean step;          // Step mode
  public boolean table;         // Display in HDB table
  public int     selection;     // Selection mode
  public int     wselection;    // Selection mode (write value)
  public int     dataSize;      // Data number
  public int     errorSize;     // Error number
  public JLDataView chartData;  // Dataview
  public JLDataView wchartData; // Dataview (Write value)
  public JLDataView errorData;  // Dataview (Errors)
  public ArrayList<HdbData> errors; // Errors
  public HdbDataSet arrayData;  // Data for array attribute
  public int maxArraySize;      // maximum size of arrayData
  public int queryMode;         // Query mode (0->DATA 1..10->Config)
  public String dvSettings;     // String containing dataview settings
  public String wdvSettings;    // String containing dataview (write) settings

  // List of expanded array item (Array item show as scalar)
  public ArrayList<ArrayAttributeInfo> arrAttInfos;


  public AttributeInfo() {
    step = false;
    table = false;
    selection = SEL_NONE;
    wselection = SEL_NONE;
    chartData = null;
    dataSize = 0;
    maxArraySize = -1;
    wchartData = null;
    arrAttInfos = null;
    unit = "";
    queryMode = HdbSigParam.QUERY_DATA;
    A1 = 1.0;
    dvSettings = null;
    wdvSettings = null;
  }

  public String getName() {
    if( sigInfo!=null ) {
      return name + HdbSigParam.FIELDS[sigInfo.queryConfig];
    } else
      return name;
  }

  public String getFullName() {
    return "tango://" + host + "/" + name;
  }

  public boolean isRW() {
    return sigInfo.isRW();
  }

  public boolean isArray() {
    return sigInfo.isArray();
  }

  public boolean isNumeric() {
    return sigInfo.isNumeric();
  }

  public boolean isString() {
    return sigInfo.isString();
  }

  public boolean isState() {
    return sigInfo.isState();
  }

  public boolean isExpanded() {
    if( !isArray() || arrAttInfos==null )
      return false;
    return this.arrAttInfos.size()>0;
  }

  public String getType()
  {
      if(type.isEmpty())
      {
          type = (sigInfo.isArray()? "array_" : "scalar_") + sigInfo.dataType.toString().toLowerCase() + "_" + sigInfo.access.toString().toLowerCase();
      }
      return type;
  }

  /**
   * Expand array attribute items
   * @param ids List of index to be expanded
   */
  public void expand(ArrayList<Integer> ids) {

    arrAttInfos = new ArrayList<ArrayAttributeInfo>();
    for (int i = 0; i < ids.size(); i++) {
      ArrayAttributeInfo aai = new ArrayAttributeInfo(ids.get(i));
      arrAttInfos.add(aai);
    }

  }

  static boolean isInList(AttributeInfo item,ArrayList<AttributeInfo> list) {

    boolean found = false;
    int i = 0;
    while(!found && i<list.size()) {
      found = item.sigInfo.sigId.equals(list.get(i).sigInfo.sigId) &&
              (item.sigInfo.queryConfig == list.get(i).sigInfo.queryConfig);
      if(!found) i++;
    }

    return found;

  }

  static AttributeInfo getFromList(AttributeInfo item,ArrayList<AttributeInfo> list) {

    boolean found = false;
    int i = 0;
    while(!found && i<list.size()) {
      found = item.sigInfo.sigId.equals(list.get(i).sigInfo.sigId);
      if(!found) i++;
    }

    if(!found)
      return null;
    else
      return list.get(i);

  }

}

package HDBViewer;

import fr.esrf.tangoatk.widget.util.chart.CfFileReader;
import fr.esrf.tangoatk.widget.util.chart.JLDataView;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
  
  private static final int DATA_IDX=0;
  private static final int WRITE_DATA_IDX=1;
  private static final int ERROR_DATA_IDX=2;

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
  private Map<SignalInfo.Interval, Integer> dataSize;      // Data number
  private Map<SignalInfo.Interval, Integer> errorSize;     // Error number
  private JLDataView[] chartData = new JLDataView[3];
//  public JLDataView chartData;  // Dataview
//  public JLDataView wchartData; // Dataview (Write value)
//  public JLDataView errorData;  // Dataview (Errors)
  private Map<SignalInfo.Interval, Map<HdbData.Aggregate, JLDataView>> aggData;  // Dataviews for aggregate data
  public ArrayList<HdbData> errors; // Errors
  public HdbDataSet arrayData;  // Data for array attribute
  public int maxArraySize;      // maximum size of arrayData
  public int queryMode;         // Query mode (0->DATA 1..10->Config)
  private String[] dataViewSettings = new String[2]; // String containing dataview settings
//  public String dvSettings;     // String containing dataview settings
//  public String wdvSettings;    // String containing dataview (write) settings


  private Map<SignalInfo.Interval, Set<HdbData.Aggregate>> extractDataInfo = new EnumMap<>(SignalInfo.Interval.class);

  // List of expanded array item (Array item show as scalar)
  public ArrayList<ArrayAttributeInfo> arrAttInfos;


  public AttributeInfo() {
    step = false;
    table = false;
    selection = SEL_NONE;
    wselection = SEL_NONE;
    dataSize = new EnumMap<>(SignalInfo.Interval.class);
    errorSize = new EnumMap<>(SignalInfo.Interval.class);
    aggData = new EnumMap<>(SignalInfo.Interval.class);
    maxArraySize = -1;
    arrAttInfos = null;
    unit = "";
    queryMode = HdbSigParam.QUERY_DATA;
    A1 = 1.0;
    extractDataInfo.put(SignalInfo.Interval.NONE, new HashSet<HdbData.Aggregate>());
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

  void toggleAggregate(SignalInfo.Interval interval, HdbData.Aggregate agg) {
    if(extractDataInfo.containsKey(interval))
    {
      //For none (raw data) the set of aggregate is empty so we don't
      //even check, but one other extract is needed.
      if(interval == SignalInfo.Interval.NONE)
      {
        if(extractDataInfo.size()>1)
        {
          extractDataInfo.remove(interval);
        }
      }
      else
      {
        Set<HdbData.Aggregate> aggs = extractDataInfo.get(interval);
        if(aggs.contains(agg))
        {
          //last aggregate, remove the interval alltogether, unless it is the last one.
          if(aggs.size() == 1)
          {
            if(extractDataInfo.size()>1)
            {
              extractDataInfo.remove(interval);
            }
          }
          else
          {
            aggs.remove(agg);
          }
        }
        else
        {
          aggs.add(agg);
        }
      }
    }
    else
    {
      HashSet<HdbData.Aggregate> aggs = new HashSet<HdbData.Aggregate>();
      if(interval != SignalInfo.Interval.NONE)
      {
        aggs.add(agg);
      }
      extractDataInfo.put(interval, aggs);
    }
  }

  Set<HdbData.Aggregate> getAggregates(SignalInfo.Interval interval) {
    return extractDataInfo.getOrDefault(interval, new HashSet<HdbData.Aggregate>());
  }

  Set<SignalInfo.Interval> getIntervals() {
    return extractDataInfo.keySet();
  }
  
  public JLDataView getDataView()
  {
    return chartData[DATA_IDX];
  }
  
  public JLDataView getWriteDataView()
  {
    return chartData[WRITE_DATA_IDX];
  }
  
  public JLDataView getErrorDataView()
  {
    return chartData[ERROR_DATA_IDX];
  }
  
  // to avoid creating a new map each time aggData is empty.
  private final static Map<HdbData.Aggregate, JLDataView> EMPTY_DATA_VIEWS = new EnumMap<HdbData.Aggregate, JLDataView>(HdbData.Aggregate.class);
  public JLDataView getAggregateDataView(SignalInfo.Interval interval, HdbData.Aggregate agg)
  {
    return aggData.getOrDefault(interval, EMPTY_DATA_VIEWS).get(agg);
  }
  
  public void setDataViewSettings(String settings)
  {
    dataViewSettings[DATA_IDX] = settings;
  }
  
  public void setWriteDataViewSettings(String settings)
  {
    dataViewSettings[WRITE_DATA_IDX] = settings;
  }
  
  public void setDataSize(SignalInfo.Interval interval, int val)
  {
    dataSize.put(interval, val);
  }  
  
  public void incrementDataSize(SignalInfo.Interval interval)
  {
    int newVal = dataSize.getOrDefault(interval, 0);
    dataSize.put(interval, ++newVal);
  }
  
  public int getDataSize(SignalInfo.Interval interval)
  {
    return dataSize.getOrDefault(interval, 0);
  }
  
  public void setErrorSize(SignalInfo.Interval interval, int val)
  {
    errorSize.put(interval, val);
  }
 
  public void incrementErrorSize(SignalInfo.Interval interval)
  {
    int newVal = errorSize.getOrDefault(interval, 0);
    errorSize.put(interval, ++newVal);
  }
  
  public int getErrorSize(SignalInfo.Interval interval)
  {
    return errorSize.getOrDefault(interval, 0);
  }

  /***
   * Reset all the dataviews stored in this attribute and its arrayattributes.
   * Clear the arrayData as well.
   */
  public void resetData()
  {
    for(JLDataView data : chartData)
    {
      if(data != null)
        data.reset();
    }
    for(Map<HdbData.Aggregate, JLDataView> aggdata : aggData.values())
    {
      for(JLDataView data : aggdata.values())
        data.reset();
    }
    if(isExpanded()) {
      for(ArrayAttributeInfo ai : arrAttInfos) {
        if(ai.chartData!=null)
          ai.chartData.reset();
        if(ai.wchartData!=null)
          ai.wchartData.reset();
      }
    }
    arrayData = null;
  }
  
  /***
   * Initialize the JLDataview to store the data.
   * @param colorIdx 
   * @param ColorBase
   * @param interval
   * @return 
   */
  public int initializeDataViews(int colorIdx, Color[] ColorBase, SignalInfo.Interval interval)
  {
    int addedData = 0;
    Color c = ColorBase[colorIdx%ColorBase.length];
    if(interval != SignalInfo.Interval.NONE)
    {
      for(HdbData.Aggregate agg : getAggregates(interval))
      {
        initializeData(interval, agg, ColorBase[colorIdx++%ColorBase.length]);
        addedData++;
      }
    }
    else
    {
      initializeData(DATA_IDX, ColorBase[colorIdx++%ColorBase.length], "");
      if(isRW())
      {
        initializeData(WRITE_DATA_IDX, ColorBase[colorIdx++%ColorBase.length], "_w");
      }
    }
    if(chartData[ERROR_DATA_IDX] == null)
    {
      chartData[ERROR_DATA_IDX] = new JLDataView();
      chartData[ERROR_DATA_IDX].setLineWidth(0);
      chartData[ERROR_DATA_IDX].setMarker(JLDataView.MARKER_DOT);
      chartData[ERROR_DATA_IDX].setLabelVisible(false);
      chartData[ERROR_DATA_IDX].setMarkerColor(c.darker());
    }
    chartData[ERROR_DATA_IDX].setName(getName()+"_error");
    errors = new ArrayList<>();
    return addedData;
  }
  
  private void initializeData(int idx, Color c, String suffix)
  {
    if(chartData[idx] == null)
    {
      chartData[idx] = new JLDataView();
      chartData[idx].setColor(c);
      chartData[idx].setUnit(unit);
    }
    if(dataViewSettings[idx] != null)
    {
      CfFileReader cfr = new CfFileReader();
      cfr.parseText(dataViewSettings[idx]);
      chartData[idx].applyConfiguration("dv", cfr);
    }
    chartData[idx].setName(getName() + suffix);
  }

  private void initializeData(SignalInfo.Interval interval, HdbData.Aggregate agg, Color c)
  {
    JLDataView data = new JLDataView();
    data.setColor(c);
    data.setUnit(unit);
    data.setName(getName() + "_" + agg.toString());

    if(!aggData.containsKey(interval))
      aggData.put(interval, new EnumMap<HdbData.Aggregate, JLDataView>(HdbData.Aggregate.class));

    aggData.get(interval).put(agg, data);
  }

}

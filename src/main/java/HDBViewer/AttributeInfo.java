package HDBViewer;

import fr.esrf.tangoatk.widget.util.chart.CfFileReader;
import fr.esrf.tangoatk.widget.util.chart.JLDataView;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.tango.jhdb.HdbSigInfo;
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
  private String  type = "";    // HdbType of the signal
  public String  unit;          // Unit
  public double  A1;            // Conversion factor
  public SignalInfo sigInfo;    // Signal info struct
  public boolean step;          // Step mode
  public boolean table;         // Display in HDB table
  public int     selection;     // Selection mode
  public int     wselection;    // Selection mode (write value)
  public int     dataSize;      // Data number
  public int     errorSize;     // Error number
  public SignalInfo.Interval interval; // Aggreate interval
  public ArrayList<HdbData> errors; // Errors (for click on chart)
  public HdbDataSet arrayData;  // Data for array attribute
  public int maxArraySize;      // maximum size of arrayData
  public int queryMode;         // Query mode (0->DATA 1..10->Config)
  public int tableIdx;          // Index for table    
  public int wtableIdx;         // Index for table (write value)


  private JLDataView[] chartData;                       // Dataviews for raw data (value,setpoint,error)
  private String dataViewSettings[];                    // Dataview settings
  private Map<HdbData.Aggregate, AggregateAttributeInfo> aggInfos;     // Aggregate infos

  // List of expanded array item (Array item show as scalar)
  public ArrayList<ArrayAttributeInfo> arrAttInfos;


  public AttributeInfo() {
    step = false;
    table = false;
    interval = HdbSigInfo.Interval.NONE;
    selection = SEL_NONE;
    wselection = SEL_NONE;
    dataSize = 0;
    errorSize = 0;
    chartData = new JLDataView[3];
    dataViewSettings = new String[3];
    aggInfos = new HashMap<HdbData.Aggregate,AggregateAttributeInfo>();
    maxArraySize = -1;
    arrAttInfos = null;
    unit = "";
    queryMode = HdbSigParam.QUERY_DATA;
    A1 = 1.0;
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
  
  public void unexpand(int id) {
    if(id>=0 && id<arrAttInfos.size())
      arrAttInfos.remove(id);
  }

  static boolean isInList(AttributeInfo item,ArrayList<AttributeInfo> list) {

    boolean found = false;
    int i = 0;
    while(!found && i<list.size()) {
      found = item.sigInfo.sigId.equals(list.get(i).sigInfo.sigId) &&
              (item.sigInfo.queryConfig == list.get(i).sigInfo.queryConfig) &&
              (item.interval == list.get(i).interval);
      if(!found) i++;
    }

    return found;

  }

  static AttributeInfo getFromList(AttributeInfo item,ArrayList<AttributeInfo> list) {

    boolean found = false;
    int i = 0;
    while(!found && i<list.size()) {
      found = item.sigInfo.sigId.equals(list.get(i).sigInfo.sigId) &&
              (item.sigInfo.queryConfig == list.get(i).sigInfo.queryConfig) &&
              item.interval == list.get(i).interval;
      if(!found) i++;
    }

    if(!found)
      return null;
    else
      return list.get(i);

  }
  
  public boolean isRAW() {
    return interval == SignalInfo.Interval.NONE;
  }

  public boolean isAggregate() {
    return interval != SignalInfo.Interval.NONE;
  }

  public HdbData.Aggregate getFirstAggregate() {
    return aggInfos.keySet().iterator().next();
  }
  
  public Set<HdbData.Aggregate> getAggregates() {
    return aggInfos.keySet();
  }
  
  public int getNbAggregate() {
    return aggInfos.keySet().size();
  }
  
  public AggregateAttributeInfo getAggregate(HdbData.Aggregate agg) {

    AggregateAttributeInfo a = aggInfos.get(agg);
    if(a==null) {
      System.out.print("AttributeInfo::getAggregate() unexpected aggregate: "+agg.toString());
    }
    return a;
    
  }
    
  public void addAggregate(HdbData.Aggregate agg) {
    AggregateAttributeInfo a = new AggregateAttributeInfo();
    a.selection = SEL_Y1;
    aggInfos.put(agg,a);
  }

  public void addAggregate(HdbData.Aggregate agg,AggregateAttributeInfo a) {
    aggInfos.put(agg,a);
  }
  
  public void removeAggregate(HdbData.Aggregate agg) {
    aggInfos.remove(agg);
  }
  
  public SortedSet<Integer> getArrayIndexes()
  {
      TreeSet<Integer> ret = new TreeSet<>();
    if( !isArray() || arrAttInfos==null )
      return ret;
      for(ArrayAttributeInfo ai : arrAttInfos)
      {
          ret.add(ai.idx);
      }
    return ret;
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
  
  public JLDataView getAggregateDataView(HdbData.Aggregate agg)
  {
    return aggInfos.get(agg).dv;
  }
  
  public void setDataViewSettings(String settings)
  {
    dataViewSettings[DATA_IDX] = settings;
  }
  
  public void setWriteDataViewSettings(String settings)
  {
    dataViewSettings[WRITE_DATA_IDX] = settings;
  }
  
  public boolean hasChartSelection() {
    
    if(selection==SEL_Y1 || selection==SEL_Y2)
      return true;
    boolean sel = false;
    for(HdbData.Aggregate agg:aggInfos.keySet())
      sel |= (getAggregate(agg).selection==SEL_Y1 || 
              getAggregate(agg).selection==SEL_Y2);
    return sel;
    
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
    
    for(AggregateAttributeInfo a : aggInfos.values())
      if(a.dv!=null)
        a.dv.reset();
    
    if(isExpanded()) {
      for(ArrayAttributeInfo ai : arrAttInfos) {
        if(ai.chartData!=null)
          ai.chartData.reset();
        if(ai.wchartData!=null)
          ai.wchartData.reset();
      }
    }
    
    arrayData = null;
    dataSize = 0;
    errorSize = 0;
    
  }
  
  /***
   * Initialize the JLDataview to store the data.
   * @param colorIdx 
   * @param ColorBase
   * @param interval
   * @return 
   */
  public int initializeDataViews(int colorIdx, Color[] ColorBase)
  {
    int addedData = 0;
    Color c = ColorBase[colorIdx % ColorBase.length];
    if (interval != SignalInfo.Interval.NONE) {
      for (HdbData.Aggregate agg : aggInfos.keySet()) {
        initializeAggreateDataViews(agg, ColorBase[colorIdx++ % ColorBase.length]);
        addedData++;
      }
    } else {
      initializeDataViews(DATA_IDX, ColorBase[colorIdx++ % ColorBase.length], "");
      addedData++;
      if (isRW()) {
        initializeDataViews(WRITE_DATA_IDX, ColorBase[colorIdx++ % ColorBase.length], "_w");
        addedData++;
      }
    }
    if (chartData[ERROR_DATA_IDX] == null) {
      chartData[ERROR_DATA_IDX] = new JLDataView();
      chartData[ERROR_DATA_IDX].setLineWidth(0);
      chartData[ERROR_DATA_IDX].setMarker(JLDataView.MARKER_DOT);
      chartData[ERROR_DATA_IDX].setLabelVisible(false);
      chartData[ERROR_DATA_IDX].setMarkerColor(c.darker());
    }
    chartData[ERROR_DATA_IDX].setName(getName() + "_error");
    errors = new ArrayList<>();
    return addedData;
  }
  
  private void initializeDataViews(int idx, Color c, String suffix)
  {
    if(chartData[idx] == null)
    {
      chartData[idx] = new JLDataView();
      chartData[idx].setColor(c);
      chartData[idx].setMarkerColor(c);
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
  
  public String getUnit(HdbData.Aggregate agg) {

    String _unit = unit;
    switch (agg) {
      case ROWS_COUNT:
        _unit = "rows";
        break;
      case ERRORS_COUNT:
        _unit = "errors";
        break;
      case COUNT_R:
      case COUNT_W:
        _unit = "values";
        break;
      case NAN_COUNT_R:
      case NAN_COUNT_W:
        _unit = "nans";
        break;
    }
    return _unit;

  }

  private void initializeAggreateDataViews(HdbData.Aggregate agg, Color c)
  {
    AggregateAttributeInfo a = getAggregate(agg);
    if( a!=null ) {
      if (a.dv == null) {
        a.dv = new JLDataView();
        a.dv.setColor(c);
        a.dv.setMarkerColor(c);
        a.dv.setUnit(getUnit(agg));
        if(a.dvSettings != null) {
          CfFileReader cfr = new CfFileReader();
          cfr.parseText(a.dvSettings);
          a.dv.applyConfiguration("dv", cfr);
        }
        a.dv.setName(getName() + "_" + interval.toString() + "_" + agg.toString());
      }
    }
  }

}

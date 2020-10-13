package HDBViewer;

import fr.esrf.tangoatk.widget.util.chart.JLDataView;

public class ArrayAttributeInfo {
  
  public int        idx;        // Position index
  public boolean    step;       // Step mode
  public boolean    table;      // Display in HDB table
  public int        selection;  // Selection mode
  public int        wselection; // Selection mode (write value)
  public JLDataView chartData;  // Dataview
  public JLDataView wchartData; // Dataview (Write value)
  public String     dvSetting;  // Dataview setting
  public String     wdvSetting; // Dataview setting
  public int tableIdx;          // Index for table    
  public int wtableIdx;         // Index for table (write value)
  
  public ArrayAttributeInfo(int idx) {
    this.idx = idx;
    step = false;
    table = false;
    selection = AttributeInfo.SEL_NONE;
    wselection = AttributeInfo.SEL_NONE;
    chartData = null;
    wchartData = null;
    dvSetting = null;
    wdvSetting = null;
  }

}

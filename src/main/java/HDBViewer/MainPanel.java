package HDBViewer;

import fr.esrf.tangoatk.widget.util.ATKGraphicsUtils;
import fr.esrf.tangoatk.widget.util.Splash;
import fr.esrf.tangoatk.widget.util.chart.CfFileReader;
import fr.esrf.tangoatk.widget.util.chart.IJLChartListener;
import fr.esrf.tangoatk.widget.util.chart.JLAxis;
import fr.esrf.tangoatk.widget.util.chart.JLChartEvent;
import fr.esrf.tangoatk.widget.util.chart.JLDataView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.tango.jhdb.Hdb;
import org.tango.jhdb.HdbFailed;
import org.tango.jhdb.HdbProgressListener;
import org.tango.jhdb.HdbReader;
import org.tango.jhdb.HdbSigInfo;
import org.tango.jhdb.HdbSigParam;
import org.tango.jhdb.SignalInfo;
import org.tango.jhdb.data.HdbData;
import org.tango.jhdb.data.HdbDataSet;
import org.tango.jhdb.data.HdbFloatArray;
import org.tango.jhdb.data.HdbLong64Array;
import org.tango.jhdb.data.HdbLongArray;
import org.tango.jhdb.data.HdbShortArray;
import org.tango.jhdb.data.HdbState;
import org.tango.jhdb.data.HdbStateArray;
import org.tango.jhdb.data.HdbStringArray;

/**
 *
 * @author pons
 */

public class MainPanel extends javax.swing.JFrame implements IJLChartListener,HdbProgressListener {

  // Panels
  DockedPanel viewDockedPanel;
  ChartPanel chartPanel;
  TablePanel tablePanel;
  ImagePanel imagePanel;

  DockedPanel selDockedPanel;
  SelectionPanel selPanel;

  DockedPanel hdbTreeDockedPanel;
  HDBTreePanel hdbTreePanel;

  // HDB Query
  Hdb hdb;
  ArrayList<AttributeInfo> selection;
  ErrorDialog errorDialog = null;
  HdbDataSet[] results;
  ThreadDlg searchDlg;

  // Selected image item
  AttributeInfo selectedImage;
  boolean       selectedImageRW;

  // Application variables
  boolean runningFromShell;
  Splash splash;
  InfoDialog infoDialog;
  int dvIdx=0;

  long startR;
  long stopR;
  
  protected static Color[] defaultColor = {
    Color.red,
    Color.blue,
    Color.cyan,
    Color.green,
    Color.magenta,
    Color.orange,
    Color.pink,
    Color.yellow,
    Color.black
  };

  public static String getVersion(){
    Package p = MainPanel.class.getPackage();

    //if version is set in MANIFEST.mf
    if(p.getImplementationVersion() != null) return p.getImplementationVersion();

    return "*.*";
  }


  public MainPanel() {
    this(true,true);
  }

  /**
   * Creates new form MainPanel
   */
  public MainPanel(boolean runningFromShell,boolean showSplash) {

    this.runningFromShell = runningFromShell;
    initComponents();

    if(showSplash) {
      splash = new Splash();
      splash.setMaxProgress(100);
      splash.setMessage("Connection to HDB");
      splash.setTitle("HDB Viewer (" + getVersion() + ")");
      splash.setVisible(true);
    }

    // Application dialogs
    errorDialog = new ErrorDialog();
    infoDialog = new InfoDialog();

    // Connect to HDB
    hdb = new Hdb();
    try {

      long t0 = System.currentTimeMillis();

      String hdbType = System.getProperty("HDB_TYPE");
      if(hdbType==null || hdbType.isEmpty()) {
        hdb.connect();
      } else if (hdbType.equalsIgnoreCase("cassandra")) {
        hdb.connectCassandra();
      } else if (hdbType.equalsIgnoreCase("mysql")) {
        hdb.connectMySQL();
      } else if (hdbType.equalsIgnoreCase("oracle")) {
        hdb.connectOracle();
      } else {
        hdb.connect();
      }

      long t1 = System.currentTimeMillis();
      infoDialog.addText(hdb.getReader().getInfo());
      infoDialog.addText("Connection time: "+(t1-t0)+"ms");

    } catch (HdbFailed e) {
      if(showSplash) splash.setVisible(false);
      Utils.showError("Cannot connect to HDB\n" + e.getMessage());
      if(runningFromShell) System.exit(0);
      else return;
    }

    hdb.getReader().addProgressListener(this);

    if(showSplash) splash.progress(50);

    String hdbV;
    if(hdb.getDBType()==Hdb.HDB_ORACLE) {
      hdbV = "HDB";
    } else {
      hdbV = "HDB++";
    }

    setTitle(hdb.getDBTypeName() + " " + hdbV + " Viewer [" + getVersion() + "]");

    selection = new ArrayList<>();

    // Docked panels

    // Create chart ToolWindow
    chartPanel = new ChartPanel(this);
    tablePanel = new TablePanel(this);
    imagePanel = new ImagePanel(this);

    viewDockedPanel = new DockedPanel(this);
    viewDockedPanel.addPanel("Chart",chartPanel);
    viewDockedPanel.addPanel("Table",tablePanel);
    viewDockedPanel.addPanel("Image",imagePanel);
    vSplitPane.setRightComponent(viewDockedPanel);

    hdbTreeDockedPanel = new DockedPanel(this);
    hdbTreePanel = new HDBTreePanel(this);
    hdbTreeDockedPanel.addPanel("HDB Tree",hdbTreePanel);
    vSplitPane.setLeftComponent(hdbTreeDockedPanel);

    selDockedPanel = new DockedPanel(this);
    selPanel = new SelectionPanel(this);
    selDockedPanel.addPanel("Selection",selPanel);
    hSplitPane.setBottomComponent(selDockedPanel);

    if(runningFromShell)
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    else
      setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

    ATKGraphicsUtils.centerFrameOnScreen(this);
    if(showSplash) splash.progress(100);
    try{Thread.sleep(50);}catch(Exception e){};
    if(showSplash) splash.setVisible(false);

    ImageIcon icon = new ImageIcon(getClass().getResource("/HDBViewer/hdbpp.gif"));
    setIconImage(icon.getImage());

  }

  /**
   * Unselect given attribute data views from chart
   * @param ai Attribute to unselect
   * @param item Index of expanded attribute or -1 for normal attribute
   * @param agg Aggregate to unselect
   */
  public void unselectAttribute(AttributeInfo ai,int item,HdbData.Aggregate agg) {

    JLAxis axis;
    
    if (ai.isAggregate()) {
      
      // Aggregate
      if(ai.getAggregate(agg).dv!=null)
        ai.getAggregate(agg).dv.removeFromAxis();
      ai.getAggregate(agg).selection = AttributeInfo.SEL_NONE;
      
    } else {
      
      // Raw attribute

      if (item < 0) {

        if (ai.getDataView() != null) {
          ai.getDataView().removeFromAxis();
        }

        if (ai.getWriteDataView() != null) {
          ai.getWriteDataView().removeFromAxis();
        }

        if (ai.getErrorDataView() != null) {
          ai.getErrorDataView().removeFromAxis();
        }

        ai.selection = AttributeInfo.SEL_NONE;
        ai.wselection = AttributeInfo.SEL_NONE;

      } else {

        ArrayAttributeInfo aai = ai.arrAttInfos.get(item);

        if (aai.chartData != null) {
          aai.chartData.removeFromAxis();
        }

        if (aai.wchartData != null) {
          aai.wchartData.removeFromAxis();
        }

        //if(aai.errorData!=null)
        //  aai.errorData.removeFromAxis();
        aai.selection = AttributeInfo.SEL_NONE;
        aai.wselection = AttributeInfo.SEL_NONE;

      }

    }

    chartPanel.chart.repaint();

  }

  /**
   * Add a attribute to the selection list
   * @param host Host name (hostname:port)
   * @param name Attribute name (domain/family/member/attname)
   * @return the created AttributeInfo object
   */
  public AttributeInfo addAttribute(String host,String name) throws HdbFailed {

    AttributeInfo ai = new AttributeInfo();
    ai.host = host;
    ai.name = name;
    HdbSigInfo si = hdb.getReader().getSigInfo(ai.getFullName());
    ai.sigInfo = si;
    ai.sigInfo.queryConfig = HdbSigParam.QUERY_DATA;
    ai.interval = SignalInfo.Interval.NONE;

    AttributeInfo eai = AttributeInfo.getFromList(ai, selection);

    if (eai != null) {

      // Attribute already added
      // Return existing object
      return eai;

    } else {

      // Add new attribute to the list
      if (ai.isString()) {
        ai.table = true;
      }

      if (ai.isNumeric() && !ai.isArray()) {
        ai.selection = AttributeInfo.SEL_Y1;
      }

      if (ai.isNumeric() && ai.isArray()) {
        ai.selection = AttributeInfo.SEL_IMAGE;
      }

      if (ai.isState() && !ai.isArray()) {
        ai.step = true;
      }

      selection.add(ai);

      updateSelectionList();

      return ai;

    }

  }

  /**
   * Sets the starting time and end time of the request
   * @param start start date (number of milliseconds since epoch)
   * @param stop stop date (number of milliseconds since epoch)
   */
  public void setDate(long start,long stop) {
    hdbTreePanel.setDate(start,stop);
  }

  /**
   * Sets the time interval of the request.
   * 0=Last 1 hour
   * 1=Last 4 hour
   * 2=Last 8 hour
   * 3=Last day
   * 4=Last week
   * 5=Last month
   * @param start start date (number of milliseconds since epoch)
   * @param stop stop date (number of milliseconds since epoch)
   */
  public void setTimeInterval(int it) {
    hdbTreePanel.setTimeInterval(it);
  }

  /**
   * Sets the HDB extraction mode.
   * 0=Normal
   * 1=Ignore errors
   * 2=Filled
   * 3=Correlated
   * @param mode HDB mode
   */
  public void setHdbMode(int mode) {
    hdbTreePanel.setHdbMode(mode);
  }


  /**
   * Sets the python script
   * @param name
   */
  public void setPyScript(String name) {
    selPanel.setPyScript(name);
  }

  /**
   * Return the AttributeInfo structure for the given attribute
   * @param host Tango host (hostname:port)
   * @param name Attribute name (domain/family/member/attname)
   * @return AttributeInfo object or null if attribute not found.
   */
  public AttributeInfo getAttributeInfo(String host,String name) {

    boolean found = false;
    int i=0;
    while(!found && i<selection.size()) {
      AttributeInfo ai = selection.get(i);
      found = ai.name.equalsIgnoreCase(name) &&
              ai.host.equalsIgnoreCase(host);
      if(!found) i++;
    }

    if(!found) {
      return null;
    } else {
      return selection.get(i);
    }

  }

  /**
   * Select all scalar attribute on the Y1 axis
   */
  public void selectAllY1() {

    for(AttributeInfo ai:selection) {
      if(ai.isNumeric() && !ai.isArray())
        selectAttribute(ai,-1,AttributeInfo.SEL_Y1,false);
      if(ai.isExpanded())
        for(int j=0;j<ai.arrAttInfos.size();j++) {
          ArrayAttributeInfo aai = ai.arrAttInfos.get(j);
          selectAttribute(ai,j,AttributeInfo.SEL_Y1,false);
        }
    }
    updateSelectionList();

  }

  /**
   * Select all scalar attribute on the Y1 axis
   */
  public void selectAllY2() {

    for(AttributeInfo ai:selection) {
      if(ai.isNumeric() && !ai.isArray())
        selectAttribute(ai,-1,AttributeInfo.SEL_Y2,false);
      if(ai.isExpanded())
        for(int j=0;j<ai.arrAttInfos.size();j++) {
          ArrayAttributeInfo aai = ai.arrAttInfos.get(j);
          selectAttribute(ai,j,AttributeInfo.SEL_Y2,false);
        }
    }
    updateSelectionList();

  }

  public boolean selectAttribute(AttributeInfo ai,int item,int selMode,boolean selectWrite) {
    return selectAttribute(ai,item,null,selMode,selectWrite);
  }

  /**
   * Select given attribute
   * @param ai Attribute Info
   * @param item Index of expanded attribute or -1 for normal attribute
   * @param selMode selection mode (AttributeInfo SEL_NONE,SEL_Y1,SEL_Y2,SEL_IMAGE)
   * @param selectWrite true to select the write attribute (if any)
   * @return true if the given attribute can be selected
   */
  public boolean selectAttribute(AttributeInfo ai,int item,HdbData.Aggregate agg,int selMode,boolean selectWrite) {

    int curMode = 0;
    List<JLDataView> dvs = new ArrayList<>();
    String dvSetting = null;

    if (!ai.isNumeric()) {
      if (selMode != AttributeInfo.SEL_NONE) {
        Utils.showError(ai.getName() + "\nNot a numerical attribute");
        return false;
      }
    }

    if (selMode == AttributeInfo.SEL_IMAGE && !ai.isArray()) {
      Utils.showError(ai.getName() + "\nNot an array attribute");
      return false;
    }

    if (item < 0) {

      if (ai.isAggregate()) {
        
        ai.getAggregate(agg).selection = selMode;
        JLDataView dv = ai.getAggregateDataView(agg);
        if(dv!=null)
          dvs.add(dv);

      } else {

        // Normal attribute
        if (selectWrite) {
          ai.wselection = selMode;
        } else {
          ai.selection = selMode;
        }

        if (selMode == AttributeInfo.SEL_IMAGE) {
          selectImageItem(ai, selectWrite);
          return true;
        }

        if (!selectWrite) {
          curMode = ai.selection;
          if (ai.getDataView() != null) {
            dvs.add(ai.getDataView());
          }
        } else {
          curMode = ai.wselection;
          if (ai.getWriteDataView() != null) {
            dvs.add(ai.getWriteDataView());
          }
        }

      }
      

      if (dvs.isEmpty()) {
        // Search has not been performed yet
        return true;
      }

      if (chartPanel.isShowingError())
        dvs.add(ai.getErrorDataView());
      
      for(JLDataView dv : dvs)
      {
        dv.removeFromAxis();

        JLAxis dataAxis = null;
        if (selMode == AttributeInfo.SEL_Y1) {
          dataAxis = chartPanel.chart.getY1Axis();
        } else if (selMode == AttributeInfo.SEL_Y2) {
          dataAxis = chartPanel.chart.getY2Axis();
        }
        if(dataAxis != null)
        {
          dataAxis.addDataView(dv);
        }
      }

      chartPanel.chart.repaint();
      return true;

    } else {

      if (selMode == AttributeInfo.SEL_IMAGE) {
        Utils.showError("Cannot display expanded item on image");
        return false;
      }

      // Expanded attribute
      ArrayAttributeInfo aai = ai.arrAttInfos.get(item);

      if(selectWrite) {
        aai.wselection = selMode;
        dvSetting = aai.wdvSetting;
      } else {
        aai.selection = selMode;
        dvSetting = aai.dvSetting;
      }

      int dataLength = 0;
      if (!selectWrite) {
        curMode = aai.selection;
        if(aai.chartData != null)
        {
          dvs.add(aai.chartData);
          dataLength = aai.chartData.getDataLength();
        }
      } else {
        curMode = aai.wselection;
        if(aai.wchartData != null)
        {
          dvs.add(aai.wchartData);
          dataLength = aai.wchartData.getDataLength();
        }
      }

      if (dataLength == 0)
      {

        if( ai.arrayData==null )
          // Search has not been performed yet
          return true;

        // We need to create/update the DV
        createDataviewExpanded(ai,item);
        if (!selectWrite) {
          if(aai.chartData != null)
            dvs.add(aai.chartData);
        } else {
          if(aai.wchartData != null)
            dvs.add(aai.wchartData);
        }

      }

      for(JLDataView dv : dvs)
      {
        if(dvSetting!=null) {
          CfFileReader cfr = new CfFileReader();
          cfr.parseText(dvSetting);
          dv.applyConfiguration("dv", cfr);
        }

        dv.removeFromAxis();
        //if (aai.errorData != null) {
        //  aai.errorData.removeFromAxis();
        //}

        if (selMode == AttributeInfo.SEL_Y1) {
          chartPanel.chart.getY1Axis().addDataView(dv);
          if (chartPanel.isShowingError()) {
            //chartPanel.chart.getY1Axis().addDataView(aai.errorData);
          }
        } else if (selMode == AttributeInfo.SEL_Y2) {
          chartPanel.chart.getY2Axis().addDataView(dv);
          if (chartPanel.isShowingError()) {
            //chartPanel.chart.getY2Axis().addDataView(aai.errorData);
          }
        }
      }
      chartPanel.chart.repaint();
      return true;

    }


  }
  
  private void addToDv(JLDataView dv,double time,double value,MutableDouble last,boolean step) {
    
    if( step )
      if (!Double.isNaN(last.value))
          dv.add(time, last.value);      

    dv.add(time,value);
    last.value = value;
        
  }
  
  private void createDataviewExpanded(AttributeInfo ai,int item) {

    boolean isRW = ai.isRW();
    HdbDataSet set = ai.arrayData;
    ArrayAttributeInfo aai = ai.arrAttInfos.get(item);

    Color c = defaultColor[dvIdx%defaultColor.length];

    if(aai.chartData==null) {
      aai.chartData = new JLDataView();
      aai.chartData.setColor(c);
      aai.chartData.setMarkerColor(c);
      aai.chartData.setUnit(ai.unit);
    }

    aai.chartData.setName(ai.getName()+"["+aai.idx+"]");
    dvIdx++;
    if(isRW) {
      if(aai.wchartData==null) {
        aai.wchartData = new JLDataView();
        c = defaultColor[dvIdx%defaultColor.length];
        aai.wchartData.setColor(c);
        aai.wchartData.setUnit(ai.unit);
      }
      aai.wchartData.setName(ai.getName()+"_w["+aai.idx+"]");
      dvIdx++;
    }

    // Fill the dvs
    MutableDouble lastValue = new MutableDouble(Double.NaN);
    MutableDouble lastWriteValue= new MutableDouble(Double.NaN);

    for (int j = 0; j < set.size(); j++) {

      HdbData d = set.get(j);
      double chartTime = (double) d.getDataTime() / 1000.0;

      try {

        // Read
        double r = (item>d.size())?Double.NaN:d.getValueAsDoubleArray()[item];
        addToDv(aai.chartData,chartTime,r,lastValue,aai.step);
          
        if( isRW ) {            
          double s = (item>d.sizeW())?Double.NaN:d.getWriteValueAsDoubleArray()[item];
          addToDv(aai.wchartData,chartTime,s,lastWriteValue,aai.step);            
        }

      } catch (HdbFailed e) {

        aai.chartData.add(chartTime, Double.NaN);
        if (isRW)
          aai.wchartData.add(chartTime, Double.NaN);

        lastValue.value = Double.NaN;
        lastWriteValue.value = Double.NaN;

      }

    }

  }

  /**
   * Reset all dataviews and selection
   */
  public void reset() {

    chartPanel.resetAll();
    tablePanel.table.reset();
    errorDialog.reset();
    if(selection!=null) {
      for(AttributeInfo ai:selection) {
        ai.resetData();
      }

      // Remove all script result
      int i=0;
      while(i<selection.size()) {
        AttributeInfo ai = selection.get(i);
        if(ai.sigInfo.sigId.startsWith("pyscript:"))
          selection.remove(i);
        else
          i++;
      }

    }
    chartPanel.chart.repaint();

  }

  private String indent(String s,String space) {
    return space + s.replaceAll("\n", "\n" + space).trim() + "\n";
  }

  void saveConfigFile(String fileName,boolean saveChartSettings) {

    try {

      FileWriter f = new FileWriter(fileName);
      f.write("HDBfile v1.0 {\n");
      f.write("  Global {\n");
      f.write("    script:\""+selPanel.getPyScript()+"\"\n");
      f.write("    showError:"+Boolean.toString(chartPanel.isShowingError())+"\n");
      f.write("    timeInterval:"+hdbTreePanel.getTimeInterval()+"\n");
      f.write("    hdbMode:"+hdbTreePanel.getHdbMode().ordinal()+"\n");
      if(saveChartSettings) {
        f.write("    chart:{\n");
        f.write(indent(chartPanel.chart.getConfiguration(),"      "));
        f.write("    }\n");
        f.write("    xaxis:{\n");
        f.write(indent(chartPanel.chart.getXAxis().getConfiguration("x"),"      "));
        f.write("    }\n");
        f.write("    y1axis:{\n");
        f.write(indent(chartPanel.chart.getY1Axis().getConfiguration("y1"),"      "));
        f.write("    }\n");
        f.write("    y2axis:{\n");
        f.write(indent(chartPanel.chart.getY2Axis().getConfiguration("y2"),"      "));
        f.write("    }\n");
      }
      f.write("  }\n");

      for(AttributeInfo ai:selection) {

        f.write("  Attribute {\n");
        f.write("    host:\""+ai.host+"\"\n");
        f.write("    name:\""+ai.name+"\"\n");
        f.write("    interval:"+ai.interval+"\n");
        
        if(ai.isAggregate()) {
          
          for (HdbData.Aggregate agg : ai.getAggregates()) {
            f.write("    aggregate: "+agg.toString()+" {\n");
            AggregateAttributeInfo aggInfo = ai.getAggregate(agg);
            f.write("      step:" + Boolean.toString(aggInfo.step) + "\n");
            f.write("      table:" + Boolean.toString(aggInfo.table) + "\n");
            f.write("      selection:" + aggInfo.selection + "\n");
            if (saveChartSettings && aggInfo.dv != null) {
              f.write("      dv: {\n" + aggInfo.dv.getConfiguration("        dv") + "      }\n");
            }
            f.write("    }\n");
          }
          
        } else {
          
          f.write("    step:" + Boolean.toString(ai.step) + "\n");
          f.write("    table:" + Boolean.toString(ai.table) + "\n");
          f.write("    selection:" + ai.selection + "\n");
          if (saveChartSettings && ai.getDataView() != null) {
            f.write("    dv: {\n" + ai.getDataView().getConfiguration("      dv") + "    }\n");
          }
          f.write("    wselection:" + ai.wselection + "\n");
          if (saveChartSettings && ai.getWriteDataView() != null) {
            f.write("    wdv: {\n" + ai.getWriteDataView().getConfiguration("      dv") + "    }\n");
          }

          if (ai.isExpanded()) {
            f.write("    expanded: {\n");
            for (ArrayAttributeInfo aai : ai.arrAttInfos) {
              f.write("    idx " + aai.idx + " {\n");
              f.write("      step:" + Boolean.toString(aai.step) + "\n");
              f.write("      table:" + Boolean.toString(aai.table) + "\n");
              f.write("      selection:" + aai.selection + "\n");
              if (saveChartSettings && aai.chartData != null) {
                f.write("      dv: {\n" + aai.chartData.getConfiguration("        dv") + "      }\n");
              }
              f.write("      wselection:" + aai.wselection + "\n");
              if (saveChartSettings && aai.wchartData != null) {
                f.write("      wdv: {\n" + aai.wchartData.getConfiguration("        dv") + "      }\n");
              }
              f.write("    }\n");
            }
            f.write("    }\n");
          }

        }
        
        f.write("  }\n");

      }

      f.write("}\n");
      f.close();

    } catch(IOException e) {
      Utils.showError("Cannot save file !\n"+e.getMessage());
    }


  }

  /**
   * Sets main frame configuration
   * @param showTree Show the HDB selection tree
   * @param showSelection Show the selection list panel
   * @param showViews  Show the data views
   */
  public void setVisiblePanel(boolean showTree,boolean showSelection,boolean showViews) {

    if(!showTree) closePanel(hdbTreeDockedPanel);
    if(!showSelection) closePanel(selDockedPanel);
    if(!showViews) closePanel(viewDockedPanel);

    viewDataviewCheckBoxMenuItem.setSelected(showTree);
    viewQueryCheckBoxMenuItem.setSelected(showSelection);
    viewSelectCheckBoxMenuItem.setSelected(showViews);

    updateDockedView();
  }

  /**
   * Select the view to be shown.
   * when needed, it must be called after performSearch.
   * 0=Chart
   * 1=Table
   * 2=Image
   * @param which
   */
  public void setVisibleView(int which) {
    viewDockedPanel.selectPanel(which);
  }

  public void loadSelectionFile(String fileName) {
    selPanel.loadFile(fileName);
  }

  /**
   * Perform the search
   */
  public void performSearch() {

    final String startDate = hdbTreePanel.getStartDate();
    final String stopDate = hdbTreePanel.getStopDate();

    reset();
    
    // Perform HDB query in a separate thread
    Thread doSearch = new Thread() {
      public void run() {

        // Wait for panel to be visible
        try {
          Thread.sleep(150);
        } catch(Exception e) {}

        // Array of sigIds
        ArrayList<HdbReader.SignalInput> sigIn = new ArrayList<>(selection.size());
        for (AttributeInfo att : selection) {
          HdbReader.SignalInput input = new HdbReader.SignalInput();
          input.info = new SignalInfo(att.sigInfo);
          input.startDate = startDate;
          input.endDate = stopDate;
          input.info.interval = att.interval; //this will only be used for aggregate.
          input.info.aggregates = att.getAggregates();
          input.info.indexes = att.getArrayIndexes();
          sigIn.add(input);
        }

        try {

          // Get Data from HDB
          startR = System.currentTimeMillis();
          results = hdb.getReader().getData(sigIn, hdbTreePanel.getHdbMode());
          stopR = System.currentTimeMillis();
          infoDialog.addText("Request time=" + (stopR-startR) + " ms");

          // retrieve unit
          for(AttributeInfo att : selection) {
            try {

              switch(att.sigInfo.queryConfig) {

                case HdbSigParam.QUERY_DATA:
                  if(att.sigInfo.isNumeric()) {
                    HdbSigParam p = hdb.getReader().getLastParam(att.sigInfo);
                    att.unit = p.unit;
                    att.A1 = p.display_unit;
                  }
                  break;

                case HdbSigParam.QUERY_CFG_ARCH_PERIOD:
                  att.unit = "ms";
                  break;

                case HdbSigParam.QUERY_CFG_ARCH_REL_CHANGE:
                  att.unit = "%";
                  break;

              }

            } catch(HdbFailed e) {
              infoDialog.addText("Warning: " + e.getMessage());
            }
          }

          // Apply conversion factor
          int resultIdx = 0;
          for(HdbDataSet result : results)
          {
              AttributeInfo ai = selection.get(resultIdx);
              if(ai != null && ai.A1 != 1.0)
                result.applyConversionFactor(ai.A1);
              resultIdx++;
          }

          // Run python script (if any)
          if(!selPanel.getPyScript().isEmpty()) {

            searchDlg.setTitle("Running python");
            long t0 = System.currentTimeMillis();
            PythonScript s = new PythonScript(selPanel.getPyScript());
            HdbDataSet[] pyResults = s.run(results);

            HdbDataSet[] newResults = new HdbDataSet[results.length + pyResults.length];
            System.arraycopy(results, 0, newResults, 0, results.length);


            for(int i=0;i<pyResults.length;i++) {

              newResults[results.length+i] = pyResults[i];

              // Create new AttributeInfo
              AttributeInfo ai = new AttributeInfo();
              ai.host = "pyscript";
              ai.name = pyResults[i].getName();
              ai.table = true;
              SignalInfo dummySi = new SignalInfo();
              dummySi.name = ai.name;
              SignalInfo inputInfo = results[(i<results.length)?i:0].get(0).info;
              // Assume same type as the input.
              dummySi.dataType = inputInfo.dataType;
              dummySi.format = inputInfo.format;
              dummySi.access = inputInfo.access;
              dummySi.interval = inputInfo.interval;
              dummySi.sigId = ai.host + ":" + ai.name;
              ai.sigInfo = dummySi;

              selection.add(ai);

            }

            results = newResults;

            long t1 = System.currentTimeMillis();
            infoDialog.addText("Script time=" + (t1-t0) + " ms");

          }

        } catch (HdbFailed e) {
          Utils.showError("HDB getData failed\n" + e.getMessage());
        } catch(Exception e2) {
            e2.printStackTrace();
          Utils.showError("HDB getData failed\nUnexpected exception " + e2);
          e2.printStackTrace();
        }

        searchDlg.hideDlg();

      }
    };

    // Launch the thread
    Date rDate = new Date();
    rDate.setTime(System.currentTimeMillis());
    infoDialog.addText("-------------------------------------------------------");
    infoDialog.addText("Launch request on " + hdb.getDBTypeName() + " at " + Hdb.hdbDateFormat.format(rDate));
    infoDialog.addText("Attribute number: " + selection.size());
    infoDialog.addText("Start: " + startDate);
    infoDialog.addText("Stop: " + stopDate);
    results = null;
    boolean hasProgress = hdb.getDBType()!=Hdb.HDB_ORACLE;
    if(isVisible())
      searchDlg = new ThreadDlg(this, "Fetching HDB data", hasProgress, doSearch);
    else
      searchDlg = new ThreadDlg(null, "Fetching HDB data", hasProgress, doSearch);
    searchDlg.showDlg();

    // Wait for thread completion
    try {
      doSearch.join();
    } catch (InterruptedException e) {
    }
    doSearch = null;

    if(results==null)
      // Got an error
      return;

    // Update selection AttributeInfo list
    dvIdx=0;
    int nbData = 0;
    int nbError = 0;
    ArrayList<String> colNames = new ArrayList<>();
    colNames.add("Time");
    
    imagePanel.updateImageSelection = true;

    boolean hasChart = false;
    boolean hasTable = false;
    boolean hasImage = false;

    // ----------------------------------------------------------------------
    // Browse results
    // ----------------------------------------------------------------------
    int resultIdx = 0;
    for (HdbDataSet result : results) {

      AttributeInfo attInfo = selection.get(resultIdx);
      SignalInfo.Interval interval = result.getSigInfo().interval;
      boolean isRW = attInfo.isRW();
      attInfo.dataSize = 0;
      attInfo.errorSize = 0;

      String unitSuffix = "";
      if(attInfo.unit.length()>0)
        unitSuffix = " (" + attInfo.unit + ")";
            
      // ----------------------------------------------------------------------
      // Prepare DataView
      // ----------------------------------------------------------------------
      if (attInfo.isNumeric()) {        
        if (!attInfo.isArray()) {
          hasChart = hasChart || attInfo.hasChartSelection();
          // Numeric scalar data
          // Create the chart dataview (if needed)
          dvIdx += attInfo.initializeDataViews(dvIdx, defaultColor);
        } else {
          // Image data
          hasImage = hasImage || (attInfo.selection == AttributeInfo.SEL_IMAGE);
          attInfo.arrayData = result;
          attInfo.maxArraySize = -1;
        }
      }

      // ----------------------------------------------------------------------
      // Compute table column names and indexes
      // ----------------------------------------------------------------------
      if (attInfo.isAggregate()) {

        for (HdbData.Aggregate agg : attInfo.getAggregates()) {
          if (attInfo.getAggregate(agg).table) {
            hasTable = true;
            attInfo.getAggregate(agg).tableIdx = colNames.size();
            colNames.add(attInfo.getName() + "_" + interval.toString() + "_" + agg.toString() + "(" + attInfo.getUnit(agg) + ")");
          }
        }

      } else {

        if (attInfo.table) {
          hasTable = true;
          attInfo.tableIdx = colNames.size();
          colNames.add(attInfo.getName() + unitSuffix);
          if (isRW) {
            attInfo.wtableIdx = colNames.size();
            colNames.add(attInfo.getName() + "_w" + unitSuffix);
          }
        }

        // Expanded attribute in table
        if (attInfo.isExpanded()) {

          for (ArrayAttributeInfo aai : attInfo.arrAttInfos) {

            if (aai.table) {

              hasTable = true;

              int idx = aai.idx;
              String unitPreffix = "";
              if (attInfo.unit.length() > 0) {
                unitPreffix = " (" + attInfo.unit + ")";
              }
              aai.tableIdx = colNames.size();
              colNames.add(attInfo.getName() + "[" + idx + "]" + unitPreffix);
              if (isRW) {
                aai.wtableIdx = colNames.size();
                colNames.add(attInfo.getName() + "_w[" + idx + "]" + unitPreffix);
              }
            }
          }
        }

      }
      
      double lastValueForError = 0.0;
      
      // Last value (used for step representation)
      MutableDouble lastValue = new MutableDouble(Double.NaN);
      MutableDouble lastWriteValue = new MutableDouble(Double.NaN);
      if(attInfo.isAggregate()) {
        for (HdbData.Aggregate agg : attInfo.getAggregates())
          attInfo.getAggregate(agg).lastValue = new MutableDouble(Double.NaN);
      }

      // ----------------------------------------------------------------------
      // Browse data (fill charts and table)
      // ----------------------------------------------------------------------
      
      for (HdbData d : result) {

        double chartTime = (double) d.getDataTime() / 1000.0;
                
        if( attInfo.isAggregate() ) {
          
          // Aggregate
          try {
            for (HdbData.Aggregate agg : attInfo.getAggregates()) {
              List<Number> aggData = d.getAggregate().get(agg);
              AggregateAttributeInfo aggInfo = attInfo.getAggregate(agg);
              double aggValue = Double.NaN;
              int aggSize = aggData==null?0:aggData.size();
              if (aggSize > 0)
                aggValue = aggData.get(0).doubleValue();
              
              if(!attInfo.isArray())
              {
                addToDv(attInfo.getAggregateDataView(agg), chartTime, aggValue, aggInfo.lastValue, aggInfo.step);
              }
              
              if(aggInfo.table)
              {
                String tableValue = Double.toString(aggValue);
                if(attInfo.isArray())
                {
                    StringBuilder array = new StringBuilder();
                    array.append(agg.toString());
                    array.append("[");
                    array.append(aggSize);
                    array.append("]\n");
                    for(int i = 0; i < aggSize; i++)
                    {
                        array.append(aggData.get(i));
                        if(i != aggData.size()-1)
                            array.append("\n");
                    }
                    tableValue = array.toString();
                }
                tablePanel.table.add(tableValue, d.getQualityFactor(), d.getDataTime(), aggInfo.tableIdx);
              }
            }
            attInfo.dataSize++;
          } catch (HdbFailed e) {
            // getAggregate() should not throw DevFailed
            System.out.println("MainPanel.performSearch(): getAggregate() Unexpected HdbFailed " + e.getMessage());
          }

        } else if (attInfo.isNumeric() && !attInfo.isArray()) {

          // Numeric scalar data          
          try {

            addToDv(attInfo.getDataView(), chartTime, d.getValueAsDouble(), lastValue, attInfo.step);
            if (attInfo.table)
            {
              String err = "";
              if(d.hasFailed())
              {
                err = "/Err"+d.getErrorMessage();
              }
              else if (d.isInvalid()) {
                err = "/ErrATTR_INVALID";
              }
              if(!err.isEmpty())
              {
                tablePanel.table.add(err, 1, d.getDataTime(), attInfo.tableIdx);
              }
              else if (attInfo.isState())
              {
                tablePanel.table.add("/State" + d.getValueAsString(),
                      d.getQualityFactor(),
                      d.getDataTime(), attInfo.tableIdx);
              }
              else
              {
                tablePanel.table.add(Double.toString(d.getValueAsDouble()), d.getQualityFactor(), d.getDataTime(), attInfo.tableIdx);
              }
            }
            if (isRW) {
              addToDv(attInfo.getWriteDataView(), chartTime, d.getWriteValueAsDouble(), lastWriteValue, attInfo.step);
              if (attInfo.table)
              {
              String err = "";
              if(d.hasFailed())
              {
                err = "/Err"+d.getErrorMessage();
            
              }
              else if (d.isInvalid()) {
                err = "/ErrATTR_INVALID";
              }
              if(!err.isEmpty())
              {
                tablePanel.table.add(err, 1, d.getDataTime(), attInfo.wtableIdx);
              }
              else if (attInfo.isState()) {
                  tablePanel.table.add("/State" + d.getWriteValueAsString(),
                        d.getQualityFactor(),
                        d.getDataTime(), attInfo.wtableIdx);
                }
                else
                {
                  tablePanel.table.add(Double.toString(d.getWriteValueAsDouble()), d.getQualityFactor(), d.getDataTime(), attInfo.wtableIdx);
                }
              }
            }
            attInfo.dataSize++;

          } catch (HdbFailed e) {

            attInfo.getDataView().add(chartTime, Double.NaN);
            if (isRW)
              attInfo.getWriteDataView().add(chartTime, Double.NaN);
            attInfo.getErrorDataView().add(chartTime, lastValueForError);
            attInfo.errors.add(d);
            lastValue.value = Double.NaN;
            lastWriteValue.value = Double.NaN;
            attInfo.errorSize++;
            nbError++;
            errorDialog.addError(attInfo.getName(), d);

            if (attInfo.table) {
              String err = "/Err"+e.getMessage();
              tablePanel.table.add(err, d.getQualityFactor(), d.getDataTime(), attInfo.tableIdx);
              if (isRW)
                tablePanel.table.add(err, d.getQualityFactor(), d.getDataTime(), attInfo.wtableIdx);
            }

          }

        } else {

          // Update maxArray size (for image display)
          if (d.size() > attInfo.maxArraySize)
            attInfo.maxArraySize = d.size();

          // Non numeric attribute or array
          // Non numeric can go only into table
          if (d.hasFailed()) {

            errorDialog.addError(attInfo.getName(), d);
            attInfo.errorSize++;
            nbError++;
            String err = "/Err"+d.getErrorMessage();
            if (attInfo.table) {
              tablePanel.table.add(err, d.getQualityFactor(), d.getDataTime(), attInfo.tableIdx);
              if (isRW) {
                tablePanel.table.add(err, d.getQualityFactor(), d.getDataTime(), attInfo.wtableIdx);
              }
            }

            if (attInfo.isExpanded()) {
              for (ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                if (aai.table) {
                  tablePanel.table.add(err, d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                  if (isRW) {
                    tablePanel.table.add(err, d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);
                  }
                }
              }
            }

          } else {

            if (attInfo.isState()) {

              // State attribute
              if(attInfo.table) {
                tablePanel.table.add("/State" + d.getValueAsString(),
                      d.getQualityFactor(),
                      d.getDataTime(), attInfo.tableIdx);
                if (isRW)
                  tablePanel.table.add("/State" + d.getWriteValueAsString(),
                        d.getQualityFactor(),
                        d.getDataTime(), attInfo.wtableIdx);
              }
              
              // Expanded state array
              if(attInfo.isExpanded()) {
                try {
                  int[] hdbV = ((HdbStateArray) d).getValue();
                  int[] hdbVWrite = ((HdbStateArray) d).getWriteValue();
                  int idx = 0;
                  for (ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                    if(aai.table) {
                      tablePanel.table.add("/State" + HdbState.getStateString(hdbV[idx]), d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                      if (isRW)
                        tablePanel.table.add("/State" + HdbState.getStateString(hdbVWrite[idx]), d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);
                      idx++;
                    }
                  }
                } catch(HdbFailed e) {}              
              }
              
            } else {

              // Non state attributes
              if( attInfo.table ) {
                tablePanel.table.add(d.getValueAsString(),
                        d.getQualityFactor(),
                        d.getDataTime(), attInfo.tableIdx);

                if (isRW)
                  tablePanel.table.add(d.getWriteValueAsString(),
                          d.getQualityFactor(),
                          d.getDataTime(), attInfo.wtableIdx);
              }
            
              if(attInfo.isExpanded()) {
                try {
                  // Do not use convenience function for performance
                  // Not all type implemented
                  // Default to convenience function
                  if (d instanceof HdbFloatArray) {
                    float[] hdbV = ((HdbFloatArray) d).getValue();
                    float[] hdbVWrite = ((HdbFloatArray) d).getWriteValue();
                  int idx = 0;
                    for(ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                      if(aai.table) {
                        tablePanel.table.add(Float.toString(hdbV[idx]), d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                        if(isRW)
                          tablePanel.table.add(Float.toString(hdbVWrite[idx]), d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);                          
                      idx++;
                      }
                    }
                  } else if (d instanceof HdbLongArray) {
                    int[] hdbV = ((HdbLongArray) d).getValue();
                    int[] hdbVWrite = ((HdbLongArray) d).getWriteValue();
                  int idx = 0;
                    for(ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                      if(aai.table) {
                        tablePanel.table.add(Integer.toString(hdbV[idx]), d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                        if(isRW)
                          tablePanel.table.add(Integer.toString(hdbVWrite[idx]), d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);                          
                      idx++;
                      }
                    }
                  } else if (d instanceof HdbLong64Array) {
                    long[] hdbV = ((HdbLong64Array) d).getValue();
                    long[] hdbVWrite = ((HdbLong64Array) d).getWriteValue();
                  int idx = 0;
                    for(ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                      if(aai.table) {
                        tablePanel.table.add(Long.toString(hdbV[idx]), d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                        if(isRW)
                          tablePanel.table.add(Long.toString(hdbVWrite[idx]), d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);                          
                      idx++;
                      }
                    }
                  } else if (d instanceof HdbShortArray) {
                    short[] hdbV = ((HdbShortArray) d).getValue();
                    short[] hdbVWrite = ((HdbShortArray) d).getWriteValue();
                  int idx = 0;
                    for(ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                      if(aai.table) {
                        tablePanel.table.add(Short.toString(hdbV[idx]), d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                        if(isRW)
                          tablePanel.table.add(Short.toString(hdbVWrite[idx]), d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);                          
                      idx++;
                      }
                    }
                  } else if (d instanceof HdbStringArray) {
                    String[] hdbV = ((HdbStringArray) d).getValue();
                    String[] hdbVWrite = ((HdbStringArray) d).getValue();
                  int idx = 0;
                    for(ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                      if(aai.table) {
                        tablePanel.table.add(hdbV[idx], d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                        if(isRW)
                          tablePanel.table.add(hdbVWrite[idx], d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);                          
                      idx++;
                      }
                    }                          
                  } else {
                    double[] hdbV = d.getValueAsDoubleArray();
                    double[] hdbVWrite = d.getValueAsDoubleArray();
                  int idx = 0;
                    for(ArrayAttributeInfo aai : attInfo.arrAttInfos) {
                      if(aai.table) {
                        tablePanel.table.add(Double.toString(hdbV[idx]), d.getQualityFactor(), d.getDataTime(), aai.tableIdx);
                        if(isRW)
                          tablePanel.table.add(Double.toString(hdbVWrite[idx]), d.getQualityFactor(), d.getDataTime(), aai.wtableIdx);                          
                      idx++;
                      }
                    }                    
                  }
                  
                } catch (HdbFailed e) {}
                
              }
              
            }
            attInfo.dataSize++;
          }

        }
          
      }
     
      resultIdx++;
    }

    infoDialog.addText("Total data="+nbData);
    infoDialog.addText("Total error="+nbError);

    // Select chart and image attribute
    for(AttributeInfo ai : selection) {

      if (ai.isAggregate()) {
        
        // Select aggregates
        for(HdbData.Aggregate agg:ai.getAggregates())
          selectAttribute(ai, -1, agg,ai.getAggregate(agg).selection, false);        

      } else {
        
        // Select normal attribute
        selectAttribute(ai, -1, ai.selection, false);
        if (ai.isRW()) {
          selectAttribute(ai, -1, ai.wselection, true);
        }

        // Select expanded attribute
        if (ai.isExpanded()) {
          for (int i = 0; i < ai.arrAttInfos.size(); i++) {
            ArrayAttributeInfo aai = ai.arrAttInfos.get(i);
            selectAttribute(ai, i, aai.selection, false);
            if (ai.isRW()) {
              selectAttribute(ai, i, aai.wselection, true);
            }
          }
        }
        
      }

    }

    // Build  Table
    // if there is only one element in the colNames, it is the time,
    // so do not add any table
    tablePanel.table.setColumnName(colNames.size() == 1 ? new String[0] : colNames.toArray(new String[0]));
    tablePanel.table.dataChanged();
    errorDialog.commit();

    // Update XAxis selection
    chartPanel.resetXItem();
    for(AttributeInfo ai : selection) {

      if(ai.isNumeric()) {

        if(ai.isArray()) {
          if(ai.isExpanded()) {
            for(int i=0;i<ai.arrAttInfos.size();i++) {
              ArrayAttributeInfo aai = ai.arrAttInfos.get(i);
              chartPanel.addXArrayItem(ai,aai);
            }
          }
        } else {
          chartPanel.addXItem(ai);
        }

      }

    }


    // Focus on panel
    if(hasChart)
      viewDockedPanel.selectPanel(0);
    else if(hasImage)
      viewDockedPanel.selectPanel(2);
    else if(hasTable)
      viewDockedPanel.selectPanel(1);

    imagePanel.updateImageSelection = false;

  }

 
  void updateSelectionList() {

    selPanel.updateSelectionList();

  }

  @Override
  public String[] clickOnChart(JLChartEvent jlce) {

    JLDataView srcDataView = jlce.getDataView();

    boolean foundE = false;
    boolean foundR = false;
    boolean foundW = false;

    int i=0;
    while(!foundE && !foundR && !foundW && i<selection.size()) {
      foundE = selection.get(i).getErrorDataView() == srcDataView;
      foundR = selection.get(i).getDataView() == srcDataView;
      foundW = selection.get(i).getWriteDataView() == srcDataView;
      if(!foundE && !foundR && !foundW) i++;
    }

    if(!foundE && !foundR && !foundW) {
      // Default behavior
      return null;
    }

    AttributeInfo ai = selection.get(i);

    if(foundE) {

      // We have an error Dataview
      int idx = jlce.getDataViewIndex();
      String[] errors = Utils.makeStringArray(ai.errors.get(idx).getErrorMessage());

      String[] ret = new String[2+errors.length];
      ret[0] = ai.getName();
      ret[1] = Utils.formatTime((long)(jlce.getXValue()*1000.0), Utils.FORMAT_MS);
      for(int j=0;j<errors.length;j++)
        ret[2+j] = errors[j];
      return ret;

    } else if (ai.isState()) {

      // We have a state
      String[] ret = new String[3];
      if(foundR)
        ret[0] = ai.getName();
      else
        ret[0] = ai.getName()+"_w";
      ret[1] = Utils.formatTime((long)(jlce.getXValue()*1000.0), Utils.FORMAT_MS);
      ret[2] = HdbState.getStateString((int)(jlce.getYValue()+0.5));
      return ret;

    } else {

      // Default behavior
      return null;

    }

  }

  void buildImageData() {

    HdbDataSet hdbSet = selectedImage.arrayData;
    boolean showError = imagePanel.imageErrorCheck.isSelected();
    boolean isW = selectedImageRW;

    imagePanel.nameLabel.setText(selectedImage.name+(selectedImageRW?"_w":""));

    int lgth;
    if(hdbSet==null)
      lgth = 0;
    else
      lgth = hdbSet.size();

    if(lgth==0) {
      imagePanel.image.clearData();
      return;
    }

    // Compute length without error
    int lgthNoError = 0;
    for (int i = 0; i < lgth; i++) {
      if(!hdbSet.get(i).hasFailed())
        lgthNoError++;
    }

    long[] times;
    double[][] data;

    if(showError) {
      times= new long[lgth];
      data = new double[lgth][];
    } else {
      times= new long[lgthNoError];
      data = new double[lgthNoError][];
    }

    int k=0;
    for (int i = 0; i < lgth; i++) {

      if (showError) {

        times[k] = hdbSet.get(i).getDataTime() / 1000;
        try {
          if (isW) {
            data[k] = hdbSet.get(i).getWriteValueAsDoubleArray();
          } else {
            data[k] = hdbSet.get(i).getValueAsDoubleArray();
          }
        } catch (HdbFailed e) {
          data[k] = new double[0];
        }
        k++;

      } else {

        if (!hdbSet.get(i).hasFailed()) {

          times[k] = hdbSet.get(i).getDataTime() / 1000;
          try {
            if (isW) {
              data[k] = hdbSet.get(i).getWriteValueAsDoubleArray();
            } else {
              data[k] = hdbSet.get(i).getValueAsDoubleArray();
            }
          } catch (HdbFailed e) {
            // Should not happen !
            data[k] = new double[0];
          }
          k++;

        }

      }

    }

    imagePanel.image.setData(times, data);

  }

  void selectImageItem(AttributeInfo ai,boolean selectWrite) {

    selectedImage = ai;
    selectedImageRW = selectWrite;
    buildImageData();

  }

  void updateDockedView() {

    if(!viewDockedPanel.visible && !hdbTreeDockedPanel.visible) {
      hSplitPane.setTopComponent(null);
    } else {
      hSplitPane.setTopComponent(vSplitPane);
    }

    viewDataviewCheckBoxMenuItem.setSelected(viewDockedPanel.visible);
    viewQueryCheckBoxMenuItem.setSelected(hdbTreeDockedPanel.visible);
    viewSelectCheckBoxMenuItem.setSelected(selDockedPanel.visible);

  }

  void viewPanel(DockedPanel panel) {

    if(panel==viewDockedPanel) {
      vSplitPane.setRightComponent(viewDockedPanel);
      viewDockedPanel.visible = true;
    } else if(panel==hdbTreeDockedPanel) {
      vSplitPane.setLeftComponent(hdbTreeDockedPanel);
      hdbTreeDockedPanel.visible = true;
    } else if(panel==selDockedPanel) {
      hSplitPane.setBottomComponent(selDockedPanel);
      selDockedPanel.visible = true;
    }

    updateDockedView();

  }

  void closePanel(DockedPanel panel) {

    if(panel==viewDockedPanel) {
      vSplitPane.setRightComponent(null);
      viewDockedPanel.visible = false;
    } else if(panel==hdbTreeDockedPanel) {
      vSplitPane.setLeftComponent(null);
      hdbTreeDockedPanel.visible = false;
    } else if(panel==selDockedPanel) {
      hSplitPane.setBottomComponent(null);
      selDockedPanel.visible = false;
     }

    updateDockedView();

  }

  void minimizePanel(DockedPanel panel) {

    JPanel p = (JPanel)getContentPane();
    p.remove(panel);
    p.add(hSplitPane,BorderLayout.CENTER);
    viewPanel(panel);

    viewDataviewCheckBoxMenuItem.setEnabled(true);
    viewQueryCheckBoxMenuItem.setEnabled(true);
    viewSelectCheckBoxMenuItem.setEnabled(true);

  }

  void maximizePanel(DockedPanel panel) {

    JPanel p = (JPanel)getContentPane();
    p.remove(hSplitPane);
    p.add(panel,BorderLayout.CENTER);
    ((JPanel)getContentPane()).revalidate();

    viewDataviewCheckBoxMenuItem.setEnabled(false);
    viewQueryCheckBoxMenuItem.setEnabled(false);
    viewSelectCheckBoxMenuItem.setEnabled(false);

  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    hSplitPane = new javax.swing.JSplitPane();
    vSplitPane = new javax.swing.JSplitPane();
    jMenuBar1 = new javax.swing.JMenuBar();
    fileMenu = new javax.swing.JMenu();
    exitMenuItem = new javax.swing.JMenuItem();
    viewMenu = new javax.swing.JMenu();
    errorsMenuItem = new javax.swing.JMenuItem();
    infoMenuItem = new javax.swing.JMenuItem();
    jSeparator1 = new javax.swing.JPopupMenu.Separator();
    viewDataviewCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
    viewQueryCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
    viewSelectCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
    refreshTreeMenuItem = new javax.swing.JMenuItem();

    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

    hSplitPane.setDividerSize(5);
    hSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

    vSplitPane.setDividerSize(5);
    hSplitPane.setLeftComponent(vSplitPane);

    getContentPane().add(hSplitPane, java.awt.BorderLayout.CENTER);

    fileMenu.setText("File");

    exitMenuItem.setText("Exit");
    exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        exitMenuItemActionPerformed(evt);
      }
    });
    fileMenu.add(exitMenuItem);

    jMenuBar1.add(fileMenu);

    viewMenu.setText("View");

    errorsMenuItem.setText("Errors ...");
    errorsMenuItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        errorsMenuItemActionPerformed(evt);
      }
    });
    viewMenu.add(errorsMenuItem);

    infoMenuItem.setText("Informations ...");
    infoMenuItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        infoMenuItemActionPerformed(evt);
      }
    });
    viewMenu.add(infoMenuItem);
    viewMenu.add(jSeparator1);

    viewDataviewCheckBoxMenuItem.setSelected(true);
    viewDataviewCheckBoxMenuItem.setText("Dataviews");
    viewDataviewCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        viewDataviewCheckBoxMenuItemActionPerformed(evt);
      }
    });
    viewMenu.add(viewDataviewCheckBoxMenuItem);

    viewQueryCheckBoxMenuItem.setSelected(true);
    viewQueryCheckBoxMenuItem.setText("HDB Tree");
    viewQueryCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        viewQueryCheckBoxMenuItemActionPerformed(evt);
      }
    });
    viewMenu.add(viewQueryCheckBoxMenuItem);

    viewSelectCheckBoxMenuItem.setSelected(true);
    viewSelectCheckBoxMenuItem.setText("Selection table");
    viewSelectCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        viewSelectCheckBoxMenuItemActionPerformed(evt);
      }
    });
    viewMenu.add(viewSelectCheckBoxMenuItem);

    refreshTreeMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
    refreshTreeMenuItem.setText("Refresh Tree");
    refreshTreeMenuItem.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        refreshTreeMenuItemActionPerformed(evt);
      }
    });
    viewMenu.add(refreshTreeMenuItem);

    jMenuBar1.add(viewMenu);

    setJMenuBar(jMenuBar1);

    pack();
  }// </editor-fold>//GEN-END:initComponents

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
      if(runningFromShell) System.exit(0);
      else setVisible(false);
    }//GEN-LAST:event_exitMenuItemActionPerformed

  private void errorsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_errorsMenuItemActionPerformed

    ATKGraphicsUtils.centerFrame((JPanel)getContentPane(), errorDialog);
    errorDialog.setVisible(true);

  }//GEN-LAST:event_errorsMenuItemActionPerformed

  private void viewQueryCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewQueryCheckBoxMenuItemActionPerformed
    if(viewQueryCheckBoxMenuItem.isSelected()) {
      viewPanel(hdbTreeDockedPanel);
    } else {
      closePanel(hdbTreeDockedPanel);
    }
  }//GEN-LAST:event_viewQueryCheckBoxMenuItemActionPerformed

  private void viewDataviewCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewDataviewCheckBoxMenuItemActionPerformed
    if(viewDataviewCheckBoxMenuItem.isSelected()) {
      viewPanel(viewDockedPanel);
    } else {
      closePanel(viewDockedPanel);
    }
  }//GEN-LAST:event_viewDataviewCheckBoxMenuItemActionPerformed

  private void viewSelectCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewSelectCheckBoxMenuItemActionPerformed
    if(viewSelectCheckBoxMenuItem.isSelected()) {
      viewPanel(selDockedPanel);
    } else {
      closePanel(selDockedPanel);
    }
  }//GEN-LAST:event_viewSelectCheckBoxMenuItemActionPerformed

  private void infoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_infoMenuItemActionPerformed
    ATKGraphicsUtils.centerFrame((JPanel)getContentPane(), infoDialog);
    infoDialog.setVisible(true);
  }//GEN-LAST:event_infoMenuItemActionPerformed

  private void refreshTreeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshTreeMenuItemActionPerformed
    // TODO add your handling code here:
    hdbTreePanel.refreshTree();
  }//GEN-LAST:event_refreshTreeMenuItemActionPerformed

  /**
   * @param args the command line arguments
   */
  public static void main(String args[]) {

    if(args.length==0) {


      new MainPanel().setVisible(true);

    } else {

      if(args.length%2!=0) {
        System.out.println("Invalid options");
        System.out.println("Usage jhdbviewer [-f filename] [-p (panel=tree|sel|view)] -[v view]");
        System.exit(0);
      }

      MainPanel p = new MainPanel(true,true);
      for(int i=0;i<args.length;i+=2) {

        if(args[i].equals("-f")) {
          String fileName = args[i+1];
          p.loadSelectionFile(fileName);
        } else if( args[i].equals("-p")) {
          int panel = Integer.parseInt(args[i+1]);
          boolean tree = (panel & 4) != 0;
          boolean select = (panel & 2) != 0;
          boolean views = (panel & 1) != 0;
          p.setVisiblePanel(tree, select, views);
        } else if( args[i].equals("-v")) {
          int view = Integer.parseInt(args[i+1]);
          p.setVisibleView(view);
        } else {
          System.out.println("Unknown option " + args[i]);
          System.out.print("Usage jhdbviewer [-f selection_filename] [-p (panel=tree|sel|view)] -[v view]");
          System.exit(0);
        }

      }

      p.performSearch();
      p.setVisible(true);

    }

    /*
    MainPanel p = new MainPanel(false);
    try {
      AttributeInfo ai = p.addAttribute("orion.esrf.fr:10000", "sr/d-ct/1/current");
      ai.table = true;
      p.selectAttribute(ai, AttributeInfo.SEL_Y1, false);
      p.setTimeInterval(1);
      p.performSearch();
      p.setVisiblePanel(false, false, true);
      p.setVisibleView(1);
      p.setVisible(true);

    } catch(HdbFailed e) {
      Utils.showError(e.getMessage());
    }
    */

  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JMenuItem errorsMenuItem;
  private javax.swing.JMenuItem exitMenuItem;
  private javax.swing.JMenu fileMenu;
  private javax.swing.JSplitPane hSplitPane;
  private javax.swing.JMenuItem infoMenuItem;
  private javax.swing.JMenuBar jMenuBar1;
  private javax.swing.JPopupMenu.Separator jSeparator1;
  private javax.swing.JMenuItem refreshTreeMenuItem;
  private javax.swing.JSplitPane vSplitPane;
  private javax.swing.JCheckBoxMenuItem viewDataviewCheckBoxMenuItem;
  private javax.swing.JMenu viewMenu;
  private javax.swing.JCheckBoxMenuItem viewQueryCheckBoxMenuItem;
  private javax.swing.JCheckBoxMenuItem viewSelectCheckBoxMenuItem;
  // End of variables declaration//GEN-END:variables

  @Override
  public void progress(HdbReader reader, double d,int current,int nb) {
    //long t = System.currentTimeMillis();
    //System.out.println("Progress="+d+" ("+(t-startR)+"ms)");
    if(searchDlg!=null) {
      if(nb>1)
        searchDlg.setProgress(d,current+"/"+nb);
      else
        searchDlg.setProgress(d,null);
    }
  }

}

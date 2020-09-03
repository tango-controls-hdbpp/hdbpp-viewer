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
      Utils.showError("Cannot connet to HDB\n" + e.getMessage());
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
        
    selection = new ArrayList<AttributeInfo>();
    
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
   * Unselect given attribute from data views
   * @param ai Attribute to unselect
   * @param item Index of expanded attribute or -1 for normal attribute
   */
  public void unselectAttribute(AttributeInfo ai,int item) {

    JLAxis axis;
    
    if(item<0) {
      
      if(ai.chartData!=null) 
        ai.chartData.removeFromAxis();
    
      if(ai.wchartData!=null)
        ai.wchartData.removeFromAxis();

      if(ai.errorData!=null) 
        ai.errorData.removeFromAxis();
    
      ai.selection = AttributeInfo.SEL_NONE;
      ai.wselection = AttributeInfo.SEL_NONE;
    
    } else {

      ArrayAttributeInfo aai = ai.arrAttInfos.get(item);
      
      if(aai.chartData!=null) 
        aai.chartData.removeFromAxis();
    
      if(aai.wchartData!=null)
        aai.wchartData.removeFromAxis();

      //if(aai.errorData!=null) 
      //  aai.errorData.removeFromAxis();
    
      aai.selection = AttributeInfo.SEL_NONE;
      aai.wselection = AttributeInfo.SEL_NONE;
      
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
  
  /**
   * Select given attribute
   * @param ai Attribute Info
   * @param item Index of expanded attribute or -1 for normal attribute
   * @param selMode selection mode (AttributeInfo SEL_NONE,SEL_Y1,SEL_Y2,SEL_IMAGE)
   * @param selectWrite true to select the write attribute (if any)
   * @return true if the given attribute can be selected
   */
  public boolean selectAttribute(AttributeInfo ai,int item,int selMode,boolean selectWrite) {
    
    int curMode = 0;
    JLDataView dv = null;
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
        dv = ai.chartData;
      } else {
        curMode = ai.wselection;
        dv = ai.wchartData;
      }

      if (dv == null) {
        // Search has not been performed yet
        return true;
      }

      JLAxis axis;

      dv.removeFromAxis();
      if (ai.errorData != null) {
        ai.errorData.removeFromAxis();
      }

      if (selMode == AttributeInfo.SEL_Y1) {
        chartPanel.chart.getY1Axis().addDataView(dv);
        if (chartPanel.isShowingError()) {
          chartPanel.chart.getY1Axis().addDataView(ai.errorData);
        }
      } else if (selMode == AttributeInfo.SEL_Y2) {
        chartPanel.chart.getY2Axis().addDataView(dv);
        if (chartPanel.isShowingError()) {
          chartPanel.chart.getY2Axis().addDataView(ai.errorData);
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
      
      if (!selectWrite) {
        curMode = aai.selection;
        dv = aai.chartData;        
      } else {
        curMode = aai.wselection;
        dv = aai.wchartData;
      }

      if (dv == null || dv.getDataLength()==0) {

        if( ai.arrayData==null )
          // Search has not been performed yet
          return true;
        
        // We need to create/update the DV
        createDataviewExpanded(ai,item);
        if (!selectWrite) {
          dv = aai.chartData;
        } else {
          dv = aai.wchartData;
        }
        
      }
            
      JLAxis axis;
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

      chartPanel.chart.repaint();
      return true;
      
    }

        
  }
  
  private void createDataviewExpanded(AttributeInfo ai,int item) {

    boolean isRW = ai.isRW();
    HdbDataSet set = ai.arrayData;
    ArrayAttributeInfo aai = ai.arrAttInfos.get(item);
    
    Color c = defaultColor[dvIdx%defaultColor.length];
    
    if(aai.chartData==null) {
      aai.chartData = new JLDataView();
      aai.chartData.setColor(c);
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
    double lastValue = Double.NaN;
    double lastWriteValue = Double.NaN;
    
    for (int j = 0; j < set.size(); j++) {

      HdbData d = set.get(j);
      double chartTime = (double) d.getDataTime() / 1000.0;

      try {

        if (aai.step) {

          // Read
          if (!Double.isNaN(lastValue)) 
            aai.chartData.add(chartTime, lastValue);
          
          if(aai.idx>d.size()) {
            aai.chartData.add(chartTime,Double.NaN);
            lastValue = Double.NaN;
          } else  {
            lastValue = d.getValueAsDoubleArray()[aai.idx];
            aai.chartData.add(chartTime,lastValue);
          }

          // Write
          if( isRW ) {
            
            if (!Double.isNaN(lastWriteValue)) 
              aai.wchartData.add(chartTime, lastValue);
          
            if(aai.idx>d.sizeW()) {
              aai.wchartData.add(chartTime,Double.NaN);
              lastWriteValue = Double.NaN;
            } else  {
              lastWriteValue = d.getWriteValueAsDoubleArray()[aai.idx];
              aai.wchartData.add(chartTime,lastWriteValue);
            }
            
          }

        } else {

          aai.chartData.add(chartTime, d.getValueAsDoubleArray()[aai.idx]);
          if (isRW)
            aai.wchartData.add(chartTime, d.getWriteValueAsDoubleArray()[aai.idx]);

        }

      } catch (HdbFailed e) {

        aai.chartData.add(chartTime, Double.NaN);
        if (isRW)
          aai.wchartData.add(chartTime, Double.NaN);
        
        lastValue = Double.NaN;
        lastWriteValue = Double.NaN;

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
        if(ai.chartData!=null)
          ai.chartData.reset();        
        if(ai.wchartData!=null)
          ai.wchartData.reset();
        ai.arrayData = null;
        if(ai.errorData!=null)
          ai.errorData.reset();
        if(ai.isExpanded()) {
          for(ArrayAttributeInfo aai:ai.arrAttInfos) {
            if(aai.chartData!=null)
              aai.chartData.reset();
            if(aai.wchartData!=null)
              aai.wchartData.reset();
          }
        }
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
      f.write("    hdbMode:"+hdbTreePanel.getHdbMode()+"\n");
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
        f.write("    step:"+Boolean.toString(ai.step)+"\n");
        f.write("    table:"+Boolean.toString(ai.table)+"\n");
        f.write("    selection:"+ai.selection+"\n");
        if(saveChartSettings && ai.chartData != null) {
          f.write("    dv: {\n"+ai.chartData.getConfiguration("      dv")+"    }\n");          
        }
        f.write("    wselection:"+ai.wselection+"\n");        
        if(saveChartSettings && ai.wchartData != null) {
          f.write("    wdv: {\n"+ai.wchartData.getConfiguration("      dv")+"    }\n");          
        }
        
        if(ai.isExpanded()) {
          f.write("    expanded: {\n");        
          for(ArrayAttributeInfo aai:ai.arrAttInfos) {
            f.write("    idx "+aai.idx+" {\n");
            f.write("      step:"+Boolean.toString(aai.step)+"\n");
            f.write("      table:"+Boolean.toString(aai.table)+"\n");
            f.write("      selection:"+aai.selection+"\n");
            if(saveChartSettings && aai.chartData != null) {
              f.write("      dv: {\n"+aai.chartData.getConfiguration("        dv")+"      }\n");          
            }
            f.write("      wselection:"+aai.wselection+"\n");                    
            if(saveChartSettings && aai.wchartData != null) {
              f.write("      wdv: {\n"+aai.wchartData.getConfiguration("        dv")+"      }\n");          
            }
            f.write("    }\n");
          }          
          f.write("    }\n");       
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
    String[] colNames = null;
    
    reset();
 
    // Perform HDB query in a separate thread
    Thread doSearch = new Thread() {
      public void run() {

        // Wait for panel to be visible
        try {
          Thread.sleep(150);
        } catch(Exception e) {}
      
        // Array of sigIds
        SignalInfo[] sigIn = new SignalInfo[selection.size()];
        for (int i = 0; i < sigIn.length; i++) {
          sigIn[i] = selection.get(i).sigInfo;
        }

        try {
          
          // Get Data from HDB
          startR = System.currentTimeMillis();          
          results = hdb.getReader().getData(sigIn, startDate, stopDate, hdbTreePanel.getHdbMode());
          stopR = System.currentTimeMillis();
          infoDialog.addText("Request time=" + (stopR-startR) + " ms");
          
          // retreive unit
          for(int i=0;i<sigIn.length;i++) {
            try {
              
              switch(sigIn[i].queryConfig) {
                
                case HdbSigParam.QUERY_DATA:
                  if(sigIn[i].isNumeric()) {
                    HdbSigParam p = hdb.getReader().getLastParam(sigIn[i]);
                    selection.get(i).unit = p.unit;              
                    selection.get(i).A1 = p.display_unit;
                  }
                  break;
                  
                case HdbSigParam.QUERY_CFG_ARCH_PERIOD:
                  selection.get(i).unit = "ms";
                  break;
                  
                case HdbSigParam.QUERY_CFG_ARCH_REL_CHANGE:
                  selection.get(i).unit = "%";
                  break;
              
              }
              
            } catch(HdbFailed e) {
              infoDialog.addText("Warning: " + e.getMessage());
            }
          }
          
          // Apply conversion factor
          for(int i=0;i<sigIn.length;i++) {
            double A1 = selection.get(i).A1;
            if(A1!=1.0)
              results[i].applyConversionFactor(A1);
          }
  
          // Run python script (if any)
          if(!selPanel.getPyScript().isEmpty()) {
            
            searchDlg.setTitle("Running python");
            long t0 = System.currentTimeMillis();
            PythonScript s = new PythonScript(selPanel.getPyScript());
            HdbDataSet[] pyResults = s.run(results);
            
            HdbDataSet[] newResults = new HdbDataSet[results.length + pyResults.length];
            for(int i=0;i<results.length;i++)
              newResults[i] = results[i];
            
            
            for(int i=0;i<pyResults.length;i++) {
              
              newResults[results.length+i] = pyResults[i];
              
              // Create new AttributeInfo
              AttributeInfo ai = new AttributeInfo();
              ai.host = "pyscript";
              ai.name = pyResults[i].getName();
              ai.table = true;
              SignalInfo dummySi = new SignalInfo();
              dummySi.name = ai.name;
              int resultIdx = 0;
              if(i < results.length)
              {
                  resultIdx = i;
              }
              SignalInfo inputInfo = results[resultIdx].get(0).info;
              // Assume same type as the input.
              dummySi.dataType = inputInfo.dataType;
              dummySi.format = inputInfo.format;
              dummySi.access = inputInfo.access;
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
    int nbTable=0;
    int colIdx=1;
    dvIdx=0;
    int nbData=0;
    int nbError=0;
    
    // Number of attribute in table
    for(AttributeInfo ai:selection) {      
      if(ai.table) nbTable++;
      if(ai.isRW()) nbTable++;
      if(ai.isExpanded()) {
        for(ArrayAttributeInfo aai:ai.arrAttInfos) {
          if(aai.table) nbTable++;
          if(ai.isRW()) nbTable++;
        }
      }
    }

    if(nbTable>0) {
      colNames = new String[1+nbTable];
      colNames[0] = "Time";
    } else {
      colNames = new String[0];
    }
    
    imagePanel.updateImageSelection = true;
    
    boolean hasChart = false;
    boolean hasTable = false;
    boolean hasImage = false;
     
    // Browse results
    for (int i = 0; i < results.length; i++) {

      AttributeInfo ai = selection.get(i);
      boolean isRW = ai.isRW();
      ai.dataSize = 0;
      ai.errorSize = 0;

      // Chart data
      if (ai.isNumeric() && !ai.isArray()) {
        
        if(ai.selection != AttributeInfo.SEL_NONE ||
           ai.wselection != AttributeInfo.SEL_NONE )
          hasChart = true;

        // Numeric scalar data
        // Create the chart dataview
        Color c = defaultColor[dvIdx%defaultColor.length];
          
        if(ai.chartData==null) {
          ai.chartData = new JLDataView();
          ai.chartData.setColor(c);
          ai.chartData.setUnit(ai.unit);
        }
        
        if(ai.dvSettings!=null) {
          CfFileReader cfr = new CfFileReader();
          cfr.parseText(ai.dvSettings);
          ai.chartData.applyConfiguration("dv", cfr);
        }
        
        ai.chartData.setName(ai.getName());
        
        if(ai.errorData==null) {
          ai.errorData = new JLDataView();
          ai.errorData.setLineWidth(0);
          ai.errorData.setMarker(JLDataView.MARKER_DOT);
          ai.errorData.setLabelVisible(false);
          ai.errorData.setMarkerColor(c.darker());
        }
        ai.errorData.setName(ai.getName()+"_error");
        ai.errors = new ArrayList<HdbData>();
        
        dvIdx++;
        if(isRW) {
          if(ai.wchartData==null) {
            ai.wchartData = new JLDataView();
            ai.wchartData.setColor(defaultColor[dvIdx%defaultColor.length]);
            ai.wchartData.setUnit(ai.unit);
          }
          if(ai.wdvSettings!=null) {
            CfFileReader cfr = new CfFileReader();
            cfr.parseText(ai.wdvSettings);
            ai.wchartData.applyConfiguration("dv", cfr);
          }
          ai.wchartData.setName(ai.getName()+"_w");          
          dvIdx++;
        }
        
        double lastValueForError = 0.0;
        double lastValue = Double.NaN;
        double lastWriteValue = Double.NaN;
        
        for (int j = 0; j < results[i].size(); j++) {

          HdbData d = results[i].get(j);
          double chartTime = (double)d.getDataTime() / 1000.0;
          
          try {
            
            lastValueForError = d.getValueAsDouble();
            
            if(ai.step) {
              
              if(!Double.isNaN(lastValue))
                ai.chartData.add(chartTime,lastValue);
              ai.chartData.add(chartTime,d.getValueAsDouble());
              lastValue = d.getValueAsDouble();
              
              if(isRW) {
                if(!Double.isNaN(lastWriteValue))
                  ai.wchartData.add(chartTime,lastWriteValue);            
                ai.wchartData.add(chartTime,d.getWriteValueAsDouble());            
                lastWriteValue = d.getWriteValueAsDouble();
              }
              
            } else {
              
              ai.chartData.add(chartTime,d.getValueAsDouble());
              if(isRW)
                ai.wchartData.add(chartTime,d.getWriteValueAsDouble());  
              
            }
            
          } catch( HdbFailed e ) {
            
            ai.chartData.add(chartTime,Double.NaN);
            if(isRW)
              ai.wchartData.add(chartTime,Double.NaN);              
            ai.errorData.add(chartTime,lastValueForError);
            ai.errors.add(d);
            lastValue = Double.NaN;
            lastWriteValue = Double.NaN;
            
          }
          
        }

      }
      
      // Table data
      if(ai.table) {

        hasTable = true;
        
        String unitPreffix = "";
        if(ai.unit.length()>0)
          unitPreffix = " (" + ai.unit + ")";
          colNames[colIdx] = ai.getName() + unitPreffix;
        if(isRW) colNames[colIdx+1] = ai.getName() + "_w" + unitPreffix;
            
        for (int j = 0; j < results[i].size(); j++) {
          HdbData d = results[i].get(j);       
        
          if(d.hasFailed()) {
            
            String err = "/Err"+d.getErrorMessage();
            tablePanel.table.add(err,1,d.getDataTime(),colIdx);
            if(isRW) tablePanel.table.add(err,1,d.getDataTime(),colIdx+1);  
            
          } else if (d.isInvalid()) {
            
            String err = "/ErrATTR_INVALID";
            tablePanel.table.add(err,1,d.getDataTime(),colIdx);
            if(isRW) tablePanel.table.add(err,1,d.getDataTime(),colIdx+1);
            
          } else {
            
            if(ai.isState()) {
              
              tablePanel.table.add("/State"+d.getValueAsString(),
                                   d.getQualityFactor(),
                                   d.getDataTime(),colIdx);            
              if(isRW)
                tablePanel.table.add("/State"+d.getWriteValueAsString(),
                                     d.getQualityFactor(),
                                     d.getDataTime(),colIdx+1);
              
            } else {       
              
              tablePanel.table.add(d.getValueAsString(),
                                   d.getQualityFactor(),
                                   d.getDataTime(),colIdx);
                
              if(isRW)
                tablePanel.table.add(d.getWriteValueAsString(),
                                     d.getQualityFactor(),
                                     d.getDataTime(),colIdx+1);            
              
            }
          }
        }
        colIdx++;
        if(isRW) colIdx++;
        
      }
      
      // Expanded attribute in table
      if (ai.isExpanded()) {

        for (ArrayAttributeInfo aai : ai.arrAttInfos) {

          if (aai.table) {

            hasTable = true;

            int idx = aai.idx;
            String unitPreffix = "";
            if(ai.unit.length()>0)
              unitPreffix =  " (" + ai.unit + ")";
            colNames[colIdx] = ai.getName() + "[" + aai.idx + "]" + unitPreffix;
            if(isRW) colNames[colIdx+1] = ai.getName() + "_w[" + aai.idx + "]" + unitPreffix;

            for (int j = 0; j < results[i].size(); j++) {

              HdbData d = results[i].get(j);
              if (d.hasFailed()) {                
                String err = "/Err" + d.getErrorMessage();
                tablePanel.table.add(err, 1, d.getDataTime(), colIdx);
                if(isRW) tablePanel.table.add(err,1, d.getDataTime(), colIdx+1);
              } else if (d.isInvalid()) {                
                String err = "/ErrATTR_INVALID";
                tablePanel.table.add(err, 1, d.getDataTime(), colIdx);
                if(isRW) tablePanel.table.add(err,1, d.getDataTime(), colIdx+1);
              } else if (idx >= d.size()) {
                String err = "/Err" + "Index out of bounds";
                tablePanel.table.add(err, 1, d.getDataTime(), colIdx);
                if(isRW) tablePanel.table.add(err,1, d.getDataTime(), colIdx+1);
              } else {

                try {
                  
                  String value;
                  String valueW="";

                  if (ai.isState()) {

                    HdbStateArray hdbV = (HdbStateArray) d;
                    value = "/State" + HdbState.getStateString(hdbV.getValue()[idx]);
                    if (isRW && idx < d.sizeW()) {
                      valueW = "/State" + HdbState.getStateString(hdbV.getWriteValue()[idx]);
                    }

                  } else {

                    // Do not use convenience function for performance
                    // Not all type implemented
                    // Default to convenience function
                    if (d instanceof HdbFloatArray) {

                      HdbFloatArray hdbV = (HdbFloatArray) d;
                      value = Float.toString(hdbV.getValue()[idx]);
                      if (isRW && idx < d.sizeW()) {
                        valueW = Float.toString(hdbV.getWriteValue()[idx]);
                      }

                    } else if (d instanceof HdbLongArray) {

                      HdbLongArray hdbV = (HdbLongArray) d;
                      value = Integer.toString(hdbV.getValue()[idx]);
                      if (isRW && idx < d.sizeW()) {
                        valueW = Float.toString(hdbV.getWriteValue()[idx]);
                      }

                    } else if (d instanceof HdbLong64Array) {

                      HdbLong64Array hdbV = (HdbLong64Array) d;
                      value = Long.toString(hdbV.getValue()[idx]);
                      if (isRW && idx < d.sizeW()) {
                        valueW = Float.toString(hdbV.getWriteValue()[idx]);
                      }

                    } else if (d instanceof HdbShortArray) {

                      HdbShortArray hdbV = (HdbShortArray) d;
                      value = Short.toString(hdbV.getValue()[idx]);
                      if (isRW && idx < d.sizeW()) {
                        valueW = Float.toString(hdbV.getWriteValue()[idx]);
                      }

                    } else {

                      value = Double.toString(d.getValueAsDoubleArray()[idx]);
                      if (isRW && idx < d.sizeW()) {
                        valueW = Double.toString(d.getWriteValueAsDoubleArray()[idx]);
                      }

                    }

                  }
                  
                  tablePanel.table.add(value,d.getQualityFactor(),d.getDataTime(), colIdx);
                  if(isRW) 
                    tablePanel.table.add(valueW,d.getQualityFactor(),d.getDataTime(), colIdx+1);

                } catch (HdbFailed e) {
                  // should not happen !!!
                }

              }
            }
            colIdx++;
            if(isRW) colIdx++;
          }

        }
      
      }
       
      
      // Image data
      if (ai.isNumeric() && ai.isArray()) {
      
        hasImage = hasImage || (ai.selection == AttributeInfo.SEL_IMAGE);
        ai.arrayData = results[i];
        ai.maxArraySize = -1;
        for(int k=0;k<results[i].size();k++) {
          if(results[i].get(k).size() > ai.maxArraySize) {
            ai.maxArraySize = results[i].get(k).size();
          }
        }
      
      }
      
      // Errors
      for (int j = 0; j < results[i].size(); j++) {
        HdbData d = results[i].get(j);
        if(d.hasFailed()) {
          errorDialog.addError(ai.getName(), d);
          ai.errorSize++;
          nbError++;
        } else {
          ai.dataSize++;
          nbData++;
        }
      }
      
    }
    
    infoDialog.addText("Total data="+nbData);
    infoDialog.addText("Total error="+nbError);

    // Select chart and image attribute
    for(AttributeInfo ai:selection) {
      
      // Select normal attribute
      selectAttribute(ai,-1,ai.selection,false);
      if(ai.isRW())
        selectAttribute(ai,-1,ai.wselection,true);
      
      // Select expanded attribute
      if(ai.isExpanded()) {
        for(int i=0;i<ai.arrAttInfos.size();i++) {
          ArrayAttributeInfo aai = ai.arrAttInfos.get(i);
          selectAttribute(ai,i,aai.selection,false);
          if(ai.isRW())
            selectAttribute(ai,i,aai.wselection,true);
        }
      }
      
    }

    // Build  Table
    tablePanel.table.setColumnName(colNames);
    tablePanel.table.dataChanged();
    errorDialog.commit();
    
    // Update XAxis selection
    chartPanel.resetXItem();
    for(AttributeInfo ai:selection) {
      
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
      
      // Select normal attribute
      selectAttribute(ai,-1,ai.selection,false);
      if(ai.isRW())
        selectAttribute(ai,-1,ai.wselection,true);
      
      // Select expanded attribute
      if(ai.isExpanded()) {
        for(int i=0;i<ai.arrAttInfos.size();i++) {
          ArrayAttributeInfo aai = ai.arrAttInfos.get(i);
          selectAttribute(ai,i,aai.selection,false);
          if(ai.isRW())
            selectAttribute(ai,i,aai.wselection,true);
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

  public String[] clickOnChart(JLChartEvent jlce) {
    
    JLDataView srcDataView = jlce.getDataView();
    
    boolean foundE = false;
    boolean foundR = false;
    boolean foundW = false;
    
    int i=0;
    while(!foundE && !foundR && !foundW && i<selection.size()) {
      foundE = selection.get(i).errorData == srcDataView;
      foundR = selection.get(i).chartData == srcDataView;
      foundW = selection.get(i).wchartData == srcDataView;
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

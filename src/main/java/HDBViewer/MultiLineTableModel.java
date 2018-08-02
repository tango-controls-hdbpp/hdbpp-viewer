/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;

/**
 * Table model for HDB
 */

class CellItem {
  int column;
  int    quality;
  String value;
}

class RowItem {  
  
  long   time;
  ArrayList<CellItem> value;
  int nbError = 0;
  
  public String qualityToStr(int quality) {

    switch(quality) {
      case 0:
        return "VALID";
      case 1:
        return "INVALID";
      case 2:
        return "ALARM";
      case 3:
        return "CHANGING";
      case 4:
        return "WARNING";
      default:
        return "UNKNOWN QUALITY";
    }

  }

  String getValueAt(int column,boolean addQuality) {
    
    boolean found=false;
    int i=0;
    while(!found && i<value.size()) {
      found = value.get(i).column == column;
      if(!found) i++;
    }
    if(found) {
      String ret = value.get(i).value;
      if(addQuality)
        ret += "\n" + qualityToStr(value.get(i).quality);
      return ret;
    } else {
      return "";
    }
    
  }
  
  boolean hasOnlyError() {
    return nbError == value.size();
  }
    
}

public class MultiLineTableModel extends AbstractTableModel {
  
  public final static int DATE_TIME_FORMAT = 1;
  public final static int EPOCH_FORMAT = 2;
  
  private ArrayList<RowItem> data;
  private int[] errorIndex;
  private String[] colNames;
  private boolean doMicroSec = false;
  private boolean showError = true;
  private boolean showQuality = false;
  
  public MultiLineTableModel() {
    data = new ArrayList<RowItem>();
    colNames = new String[0];
    errorIndex = new int[0];
  }
  
  public void setDoMicroSec(boolean doMicro) {
    doMicroSec = doMicro;
    fireTableDataChanged();
  }
  
  public boolean isDoingMicroSec() {
    return doMicroSec;
  }
  
  public void setShowError(boolean show) {
    showError = show;
    fireTableDataChanged();
  }
  
  public boolean isShowingError() {
    return showError;
  }
  
  public void setShowQuality(boolean show) {
    showQuality = show;
    fireTableDataChanged();
  }
  
  public boolean isShowingQuality() {
    return showQuality;
  }
  
  public void setColumnNames(String[] names) {
    colNames = names;
    fireTableStructureChanged();
  }
  
  public void reset() {
    data = new ArrayList<RowItem>();
    colNames = new String[0];    
    errorIndex = new int[0];
 }
  
  private RowItem binarySearch(long time) {
    
    int low = 0;
    int high = data.size()-1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      long midVal = data.get(mid).time;

       if (midVal < time)
         low = mid + 1;
       else if (midVal > time)
         high = mid - 1;
       else
         return data.get(mid); // item found
    }
    
    // r is the insertion position
    int r = -(low + 1);
    if(r<0) r=  -(r+1);
    
    RowItem newItem = new RowItem();
    newItem.time = time;
    newItem.value = new ArrayList<CellItem>();
    data.add(r,newItem);
    return newItem;   
            
  }
  
  public void add(String value,int quality,long time,int colIdx) {
    
    RowItem n = binarySearch(time);
    CellItem c = new CellItem();
    c.column = colIdx;
    c.value = value;
    c.quality = quality;
    n.value.add(c);
    if(value.startsWith("/Err"))
      n.nbError++;
                
  }
  
  public void commit() {
    
    // Build error index
    int nbIdx = 0;
    for(int i=0;i<data.size();i++) {
      if(!data.get(i).hasOnlyError()) nbIdx++;
    }
    errorIndex = new int[nbIdx];
    int nb = 0;
    for(int i=0;i<data.size();i++) {
      if(!data.get(i).hasOnlyError()) {
        errorIndex[nb] = i;
        nb++;
      }
    }
        
  }

  public int getRowCount() {
    if(showError)
      return data.size();
    else
      return errorIndex.length;
  }

  public int getColumnCount() {
    return colNames.length;
  }

  public String getColumnName(int columnIndex) {
    return colNames[columnIndex];    
  }

  public Class<?> getColumnClass(int columnIndex) {
    return String.class;
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return false;
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    
    int row;
    if(showError) 
      row = rowIndex;
    else
      row = errorIndex[rowIndex];
    
    RowItem i = data.get(row);
    if(columnIndex==0) {
      // Timestamp
      return Utils.formatTime(i.time,(doMicroSec?Utils.FORMAT_US:Utils.FORMAT_SEC));
    } else {
      String r = i.getValueAt(columnIndex,showQuality);
      if(showError) {
        return r;
      } else {
        if(r.startsWith("/Err"))
          return "";
        else
          return r;
      }
    }
    
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
  }
  
  private void appendRow(StringBuffer f,int r,int timeFormat) {

    int cCount = getColumnCount();

    if( timeFormat==DATE_TIME_FORMAT ) {
    String[] ds = Utils.formatDateAndTime(data.get(r).time);
      f.append(ds[0]).append("\t");
      f.append(ds[1]).append("\t");
    } else {
      f.append(Double.toString(data.get(r).time/1e6) + "\t");
    }
    for(int c=1;c<cCount;c++) {
      String v = (String)getValueAt(r,c);
      f.append(v);
      if(c<cCount-1)
       f.append("\t");
    }
    f.append("\n");

  }
  
  public String buildTabbedString(int[] rows,int timeFormat,boolean showHeader) {
    
    StringBuffer f = new StringBuffer();
    
    int cCount = getColumnCount();

    if(showHeader) {
      
      if (timeFormat == DATE_TIME_FORMAT) {
        f.append("HDB Date\tHDB Time\t");
      } else {
        f.append("HDB Time\t");
      }

      for (int i = 1; i < cCount; i++) {
        f.append(colNames[i]).append("\t");
      }
      f.append("\n");
      
    }
    
    if(rows==null) {
      // Whole table
      for(int r=0;r<getRowCount();r++)
        appendRow(f,r,timeFormat);      
    } else {
      // Selected rows
      for(int r=0;r<rows.length;r++)
        appendRow(f,rows[r],timeFormat);
    }
    
    return f.toString();
    
  }
  
  public void saveFile(String fileName,int timeFormat,boolean showHeader) throws IOException {
    
    FileWriter f = new FileWriter(fileName);
    if(showHeader) {
      f.write("# File generated from hdbviewer application\n");
      f.write("#\n");
    }
    f.write(buildTabbedString(null,timeFormat,showHeader));       
    f.close();
    
  }
  
}

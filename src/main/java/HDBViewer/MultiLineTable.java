package HDBViewer;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import javax.swing.*;

/* A specific table for HDB */
public class MultiLineTable extends JTable {

  final private MultiLineTableModel tableModel;
  final private MultiLineCellRenderer renderer;

  public MultiLineTable() {

    renderer = new MultiLineCellRenderer();
    setDefaultRenderer(String.class, renderer);
    
    tableModel = new MultiLineTableModel();
    this.setModel(tableModel);    

  }
  
  public void reset() {
    tableModel.reset();
  }
  
  public void setColumnName(String[] colNames) {
    tableModel.setColumnNames(colNames);
  }

  public void add(String value,int quality,long time,int colIdx) {
    tableModel.add(value, quality, time, colIdx);
  }
  
  public void add(String value,int quality,long time,Collection<Integer> colIdxes) {
    for(int colIdx : colIdxes)
    {
      tableModel.add(value, quality, time, colIdx);
    }
  }
  
  public void doMicroSec(boolean doMicro) {
    tableModel.setDoMicroSec(doMicro);
    updateSize();
  }
  
  public void showError(boolean show) {
    tableModel.setShowError(show);
    updateSize();
  }
  
  public void showQuality(boolean show) {
    tableModel.setShowQuality(show);
    updateSize();
  }
  
  public void saveFile(String fileName,int timeFormat,boolean showHeader) throws IOException {
    tableModel.saveFile(fileName,timeFormat,showHeader);
  }
  
  public String getSelectionAsString() {
    
    return tableModel.buildTabbedString(getSelectedRows(),MultiLineTableModel.DATE_TIME_FORMAT,true);
    
  }
  
  public void dataChanged() {
    tableModel.commit();
    updateSize();
  }
  
  private void updateSize() {
    updateRows();
    if(tableModel.getColumnCount()>0) {
      if(tableModel.isDoingMicroSec()) {
        getColumnModel().getColumn(0).setMaxWidth(200);
        getColumnModel().getColumn(0).setPreferredWidth(200);
      } else {
        getColumnModel().getColumn(0).setMaxWidth(150);
        getColumnModel().getColumn(0).setPreferredWidth(150);
      }
    }
    validate();    
  }

  /*
  protected void processEvent(AWTEvent e) {

    if( e instanceof MouseEvent ) {
      MouseEvent me = (MouseEvent)e;
      if(me.getButton()==1 && me.getID()==MouseEvent.MOUSE_PRESSED) {
        int column = getColumnForLocation(me.getX());
        if( column==1 ) {
          int row = getRowForLocation(me.getY());
          String value = (String)getModel().getValueAt(row,column);
        }
      }
    }
    super.processEvent(e);
  }
  */
  
  private int cellHeight(int row, int col) {
    return renderer.getHeight((String)tableModel.getValueAt(row, col)) + 1;
  }

  void updateRow(int row) {
    int maxHeight = 0;
    for (int j = 0; j < getColumnCount(); j++) {
      int ch;
      if ((ch = cellHeight(row, j)) > maxHeight) {
        maxHeight = ch;
      }
    }
    setRowHeight(row, maxHeight);
  }

  public void updateRows() {
    for (int i = 0; i < getRowCount(); i++) updateRow(i);
  }


  public int getRowForLocation(int y) {

    boolean found = false;
    int i = 0;
    int h = 0;

    while(i<getModel().getRowCount() && !found) {
      found = (y>=h && y<=h+getRowHeight(i));
      if(!found) {
        h+=getRowHeight(i);
        i++;
      }
    }

    if(found) {
      return i;
    } else {
      return -1;
    }

  }

  public int getColumnForLocation(int x) {

    boolean found = false;
    int i = 0;
    int w = 0;

    while(i<getModel().getColumnCount() && !found) {
      int cWidth = getColumnModel().getColumn(i).getWidth();
      found = (x>=w && x<=w+cWidth);
      if(!found) {
        w+=cWidth;
        i++;
      }
    }

    if(found) {
      return i;
    } else {
      return -1;
    }

  }

}

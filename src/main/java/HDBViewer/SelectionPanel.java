/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer;

import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.ApiUtil;
import fr.esrf.TangoApi.Database;
import fr.esrf.TangoApi.DbDatum;
import fr.esrf.tangoatk.widget.util.chart.CfFileReader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import org.tango.jhdb.HdbFailed;
import org.tango.jhdb.HdbSigParam;
import org.tango.jhdb.SignalInfo;
import org.tango.jhdb.data.HdbData;

/**
 *
 * @author pons
 */



public class SelectionPanel extends javax.swing.JPanel {

  final static String colNames[] = {
    "Tango Host",
    "Attribute",
    "Type",
    "Interval",
    "Agg.",
    "Records",
    "Table",
    "Step",
    "Sel. Y1",
    "Sel. Y2",
    "Sel. Img"};

  private final int HOST_IDX = 0;
  private final int ATTRIBUTE_IDX = 1;
  private final int TYPE_IDX = 2;
  private final int INTERVAL_IDX = 3;
  private final int AGGREGATE_IDX = 4;
  private final int RECORDS_IDX = 5;
  private final int TABLE_IDX = 6;
  private final int STEP_IDX = 7;
  private final int Y1_IDX = 8;
  private final int Y2_IDX = 9;
  private final int IMG_IDX = 10;

  private static class SelRowItem {
    SelRowItem(int idx,boolean write,int arrayItem) {
      attIdx = idx;
      isWrite = write;
      this.arrayItem = arrayItem;
      this.aggregate = null;      
    }
    SelRowItem(int idx,boolean write,int arrayItem,HdbData.Aggregate aggregate) {
      attIdx = idx;
      isWrite = write;
      this.arrayItem = arrayItem;
      this.aggregate = aggregate;
    }
    int attIdx;
    int arrayItem;
    boolean isWrite;
    HdbData.Aggregate aggregate;
    
  }

  private final static Color backColor = new Color(240,240,255);
  class BooleanCellRenderer implements TableCellRenderer {

    private final JCheckBox checkBox;
    private final JButton button;
    private final JLabel  empty;

    BooleanCellRenderer() {
      checkBox = new JCheckBox();
      empty = new JLabel("");
      checkBox.setHorizontalAlignment(JLabel.CENTER);
      button = new JButton("Expand");
      button.setMargin(new Insets(2,2,2,2));
      button.setFont(new Font("Dialog",Font.PLAIN,12));
    }

    private SelectionPanel getParent() {
      return SelectionPanel.this;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table,Object value,
         boolean isSelected, boolean hasFocus, int row, int column) {

      Boolean b = (Boolean)value;
      SelectionPanel p = getParent();

      if(!p.isEditable(row,column)) {
        return empty;
      }

      int attIdx = p.rowToIdx[row].attIdx;
      int item = p.rowToIdx[row].arrayItem;
      AttributeInfo ai = p.parent.selection.get(attIdx);

      if(column == Y1_IDX || column == Y2_IDX) {

       // Draw a button for array attribute in the Y1 and Y2
        if(ai.isArray() && item==-1) {
          if(ai.isNumeric()) {
            button.setText("Expand");
            return button;
          }
        }

      }

      checkBox.setSelected(b);
      if(column >= Y1_IDX)
        checkBox.setBackground(backColor);
      else
        checkBox.setBackground(Color.WHITE);

      return checkBox;
    }
  }

  private class SelectionTableModel extends DefaultTableModel
  {

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      switch(columnIndex)
      {
        case HOST_IDX:
        case ATTRIBUTE_IDX:
        case TYPE_IDX:
          return String.class;
        case AGGREGATE_IDX:
          return HdbData.Aggregate.class;
        case INTERVAL_IDX:
          return SignalInfo.Interval.class;
        case RECORDS_IDX:
          return String.class;
        case TABLE_IDX:
        case STEP_IDX:
        case Y1_IDX:
        case Y2_IDX:
        case IMG_IDX:
          return Boolean.class;
        default:
          return String.class;
      }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return isEditable(row,column);
    }

    @Override
    public void setValueAt(Object aValue, int row, int column) {

      int selMode = AttributeInfo.SEL_NONE;
      int attIdx = rowToIdx[row].attIdx;
      boolean isW = rowToIdx[row].isWrite;
      int item = rowToIdx[row].arrayItem;
      HdbData.Aggregate agg = rowToIdx[row].aggregate;
      AttributeInfo ai = parent.selection.get(attIdx);
      
      boolean b;
      switch (column) {
        case TABLE_IDX: // Table
          b = ((Boolean) aValue).booleanValue();
          if(ai.isRAW()) {
            if(item<0)
              ai.table = b;
            else
              ai.arrAttInfos.get(item).table = true;
          } else {
            ai.getAggregate(agg).table = b;
          }
          updateSelectionList();
          return;
        case STEP_IDX: // Step
          b = ((Boolean) aValue).booleanValue();
          if (ai.isRAW()) {
            if (item < 0) {
              ai.step = b;
            } else {
              ai.arrAttInfos.get(item).step = true;
            }
          } else {
            ai.getAggregate(agg).step = b;
          }
          updateSelectionList();
          return;
        case Y1_IDX: // Y1
          b = ((Boolean) aValue).booleanValue();
          if(b) selMode = AttributeInfo.SEL_Y1;
          break;
        case Y2_IDX: // Y2
          b = ((Boolean) aValue).booleanValue();
          if(b) selMode = AttributeInfo.SEL_Y2;
          break;
        case IMG_IDX: // Image
          b = ((Boolean) aValue).booleanValue();
          if(b) selMode = AttributeInfo.SEL_IMAGE;
          break;
      }

      // Array item to be expanded
      if (item<0 &&
          ai.isArray() &&
         (selMode == AttributeInfo.SEL_Y1 ||
          selMode == AttributeInfo.SEL_Y2)) {

        ArrayList<Integer> expandedIDs = IDSelectionDlg.getIds(ai.maxArraySize);
        if (expandedIDs == null) {
          // Canceling
          updateSelectionList();
          return;
        } else {
          // Unselect old expanded (if any)
          if(ai.isExpanded()) {
            for(int i=0;i<ai.arrAttInfos.size();i++)
              parent.unselectAttribute(ai, i, null);
          }
          ai.expand(expandedIDs);
        }

        updateSelectionList();
        return;

      }

      if( parent.selectAttribute(ai, item, agg, selMode, isW) ) {

        // Unselect other image (if any)
        for(AttributeInfo _ai : parent.selection) {
          if(_ai!=ai) {
            if(_ai.selection==AttributeInfo.SEL_IMAGE)
              _ai.selection = AttributeInfo.SEL_NONE;
            if(_ai.wselection==AttributeInfo.SEL_IMAGE)
            _ai.wselection = AttributeInfo.SEL_NONE;
          } else {
            if(isW) {
              if(_ai.selection==AttributeInfo.SEL_IMAGE)
                ai.selection = AttributeInfo.SEL_NONE;
            } else {
              if(_ai.wselection==AttributeInfo.SEL_IMAGE)
                ai.wselection = AttributeInfo.SEL_NONE;
            }
          }
        }

      }

      updateSelectionList();

    }
  }

  final MainPanel parent;

  SelectionTableModel selModel;
  JTable selTable;
  SelRowItem[] rowToIdx;
  String defaultScriptPath=".";
  String defaultSelFilePath=".";

  /**
   * Creates new form SelectionPanel
   */
  SelectionPanel(MainPanel p) {

    this.parent = p;
    initComponents();

    // Create table
    selModel = new SelectionTableModel();

    selTable = new JTable(selModel);
    selTable.setDefaultRenderer(Boolean.class, new BooleanCellRenderer());
    JScrollPane selView = new JScrollPane(selTable);
    selView.setPreferredSize(new Dimension(1200,100));
    listPanel.add(selView, BorderLayout.CENTER);

    updateSelectionList();

    // Got default path from database
    try {

      Database db = ApiUtil.get_db_obj();

      DbDatum ds = db.get_property("HDBViewer","scriptPath");
      if(!ds.is_empty()) defaultScriptPath = ds.extractString();

      DbDatum df = db.get_property("HDBViewer","selFilePath");
      if(!df.is_empty()) defaultSelFilePath = df.extractString();

    } catch(DevFailed e) {

    }

  }

  boolean isEditable(int row,int column) {
    if(column == INTERVAL_IDX)
      return true;

    int attIdx = rowToIdx[row].attIdx;

    if(column == AGGREGATE_IDX)
      return false;

    if(column<TABLE_IDX)
      return false;

    int item = rowToIdx[row].arrayItem;
    AttributeInfo ai = parent.selection.get(attIdx);

    if(column==TABLE_IDX) {
      // Table
      return true;
    }

    if(column==STEP_IDX) {
      // Step
      return (item>=0) ||
             (ai.isNumeric() && !ai.isArray());
    }

    if(column==Y1_IDX || column==Y2_IDX) {
      // Y1, Y2
      return ai.isNumeric();
    }

    if(column==IMG_IDX) {
      // Image
      return item==-1 && ai.isNumeric() && ai.isArray();
    }

    return false;

  }

  String getPyScript() {
    return scriptText.getText();
  }

  void setPyScript(String name) {
    scriptText.setText(name);
  }

  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    listPanel = new javax.swing.JPanel();
    btnPanel = new javax.swing.JPanel();
    innerScriptPanel = new javax.swing.JPanel();
    scriptText = new javax.swing.JTextField();
    scriptButton = new javax.swing.JButton();
    innerSelectionPanel = new javax.swing.JPanel();
    loadButton = new javax.swing.JButton();
    saveButton = new javax.swing.JButton();
    removeButton = new javax.swing.JButton();
    removeAllButton = new javax.swing.JButton();
    allY1Button = new javax.swing.JButton();
    allY2Button = new javax.swing.JButton();

    setLayout(new java.awt.BorderLayout());

    listPanel.setLayout(new java.awt.BorderLayout());
    add(listPanel, java.awt.BorderLayout.CENTER);

    btnPanel.setLayout(new java.awt.GridBagLayout());

    innerScriptPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Python script", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 0, 10))); // NOI18N
    innerScriptPanel.setLayout(new java.awt.GridBagLayout());

    scriptText.setToolTipText("");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    innerScriptPanel.add(scriptText, gridBagConstraints);

    scriptButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    scriptButton.setText("...");
    scriptButton.setToolTipText("Load a python script");
    scriptButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
    scriptButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        scriptButtonActionPerformed(evt);
      }
    });
    innerScriptPanel.add(scriptButton, new java.awt.GridBagConstraints());

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    btnPanel.add(innerScriptPanel, gridBagConstraints);

    innerSelectionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Selection", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 0, 10))); // NOI18N
    innerSelectionPanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 2, 1));

    loadButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    loadButton.setText("Load");
    loadButton.setToolTipText("Load a selection file");
    loadButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
    loadButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        loadButtonActionPerformed(evt);
      }
    });
    innerSelectionPanel.add(loadButton);

    saveButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    saveButton.setText("Save");
    saveButton.setToolTipText("Save current selection to file");
    saveButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
    saveButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        saveButtonActionPerformed(evt);
      }
    });
    innerSelectionPanel.add(saveButton);

    removeButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    removeButton.setText("Remove");
    removeButton.setToolTipText("Remove selected attribute from selection list");
    removeButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
    removeButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        removeButtonActionPerformed(evt);
      }
    });
    innerSelectionPanel.add(removeButton);

    removeAllButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    removeAllButton.setText("Clear All");
    removeAllButton.setToolTipText("Clear the whole selection");
    removeAllButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
    removeAllButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        removeAllButtonActionPerformed(evt);
      }
    });
    innerSelectionPanel.add(removeAllButton);

    allY1Button.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    allY1Button.setText("All Y1");
    allY1Button.setMargin(new java.awt.Insets(2, 5, 2, 5));
    allY1Button.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        allY1ButtonActionPerformed(evt);
      }
    });
    innerSelectionPanel.add(allY1Button);

    allY2Button.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    allY2Button.setText("All Y2");
    allY2Button.setMargin(new java.awt.Insets(2, 5, 2, 5));
    allY2Button.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        allY2ButtonActionPerformed(evt);
      }
    });
    innerSelectionPanel.add(allY2Button);

    btnPanel.add(innerSelectionPanel, new java.awt.GridBagConstraints());

    add(btnPanel, java.awt.BorderLayout.SOUTH);
  }// </editor-fold>//GEN-END:initComponents

  void updateSelectionList() {

    int nbAtt = 0;
    for (int i = 0; i < parent.selection.size(); i++) {

      AttributeInfo ai = parent.selection.get(i);

      if(ai.isExpanded()) {

        // Expanded array attribute
        if(ai.isRW())
          nbAtt += 2*(1+ai.arrAttInfos.size());
        else
          nbAtt += 1+ai.arrAttInfos.size();

      } else {

        if(ai.isAggregate()) {
          nbAtt+=ai.getNbAggregate();
        } else {
          if(ai.isRW())
            nbAtt+=2;
          else
            nbAtt+=1;
        }

      }

    }

    int j=0;
    Object[][] objs = new Object[nbAtt][colNames.length];
    rowToIdx = new SelRowItem[nbAtt];

    for (int i = 0; i < parent.selection.size(); i++) {

      AttributeInfo ai = parent.selection.get(i);

      if (ai.isAggregate()) {

        for (HdbData.Aggregate agg : ai.getAggregates()) {

          objs[j][HOST_IDX] = ai.host;
          objs[j][ATTRIBUTE_IDX] = ai.getName();
          objs[j][TYPE_IDX] = ai.getType();
          objs[j][INTERVAL_IDX] = ai.interval;
          objs[j][AGGREGATE_IDX] = agg;
          if(agg.compareTo(HdbData.Aggregate.COUNT_W)>=0 && !ai.isRW()) {
            objs[j][RECORDS_IDX] = "0";
          } else {
            objs[j][RECORDS_IDX] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";          
          }
          objs[j][TABLE_IDX] = ai.getAggregate(agg).table;
          objs[j][STEP_IDX] = ai.getAggregate(agg).step;
          objs[j][Y1_IDX] = (ai.getAggregate(agg).selection == AttributeInfo.SEL_Y1);
          objs[j][Y2_IDX] = (ai.getAggregate(agg).selection == AttributeInfo.SEL_Y2);
          objs[j][IMG_IDX] = (ai.getAggregate(agg).selection == AttributeInfo.SEL_IMAGE);
          rowToIdx[j] = new SelRowItem(i, false, -1, agg);
          j++;

        }

      } else {

        objs[j][HOST_IDX] = ai.host;
        objs[j][ATTRIBUTE_IDX] = ai.getName();
        objs[j][TYPE_IDX] = ai.getType();
        objs[j][INTERVAL_IDX] = ai.interval;
        objs[j][RECORDS_IDX] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
        objs[j][TABLE_IDX] = ai.table;
        objs[j][STEP_IDX] = ai.step;
        objs[j][Y1_IDX] = (ai.selection == AttributeInfo.SEL_Y1);
        objs[j][Y2_IDX] = (ai.selection == AttributeInfo.SEL_Y2);
        objs[j][IMG_IDX] = (ai.selection == AttributeInfo.SEL_IMAGE);
        rowToIdx[j] = new SelRowItem(i, false, -1);
        j++;

        if (ai.isExpanded()) {
          int k = 0;
          for (ArrayAttributeInfo aai : ai.arrAttInfos) {
            objs[j][HOST_IDX] = ai.host;
            objs[j][ATTRIBUTE_IDX] = ai.getName() + "[" + aai.idx + "]";
            objs[j][TYPE_IDX] = "Item #" + aai.idx;
            objs[j][INTERVAL_IDX] = ai.interval;
            objs[j][RECORDS_IDX] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
            objs[j][TABLE_IDX] = aai.table;
            objs[j][STEP_IDX] = aai.step;
            objs[j][Y1_IDX] = (aai.selection == AttributeInfo.SEL_Y1);
            objs[j][Y2_IDX] = (aai.selection == AttributeInfo.SEL_Y2);
            objs[j][IMG_IDX] = (aai.selection == AttributeInfo.SEL_IMAGE);
            rowToIdx[j] = new SelRowItem(i, false, k);
            j++;
            k++;
          }
        }

        if (ai.isRW()) {

          objs[j][HOST_IDX] = ai.host;
          objs[j][ATTRIBUTE_IDX] = ai.getName() + "_w";
          objs[j][TYPE_IDX] = ai.getType();
          objs[j][INTERVAL_IDX] = ai.interval;
          objs[j][RECORDS_IDX] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
          objs[j][TABLE_IDX] = ai.table;
          objs[j][STEP_IDX] = ai.step;
          objs[j][Y1_IDX] = (ai.wselection == AttributeInfo.SEL_Y1);
          objs[j][Y2_IDX] = (ai.wselection == AttributeInfo.SEL_Y2);
          objs[j][IMG_IDX] = (ai.wselection == AttributeInfo.SEL_IMAGE);
          rowToIdx[j] = new SelRowItem(i, true, -1);
          j++;

          if (ai.isExpanded()) {
            int k = 0;
            for (ArrayAttributeInfo aai : ai.arrAttInfos) {
              objs[j][HOST_IDX] = ai.host;
              objs[j][ATTRIBUTE_IDX] = ai.getName() + "_w[" + aai.idx + "]";
              objs[j][TYPE_IDX] = "Write item #" + aai.idx;
              objs[j][INTERVAL_IDX] = ai.interval;
              objs[j][RECORDS_IDX] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
              objs[j][TABLE_IDX] = aai.table;
              objs[j][STEP_IDX] = aai.step;
              objs[j][Y1_IDX] = (aai.wselection == AttributeInfo.SEL_Y1);
              objs[j][Y2_IDX] = (aai.wselection == AttributeInfo.SEL_Y2);
              objs[j][IMG_IDX] = (aai.wselection == AttributeInfo.SEL_IMAGE);
              rowToIdx[j] = new SelRowItem(i, true, k);
              j++;
              k++;
            }
          }

        }

      }

    }
    
    selTable.setRowHeight(20);
    selModel.setDataVector(objs, colNames);
    selTable.getColumnModel().getColumn(ATTRIBUTE_IDX).setPreferredWidth(300);
    selTable.getColumnModel().getColumn(TYPE_IDX).setMinWidth(120);
    selTable.getColumnModel().getColumn(TYPE_IDX).setMaxWidth(120);
    selTable.getColumnModel().getColumn(INTERVAL_IDX).setMinWidth(80);
    selTable.getColumnModel().getColumn(INTERVAL_IDX).setMaxWidth(80);
    selTable.getColumnModel().getColumn(INTERVAL_IDX).setMinWidth(80);
    selTable.getColumnModel().getColumn(AGGREGATE_IDX).setMaxWidth(80);
    selTable.getColumnModel().getColumn(RECORDS_IDX).setMinWidth(100);
    selTable.getColumnModel().getColumn(RECORDS_IDX).setPreferredWidth(100);
    selTable.getColumnModel().getColumn(TABLE_IDX).setMaxWidth(60);
    selTable.getColumnModel().getColumn(STEP_IDX).setMaxWidth(60);
    selTable.getColumnModel().getColumn(Y1_IDX).setMaxWidth(60);
    selTable.getColumnModel().getColumn(Y2_IDX).setMaxWidth(60);
    selTable.getColumnModel().getColumn(IMG_IDX).setMaxWidth(60);

  }

  private void removeFromSel(SelRowItem item,ArrayList<Integer> attToRemove) {
        
    AttributeInfo ai = parent.selection.get(item.attIdx);
    if(ai.isAggregate()) {
      
      parent.unselectAttribute(ai, -1, item.aggregate);
      ai.removeAggregate(item.aggregate);
      if(ai.getNbAggregate()==0)
        if(!attToRemove.contains(item.attIdx))
          attToRemove.add(item.attIdx);
        
    } else {

      if( item.arrayItem==-1 ) {
        // Remove attribute and expandeds
        parent.unselectAttribute(ai, -1, null);
        if(ai.isExpanded()) {
          for(int j=0;j<ai.arrAttInfos.size();j++)
            parent.unselectAttribute(ai,j,null);
        }
        if(!attToRemove.contains(item.attIdx))
          attToRemove.add(item.attIdx);
      } else {
        // remove one of expanded items
        parent.unselectAttribute(ai, item.arrayItem, null);
        ai.unexpand(item.arrayItem);                
      }
      
    }
    
  }

  private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed

    int[] rows = selTable.getSelectedRows();
    ArrayList<Integer> toClear = new ArrayList<Integer>();
    for(int i=0;i<rows.length;i++)
      removeFromSel(rowToIdx[rows[i]],toClear);    
    
    // Empty unused attribute
    Collections.sort(toClear);    
    for(int i=toClear.size()-1;i>=0;i--)
      parent.selection.remove(toClear.get(i).intValue());
    
    updateSelectionList();

  }//GEN-LAST:event_removeButtonActionPerformed

  void loadFile(String fileName) {

       try {

        // Load the config file
        ConfigFileReader cf = new ConfigFileReader(new FileReader(fileName));
        ArrayList<AttributeInfo> list = cf.parseFile();

        // Update type info
        for(int i=0;i<list.size();i++) {
          SignalInfo si = parent.hdb.getReader().getSigInfo(list.get(i).getFullName(), HdbSigParam.QUERY_DATA);
          list.get(i).sigInfo = si;
        }

        // Update list
        parent.reset();
        parent.selection = list;
        updateSelectionList();

        // Update global
        parent.chartPanel.setShowError(cf.showError);
        if(cf.chartSettings!=null) {
          CfFileReader cfr = new CfFileReader();
          cfr.parseText(cf.chartSettings);
          parent.chartPanel.chart.applyConfiguration(cfr);
        }
        if(cf.xSettings!=null) {
          CfFileReader cfr = new CfFileReader();
          cfr.parseText(cf.xSettings);
          parent.chartPanel.chart.getXAxis().applyConfiguration("x",cfr);
        }
        if(cf.y1Settings!=null) {
          CfFileReader cfr = new CfFileReader();
          cfr.parseText(cf.y1Settings);
          parent.chartPanel.chart.getY1Axis().applyConfiguration("y1",cfr);
        }
        if(cf.y2Settings!=null) {
          CfFileReader cfr = new CfFileReader();
          cfr.parseText(cf.y2Settings);
          parent.chartPanel.chart.getY2Axis().applyConfiguration("y2",cfr);
        }
        parent.hdbTreePanel.setTimeInterval(cf.timeInterval);
        parent.hdbTreePanel.hdbModeCombo.setSelectedIndex(cf.hdbMode);
        scriptText.setText(cf.scriptName);

      } catch(IOException e) {
        Utils.showError("Cannot load file\n"+e.getMessage());
      } catch(HdbFailed e2) {
        Utils.showError("Cannot load file\n"+e2.getMessage());
      }

  }

  private void removeAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeAllButtonActionPerformed
    parent.selection.clear();
    updateSelectionList();
  }//GEN-LAST:event_removeAllButtonActionPerformed

  private void loadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButtonActionPerformed

    JFileChooser chooser;
    File f = new File(defaultSelFilePath);
    if(f.exists())
     chooser = new JFileChooser(defaultSelFilePath);
    else
     chooser = new JFileChooser(".");
    String[] exts={"hdb"};
    HDBFileFilter filter = new HDBFileFilter("HDB selection file",exts);
    chooser.addChoosableFileFilter(filter);
    chooser.setFileFilter(filter);
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      loadFile(chooser.getSelectedFile().getAbsolutePath());
      defaultSelFilePath = chooser.getSelectedFile().getPath();
    }


  }//GEN-LAST:event_loadButtonActionPerformed

  private void saveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButtonActionPerformed

    // Acessory panel
    JCheckBox saveChartSettings;
    JPanel aPanel = new JPanel();
    aPanel.setLayout(new GridBagLayout());
    saveChartSettings = new JCheckBox("Save chart settings");
    GridBagConstraints gbc = new GridBagConstraints();
    aPanel.add(saveChartSettings,gbc);

    JFileChooser chooser;
    File f = new File(defaultSelFilePath);
    if(f.exists())
     chooser = new JFileChooser(defaultSelFilePath);
    else
     chooser = new JFileChooser(".");
    chooser.setAccessory(aPanel);
    String[] exts={"hdb"};
    HDBFileFilter filter = new HDBFileFilter("HDB selection file",exts);
    chooser.addChoosableFileFilter(filter);
    chooser.setFileFilter(filter);
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
      parent.saveConfigFile(chooser.getSelectedFile().getAbsolutePath(),saveChartSettings.isSelected());
      defaultSelFilePath = chooser.getSelectedFile().getPath();
    }
  }//GEN-LAST:event_saveButtonActionPerformed

  private void scriptButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scriptButtonActionPerformed
    JFileChooser chooser;
    File f = new File(defaultScriptPath);
    if(f.exists())
     chooser = new JFileChooser(defaultScriptPath);
    else
     chooser = new JFileChooser(".");
    String[] exts={"py"};
    HDBFileFilter filter = new HDBFileFilter("Python HDB script",exts);
    chooser.addChoosableFileFilter(filter);
    chooser.setFileFilter(filter);
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      scriptText.setText(chooser.getSelectedFile().getAbsolutePath());
    }
  }//GEN-LAST:event_scriptButtonActionPerformed

  private void allY1ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allY1ButtonActionPerformed
    parent.selectAllY1();
  }//GEN-LAST:event_allY1ButtonActionPerformed

  private void allY2ButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allY2ButtonActionPerformed
    parent.selectAllY2();
  }//GEN-LAST:event_allY2ButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton allY1Button;
  private javax.swing.JButton allY2Button;
  private javax.swing.JPanel btnPanel;
  private javax.swing.JPanel innerScriptPanel;
  private javax.swing.JPanel innerSelectionPanel;
  private javax.swing.JPanel listPanel;
  private javax.swing.JButton loadButton;
  private javax.swing.JButton removeAllButton;
  private javax.swing.JButton removeButton;
  private javax.swing.JButton saveButton;
  private javax.swing.JButton scriptButton;
  private javax.swing.JTextField scriptText;
  // End of variables declaration//GEN-END:variables
}

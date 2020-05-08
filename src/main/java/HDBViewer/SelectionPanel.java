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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import org.tango.jhdb.HdbFailed;
import org.tango.jhdb.HdbSigInfo;

/**
 *
 * @author pons
 */

class BooleanCellRenderer  implements TableCellRenderer {

  private final static Color backColor = new Color(240,240,255);  
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
  
  private SelectionPanel getParent(JTable table) {
    
    Container c = table.getParent();
    boolean found = false;
    while(!found && c!=null) {
      found = c instanceof SelectionPanel;
      if(!found) c = c.getParent();
    }
    
    return (SelectionPanel)c;
    
  }
  
  public Component getTableCellRendererComponent(JTable table,Object value,
												 boolean isSelected, boolean hasFocus, int row, int column) {
    
    Boolean b = (Boolean)value;
    SelectionPanel p = getParent(table);
    
    if(!p.isEditable(row,column)) {
      return empty;
    }

    int attIdx = p.rowToIdx[row].attIdx;
    int item = p.rowToIdx[row].arrayItem;
    AttributeInfo ai = p.parent.selection.get(attIdx);

    if(column==6 || column==7) {
      
      // Draw a button for array attribute in the Y1 and Y2
      if(ai.isArray() && item==-1) {
        if(ai.isNumeric()) {
          button.setText("Expand");
          return button;
        }
      }
      
    }

    checkBox.setSelected(b);
    if(column>=6)
      checkBox.setBackground(backColor);
    else
      checkBox.setBackground(Color.WHITE);
    
    

    return checkBox;
    
  }
  
}


public class SelectionPanel extends javax.swing.JPanel {

  final static String colNames[] = {
    "Tango Host",
    "Attribute",
    "Type",
    "Records",
    "Table",
    "Step",
    "Sel. Y1",
    "Sel. Y2",
    "Sel. Img"};

  class SelRowItem {
    SelRowItem(int idx,boolean write,int arrayItem) {
      attIdx = idx;
      isWrite = write;
      this.arrayItem = arrayItem;
    }
    int attIdx;
    int arrayItem;
    boolean isWrite;
  }

  final MainPanel parent;

  DefaultTableModel selModel;
  JTable selTable;
  JScrollPane selView;
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
    selModel = new DefaultTableModel() {

      public Class getColumnClass(int columnIndex) {
        if (columnIndex >= 4) {
          return Boolean.class;
        } else {
          return String.class;
        }
      }

      public boolean isCellEditable(int row, int column) {
        return isEditable(row,column);
      }

      public void setValueAt(Object aValue, int row, int column) {
        
        int selMode = AttributeInfo.SEL_NONE;
        boolean b = ((Boolean) aValue).booleanValue();
        int attIdx = rowToIdx[row].attIdx;
        boolean isW = rowToIdx[row].isWrite;
        int item = rowToIdx[row].arrayItem;

        switch (column) {
          
          case 4: // Table
            if(item<0)
              parent.selection.get(attIdx).table = b;
            else
              parent.selection.get(attIdx).arrAttInfos.get(item).table = true;
            updateSelectionList();
            return;
          case 5: // Step
            if(item<0)
              parent.selection.get(attIdx).step = b;
            else
              parent.selection.get(attIdx).arrAttInfos.get(item).step = true;
            updateSelectionList();
            return;
          case 6: // Y1
            if(b) selMode = AttributeInfo.SEL_Y1;
            break;
          case 7: // Y2
            if(b) selMode = AttributeInfo.SEL_Y2;
            break;
          case 8: // Image
            if(b) selMode = AttributeInfo.SEL_IMAGE;
            break;
        }
        
        AttributeInfo ai = parent.selection.get(attIdx);
        
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
                parent.unselectAttribute(ai, i);
            }
            ai.expand(expandedIDs);
          }

          updateSelectionList();
          return;

        }
        
        if( parent.selectAttribute(ai, item, selMode, isW) ) {

          // Unselect other image (if any)          
          for(AttributeInfo _ai:parent.selection) {
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

    };
    
    selTable = new JTable(selModel);
    selTable.setDefaultRenderer(Boolean.class, new BooleanCellRenderer());
    JScrollPane selView = new JScrollPane(selTable);
    selView.setPreferredSize(new Dimension(600,100));
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
    
    if(column<4)
      return false;

    int attIdx = rowToIdx[row].attIdx;
    int item = rowToIdx[row].arrayItem;
    AttributeInfo ai = parent.selection.get(attIdx);
    
    if(column==4) {
      // Table
      return true;
    }
    
    if(column==5) {
      // Step
      return (item>=0) ||
             (ai.isNumeric() && !ai.isArray());
    }
    
    if(column==6 || column==7) {
      // Y1, Y2
      return ai.isNumeric();
    }
    
    if(column==8) {
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
        
        if(ai.isRW())
          nbAtt+=2;
        else
          nbAtt+=1;      
        
      }
      
    }

    int j=0;
    Object[][] objs = new Object[nbAtt][9];
    rowToIdx = new SelRowItem[nbAtt];

    for (int i = 0; i < parent.selection.size(); i++) {
      
      AttributeInfo ai = parent.selection.get(i);
      
      objs[j][0] = ai.host;
      objs[j][1] = ai.getName();
      objs[j][2] = ai.getType();
      objs[j][3] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
      objs[j][4] = ai.table;        
      objs[j][5] = ai.step;        
      objs[j][6] = (ai.selection == AttributeInfo.SEL_Y1);
      objs[j][7] = (ai.selection == AttributeInfo.SEL_Y2);
      objs[j][8] = (ai.selection == AttributeInfo.SEL_IMAGE);
      rowToIdx[j] = new SelRowItem(i,false,-1);
      j++;
      
      if (ai.isExpanded()) {
        int k = 0;
        for (ArrayAttributeInfo aai : ai.arrAttInfos) {
          objs[j][0] = ai.host;
          objs[j][1] = ai.getName() + "[" + aai.idx + "]";
          objs[j][2] = "Item #" + aai.idx;
          objs[j][3] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
          objs[j][4] = aai.table;
          objs[j][5] = aai.step;
          objs[j][6] = (aai.selection == AttributeInfo.SEL_Y1);
          objs[j][7] = (aai.selection == AttributeInfo.SEL_Y2);
          objs[j][8] = (aai.selection == AttributeInfo.SEL_IMAGE);
          rowToIdx[j] = new SelRowItem(i, false, k);
          j++;
          k++;
        }
      }
        
      if(ai.isRW()) {
        
        objs[j][0] = ai.host;
        objs[j][1] = ai.getName()+"_w";
        objs[j][2] = ai.getType();
        objs[j][3] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
        objs[j][4] = ai.table;        
        objs[j][5] = ai.step;        
        objs[j][6] = (ai.wselection == AttributeInfo.SEL_Y1);
        objs[j][7] = (ai.wselection == AttributeInfo.SEL_Y2);
        objs[j][8] = (ai.wselection == AttributeInfo.SEL_IMAGE);
        rowToIdx[j] = new SelRowItem(i,true,-1);
        j++;
        
        if (ai.isExpanded()) {
          int k = 0;
          for (ArrayAttributeInfo aai : ai.arrAttInfos) {
            objs[j][0] = ai.host;
            objs[j][1] = ai.getName() + "_w[" + aai.idx + "]";
            objs[j][2] = "Write item #" + aai.idx;
            objs[j][3] = Integer.toString(ai.dataSize) + " (Err=" + Integer.toString(ai.errorSize) + ")";
            objs[j][4] = aai.table;
            objs[j][5] = aai.step;
            objs[j][6] = (aai.wselection == AttributeInfo.SEL_Y1);
            objs[j][7] = (aai.wselection == AttributeInfo.SEL_Y2);
            objs[j][8] = (aai.wselection == AttributeInfo.SEL_IMAGE);
            rowToIdx[j] = new SelRowItem(i, true, k);
            j++;
            k++;
          }
        }
        
      }
      
    }

    selModel.setDataVector(objs, colNames);
    selTable.getColumnModel().getColumn(1).setPreferredWidth(350);
    selTable.getColumnModel().getColumn(3).setMinWidth(100);
    selTable.getColumnModel().getColumn(4).setMaxWidth(60);
    selTable.getColumnModel().getColumn(5).setMaxWidth(60);
    selTable.getColumnModel().getColumn(6).setMaxWidth(60);
    selTable.getColumnModel().getColumn(7).setMaxWidth(60);
    selTable.getColumnModel().getColumn(8).setMaxWidth(60);
      
  }
  
  private void removeID(String id) {
    
    boolean found = false;
    int i=0;
    while(!found && i<parent.selection.size()) {
      found = parent.selection.get(i).sigInfo.sigId == id;
      if(!found) i++;
    }
    
    if(found) {
      AttributeInfo ai = parent.selection.get(i);      
      parent.unselectAttribute(ai,-1);
      if(ai.isExpanded()) {
        for(int j=0;j<ai.arrAttInfos.size();j++)
          parent.unselectAttribute(ai,j);
      }
      parent.selection.remove(i);
    }
    
  }

  private void removeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButtonActionPerformed

    int[] rows = selTable.getSelectedRows();
    ArrayList<String> ids = new ArrayList<String>();
    
    // List of ids to remove
    for(int i=0;i<rows.length;i++) {
      int attIdx = rowToIdx[rows[i]].attIdx;
      String id = parent.selection.get(attIdx).sigInfo.sigId;
      if(!ids.contains(id)) ids.add(id);
    }
    
    for(int i=0;i<ids.size();i++)
      removeID(ids.get(i));
    
    updateSelectionList();
    
  }//GEN-LAST:event_removeButtonActionPerformed

  void loadFile(String fileName) {
    
       try {

        // Load the config file
        ConfigFileReader cf = new ConfigFileReader(new FileReader(fileName));
        ArrayList<AttributeInfo> list = cf.parseFile();
        
        // Update type info
        for(int i=0;i<list.size();i++) {
          HdbSigInfo si = parent.hdb.getReader().getSigInfo(list.get(i).getFullName());
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

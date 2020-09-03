/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer;

import HDBViewer.AttributeTree.TreeListener;
import HDBViewer.AttributeTree.TreePanel;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import javax.swing.tree.TreePath;
import org.tango.jhdb.HdbFailed;
import org.tango.jhdb.HdbSigInfo;

/**
 *
 * @author pons
 */
public class HDBTreePanel extends javax.swing.JPanel implements ActionListener,TreeListener {

  MainPanel parent;
  
  com.toedter.calendar.JSpinnerDateEditor s0;
  com.toedter.calendar.JSpinnerDateEditor s1;
  TreePanel treePanel;

  /**
   * Creates new form QueryPanel
   */
  HDBTreePanel(MainPanel parent) {

    this.parent = parent;
    
    s0 = new com.toedter.calendar.JSpinnerDateEditor();
    ((javax.swing.JSpinner.DefaultEditor) s0.getEditor()).getTextField().setHorizontalAlignment(javax.swing.JTextField.LEFT);

    s1 = new com.toedter.calendar.JSpinnerDateEditor();
    ((javax.swing.JSpinner.DefaultEditor) s1.getEditor()).getTextField().setHorizontalAlignment(javax.swing.JTextField.LEFT);
    
    initComponents();
    
    // Create JTree

    treePanel = new TreePanel(parent.hdb.getReader());
    treePanel.addTreeListener(this);
    rightPanel.add(treePanel, BorderLayout.CENTER);
    
    lastTimeCombo.setEditable(false);
    lastTimeCombo.removeAllItems();
    lastTimeCombo.addItem("Last 1 hour");
    lastTimeCombo.addItem("Last 4 hour");
    lastTimeCombo.addItem("Last 8 hour");
    lastTimeCombo.addItem("Last day");
    lastTimeCombo.addItem("Last week");
    lastTimeCombo.addItem("Last month");
    lastTimeCombo.setSelectedIndex(2);
    lastTimeCombo.addActionListener(this);

    long now = System.currentTimeMillis();
    startDateChooser.setDate(new Date(now - 3600 * 8 * 1000));
    stopDateChooser.setDate(new Date(now));
    
  }
  
  String getStartDate() {
    String d = ((javax.swing.JSpinner.DefaultEditor) s0.getEditor()).getTextField().getText();
    return d;
  }

  String getStopDate() {
    String d = ((javax.swing.JSpinner.DefaultEditor) s1.getEditor()).getTextField().getText();
    return d;
  }

  void setDate(long start,long stop) {
    startDateChooser.setDate(new Date(start));
    stopDateChooser.setDate(new Date(stop));
  }

  // 0=Last 1 hour
  // 1=Last 4 hour
  // 2=Last 8 hour
  // 3=Last day
  // 4=Last week
  // 5=Last month
  void setTimeInterval(int it) {
    lastTimeCombo.setSelectedIndex(it);
  }
  
  int getTimeInterval() {
    return lastTimeCombo.getSelectedIndex();
  }
  
  long getStepDuration() {
    
      int idx = lastTimeCombo.getSelectedIndex();
      long time = 0;

      switch (idx) {
        case 0: // Last 1 hour
          time = 3600;
          break;
        case 1: // Last 4 hour
          time = 4 * 3600;
          break;
        case 2: // Last 8 hour
          time = 8 * 3600;
          break;
        case 3: // Last day
          time = 86400;
          break;
        case 4: // Last week
          time = 7 * 86400;
          break;
        case 5: // Last month
          time = 30 * 86400;
          break;
      }
      
      return time;
    
  }
  
  public void actionPerformed(ActionEvent evt) {

    Object src = evt.getSource();

    if (src == lastTimeCombo) {

      long time = getStepDuration();
      long now = System.currentTimeMillis();

      startDateChooser.setDate(new Date(now - time * 1000));
      stopDateChooser.setDate(new Date(now));

    }
  }

  @Override
  public void attributeAction(TreePanel source,ArrayList<AttributeInfo> list) {
 
    // Get attribute information from HDB and add it to the list
    try {

      // Update type info
      for(int i=0;i<list.size();i++) {
        int queryMode = list.get(i).queryMode;
        HdbSigInfo si = parent.hdb.getReader().getSigInfo(list.get(i).getFullName(),queryMode);
        list.get(i).sigInfo = si;
      }

      // Add to query list
      for(int i=0;i<list.size();i++) {
        AttributeInfo ai = list.get(i);        
        if(!AttributeInfo.isInList(ai, parent.selection)) {

          if(ai.isString())
            ai.table = true;
          
          if(ai.isNumeric() && !ai.isArray()) {
            ai.selection = AttributeInfo.SEL_Y1;
          }
          
          //if(ai.isNumeric() && ai.isArray()) {
          //  ai.selection = AttributeInfo.SEL_IMAGE;
          //}
          
          if(ai.isState() && !ai.isArray()) {
            ai.step = true;            
          }

          parent.selection.add(ai);
          
        }
      }

      parent.updateSelectionList();

    } catch(HdbFailed e) {
      Utils.showError(e.getMessage());
    }
    
  }
  
  int getHdbMode() {
    return hdbModeCombo.getSelectedIndex();
  }

  void setHdbMode(int mode) {
    hdbModeCombo.setSelectedIndex(mode);
  }
  
  void refreshTree() {

    treePanel.clearListener();
    TreePath oldPath = treePanel.getSelectionPath();    
    rightPanel.remove(treePanel);
    
    // Create new tree Panel    
    treePanel = new TreePanel(parent.hdb.getReader());
    treePanel.setSelectionPath(oldPath);
    treePanel.addTreeListener(this);    
    
    rightPanel.add(treePanel, BorderLayout.CENTER);        
    rightPanel.revalidate();
    
  }
  
  private void moveDate(long step) {
  
    long start = startDateChooser.getDate().getTime();    
    long stop = stopDateChooser.getDate().getTime();    
    start += step * 1000;    
    stop += step * 1000;
    startDateChooser.setDate(new Date(start));
    stopDateChooser.setDate(new Date(stop));
    parent.performSearch();
    parent.selPanel.updateSelectionList();
    
  }


  /**
   * This method is called from within the constructor to initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is always
   * regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {

    treeBtnPanel = new javax.swing.JPanel();
    rightPanel = new javax.swing.JPanel();
    selPanel = new javax.swing.JPanel();
    startDateChooser = new com.toedter.calendar.JDateChooser(null, null, "HH:mm:ss  dd/MM/yyyy", s0);
    stopDateChooser = new com.toedter.calendar.JDateChooser(null, null, "HH:mm:ss  dd/MM/yyyy", s1);
    jLabel1 = new javax.swing.JLabel();
    jLabel2 = new javax.swing.JLabel();
    hdbModeCombo = new javax.swing.JComboBox();
    searchButton = new javax.swing.JButton();
    lastTimeCombo = new javax.swing.JComboBox();
    forwardButton = new javax.swing.JButton();
    backButton = new javax.swing.JButton();

    setLayout(new java.awt.BorderLayout());

    treeBtnPanel.setLayout(new java.awt.BorderLayout());

    rightPanel.setLayout(new java.awt.BorderLayout());
    treeBtnPanel.add(rightPanel, java.awt.BorderLayout.CENTER);

    add(treeBtnPanel, java.awt.BorderLayout.CENTER);

    selPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "HDB Search", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 0, 10))); // NOI18N
    selPanel.setMinimumSize(new java.awt.Dimension(0, 0));
    selPanel.setPreferredSize(new java.awt.Dimension(270, 165));
    selPanel.setLayout(null);

    startDateChooser.setDateFormatString("dd/MM/yyyy HH:mm:ss");
    startDateChooser.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    selPanel.add(startDateChooser);
    startDateChooser.setBounds(60, 15, 200, 25);

    stopDateChooser.setDateFormatString("dd/MM/yyyy HH:mm:ss");
    stopDateChooser.setFont(new java.awt.Font("Monospaced", 0, 12)); // NOI18N
    selPanel.add(stopDateChooser);
    stopDateChooser.setBounds(60, 45, 200, 25);

    jLabel1.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    jLabel1.setText("Stop");
    selPanel.add(jLabel1);
    jLabel1.setBounds(10, 50, 45, 15);

    jLabel2.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    jLabel2.setText("Start");
    selPanel.add(jLabel2);
    jLabel2.setBounds(10, 20, 45, 15);

    hdbModeCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Normal", "Ignore errors", "Filled", "Correlated" }));
    hdbModeCombo.setToolTipText("Select HDB extraction mode");
    selPanel.add(hdbModeCombo);
    hdbModeCombo.setBounds(10, 135, 120, 25);

    searchButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    searchButton.setText("Perform search");
    searchButton.setToolTipText("Fetch data from HDB");
    searchButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
    searchButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        searchButtonActionPerformed(evt);
      }
    });
    selPanel.add(searchButton);
    searchButton.setBounds(135, 135, 125, 25);

    lastTimeCombo.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    lastTimeCombo.setSelectedIndex(-1);
    selPanel.add(lastTimeCombo);
    lastTimeCombo.setBounds(10, 75, 250, 24);

    forwardButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    forwardButton.setText(">>");
    forwardButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        forwardButtonActionPerformed(evt);
      }
    });
    selPanel.add(forwardButton);
    forwardButton.setBounds(135, 105, 125, 25);

    backButton.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
    backButton.setText("<<");
    backButton.addActionListener(new java.awt.event.ActionListener() {
      public void actionPerformed(java.awt.event.ActionEvent evt) {
        backButtonActionPerformed(evt);
      }
    });
    selPanel.add(backButton);
    backButton.setBounds(10, 105, 120, 25);

    add(selPanel, java.awt.BorderLayout.SOUTH);
  }// </editor-fold>//GEN-END:initComponents

  private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
    parent.performSearch();
    parent.selPanel.updateSelectionList();
  }//GEN-LAST:event_searchButtonActionPerformed

  private void forwardButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_forwardButtonActionPerformed
    // TODO add your handling code here:    
    moveDate(getStepDuration());
  }//GEN-LAST:event_forwardButtonActionPerformed

  private void backButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backButtonActionPerformed
    // TODO add your handling code here:
    moveDate(-getStepDuration());
  }//GEN-LAST:event_backButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton backButton;
  private javax.swing.JButton forwardButton;
  javax.swing.JComboBox hdbModeCombo;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JComboBox lastTimeCombo;
  private javax.swing.JPanel rightPanel;
  private javax.swing.JButton searchButton;
  private javax.swing.JPanel selPanel;
  private com.toedter.calendar.JDateChooser startDateChooser;
  private com.toedter.calendar.JDateChooser stopDateChooser;
  private javax.swing.JPanel treeBtnPanel;
  // End of variables declaration//GEN-END:variables

}

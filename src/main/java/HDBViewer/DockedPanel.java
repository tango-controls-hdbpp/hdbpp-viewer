/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer;

import fr.esrf.tangoatk.widget.util.ATKGraphicsUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * A class for handling docked panel
 */
public class DockedPanel extends JPanel {

  final static Insets noInsets = new Insets(0,0,0,0);
  final static ImageIcon closeIcon = new ImageIcon(DockedPanel.class.getResource("/HDBViewer/close.gif"));
  final static ImageIcon maximizeIcon = new ImageIcon(DockedPanel.class.getResource("/HDBViewer/maximize.gif"));
  final static ImageIcon minimizeIcon = new ImageIcon(DockedPanel.class.getResource("/HDBViewer/minimize.gif"));
  final static Color selColor = new Color(220,220,255);
  final static Color backColor = new Color(225,225,225);
  final static Font smalFont = new Font("Dialog",Font.PLAIN,11);
  
  class TabBarPanel extends JPanel implements MouseListener {
    
    DockedPanel parent;
    
    TabBarPanel(DockedPanel parent) {
      this.parent = parent;  
      addMouseListener(this);
    }
    
    public void paint(Graphics g) {
      
      Dimension d = getSize();
      g.setColor(getBackground());
      g.fillRect(0, 0, d.width, d.height);
      g.setFont(smalFont);
      int pos = 5;
      int sPos=0;
      int ePos=0;
      
      for(int i=0;i<parent.items.size();i++) {

        DockedItem di = parent.items.get(i);
                
        // Tab drawing
        g.setColor(Color.BLACK);
        g.drawLine(pos, d.height, pos, 8);
        g.drawLine(pos, 8, pos+6, 2);
        g.drawLine(pos+6, 2, pos+di.strWidth+10, 2);
        g.drawLine(pos+di.strWidth+10,2,pos+di.strWidth+10,d.height);
        
        g.setColor(Color.WHITE);
        g.drawLine(pos+1, d.height, pos+1, 8);
        g.drawLine(pos+1, 8, pos+7, 3);
        g.drawLine(pos+7, 3, pos+di.strWidth+9, 3);
        //g.drawLine(pos+di.strWidth+9,3,pos+di.strWidth+9,d.height);
        
        di.xPoly[0] = pos+2;
        di.yPoly[0] = d.height;

        di.xPoly[1] = pos+2;
        di.yPoly[1] = 8;

        di.xPoly[2] = pos+8;
        di.yPoly[2] = 4;

        di.xPoly[3] = pos+di.strWidth+9;
        di.yPoly[3] = 4;
        
        di.xPoly[4] = pos+di.strWidth+9;
        di.yPoly[4] = d.height;
        
        
        if(i==parent.selectedPanel) {
          g.setColor(selColor);
          sPos = pos;
          ePos = pos+ di.strWidth + 10;
        } else {
          g.setColor(backColor);
        }
        g.fillPolygon(di.xPoly,di.yPoly,di.nbPoint);

        // Text
        g.setColor(Color.BLACK);
        g.drawString(di.name, pos+7 , 13);
        
        pos = pos + di.strWidth + 10;
        
      }
      
      g.setColor(Color.BLACK);
      g.drawLine(0,d.height-1,sPos,d.height-1);
      g.drawLine(ePos, d.height-1, d.width, d.height-1);
            
    }

    public void mouseClicked(MouseEvent e) {

      int x = e.getX();
      
      boolean found = false;
      int pos = 5;
      int i=0;
      while(!found && i<parent.items.size()) {
        DockedItem di = parent.items.get(i);
        found = x>=pos && x<=pos+di.strWidth+10;
        if(!found) i++;
        pos = pos + di.strWidth + 10;
      }
      if(found)
        parent.selectPanel(i);
      
    }
    
    public void mousePressed(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    
  }

  class BarPanel extends JPanel implements ActionListener {

    DockedPanel parent;
    JButton closeBtn;
    JButton maximizeBtn;
    JButton minimizeBtn;
    TabBarPanel tabBar;
    
    BarPanel(DockedPanel parent) {
      
      this.parent = parent;
      
      setLayout(new GridBagLayout());
      
      GridBagConstraints gbc = new GridBagConstraints();

      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.BOTH;
      
      tabBar = new TabBarPanel(parent);
      add(tabBar,gbc);

      gbc.gridx = 1;
      gbc.gridy = 0;
      gbc.weightx = 0.0;
      gbc.fill = GridBagConstraints.NONE;
      
      minimizeBtn = new JButton();
      minimizeBtn.setMargin(noInsets);
      minimizeBtn.setIcon(minimizeIcon);
      minimizeBtn.setToolTipText("Minimize");
      minimizeBtn.setEnabled(false);
      minimizeBtn.addActionListener(this);
      add(minimizeBtn,gbc);
      
      gbc.gridx = 2;
      gbc.gridy = 0;
      gbc.weightx = 0.0;
      gbc.fill = GridBagConstraints.NONE;
      
      maximizeBtn = new JButton();
      maximizeBtn.setMargin(noInsets);
      maximizeBtn.setIcon(maximizeIcon);
      maximizeBtn.setToolTipText("Maximize");
      maximizeBtn.addActionListener(this);
      add(maximizeBtn,gbc);

      gbc.gridx = 3;
      gbc.gridy = 0;
      gbc.weightx = 0.0;
      gbc.fill = GridBagConstraints.NONE;
      
      closeBtn = new JButton();
      closeBtn.setMargin(noInsets);
      closeBtn.setIcon(closeIcon);
      closeBtn.setToolTipText("Close");
      closeBtn.addActionListener(this);
      add(closeBtn,gbc);
      
      
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      Object src = e.getSource();
      if(src==closeBtn) {
        parent.close();
      } else if(src==maximizeBtn) {
        parent.maximize();
        minimizeBtn.setEnabled(true);
        maximizeBtn.setEnabled(false);
      } else if(src==minimizeBtn) {
        parent.minimize();
        minimizeBtn.setEnabled(false);
        maximizeBtn.setEnabled(true);
      }
    }
    
  }
  
  // ----------------------------------------------------------

  class DockedItem {

    String name;
    JPanel panel;
    
    int    strWidth;

    int[]  xPoly;
    int[]  yPoly;
    int nbPoint;
        
    DockedItem(String name,JPanel panel) {
      
      this.name = name;
      this.panel = panel;
      strWidth = ATKGraphicsUtils.measureString(name, smalFont).width;
      nbPoint = 5;
      xPoly = new int[nbPoint];
      yPoly = new int[nbPoint];
      
    }
    
  }
  
  ArrayList<DockedItem> items;
  BarPanel barPanel;
  int selectedPanel;
  boolean visible;
  MainPanel parent;
  
  public DockedPanel(MainPanel parent) {
    this.parent = parent;
    items = new ArrayList<DockedItem>();
    setLayout(new BorderLayout());
    barPanel = new BarPanel(this);
    add(barPanel,BorderLayout.NORTH);    
    selectedPanel = -1;
    visible=true;
  }
  
  public void addPanel(String name,JPanel panel) {    
    DockedItem it = new DockedItem(name,panel);
    items.add(it);       
    if(selectedPanel==-1) selectPanel(0);
  }
  
  public void close() {
    parent.closePanel(this);
  }
  
  public void maximize() {
    parent.maximizePanel(this);
  }

  public void minimize() {
    parent.minimizePanel(this);
  }
  
  public void selectPanel(int idx) {

    if(selectedPanel>=0) {
      // Hide old panel
      JPanel panelToHide = items.get(selectedPanel).panel;
      remove(panelToHide);
    }
        
    selectedPanel = idx;
    
    JPanel panelToDisplay = items.get(idx).panel;
    
    add(panelToDisplay,BorderLayout.CENTER);
    panelToDisplay.setVisible(true);
    revalidate();
    repaint();
    
  }
    
}

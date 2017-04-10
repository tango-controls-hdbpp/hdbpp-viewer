/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer.AttributeTree;

import HDBViewer.AttributeInfo;
import HDBViewer.Utils;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.tango.jhdb.HdbFailed;
import org.tango.jhdb.HdbReader;
import org.tango.jhdb.HdbSigParam;

/**
 *
 * Selection tree for attribute
 */

class RootNode extends TreeNode {

  RootNode(HdbReader reader) {
    this.reader = reader;
  }
  
  void populateNode() {

    if(reader==null)
      return;
    
    try {
      String[] hosts = reader.getHosts();
      for(int i=0;i<hosts.length;i++)
        add(new HostNode(reader,hosts[i]));
    } catch(HdbFailed e) {
      Utils.showError(e.getMessage());
    }
    
  }
  
  ImageIcon getIcon() {
    return TreeNodeRenderer.hosticon;
  }
  
  public String toString() {
    return "Root";
  }

}

class HostNode extends TreeNode {

  String host;
  String alias;
  
  HostNode(HdbReader reader,String host) {
    this.reader = reader;
    this.host = host;
    alias = HostAlias.getInstance().getAliasFor(host);
  }
  
  void populateNode() {    
    try {
      String[] domains = reader.getDomains(host);
      for(int i=0;i<domains.length;i++)
        add(new DomainNode(reader,host,domains[i]));
    } catch(HdbFailed e) {
      Utils.showError(e.getMessage());
    }
  }
  
  ImageIcon getIcon() {
    return TreeNodeRenderer.hosticon;
  }
  
  public String toString() {
    return alias;
  }

}

class DomainNode extends TreeNode {

  String host;
  String domain;

  DomainNode(HdbReader reader,String host,String domain) {
    this.reader = reader;
    this.host = host;
    this.domain = domain;
  }
  
  void populateNode() {
    try {
      String[] families = reader.getFamilies(host,domain);
      for(int i=0;i<families.length;i++)
        add(new FamilyNode(reader,host,domain,families[i]));
    } catch(HdbFailed e) {
      Utils.showError(e.getMessage());
    }    
  }
  
  public String toString() {
    return domain;
  }

}

class FamilyNode extends TreeNode {

  String host;
  String domain;
  String family;
  
  FamilyNode(HdbReader reader,String host,String domain,String family) {
    this.reader = reader;
    this.host = host;
    this.domain = domain;
    this.family = family;
  }
  
  void populateNode() {
    try {
      String[] members = reader.getMembers(host,domain,family);
      for(int i=0;i<members.length;i++)
        add(new MemberNode(reader,host,domain,family,members[i]));
    } catch(HdbFailed e) {
      Utils.showError(e.getMessage());
    }    
  }
  
  public String toString() {
    return family;
  }

}

class MemberNode extends TreeNode {

  String host;
  String domain;
  String family;
  String member;
  
  MemberNode(HdbReader reader,String host,String domain,String family,String member) {
    this.reader = reader;
    this.host = host;
    this.domain = domain;
    this.family = family;
    this.member = member;
  }
  
  void populateNode() {
    try {
      String[] names = reader.getNames(host,domain,family,member);
      for(int i=0;i<names.length;i++)
        add(new AttributeNode(reader,host,domain,family,member,names[i]));
    } catch(HdbFailed e) {
      Utils.showError(e.getMessage());
    }    
  }
  
  ImageIcon getIcon() {
    return TreeNodeRenderer.devicon;
  }
  
  public String toString() {
    return member;
  }

}

class AttributeNode extends TreeNode {

  String host;
  String domain;
  String family;
  String member;
  String name;
  
  AttributeNode(HdbReader reader,String host,String domain,String family,String member,String name) {
    this.reader = reader;
    this.host = host;
    this.domain = domain;
    this.family = family;
    this.member = member;
    this.name = name;
  }
  
  void populateNode() {
  }

  public boolean isLeaf() {
    return true;
  }
  
  ImageIcon getIcon() {
    return TreeNodeRenderer.atticon;    
  }
  
  public String toString() {
    return name;
  }
  
  public String getHostName() {
    return host;
  }
  
  public String getAttributeName() {
    return domain + "/" + family + "/" + member + "/" + name;
  }

}

public class TreePanel extends JPanel implements MouseListener,TreeSelectionListener {

  TreeNode root;
  DefaultTreeModel treeModel;
  JTree tree;
  JScrollPane treeView;
  ArrayList<TreeListener> listeners;
  JPopupMenu actionMenu;
  JMenuItem addMenu;
  JMenu addConfMenu;
  JMenuItem addConfAll;
  JMenuItem addConfLabel;
  JMenuItem addConfUnit;
  JMenuItem addConfDisplayUnit;
  JMenuItem addConfStandardUnit;
  JMenuItem addConfFormat;
  JMenuItem addConfArchRelChange;
  JMenuItem addConfArchAbsChange;
  JMenuItem addConfArchPeriod;
  JMenuItem addConfDescription;
                     
  public TreePanel(HdbReader reader) {

    root = new RootNode(reader);
    treeModel = new DefaultTreeModel(root);
    tree = new JTree(treeModel);
    tree.setEditable(false);
    tree.setCellRenderer(new TreeNodeRenderer());
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    //tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setBorder(BorderFactory.createLoweredBevelBorder());
    tree.addMouseListener(this);
    tree.addTreeSelectionListener(this);
    tree.setToggleClickCount(0);
    treeView = new JScrollPane(tree);
    setLayout(new BorderLayout());
    add(treeView, BorderLayout.CENTER);
    setPreferredSize(new Dimension(250,400));
    setMinimumSize(new Dimension(10,10));
    listeners = new ArrayList<TreeListener>();

    actionMenu = new JPopupMenu();

    addMenu = new JMenuItem("Add");
    actionMenu.add(addMenu);
    addMenu.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_DATA);
      }      
    });

    addConfMenu = new JMenu("Configuration");
    actionMenu.add(addConfMenu);
    
    addConfAll = new JMenuItem("All");
    addConfMenu.add(addConfAll);
    addConfAll.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_ALL);
      }      
    });
    
    addConfLabel = new JMenuItem("Label");
    addConfMenu.add(addConfLabel);
    addConfLabel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_LABEL);
      }      
    });

    addConfUnit = new JMenuItem("Unit");
    addConfMenu.add(addConfUnit);
    addConfUnit.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_UNIT);
      }      
    });

    addConfDisplayUnit = new JMenuItem("Display Unit");
    addConfMenu.add(addConfDisplayUnit);
    addConfDisplayUnit.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_DISPLAY_UNIT);
      }      
    });

    addConfStandardUnit = new JMenuItem("Standard Unit");
    addConfMenu.add(addConfStandardUnit);
    addConfStandardUnit.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_STANDARD_UNIT);
      }      
    });

    addConfFormat = new JMenuItem("Format");
    addConfMenu.add(addConfFormat);
    addConfFormat.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_FORMAT);
      }      
    });

    addConfArchRelChange = new JMenuItem("Arch Rel Change");
    addConfMenu.add(addConfArchRelChange);
    addConfArchRelChange.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_ARCH_REL_CHANGE);
      }      
    });
    
    addConfArchAbsChange = new JMenuItem("Arch Abs Change");
    addConfMenu.add(addConfArchAbsChange);
    addConfArchAbsChange.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_ARCH_ABS_CHANGE);
      }      
    });

    addConfArchPeriod = new JMenuItem("Arch Period");
    addConfMenu.add(addConfArchPeriod);
    addConfArchPeriod.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_ARCH_PERIOD);
      }      
    });

    addConfDescription = new JMenuItem("Description");
    addConfMenu.add(addConfDescription);
    addConfDescription.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fireTreeListener(HdbSigParam.QUERY_CFG_DESCRIPTION);
      }      
    });
    
  }

  public ArrayList<AttributeInfo> getSelection() {
    return getSelection(HdbSigParam.QUERY_DATA);
  }
  
  public ArrayList<AttributeInfo> getSelection(int queryMode) {
    
    ArrayList<AttributeInfo> ret = new ArrayList<AttributeInfo>();
    TreePath[] paths = tree.getSelectionPaths();
    
    for(int i=0;i<paths.length;i++) {
      TreeNode node = (TreeNode)paths[i].getLastPathComponent();
      if(node instanceof AttributeNode) {
        AttributeNode aNode = (AttributeNode)node;
        AttributeInfo ai = new AttributeInfo();
        ai.name = aNode.getAttributeName();
        ai.host = aNode.getHostName();
        ai.step = false;
        ai.queryMode = queryMode;        
        ret.add(ai);
      }
    }
    
    return ret;
    
  }
  
  public void addTreeListener(TreeListener l) {
    if(!listeners.contains(l))
      listeners.add(l);
  }
  
  public void removeTreeListener(TreeListener l) {
    listeners.remove(l);
  }
  
  private void fireTreeListener(int mode) {
    
    ArrayList<AttributeInfo> la = getSelection(mode);
    for(TreeListener l:listeners)
      l.attributeAction(this,la);
    
  }
  
  public void valueChanged(TreeSelectionEvent e) {


  }

  public void mousePressed(MouseEvent e) {

    TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
    if (selPath != null) {

      if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON3) {


        if( !tree.isSelectionEmpty() && ((TreeNode)selPath.getLastPathComponent()).isLeaf() ) {

          // Check that the node is not already selected
          // If not, add it to the path
          if( !tree.isPathSelected(selPath) )
            tree.addSelectionPath(selPath);

          int nbSel = tree.getSelectionCount();
          if(nbSel>1)
            addMenu.setText("Add " + nbSel + " items");
          else
            addMenu.setText("Add");          
          actionMenu.show(tree, e.getX(), e.getY());

        }

      }
      
      if(e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 ) {
        // Force single selection on double click
        tree.setSelectionPath(selPath);
        fireTreeListener(HdbSigParam.QUERY_DATA);
      }

    }

  }
  public void mouseClicked(MouseEvent e) {}
  public void mouseReleased(MouseEvent e) {}
  public void mouseEntered(MouseEvent e) {}
  public void mouseExited(MouseEvent e) {}
    
}

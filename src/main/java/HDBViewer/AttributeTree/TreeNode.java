package HDBViewer.AttributeTree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import org.tango.jhdb.HdbReader;

/**
 * An abstract class for tree node.
 */
abstract class TreeNode extends DefaultMutableTreeNode {

  private boolean areChildrenDefined = false;
  TreePanel parentPanel = null;
  HdbReader reader;

  // Create node on the fly and return number of child
  @Override
  public int getChildCount() {

    if(!areChildrenDefined) {
      areChildrenDefined = true;
      populateNode();
    }

    return super.getChildCount();

  }

  // Clear all child nodes
  public void clearNodes() {
     removeAllChildren();
     areChildrenDefined = false;
  }

  // Fill children list
  abstract void populateNode();

  // Returns node icon
  ImageIcon getIcon() {
    return null;
  }

  // Returns true if the node is a leaf, false otherwise
  @Override
  public boolean isLeaf() {
    return false;
  }

  // Return the complete path of the node
  public TreePath getCompletePath() {
    int i;

    // Construct the path
    TreeNode node = this;
    TreeNode[] nodes = new TreeNode[node.getLevel()+1];
    for (i = nodes.length - 1; i >= 0; i--) {
      nodes[i] = node;
      node = (TreeNode) node.getParent();
    }
    return new TreePath(nodes);

  }
  
}

/**
 * Tree node renderer
 */
 class TreeNodeRenderer extends DefaultTreeCellRenderer {

   final static ImageIcon devicon = new ImageIcon(TreeNodeRenderer.class.getResource("/HDBViewer/device.gif"));
   final static ImageIcon srvicon = new ImageIcon(TreeNodeRenderer.class.getResource("/HDBViewer/server.gif"));
   final static ImageIcon leaficon = new ImageIcon(TreeNodeRenderer.class.getResource("/HDBViewer/leaf.gif"));
   final static ImageIcon atticon = new ImageIcon(TreeNodeRenderer.class.getResource("/HDBViewer/attribute.gif"));
   final static ImageIcon hosticon = new ImageIcon(TreeNodeRenderer.class.getResource("/HDBViewer/host.gif"));


   public TreeNodeRenderer() {}

   @Override
   public Component getTreeCellRendererComponent(
       JTree tree,
       Object value,
       boolean sel,
       boolean expanded,
       boolean leaf,
       int row,
       boolean hasFocus) {

     super.getTreeCellRendererComponent(
         tree, value, sel,
         expanded, leaf, row,
         hasFocus);

     TreeNode node = (TreeNode)value;
     ImageIcon icon = node.getIcon();
     if( icon!=null ) setIcon(icon);

     return this;
   }

 }

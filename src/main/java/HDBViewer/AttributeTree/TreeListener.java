/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer.AttributeTree;

import HDBViewer.AttributeInfo;
import java.util.ArrayList;

/**
 *
 * @author pons
 */
public interface TreeListener {
  
  public void attributeAction(TreePanel source,ArrayList<AttributeInfo> attributes);
  
}

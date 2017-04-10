package HDBViewer;

import fr.esrf.tangoatk.widget.util.ATKConstant;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class MultiLineCellRenderer extends JTextArea implements TableCellRenderer {

  private final static Font  TABLE_FONT = new Font("Dialog",Font.PLAIN,12);
  private final static Font  TABLE_FONT_BOLD = new Font("Dialog",Font.BOLD,12);
  private final static Color  selColor = new Color(220,220,255);
  private final static Color  backColor0 = new Color(250,255,255);  
  private final static Color  errorColor = new Color(200,200,200);  
  private final static Border selBorder = BorderFactory.createLineBorder(selColor);
  
  int    rowHeight;
  int    wInset;

  public MultiLineCellRenderer() {
    
    setLayout(null);
    setEditable(false);
    setLineWrap(false);
    setWrapStyleWord(false);
    setFont(TABLE_FONT);
    rowHeight = getRowHeight();        
    Insets m = getMargin();
    wInset = m.top + m.bottom;    
    
  }
  
  public int getHeight(String value) {

    int nbLine = 1;
    for(int i=0;i<value.length();i++)
      if(value.charAt(i)=='\n')
        nbLine++;
    return nbLine*rowHeight+wInset;
    
  }

  public Component getTableCellRendererComponent(JTable table,Object value,
												boolean isSelected, boolean hasFocus, int row, int column) {
    
    String str = (String)value;
    if( column==0 ) {
      // Time column
      setFont(TABLE_FONT_BOLD);
    } else {
      setFont(TABLE_FONT);
    }

    if(str.startsWith("/Err")) {
      setText(str.substring(4));
    } else if(str.startsWith("/State")) {
      setText(str.substring(6));      
    } else {
      setText(str);      
    }


    // Background color
    
    int[] selRows = table.getSelectedRows();
    if (Utils.contains(selRows, row)) {
      setBackground(selColor);
    } else {
      if(column>0) {        
        if(str.startsWith("/Err")) {
          setBackground(errorColor);   
        } else if(str.startsWith("/State")) {
          String stateStr;
          int idx = str.indexOf('\n');
          if(idx!=-1) {
            stateStr = str.substring(6,idx);
          } else {
            stateStr = str.substring(6);
          }
          setBackground(ATKConstant.getColor4State(stateStr));          
        } else {
          setBackground(Color.WHITE);
        }
      } else {
        // Time column
        setBackground(backColor0);        
      }
    }

    if (isSelected) {
      setBorder(selBorder);
    } else {
      setBorder(null);
    }

    return this;
  }
  
}


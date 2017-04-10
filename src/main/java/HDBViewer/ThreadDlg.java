package HDBViewer;

import fr.esrf.tangoatk.widget.util.ATKGraphicsUtils;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Class for handling a long task in an asynchronous way without blocking GUI
 *
 * @author  pons
 */

public class ThreadDlg extends JDialog {

  static public boolean   stopflag;
  static public ThreadDlg progDlg=null;

  private ThreadPanel panel;
  private Thread subProc;

  // Construction
  public ThreadDlg(Frame parent, String title,boolean hasProgress,Thread process) {

    super(parent, true);
    getContentPane().setLayout(new BorderLayout());

    panel = new ThreadPanel();
    panel.setMessage(title);
    panel.setOpaque(false);
    panel.setBorder(BorderFactory.createEtchedBorder());
    if(!hasProgress) panel.hideProgress();
    setContentPane(panel);
    setUndecorated(true);

    // Add a thread listener
    subProc = process;

    stopflag = false;

    progDlg = this;
    // Add window listener to start the subProc
    // when the dialog is displayed
    addWindowListener(new WindowAdapter() {
      public void windowOpened(WindowEvent e) {
        subProc.start();
      }
    });

  }
  
  public void setTitle(String text) {
    panel.setMessage(text);
  }
  
  public void setProgress(double p,String prefix) {
    panel.setProgress(p,prefix);
  }

  public void showDlg() {

    ATKGraphicsUtils.centerDialog(this);
    setVisible(true);

  }
  
  public void hideDlg() {
    setVisible(false);
    progDlg = null;
  }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer;

import fr.esrf.tangoatk.widget.util.chart.JLDataView;

/**
 *
 * @author pons
 */
public class AggregateAttributeInfo {

    int selection;      // Selection mode
    boolean table;      // Display in HDB table
    boolean step;       // Step mode
    JLDataView dv;      // Dataview
    String dvSettings;  // Dataview settings
    MutableDouble lastValue; // last value for step mode
    int tableIdx;       // Indexes for table    
    public AggregateAttributeInfo() {
      dv = null;
      dvSettings=null;
    }

}

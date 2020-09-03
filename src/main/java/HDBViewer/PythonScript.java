/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package HDBViewer;

import java.util.ArrayList;
import org.python.core.PyObject;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.util.PythonInterpreter;
import org.tango.jhdb.HdbFailed;
import org.tango.jhdb.HdbSigInfo;
import org.tango.jhdb.SignalInfo;
import org.tango.jhdb.data.HdbData;
import org.tango.jhdb.data.HdbDataSet;
import org.tango.jhdb.data.HdbDouble;
import org.tango.jhdb.data.HdbDoubleArray;
import org.tango.jhdb.data.HdbString;

/**
 * Execute python script on HdbData
 */
public class PythonScript {

  private PyObject hdbInterfaceClass;

  public PythonScript(String fileName) {
    PythonInterpreter interpreter = new PythonInterpreter();
    interpreter.execfile(fileName);
    hdbInterfaceClass = interpreter.get("PyHDBInterface");
  }

  /**
   * Execute a python script and return calculated HdbDataSet
   * @param inputs Input attributes
   * @throws HdbFailed in case of failure
   */
  public HdbDataSet[] run(HdbDataSet[] inputs) throws HdbFailed {

    // Build python input parameter
    
    PyList globalList = new PyList();
    for (int i = 0; i < inputs.length; i++) {

      PyList attList = new PyList();
      attList.add(inputs[i].getName());
      attList.add(new PyInteger(inputs[i].size()));
      for (int j = 0; j < inputs[i].size(); j++) {
        
        PyList vList = new PyList();
        HdbData d = inputs[i].get(j);
        int _sec = (int) (d.getDataTime() / 1000000);
        int _usec = (int) (d.getDataTime() % 1000000);
        vList.add(_sec);
        vList.add(_usec);
        vList.add(0);
        try {
          if (d.info.isNumeric()) {
            if (d.info.isArray()) {
              vList.add(d.getValueAsDoubleArray());
            } else {
              vList.add(d.getValueAsDouble());
            }
          } else {
            vList.add(d.getValueAsString());
          }
        } catch (HdbFailed e) {
          vList.add(e.getMessage());
        }
        attList.add(vList);

      }
      
      globalList.add(attList);

    }
    
    PyList pyR;
    
    try {

      // Call py constructor
      PyObject buildingObject = hdbInterfaceClass.__call__(new PyInteger(globalList.size()),
            globalList);
    
      // Call py getResult function
      PyHDBInterface hdbI = (PyHDBInterface) buildingObject.__tojava__(PyHDBInterface.class);        
      pyR = hdbI.getResult();
    
    } catch (Exception e) {
      
      throw new HdbFailed(e.toString());  
      
    }

    // Build result
    HdbDataSet[] result = new HdbDataSet[pyR.size()];
    for(int i=0;i<pyR.size();i++) {
      
      PyList attList = (PyList)pyR.get(i);
      String attName = (String)attList.get(0);
      int lgth = ((Integer) attList.get(1)).intValue();
      ArrayList<HdbData> list = new ArrayList<>();
      SignalInfo si = new SignalInfo();
      
      for(int j=0;j<lgth;j++) {
        
        PyList vList = (PyList)attList.get(j+2);
        
        int _sec = ((Integer) vList.get(0)).intValue();
        int _usec = ((Integer) vList.get(1)).intValue();
        HdbData d;
        //we might return more results than inputs, but we assume that
        //all data in a dataset are of the same type
        if(i < inputs.length)
        {
            d = inputs[i].get(0).copy(); // we use the same type as data input
        }
        else
        {
            d = inputs[0].get(0).copy(); // we use the first input type as default type
        }
        
        if(j==0) {
          si = d.info;
          si.name = attName;
        }
        
        
          if (d.info.isNumeric()) {
            if (d.info.isArray()) {
                                
            PyList arrList = ((PyList)vList.get(3));
            ArrayList<Object> v = new ArrayList<>();
            for(int k=0;k<arrList.size();j++) {
              v.add((Double)arrList.get(k));
            }
            d.parseValue(v);
            
          } else {
            ArrayList<Object> v = new ArrayList<>();
            v.add((Double)vList.get(3));
            d.parseValue(v);
                        
          }
          
        } else {
          ArrayList<Object> v = new ArrayList<>();
          v.add((String)vList.get(3));
          d.parseValue(v);
          
        }
        
        long dt = (long)_sec * 1000000 + (long)_usec;
        d.setDataTime(dt);
        list.add(d);
                
      }
      
      result[i] = new HdbDataSet(list);      
      result[i].setSigInfo(si);
      
    }
    

    return result;
    
  }
  
  
  private PyHDBInterface run(PyList globalList) {

    // Test function
    PyObject buildingObject = hdbInterfaceClass.__call__(new PyInteger(globalList.size()),
            globalList);
    return (PyHDBInterface) buildingObject.__tojava__(PyHDBInterface.class);
    
  }

  public static void main(String[] args) {

    // Test function
    System.out.println("Running PY");

    try {

      PythonScript script = new PythonScript("test.py");
      
      int t0 = (int)(System.currentTimeMillis()/1000);
      int us = 123456;
      
      PyList globalList = new PyList();

      PyList d1List = new PyList();
      d1List.add("Attribute 1");
      d1List.add(new PyInteger(2));
      PyList v1_1 = new PyList();
      v1_1.add(t0);
      v1_1.add(us);
      v1_1.add(HdbSigInfo.TYPE_SCALAR_DOUBLE_RO);
      v1_1.add(1.1);      
      d1List.add(v1_1);

      PyList v1_2 = new PyList();
      v1_2.add(t0+1);
      v1_2.add(us);
      v1_2.add(HdbSigInfo.TYPE_SCALAR_DOUBLE_RO);
      v1_2.add(3.2);      
      d1List.add(v1_2);

      PyList d2List = new PyList();
      d2List.add("Attribute 2");
      d2List.add(new PyInteger(2));
      PyList v2_1 = new PyList();
      v2_1.add(t0);
      v2_1.add(us);
      v2_1.add(HdbSigInfo.TYPE_SCALAR_DOUBLE_RO);
      v2_1.add(2.7);      
      d2List.add(v2_1);

      PyList v2_2 = new PyList();
      v2_2.add(t0+1);
      v2_2.add(us);
      v2_2.add(HdbSigInfo.TYPE_SCALAR_DOUBLE_RO);
      v2_2.add(0.4);      
      d2List.add(v2_2);

      globalList.add(d1List);
      globalList.add(d2List);

      PyList r = script.run(globalList).getResult();
      for (int i = 0; i < r.size(); i++) {
        PyList l = (PyList) r.get(i);
        System.out.println("Name: " + l.get(0));
        int lgth = ((Integer) l.get(1)).intValue();
        for (int j = 0; j < lgth; j++) {
          PyList val = (PyList)l.get(2+j);
          int _sec = ((Integer)val.get(0)).intValue();
          int _us = ((Integer)val.get(1)).intValue();
          int type = ((Integer)val.get(2)).intValue();
          double d = ((Double)val.get(3)).doubleValue();
          System.out.println("# " + j + "(" + _sec + "," +_us + ")" + "=" + d);
        }
      }

    } catch (Exception e) {
      System.out.println("Got error: " + e.toString());
    }

  }

}

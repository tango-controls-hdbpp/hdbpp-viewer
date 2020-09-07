package HDBViewer.AttributeTree;

import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoApi.ApiUtil;
import fr.esrf.TangoApi.Database;
import fr.esrf.TangoApi.DbDatum;
import java.util.ArrayList;

public class HostAlias {
  
  private static HostAlias obj=null;
  
  ArrayList<String> hosts;
  ArrayList<String> alias;
  
  private HostAlias() {
    
    hosts = new ArrayList<>();
    alias = new ArrayList<>();
    
    try {
      
      Database db = ApiUtil.get_db_obj();
      DbDatum datum = db.get_property("HDBViewer", "HostAlias");
      if(!datum.is_empty()) {
        
        String[] aliases = datum.extractStringArray();
        for (String aliase : aliases) {
          int idx = aliase.indexOf(',');
          if (idx!=-1) {
            String hName = aliase.substring(0, idx);
            String aName = aliase.substring(idx+1, aliase.length());
            hosts.add(hName);
            alias.add(aName);
          } else {
            System.out.println("HostAlias() wrong syntax :" + aliase);
          }
        }
                
      }
      
    } catch(DevFailed e) {
      System.out.print("HostAlias() failed:"+e.errors[0].desc);
    }
    
  }
  
  static HostAlias getInstance() {
    if(obj==null) obj = new HostAlias();
    return obj;
  }
  
  String getAliasFor(String host) {
    
    boolean found = false;
    int i=0;
    while(!found && i<hosts.size()) {
      found = hosts.get(i).equalsIgnoreCase(host);
      if(!found) i++;
    }
    
    if(found)
      return alias.get(i);
    else
      return host;
        
  }
  
}

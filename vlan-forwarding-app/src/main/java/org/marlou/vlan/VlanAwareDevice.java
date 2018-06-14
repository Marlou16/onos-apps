package org.marlou.vlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.onosproject.net.Device;

/**
 * This Class stores for every devices the VLAN related information. (VLAN-port relations).
 * Furthermore, it relies on the original Device object from ONOS.
 * 
 * @author Marlou Pors
 */
public class VlanAwareDevice {
	
    private HashMap<Integer, ArrayList<Integer>> portsPerVlan;
    private HashMap<Integer, ArrayList<Integer>> vlansPerPort;
    
    private Device device;
    
    public VlanAwareDevice(Device device) {
    	this.device = device;
    	this.portsPerVlan = new HashMap<Integer, ArrayList<Integer>>();
    	this.vlansPerPort = new HashMap<Integer, ArrayList<Integer>>();
    }
    
    public Device getCore(){
    	return device;
    }
        
    public HashMap<Integer, ArrayList<Integer>> getPortsPerVlan() {
    	return portsPerVlan;
    }
    
    public HashMap<Integer, ArrayList<Integer>> getVlansPerPort() {
    	return vlansPerPort;
    }

    public void addVlanOnPort(int vlan, int port) {
    	addEntry(portsPerVlan, vlan, port);
    	addEntry(vlansPerPort, port, vlan);
    }
    
    private void addEntry(HashMap<Integer, ArrayList<Integer>> map, int key, int entry) {
    	if (map.keySet().contains(key)) {
    		if (!map.get(key).contains(entry)) {
    			map.get(key).add(entry);
    			Collections.sort(map.get(key));
    		}
    	}
    	else {
    		map.put(key, new ArrayList<>(Arrays.asList(entry)));
    	}
    }
    
    public void removeVlanOnPort(int vlan, int port) {
    	removeEntry(portsPerVlan, vlan, port);
    	removeEntry(vlansPerPort, port, vlan);
    }
    
    private void removeEntry(HashMap<Integer, ArrayList<Integer>> map, int key, int entry) {
    	// Though you can assume that this is always the case, it is an easy save.
    	if (map.keySet().contains(key) && map.get(key).contains(entry)) {
    		map.get(key).remove((Integer) entry);		// otherwise the methods take the 'int' for position.
    		// Collections.sort(map.get(key))    		// not necessary I think.
    	}
    }
    
    public String showHashMapContents(int key, String keytype) {
    	return keytype.equals("port") ? vlansPerPort.get(key).toString() : portsPerVlan.get(key).toString();
    }
}

package org.marlou.vlan.cli;

import java.net.URI;
import java.util.ArrayList;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.marlou.vlan.VlanAwareDevice;
import org.marlou.vlan.VlanForwarder;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupKey;

@Command(scope = "onos", name = "add-vlan-on-port",
description = "Given a device, adds a vlan to the set of accepted VLANs on a certain port")
public class AddVlanOnPortCommand extends AbstractShellCommand {

	@Argument(index=0, name= "deviceId", required=true)
	URI deviceURI = null;
	
	@Argument(index=1, name= "portId", required=true)
	int portId = 0;

	// For now, you can only add one accepted VLAN at once
	@Argument(index=2, name= "vlanId", required=true)
	int vlanId = 0;

	@Override
	protected void execute() {
		// You should also check whether the values are valid, but for now I don't care.
		VlanForwarder forwarder = get(VlanForwarder.class);
		DeviceId deviceId = DeviceId.deviceId(deviceURI);
		VlanAwareDevice concernedDevice = forwarder.devices().get(deviceId);
		
		// Update the HashMaps
		concernedDevice.addVlanOnPort(vlanId, portId);
		
		// If in static mode: Install an initial FloodingRule for this VLAN or update an existing one.
		if (!forwarder.isReactiveMode()) {
			forwarder.installVlanFloodRule(concernedDevice, vlanId);			
		}
		// If in reactive mode, we are going to fill the groups with the correct ports to flood to.
		// This is still work in progress.
		else {
			// If the groups for the port/vlanId combination does not exist yet, create one.
			if (forwarder.groupService.getGroup(deviceId, forwarder.generateGroupKey(deviceId, Integer.valueOf(portId + "" + vlanId))) == null) {
				forwarder.installEmptyVlanGroup(concernedDevice, vlanId, portId);
			}
			
			// For all ports which now support the given vlanId:
			// -> Fill the group for the given port/vlanId combination with all other known ports  ('if')
			// -> Add the new port/vlanId combination to all other groups for the ports that already supported the vlanId. ('else')
			for (int tempPort : concernedDevice.getPortsPerVlan().get(vlanId)) {
				if (tempPort == portId) {
					GroupKey groupKey = forwarder.generateGroupKey(deviceId, Integer.valueOf(portId + "" + vlanId));
					ArrayList<GroupBucket> buckets = new ArrayList<GroupBucket>();
					for (int otherPort : getAllButInPort(concernedDevice)) {
						buckets.add(DefaultGroupBucket.createAllGroupBucket(DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(otherPort)).build()));
					}
					forwarder.groupService.addBucketsToGroup(deviceId, 
							 groupKey, 
							 new GroupBuckets(buckets),
							 groupKey, 
							 forwarder.appId());
				}
				else {
					GroupKey groupKey = forwarder.generateGroupKey(deviceId, Integer.valueOf(tempPort + "" + vlanId));
					ArrayList<GroupBucket> buckets = new ArrayList<GroupBucket>();
					buckets.add(DefaultGroupBucket.createAllGroupBucket(DefaultTrafficTreatment.builder().setOutput(PortNumber.portNumber(portId)).build()));
					
					forwarder.groupService.addBucketsToGroup(deviceId, 
															 groupKey, 
															 new GroupBuckets(buckets),
															 groupKey, 
															 forwarder.appId());
				}
			}
		}
		
	}
	
	/**
	 * Gives back all ports which support the given vlanId, except the inport.
	 * @param concernedDevice
	 * @return
	 */
	private ArrayList<Integer> getAllButInPort(VlanAwareDevice concernedDevice) {
		ArrayList<Integer> allButInPort = new ArrayList<Integer>();
		for (int port : concernedDevice.getPortsPerVlan().get(vlanId)) {
			if (port != portId) {
				allButInPort.add(port);
			}
		}
		return allButInPort;
		
	}
}

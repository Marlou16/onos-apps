package org.marlou.vlan.cli;

import java.net.URI;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.marlou.vlan.VlanAwareDevice;
import org.marlou.vlan.VlanForwarder;
import org.onlab.packet.VlanId;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;

@Command(scope = "onos", name = "remove-vlan-on-port",
description = "Given a device, removes an accepted vlan from a port")
public class RemoveVlanOnPortCommand extends AbstractShellCommand {

	@Argument(index=0, name="deviceId", required=true)
	URI deviceURI = null;
	
	@Argument(index=1, name="portId", required=true)
	int portId = 0;

	// For now, you can only add one accepted VLAN at once
	@Argument(index=2, name="vlanId", required=true)
	int vlanId = 0;
	
	@Override
	protected void execute() {
		VlanForwarder forwarder = get(VlanForwarder.class);
		VlanAwareDevice concernedDevice = forwarder.devices().get(DeviceId.deviceId(deviceURI));
		
		// Setup all in order to remove the old Flooding Rule
		TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
    	selectorBuilder.matchVlanId(VlanId.vlanId(((Integer) vlanId).shortValue())); 
    	TrafficSelector selector = selectorBuilder.build();
		
		for(FlowRule rule : forwarder.flowRuleService.getFlowEntriesById(forwarder.appId()))
		{
			if(rule.selector().criteria().equals(selector.criteria())) {
				forwarder.flowRuleService.removeFlowRules(rule);
				break;
			}
		}
		
		// Update the VlanAwareDevice object, and check that you do nothing if the VLAN entry gets empty, because then you should not add a rule.
		concernedDevice.removeVlanOnPort(vlanId, portId);
		
		if (concernedDevice.getPortsPerVlan().get(vlanId).isEmpty()) {
			concernedDevice.getPortsPerVlan().remove(vlanId);				// doing some cleaning
		}
		else {
			if (!forwarder.isReactiveMode()) {
				forwarder.installVlanFloodRule(concernedDevice, vlanId);	// install a new rule if in static mode!				
			}
		}
		
		/* FUTURE WORK: Update the Vlan Flooding Groups when removing a Vlan/Port combination.
		*/
	}

}

package org.marlou.vlan.cli;

import java.net.URI;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.marlou.vlan.VlanForwarder;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.DeviceId;


@Command(scope = "onos", name = "show-ports-on-vlan")
public class ShowPortsOnVlanCommand extends AbstractShellCommand {
	
	@Argument(index=0, name= "deviceId", required=true)
	URI deviceURI = null;
	
	@Argument(index=1, name= "vlanId", required=true)
	int vlanId = 0;
	
	@Override
	protected void execute() {
		// Again, somehow we should be able to retrieve the PortsPerVLAN...
		DeviceId deviceId = DeviceId.deviceId(deviceURI);
		VlanForwarder forwarder = get(VlanForwarder.class);
		print(forwarder.devices().get(deviceId).showHashMapContents(vlanId, "vlan"));
	}
}
package org.marlou.vlan.cli;

import org.apache.karaf.shell.commands.Command;
import org.marlou.vlan.VlanForwarder;
import org.onosproject.cli.AbstractShellCommand;


@Command(scope = "onos", name = "show-mode-vlan-app")
public class ShowModeCommand extends AbstractShellCommand {

	@Override
	protected void execute() {
		VlanForwarder forwarder = get(VlanForwarder.class);
		String mode = forwarder.isReactiveMode() ? "reactive intent" : "passive flooding";
		print("Currently the VLAN APP works in " + mode + " mode. You can switch using the command switch-mode-vlan-app.");
		
	} 

}

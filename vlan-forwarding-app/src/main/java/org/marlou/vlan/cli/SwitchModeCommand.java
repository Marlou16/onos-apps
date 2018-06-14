package org.marlou.vlan.cli;

import org.apache.karaf.shell.commands.Command;
import org.marlou.vlan.VlanForwarder;
import org.onosproject.cli.AbstractShellCommand;

@Command(scope = "onos", name = "switch-mode-vlan-app")
public class SwitchModeCommand extends AbstractShellCommand {

	@Override
	protected void execute() {
		VlanForwarder forwarder = get(VlanForwarder.class);
		forwarder.switchMode();		
	}

}

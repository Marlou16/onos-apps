This apps is a more sophisticated app that the static load-balancer, and implements (standard) VLAN support for ONOS. 

Characteristics of the app are the following:
* We use the class `VlanAwareDevice` to store all related VLAN information, mostly with use of HashMaps. 
Given a particular vlanId, you can easily find which ports on a device allow this VLAN and given a particular port, you can easily find which VLANs are allowed
* VLAN support is added to the devices from the ONOS the onsole, which is implemented using custom made commands.
The definition of these commands can be found in `cli` folder
* You can run the app in a reactive mode an static mode. Standardly, we have the reactive mode, where all VLAN tagged packets are processed by the controller which checks the VLAN configuration what to do.
In static mode, standard VLAN flooding rules are installed given the VLAN configuration.

The code knows certain limitations / bugs:
* The reactive mode uses a `vlanFlood` method, which follows the idea that a packet needs to be flooded over all ports supporting the VLAN except the incoming port.
Unfortunately, we were unsuccesful implementing this right. Currently, we only send the package to the lowest port which match on the VLAN.
We have tried to solve this issue by using Groups, but we were not able to implement correctly to send a PACKET_OUT which let's the switch go to the correct group
* The static mode flooding rules do not take the incoming port into account. This means that a packet which comes in on a port not support a particular VLAN, stills forwards the packet.

We hope to solve the limitations in the future.

When using the available `.oar` or `.jar` file, the app is named `org.marlou.vlan-app`.

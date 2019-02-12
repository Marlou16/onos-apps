This app implements (the core for) a static load balancer in a topology with <X> hosts and 2 switches, 
where the switches are connected to each other with two links. (Load balancing occurs over these two links between the switches).

Characteristics of the app are the following:
* Load balancing is achieved using one SELECT group per switch, where the weight per bucket defines the load balancing.
* The groups on the switches are installed directly when activating the app (the weights are defined in the code)
* The forwarding rules matching on the input port having the action to go to the group are installed when a host initiates traffic.

The app was tested using two whiteboxes with Pica8 in OVS mode installed, but a problem was that in groups, weights are not supported.
Using Mininet and a topologie with on one switch one host and on the other 10, shows that the load balancing works.

When using the provided `.oar` file to install the app on ONOS, the app-name is `org.marlou.app`






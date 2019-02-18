This app is a very simple, static, proactive aggregation function.

Characteristics of the app are the following:
* Given a single switch, port 1 will be labeled as the 'uplink' and all other ports are part of the 'downlink'
* All downlinks forward to the uplink
* All traffic from the uplink to the downlink is broadcasted to all ports, which is implemented using a Group.
* There is no packet processing implemented

The code knows certain limitations / bugs:
* The app only works when a network with 1 switch is already connected to the controller, because the installation of the flows is implemented in the `activate()` method
* The group entry reaches a `PENDING_ADD` status, which is not what we want, but traffic seems to work.
* Because we have static, pre-installed flows matching only on flows, we miss host discovery (because traffic doesn't reach the controller)

The code is created for learning purposes and as a good starting point to convert the code to also support the P4Runtime southbound interface. For know, flows etc. are installed via OpenFlow

When using the available `.oar` or `.jar` file, the app is named `org.marlou.aggregator`.

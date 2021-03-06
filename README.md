# onos-apps

This folder presents custom made ONOS applications written in Java, made while investigating SDN and whiteboxing.
The apps do contain some bugs, and are merely used as a proof of concept for different controller functionality,
written during my research.

In the app `/target/` folders, the `.jar` and `.oar` files are available to install on the ONOS controller.

Currently, we have the following apps:
* __My first app__, implementing a simple static loadbalancer
* An app implementing __basic VLAN support__, which can be applied in a reactive and static manner. This app also contains custom-made ONOS commands.
* A simple __Aggregator__, which implements static forwarding to an uplink and broadcast over the downlink.

More information on the projects can be found in the `README` files of the projects itself.



__important when building the projects yourself__

The apps are created following the ONOS tutorial, which creates standard test-files. We did not 'officially' test these apps, such that the test-files in the app are effectively incorrect. 
Therefore, when building the app, you should use the command `mvn install -DskipTests`.

__more onos apps__

These apps are created using the code provided by other onos applications, which you can find here: https://github.com/opennetworkinglab/onos-app-samples
Mostly is relied on the `group-fwd` and `ifwd` apps.

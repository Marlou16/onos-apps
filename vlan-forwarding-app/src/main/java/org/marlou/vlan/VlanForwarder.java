/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.marlou.vlan;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.VlanId;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.GroupId;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.group.DefaultGroupBucket;
import org.onosproject.net.group.DefaultGroupDescription;
import org.onosproject.net.group.DefaultGroupKey;
import org.onosproject.net.group.Group;
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.IntentState;
import org.onosproject.net.intent.Key;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;

import com.google.common.collect.Sets;


@Component(immediate = true)
@Service(value = VlanForwarder.class)
public class VlanForwarder {
	
    private static final int DEFAULT_PRIORITY = 50000;
    
    private static final EnumSet<IntentState> WITHDRAWN_STATES = EnumSet.of(IntentState.WITHDRAWN,
																            IntentState.WITHDRAWING,
																            IntentState.WITHDRAW_REQ);
    
    private final Logger log = getLogger(getClass());
    protected static KryoNamespace appKryo = new KryoNamespace.Builder()
												            .register(Integer.class)
												            .register(DeviceId.class)
												            .build("marlou-her-epic-vlan-app");

    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public FlowObjectiveService flowObjectiveService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public HostService hostService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public IntentService intentService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public GroupService groupService;

    private VlanForwarderImplementer processor = new VlanForwarderImplementer();

    private HashMap<DeviceId, VlanAwareDevice> devices;
    public HashMap<DeviceId, VlanAwareDevice> devices() { return devices; }
    
    private ApplicationId appId;
    public ApplicationId appId() { return appId; }
    
    private boolean reactiveMode;
    public boolean isReactiveMode() { return reactiveMode; }
    
    
    @Activate
    public void activate() {
    	appId = coreService.registerApplication("org.onosproject.vlan-app");
    	devices = new HashMap<DeviceId, VlanAwareDevice>();
    	
    	// Convert the devices to VlanAwareDevices needed for this app
    	Set<Device> simpleDevices = Sets.newHashSet(deviceService.getAvailableDevices());
    	simpleDevices.forEach(device -> {
    		this.devices.put(device.id(), new VlanAwareDevice(device));
    	});
    	
    	packetService.addProcessor(processor, PacketProcessor.director(2));

    	// We start in reactive mode, which also means we want to install the intercepts.
    	reactiveMode = true;
    	requestIntercepts();
    	
        log.info("Started", appId.id());
    }

    @Deactivate
    public void deactivate() {
    	// When in reactive mode, more cleanup is necessary (namely groups and intents).
    	if (reactiveMode) {
    		withdrawIntercepts();
        	clearIntents();
        	clearGroups();
    	}
    	flowRuleService.removeFlowRulesById(appId);
    	packetService.removeProcessor(processor);
    	processor = null;
        log.info("Stopped", appId.id());
    }
    
    /**
     * Cleans up the intents installed by this app (in reactive mode).
     */
    public void clearIntents() {
    	for (Intent intent : intentService.getIntents()) {
    		if (intent.appId().equals(appId)) {
    			intentService.withdraw(intent);
    		}
    	}
    }
    
    /**
     * Cleans up the groups installed by this app (in reactive mode).
     */
    public void clearGroups() {
    	for (Device device : deviceService.getAvailableDevices()) {
	    	for (Group group : groupService.getGroups(device.id(), appId)) {
	    		groupService.removeGroup(device.id(), group.appCookie(), appId);
	    	}
	    }
    }
    
    /**
     * Method to switch the app functionality between passive (flooding rules) mode and reactive mode (processing packet ins)
     */
    public void switchMode() {
    	reactiveMode = !reactiveMode;					// switch
    	
    	if (reactiveMode) {
    		flowRuleService.removeFlowRulesById(appId);	
    		requestIntercepts();
    	}
    	else {	// passiveMode
    		withdrawIntercepts();
    		clearIntents();
    		clearGroups();
    		for (VlanAwareDevice device : devices.values()) {
    			for (Integer vlanId : device.getPortsPerVlan().keySet()) {
    				installVlanFloodRule(device, vlanId);
    			}
    		}
    	}
    }
    

    /**
     * Install rules which allow us to process packet_ins.
     * These rules get a high priority in order to compete with other standard flow rules (installed by other apps).
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        selector.matchVlanId(VlanId.ANY);
        packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        selector.matchVlanId(VlanId.ANY);
        packetService.requestPackets(selector.build(), PacketPriority.CONTROL, appId);
    }

    /**
     * Uninstalls the rules which allowed us to process packet_ins.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        selector.matchVlanId(VlanId.ANY);
        packetService.cancelPackets(selector.build(), PacketPriority.CONTROL, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        selector.matchVlanId(VlanId.ANY);
        packetService.cancelPackets(selector.build(), PacketPriority.CONTROL, appId);
    }
    
    
    /**
     * This class contains the process method to process VLAN tagged packets.
     * The process method is only used when the app functions in reactive mode.
     * When in 'static' mode, we rely on installed VLAN Flooding Rules.
     * @author Marlou Pors
     *
     */
    private class VlanForwarderImplementer implements PacketProcessor {
       	
    	@Override
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we can't do any more to it.
            if (context.isHandled()) {
            	return;
            }
            
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }
            
            VlanId vlanId = VlanId.vlanId(ethPkt.getVlanID());
            HostId srcId = HostId.hostId(ethPkt.getSourceMAC(), vlanId);			// Adding the VLAN is very important
            HostId dstId = HostId.hostId(ethPkt.getDestinationMAC(), vlanId);		// Also here.

            // If the VLAN was not to be accepted on the incoming port, stop immediately.
            VlanAwareDevice concernedDevice = devices.get(pkt.receivedFrom().deviceId());
            PortNumber inPort = pkt.receivedFrom().port();
            
        	// Small hack: the HashMaps work with Integer keys, but we get a VlanId of which we want to use the value...
        	Integer vlanInt = Integer.valueOf(vlanId.toString());
        	Integer inPortInt = Integer.valueOf(pkt.receivedFrom().port().toString());
            
        	if (!concernedDevice.getVlansPerPort().get(inPortInt).contains(vlanInt)) {
        		return;
        	}
            
            // Do we know who this is for? No = flood ; Yes = setup connectivity.
            Host dst = hostService.getHost(dstId);
            if (dst == null) {
            	vlanFlood(context, vlanId);
            	return;
            }
          
            setUpConnectivity(context, srcId, dstId, vlanId);
           	forwardPacketToDst(context, dst);
        }

    }
    

    /**
     * Method which calculates to which sends the packet to all ports which allow the given vlanId, excluding the inport of the packet.
     * Currently, this method only sends the packet through to the lowest port which allows the given vlan.
     * Future Work is to fix this bug.
     * 
     * @param context packet context which is to be send through
     * @param vlanId which the packet was tagged with.
     */
    private void vlanFlood(PacketContext context, VlanId vlanId) {
    	VlanAwareDevice concernedDevice = this.devices.get(context.inPacket().receivedFrom().deviceId());
    	PortNumber inPort = context.inPacket().receivedFrom().port();
    	
        Integer vlanInt = Integer.valueOf(vlanId.toString());
    	
    	// If the VLAN is not accepted on any port but the inport, return.
    	// That concernedDevice.getPortsPerVlan() contains the key vlanId is indirectly checked in the processing method.
    	if (concernedDevice.getPortsPerVlan().get(vlanInt).size() == 1) {
    		return;
    	}
    	 	
    	/* Future Work: Solution to the bug in this method is to send the packet through to groups,
    	 * Where each group contains the correct set of output ports.
    	 * The groups are filled with use of the AddVlanOnPortCommand and works correctly,
    	 * But we fail to send the instruction to the switch to go to this group.
    	 * 
    	 */
    	// int groupId = Integer.valueOf(inPort.toString() + "" + vlanId.toString());
        // TrafficTreatment treatment = DefaultTrafficTreatment.builder().group(new GroupId(groupId)).build();
        // OutboundPacket packet = new DefaultOutboundPacket(concernedDevice.getCore().id(), treatment, context.inPacket().unparsed());
        // log.info("PACKET:   " + packet.toString());
        // packetService.emit(packet);
    	
    	Group towards = groupService.getGroup(concernedDevice.getCore().id(), generateGroupKey(concernedDevice.getCore().id(), Integer.valueOf(inPort.toString() + "" + vlanId.toString())));
    	log.info("WE ARE GOING TO SEND TO" + towards.id().toString());
    	context.treatmentBuilder().group(towards.id());
    	context.send();
    	
    	/*
    	if (groupService.getGroup(concernedDevice.getCore().id(), generateGroupKey(concernedDevice.getCore().id(), Integer.valueOf(inPort.toString() + "" + vlanId.toString()))) != null) {
    		log.info("THE GROUP DOES EXIST SO DO SOME SHIT!");
    	}
        
        // This loop thus not creates a set of Output ports. Placing the 'context.send()' inside the loop also doesn't fix things.
     	for (int port : concernedDevice.getPortsPerVlan().get(vlanInt)) {
    		PortNumber portNumber = PortNumber.portNumber(port);
    		if (!portNumber.equals(inPort)) {
    			context.treatmentBuilder().setOutput(portNumber);
    		}
    	}
    	context.send();
    	*/
    }
    
    /**
     * Forwards a packet_in to a specific destination. [Taken from the IntentReactiveForwarding App]
     * @param context packet_in
     * @param dst which we send the packet to.
     */
    private void forwardPacketToDst(PacketContext context, Host dst) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(dst.location().port()).build();
        OutboundPacket packet = new DefaultOutboundPacket(dst.location().deviceId(), treatment, context.inPacket().unparsed());
        packetService.emit(packet);
    }
    
    
    /**
     * Sets up the connectivity between srcId and dstId with use of Intents
     * [Inspiration form IntentReactiveForwarding App, expanded with VLAN support]
     * @param context
     * @param srcId
     * @param dstId
     * @param vlanId
     */
    private void setUpConnectivity(PacketContext context, HostId srcId, HostId dstId, VlanId vlanId) {
        
    	// Incorporate VLAN in the Intent.
    	TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchVlanId(vlanId);
        TrafficSelector selector = selectorBuilder.build();
        
        // Easy way to get an empty treatment :)
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

        Key key;
        if (srcId.toString().compareTo(dstId.toString()) < 0) {
            key = Key.of(srcId.toString() + dstId.toString(), appId);
        } else {
            key = Key.of(dstId.toString() + srcId.toString(), appId);
        }

        HostToHostIntent intent = (HostToHostIntent) intentService.getIntent(key);
        if (intent == null || WITHDRAWN_STATES.contains(intentService.getIntentState(key))) {
            HostToHostIntent hostIntent = HostToHostIntent.builder()
                    .appId(appId)
                    .key(key)
                    .one(srcId)
                    .two(dstId)
                    .selector(selector)
                    .treatment(treatment)
                    .priority(DEFAULT_PRIORITY)
                    .build();

            intentService.submit(hostIntent);
        }

    }

    // Install Flooding Rules per VLAN to ensure traffic forwarding (in static mode)
    /**
     * Installs Flooding Rules per VLAN to ensure traffic forwarding when the app is in static mode.
     * Currently, these Flooding Rules do not account the inPort, which gives the following 'bug':
     * A packet coming in from a port which doesn't allow a specific VLAN is still forwarded.
     * @param device
     * @param vlan
     */
    public void installVlanFloodRule(VlanAwareDevice device, Integer vlan) {
    	TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
    	TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
    	
    	/* Future Work: Expand the Flooding Rules to also account the inport to fix the bug. 
    	 */
    	selectorBuilder.matchVlanId(VlanId.vlanId(vlan.shortValue())); 
    	TrafficSelector vlanSelector = selectorBuilder.build();
    	
    	for (int portnr : device.getPortsPerVlan().get(vlan))
    	{
    		treatmentBuilder.setOutput(PortNumber.portNumber(portnr));
    	}
    	
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(vlanSelector)
                .withTreatment(treatmentBuilder.build())
                .withPriority(DEFAULT_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();
        flowObjectiveService.forward(device.getCore().id(), forwardingObjective); 	
    }

    
    /** 
     * Installs an Empty group with ID "port+vlanId" which is to be filled with ports to flood a packet to, when a packet arrives on the port with vlanId.
     * @param concernedDevice
     * @param vlanId
     * @param port
     */
    public void installEmptyVlanGroup(VlanAwareDevice concernedDevice, int vlanId, int port) {
    	Integer groupId = Integer.valueOf(port + "" + vlanId);
    	
        GroupDescription groupDescription = new DefaultGroupDescription(
                concernedDevice.getCore().id(),
                GroupDescription.Type.ALL,
                new GroupBuckets(new ArrayList<GroupBucket>()),
                generateGroupKey(concernedDevice.getCore().id(), groupId),
                groupId,
                appId
        );

        groupService.addGroup(groupDescription);
    	
    }
    
    
    
    /** Generates a GroupKey given the parameters, such that we can identify the group.
     * @param deviceId
     * @param groupId
     * @return
     */
    public GroupKey generateGroupKey(DeviceId deviceId, Integer groupId) {
        int hashed = Objects.hash(deviceId, groupId);
        return new DefaultGroupKey(appKryo.serialize(hashed));
    }
    
}

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
package org.marlou.app;

import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.GroupId;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
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
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;

import com.google.common.collect.Sets;

@Component(immediate = true)
public class StaticLoadBalancer {
	
	// FUTURE WORK: Transform the hardcoded values to properties, like ReactiveForwarding.java
	// Namely, these properties can be read out via localhost:8181/onos/v1/configuration.
	
	
    // Note that the priority is sometimes overrules by specific match-fields, it seems.
    private static final int DEFAULT_PRIORITY = 100;
    
    // Eventually, you want to have a solution which is more neat,
    // But because every device only has one group, this will suffice.
    private static final int HARDCODED_GROUPID = 44;					// #loveit
    private static final short WEIGHT_BUCKET1 = (short) 30;
    private static final short WEIGHT_BUCKET2 = (short) 70;
    
    // In the activate() method, the portNumbers are added to this list,
    // But you can define them here.
	private ArrayList<PortNumber> switchToSwitchPorts = new ArrayList<PortNumber>();
	private final static int[] PORTS = {2,3};
    

    private final Logger log = getLogger(getClass());
    protected static KryoNamespace appKryo = new KryoNamespace.Builder()
            .register(Integer.class)
            .register(DeviceId.class)
            .build("marlou-her-epic-app");

    // Services we use to retrieve some properties and information.
    // There are more, look at ReactiveForwarding.java or GroupForwarding.java
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    private LoadBalancerImplementer processor = new LoadBalancerImplementer();
    private ApplicationId appId;

    @Activate
    public void activate() {
    	appId = coreService.registerApplication("org.onosproject.static-lb");
    	
    	for (int portnr : PORTS) {
    		switchToSwitchPorts.add(PortNumber.portNumber(portnr));
    	}
    	
    	Set<Device> devices = Sets.newHashSet(deviceService.getAvailableDevices());
    	devices.forEach(device -> {
    		installGroup(device.id());
    	});
    	
    	packetService.addProcessor(processor, PacketProcessor.director(2));
    	requestIntercepts();
    	
        log.info("Started", appId.id());
    }

    @Deactivate
    public void deactivate() {
    	withdrawIntercepts();
    	
    	//clean-up rules
    	flowRuleService.removeFlowRulesById(appId);
    	
    	//clean-up groups at the devices
    	Set<Device> devices = Sets.newHashSet(deviceService.getAvailableDevices());
    	devices.forEach(device -> {
    		groupService.removeGroup(device.id(), 
    				generateGroupKey(device.id(), HARDCODED_GROUPID),appId);
    	});

    	packetService.removeProcessor(processor);
    	processor = null;
        log.info("Stopped");
    }
    
    /**
     * Request packet in via packet service, taken from the ReactiveForwarding-app
     * Note: We don't support IPv6 forwarding, thus these lines are deleted.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Cancel request for packet in via packet service, taken from the ReactiveForwarding-app
     * Note: We don't support IPv6 forwarding, thus these lines are deleted.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }
    
    private class LoadBalancerImplementer implements PacketProcessor {
        
    	/**
    	 * OK, so the idea is the following:
    	 * We need to know the ports where the two switches are connected towards each other.
    	 * If traffic comes from one of these ports and there is a packet_in, just flood (1)
    	 * Otherwise, if there is a packet_in: (2)
    	 * 	- Create a flow for dst_mac, output at incoming port
    	 *  - Create a flow for incoming port, go to group.
    	 */
    	
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

            // MacAddress src_mac = ethPkt.getSourceMAC();  <- used in InstallRule method.
            PortNumber in_port = pkt.receivedFrom().port();
            MacAddress dst_mac = ethPkt.getDestinationMAC();
            // Question: How is this result different than the other?
            // MacAddress dst_mac = HostId.hostId(ethPkt.getDestinationMAC()).mac();


            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }

            // Do not process LLDP MAC address in any way.
            // Also, do not process IPv4 multicast packets, let mfwd handle them. (OK....)
            // Note: check for Multicast different than in ReactiveForwarding.
            if (dst_mac.isLldp() || ethPkt.isMulticast()) {
                return;
            }
            
            // Situation (1):
            // Note that this solution is not as neat as in ReactiveForwarding,
            // Due that we do not use a TopologyListener.
            if (switchToSwitchPorts.contains(in_port)) {
                packetOut(context, PortNumber.FLOOD);
                return;
            }
            
            // Situation (2):
            installRule(context, in_port, false);		// Install the rule towards the host
            installRule(context, in_port, true);		// Install the rule towards the group
            
            // Send the packet out back into the OpenFlow pipeline, such that we match our new rules.
            packetOut(context, PortNumber.TABLE);
        }

    }

    // Indicates whether this is a control packet, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }
    

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    // Installs two type of rules:
    // 1 - Given a destination MAC, the corresponding out_port
    // 2 - Given an in_port, the corresponding action group.
    private void installRule(PacketContext context, PortNumber in_port, boolean groupRule) {
    	
    	Ethernet inPkt = context.inPacket().parsed();
    	TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
    	TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
    	
    	
    	// (1): Send forwarding rule towards destination
    	if (!groupRule) {
    		selectorBuilder.matchEthDst(inPkt.getSourceMAC());
    		treatmentBuilder.setOutput(in_port);		
    	}
    	
    	// (2): Create a rule towards a group (if needed, also install group!)
    	else {
    		selectorBuilder.matchInPort(in_port);
    		treatmentBuilder.group(new GroupId(HARDCODED_GROUPID));
    	}
    	
    	// Construct the FlowRule and send it onwards.
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(selectorBuilder.build())
                .withTreatment(treatmentBuilder.build())
                .withPriority(DEFAULT_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();

        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                                     forwardingObjective);
    	
    }
    
    // Creates the group for load-balancing.
    private void installGroup(DeviceId deviceId) {
    	Integer groupId = HARDCODED_GROUPID;
    	
    	// Create the two buckets
    	ArrayList<GroupBucket> buckets = new ArrayList<GroupBucket>();
    	buckets.add(createSimpleBucket(switchToSwitchPorts.get(0), WEIGHT_BUCKET1));
    	buckets.add(createSimpleBucket(switchToSwitchPorts.get(1), WEIGHT_BUCKET2));

    	
        GroupDescription groupDescription = new DefaultGroupDescription(
                deviceId,
                GroupDescription.Type.SELECT,
                new GroupBuckets(buckets),
                generateGroupKey(deviceId, groupId),
                groupId,
                appId
        );

        groupService.addGroup(groupDescription);
    }
    
    // Creation of bucket (done in a seperate method to reduce code-duplication)
    private GroupBucket createSimpleBucket(PortNumber outputPort, short weight) {
    	TrafficTreatment.Builder treatmentBuilder = DefaultTrafficTreatment.builder();
    	treatmentBuilder.setOutput(outputPort);
    	return DefaultGroupBucket.createSelectGroupBucket(treatmentBuilder.build(), weight);
    }
    
    
    // Directly taken from the GroupForwarding example app.
    private GroupKey generateGroupKey(DeviceId deviceId, Integer groupId) {
        int hashed = Objects.hash(deviceId, groupId);
        return new DefaultGroupKey(appKryo.serialize(hashed));
    }

}
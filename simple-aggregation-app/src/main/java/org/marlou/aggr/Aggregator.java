/*
 * Copyright 2019-present Open Networking Foundation
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
package org.marlou.aggr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
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
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * Implements a static, pro-active aggregator for one switch.
 * Call the first port the 'uplink', where all traffic will come out.
 * At first, all traffic from the 'uplink' downwards will be broadcasted, using groups.
 * 
 * @author Marlou Pors
 *
 */


@Component(immediate = true)
@Service(value = Aggregator.class)
public class Aggregator {

	private static final int DEAFULT_PRIORITY = 50000;
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    // Needed to generate hashed as input for GroupKey generation.
    protected static KryoNamespace appKryo = new KryoNamespace.Builder()
            .register(DeviceId.class)
            .build("aggregator-app");
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public CoreService coreService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public DeviceService deviceService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public PacketService packetService;
    private AggregatorImplementer processor = new AggregatorImplementer();
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public FlowObjectiveService flowObjectiveService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public HostService hostService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public FlowRuleService flowRuleService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public IntentService intentService;
    
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    public GroupService groupService;
    
    
    
    private ApplicationId appId;
    public ApplicationId appId() { return appId; }
    
    private PortNumber uplink;
    public PortNumber uplink() { return uplink; }

    private ArrayList<PortNumber> downlinks;
    public ArrayList<PortNumber> downlinks() { return downlinks; }
    
    private Device aggregator;
    public Device aggregator() { return aggregator; }

    
    // Note: See GroupForwarder with a InternalDeviceListener
    // This you can use to let the app react on a new network...
    
    /* We assume that the network already exist before starting the application.
     * In the 'activate', we state the first port of the switch to be the uplink.
     */
    @Activate
    protected void activate() {
        appId = coreService.registerApplication("org.marlou.aggregation-app");
        
        List<Device> devices = Lists.newArrayList(deviceService.getAvailableDevices());
        deviceService.getAvailableDevices();
        // should maybe be solved with a Exception / Error
        if (devices.size() != 1) {
        	log.warn("There are more than one switches, ABORT the ship!");
        }
        else {
        	this.aggregator = devices.get(0);
        	setAggregationPorts();
        	setForwardingToUplink();
        	setGroupToDownlink();
        }
        
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
               
        log.info("Started", appId.id());
    }

    @Deactivate
    protected void deactivate() {
    	withdrawIntercepts();
    	flowRuleService.removeFlowRulesById(appId);
    	for (Group group: groupService.getGroups(aggregator.id(), appId)) {
    		groupService.removeGroup(aggregator.id(), group.appCookie(), appId);
    	}
    	packetService.removeProcessor(processor);
        log.info("Stopped", appId.id());
    }
    
    /**
     * Register the first port as the uplink and the others as downlinks,
     */
    public void setAggregationPorts() {
    	this.uplink = PortNumber.portNumber(1);
    	this.downlinks = new ArrayList<PortNumber>();
    	for(Port p : deviceService.getPorts(aggregator.id())) {
    		if (!p.number().equals(uplink))
    			this.downlinks.add(p.number());
    	}
    }
    
    /**
     * For each port in the downlink, install a flow to forward to the uplink.
     */
    public void setForwardingToUplink() {
    	for(PortNumber downlink : downlinks) {
    		ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(DefaultTrafficSelector.builder()
    								.matchInPort(downlink)
    								.build())
                    .withTreatment(DefaultTrafficTreatment.builder()
                    				.setOutput(uplink)
                    				.build())
                    .withPriority(DEAFULT_PRIORITY)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(appId)
                    .makePermanent()
                    .add();
        	
        	flowObjectiveService.forward(aggregator.id(), forwardingObjective);
    	}
    }
    
    /**
     * given the uplink, create a groupEntry such that traffic is forwarded to each port in de downlink.
     */
    public void setGroupToDownlink() {
    	GroupKey groupKey = new DefaultGroupKey(appKryo.serialize(Objects.hash(aggregator.id())));
    	// No idea, what kind of groupId this gives; or what is the exact different between the 'cookie'  and 'id'...
    	Integer groupId = aggregator.id().hashCode() & 0x7FFFFFFF;
    	
    	// creation of the buckets
    	ArrayList<GroupBucket> buckets = new ArrayList<GroupBucket>();
    	for(PortNumber downlink : downlinks) {
    		buckets.add(DefaultGroupBucket.createSelectGroupBucket(
    							DefaultTrafficTreatment.builder().setOutput(downlink).build()));		
    	}
    	
    	// addition of the group which contains the created buckets
    	groupService.addGroup(new DefaultGroupDescription(
    			aggregator.id(),
    			GroupDescription.Type.ALL,
    			new GroupBuckets(buckets),
    			groupKey,
    			groupId,
    			appId
    	));;

    	// adding the forwarding towards this group from the uplink
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(DefaultTrafficSelector.builder()
                						.matchInPort(uplink)
                						.build())
                .withTreatment(DefaultTrafficTreatment.builder()
                						.group(groupService.getGroup(aggregator.id(), groupKey).id())
                						.build())
                .withPriority(DEAFULT_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();
    	
    	flowObjectiveService.forward(aggregator.id(), forwardingObjective);

    }

    
    /**
     * Install rules which allow us to process packet_ins; 
     * Empty for now.
     */
    private void requestIntercepts() {
    	return;
    }
    
    /**
     * Uninstalls the rules which allowed us to process packet_ins
     * Emtpy for now.
     */
    private void withdrawIntercepts() {
    	return;
    }
    
    private class AggregatorImplementer implements PacketProcessor {

		@Override
		public void process(PacketContext context) {
			/* For starters, we implement this aggregator pro-active,
			 * which means there is no (need for) packet processing...
			 * But in the future packet processing for the way back could be here!
			 */		
		}
    	
    }

}
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
package org.marlou.aggr;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
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
import org.onosproject.net.group.GroupBucket;
import org.onosproject.net.group.GroupBuckets;
import org.onosproject.net.group.GroupDescription;
import org.onosproject.net.group.GroupKey;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;

import org.onlab.util.KryoNamespace;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.collect.Lists;


@Component(immediate = true)
@Service(value = Aggregator.class)
public class Aggregator {
	
    private static final int DEFAULT_PRIORITY = 50000;
    
    private final Logger log = getLogger(getClass());
    protected static KryoNamespace appKryo = new KryoNamespace.Builder()
												            .register(DeviceId.class)
												            .build("marlou-her-aggregator");

    
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
    public GroupService groupService;

    private AggregatorImplementer processor = new AggregatorImplementer();
   
    private ApplicationId appId;
    public ApplicationId appId() { return appId; }
    
    private PortNumber uplink;
    public PortNumber uplink() { return uplink; }

    private ArrayList<PortNumber> downlinks;
    public ArrayList<PortNumber> downlinks() { return downlinks; }
    
    private Device aggregator;
    public Device aggregator() { return aggregator; }
    
    
    @Activate
    public void activate() {
    	appId = coreService.registerApplication("org.marlou.aggregator");
    	 	
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
    public void deactivate() {
    	withdrawIntercepts();
    	flowRuleService.removeFlowRulesById(appId);
    	packetService.removeProcessor(processor);
    	processor = null;
        log.info("Stopped", appId.id());
    }
    
    
    /**
    * Register the first port as the uplink and the others as downlinks, also eliminate the LOCAL port.
    */
    public void setAggregationPorts() {
    	this.uplink = PortNumber.portNumber(1);
    	this.downlinks = new ArrayList<PortNumber>();
    	for(Port p : deviceService.getPorts(aggregator.id())) {
    		if (!p.number().equals(uplink) && !p.number().equals(PortNumber.LOCAL))
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
    				.withPriority(DEFAULT_PRIORITY)
    				.withFlag(ForwardingObjective.Flag.VERSATILE)
    				.fromApp(appId)
    				.makePermanent()
    				.add();

    		flowObjectiveService.forward(aggregator.id(), forwardingObjective);
    	}
    }
   
    /**
     * given the uplink, create a groupEntry such that traffic is forwarded to each port in the downlink set.
     */
    public void setGroupToDownlink() {
    	GroupKey groupKey = new DefaultGroupKey(appKryo.serialize(Objects.hash(aggregator.id())));
    	// No idea, what kind of groupId this gives; To be improved.
    	Integer groupId = aggregator.id().hashCode() & 0x7FFFFFFF;

    	// creation of the buckets to forward to a downlink
    	ArrayList<GroupBucket> buckets = new ArrayList<GroupBucket>();
    	for(PortNumber downlink : downlinks) {
    		buckets.add(DefaultGroupBucket.createSelectGroupBucket(
    				DefaultTrafficTreatment.builder().setOutput(downlink).build()));		
    	}

    	// addition of the group which contains the created buckets
    	// We set the type to ALL, this results in a broadcast to all downlinks.
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
				.withPriority(DEFAULT_PRIORITY)
				.withFlag(ForwardingObjective.Flag.VERSATILE)
				.fromApp(appId)
				.makePermanent()
				.add();

		flowObjectiveService.forward(aggregator.id(), forwardingObjective);

    }
    
   

    /**
     * Install rules which allow us to process packet_ins.
     */
    private void requestIntercepts() {
    	// TODO
    }

    /**
     * Uninstalls the rules which allowed us to process packet_ins.
     */
    private void withdrawIntercepts() {
    	// TODO
    }
    
    
    /**
     * This class contains the process method to process VLAN tagged packets.
     * The process method is only used when the app functions in reactive mode.
     * When in 'static' mode, we rely on installed VLAN Flooding Rules.
     * @author Marlou Pors
     *
     */
    private class AggregatorImplementer implements PacketProcessor {
       	
    	@Override
        public void process(PacketContext context) {
            // TODO
        }

    }
       
}

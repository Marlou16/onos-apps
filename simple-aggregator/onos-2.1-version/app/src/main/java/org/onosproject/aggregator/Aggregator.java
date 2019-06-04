package org.onosproject.aggregator;

import com.google.common.collect.Lists;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.util.KryoNamespace;
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
import org.onosproject.net.group.*;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.slf4j.LoggerFactory.getLogger;

/** Because we have defined the P4 code to match on the IP destination address instead of incoming port
    We need to change the 'selectors'. However, this is not really known beforehand, only if we assume
    the simple IP addresses as we can make in Mininet (10.0.0.X).
    At the same time, we don't need a flow for each different downlink port (because everything up is towards 10.0.0.1)
    However, theoretically speaking, in this was you can not force all traffic from downlink ports to go up...
    Interesting...
    We could also choose to change the P4 code to match on incoming port...

    CONCLUSION:
    You want to match on both the destination address and in_port in order to fix that you can't have multicast from
    one of the downlinks.
    For starters, I tried using the IP_DST, but then off course, the logic turns around (you only need one flow for
    upstream traffic and a lot for downstream traffic - which I messed up.
    Also, ".matchIPDst(Ip4Prefix.valueOf(Ip4Address.valueOf("10.0.0.1"),24))" seems to result in 10.0.0.0/24, so
    We should better look into how these Ip4Addresses work...
    FIXME OMG is it that easy in changing your /24 to /32?! (@Wouter)

    OPTION 2:
    Let's test it with the P4 code in TOTAL HARDCODED mode

 */
@Component(immediate = true)
public class Aggregator {

    private static final int NUMBER_OF_PORTS = 5;
    private static final boolean HARDCODED_MODE = true;

    private static final int DEFAULT_PRIORITY = 50000;

    private final Logger log = getLogger(getClass());
    protected static KryoNamespace appKryo = new KryoNamespace.Builder()
            .register(DeviceId.class)
            .build("marlou-her-aggregator");


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    public FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
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
            //setForwardingToUplink();
            setForwardingUpstream();
            //setGroupToDownlink();
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

        groupService.removeGroup(aggregator.id(), new DefaultGroupKey(appKryo.serialize(Objects.hash(aggregator.id()))), appId);

        processor = null;
        log.info("Stopped", appId.id());
    }


    /**
     * Register the first port as the uplink and the others as downlinks, also eliminate the LOCAL port.
     */
    public void setAggregationPorts() {
        this.uplink = PortNumber.portNumber(1);
        this.downlinks = new ArrayList<PortNumber>();

        if(!HARDCODED_MODE) {
            for (Port p : deviceService.getPorts(aggregator.id())) {
                if (!p.number().equals(uplink) && !p.number().equals(PortNumber.LOCAL))
                    this.downlinks.add(p.number());
            }
        }
        else {
            for (int i = 2; i <= NUMBER_OF_PORTS; i++) {
                this.downlinks.add(PortNumber.portNumber(i));       // FIXME: QUIT DOING THINGS HARDCODED MAN.
            }
        }
    }

    public void setForwardingUpstream() {
        // will apply for all downlinks, but we match on destination IP, making one flow sufficient.
        // TODO This is still hardcoded...
        Ip4Prefix ip = Ip4Prefix.valueOf("10.0.0.1/32");
        log.info("WOW, I created a Ip4Prefix: " + ip.toString());
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .withSelector(DefaultTrafficSelector.builder()
                            .matchIPDst(ip)
                            .build())
                .withTreatment(DefaultTrafficTreatment.builder()
                        .setOutput(uplink)
                        .build())
                .withPriority(DEFAULT_PRIORITY)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .fromApp(appId)
                .makePermanent()
                .add();

        log.info("SO CURIOUS LOVE IT: " + forwardingObjective.toString());

        flowObjectiveService.forward(aggregator.id(), forwardingObjective);
    }

    /**
     * For each port in the downlink, install a flow to forward to the uplink.
     * FIXME Currently not used anymore because of changed Match criteria.
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
        Integer groupId = 1;

        // creation of the buckets to forward to a downlink
        ArrayList<GroupBucket> buckets = new ArrayList<GroupBucket>();
        for(PortNumber downlink : downlinks) {
            buckets.add(DefaultGroupBucket.createAllGroupBucket(
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
        ));

        int dummy = 1;
        // can also be an foreach loop off course.
        // TODO if this works, we need to find out how to un-make it hardcoded, hihi.
        for (int i = 0; i < downlinks.size(); i++) {
            dummy++;
            Ip4Prefix ip = Ip4Prefix.valueOf("10.0.0." + String.valueOf(dummy) + "/32");

            log.info("ALARM ALARM ALARM: " + ip.toString());

            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(DefaultTrafficSelector.builder()
                            .matchIPDst(ip)
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

//        // adding the forwarding towards this group from the uplink
//        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
//                .withSelector(DefaultTrafficSelector.builder()
//                        .matchInPort(uplink)
//                        .build())
//                .withTreatment(DefaultTrafficTreatment.builder()
//                        .group(groupService.getGroup(aggregator.id(), groupKey).id())
//                        .build())
//                .withPriority(DEFAULT_PRIORITY)
//                .withFlag(ForwardingObjective.Flag.VERSATILE)
//                .fromApp(appId)
//                .makePermanent()
//                .add();
//
//        flowObjectiveService.forward(aggregator.id(), forwardingObjective);

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
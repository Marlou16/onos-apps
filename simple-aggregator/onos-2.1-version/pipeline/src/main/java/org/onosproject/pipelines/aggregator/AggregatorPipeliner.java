package org.onosproject.pipelines.aggregator;

import org.onosproject.net.DeviceId;
import org.onosproject.net.behaviour.NextGroup;
import org.onosproject.net.behaviour.Pipeliner;
import org.onosproject.net.behaviour.PipelinerContext;
import org.onosproject.net.driver.AbstractHandlerBehaviour;

import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.slf4j.Logger;

import java.util.List;

import static java.lang.Math.toIntExact;
import static org.slf4j.LoggerFactory.getLogger;

public class AggregatorPipeliner  extends AbstractHandlerBehaviour implements Pipeliner {

    private final Logger log = getLogger(getClass());

    private FlowRuleService flowRuleService;
    private DeviceId deviceId;

    @Override
    public void init(DeviceId deviceId, PipelinerContext context) {
        // wordt voor 'elk' device een Pipeliner geinitieerd?
        this.deviceId = deviceId;
        this.flowRuleService = context.directory().get(FlowRuleService.class);
    }

    @Override
    public void filter(FilteringObjective obj) {
        obj.context().ifPresent(c -> c.onError(obj, ObjectiveError.UNSUPPORTED));
    }

    @Override
    public void forward(ForwardingObjective obj) {
        log.info("Entering the forward method at the Pipeliner");
        if (obj.treatment() == null) {
            obj.context().ifPresent(c -> c.onError(obj, ObjectiveError.UNSUPPORTED));
        }

        // Regarding NextHopID;
        // It needs to be a key and an action, so we define the NID here and add it to an objective and treatment!

        // the NextHopID must be unique.. yeah or not off course when dodging duplicates
        // TODO Investigate where there is checked whether an entry does already exist (auto-magical?)
        Instruction instruction = obj.treatment().allInstructions().get(0);
        int NID = 0;
        switch (instruction.type()) {
            case OUTPUT:
                NID = toIntExact(((Instructions.OutputInstruction) instruction).port().toLong());
                break;
            case GROUP:
                NID = 44;   // TODO Can we use the "GroupId" or does this give unworkable values? Let's start simple
                break;
        }


        // Create the FlowRule for the IPV4_TABLE, having the IP_dest key and NID action;
        // Note that you already translate the treatment to a PI-variant, thus you can skip this at the Interpreter

        PiAction action = PiAction.builder()
                .withId(AggregatorConstants.SWITCH_INGRESS_IPV4_HANDLE)
                .withParameter(new PiActionParam(AggregatorConstants.NID, NID))
                .build();

        log.info("This is the PiAction for the IPV4_TABLE: " + action.toString());

        final FlowRule.Builder ipv4RuleBuilder = DefaultFlowRule.builder()
                .forTable(AggregatorConstants.SWITCH_INGRESS_IPV4_TAB)
                .forDevice(deviceId)
                .withSelector(obj.selector())
                .fromApp (obj.appId())
                .withPriority(obj.priority())
                .withTreatment(DefaultTrafficTreatment.builder().piTableAction(action).build());


        // create the Flowrule for the SET_OUT_PORT_TABLE, having the NID key and unicast/multicast action
        // Note that you set the NID in the selector, which now can be used as the key in the table (auto-magical)
        // We still need to make the unicast/multicast translation in the Interpreter (depends on objective.TYPE)

        PiCriterion criterion = PiCriterion.builder().
                matchExact(AggregatorConstants.HDR_MD_NEXTHOP_ID, NID).
                build();

        log.info("And this is the PiCriterion used as the selector for the OUTPORT TABLE: " + criterion.toString());

        final FlowRule.Builder outputRuleBuilder = DefaultFlowRule.builder()
                .forTable(AggregatorConstants.SWITCH_INGRESS_SET_OUT_PORT)
                .forDevice(deviceId)
                .withSelector(DefaultTrafficSelector.builder().matchPi(criterion).build())
                .fromApp (obj.appId())
                .withPriority(obj.priority())
                .withTreatment(obj.treatment());

        if (obj.permanent()) {
            ipv4RuleBuilder.makePermanent();
            outputRuleBuilder.makePermanent();
        } else {
            ipv4RuleBuilder.makeTemporary(obj.timeout());
            outputRuleBuilder.makeTemporary(obj.timeout());
        }

        switch (obj.op()) {
            case ADD:
                flowRuleService.applyFlowRules(ipv4RuleBuilder.build());
                flowRuleService.applyFlowRules(outputRuleBuilder.build());
                break;
            case REMOVE:
                flowRuleService.removeFlowRules(ipv4RuleBuilder.build());
                flowRuleService.applyFlowRules(outputRuleBuilder.build());
                break;
            default:
                log.warn("Unknown operation {}", obj.op());
        }

        obj.context().ifPresent(c -> c.onSuccess(obj));


    }

    @Override
    public void next(NextObjective obj) {

        log.info("We entered the next method in the Pipeliner, with the following objective" + obj.toString());

        obj.context().ifPresent(c -> c.onError(obj, ObjectiveError.UNSUPPORTED));
    }

    @Override
    public List<String> getNextMappings(NextGroup nextGroup) {
        // What is the goal of this method. When do we arrive here?
        // Because we are working with groups, so...
        return null;
    }
}

package org.onosproject.pipelines.aggregator;

import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flow.instructions.PiInstruction;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.net.pi.runtime.PiPacketOperation;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class AggregatorInterpreter extends AbstractHandlerBehaviour implements PiPipelineInterpreter {

    private final Logger log = getLogger(getClass());

    // OPTIONAL SHIT, SKIP!

    @Override
    public Optional<PiMatchFieldId> mapCriterionType(Criterion.Type type) {
        return Optional.empty();
    }

    @Override
    public Optional<Criterion.Type> mapPiMatchFieldId(PiMatchFieldId fieldId) {
        return Optional.empty();
    }

    @Override
    public Optional<PiTableId> mapFlowRuleTableId(int flowRuleTableId) {
        return Optional.empty();
    }

    @Override
    public Optional<Integer> mapPiTableId(PiTableId piTableId) {
        return Optional.empty();
    }


    // LET'S START WITH THIS, AY-OH LET'S GO!
    // implementation for when a simple forwarding rule

    // BTW, how (and where) do you check whether a particular entry does already exist?
    // because for the outport to the uplink we can use the same entry in the set_output_port table! BAM

    @Override
    public PiAction mapTreatment(TrafficTreatment treatment, PiTableId piTableId) throws PiInterpreterException {

        log.info("We are now in the mapTreatment method of the Interpreter");

        // Basic handling :)
        if (treatment.allInstructions().isEmpty()) {
            // No actions means drop.
            return PiAction.builder().withId(AggregatorConstants.SWITCH_INGRESS_DISCARD).build();
        } else if (treatment.allInstructions().size() > 1) {
            // We understand treatments with only 1 instruction.
            throw new PiInterpreterException("Treatment has multiple instructions");
        }


        Instruction instruction = treatment.allInstructions().get(0);
        log.info("This is the instruction: " + instruction.toString());

        // NOTE in this case the instruction type is PROTOCOL_INDEPENDENT
        // You could add this (kind of) check if you like it neat
        if (piTableId.equals(AggregatorConstants.SWITCH_INGRESS_IPV4_TAB)) {
            // in this case we know that the PiAction is hidden already in the treatment!
            // Because we did this in the Pipeliner! :)
            return (PiAction) ((PiInstruction) instruction).action();
        }

        if (piTableId.equals(AggregatorConstants.SWITCH_INGRESS_SET_OUT_PORT)) {
            switch (instruction.type()) {
                case OUTPUT:    // do unicast
                    return PiAction.builder()
                            .withId(AggregatorConstants.SWITCH_INGRESS_DO_UCAST)
                            .withParameter(new PiActionParam(AggregatorConstants.PORT,
                                                ((Instructions.OutputInstruction) instruction).port().toLong()))
                            .build();
                case GROUP:
                    return PiAction.builder()
                            .withId(AggregatorConstants.SWITCH_INGRESS_MGID_RID_HANDLE)
                            .withParameter(new PiActionParam(AggregatorConstants.OPERATOR_MGID, 44))
                            .withParameter(new PiActionParam(AggregatorConstants.OPERATOR_RID, 47))
                            .build();
                case NOACTION:
                    log.info("OMG, we reached this part of the lands! Abort, abort!");
                    return null;    // do we need to handle this?
            }
        }

        // maybe some nice logging to indicate something weird happened and shit is about to go down.
        log.info("This is an interesting case...");
        return null;
    }


    // THESE TWO WE DON'T NEED TO IMPLEMENT (YET) BECAUSE THE AGGREGATOR APP DOESN'T DO PACKET PROCESSING :)

    @Override
    public Collection<PiPacketOperation> mapOutboundPacket(OutboundPacket packet) throws PiInterpreterException {
        return null;
    }

    @Override
    public InboundPacket mapInboundPacket(PiPacketOperation packetOperation, DeviceId deviceId) throws PiInterpreterException {
        return null;
    }
}

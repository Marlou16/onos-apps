/*
 * Copyright 2017-present Open Networking Foundation
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

package org.onosproject.pipelines.aggregator;

import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiActionProfileId;
import org.onosproject.net.pi.model.PiPacketMetadataId;
import org.onosproject.net.pi.model.PiCounterId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiTableId;
/**
 * Constants for aggregator pipeline.
 */
public final class AggregatorConstants {

    // hide default constructor
    private AggregatorConstants() {
    }

    // Header field IDs
    public static final PiMatchFieldId HDR_HDR_IPV6_DST_ADDR =
            PiMatchFieldId.of("hdr.ipv6.dstAddr");
    public static final PiMatchFieldId HDR_HDR_VLAN_TAG_VID =
            PiMatchFieldId.of("hdr.vlan_tag.vid");
    public static final PiMatchFieldId HDR_MD_NEXTHOP_ID =
            PiMatchFieldId.of("md.nexthop_id");
    public static final PiMatchFieldId HDR_HDR_IPV4_DST_ADDR =
            PiMatchFieldId.of("hdr.ipv4.dstAddr");
    public static final PiMatchFieldId HDR_HDR_ETHERNET_DST_ADDR =
            PiMatchFieldId.of("hdr.ethernet.dstAddr");
    // Table IDs
    public static final PiTableId SWITCH_INGRESS_IPV6_TAB =
            PiTableId.of("SwitchIngress.ipv6_tab");
    public static final PiTableId SWITCH_INGRESS_SET_OUT_PORT =
            PiTableId.of("SwitchIngress.set_out_port");
    public static final PiTableId SWITCH_INGRESS_DROP_ALL =
            PiTableId.of("SwitchIngress.drop_all");
    public static final PiTableId SWITCH_INGRESS_IPV4_TAB =
            PiTableId.of("SwitchIngress.ipv4_tab");
    public static final PiTableId SWITCH_INGRESS_ETHERNET_TAB =
            PiTableId.of("SwitchIngress.ethernet_tab");
    // Action IDs
    public static final PiActionId SWITCH_INGRESS_DO_UCAST =
            PiActionId.of("SwitchIngress.do_ucast");
    public static final PiActionId SWITCH_INGRESS_IPV6_HANDLE =
            PiActionId.of("SwitchIngress.ipv6_handle");
    public static final PiActionId SWITCH_INGRESS_ETHERNET_HANDLE =
            PiActionId.of("SwitchIngress.ethernet_handle");
    public static final PiActionId NO_ACTION = PiActionId.of("NoAction");
    public static final PiActionId SWITCH_INGRESS_IPV4_HANDLE =
            PiActionId.of("SwitchIngress.ipv4_handle");
    public static final PiActionId SWITCH_INGRESS_DISCARD =
            PiActionId.of("SwitchIngress.discard");
    public static final PiActionId SWITCH_INGRESS_MGID_RID_HANDLE =
            PiActionId.of("SwitchIngress.mgid_rid_handle");
    // Action Param IDs
    public static final PiActionParamId OPERATOR_RID =
            PiActionParamId.of("operator_rid");
    public static final PiActionParamId PORT = PiActionParamId.of("port");
    public static final PiActionParamId NID = PiActionParamId.of("nid");
    public static final PiActionParamId OPERATOR_MGID =
            PiActionParamId.of("operator_mgid");
}
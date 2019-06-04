/* -*- P4_16 -*- */
//Wouter Miltenburg - KPN

#include <core.p4>
#if __TARGET_TOFINO__ == 2
#include <t2na.p4>
#else
#include <tna.p4>
#endif

#include "inc16/headers.p4"
#include "inc16/util.p4"

struct l3_meta_t {
    bit<16> nexthop_id;
    bit<1>  ipv4_v6_match;
}



// ---------------------------------------------------------------------------
// Ingress parser
// ---------------------------------------------------------------------------
parser SwitchIngressParser(
        packet_in pkt,
        out header_t hdr,
        out l3_meta_t ig_md,
        out ingress_intrinsic_metadata_t ig_intr_md) {

    TofinoIngressParser() tofino_parser;

    state start {
        tofino_parser.apply(pkt, ig_intr_md);
        transition parse_ethernet;
    }

    state parse_ethernet {
        pkt.extract(hdr.ethernet);
        transition select(hdr.ethernet.etherType) {
            0x8100 : parse_vlan_tag;
            0x0800 : parse_ipv4;
            0x86DD : parse_ipv6;
            default : reject;
        }
    }

    state parse_vlan_tag {
        pkt.extract(hdr.vlan_tag);
        transition select(hdr.vlan_tag.etherType) {
            0x0800 : parse_ipv4;
            0x86DD : parse_ipv6;
            default : reject;
        }
    }

    state parse_ipv4 {
        pkt.extract(hdr.ipv4);
        transition accept;
    }

    state parse_ipv6 {
        pkt.extract(hdr.ipv6);
        transition accept;
    }

}

control SwitchIngress(
        inout header_t hdr,
        inout l3_meta_t md,
        in ingress_intrinsic_metadata_t ig_md,
        in ingress_intrinsic_metadata_from_parser_t ig_prsr_md,
        inout ingress_intrinsic_metadata_for_deparser_t ig_dprsr_md,
        inout ingress_intrinsic_metadata_for_tm_t ig_tm_md){

    action discard() {
        ig_dprsr_md.drop_ctl = 0x1; // Drop packet.
        exit;
    }

    action ethernet_handle(bit<16> nid) {
        md.nexthop_id = nid;
    }

    action ipv4_handle(bit<16> nid) {
        md.nexthop_id = nid;
        md.ipv4_v6_match = 1;
    }

    action ipv6_handle(bit<16> nid) {
        md.nexthop_id = nid;
        md.ipv4_v6_match = 1;
    }

    action do_ucast(bit<9> port) {
        ig_tm_md.ucast_egress_port = port;
    }

    action mgid_rid_handle(bit<16> operator_mgid, bit<16> operator_rid){
        ig_tm_md.mcast_grp_a = operator_mgid;
        ig_tm_md.rid = operator_rid;
    }

    table drop_all {
        actions = {
            discard;
        }
        size = 1;
        default_action = discard();
    }
    table ethernet_tab {
        actions = {
            ethernet_handle;
            discard;
        }
        key = {
            hdr.ethernet.dstAddr: exact;
            hdr.vlan_tag.vid : exact;
        }
        default_action = discard();
    }
    table ipv4_tab {
        actions = {
            discard;
            ipv4_handle;
        }
        key = {
            hdr.ipv4.dstAddr: exact;
        }
    }
    table ipv6_tab {
        actions = {
            discard;
            ipv6_handle;
        }
        key = {
            hdr.ipv6.dstAddr: exact;
        }
    }
    table set_out_port {
        actions = {
            do_ucast;
            mgid_rid_handle;
            discard;
        }
        key = {
            md.nexthop_id: exact;
        }
    }
    apply {
        if (hdr.ipv4.isValid()) {
            ipv4_tab.apply();
        }
        else {
            if (hdr.ipv6.isValid()) {
                ipv6_tab.apply();
            }
            else {
                drop_all.apply();
            }
        }
        if (md.ipv4_v6_match == 0) {
            ethernet_tab.apply();
        }
        set_out_port.apply();
        ig_tm_md.bypass_egress = true;
    }
}

control SwitchIngressDeparser(
        packet_out pkt,
        inout header_t hdr,
        in l3_meta_t ig_md,
        in ingress_intrinsic_metadata_for_deparser_t ig_intr_dprsr_md) {
        apply{
          pkt.emit(hdr);
        }
}


Pipeline(SwitchIngressParser(),
         SwitchIngress(),
         SwitchIngressDeparser(),
         EmptyEgressParser<header_t, l3_meta_t>(),
         EmptyEgress<header_t, l3_meta_t>(),
         EmptyEgressDeparser<header_t, l3_meta_t>()) pipe;

Switch(pipe) main;

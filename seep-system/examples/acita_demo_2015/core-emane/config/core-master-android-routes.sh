#!/bin/bash
#Master route changes.
CORE_ETH="eth0"
VMCORE_ETH="eth1"

MASTER_ANDROID_NET="193.168.56.0/24"
MASTER_VMCORE_VETH_IP="10.0.7.11"

# Master 
ip route add $MASTER_ANDROID_NET via $MASTER_VMCORE_VETH_IP dev $VMCORE_ETH

WORKER1_ANDROID_NET="193.168.129.0/24"
WORKER1_CORE_IP="10.0.0.10"

ip route add $WORKER1_ANDROID_NET via $WORKER1_CORE_IP dev $CORE_ETH 

WORKER2_ANDROID_NET="193.168.XXX.0/24"
WORKER2_CORE_IP="10.0.0.11"

ip route add $WORKER2_ANDROID_NET via $WORKER2_CORE_IP dev $CORE_ETH 

WORKER3_ANDROID_NET="193.168.XXX.0/24"
WORKER3_CORE_IP="10.0.0.12"

ip route add $WORKER3_ANDROID_NET via $WORKER3_CORE_IP dev $CORE_ETH 

WORKER4_ANDROID_NET="193.168.XXX.0/24"
WORKER4_CORE_IP="10.0.0.13"

ip route add $WORKER4_ANDROID_NET via $WORKER4_CORE_IP dev $CORE_ETH 

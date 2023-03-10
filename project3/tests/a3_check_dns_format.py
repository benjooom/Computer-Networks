
import sys
sys.path.append('.')

from sim.basics import BasicHost, RoutingUpdate, DNSPayload
from sim.api import *
from dv_router import DVRouter as RIPRouter
import sim.topo as topo
import os
import time
from dns_resolver import *


g_checker_dns_request = False


class ValueTester:
    def __init__(self, expected=""):
        self.expected = expected

    def set_expected(self, expected):
        self.expected = expected


class DNSResTester(UDPHost, ValueTester):
    def __init__(self):
        super().__init__()
        self.is_support_recursive = False

    def handle_dns(self, packet: DNSPayload):
        if type(packet) == DNSPayload:
            print("Received DNS packet: " + str(packet))
            if packet.response == self.expected:
                print("> DNS response checked")
                if g_checker_dns_request:
                    print("Test complete")
                    print("Print trace: " + str(packet.trace))
                    os._exit(0)
                else:
                    os._exit(50)
            else:
                print(packet)
        else:
            print("Handle non-DNS packet: " + str(packet))
        pass


class DNSReqTester(DNSServer, ValueTester):
    def __init__(self):
        super().__init__()

    def handle_dns(self, packet: DNSPayload):
        if type(packet) == DNSPayload:
            print("Received DNS packet: " + str(packet))
            if packet.request == self.expected:
                print("> DNS request checked")
                global g_checker_dns_request
                g_checker_dns_request = True
        else:
            print("Handle non-DNS packet: " + str(packet))
        super().handle_dns(packet)


def check_dns_list(res, expected_domain):
    if not isinstance(res, list):
        print("Wrong format:", str(res))
        os._exit(-1)
    if len(res) != len(expected_domain):
        print("Unmatched!")
        os._exit(-1)
    for idx in range(len(expected_domain)):
        if expected_domain[idx] not in res:
            print("No entry found for ", str(expected_domain[idx]))
            os._exit(-1)


def create (switch_type = RIPRouter):
    """
    Creates a topology with loops that looks like:
     s1 - s2 - s3 - s4 - s5
     |    |    |    |    |
    h1   h2   h3   h4   h5
    """

    switch_type.create('s1')
    switch_type.create('s2')
    switch_type.create('s3')
    switch_type.create('s4')
    switch_type.create('s5')
    DNSResTester.create('h1', ip_addr="10.10.10.101")
    DNSResolver.create('h2', ip_addr="10.10.10.102")
    DNSServer.create('h3', ip_addr="10.10.10.103")
    DNSServer.create('h4', ip_addr="10.10.10.104")
    DNSReqTester.create('h5', ip_addr="10.10.10.105")

    topo.link(s1, s2)
    topo.link(s2, s3)
    topo.link(s3, s4)
    topo.link(s4, s5)
    topo.link(s1, h1)
    topo.link(s2, h2)
    topo.link(s3, h3)
    topo.link(s4, h4)
    topo.link(s5, h5)


import sim.core
from dv_router import DVRouter as switch

import sim.api as api
import logging
api.simlog.setLevel(logging.DEBUG)
api.userlog.setLevel(logging.DEBUG)

#_DISABLE_CONSOLE_LOG = True
create(switch)
# Add DNS entry
h2_domain = "com"
h3_domain = "domain.com"
h4_domain = "example.domain.com"
h5_host = "example.domain.com"
h5_address = "192.168.100.1"
dummy_host = "dummy.domain.com"
h2.add_dns_server(h2_domain, "10.10.10.103")     # h2 -> h3
h3.add_dns_server(h3_domain, "10.10.10.104")     # h3 -> h4
h4.add_dns_server(h4_domain, "10.10.10.105")     # h4 -> h5
h5.add_dns(h5_host, h5_address, 5.)              # h5 has the host address
h5.add_dns(dummy_host, "192.168.100.2", 5.)      # h5 has dummy address as well
start = sim.core.simulate
start()
h5.set_expected(h5_host)                       # expected request at the final DNS server
h1.set_expected([h5_address])
time.sleep(15)
h1.send_data_to_addr(DNSPayload(opcode=DNSPayload.ReqStr, request=h5_host), "10.10.10.102")
print("DNS DATA SENT")
time.sleep(60)
print("timeout")
os._exit(50)

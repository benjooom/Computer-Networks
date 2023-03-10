
import sys
sys.path.append('.')

from sim.basics import BasicHost, RoutingUpdate, DNSPayload
from sim.api import *
from dv_router import DVRouter as RIPRouter
import sim.topo as topo
import os
import time
from dns_server import *


class DNSReceiver(DNSServer):
    def __init__(self):
        UDPHost.__init__(self)
        self.is_support_recursive = False

    def handle_dns(self, packet):
        print("Handle DNS packet: " + str(packet))
        os._exit(0)
        pass


def create (switch_type = RIPRouter):
    """
    Creates a topology with loops that looks like:
     s1 - s2 - s3
     |    |    |
    h1   h2   h3
    """

    switch_type.create('s1')
    switch_type.create('s2')
    switch_type.create('s3')
    DNSReceiver.create('h1', ip_addr="10.10.10.101")
    DNSServer.create('h2', ip_addr="10.10.10.102")
    DNSServer.create('h3', ip_addr="10.10.10.103")

    topo.link(s1, s2)
    topo.link(s2, s3)
    topo.link(s1, h1)
    topo.link(s2, h2)
    topo.link(s3, h3)


def check_dns_list(res, expected_domain):
    if not isinstance(res, list):
        print("Wrong format:", str(res))
        os._exit(50)
    if len(res) != len(expected_domain):
        print("Unmatched! %s <-> %s" % (str(res), str(expected_domain)))
        os._exit(50)
    for idx in range(len(expected_domain)):
        if expected_domain[idx] not in res:
            print("No entry found for ", str(expected_domain[idx]))
            os._exit(50)


import sim.core
from dv_router import DVRouter as switch

import sim.api as api
import logging
api.simlog.setLevel(logging.DEBUG)
api.userlog.setLevel(logging.DEBUG)

#_DISABLE_CONSOLE_LOG = True
create(switch)
# Add DNS entry
h2_domain = "domain.com"
h2_address = "10.10.10.103"
h3_host = "example.domain.com"
h3_address = "192.168.100.1"
dummy_host = "dummy.domain.com"

# 1) ADD TEST
h2.add_dns_server(h2_domain, h2_address)        # h2 -> h3
h3.add_dns(h3_host, h3_address, 5.)             # h3 has the host address
h3.add_dns(dummy_host, "192.168.100.2", 5.)     # h3 has dummy address as well
h2_res = h2.get_dns(h2_domain)                  # DNS server record
h3_res = h3.get_dns(h3_host)                    # DNS host record
check_dns_list(h2_res, [h2_address])
check_dns_list(h3_res, [h3_address])
time.sleep(5)

# 2) DEL TEST
h3.del_dns(h3_host)
time.sleep(15)                                  # to make sure the cache is invalidated
h2_res = h2.get_dns(h2_domain)
h3_res = h3.get_dns(h3_host)
check_dns_list(h2_res, [h2_address])            # should have the same record
check_dns_list(h3_res, [])                      # should be removed
print("Test complete")
os._exit(0)

from sim.basics import *
from sim.core import GetEntryFromIpAddr
import logging

simlog = logging.getLogger("simulator")

class UDPHost(BasicHost):
    def __init__(self):
        pass

    def send_data_to_addr(self, packet: Packet, ip_addr: str):
        dst_entry = GetEntryFromIpAddr(ip_addr)
        if dst_entry is not None:
            packet.dst = dst_entry
        packet.src = self
        self.send_data(packet)

    def handle_rx (self, packet: Packet, port: int):
        """
        Silently drops messages to nobody.
        Calls DNSServer's handler
        """
        if packet.dst is NullAddress:
            # Silently drop messages not to anyone in particular
            return

        trace = ','.join((s.name for s in packet.trace))
        if packet.dst is self:
            # simlog.info("DNSHost rx: %s %s" % (packet, trace))
            if hasattr(self, "handle_dns") and type(packet) == DNSPayload:
                simlog.info(str(self))
                self.handle_dns(packet)

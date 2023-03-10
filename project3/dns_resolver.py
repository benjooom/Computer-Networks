from dns_server import *

'''
Create your DNS server in this file.
'''
class DNSResolver(DNSServer):
    def __init__(self):
        super().__init__()
        self.is_support_recursive = True

    def handle_dns(self, packet: DNSPayload):
        super().handle_dns(packet)

    def set_recursive(self, is_support_recursive):
        pass

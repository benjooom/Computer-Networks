from struct import pack
from sim.api import *
from sim.basics import *
from udp_host import *


'''
Create your DNS server in this file.
'''
class DNSServer(UDPHost):
    def __init__(self):
        self.is_support_recursive = False

        # add more local variables if needed
        self.A_types = {} #DNS records, domain name to address; e.g. "example.com" -> NS_types = { "com":[ ["1.0.1", 5], {"example": [["1.0.1.1", 5], {}]} ]}
        self.NS_types = {} #DNS name servers; e.g. "example.com" -> NS_types = { "com":[ {"1.0.1"}, {"example": [{"1.0.1.1"}, {}]} ]}
        self.cache_types = {} #DNS cache

        self.return_ips = {} #fromat: {id(packet.request): [src1, src2, src3, ...]}
        self.search_DNS = {} #format: {id(packet.request): []}
        self.searched_DNS = {} #format: {id(packet.request): []}

        self.cache_pos = {} #format: {"domain": (A-type record, ttl)}
        self.cache_neg = {} #format: {"domain": ttl}

    def check_A_records(self, packet: DNSPayload):
        return_type_A = self.get_dns_A(packet.request, "PERFECT")
        if(len(return_type_A) > 0): #Found the host!
            packet.opcode = DNSPayload.ResFoundHostStr
            packet.response = [return_type_A[0]]
            packet.dns_ttl = (time.time()) + return_type_A[1]
            return 1
        else:
            return 0

    def check_cache_records(self, packet: DNSPayload):
        if(packet.request in self.cache_pos): #Found positive cache!
            if(self.cache_pos[packet.request][1] > (time.time())):
                packet.opcode = DNSPayload.ResFoundHostStr
                packet.response = self.cache_pos[packet.request][0]
                packet.dns_ttl = self.cache_pos[packet.request][1]
                return 1

        if(packet.request in self.cache_neg): #Found negative cache!
            if(self.cache_neg[packet.request] > (time.time())):
                packet.opcode = DNSPayload.ResNotFoundStr
                packet.response = []
                self.cache_neg[packet.request] = (time.time()) + 60 #CACHE!
                return 1

        #Nothing in cache
        return 0

    def check_NS_records(self, packet: DNSPayload):
        return_type_NS = self.get_dns_NS(packet.request)
        if(len(return_type_NS) > 0): #Found the DNS servers!
            packet.opcode = DNSPayload.ResFoundDNSStr
            packet.response = return_type_NS
            return 1
        else:
            return 0

    def handle_dns(self, packet: DNSPayload):
        if(self.is_support_recursive): #Support recursive DNS lookup
            #Clean caches first below!
            for key in self.cache_pos: #Check positive records
                if((time.time()) > self.cache_pos[key][1]):
                    self.cache_pos[key][1] = 0
            for key in self.cache_neg: #Check negative records
                if((time.time()) > self.cache_neg[key]):
                    self.cache_neg[key] = 0

            if(packet.opcode == DNSPayload.ReqStr): #Request to a recursive search
                if(id(packet.request) in self.return_ips.keys()): #Keep track of the return ip so that we know how to forward packets
                    self.return_ips[id(packet.request)].append(packet.src.ip_addr)
                else:
                    self.return_ips[id(packet.request)] = [packet.src.ip_addr]

                if(self.check_A_records(packet) > 0): #Are there A-type records present, if so return
                    self.send_dns_data(packet, self.return_ips[id(packet.request)].pop())
                    if(self.return_ips[id(packet.request)] == []):
                        del self.return_ips[id(packet.request)]
                elif(self.check_cache_records(packet) > 0): #Are there cache records present, if so return
                    self.send_dns_data(packet, self.return_ips[id(packet.request)].pop())
                    if(self.return_ips[id(packet.request)] == []):
                        del self.return_ips[id(packet.request)]
                else: #If not, perform recursive search
                    if(len(self.return_ips[id(packet.request)]) == 1):
                        self.search_DNS[id(packet.request)] = self.get_dns_NS(packet.request)
                        self.searched_DNS[id(packet.request)] = []
                    if(len(self.search_DNS[id(packet.request)]) > 0): #There exist DNS records!
                        temp_ip = self.search_DNS[id(packet.request)].pop()
                        self.searched_DNS[id(packet.request)].append(temp_ip)
                        self.send_dns_data(packet, temp_ip)
                    else: #Reply that nothing was found otherwise
                        packet.opcode = DNSPayload.ResNotFoundStr
                        packet.response = []
                        self.cache_neg[packet.request] = (time.time()) + 60 #CACHE!
                        self.send_dns_data(packet, self.return_ips[id(packet.request)].pop())
            elif(packet.opcode == DNSPayload.ResNotFoundStr): #From recursive searches, nothing found!
                if(self.search_DNS[id(packet.request)] == []):
                    packet.response = []
                    self.cache_neg[packet.request] = (time.time()) + 60 #CACHE!
                    self.send_dns_data(packet, self.return_ips[id(packet.request)].pop())
                    if(self.return_ips[id(packet.request)] == []):
                        del self.return_ips[id(packet.request)]
                else:
                    packet.opcode = DNSPayload.ReqStr
                    temp_ip = self.search_DNS[id(packet.request)].pop()
                    self.searched_DNS[id(packet.request)].append(temp_ip)
                    self.send_dns_data(packet, temp_ip)
            elif(packet.opcode == DNSPayload.ResFoundHostStr): #Somone somewhere found the host!
                self.cache_pos[packet.request] = [packet.response, packet.dns_ttl] #CACHE!
                self.send_dns_data(packet, self.return_ips[id(packet.request)].pop())
                if(self.return_ips[id(packet.request)] == []):
                    del self.return_ips[id(packet.request)]
            elif(packet.opcode == DNSPayload.ResFoundDNSStr): #There were more DNS servers found
                new_DNS_found = list(set(packet.response).difference(set(self.search_DNS[id(packet.request)] + self.searched_DNS[id(packet.request)])))
                self.search_DNS[id(packet.request)] += new_DNS_found
                if(self.search_DNS[id(packet.request)] == []): #No more searching possible, cache negative
                    packet.opcode = DNSPayload.ResNotFoundStr
                    packet.response = []
                    self.cache_neg[packet.request] = (time.time()) + 60 #CACHE!
                    self.send_dns_data(packet, self.return_ips[id(packet.request)].pop())
                    if(self.return_ips[id(packet.request)] == []):
                        del self.return_ips[id(packet.request)]
                else:
                    packet.opcode = DNSPayload.ReqStr
                    temp_ip = self.search_DNS[id(packet.request)].pop()
                    self.searched_DNS[id(packet.request)].append(temp_ip)
                    self.send_dns_data(packet, temp_ip)
        else: #Iterative DNS lookup
            if(packet.opcode == DNSPayload.ReqStr): #Check if we can find the address in our A-type records
                if(self.check_A_records(packet) > 0):
                    self.send_dns_data(packet, packet.src.ip_addr)
                else: #If not, check NS-type records
                    if(self.check_NS_records(packet) > 0): #Send NS-type records back if we found any
                        self.send_dns_data(packet, packet.src.ip_addr)
                    else: #Reply that nothing was found otherwise
                        packet.opcode = DNSPayload.ResNotFoundStr
                        packet.response = []
                        self.send_dns_data(packet, packet.src.ip_addr)

    def send_dns_data(self, packet: DNSPayload, address):
        self.send_data_to_addr(packet, address)

    def add_dns(self, domain, address, dns_ttl):
        split_domain = domain.split('.') #Split the domain!
        node = self.A_types

        #Loop through hierarhy to get to matching node, or leaf if new entry
        for i in range(len(split_domain)-1, 0, -1):
            if(split_domain[i] in node):
                node = node[split_domain[i]][1]
            else:
                new_subtree = {}
                new_data = []
                node[split_domain[i]] = (new_data, new_subtree)
                node = node[split_domain[i]][1]

        #If the domain already exists in hierarchy, replace the entry
        if(split_domain[0] in node):
            node[split_domain[0]][0][0] = address
            node[split_domain[0]][0][1] = dns_ttl
        else:
            new_subtree = {}
            new_data = [address, dns_ttl]
            node[split_domain[0]] = (new_data, new_subtree)

    def del_dns(self, domain):
        split_domain = domain.split('.') #Split the domain!
        node = self.A_types

        #Loop through hierarhy to get to matching node
        for i in range(len(split_domain)-1, 0, -1):
            if(split_domain[i] in node):
                node = node[split_domain[i]][1]
            else: #No match possible
                return

        #If the domain is in the hierarchy... remove it
        if(split_domain[0] in node):
            del node[split_domain[0]][0][0]
            del node[split_domain[0]][0][0]


    def add_dns_server(self, domain, address):
        split_domain = domain.split('.') #Split the domain!
        node = self.NS_types

        #Loop through hierarhy to get to matching node, or leaf if new entry
        for i in range(len(split_domain)-1, 0, -1):
            if(split_domain[i] in node):
                node = node[split_domain[i]][1]
            else:
                new_subtree = {}
                new_addresses = set()
                node[split_domain[i]] = (new_addresses, new_subtree)
                node = node[split_domain[i]][1]

        #If the domain already exists in hierarchy, add the DNS address
        if(split_domain[0] in node):
            node[split_domain[0]][0].add(address)
        else:
            new_subtree = {}
            new_addresses = {address}
            node[split_domain[0]] = (new_addresses, new_subtree)


    def del_dns_server(self, domain, address):
        split_domain = domain.split('.') #Split the domain!
        node = self.NS_types

        #Loop through hierarhy to get to matching node
        for i in range(len(split_domain)-1, 0, -1):
            if(split_domain[i] in node):
                node = node[split_domain[i]][1]
            else: #No match possible
                return

        #If the domain, address pair is in the hierarchy... remove it
        if(split_domain[0] in node):
            if(address in node[split_domain[0]][0]):
                node[split_domain[0]][0].remove(address)

    def get_dns(self, domain):
        #Check A-type records
        A_return_addresses = self.get_dns_A(domain, "ALL")
        #Check NS-type records
        NS_return_addresses = self.get_dns_NS(domain)
        #Check cache!
        cached_return_addresses = self.get_cached_records(domain)
        return (A_return_addresses + NS_return_addresses + cached_return_addresses)

    def set_recursive(self, is_support_recursive):
        self.is_support_recursive = is_support_recursive

    def get_cached_records(self, domain):
        return_addresses = []
        for key in self.cache_pos:
            if(key in domain):
                if((time.time()) > self.cache_pos[key][1]):
                    self.cache_pos[key][1] = 0
                else:
                    return_addresses += self.cache_pos[key][0]
        return return_addresses

    def get_dns_A(self, domain, return_type):
        split_domain = domain.split('.') #Split the domain!
        return_addresses = []
        node = self.A_types
        if(return_type == "ALL"): #Find all matching type A records
            for i in range(len(split_domain)-1, -1, -1):
                if(split_domain[i] in node):
                    if(len(node[split_domain[i]][0]) != 0):
                        return_addresses.append(node[split_domain[i]][0][0])
                    node = node[split_domain[i]][1]
        elif(return_type == "PERFECT"): #Find only the perfectly matching type A record
            for i in range(len(split_domain)-1, 0, -1):
                if(split_domain[i] in node):
                    node = node[split_domain[i]][1]

            if(split_domain[0] in node):
                if(len(node[split_domain[0]][0]) != 0):
                        return_addresses.append(node[split_domain[0]][0][0])
                        return_addresses.append(node[split_domain[0]][0][1])
        return return_addresses

    def get_dns_NS(self, domain):
        split_domain = domain.split('.') #Split the domain!
        return_addresses = []

        node = self.NS_types
        for i in range(len(split_domain)-1, -1, -1): #Find all matching DNS servers
            if(split_domain[i] in node):
                return_addresses += list(node[split_domain[i]][0])
                node = node[split_domain[i]][1]

        return return_addresses

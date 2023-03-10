from sim.api import *
from sim.basics import *

'''
Create your distance vector router in this file.
'''
infty = float('inf')

class DVRouter (Entity):
    def __init__(self):
        # Add your code here!
        self.port_forward = {} #Create a routing table a dict = {destination_router: [length, next_hop, exit_port]}
        self.port_forward[self] = [0, NullAddress, None] #Establish the self router as a destination of 0
        self.neighbor_links = {} #Create a dictionary with the direct distance to neighbor links!

    def handle_rx (self, packet, port):
        # Add your code here!
        if(isinstance(packet, DiscoveryPacket)): #This is a discovery packet
            if(packet.is_link_up): #If the link is up, then add the packet with latency and propogate info to neighbors
                self.neighbor_links[packet.src] = packet.latency #Add neighbor
                if packet.src in self.port_forward: #If the a path to dest exists by some other means, check if direct link is faster
                    if (self.port_forward[packet.src][0] > packet.latency):
                        self.port_forward[packet.src] = [packet.latency, packet.src, port]
                else:
                    self.port_forward[packet.src] = [packet.latency, packet.src, port]
                update_packet = RoutingUpdate() #Send all routing options through self to new link
                for entry in self.port_forward:
                    if(entry != self):
                        update_packet.add_destination(entry, self.port_forward[entry][0])
                self.send(update_packet, flood=True)
            else: #If the link is down, set the distance to it (and any path that uses it) to infinity and propogate that info to others (poison reverse)
                changes = [packet.src] #So we know which changes to forward table to send to other nodes
                self.neighbor_links[packet.src] = infty #Set the direct link to infty
                self.port_forward[packet.src] = [infty, NullAddress, port]
                for entry in self.port_forward: #Eliminate any path using the downed link as a next hop
                    if(packet.src == self.port_forward[entry][1]):
                        self.port_forward[entry][0] = infty
                        self.port_forward[entry][1] = NullAddress
                        self.port_forward[entry][2] = port
                        changes.append(entry)
                update_packet = RoutingUpdate() #Send changed paths ONLY to neighbors
                for change in changes:
                    update_packet.add_destination(change, self.port_forward[change][0])
                self.send(update_packet, flood=True)

        elif(isinstance(packet, RoutingUpdate)): #This is a routing update packet
            updates = packet.all_dests() #Get the list of updates
            changes = [] #So we know which changes to forward table to send to other nodes
            for update in updates:
                if update in self.port_forward:
                    if(self.port_forward[update][0] > (packet.get_distance(update) + self.neighbor_links[packet.src])): #Curr distance is greater than advertised one
                        self.port_forward[update][0] = packet.get_distance(update) + self.neighbor_links[packet.src]
                        self.port_forward[update][1] = packet.src
                        self.port_forward[update][2] = port
                        changes.append(update)
                    elif(self.port_forward[update][0] == (packet.get_distance(update) + self.neighbor_links[packet.src])): #Distances are equal, pick lower port
                        if(self.port_forward[update][2] > port):
                            self.port_forward[update][1] = packet.src
                            self.port_forward[update][2] = port
                            changes.append(update)
                    else: #Otherwise, probably about a link down... lets deal with that:
                        if(packet.get_distance(update) == infty):
                            changes.append(update)
                            for entry in self.port_forward:
                                if((entry == update) & (self.port_forward[entry][1] == packet.src)):
                                    self.port_forward[entry] = [infty, NullAddress, port]
                                    changes.append(entry)
                else: #The update is not in our table, so lets add it
                    self.port_forward[update] = [(packet.get_distance(update) + self.neighbor_links[packet.src]), packet.src, port]
                    changes.append(update)
                #Share ONLY the newfound better paths to neighbors, not any path that is unchanged
                update_packet = RoutingUpdate()
                for change in changes:
                    update_packet.add_destination(change, self.port_forward[change][0])
                self.send(update_packet, flood=True)

        else: #This is a normal packet, send it to the next hop in the shortest distance path to dest... drop packet if the path to dest is infty or if dest is self
            if((packet.dst != self) & (self.port_forward[packet.dst][0] != infty)):
                self.send(packet,self.port_forward[packet.dst][2])

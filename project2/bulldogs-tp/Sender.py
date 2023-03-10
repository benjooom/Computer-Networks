import base64
from ctypes import sizeof
from posixpath import split
import sys
import getopt

import Checksum
import BasicSender

'''
This is a skeleton sender class. Create a fantastic transport protocol here.
'''


class Sender(BasicSender.BasicSender):
    def __init__(self, dest, port, filename, debug=False, sackMode=False):
        super(Sender, self).__init__(dest, port, filename, debug)

        self.seqno = 0 #Start packet sequence at 0
        self.msg_type = None #Message type initial none
        self.end_confirmed = 0 #If we reached the end of the data or not
        self.sending = 1 #If we need to keep sending or not
        self.packet_window = [] #Initiate Packet window, format: [[seqno, packet]]
        self.current_ack = 0
        self.send_active = 1
        self.dup_ack = 0
        self.current_timer = 0
        self.sack_window = []

        if sackMode:
            pass
            #raise NotImplementedError  # remove this line when you implement SACK

    # Main sending loop.
    def start(self):
        msg = self.infile.read(1450) #Read first bytes of file
        msg = base64.b64encode(msg)
        msg = msg.decode()
        self.msg_type = 'start' #Seqno is 0 so this a start type packet
        packet = self.make_packet(self.msg_type, self.seqno, msg) #Make the packet

        while self.sending:
            if(self.seqno == 0):
                self.send(packet) #Send the packet
                response = self.receive(0.001) #Wait 500ms to receive packet
                if(response != None and Checksum.validate_checksum(response)): #Check that we received a packet and that its not corrupt
                    self.seqno += 1
            else:
                if(self.send_active):
                    self.send_packets()
                response = self.receive(0.001) #Wait 1ms to receive packet
                if(response != None):
                    if(sackMode):
                        self.handle_new_sack(response)
                    else:
                        self.handle_new_ack(response)
                else:
                    if(self.current_timer < 50):
                        self.current_timer += 1
                        self.send_active = 0
                    else:
                        self.current_timer = 0
                        self.send_active = 1

        self.infile.close()

    def send_packets(self):
        if(not self.end_confirmed):
            if(len(self.packet_window) == 5):
                for i in range(len(self.packet_window)):
                    if(sackMode):
                        if(self.sack_window[i] == 0):
                            self.send(self.packet_window[i][1]) #Send the packet
                    else:
                        self.send(self.packet_window[i][1]) #Send the packet
            while(len(self.packet_window) < 5):
                msg = self.infile.read(1450)
                msg = base64.b64encode(msg)
                msg = msg.decode()

                if (msg == ""):
                    self.msg_type = 'end'
                    self.end_confirmed = 1
                    packet = self.make_packet(self.msg_type, self.seqno, msg) #Make the packet
                    self.packet_window.append([self.seqno, packet])
                    self.sack_window.append(0)
                    self.seqno += 1
                    break
                else:
                    self.msg_type = 'data'
                    packet = self.make_packet(self.msg_type, self.seqno, msg) #Make the packet
                    self.packet_window.append([self.seqno, packet])
                    self.sack_window.append(0)
                    self.seqno += 1
                self.send(packet)
            return
        else:
            if(len(self.packet_window) == 0):
                self.sending = 0

        for i in range(len(self.packet_window)):
            if(sackMode):
                if(self.sack_window[i] == 0):
                    self.send(self.packet_window[i][1]) #Send the packet
            else:
                self.send(self.packet_window[i][1]) #Send the packet

    def handle_new_ack(self, ack):
        if(Checksum.validate_checksum(ack)):
            split_ack = self.split_packet(ack)
            if(split_ack[0] != 'ack'):
                self.send_active = 0
                return
            elif((len(self.packet_window) > 0) and (self.packet_window[-1][0] < (int(split_ack[1]) - 1))):
                self.send_active = 0
                return
            elif(len(split_ack) != 4):
                self.send_active = 0
                return

            if(self.current_ack < int(split_ack[1])):
                self.current_ack = int(split_ack[1])
                self.dup_ack = 1
                self.send_active = 1
            elif(self.current_ack == int(split_ack[1])):
                self.handle_dup_ack()
                return
            else:
                self.send_active = 0

            while(len(self.packet_window) > 0 and self.packet_window[0][0] < int(split_ack[1])):
                self.packet_window.pop(0)[0]

    def handle_new_sack(self, ack):
        if(Checksum.validate_checksum(ack)):
            split_sack = self.split_packet(ack)
            split_ack = split_sack[1].split(';')
            sub_split_ack = split_ack[1].split(',')
            for i in range(len(sub_split_ack)):
                if(sub_split_ack[i] == ''):
                    sub_split_ack[i] = 0
                else:
                    sub_split_ack[i] = int(sub_split_ack[i])

            if(split_sack[0] != 'sack'):
                self.send_active = 0
                return
            elif((len(self.packet_window) > 0) and (self.packet_window[-1][0] < (int(split_ack[0]) - 1))):
                self.send_active = 0
                return
            elif(len(split_sack) != 4):
                self.send_active = 0
                return

            for i in range(1,len(sub_split_ack)):
                for j in range(len(self.packet_window)):
                    if(self.packet_window[j][0] == sub_split_ack[i]):
                        self.sack_window[j] = 1

            if(self.current_ack < int(split_ack[0])):
                self.current_ack = int(split_ack[0])
                self.dup_ack = 1
                self.send_active = 1
            elif(self.current_ack == int(split_ack[0])):
                self.handle_dup_ack()
                return
            else:
                self.send_active = 0

            while(len(self.packet_window) > 0 and self.packet_window[0][0] < int(split_ack[0])):
                self.packet_window.pop(0)
                self.sack_window.pop(0)

    def handle_dup_ack(self):
        self.dup_ack += 1
        self.send_active = 0
        if(self.dup_ack > 3):
            self.dup_ack = 0
            self.current_timer = 0
            self.send(self.packet_window[0][1]) #Send the packet lost

    def log(self, msg):
        if self.debug:
            print(msg)


'''
This will be run if you run this script from the command line. You should not
change any of this; the grader may rely on the behavior here to test your
submission.
'''
if __name__ == "__main__":
    def usage():
        print("BULLDOGS-TP Sender")
        print("-f FILE | --file=FILE The file to transfer; if empty reads from STDIN")
        print("-p PORT | --port=PORT The destination port, defaults to 33122")
        print("-a ADDRESS | --address=ADDRESS The receiver address or hostname, defaults to localhost")
        print("-d | --debug Print debug messages")
        print("-h | --help Print this usage message")
        print("-k | --sack Enable selective acknowledgement mode")


    try:
        opts, args = getopt.getopt(sys.argv[1:],
                                   "f:p:a:dk", ["file=", "port=", "address=", "debug=", "sack="])
    except:
        usage()
        exit()

    port = 33122
    dest = "localhost"
    filename = None
    debug = False
    sackMode = False

    for o, a in opts:
        if o in ("-f", "--file="):
            filename = a
        elif o in ("-p", "--port="):
            port = int(a)
        elif o in ("-a", "--address="):
            dest = a
        elif o in ("-d", "--debug="):
            debug = True
        elif o in ("-k", "--sack="):
            sackMode = True

    s = Sender(dest, port, filename, debug, sackMode)
    try:
        s.start()
    except (KeyboardInterrupt, SystemExit):
        exit()

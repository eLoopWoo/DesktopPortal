import struct
import bluetooth
import socket
import time
import thread
import cStringIO

from enum import Enum
from PIL import ImageGrab
from uuid import getnode as get_mac

MAGIC_UUID = "aaaaaaaa-5555-4444-3333-bbbbbbbbbbbb"
lan_server_sock = None

class Command(Enum):
    GET_SCREEN = 1
    UPLOAD_FILE = 2

class IOUtils:
    CHUNK_SIZE = 4096
    @staticmethod
    def _send_all_to_sock(client_sock, data):
        bytes_sent = 0
        while bytes_sent < len(data):
            data_to_send = data[bytes_sent:bytes_sent + IOUtils.CHUNK_SIZE] if \
                (len(data) - bytes_sent >= IOUtils.CHUNK_SIZE) else data[bytes_sent:]
            bytes_sent += client_sock.send(data_to_send)
        
    @staticmethod
    def _receive_all_from_sock(client_sock, requested_len):
        data = ""
        bytes_received = 0
        while bytes_received < requested_len:
            bytes_to_read = IOUtils.CHUNK_SIZE if (requested_len - bytes_received >= IOUtils.CHUNK_SIZE) else \
                requested_len - bytes_received
            data += client_sock.recv(bytes_to_read)
            bytes_received = len(data)
        return data
        
    @staticmethod
    def _receive_all_from_sock_to_file(client_sock, requested_len, file_obj):
        data = ""
        bytes_received = 0
        while bytes_received < requested_len:
            bytes_to_read = IOUtils.CHUNK_SIZE if (requested_len - bytes_received >= IOUtils.CHUNK_SIZE) else \
                requested_len - bytes_received
            data = client_sock.recv(bytes_to_read)
            bytes_received += len(data)
            file_obj.write(data)
        return bytes_received
    
class LANStreamingServer:
    def __init__(self, address_to_bind, port_to_bind):
        self.address_to_bind = address_to_bind
        self.port_to_bind = port_to_bind
        
    @staticmethod
    def _serve_screenshots(client_sock):
        print "Serving screenshots!"
        try:
            while True:
                img = ImageGrab.grab()
                mem_file = cStringIO.StringIO()
                img.save(mem_file, 'jpeg')
                data = mem_file.getvalue()
                IOUtils._send_all_to_sock(client_sock, struct.pack("<I", len(data)))
                IOUtils._send_all_to_sock(client_sock, data)
                time.sleep(0.5)
        except Exception as e:
            print "Exception while serving screenshot!\n"
            print e.message
    
    @staticmethod
    def _receive_file(client_sock):
        print "Receiving a file!"
        file_obj = None
        try:
            file_size = struct.unpack("<Q", client_sock.recv(8))[0]
            print "File size %d" % file_size
            file_name_length = struct.unpack("<I", client_sock.recv(4))[0]
            print "File name length %d" % file_name_length
            file_name = IOUtils._receive_all_from_sock(client_sock, file_name_length)
            print "File name " + file_name
            file_obj = open(file_name, "wb")
            total_received_size = IOUtils._receive_all_from_sock_to_file(client_sock, file_size, file_obj)
            print "Finished receiving file with size %d" % total_received_size
        except Exception as e:
            print "Exception while receiving a file!\n"
            print e.message
        finally:
            if not file_obj == None:
                file_obj.close()
            
    @staticmethod
    def _route_socket(client_sock):
        try:
            command_id = struct.unpack("B", client_sock.recv(1))[0]
            print "Received command %d" % command_id
            if Command(command_id) == Command.GET_SCREEN:
                LANStreamingServer._serve_screenshots(client_sock)
            elif Command(command_id) == Command.UPLOAD_FILE:
                LANStreamingServer._receive_file(client_sock)
            else:
                print "Incorrect command received!\n"
        except Exception as e:
            print "Exception while routing the socket!\n"
            print e.message
        finally:
            client_sock.close()
    
    def _cleanup(self):
        self.lan_server_sock.close()
    
    def start_serving(self):
        self.lan_server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.lan_server_sock.bind((self.address_to_bind, self.port_to_bind))
        self.lan_server_sock.listen(5)
        print "Bounded the LAN socket on the address {0}:{1}".format(self.address_to_bind, self.port_to_bind)
        while True:
            client_sock, client_info = self.lan_server_sock.accept()
            print "A new client connected from {0}:{1}, starting to serve!".format(client_info[0], client_info[1])
            thread.start_new(LANStreamingServer._route_socket, (client_sock,))
            time.sleep(2)

class BluetoothHandshakeServer:
    SERVICE_CLASS = bluetooth.SERIAL_PORT_CLASS
    PROFILE = bluetooth.SERIAL_PORT_PROFILE
    def __init__(self, uuid, service_name, lan_info):
        self.uuid = uuid
        self.service_name = service_name
        self.lan_info = lan_info
        self.bluetooth_server_sock = None
        
    def _start_advertising(self):
        try:
            self.bluetooth_server_sock = bluetooth.BluetoothSocket( bluetooth.RFCOMM )
            self.bluetooth_server_sock.bind(("", bluetooth.PORT_ANY))
            self.bluetooth_server_sock.listen(1)
            port = self.bluetooth_server_sock.getsockname()[1]
            bluetooth.advertise_service( self.bluetooth_server_sock, self.service_name,
                           service_id = self.uuid,
                           service_classes = [ self.uuid, self.SERVICE_CLASS ],
                           profiles = [ self.PROFILE ])
                           
            print "Advertising our service on RFCOMM port %d\n" % port
        except Exception as e:
            print "An exception has occured while trying to advertise the service!\n"
            print e.message

    
    def _wait_for_connection_and_handshake(self):
        client_sock, client_info = self.bluetooth_server_sock.accept()
        print "A client connected from the address %s!\n" % client_info[0]
        client_sock.send(socket.inet_aton(self.lan_info[0]))
        time.sleep(1)
        client_sock.send(struct.pack("<H", self.lan_info[1]))
        client_sock.close()
        
    def _cleanup(self):
        bluetooth.stop_advertising(self.bluetooth_server_sock)
        self.bluetooth_server_sock.close() 
        
    def start_serving(self):
        while True:
            self._start_advertising()
            self._wait_for_connection_and_handshake()
            self._cleanup()
            time.sleep(2)

def prompt_user_for_IP():
    ipv4_addresses = socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)
    print "Found the following local IP addresses:"
    for x in xrange(0, len(ipv4_addresses)):
        print "{0}. {1}".format(x + 1, ((ipv4_addresses[x])[4])[0])
    print "Please choose which IP address to use: ",
    choice_index = raw_input()
    print ""
    choice_index_real = int(choice_index) - 1
    assert choice_index_real >= 0 and choice_index_real < len(ipv4_addresses), "Invalid option supplied"
    return ((ipv4_addresses[choice_index_real])[4])[0]
    
    
if __name__ == "__main__":
    address_to_bind = prompt_user_for_IP()
    port_to_bind = (get_mac() % 64535) + 1000     # To make sure we don't use first 1000 ports
    lan_server = LANStreamingServer(address_to_bind, port_to_bind)
    thread.start_new(lan_server.start_serving, ())
    
    bluetooth_server = BluetoothHandshakeServer(MAGIC_UUID, "HandshakeService", (address_to_bind, port_to_bind))
    bluetooth_server.start_serving()

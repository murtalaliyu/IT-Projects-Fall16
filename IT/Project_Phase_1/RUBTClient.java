/*
Authors
Murtala Aliyu
Anrew Marshall
*/

import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.nio.file.*;
import GivenTools.*;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.lang.*;
import java.net.InetAddress;

public class RUBTClient{
	public static void main(String[] args) throws Exception {

		//return error message if torrent file and file name arguments aren't entered
		if (args.length != 2) {
			System.err.println("\nUsage error: java RUBTClient <.torrent file> <.mov file>\n");
			return;
		}

		//get torrent file path and print it
		Path filePath = Paths.get("/Users/Murtala/Desktop/IT-Projects-Fall16/IT/Project_Phase_1/GivenTools/CS352_Exam_Solutions.mp4.torrent");
		//System.out.println("\nTorrent file path is: " + filePath + "\n");

		//open torrent path and parse it
		byte[] byteFilePathArray = Files.readAllBytes(filePath);

		//decode data using TorretInfo.java
		TorrentInfo decodedTorrentByteFile = new TorrentInfo(byteFilePathArray);
		//System.out.println("Decoded torrent byte file: " + decodedTorrentByteFile + "\n");

		//get tracker url
		URL url = decodedTorrentByteFile.announce_url;
		String urlString = url.toString();
		urlString += "?";

		//get infoHash in the form of ByteBuffer and convert to hex string
		ByteBuffer infoHash = decodedTorrentByteFile.info_hash;
		String infoHashString = toHex(infoHash.array());

		//generate random peer id of size 20
		byte[] peerId = getRandomByteArray(20);
		String peerIdString = new String(peerId);

		//assemble final url
		urlString += "info_hash=";
		urlString += infoHashString;
		urlString += "&peer_id=";
		urlString += peerIdString;
		urlString += "&port=6882&uploaded=0&downloaded=0&left=";
		urlString += decodedTorrentByteFile.file_length;
		urlString += "&event=started";

		//send HTTP get request to tracker
  		HttpURLConnection connect = (HttpURLConnection) new URL(urlString).openConnection();
 		DataInputStream input1 = new DataInputStream(connect.getInputStream());
 
 		int size = connect.getContentLength();
 		byte[] encodedTrackerResponse = new byte[size];
 
  		input1.readFully(encodedTrackerResponse);
  		input1.close();

		//get list of peers from tracker response
 		Object o = null;
 		o = Bencoder2.decode(encodedTrackerResponse);
 		//System.out.println(o);
 		Map<ByteBuffer, Object> response = (HashMap<ByteBuffer, Object>) o;
 
 		//print response
 		ToolKit.printMap(response, 0);

 		//get list of peers
 		ArrayList<Peer> peers = getListOfPeers(encodedTrackerResponse);

 		//System.out.println(peers);
 		
 		//get peer info from each peer
 		Peer tmp = null; 
 		for (int i = 0; i < peers.size(); i++) {
 			tmp = peers.get(i);

 			//open socket connection to each peer
			try (Socket socket = new Socket(tmp.ip, tmp.port))
			 {

                // open up IO streams
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());

				//create handshake message
		        byte pstrlen = 19;
		        String pstr = "BitTorrent protocol";
		        byte[] reserved = {0, 0, 0, 0, 0, 0, 0, 0};

		        //send handshake
				out.writeByte(pstrlen);
				out.writeBytes(pstr);
				out.write(reserved);
				out.write(infoHash.array());
				out.write(peerId);

				//get peer handshake response
				byte[] b = new byte[100];
				in.readFully(b);
				System.out.println(b);

				//print handshake response
				String strg = new String(b);
				System.out.println(strg);

				
				
                
			} catch (IOException e) {

			//return error when something goes wrong when server is listening on specified port
            System.out.println("Client disconnected on port " + tmp.port);
            System.out.println(e.getMessage());
			}
 		}

	}

	//implement byteBuffer to hex string
	private static String toHex(byte[] info_hash) {
		String hash_hex = "";

		for (int i = 0; i < info_hash.length; i++) {
			hash_hex += "%" + String.format("%02X",info_hash[i]);
		}
		return hash_hex;
	}

	//implement byteBufferToString 
	public static String byteBufferToString(ByteBuffer myByteBuffer) {
	
		if (myByteBuffer.hasArray()) {
	    	return new String(myByteBuffer.array(),
	        myByteBuffer.arrayOffset() + myByteBuffer.position(),
	        myByteBuffer.remaining());
		} else {
		    final byte[] b = new byte[myByteBuffer.remaining()];
		    myByteBuffer.duplicate().get(b);
		    return new String(b);
		}
	}

	//pertaining to peer list
	public final static ByteBuffer PEER_KEY = ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', 's'});
	public final static ByteBuffer PEER_ID = ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', ' ', 'i', 'd'});
	public final static ByteBuffer PEER_IP = ByteBuffer.wrap(new byte[] {'i', 'p'});
	public final static ByteBuffer PEER_PORT = ByteBuffer.wrap(new byte[] {'p', 'o', 'r', 't'});

	//get list of peers from tracker response
	public static ArrayList<Peer> getListOfPeers(byte[] encodedTrackerResponse) throws BencodingException {
		Object o = Bencoder2.decode(encodedTrackerResponse);

		HashMap<ByteBuffer, Object> response = (HashMap<ByteBuffer, Object>) o;

		ArrayList peerResponse = (ArrayList) response.get(PEER_KEY);
		ArrayList<Peer> peerList = new ArrayList<Peer>();

		for (int i = 0; i < peerResponse.size(); i++) {
			HashMap tmp = (HashMap) peerResponse.get(i);
			String name = null, ip = null;

			name = byteBufferToString((ByteBuffer) tmp.get(PEER_ID));
			ip = byteBufferToString((ByteBuffer) tmp.get(PEER_IP));

			int port = (int) tmp.get(PEER_PORT);

			if (name.contains("RU")) {
				Peer peer = new Peer(name, port, ip);
				peerList.add(peer);
			}
		}

		return peerList;
	}
	
	//escape given string
	public static String escapeStr(String hex){
		String percent = "%", hexString = "";
		hexString += percent;
		int a = 0, i = 0;
		while (i < 19) {
			hexString += hex.substring(a, a+2);
			hexString += percent;
			a += 2;
			i++;
		}
		hexString += hex.substring(38,40);

		return hexString;
	}

	//generate random bytes
	public static byte[] getRandomByteArray(int size){

		byte[] result = new byte[size];
		Random random = new Random();
		random.nextBytes(result);

		return result;
	}
}

//peer class
class Peer{
		
	public String name;
	public int port;
	public String ip;

	public Peer(String name, int port, String ip) {
		this.name = name;
		this.port = port;
		this.ip = ip;
	}

	public String toString() {
		String returnStr = "Peer: " + name + "Port: " + port + "IP: " + ip;
		return returnStr;
	}
}
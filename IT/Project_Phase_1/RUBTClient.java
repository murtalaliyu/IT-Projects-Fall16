/*
Authors
Murtala Aliyu
Andrew Marshall
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
import java.util.UUID;

public class RUBTClient {

	public static final byte MESSAGE_TYPE_KEEP_ALIVE = -1;
	public static final byte MESSAGE_TYPE_CHOKE = 0;
	public static final byte MESSAGE_TYPE_UNCHOKE = 1;
	public static final byte MESSAGE_TYPE_INTERESTED = 2;
	public static final byte MESSAGE_TYPE_NOT_INTERESTED = 3;
	public static final byte MESSAGE_TYPE_HAVE = 4;
	public static final byte MESSAGE_TYPE_BITFIELD = 5;
	public static final byte MESSAGE_TYPE_REQUEST = 6;
	public static final byte MESSAGE_TYPE_PIECE = 7;
	public static final byte MESSAGE_TYPE_CANCEL = 8;
	public static final byte MESSAGE_TYPE_HANDSHAKE = 9;
	public static final byte[] BIT_TORRENT_PROTOCOL = new String("BitTorrent protocol").getBytes();

	public static boolean isChoked = false;

	public static int left = 0;
	public static int numofPieces = 0;
	public static int downloaded = 0;
	public static int uploaded = 0;

	public static int length = 0;

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
 		System.out.println();
 		ToolKit.printMap(response, 0);

 		//get list of peers
 		ArrayList<Peer> peers = getListOfPeers(encodedTrackerResponse);
 		
 		//get peer info from each peer
 		Peer tmp = null; 
		tmp = peers.get(0);

		//open socket connection to each peer
		try (Socket socket = new Socket(tmp.ip, tmp.port)) {

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
			out.flush();

			//get peer handshake response
			byte[] handshakeResponse = new byte[68];
			in.readFully(handshakeResponse);

			//verify peerID
			byte[] peerInfoHash = Arrays.copyOfRange(handshakeResponse, 28, 48);
			if (!Arrays.equals(peerInfoHash, infoHash.array())) {
				System.out.println("Handshake with " + tmp.name + " denied");
				socket.close();

			} else {

				System.out.println("\nHandshake with " + tmp.name + " accepted at port " + tmp.port);

				//get bitfield length
				int length = in.readInt();
				System.out.println("bitfield length is: " + length);

				//get message ID
				Byte messageID = in.readByte();
				System.out.println("message id is: " + messageID);

				//verify messageID
				String[] bitFields = new String[length]; byte[] bitfield = new byte[length-1];;
				if (messageID == (int) MESSAGE_TYPE_BITFIELD) {
					System.out.println("Got a bitfield, message ID is " + messageID);

					//get bitfields
					in.readFully(bitfield);
					System.out.println(bitfield);

					//convert bitfield to human readable form
					for (int z = 0; z < bitfield.length; z++) {
						String s = "0b" + ("0000000" + Integer.toBinaryString(0xFF & bitfield[z])).replaceAll(".*(.{8})$", "$1");
						//int foo = Integer.parseInt(s, 2);
						bitFields[z] = s;
						System.out.println(s);
					}
					/*String[] bitFieldsReadable = new String[length];
					for (int i = 0; i < bitFieldsReadable.length; i++) {
						bitFieldsReadable[i] = binaryToString(bitFields[i]);
						System.out.println(bitFieldsReadable[i]);
					}*/
				}

				//send interested message
				byte[] interestedMessage = new byte[5];
				System.arraycopy(intToByteArray(1), 0, interestedMessage, 0, 4);
				interestedMessage[4] = (byte) 2;

				out.write(interestedMessage);
				int id = getMessageIDFromPeer(in);

				//out.write(keepAlive);	//not really doing its job
				
				//check if we're unchoked
				byte[] currentPiece = new byte[decodedTorrentByteFile.file_length];
				if (id == 1) {
					System.out.println("We are unchoked, message ID is " + id);

					//get piece count and length
					int pieceCount = decodedTorrentByteFile.file_length / decodedTorrentByteFile.piece_length;
					int pieceLength = decodedTorrentByteFile.piece_length;
					System.out.println("piece count: " + pieceCount + ". Piece length: " + pieceLength);

					for (int i = 0; i < pieceCount; i++) {
						
						if (i == pieceCount - 1) {
							pieceLength = decodedTorrentByteFile.file_length % pieceLength;
						}

						for (int j = 0; j < 2; j++) {

							System.out.println("i: " + i + ". j: " + j);

							//send request
							out.writeInt(13); // Message Length
							out.writeByte(MESSAGE_TYPE_REQUEST); // Message ID
							out.writeInt(i); // Index
							out.writeInt(j*(decodedTorrentByteFile.piece_length/2)); // Begin
							out.writeInt(pieceLength/2); // Length

							//read request response
							length = in.readInt();
	                    	messageID = in.readByte();
							System.out.println("message id: " + messageID + ". count = " + i);

							//verify that this is a piece
							if (messageID == 7) {
								int index = in.readInt();
	                        	int begin = in.readInt();

		                        /*if (i == pieceCount - 1) { // Last piece
		                            pieceLength = in.available();
		                        } else { // Wait until there is enough available bytes
		                            while (in.available() < pieceLength) { }
		                        }*/
								System.out.println("index: " + index + ". begin: " + begin + ". available: " + in.available());

								//read fully
								byte[] block = new byte[pieceLength/2];
								in.readFully(block, 0, pieceLength/2);
								System.out.println("block: " + block + ". blockSize: " + block.length);

								//save downloaded piece
								//currentPiece = Arrays.copyOfRange(block, 0, decodedTorrentByteFile.piece_length/2);
								System.arraycopy(block, 0, currentPiece, i*(decodedTorrentByteFile.piece_length/2), block.length);
								System.out.println("currentPiece: " + currentPiece + ". totalDownloadedPieceSize: " + currentPiece.length);

								//verify block
								//ByteBuffer[] pieceHash = decodedTorrentByteFile.piece_hashes;

							} else {
								System.err.println("Error! Piece #" + i + " has not been received");
							}

						}

					}
				}

				//write to file
				String fileName = decodedTorrentByteFile.file_name;
				System.out.println(fileName);
				FileOutputStream fos = new FileOutputStream("/Users/Murtala/Desktop/IT-Projects-Fall16/IT/Project_Phase_1/GivenTools/" + fileName);
				fos.write(currentPiece);
				fos.close();

			}

		} catch (IOException e) {

		//return error when something goes wrong when server is listening on specified port
        System.out.println("Client disconnected at port " + tmp.port);
        System.err.println(e.getMessage());
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

	//implement binary string to ascii string
	/*public static String binaryToString(String input) {
		StringBuilder sb = new StringBuilder(); // Some place to store the chars
		String output = "";

		Arrays.stream(input.split("(?<=\\G.{8})")).forEach(s -> System.out.print((char) Integer.parseInt(s, 2)));

		output += sb.toString(); // Output text 

		return output;
	}*/

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

		String uuid = UUID.randomUUID().toString();
		String newUuid = uuid.substring(0, 8);
		newUuid += uuid.substring(9, 13);
		newUuid += uuid.substring(14, 18);
		newUuid += uuid.substring(19, 23);

		byte[] byteArray = newUuid.getBytes();

		return byteArray;
	}

	//int to byte array
	public static byte[] intToByteArray(int value) {
		byte[] retVal = ByteBuffer.allocate(4).putInt(value).array();
		return retVal;
	}

	//get message ID from peer
	public static int getMessageIDFromPeer(DataInputStream in) throws Exception {
		byte messageID = readMessage(in);

		while (true) {
			switch (messageID) {

			case MESSAGE_TYPE_KEEP_ALIVE:
				break;

			case MESSAGE_TYPE_CHOKE:
				return (int) MESSAGE_TYPE_CHOKE;
				
			case MESSAGE_TYPE_UNCHOKE:
				return (int) MESSAGE_TYPE_UNCHOKE;
				
			case MESSAGE_TYPE_INTERESTED:
				return (int) MESSAGE_TYPE_INTERESTED;
				
			case MESSAGE_TYPE_NOT_INTERESTED:
				return (int) MESSAGE_TYPE_NOT_INTERESTED;
				
			case MESSAGE_TYPE_HAVE:
				return (int) MESSAGE_TYPE_HAVE;
				
			case MESSAGE_TYPE_BITFIELD:
				return (int) MESSAGE_TYPE_BITFIELD;
				
			case MESSAGE_TYPE_REQUEST:
				return (int) MESSAGE_TYPE_REQUEST;
				
			case MESSAGE_TYPE_PIECE:
				return (int) MESSAGE_TYPE_PIECE;
				
			case MESSAGE_TYPE_CANCEL:
				return (int) MESSAGE_TYPE_CANCEL;
				
			case MESSAGE_TYPE_HANDSHAKE:
				return (int) MESSAGE_TYPE_HANDSHAKE;
			}
			
			messageID = readMessage(in);
		}
	}

	//read peer message
	public static byte readMessage(DataInputStream in) throws Exception {
		length = in.readInt();

		if (length == 0) {
			return -1;
		}

		byte id = in.readByte();

		switch (id) {

		// choke
		case 0:
			return id;

		// unchoke
		case 1:
			return id;

		// interested
		case 2:
			return id;

		// not interested
		case 3:
			return id;

		// have
		case 4:
			in.readInt();
			return id;

		case 5:
			for (int i = 0; i < length - 1; i++) {
				in.readByte();
			}
			return id;

		// request
		case 6:
			return id;

		// piece
		case 7:
			int index = in.readInt();
			int begin = in.readInt();

		// cancel
		case 8:
			return id;

		default:
			break;
		}

		return 0;
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
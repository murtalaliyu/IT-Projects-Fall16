/*
Authors
Murtala Aliyu
Anrew Marshall
*/

import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.nio.file.*;
import GivenTools.TorrentInfo;
import GivenTools.Bencoder2;
import GivenTools.ToolKit;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.lang.*;

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

		//get infoHash in the form of ByteBuffer and convert to byte array
		ByteBuffer infoHash = decodedTorrentByteFile.info_hash;
		byte[] b = new byte[infoHash.remaining()];
		infoHash.get(b);
		
		final char[] hexArray = "0123456789ABCDEF".toCharArray();
		char[] hexChars = new char[b.length * 2];
		for ( int j = 0; j < b.length; j++ ) {
			int v = b[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		String hex = new String(hexChars);

		//add percent to hex value
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

		//generate peer id
		String peerId = "%25%85%04%26%23%e3%32%0d%f2%90%e2%51%f6%15%92%2f%d9%b0%ef%a9";

		//assemble final url
		urlString += "info_hash=";
		urlString += hexString;
		urlString += "&peer_id=";
		urlString += peerId;
		urlString += "&port=6881&uploaded=0&downloaded=0&left=";
		urlString += decodedTorrentByteFile.file_length;
		urlString += "&event=started";

		//send HTTP get request to tracker
		HttpURLConnection connect = (HttpURLConnection) new URL(urlString).openConnection();
		DataInputStream input = new DataInputStream(connect.getInputStream());

		int size = connect.getContentLength();
		byte[] encodedTrackerResponse = new byte[size];

		input.readFully(encodedTrackerResponse);
		input.close();

		//get list of peers from tracker response
		Object o = null;
		o = Bencoder2.decode(encodedTrackerResponse);
		//System.out.println(o);
		Map<ByteBuffer, Object> response = (HashMap<ByteBuffer, Object>) o;

		//print response
		ToolKit.printMap(response, 1);
	}
	
	public static String escapeStr(String str){
		String esc = "";
		
		int len = str.length();
		for(int i=2; i < len; i+=2 ){
			esc += "%";
			esc += str.substring(i-2, i);
		}
		esc += "%";
		
		return esc;
	}
	
	//public static BufferedReader connectToPeer()
}
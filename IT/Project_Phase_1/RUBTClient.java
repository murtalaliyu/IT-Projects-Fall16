import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.nio.file.*;
import GivenTools.TorrentInfo;
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
		Path filePath = Paths.get("C:/Users/Andrew/Documents/Code/Java/Internet_Technology/Phase_1/GivenTools/CS352_Exam_Solutions.mp4.torrent");
		//System.out.println("\nTorrent file path is: " + filePath + "\n");

		//open torrent path and parse it
		byte[] byteFilePathArray = Files.readAllBytes(filePath);

		//decode data using Bencoder2.java
		TorrentInfo decodedTorrentByteFile = new TorrentInfo(byteFilePathArray);
		//System.out.println("Decoded torrent byte file: " + decodedTorrentByteFile + "\n");

		//get tracker url
		URL url = decodedTorrentByteFile.announce_url;

		//get infoHash in the form of ByteBuffer
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
		System.out.println(hex);
		
		/*System.out.println("\n" + infoHash.toString() + "\n");
		String x = new String(infoHash.array(), "ASCII");
		System.out.println(x + "\n");*/

		/*String u = url.toString();
		u = u.concat("?");

		System.out.println("\nTracker URL: " + u + "\n");*/

		/*/send HTTP get request from tracker
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			String tempString = "";
			while ((tempString = br.readLine()) != null) {
				System.out.println(tempString);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}*/
	}
}
import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.nio.file.*;
import GivenTools.TorrentInfo;
import java.net.*;
import java.util.Map;
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
		Path filePath = Paths.get("/Users/Murtala/Desktop/IT/Project_Phase_1/GivenTools/CS352_Exam_Solutions.mp4.torrent");
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
		String x = infoHash.toHexString();
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
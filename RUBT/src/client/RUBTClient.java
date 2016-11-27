package client;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

import givenTools.BencodingException;
import givenTools.TorrentInfo;

/**
 * Start of the program contains main method and parses the information from the
 * torrent file.
 *
 */
public class RUBTClient {

	public static TorrentInfo torrentInfo;

	public static Torrent readTorrentFile(File torrentFile) throws IOException, BencodingException {

		if (!torrentFile.canRead()) {
			System.out.println("Can't read torrent file");
		}

		/*
		 * Read the torrent file into a byte array and create a TorrentInfo
		 * object with it
		 */
		byte[] byteFile = new byte[(int) torrentFile.length()];
		DataInputStream dis = new DataInputStream(new FileInputStream(torrentFile));
		dis.readFully(byteFile);
		dis.close();

		torrentInfo = new TorrentInfo(byteFile);
		Torrent torrent = new Torrent(torrentInfo, "CS352_Exam_Solutions.mp4");
		// Torrent torrent = new Torrent(torrentInfo, args[1]);
		return torrent;
	}

	public static void main(String[] args) throws IOException, BencodingException, InterruptedException {

		/*
		 * if (args.length != 2) {
		 * System.out.println("Invalid amount of arguments."); return; }
		 */

		/*
		 * Check if we can open the torrent file
		 */
		File torrentFile = new File("CS352_Exam_Solutions.mp4.torrent");
		// File torrentFile = new File(args[0]);
		Torrent torrent = readTorrentFile(torrentFile);

		/*
		 * Born a thread and start it.
		 */
		System.out.println("Starting a torrent thread");
		(new Thread(torrent)).start();

		/*
		 * Gracefully exit the program.
		 */
		Scanner sc = new Scanner(System.in);
		System.out.println("Type 'q' to quit");
		while (true) {

			if (sc.nextLine().equals("q")) {
				torrent.stop();
				sc.close();
				return;

			} else
				continue;
		}
	}
}

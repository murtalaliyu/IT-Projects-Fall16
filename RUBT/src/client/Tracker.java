package client;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;

import givenTools.Bencoder2;
import givenTools.BencodingException;

/**
 * Tracker class that communicates with the tracker.
 * 
 * @author Rushiraj
 */
public class Tracker {

	private URL trackerURL;

	public Tracker(URL trackerURL) {
		this.trackerURL = trackerURL;
	}

	/**
	 * Send Started announce to tracker
	 * 
	 * @param peerId
	 *            Our peerId
	 * @param port
	 *            Port on which we are listening.
	 * @param uploaded
	 *            Amount we have uploaded
	 * @param downloaded
	 *            Amount we have downloaded
	 * @param left
	 *            Amount we have left to download
	 * @param infoHash
	 *            Info hash of the torrent we want.
	 * @return Tracker response dictionary.
	 * @throws IOException
	 * @throws BencodingException
	 */
	public HashMap<ByteBuffer, Object> start(String peerId, int port, int uploaded, int downloaded, int left,
			String infoHash) throws IOException, BencodingException {

		return announce("started", peerId, port, uploaded, downloaded, left, infoHash);
	}

	/**
	 * Send Stopped announce to tracker
	 * 
	 * @param peerId
	 *            Our peerId
	 * @param port
	 *            Port on which we are listening.
	 * @param uploaded
	 *            Amount we have uploaded
	 * @param downloaded
	 *            Amount we have downloaded
	 * @param left
	 *            Amount we have left to download
	 * @param infoHash
	 *            Info hash of the torrent we want.
	 * @return Tracker response dictionary.
	 * @throws IOException
	 * @throws BencodingException
	 */
	public HashMap<ByteBuffer, Object> stop(String peerId, int port, int uploaded, int downloaded, int left,
			String infoHash) throws IOException, BencodingException {

		return announce("stopped", peerId, port, uploaded, downloaded, left, infoHash);
	}

	/**
	 * Send Completed announce to tracker
	 * 
	 * @param peerId
	 *            Our peerId
	 * @param port
	 *            Port on which we are listening.
	 * @param uploaded
	 *            Amount we have uploaded
	 * @param downloaded
	 *            Amount we have downloaded
	 * @param left
	 *            Amount we have left to download
	 * @param infoHash
	 *            Info hash of the torrent we want.
	 * @return Tracker response dictionary.
	 * @throws IOException
	 * @throws BencodingException
	 */
	public HashMap<ByteBuffer, Object> complete(String peerId, int port, int uploaded, int downloaded, int left,
			String infoHash) throws IOException, BencodingException {

		return announce("completed", peerId, port, uploaded, downloaded, left, infoHash);
	}

	/**
	 * Send regular announce to tracker
	 * 
	 * @param peerId
	 *            Our peerId
	 * @param port
	 *            Port on which we are listening.
	 * @param uploaded
	 *            Amount we have uploaded
	 * @param downloaded
	 *            Amount we have downloaded
	 * @param left
	 *            Amount we have left to download
	 * @param infoHash
	 *            Info hash of the torrent we want.
	 * @return Tracker response dictionary.
	 * @throws IOException
	 * @throws BencodingException
	 */
	public HashMap<ByteBuffer, Object> announce(String peerId, int port, int uploaded, int downloaded, int left,
			String infoHash) throws IOException, BencodingException {

		return announce(null, peerId, port, uploaded, downloaded, left, infoHash);
	}

	@SuppressWarnings("unchecked")
	private HashMap<ByteBuffer, Object> announce(String event, String peerId, int port, int uploaded, int downloaded,
			int left, String infoHash) throws IOException, BencodingException {

		URL url = new URL(this.trackerURL.toString() + "?info_hash=" + infoHash + "&peer_id=" + peerId + "&port=" + port
				+ "&uploaded=" + uploaded + "&downloaded=" + downloaded + "&left=" + left + "&event="
				+ (event != null ? event : ""));

		// Send the message and receive the response
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		DataInputStream dis = new DataInputStream(con.getInputStream());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		int read;
		while ((read = dis.read()) != -1) {
			baos.write(read);
		}

		dis.close();
		HashMap<ByteBuffer, Object> res = (HashMap<ByteBuffer, Object>) Bencoder2.decode(baos.toByteArray());
		baos.close();
		return res;
	}

}

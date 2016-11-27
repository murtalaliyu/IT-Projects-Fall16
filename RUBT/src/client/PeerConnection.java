package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Responsible for network communications with a Peer from the rest of the
 * application. One is created for each Peer. Messages received are passed back
 * to the main Torrent object.
 * 
 * @author Rushiraj
 * 
 */
public class PeerConnection implements Runnable {

	private Torrent torrent;
	private Socket sock;
	private String ip;

	private int port;

	private boolean running = true;
	private boolean handshakeDone = false;

	private Timer keepaliveTimer;

	private ByteBuffer peerId = null;

	// 2 << 14 = 32768 = Length of piece.
	private byte[] buffer = new byte[2 << 14];

	private ConcurrentLinkedQueue<ByteBuffer> messages = new ConcurrentLinkedQueue<ByteBuffer>();

	/**
	 * Creates a new PeerConnection, but does not start it.
	 *
	 * @param t
	 *            The torrent object that messages are to be passed to
	 * @param ip
	 *            The IP address of the peer to connect to
	 * @param port
	 *            The port of the peer to connect to
	 * @param peerId
	 *            The peerId of the peer to connect to
	 */

	public PeerConnection(Torrent t, String ip, int port, ByteBuffer peerId) {
		this.torrent = t;
		this.ip = ip;
		this.peerId = peerId.duplicate();
		this.port = port;
		this.keepaliveTimer = new Timer(ip + " keepaliveTimer", true);

	}

	/**
	 * Responsible for sending messages when they are
	 * ready to be sent and receiving and rebuilding messages.
	 */
	@Override
	public void run() {
		try {
			System.out.println("Downloading from: " + ip);
			sock = new Socket(ip, port);
			OutputStream outputStream = sock.getOutputStream();
			InputStream inputStream = sock.getInputStream();
			int len;
			ByteBuffer writingBuffer = ByteBuffer.wrap(buffer);
			writingBuffer.position(0);

			while (running) {

				Thread.sleep(10);
				ByteBuffer msg = messages.poll();

				try {

					// We have a message to send.
					if (msg != null) {
						outputStream.write(msg.array());
					}

				} catch (Exception e) {

					messages.clear();
					sock = new Socket(ip, port);
					outputStream = sock.getOutputStream();
					inputStream = sock.getInputStream();
					writingBuffer.position(0);
					this.torrent.peerDying(peerId);

				}

				// We have bytes to read.
				if (inputStream.available() > 0) {
					byte[] tbuf = new byte[1024];
					len = inputStream.read(tbuf);
					writingBuffer.put(tbuf, 0, len);
				}

				// Not enough to do anything yet.
				if (writingBuffer.position() <= 4) {
					continue;
				}

				if (!handshakeDone) {

					// Handshake is complete.
					if (writingBuffer.position() >= 68) {
						int ol = writingBuffer.position();

						// Grab the first 68 bytes.
						writingBuffer.position(68).flip();
						ByteBuffer msgBuf = ByteBuffer.allocate(68);
						msgBuf.put(writingBuffer);
						msgBuf.flip();
						msgBuf.position(0);
						writingBuffer.limit(ol);
						writingBuffer.compact();

						// Pass the message to the Peer object.
						Message peerMessage = processHandshake(msgBuf);
						if (peerMessage != null) {
							torrent.recvMessage(peerMessage);
						}

						handshakeDone = true;
					}

					// We have a full message
				} else {

					while (writingBuffer.position() >= 4
							&& writingBuffer.position() >= (len = ByteBuffer.wrap(buffer).getInt() + 4)) {

						ByteBuffer msgBuf;
						int ol = writingBuffer.position();
						writingBuffer.position(len).flip();
						msgBuf = ByteBuffer.allocate(len);
						msgBuf.put(writingBuffer);
						msgBuf.flip();

						writingBuffer.limit(ol);
						writingBuffer.compact();

						Message peerMessage = processMessage(msgBuf);

						if (peerMessage != null) {
							torrent.recvMessage(peerMessage);
						}
					}
				}
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();

		} finally {

			try {
				sock.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Convert a handshake network message into a Message object
	 * 
	 * @param msg
	 *            A ByteBuffer containing the handshake message
	 * @return A PeerMessage representing a handshake.
	 */
	private Message processHandshake(ByteBuffer msg) {
		return Message.Handshake(peerId, msg);
	}

	/**
	 * Convert a network message into a Message object
	 * 
	 * @param msg
	 *            A ByteBuffer containing the message
	 * @return A PeerMessage representing whatever message was passed in.
	 *         Keep-Alive messages are discarded.
	 */
	private Message processMessage(ByteBuffer msg) {
		int len = msg.getInt();
		if (len == 0) {
			return null;
		}

		byte type = msg.get();
		switch (type) {
		case 0:
			return Message.Choke(peerId);

		case 1:
			return Message.Unchoke(peerId);

		case 2:
			return Message.Interested(peerId);

		case 3:
			return Message.NotInterested(peerId);

		case 4:
			return Message.Have(peerId, msg.getInt());

		case 5:
			len -= 1;
			BitSet pcs = new BitSet(len * 8);
			byte b = 0;

			// Turn the BitField into a BitSet
			for (int j = 0; j < len * 8; ++j) {
				if (j % 8 == 0) {
					b = msg.get();
				}
				
				pcs.set(j, ((b << (j % 8)) & 0x80) != 0);
			}

			return Message.Bitfield(peerId, pcs);

		case 6:
			int idx = msg.getInt();
			int begin = msg.getInt();
			int length = msg.getInt();
			return Message.Request(peerId, idx, begin, length);

		case 7:
			idx = msg.getInt();
			begin = msg.getInt();
			return Message.Piece(peerId, idx, begin, (ByteBuffer) msg.compact().flip());

		default:
			return null;
		}
	}

	private TimerTask tsk = null;

	/**
	 * Updates the KeepALive timer. Called every time a message is queued.
	 */
	private void resetKeepAlive() {
		if (tsk != null) {
			tsk.cancel();
			keepaliveTimer.purge();
		}

		tsk = new TimerTask() {

			@Override
			public void run() {
				messages.add(ByteBuffer.wrap(Constants.KEEP_ALIVE));
				resetKeepAlive();
			}
		};

		keepaliveTimer.schedule(tsk, 120000);
	}

	/**
	 * Adds the message to the messages queue and updates the KeepALive timer.
	 *
	 * @param msg
	 *            message to add to the queue
	 * @return Whether or not the message was successfully added.
	 */
	private boolean sendMessage(ByteBuffer msg) {
		resetKeepAlive();
		return messages.add(msg);
	}

	/**
	 * Sends Choke message
	 * 
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendChoke() {
		return sendMessage(ByteBuffer.wrap(Constants.CHOKE));
	}

	/**
	 * Sends Unchoke message
	 * 
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendUnchoke() {
		return sendMessage(ByteBuffer.wrap(Constants.UNCHOKE));
	}

	/**
	 * Sends Have message
	 * 
	 * @param index
	 *            index of the piece that i
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendHave(int index) {
		ByteBuffer bb = ByteBuffer.allocate(9);
		bb.put(Constants.HAVE);
		bb.putInt(index);
		bb.flip();
		return sendMessage(bb);
	}

	/**
	 * Sends Piece message
	 * 
	 * @param index
	 *            index of the piece that is being sent
	 * @param begin
	 *            beginning index of the piece data
	 * @param length
	 *            number of bytes of the piece data being sent
	 * @param pieceData
	 *            ByteBuffer containing piece data. The bytes from begin to
	 *            begin+length will be sent.
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendPiece(int index, int begin, int length, ByteBuffer pieceData) {
		ByteBuffer bb = ByteBuffer.allocate(13 + length);
		bb.put(Constants.PIECE);
		bb.putInt(0, 9 + length);
		bb.putInt(index);
		bb.putInt(begin);
		bb.put(pieceData.array(), begin, length);
		bb.flip();
		return sendMessage(bb);
	}

	/**
	 * Sends Request message
	 * 
	 * @param index
	 *            index of the piece being requested
	 * @param begin
	 *            beginning index of the piece data
	 * @param length
	 *            number of bytes of the piece requested
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendRequest(int index, int begin, int length) {
		ByteBuffer bb = ByteBuffer.allocate(17);
		bb.put(Constants.REQUEST);
		bb.putInt(index);
		bb.putInt(begin);
		bb.putInt(length);
		bb.flip();
		return sendMessage(bb);
	}

	/**
	 * Sends Interested message
	 * 
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendInterested() {
		return sendMessage(ByteBuffer.wrap(Constants.INTERESTED));
	}

	/**
	 * Sends Not Interested message
	 * 
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendNotInterested() {
		return sendMessage(ByteBuffer.wrap(Constants.NOT_INTERESTED));
	}

	/**
	 * Sends BitField message
	 * 
	 * @param bitfield
	 *            ByteBuffer containing BitField data
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendBitfield(ByteBuffer bitfield) {
		ByteBuffer bb = ByteBuffer.allocate(bitfield.limit() + 5);
		bb.put(Constants.BITFIELD);
		bb.putInt(0, bitfield.limit() + 1);
		bb.put(bitfield);
		bb.flip();
		return sendMessage(bb);
	}

	/**
	 * Sends Handshake message
	 * 
	 * @param infoHash
	 *            InfoHash of the torrent for which we are connecting
	 * @param peerId
	 *            Our peerId
	 * @return whether or not the message was queued to be sent
	 */
	public boolean sendHandshake(ByteBuffer infoHash, ByteBuffer peerId) {

		// 49 bytes + size of PROTOCOL_HEADER
		ByteBuffer handshakeBuffer = ByteBuffer.allocate(68);
		infoHash.position(0);
		peerId.position(0);

		handshakeBuffer.put((byte) 19);
		handshakeBuffer.put(Constants.PROTOCOL_HEADER);

		// Two ints are 8 bytes
		handshakeBuffer.putInt(0);
		handshakeBuffer.putInt(0);
		handshakeBuffer.put(infoHash);
		handshakeBuffer.put(peerId);
		return sendMessage(handshakeBuffer);
	}

	/**
	 * Used to stop the thread
	 */
	public void shutdown() {
		this.running = false;
	}
}

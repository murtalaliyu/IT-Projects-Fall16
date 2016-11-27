package client;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import givenTools.BencodingException;
import givenTools.TorrentInfo;

/**
 * A torrent object responsible for talking to peers and downloading its own
 * pieces.
 * 
 * @author Rushiraj
 * 
 */

public class Torrent implements Runnable {

	private Tracker tracker;
	private TorrentInfo torrentInfo;
	private ArrayList<Piece> pieces;

	private String peerId;
	private String encodedInfoHash;
	private String outputFile;

	private RandomAccessFile dataFile;
	private MappedByteBuffer fileByteBuffer;

	// Thread safe queue to hold messages
	private ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<Message>();
	private HashMap<ByteBuffer, Peer> peers = new HashMap<ByteBuffer, Peer>();
	private BitSet piecesHad = null;

	private final Object fileLock = new Object();
	private final Object peerLock = new Object();

	private boolean running = true;
	private boolean sentComplete = false;

	private int port = 6883;
	private int uploaded = 0;
	private int downloaded = 0;
	private int left = 0;
	private int minInterval = 0;
	private int interval = 0;

	private long lastAnnounce = 0;
	private long startTime;

	private Timer chokeTimer;

	public Torrent(TorrentInfo ti, String outputFile) {
		this.torrentInfo = ti;
		this.outputFile = outputFile;
		this.encodedInfoHash = encodeInfoHash(this.torrentInfo.info_hash.array());

		// Should be randomly generated
		this.peerId = "rushirajgajjarcs3521";
		this.pieces = generatePieces();
		this.left = ti.file_length;
		this.tracker = new Tracker(this.torrentInfo.announce_url);

		writeFile();
		verify();
	}
	
	public void writeFile() {
		
		/*
		 * Output file is saved even if the download is stopped mid way.
		 */
		try {

			dataFile = new RandomAccessFile(this.outputFile, "rw");
			fileByteBuffer = dataFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0,
					(Integer) torrentInfo.info_map.get(TorrentInfo.KEY_LENGTH));

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Verify the file and updates what pieces we have.
	 */
	private void verify() {
		int offset = 0;
		MessageDigest md;
		byte[] sha1;

		try {

			for (Piece pc : pieces) {
				md = MessageDigest.getInstance("SHA-1");
				ByteBuffer bb = ByteBuffer.allocate(pc.getSize());
				bb.put((ByteBuffer) fileByteBuffer.duplicate().position(offset).limit(offset + pc.getSize())).flip();
				offset += pc.getSize();
				sha1 = md.digest(bb.array());

				if (Arrays.equals(sha1, pc.getHash())) {
					left -= pc.getSize();
					pc.setData(bb);
					pc.setState(Piece.PieceState.COMPLETE);
					piecesHad.set(pc.getIndex());
				}
			}

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

	}

	/*
	 * Used for stopping the thread.
	 */
	public void stop() {
		this.running = false;
	}

	private void updateChokedPeers() {
		Peer choked = null;
		for (Peer p : peers.values()) {
			if (!p.choking) {
				p.getPeerConnection().sendChoke();
				p.choking = true;
				choked = p;
				break;
			}
		}

		// There are no unchoked peers.
		if (choked == null) {
			for (Peer p : peers.values()) {
				p.choking = false;
				p.getPeerConnection().sendUnchoke();

			}

		} else {

			// We choked an unchoked peer, so unchoke a random peer.
			for (Peer p : peers.values()) {
				if (p.choking && p != choked) {
					p.choking = false;
					p.getPeerConnection().sendUnchoke();
					return;
				}
			}

			/*
			 * We couldn't find anyone else to unchoke, just unchoke the choked
			 * peer again.
			 */
			choked.choking = false;
			choked.getPeerConnection().sendUnchoke();
		}

		if (left == 0) {
			System.out.println("File is done downloading in "
					+ TimeUnit.MINUTES.convert((System.nanoTime() - this.startTime), TimeUnit.NANOSECONDS)
					+ " Minutes");
			System.out.println("Seeding");
		}
	}

	/**
	 * Main runnable for the thread. First finds list of peers with the
	 * specified IP address Then enters run loop and ends once we get all the
	 * pieces
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		try {
			this.startTime = System.nanoTime();

			/*
			 * Update choked peers 30 seconds.
			 */
			chokeTimer = new Timer("Choke Timer", true);
			chokeTimer.schedule(new TimerTask() {

				@Override
				public void run() {
					Torrent.this.updateChokedPeers();
				}

			}, 0, 30000);

			HashMap<ByteBuffer, Object> trackerResponse = tracker.start(peerId, port, uploaded, downloaded, left,
					encodedInfoHash);

			ArrayList<HashMap<ByteBuffer, Object>> tmp_peers = (ArrayList<HashMap<ByteBuffer, Object>>) trackerResponse
					.get(Constants.PEERS);
			/*
			 * Get the peers we have to connect to from the tracker response.
			 */
			for (HashMap<ByteBuffer, Object> p : tmp_peers) {
				if (byteBufferToString((ByteBuffer) p.get(Constants.PEER_IP)).equals("172.16.97.11")
						|| byteBufferToString((ByteBuffer) p.get(Constants.PEER_IP)).equals("172.16.97.12")
						|| byteBufferToString((ByteBuffer) p.get(Constants.PEER_IP)).equals("172.16.97.13")) {
					
					ByteBuffer tem_ip = (ByteBuffer) p.get(Constants.PEER_IP);
					ByteBuffer tem_id = (ByteBuffer) p.get(Constants.PEER_ID);
					int tem_port = (Integer) p.get(Constants.PEER_PORT);

					PeerConnection pc = new PeerConnection(this, byteBufferToString(tem_ip), tem_port,tem_id);

					pc.sendHandshake(this.torrentInfo.info_hash, ByteBuffer.wrap(this.peerId.getBytes()));
					System.out
							.println("Sent handshake to " + byteBufferToString(tem_id));

					Peer pr = new Peer((ByteBuffer) p.get(Constants.PEER_ID), pc);
					pr.handshook = false;
					peers.put(pr.getPeerId(), pr);

					/*
					 * Start threads for each peer and begin downloading.
					 */
					System.out.println(
							"Starting a new thread for " + byteBufferToString(tem_id));
					(new Thread(pc)).start();
				}
			}

			minInterval = (Integer) trackerResponse.get(Constants.MIN_INTERVAL) * 1000;
			interval = (Integer) trackerResponse.get(Constants.INTERVAL) * 1000;

			/*
			 * "Tracker announces should be performed no more frequently than
			 * the value of "min_interval" (or 1/2 "interval" if "min_interval"
			 * is not present)"
			 */
			if (minInterval == 0)
				minInterval = interval / 2;

			lastAnnounce = System.currentTimeMillis();

			/*
			 *
			 */
			while (running) {
				if ((System.currentTimeMillis() - lastAnnounce) >= (minInterval - 5000)) {
					tracker.announce(peerId, port, uploaded, downloaded, left, encodedInfoHash);
					lastAnnounce = System.currentTimeMillis();
				}

				/*
				 * Process all messages that have come in since the last time we
				 * looped.
				 */
				processMessages();

				/*
				 * All peers that are no longer busy are marked as not busy.
				 */
				processFreePeers();

				try {

					Thread.sleep(500);

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (BencodingException e) {
			e.printStackTrace();

		} finally {

			synchronized (fileLock) {
				try {

					if (dataFile != null)
						dataFile.close();

				} catch (IOException e) {
					e.printStackTrace();
				}

				fileByteBuffer = null;
			}

			try {

				// send stopped message
				tracker.stop(peerId, port, uploaded, downloaded, left, encodedInfoHash);

			} catch (IOException e) {
				e.printStackTrace();
			} catch (BencodingException e) {
				e.printStackTrace();
			}

			// shutdown all the peers
			for (Peer pr : peers.values()) {
				pr.getPeerConnection().shutdown();
			}
		}
	}

	public void processMessages() {
		Message msg;
		while ((msg = messages.poll()) != null) {
			handleMessage(peers.get(msg.getPeerId()), msg);
		}
	}

	private void handleMessage(Peer pr, Message msg) {

		/*
		 * Send handshake if not already sent.
		 */
		if (!pr.handshook) {

			if (msg.getType() == Constants.MessageType.Handshake) {
				ByteBuffer message = msg.getBytes();

				if (message.get() != 19 || ((ByteBuffer) message.slice().limit(19))
						.compareTo(ByteBuffer.wrap(Constants.PROTOCOL_HEADER)) != 0) {
					pr.getPeerConnection().shutdown();
					return;
				}

				if (((ByteBuffer) message.slice().position(19 + 8).limit(20)).compareTo(torrentInfo.info_hash) != 0) {
					pr.getPeerConnection().shutdown();
					return;
				}

				if (((ByteBuffer) message.slice().position(19 + 8 + 20)).compareTo(pr.getPeerId()) != 0) {
					pr.getPeerConnection().shutdown();
					return;
				}

				ByteBuffer bf = getBitField();
				if (bf != null) {
					pr.getPeerConnection().sendBitfield(bf);
				}

				pr.handshook = true;
				return;
			}

		}

		switch (msg.getType()) {

		case Handshake:
			break;

		case Choke:
			pr.choked = true;
			pr.outstandingRequests = 0;
			break;

		case Unchoke:
			pr.choked = false;
			break;

		case Interested:
			pr.interested = true;
			break;

		case NotInterested:
			pr.interested = false;
			break;

		case Have:
			pr.setPieceAvailable(msg.getIndex());
			if (!pr.weHaveInterest && !piecesHad.get(msg.getIndex())) {
				pr.weHaveInterest = true;
				pr.getPeerConnection().sendInterested();
			}
			break;

		case Bitfield:
			pr.setAvailablePieces(msg.getBitfield());
			BitSet tmp = ((BitSet) piecesHad.clone());
			tmp.flip(0, piecesHad.size());

			if (!tmp.intersects(pr.getAvailablePieces())) {
				pr.weHaveInterest = false;
				pr.getPeerConnection().sendNotInterested();

			} else {
				pr.weHaveInterest = true;
				pr.getPeerConnection().sendInterested();
			}
			break;

		case Request:
			if (!pr.choking) {
				pr.getPeerConnection().sendPiece(msg.getIndex(), msg.getBegin(), msg.getLength(),
						pieces.get(msg.getIndex()).getByteBuffer());
				uploaded += msg.getLength();
			}
			break;

		case Piece:
			Piece pc = pieces.get(msg.getIndex());
			pr.outstandingRequests--;

			((ByteBuffer) pc.getByteBuffer().position(msg.getBegin())).put(msg.getBytes());
			pc.putSlice(msg.getBegin() / Piece.SLICE_SIZE);

			int slice = pc.getNextSlice();
			if (slice == -1) {

				if (!pc.isLoadingSlices()) {
					putPiece(pc);
				}

			} else {
				pr.getPeerConnection().sendRequest(pc.getIndex(), pc.getBeginOfSlice(slice),
						pc.getLengthOfSlice(slice));
			}
			break;

		case Cancel:
			break;

		default:

		}
	}

	private void processFreePeers() {
		for (Peer p : peers.values()) {
			if (!p.handshook) {
				continue;
			}

			if (!p.choked && p.outstandingRequests < 5) {
				Piece pc = choosePiece(p);

				// There's no piece to download from this peer.
				if (pc == null) {
					continue;
				}

				int slice = pc.getNextSlice();
				if (slice != -1) {
					p.outstandingRequests++;
					p.getPeerConnection().sendRequest(pc.getIndex(), pc.getBeginOfSlice(slice),
							pc.getLengthOfSlice(slice));
				}
			}
		}
	}

	private Piece choosePiece(Peer pr) {
		int[] pieceRanks = new int[pieces.size()];

		for (Piece piece : pieces) {
			if (piece.getState() == Piece.PieceState.INCOMPLETE && pr.canGetPiece(piece.getIndex())) {
				pieceRanks[piece.getIndex()] = 0;

			} else {
				pieceRanks[piece.getIndex()] = -1;
			}
		}

		for (Peer peer : peers.values()) {
			for (Piece piece : pieces) {
				if (peer.canGetPiece(piece.getIndex()) && pieceRanks[piece.getIndex()] != -1) {
					pieceRanks[piece.getIndex()]++;
				}
			}
		}

		int leastPieceIndex = -1, leastPieceValue = -1;

		for (int i = 0; i < pieceRanks.length; i++) {
			if (leastPieceIndex == -1 && pieceRanks[i] != -1) {
				leastPieceIndex = i;
				leastPieceValue = pieceRanks[i];

			} else if (leastPieceValue != -1 && leastPieceValue > pieceRanks[i] && pieceRanks[i] != -1) {
				leastPieceIndex = i;
				leastPieceValue = pieceRanks[i];
			}
		}

		if (leastPieceIndex == -1)
			return null;

		return pieces.get(leastPieceIndex);
	}

	/**
	 * If a peer dies send it a handshake.
	 * 
	 * @param peerId
	 *            peer id of peer that died.
	 * @throws UnsupportedEncodingException
	 */
	public void peerDying(ByteBuffer peerId) throws UnsupportedEncodingException {
		System.out.println("Peer " + byteBufferToString(peerId) + " died.");
		peers.get(peerId).handshook = false;
		peers.get(peerId).choked = true;
		peers.get(peerId).getPeerConnection().sendHandshake(this.torrentInfo.info_hash,
				ByteBuffer.wrap(this.peerId.getBytes()));
		System.out.println("Handshake sent to " + byteBufferToString(peerId));
	}

	public static String byteBufferToString(ByteBuffer b) throws UnsupportedEncodingException {
		String str = new String(b.array(), "UTF-8");
		return str;
	}

	/**
	 * Write the piece data to the piece buffer
	 *
	 * @param piece
	 *            A piece object representation to be added
	 * @return whether or not this piece validated.
	 */
	public boolean putPiece(Piece piece) {
		MessageDigest md;
		byte[] sha1 = null;
		try {

			md = MessageDigest.getInstance("SHA-1");
			sha1 = md.digest(piece.getByteBuffer().array());

		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		synchronized (peerLock) {
			synchronized (fileLock) {
				if (Arrays.equals(sha1, piece.getHash())) {

					fileByteBuffer.position(piece.getIndex() * torrentInfo.piece_length);
					fileByteBuffer.put(piece.getByteBuffer());
					piece.setState(Piece.PieceState.COMPLETE);
					piecesHad.set(piece.getIndex());

					for (Peer p : peers.values()) {
						p.getPeerConnection().sendHave(piece.getIndex());
						BitSet tmp = ((BitSet) piecesHad.clone());
						tmp.flip(0, piecesHad.size());

						if (!tmp.intersects(p.getAvailablePieces())) {
							p.weHaveInterest = false;
							p.getPeerConnection().sendNotInterested();
						}
					}

					// Update download percentage.
					downloaded += piece.getSize();
					left -= piece.getSize();
					System.out.println("Download " + 100 * downloaded / (downloaded + left) + "% complete");

				} else {
					piece.clearSlices();
					piece.setState(Piece.PieceState.INCOMPLETE);
					return false;
				}
			}
		}

		if (piecesHad.nextClearBit(0) == pieces.size() && !sentComplete) {
			sentComplete = true;
			try {

				tracker.complete(peerId, port, uploaded, downloaded, left, encodedInfoHash);

			} catch (IOException e) {
				e.printStackTrace();
			} catch (BencodingException e) {
				e.printStackTrace();
			}
		}

		return true;
	}

	/**
	 * Calculates and creates an ArrayList of pieces to be downloaded
	 *
	 * @return An ArrayList of pieces
	 */
	private ArrayList<Piece> generatePieces() {
		ArrayList<Piece> al = new ArrayList<Piece>();
		int total = torrentInfo.file_length;

		for (int i = 0; i < torrentInfo.piece_hashes.length; ++i, total -= torrentInfo.piece_length) {
			al.add(new Piece(i, Math.min(total, torrentInfo.piece_length), torrentInfo.piece_hashes[i]));
		}

		this.piecesHad = new BitSet(al.size());
		return al;
	}

	/**
	 * URL encodes the InfoHash byte array
	 *
	 * @param infoHashByteArray
	 *            Byte array from torrent file
	 * @return The encoded InfoHash as a string
	 */
	private String encodeInfoHash(byte[] infoHashByteArray) {
		StringBuilder sb = new StringBuilder();
		for (byte b : infoHashByteArray) {
			sb.append(String.format("%%%02X", b));
		}

		return sb.toString();
	}

	/**
	 * Calculates a BitField for the torrent's current state.
	 * 
	 * @return A byte buffer containing the torrent's current BitField. This is
	 *         suitable to be sent across the network
	 */
	public ByteBuffer getBitField() {
		synchronized (fileLock) {

			// Ceiling(pieces.size() / 8)
			byte[] bf = new byte[(pieces.size() + 8 - 1) / 8];
			for (int i = 0; i < pieces.size(); ++i) {
				bf[i / 8] |= (pieces.get(i).getState() == Piece.PieceState.COMPLETE) ? 0x80 >> (i % 8) : 0;
			}

			boolean fail = false;
			for (int i = 0; i < pieces.size() / 8 && !fail; ++i) {
				fail = (bf[i] != 0);
			}
			if (fail) {
				return ByteBuffer.wrap(bf);
			}

			return null;
		}
	}

	public void recvMessage(Message message) {
		messages.add(message);
	}

}

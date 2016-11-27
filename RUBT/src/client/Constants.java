package client;

import java.nio.ByteBuffer;

public class Constants {

	public static final ByteBuffer INCOMPLETE = ByteBuffer
			.wrap(new byte[] { 'i', 'n', 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e' });
	public static final ByteBuffer PEERS = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', 's' });
	public static final ByteBuffer DOWNLOADED = ByteBuffer
			.wrap(new byte[] { 'd', 'o', 'w', 'n', 'l', 'o', 'a', 'd', 'e', 'd' });
	public static final ByteBuffer COMPLETE = ByteBuffer.wrap(new byte[] { 'c', 'o', 'm', 'p', 'l', 'e', 't', 'e' });
	public static final ByteBuffer MIN_INTERVAL = ByteBuffer
			.wrap(new byte[] { 'm', 'i', 'n', ' ', 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public static final ByteBuffer INTERVAL = ByteBuffer.wrap(new byte[] { 'i', 'n', 't', 'e', 'r', 'v', 'a', 'l' });
	public static final ByteBuffer PEER_IP = ByteBuffer.wrap(new byte[] { 'i', 'p' });
	public static final ByteBuffer PEER_ID = ByteBuffer.wrap(new byte[] { 'p', 'e', 'e', 'r', ' ', 'i', 'd' });
	public static final ByteBuffer PEER_PORT = ByteBuffer.wrap(new byte[] { 'p', 'o', 'r', 't' });

	public static final byte[] PROTOCOL_HEADER = new byte[] { 'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't', ' ',
			'p', 'r', 'o', 't', 'o', 'c', 'o', 'l' };
	public static final byte[] KEEP_ALIVE = new byte[] { 0, 0, 0, 0 };
	public static final byte[] CHOKE = new byte[] { 0, 0, 0, 1, 0 };
	public static final byte[] UNCHOKE = new byte[] { 0, 0, 0, 1, 1 };
	public static final byte[] INTERESTED = new byte[] { 0, 0, 0, 1, 2 };
	public static final byte[] NOT_INTERESTED = new byte[] { 0, 0, 0, 1, 3 };
	public static final byte[] HAVE = new byte[] { 0, 0, 0, 5, 4 };
	public static final byte[] BITFIELD = new byte[] { 0, 0, 0, 0, 5 };
	public static final byte[] REQUEST = new byte[] { 0, 0, 0, 13, 6 };
	public static final byte[] PIECE = new byte[] { 0, 0, 0, 0, 7 };

	public enum MessageType {
		Handshake, Choke, Unchoke, Interested, NotInterested, Have, Bitfield, Request, Piece, Cancel,
	}

}

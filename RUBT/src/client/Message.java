package client;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.HashMap;

import client.Constants.MessageType;

/**
 * Class Responsible for creating a message to send.
 * 
 * @author Rushiraj
 */
public class Message implements Comparable<Message> {

	@Override
	public int compareTo(Message Message) {
		return peerId.compareTo(Message.peerId);
	}

	private ByteBuffer peerId;
	private MessageType type;
	private HashMap<String, Object> data;

	public static Message Handshake(ByteBuffer peerId, ByteBuffer msg) {
		Message m = new Message(peerId);
		m.type = MessageType.Handshake;
		m.data.put("bytes", msg);
		return m;
	}

	public static Message Choke(ByteBuffer ip) {
		Message m = new Message(ip);
		m.type = MessageType.Choke;
		return m;
	}

	public static Message Unchoke(ByteBuffer ip) {
		Message m = new Message(ip);
		m.type = MessageType.Unchoke;
		return m;
	}

	public static Message Interested(ByteBuffer ip) {
		Message m = new Message(ip);
		m.type = MessageType.Interested;
		return m;
	}

	public static Message NotInterested(ByteBuffer ip) {
		Message m = new Message(ip);
		m.type = MessageType.NotInterested;
		return m;
	}

	public static Message Have(ByteBuffer ip, int index) {
		Message m = new Message(ip);
		m.type = MessageType.Have;
		m.data.put("index", index);
		return m;
	}

	public static Message Bitfield(ByteBuffer ip, BitSet bitfield) {
		Message m = new Message(ip);
		m.type = MessageType.Bitfield;
		m.data.put("bitfield", bitfield);
		return m;
	}

	public static Message Request(ByteBuffer ip, int index, int begin, int length) {
		Message m = new Message(ip);
		m.type = MessageType.Request;
		m.data.put("index", index);
		m.data.put("begin", begin);
		m.data.put("length", length);
		return m;
	}

	public static Message Piece(ByteBuffer ip, int index, int begin, ByteBuffer bytes) {
		Message m = new Message(ip);
		m.type = MessageType.Piece;
		m.data.put("index", index);
		m.data.put("begin", begin);
		m.data.put("bytes", bytes);
		return m;
	}

	public static Message Cancel(ByteBuffer ip, int index, int begin, int length) {
		Message m = new Message(ip);
		m.type = MessageType.Cancel;
		m.data.put("index", index);
		m.data.put("begin", begin);
		m.data.put("length", length);
		return m;
	}

	public ByteBuffer getPeerId() {
		return peerId;
	}

	public MessageType getType() {
		return type;
	}

	public int getIndex() {
		if (type == MessageType.Have || type == MessageType.Request || type == MessageType.Piece
				|| type == MessageType.Cancel)
			return (Integer) data.get("index");
		return -1;
	}

	public int getBegin() {
		if (type == MessageType.Request || type == MessageType.Piece || type == MessageType.Cancel)
			return (Integer) data.get("begin");
		return -1;
	}

	public int getLength() {
		if (type == MessageType.Request || type == MessageType.Cancel)
			return (Integer) data.get("length");
		return -1;
	}

	public BitSet getBitfield() {
		if (type == MessageType.Bitfield)
			return (BitSet) data.get("bitfield");
		return null;
	}

	public ByteBuffer getBytes() {
		if (type == MessageType.Piece || type == MessageType.Handshake)
			return (ByteBuffer) data.get("bytes");
		return null;
	}

	private Message(ByteBuffer peerId) {
		this.peerId = peerId;
		data = new HashMap<String, Object>();

	}
}

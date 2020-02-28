package socketio_client.parser;

import exceptions.SocketIOException;
import exceptions.SocketIOParserException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.Character.isDigit;

/**
 * Socket.IO parser implementation. Socket.IO uses json to represent a packet's data.
 * <p>
 * If the packet contains any binary data(byte[]), they will be replaces with a json object like so:
 *      {@code {"_placeholder": true, "num": #placeholder_num}}, and then the replaced byte[]s will be sent separately.
 * <p> For example:
 *      {@code {"name": "john", "file": [1,2,3]}} will be transformed into {@code {"name": "john", "file": {"_placeholder": true, "num": 0}}}
 *      and the byte[]{1,2,3} will be sent separately.
 * <p> Besides the data part, the metadata of a packet(namespace, id, type) is encoded like so:
 * <br> Value of packet's type
 * <br> + (if any) number of binary attachments (number of byte[]s in data) + ATTACHMENT_SEPARATOR("-")
 * <br> + (if namespace isn't "/") NAMESPACE + NAMESPACE_END(",")
 * <br> + (if id &gt; -1) packet's id
 * <br> + (if not null) packet's json encoded data.
 * If the packet's type is {@link socketio_client.parser.Type#EVENT}, data will be encoded to json array,
 *      which will have event's name as the first argument.
 */
public class ParserImpl implements Parser {

    private static final String PLACEHOLDER = "_placeholder";
    private static final String NUM = "num";
    private static final String ATTACHMENT_SEPARATOR = "-";
    private static final String NAMESPACE_START = "/";
    private static final String NAMESPACE_END = ",";

    public static class EncoderImpl implements Parser.Encoder {

        public void encode(Packet packet, EncodeCallback callback) {
            Type type = packet.type;
            if(type == Type.BINARY_EVENT || type == Type.BINARY_ACK) {
                callback.call(encodeBinaryPacket(packet));
                return;
            }
            callback.call(encodeStringPacket(packet));
        }

        private String encodeStringPacket(Packet packet) {
            StringBuilder builder = new StringBuilder();
            builder.append(packet.type.value());

            if(packet.type == Type.BINARY_EVENT || packet.type == Type.BINARY_ACK)
                builder.append(packet.attachmentSize)
                        .append(ATTACHMENT_SEPARATOR);

            if(!Packet.DEFAULT_NAMESPACE.equals(packet.namespace))
                builder.append(packet.namespace)
                        .append(NAMESPACE_END);

            if(packet.id > -1)
                builder.append(packet.id);

            if(packet.data != null)
                builder.append(packet.data);

            return builder.toString();
        }

        private Object[] encodeBinaryPacket(Packet packet) {
            List<Object> parts = new LinkedList<>();
            int numOfByteArrays = consumeBinaryData(packet.data, 0, parts::add);
            packet.attachmentSize = numOfByteArrays;
            parts.add(0, encodeStringPacket(packet));
            return parts.toArray();
        }

        private int consumeBinaryData(Object data, int numOfByteArr, Consumer<Object> consumer) {
            if(data == null) return numOfByteArr;
            if(data instanceof JSONArray) {
                JSONArray arrayObj = (JSONArray) data;
                for(int i = 0; i < arrayObj.length(); i++) {
                    if(arrayObj.get(i) instanceof byte[]) {
                        consumer.accept(arrayObj.get(i));
                        arrayObj.put(i, placeholderObject(numOfByteArr++));
                    } else {
                        numOfByteArr = consumeBinaryData(arrayObj.get(i), numOfByteArr, consumer);
                    }
                }
            }
            if(data instanceof JSONObject) {
                JSONObject jObj = (JSONObject) data;
                for(String key : jObj.keySet()) {
                    if(jObj.get(key) instanceof byte[]) {
                        consumer.accept(jObj.get(key));
                        jObj.put(key, placeholderObject(numOfByteArr++));
                    } else {
                        numOfByteArr = consumeBinaryData(jObj.get(key), numOfByteArr, consumer);
                    }
                }
            }

            return numOfByteArr;
        }

        private JSONObject placeholderObject(int num) {
            return new JSONObject()
                    .put(PLACEHOLDER, true)
                    .put(NUM, num);
        }
    }

    public static class DecoderImpl implements Parser.Decoder {

        private final Logger logger = LoggerFactory.getLogger(DecoderImpl.class);

        private Packet pendingPacket;
        private int attachmentsLeftToWait;

        public void add(String encodedStr, DecodeCallback callback) {
            Packet packet = decodeStringData(encodedStr);
            if(packet.type != Type.BINARY_EVENT && packet.type != Type.BINARY_ACK) {
                callback.call(packet);
                return;
            }

            // Packet is either binary_event or binary_ack
            // If there are no attachments to wait for, notify caller.
            if(packet.attachmentSize == 0) {
                callback.call(packet);
                return;

            }

            // Wait for attachments to arrive. They will arrive as pure byte arrays(byte[]).
            // To reconstruct the original packet, we have to substitute
            //      {_placeholder: true, num: _} objects with arriving byte arrays(byte[]).
            // There can be at most 1 pending packet at any given time.
            // If we receive a binary packet (with attachments) while there is already a pending one, something must be wrong with server.
            if(pendingPacket != null) {
                SocketIOParserException e = new SocketIOParserException("Received a binary packet while another one is already waiting for re-construction.");
                logException(e);
                throw e;
            }

            pendingPacket = packet;
            attachmentsLeftToWait = packet.attachmentSize;
        }

        public void add(byte[] bytes, DecodeCallback callback) {
            // Received a pure byte array(byte[]).
            // This means there must be a reconstruction pending binary packet.
            if(pendingPacket == null || attachmentsLeftToWait == 0) {
                SocketIOParserException e = new SocketIOParserException("Received a byte[] when there was no re-construction pending packet.");
                logException(e);
                throw e;
            }

            boolean isPlaceFound = substitutePlaceholderWithByteArray(pendingPacket.data, bytes, false);
            if(!isPlaceFound) {
                SocketIOParserException e = new SocketIOParserException("No placeholder object found for this byte[].");
                logException(e);
                throw e;
            }

            // We are done with attachments. Notify the caller and clear pending packet.
            --attachmentsLeftToWait;
            if(attachmentsLeftToWait == 0) {
                callback.call(pendingPacket);
                pendingPacket = null;
            }
        }

        private Packet decodeStringData(String encodedStr) {
            int typeValue = Character.digit(encodedStr.charAt(0), 10);
            int dataStartIndex = encodedStr.indexOf("[");
            // The metadata separators are only searched until this index.
            // If data isn't present, this variable will be set to -1, which will cause all separator searched to fail.
            // Therefore, if data isn't present, set it to Integer.MAX_VALUE so that separator searches can progress.
            if(dataStartIndex == -1)
                dataStartIndex = Integer.MAX_VALUE;

            if(!Type.isValid(typeValue)) {
                SocketIOParserException e = new SocketIOParserException("Type (" + typeValue + ") is not a valid Socket.IO packet type.");
                logException(e);
                throw e;
            }

            Type type = Type.getTypeForValue(typeValue);
            Packet packet = new Packet(type);
            int nextReadIndex = 1; // index of the next character to read.

            // If attachment separator "-" is found, then find the number of attachments
            int indexOfAttachmentSeparator = encodedStr.indexOf(ATTACHMENT_SEPARATOR, nextReadIndex);
            if(indexOfAttachmentSeparator > -1 && indexOfAttachmentSeparator < dataStartIndex) {
                packet.attachmentSize = Integer.parseInt(encodedStr.substring(1, indexOfAttachmentSeparator));
                nextReadIndex = indexOfAttachmentSeparator + 1;
            }

            int indexOfNamespaceStart = encodedStr.indexOf(NAMESPACE_START, nextReadIndex);
            if(indexOfNamespaceStart > -1 && indexOfNamespaceStart < dataStartIndex) {
                int indexOfNamespaceEnd = encodedStr.indexOf(NAMESPACE_END, indexOfNamespaceStart);
                if(indexOfNamespaceEnd == -1 || indexOfNamespaceEnd > dataStartIndex) {
                    SocketIOParserException e = new SocketIOParserException("Namespace end symbol (" + NAMESPACE_END + ") is not found.");
                    logException(e);
                    throw e;
                }
                packet.namespace = encodedStr.substring(indexOfNamespaceStart, indexOfNamespaceEnd);
                nextReadIndex = indexOfNamespaceEnd + 1;
            }

            // If there are no more chars to read, return.
            if(encodedStr.length() <= nextReadIndex)
                return packet;

            // Look for packet id, then packet data.
            char ch = encodedStr.charAt(nextReadIndex);
            if(isDigit(ch)) {
                int endOfId = nextReadIndex + 1;
                while(endOfId < encodedStr.length() && isDigit(encodedStr.charAt(endOfId)))
                    ++endOfId;
                int id = Integer.parseInt(encodedStr.substring(nextReadIndex, endOfId));
                nextReadIndex = endOfId;
                packet.id = id;
            }

            // If there are no more chars, return.
            if(encodedStr.length() <= nextReadIndex)
                return packet;

            packet.data = new JSONTokener(encodedStr.substring(nextReadIndex)).nextValue();
            return packet;
        }

        private boolean substitutePlaceholderWithByteArray(Object data, byte[] array, boolean placeholderFound) {
            if(placeholderFound) return true;
            if(data == null) return false;
            if(data instanceof JSONArray) {
                JSONArray arr = (JSONArray) data;
                for(int i = 0; i < arr.length(); i++) {
                    if(isPlaceholderObject(arr.get(i))) {
                        arr.put(i, array);
                        placeholderFound = true;
                    }
                    else
                        placeholderFound = substitutePlaceholderWithByteArray(arr.get(i), array, placeholderFound);

                    if(placeholderFound) return true;
                }
            }
            if(data instanceof JSONObject) {
                JSONObject jobj = (JSONObject) data;
                for(String key : jobj.keySet()) {
                    if(isPlaceholderObject(jobj.get(key))) {
                        jobj.put(key, array);
                        placeholderFound = true;
                    }
                    else
                        placeholderFound = substitutePlaceholderWithByteArray(jobj.get(key), array, placeholderFound);

                    if(placeholderFound) return true;
                }
            }

            return placeholderFound;
        }

        private boolean isPlaceholderObject(Object obj) {
            return obj instanceof JSONObject && ((JSONObject)obj).optBoolean(PLACEHOLDER);
        }

        private void logException(Exception e) {
            logger.error("Error during the decoding process", e);
        }
    }

}

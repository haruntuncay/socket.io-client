package socketio_client.parser;

public interface Parser {

    interface Encoder {

        void encode(Packet packet, EncodeCallback callback);

        interface EncodeCallback {

            void call(Object... args);
        }
    }

    interface Decoder {

        void add(String encodedObj, DecodeCallback callback);

        void add(byte[] bytes, DecodeCallback callback);

        interface DecodeCallback {

            void call(Packet packet);
        }
    }

}

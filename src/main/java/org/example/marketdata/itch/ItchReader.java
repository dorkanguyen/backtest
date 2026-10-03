package org.example.marketdata.itch;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Reads raw messages from a gzip-compressed Nasdaq TotalView-ITCH 5.0 file.
 *
 * <p>In the file every message is preceded by its length as a 2-byte number. The file is
 * decompressed on the fly, so the uncompressed data is never written to disk.
 */
public class ItchReader implements AutoCloseable {

    private static final int BUFFER_SIZE = 1 << 20;

    private final DataInputStream input;

    /**
     * Opens the file for reading.
     *
     * @param file path of the {@code .gz} file
     * @throws IOException if the file cannot be opened or is not a gzip file
     */
    public ItchReader(Path file) throws IOException {
        this.input = new DataInputStream(
                new BufferedInputStream(
                        new GZIPInputStream(Files.newInputStream(file), BUFFER_SIZE),
                        BUFFER_SIZE));
    }

    /**
     * Reads the next message.
     *
     * @return the message bytes (the first byte is the message type), or {@code null} at the end
     *     of the file
     * @throws IOException if the file cannot be read or ends in the middle of a message
     */
    public byte[] nextMessage() throws IOException {
        int length;
        try {
            length = input.readUnsignedShort();
        } catch (EOFException e) {
            return null;
        }
        byte[] message = new byte[length];
        input.readFully(message);
        return message;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}

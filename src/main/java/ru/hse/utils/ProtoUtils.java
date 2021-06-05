package ru.hse.utils;

import com.google.protobuf.InvalidProtocolBufferException;
import org.jetbrains.annotations.NotNull;
import ru.hse.data.ArrayProtos;
import ru.hse.data.IntArray;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.stream.IntStream;

public class ProtoUtils {

    public static void writeArray(OutputStream outputStream, IntArray array) throws IOException {
        Iterable<Integer> iterable = () -> IntStream.of(array.getData()).iterator();
        ArrayProtos.IntArray responseArray = ArrayProtos.IntArray.newBuilder().
                setId(array.getId()).
                addAllElements(iterable).
                build();
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        byte[] data = responseArray.toByteArray();
        dataOutputStream.writeInt(data.length);
        outputStream.write(data);
        outputStream.flush();
    }

    @NotNull
    public static ByteBuffer serialize(IntArray array) {
        Iterable<Integer> iterable = () -> IntStream.of(array.getData()).iterator();
        ArrayProtos.IntArray responseArray = ArrayProtos.IntArray.newBuilder().
                setId(array.getId()).
                addAllElements(iterable).
                build();
        byte[] data = responseArray.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(data.length + Integer.BYTES);
        buffer.putInt(data.length);
        buffer.put(data);
        buffer.flip();
        return buffer;
    }

    @NotNull
    public static IntArray readArray(InputStream inputStream) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(inputStream);
        int size = dataInputStream.readInt();
        ArrayProtos.IntArray requestArray = ArrayProtos.IntArray.parseFrom(inputStream.readNBytes(size));
        int[] data = requestArray.getElementsList().stream().mapToInt(i -> i).toArray();
        int id = requestArray.getId();
        return new IntArray(id, data);
    }

    @NotNull
    public static IntArray readArray(ByteBuffer buffer) throws InvalidProtocolBufferException {
        ArrayProtos.IntArray requestArray = ArrayProtos.IntArray.parseFrom(buffer);
        int[] data = requestArray.getElementsList().stream().mapToInt(i -> i).toArray();
        int id = requestArray.getId();
        return new IntArray(id, data);
    }
}

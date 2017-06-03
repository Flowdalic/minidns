package de.measite.minidns.source.async;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.measite.minidns.DNSMessage;
import de.measite.minidns.MiniDNSException;
import de.measite.minidns.MiniDnsFuture;
import de.measite.minidns.MiniDnsFuture.InternalMiniDnsFuture;
import de.measite.minidns.util.MultipleIoException;

public class AsyncDnsRequest {

    private static final Logger LOGGER = Logger.getLogger(AsyncDnsRequest.class.getName());

    private final InternalMiniDnsFuture<DNSMessage, IOException> future = new InternalMiniDnsFuture<DNSMessage, IOException>() {
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean res = super.cancel(mayInterruptIfRunning);
            cancelAsyncDnsRequest();
            return res;
        }
    };

    private final DNSMessage request;

    private final int udpPayloadSize;

    private final SocketAddress socketAddress;

    private final AsyncNetworkDataSource asyncNds;

    private ByteBuffer writeBuffer;

    private List<IOException> exceptions;

    private SelectionKey selectionKey;

    final long deadline;

    AsyncDnsRequest(DNSMessage request, InetAddress inetAddress, int port, int udpPayloadSize, AsyncNetworkDataSource asyncNds) {
        this.request = request;
        this.udpPayloadSize = udpPayloadSize;
        this.asyncNds = asyncNds;

        deadline = System.currentTimeMillis() + asyncNds.getTimeout();
        socketAddress = new InetSocketAddress(inetAddress, port);
    }

    private void ensureWriteBufferIsInitialized() {
        if (writeBuffer != null) {
            if (!writeBuffer.hasRemaining()) {
                writeBuffer.rewind();
            }
            return;
        }
        writeBuffer = request.getInByteBuffer();
    }

    private synchronized void cancelAsyncDnsRequest() {
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        asyncNds.cancelled(this);
    }

    private synchronized void registerWithSelector(SelectableChannel channel, int ops, ChannelSelectedHandler handler)
            throws ClosedChannelException {
        if (future.isCancelled()) {
            return;
        }
        selectionKey = asyncNds.registerWithSelector(channel, ops, handler);
    }

    private void addException(IOException e) {
        if (exceptions == null) {
            exceptions = new ArrayList<>(4);
        }
        exceptions.add(e);
    }

    private final void gotResult(DNSMessage result) {
        // TODO Add caching here?
        future.setResult(result);
    }

    MiniDnsFuture<DNSMessage, IOException> getFuture() {
        return future;
    }

    boolean wasDeadlineMissedAndFutureNotified() {
        if (System.currentTimeMillis() < deadline) {
            return false;
        }

        future.setException(new IOException("Timeout"));
        return true;
    }

    void startHandling() {
        startUdpRequest();
    }

    private void abortUdpRequestAndCleanup(DatagramChannel datagramChannel, String errorMessage, IOException exception) {
        LOGGER.log(Level.SEVERE, errorMessage, exception);
        addException(exception);

        try {
            datagramChannel.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Exception closing datagram channel", e);
            addException(e);
        }

        startTcpRequest();
    }

    private void startUdpRequest() {
        if (future.isCancelled()) {
            return;
        }

        DatagramChannel datagramChannel;
        try {
            datagramChannel = DatagramChannel.open();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Exception opening datagram channel", e);
            addException(e);
            startTcpRequest();
            return;
        }

        try {
            datagramChannel.configureBlocking(false);
        } catch (IOException e) {
            abortUdpRequestAndCleanup(datagramChannel, "Exception configuring datagram channel", e);
            return;
        }

        try {
            registerWithSelector(datagramChannel, SelectionKey.OP_CONNECT, new UdpConnectedChannelSelectedHandler(future));
        } catch (ClosedChannelException e) {
            abortUdpRequestAndCleanup(datagramChannel, "Exception registering datagram channel for OP_CONNECT", e);
            return;
        }

        try {
            datagramChannel.connect(socketAddress);
        } catch (IOException e) {
            abortUdpRequestAndCleanup(datagramChannel, "Exception connecting datagram channel", e);
            return;
        }
    }

    class UdpConnectedChannelSelectedHandler extends ChannelSelectedHandler {

        UdpConnectedChannelSelectedHandler(Future<?> future) {
            super(future);
        }

        @Override
        protected void handleChannelSelectedAndNotCancelled(SelectableChannel channel, SelectionKey selectionKey) {
            DatagramChannel datagramChannel = (DatagramChannel) channel;

            if (!datagramChannel.isConnected()) {
                throw new AssertionError();
            }

            try {
                registerWithSelector(datagramChannel, SelectionKey.OP_WRITE, new UdpWritableChannelSelectedHandler(future));
            } catch (ClosedChannelException e) {
                abortUdpRequestAndCleanup(datagramChannel, "Exception registering datagram channel for OP_WRITE", e);
                return;
            }
        }
    }

    class UdpWritableChannelSelectedHandler extends ChannelSelectedHandler {

        UdpWritableChannelSelectedHandler(Future<?> future) {
            super(future);
        }

        @Override
        public void handleChannelSelectedAndNotCancelled(SelectableChannel channel, SelectionKey selectionKey) {
            DatagramChannel datagramChannel = (DatagramChannel) channel;

            ensureWriteBufferIsInitialized();

            try {
                datagramChannel.write(writeBuffer);
            } catch (IOException e) {
                abortUdpRequestAndCleanup(datagramChannel, "Exception writing to datagram channel", e);
                return;
            }

            if (writeBuffer.hasRemaining()) {
                try {
                    registerWithSelector(datagramChannel, SelectionKey.OP_WRITE, this);
                } catch (ClosedChannelException e) {
                    abortUdpRequestAndCleanup(datagramChannel, "Exception registering datagram channel for OP_WRITE", e);
                }
                return;
            }

            try {
                registerWithSelector(datagramChannel, SelectionKey.OP_READ, new UdpReadableChannelSelectedHandler(future));
            } catch (ClosedChannelException e) {
                abortUdpRequestAndCleanup(datagramChannel, "Exception registering datagram channel for OP_READ", e);
                return;
            }
        }

    }

    class UdpReadableChannelSelectedHandler extends ChannelSelectedHandler {

        UdpReadableChannelSelectedHandler(Future<?> future) {
            super(future);
        }

        final ByteBuffer byteBuffer = ByteBuffer.allocate(udpPayloadSize);

        @Override
        public void handleChannelSelectedAndNotCancelled(SelectableChannel channel, SelectionKey selectionKey) {
            DatagramChannel datagramChannel = (DatagramChannel) channel;

            try {
                datagramChannel.read(byteBuffer);
            } catch (IOException e) {
                abortUdpRequestAndCleanup(datagramChannel, "Exception reading from datagram channel", e);
                return;
            }

            try {
                datagramChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Exception closing datagram channel", e);
                addException(e);
            }

            DNSMessage response;
            try {
                response = new DNSMessage(byteBuffer.array());
            } catch (IOException e) {
                abortUdpRequestAndCleanup(datagramChannel, "Exception constructing dns message from datagram channel", e);
                return;
            }

            if (response.id != request.id) {
                addException(new MiniDNSException.IdMismatch(request, response));
                startTcpRequest();
                return;
            }

            if (response.truncated) {
                startTcpRequest();
                return;
            }

            gotResult(response);
        }
    }

    private void abortTcpRequestAndCleanup(SocketChannel socketChannel, String errorMessage, IOException exception) {
        LOGGER.log(Level.SEVERE, errorMessage, exception);
        addException(exception);

        if (socketChannel != null && socketChannel.isOpen()) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Exception closing socket channel", e);
                addException(e);
            }
        }

        future.setException(MultipleIoException.toIOException(exceptions));
    }

    private void startTcpRequest() {
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
        } catch (IOException e) {
            abortTcpRequestAndCleanup(socketChannel, "Exception opening socket channel", e);
            return;
         }

        try {
            socketChannel.configureBlocking(false);
        } catch (IOException e) {
            abortTcpRequestAndCleanup(socketChannel, "Exception configuring socket channel", e);
            return;
       }

        try {
            registerWithSelector(socketChannel, SelectionKey.OP_CONNECT, new TcpConnectedChannelSelectedHandler(future));
        } catch (ClosedChannelException e) {
            abortTcpRequestAndCleanup(socketChannel, "Exception registering socket channel", e);
            return;
        }

        try {
            socketChannel.connect(socketAddress);
        } catch (IOException e) {
            abortTcpRequestAndCleanup(socketChannel, "Exception connecting socket channel", e);
            return;
        }
    }

    class TcpConnectedChannelSelectedHandler extends ChannelSelectedHandler {

        TcpConnectedChannelSelectedHandler(Future<?> future) {
            super(future);
        }

        @Override
        public void handleChannelSelectedAndNotCancelled(SelectableChannel channel, SelectionKey selectionKey) {
            SocketChannel socketChannel = (SocketChannel) channel;

            boolean connected;
            try {
                connected = socketChannel.finishConnect();
            } catch (IOException e) {
                abortTcpRequestAndCleanup(socketChannel, "Exception finish connecting socket channel", e);
                return;
            }

            assert connected;

            try {
                registerWithSelector(socketChannel, SelectionKey.OP_WRITE, new TcpWritableChannelSelectedHandler(future));
            } catch (ClosedChannelException e) {
                abortTcpRequestAndCleanup(socketChannel, "Exception registering socket channel for OP_WRITE", e);
                return;
            }
        }

    }

    class TcpWritableChannelSelectedHandler extends ChannelSelectedHandler {

        TcpWritableChannelSelectedHandler(Future<?> future) {
            super(future);
        }

        @Override
        public void handleChannelSelectedAndNotCancelled(SelectableChannel channel, SelectionKey selectionKey) {
            SocketChannel socketChannel = (SocketChannel) channel;

            ensureWriteBufferIsInitialized();

            try {
                socketChannel.write(writeBuffer);
            } catch (IOException e) {
                abortTcpRequestAndCleanup(socketChannel, "Exception writing to socket channel", e);
                return;
            }

            if (writeBuffer.hasRemaining()) {
                try {
                    registerWithSelector(socketChannel, SelectionKey.OP_WRITE, this);
                } catch (ClosedChannelException e) {
                    abortTcpRequestAndCleanup(socketChannel, "Exception registering socket channel for OP_WRITE", e);
                }
                return;
            }

            try {
                registerWithSelector(socketChannel, SelectionKey.OP_READ, new TcpReadableChannelSelectedHandler(future));
            } catch (ClosedChannelException e) {
                abortTcpRequestAndCleanup(socketChannel, "Exception registering socket channel for OP_READ", e);
                return;
            }
        }

    }

    class TcpReadableChannelSelectedHandler extends ChannelSelectedHandler {

        TcpReadableChannelSelectedHandler(Future<?> future) {
            super(future);
        }

        final ByteBuffer messageLengthByteBuffer = ByteBuffer.allocate(2);

        ByteBuffer byteBuffer;

        @Override
        public void handleChannelSelectedAndNotCancelled(SelectableChannel channel, SelectionKey selectionKey) {
            SocketChannel socketChannel = (SocketChannel) channel;

            if (byteBuffer == null) {
                try {
                    socketChannel.read(messageLengthByteBuffer);
                } catch (IOException e) {
                    abortTcpRequestAndCleanup(socketChannel, "Exception reading from socket channel", e);
                    return;
                }

                if (messageLengthByteBuffer.hasRemaining()) {
                    try {
                        registerWithSelector(socketChannel, SelectionKey.OP_READ, this);
                    } catch (ClosedChannelException e) {
                        abortTcpRequestAndCleanup(socketChannel, "Exception registering socket channel for OP_READ", e);
                    }
                    return;
                }
                InputStream is = new ByteArrayInputStream(messageLengthByteBuffer.array());
                DataInputStream dis = new DataInputStream(is);
                int messageLength;
                try {
                    messageLength = dis.readUnsignedShort();
                } catch (IOException e) {
                    throw new Error(e);
                }
                byteBuffer = ByteBuffer.allocate(messageLength);
            }

            try {
                socketChannel.read(byteBuffer);
            } catch (IOException e) {
                throw new Error(e);
            }

            if (byteBuffer.hasRemaining()) {
                try {
                    registerWithSelector(socketChannel, SelectionKey.OP_READ, this);
                } catch (ClosedChannelException e) {
                    abortTcpRequestAndCleanup(socketChannel, "Exception registering socket channel for OP_READ", e);
                }
                return;
            }

            try {
                socketChannel.close();
            } catch (IOException e) {
                addException(e);
            }

            DNSMessage response;
            try {
                response = new DNSMessage(byteBuffer.array());
            } catch (IOException e) {
                abortTcpRequestAndCleanup(socketChannel, "Exception creating DNS message form socket channel bytes", e);
                return;
            }

            if (request.id != response.id) {
                MiniDNSException idMismatchException = new MiniDNSException.IdMismatch(request, response);
                addException(idMismatchException);
                AsyncDnsRequest.this.future.setException(MultipleIoException.toIOException(exceptions));
                return;
            }

            gotResult(response);
        }

    }

}
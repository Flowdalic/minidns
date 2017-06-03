package de.measite.minidns.source.async;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class ChannelSelectedHandler {

    private static final Logger LOGGER = Logger.getLogger(ChannelSelectedHandler.class.getName());

    final Future<?> future;

    ChannelSelectedHandler(Future<?> future) {
        this.future = future;
    }

    void handleChannelSelected(SelectableChannel channel, SelectionKey selectionKey) {
        if (future.isCancelled()) {
            try {
                channel.close();
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "Could not close channel", e);
            }
            return;
        }
        handleChannelSelectedAndNotCancelled(channel, selectionKey);
    }

    protected abstract void handleChannelSelectedAndNotCancelled(SelectableChannel channel, SelectionKey selectionKey);

}

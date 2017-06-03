package de.measite.minidns.integrationtest;

import java.io.IOException;

import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.MiniDnsFuture;
import de.measite.minidns.Record;
import de.measite.minidns.source.async.AsyncNetworkDataSource;

public class AsyncApiTest {

    public static void main(String[] args) throws IOException {
        asyncApiTest();
    }

    public static void asyncApiTest() throws IOException {
        DNSClient client = new DNSClient();
        client.setDataSource(new AsyncNetworkDataSource());
        client.getDataSource().setTimeout(60 * 60 * 1000);

        MiniDnsFuture<DNSMessage, IOException> future = client.queryAsync("example.com", Record.TYPE.NS);
        DNSMessage response = future.getOrThrow();
    }
}

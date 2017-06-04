package de.measite.minidns.integrationtest;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.DNSMessage.RESPONSE_CODE;
import de.measite.minidns.MiniDnsFuture;
import de.measite.minidns.Record;
import de.measite.minidns.source.DNSDataSource;
import de.measite.minidns.source.async.AsyncNetworkDataSource;

public class AsyncApiTest {

    public static void main(String[] args) throws IOException {
        tcpAsyncApiTest();
    }

    public static void simpleAsyncApiTest() throws IOException {
        DNSClient client = new DNSClient();
        client.setDataSource(new AsyncNetworkDataSource());
        client.getDataSource().setTimeout(60 * 60 * 1000);

        MiniDnsFuture<DNSMessage, IOException> future = client.queryAsync("example.com", Record.TYPE.NS);
        DNSMessage response = future.getOrThrow();
        assertEquals(RESPONSE_CODE.NO_ERROR, response.responseCode);
    }

    public static void tcpAsyncApiTest() throws IOException {
        DNSDataSource dataSource = new AsyncNetworkDataSource();
        dataSource.setTimeout(60 * 60 * 1000);
        dataSource.setUdpPayloadSize(256);

        DNSClient client = new DNSClient();
        client.setDataSource(dataSource);
        client.setAskForDnssec(true);

        MiniDnsFuture<DNSMessage, IOException> future = client.queryAsync("google.com", Record.TYPE.AAAA);
        DNSMessage response = future.getOrThrow();
        assertEquals(RESPONSE_CODE.NO_ERROR, response.responseCode);
    }
}

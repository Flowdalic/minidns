package org.minidns.hla;

import org.junit.jupiter.api.Test;
import org.minidns.DnsClient;
import org.minidns.dnsqueryresult.DnsQueryResult;
import org.minidns.record.Record;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.minidns.DnsWorld.a;
import static org.minidns.DnsWorld.applyZones;
import static org.minidns.DnsWorld.cname;
import static org.minidns.DnsWorld.ns;
import static org.minidns.DnsWorld.record;
import static org.minidns.DnsWorld.rootZone;
import static org.minidns.DnsWorld.zone;

import java.io.IOException;

public class ChaseCnameTest {

    @SuppressWarnings("unchecked")
    @Test
    public void simpleChaseCnameTest() throws IOException {
        DnsClient client = new DnsClient();
        applyZones(client,
                rootZone(
                        record("com", ns("ns.com")),
                        record("ns.com", a("1.1.1.1"))
                ), zone("com", "ns.com", "1.1.1.1",
                        record("example.com", ns("ns.example.com")),
                        record("ns.example.com", a("1.1.1.2"))
                ), zone("example.com", "ns.example.com", "1.1.1.2",
                        record("www.example.com", cname("webhost.example.com")),
                        record("webhost.example.com", a("1.1.1.3"))
                )
        );

        DnsQueryResult result = client.query("www.example.com", Record.TYPE.A);
        assertNotNull(result.response);
    }
}

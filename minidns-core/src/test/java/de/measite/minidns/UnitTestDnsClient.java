/*
 * Copyright 2015 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package de.measite.minidns;

import java.io.IOException;
import java.net.InetAddress;

public class UnitTestDnsClient extends AbstractDNSClient {

    private final DNSWorld dnsWorld;

    public UnitTestDnsClient(DNSWorld dnsWorld) {
        this.dnsWorld = dnsWorld;
    }

    @Override
    protected DNSMessage query(DNSMessage message, InetAddress address, int port)
            throws IOException {
        return dnsWorld.query(message, address, port);
    }

}

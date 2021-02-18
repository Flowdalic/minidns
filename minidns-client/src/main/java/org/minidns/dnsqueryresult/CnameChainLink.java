/*
 * Copyright 2015-2020 the original author or authors
 *
 * This software is licensed under the Apache License, Version 2.0,
 * the GNU Lesser General Public License version 2 or later ("LGPL")
 * and the WTFPL.
 * You may choose either license to govern your use of this software only
 * upon the condition that you accept all of the terms of either
 * the Apache License 2.0, the LGPL 2.1+ or the WTFPL.
 */
package org.minidns.dnsqueryresult;

import java.util.List;

import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsname.DnsName;

public class CnameChainLink {
    public final DnsName target;
    public final DnsMessage query;
    public final DnsQueryResult dnsQueryResult;

    private CnameChainLink(DnsMessage query, DnsQueryResult dnsQueryResult) {
        this.target = query.getQuestion().name;
        this.query = query;
        this.dnsQueryResult = dnsQueryResult;
    }

    public static void createAndAppend(List<CnameChainLink> cnameChain, DnsMessage query, DnsQueryResult dnsQueryResult) {
        CnameChainLink link = new CnameChainLink(query, dnsQueryResult);
        cnameChain.add(link);
    }
}

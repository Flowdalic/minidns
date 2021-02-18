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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.minidns.dnsmessage.DnsMessage;

public class CnameChainDnsQueryResult extends DnsQueryResult {

    public final List<CnameChainLink> cnameChain;

    public CnameChainDnsQueryResult(DnsMessage query, List<CnameChainLink> cnameChain) {
        super(deduceQueryMethod(cnameChain), query, deduceResponse(cnameChain));

        this.cnameChain = Collections.unmodifiableList(cnameChain);
    }

    private static QueryMethod deduceQueryMethod(List<CnameChainLink> cnameChain) {
        Iterator<CnameChainLink> it = cnameChain.iterator();
        QueryMethod deducedMethod = it.next().dnsQueryResult.queryMethod;

        while (it.hasNext()) {
            QueryMethod queryMethod = it.next().dnsQueryResult.queryMethod;
            if (queryMethod != deducedMethod) {
                return QueryMethod.various;
            }
        }

        return deducedMethod;
    }

    private static DnsMessage deduceResponse(List<CnameChainLink> cnameChain) {
        // TODO: In case of a cname chain there is no single response. Shall we
        // synthesize one? Use the first or last response in the chain?
        return cnameChain.get(cnameChain.size() - 1).dnsQueryResult.response;
    }
}

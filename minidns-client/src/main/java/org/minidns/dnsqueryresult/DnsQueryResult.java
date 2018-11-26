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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.DnsMessage.RESPONSE_CODE;
import org.minidns.dnsmessage.Question;
import org.minidns.dnsname.DnsName;

public abstract class DnsQueryResult {

    public enum QueryMethod {
        udp,
        tcp,
        asyncUdp,
        asyncTcp,
        cachedDirect,
        cachedSynthesized,
        testWorld,
    }

    public final QueryMethod queryMethod;

    public final Question question;

    public final DnsMessage query;

    public final DnsMessage response;

    public final List<DnsMessage> cnameChain;

    public final List<DnsName> cnameNamesChain;

    protected DnsQueryResult(QueryMethod queryMethod, DnsMessage query, DnsMessage response) {
        this(queryMethod, query, response, null);
    }

    protected DnsQueryResult(QueryMethod queryMethod, DnsMessage query, DnsMessage response,
            List<DnsMessage> cnameChain) {
        assert queryMethod != null;
        assert query != null;
        assert response != null;

        this.queryMethod = queryMethod;
        this.question = query.getQuestion();
        this.query = query;
        this.response = response;

        if (cnameChain == null) {
            cnameChain = Collections.emptyList();
        }

        this.cnameChain = Collections.unmodifiableList(cnameChain);

        List<DnsName> cnameNamesChainLocal = new ArrayList<>(cnameChain.size());
        for (DnsMessage cnameMessage : cnameChain) {
            cnameMessage.getAnswersFor(question);
        }
        this.cnameNamesChain = Collections.unmodifiableList(cnameNamesChainLocal);
    }

    @Override
    public String toString() {
        return response.toString();
    }

    public boolean wasSuccessful() {
        return response.responseCode == RESPONSE_CODE.NO_ERROR;
    }
}

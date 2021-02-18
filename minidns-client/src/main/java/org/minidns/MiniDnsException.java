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
package org.minidns;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsqueryresult.CnameChainLink;
import org.minidns.dnsqueryresult.DnsQueryResult;

public abstract class MiniDnsException extends IOException {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    protected MiniDnsException(String message) {
        super(message);
    }

    public static class IdMismatch extends MiniDnsException {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        private final DnsMessage request;
        private final DnsMessage response;

        public IdMismatch(DnsMessage request, DnsMessage response) {
            super(getString(request, response));
            assert request.id != response.id;
            this.request = request;
            this.response = response;
        }

        public DnsMessage getRequest() {
            return request;
        }

        public DnsMessage getResponse() {
            return response;
        }

        private static String getString(DnsMessage request, DnsMessage response) {
            return "The response's ID doesn't matches the request ID. Request: " + request.id + ". Response: " + response.id;
        }
    }

    public static class NullResultException extends MiniDnsException {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        private final DnsMessage request;

        public NullResultException(DnsMessage request) {
            super("The request yielded a 'null' result while resolving.");
            this.request = request;
        }

        public DnsMessage getRequest() {
            return request;
        }
    }

    public static class ErrorResponseException extends MiniDnsException {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private final DnsMessage request;
        private final DnsQueryResult result;

        public ErrorResponseException(DnsMessage request, DnsQueryResult result) {
            super("Received " + result.response.responseCode + " error response\n" + result);
            this.request = request;
            this.result = result;
        }

        public DnsMessage getRequest() {
            return request;
        }

        public DnsQueryResult getResult() {
            return result;
        }
    }

    public static class NoQueryPossibleException extends MiniDnsException {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        private final DnsMessage request;

        public NoQueryPossibleException(DnsMessage request) {
            super("No DNS server could be queried");
            this.request = request;
        }

        public DnsMessage getRequest() {
            return request;
        }
    }

    private abstract static class AbstractCname extends MiniDnsException {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public final DnsMessage initialQuery;
        public final List<CnameChainLink> cnameChain;
        public final DnsQueryResult currentResult;

        protected AbstractCname(String message, DnsMessage initialQuery, List<CnameChainLink> cnameChain,
                DnsQueryResult currentResult) {
            super(message);
            this.initialQuery = initialQuery;
            this.cnameChain = Collections.unmodifiableList(cnameChain);
            this.currentResult = currentResult;
        }
    }

    public static final class CnameChainToLong extends AbstractCname {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        private CnameChainToLong(String message, DnsMessage initialQuery, List<CnameChainLink> cnameChain,
                DnsQueryResult currentResult) {
            super(message, initialQuery, cnameChain, currentResult);
        }

        public static CnameChainToLong create(List<CnameChainLink> cnameChain, DnsQueryResult currentResult) {
            DnsMessage initialQuery = cnameChain.get(0).query;
            String message = "The CNAME chain for " + initialQuery.getQuestion()
                    + " is to long. It currently is " + cnameChain.size() + "links long";
            return new CnameChainToLong(message, initialQuery, cnameChain, currentResult);
        }

    }

    public static final class CnameLoop extends AbstractCname {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        public final int loopingLink;

        private CnameLoop(String message, int loopingLink, DnsMessage initialQuery, List<CnameChainLink> cnameChain,
                DnsQueryResult currentResult) {
            super(message, initialQuery, cnameChain, currentResult);
            this.loopingLink = loopingLink;
        }

        public static CnameLoop create(int loopingLink, List<CnameChainLink> cnameChain, DnsQueryResult currentResult) {
            DnsMessage initialQuery = cnameChain.get(0).query;
            // TODO: Improve message to include the actual loop.
            String message = "The CNAME chain for " + initialQuery.getQuestion()
                    + " contains a loop";
            return new CnameLoop(message, loopingLink, initialQuery, cnameChain, currentResult);
        }

    }
}

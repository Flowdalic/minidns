package org.minidns.integrationtest;

import java.io.IOException;

import org.minidns.hla.DnssecResolverApi;
import org.minidns.hla.ResolverResult;
import org.minidns.record.MX;

public class Test {

	public static void main(String[] args) throws IOException {
		ResolverResult<MX> res = DnssecResolverApi.INSTANCE.resolve("github.xxx", MX.class);
		System.out.println(res);
	}
}

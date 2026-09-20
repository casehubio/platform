package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.InvocationRecord;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface CorpusLoader {

    boolean supports(String path);

    Map<String, List<InvocationRecord<Object, Object>>> load(InputStream input, String defaultTenancyId);
}

package com.matyrobbrt.metabase.params;

import java.util.Map;

public interface QueryParameters {
    Map<String, Object> compile();
}

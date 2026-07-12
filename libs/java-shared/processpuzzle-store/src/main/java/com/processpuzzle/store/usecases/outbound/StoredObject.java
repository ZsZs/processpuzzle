package com.processpuzzle.store.usecases.outbound;

import java.io.InputStream;
import java.util.Map;

public record StoredObject(InputStream inputStream, Map<String, String> metadata) {
}

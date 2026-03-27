package com.processpuzzle.objectstore.usecases.outbound;

import java.io.InputStream;
import java.util.Map;

public record StoredObject(InputStream inputStream, Map<String, String> metadata) {
}

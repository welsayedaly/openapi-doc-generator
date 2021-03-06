package com.github.chhorz.openapi.common.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * https://github.com/OAI/OpenAPI-Specification/blob/v3.0.1/versions/3.0.1.md#components-object
 *
 * @author chhorz
 *
 */
public class Components {

	private Map<String, Schema> schemas;
	private Map<String, Response> responses;
	private Map<String, Parameter> parameters;
	private Map<String, Example> examples;
	private Map<String, RequestBody> requestBodies;
	private Map<String, Header> headers;
	private Map<String, SecurityScheme> securitySchemes;
	private Map<String, Link> links;
	private Map<String, Callback> callbacks;

	public static boolean isValidKey(final String key) {
		return key.matches("^[a-zA-Z0-9\\.\\-_]+$");
	}

	public void putAllSchemas(final Map<String, Schema> schemas) {
		if (this.schemas == null) {
			this.schemas = new HashMap<>();
		}
		this.schemas.putAll(schemas);
	}

	public Map<String, Schema> getSchemas() {
		return schemas;
	}

	public void putRequestBody(final String key, final RequestBody requestBody) {
		if (requestBodies == null) {
			requestBodies = new HashMap<>();
		}
		requestBodies.put(normalizeKey(key), requestBody);
	}

	public Map<String, RequestBody> getRequestBodies() {
		return requestBodies;
	}

	public void putResponse(final String key, final Response response) {
		if (responses == null) {
			responses = new HashMap<>();
		}
		responses.put(normalizeKey(key), response);
	}

	public Map<String, Response> getResponses() {
		return responses;
	}

	private String normalizeKey(final String key) {
		return key.substring(key.lastIndexOf('.') + 1);
	}

}

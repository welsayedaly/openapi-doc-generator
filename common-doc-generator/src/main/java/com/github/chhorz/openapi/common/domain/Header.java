package com.github.chhorz.openapi.common.domain;

/**
 * https://github.com/OAI/OpenAPI-Specification/blob/v3.0.1/versions/3.0.1.md#header-object
 *
 * @author chhorz
 *
 */
public class Header {

	// fixed fields
	private String description;
	private Boolean required = Boolean.FALSE;
	private Boolean deprecated;
	private Boolean allowEmptyValue;

}

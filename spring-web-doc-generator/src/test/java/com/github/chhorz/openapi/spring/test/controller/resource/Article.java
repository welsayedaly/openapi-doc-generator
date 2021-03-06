package com.github.chhorz.openapi.spring.test.controller.resource;

/**
 * An article that can be ordered.
 *
 * @author chhorz
 *
 */
public class Article {

	private Long number;
	private String name;

	public Long getNumber() {
		return number;
	}

	public void setNumber(final Long number) {
		this.number = number;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

}

package com.github.chhorz.openapi.common.domain;

import com.github.chhorz.openapi.common.domain.meta.Required;

/**
 * https://github.com/OAI/OpenAPI-Specification/blob/v3.0.1/versions/3.0.1.md#info-object
 *
 * @author chhorz
 *
 */
public class Info {

	@Required
	private String title;
	private String description;
	private String termsOfService;
	private Contact contact;
	private License license;
	@Required
	private String version;

	public String getTitle() {
		return title;
	}

	public void setTitle(final String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getTermsOfService() {
		return termsOfService;
	}

	public void setTermsOfService(final String termsOfService) {
		this.termsOfService = termsOfService;
	}

	public Contact getContact() {
		return contact;
	}

	public void setContact(final Contact contact) {
		this.contact = contact;
	}

	public License getLicense() {
		return license;
	}

	public void setLicense(final License license) {
		this.license = license;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(final String version) {
		this.version = version;
	}

}

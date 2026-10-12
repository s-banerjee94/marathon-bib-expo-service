package com.timekeeper.bibexpo.organization.model.event;

/** Published as a new organization is saved, so higher modules can give it their starting data. */
public record OrganizationCreatedEvent(Long organizationId) {
}

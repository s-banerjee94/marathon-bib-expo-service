package com.timekeeper.bibexpo.organization.api;

/**
 * The inventory caps one organization is allowed. Returned as a single value so a caller that
 * needs more than one of them does not pay a lookup per cap.
 *
 * @param maxTerms                    own vocabulary entries; platform defaults are not counted
 * @param maxOptionsPerAttribute      choices on one of the organization's own attributes
 * @param maxVariantAttributesPerItem attributes one item may split its stock by
 * @param maxVariantsPerItem          variant rows one item may hold
 * @param maxLocations                places the organization may keep stock in
 */
public record InventoryLimits(int maxTerms,
                              int maxOptionsPerAttribute,
                              int maxVariantAttributesPerItem,
                              int maxVariantsPerItem,
                              int maxLocations) {
}

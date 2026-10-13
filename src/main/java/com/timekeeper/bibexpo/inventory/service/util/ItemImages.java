package com.timekeeper.bibexpo.inventory.service.util;

import com.timekeeper.bibexpo.inventory.model.entity.InventoryItem;
import com.timekeeper.bibexpo.inventory.model.entity.InventoryVariant;
import com.timekeeper.bibexpo.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * The picture that stands for an item in a list: its own photo, else the first of its variants that has
 * one, else none.
 */
@Component
@RequiredArgsConstructor
public class ItemImages {

    private final StorageService storageService;

    /** A signed link for one item whose variants are already loaded, or null. */
    public String urlFor(InventoryItem item, List<InventoryVariant> variants) {
        return storageService.createDownloadUrl(keyFor(item, variants));
    }

    private static String keyFor(InventoryItem item, List<InventoryVariant> variants) {
        if (item.getImageKey() != null) {
            return item.getImageKey();
        }
        return variants.stream()
                .filter(v -> v.getImageKey() != null)
                .min(Comparator.comparing(InventoryVariant::getId))
                .map(InventoryVariant::getImageKey)
                .orElse(null);
    }
}

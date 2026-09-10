package com.timekeeper.bibexpo.event.model.entity;

/**
 * One goody an event hands out, and how it got onto the event's list.
 *
 * @param name   the goody name; for an imported goody, the column heading exactly as the import
 *               stored it, since that is the key participant records carry it under
 * @param source whether an import or a person put it there
 */
public record EventGoodie(String name, GoodieSource source) {
}

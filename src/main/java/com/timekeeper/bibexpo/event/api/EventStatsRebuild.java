package com.timekeeper.bibexpo.event.api;

/**
 * What a counter rebuild found and wrote, for the caller that owns the roster to log.
 *
 * @param participants  participants walked
 * @param bibCollected  how many of them had collected their bib
 * @param counterRows   counter rows written
 */
public record EventStatsRebuild(int participants, int bibCollected, int counterRows) {}

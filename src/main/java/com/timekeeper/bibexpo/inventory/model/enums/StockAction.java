package com.timekeeper.bibexpo.inventory.model.enums;

/**
 * A stock action the caller may take, so a screen shows only the buttons that will work.
 * COUNT is the recount part of ADJUSTMENT; ADJUSTMENT also writes off damaged and lost stock.
 */
public enum StockAction {
    RECEIPT,
    TRANSFER,
    COUNT,
    ADJUSTMENT,
    REVERSE
}

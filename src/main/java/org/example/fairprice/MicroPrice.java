package org.example.fairprice;

/**
 * Fair price as the best bid and ask weighted by the quantity waiting on the opposite side.
 *
 * <p>If many shares wait to be bought and only a few to be sold, the ask is likely to be taken
 * soon and the price to move up, so the fair price is closer to the ask. This is the
 * {@link MultiLevelPrice} model using only the best level.
 */
public class MicroPrice extends MultiLevelPrice {

    /** Creates the model. */
    public MicroPrice() {
        super(1);
    }
}

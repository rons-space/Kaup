package app.kaup.core.data.converters

import androidx.room.TypeConverter
import app.kaup.shared.models.Quantity

/**
 * Persists a [Quantity] as its raw thousandths.
 *
 * Storing the scaled integer rather than a REAL is the whole point of ADR-020:
 * stock is a sum over an append-only log, and a column that round-trips through
 * binary floating point would reintroduce the drift the type exists to remove.
 *
 * A null column reads back as zero rather than throwing. The movement log is
 * replayed to compute stock, so refusing to read one row would take out every
 * figure derived from the table; a zero-quantity movement is visibly wrong in
 * the log and harmless to the sum.
 */
class QuantityConverter {
    @TypeConverter
    fun toQuantity(thousandths: Long?): Quantity = Quantity(thousandths ?: 0L)

    @TypeConverter
    fun fromQuantity(quantity: Quantity): Long = quantity.thousandths
}

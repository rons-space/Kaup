package app.kaup.shared.domain

import app.kaup.shared.models.*
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictResolverTest {

    private val resolver = ConflictResolver()

    @Test
    fun `sorts exact timestamp ties deterministically by deviceId`() {
        val time = "2026-06-13T10:00:00Z"
        val m1 = createMovement("1", MovementDirection.IN, 1.0, time, "device_C")
        val m2 = createMovement("2", MovementDirection.IN, 1.0, time, "device_A")
        val m3 = createMovement("3", MovementDirection.IN, 1.0, time, "device_B")

        val sorted = resolver.sortDeterministically(listOf(m1, m2, m3))

        assertEquals("device_A", sorted[0].deviceId)
        assertEquals("device_B", sorted[1].deviceId)
        assertEquals("device_C", sorted[2].deviceId)
    }

    @Test
    fun `every input order resolves to the same timeline`() {
        // The point of a total order: devices that received these movements in
        // different orders must still agree, without talking to each other.
        val time = "2026-06-13T10:00:00Z"
        val movements = listOf(
            createMovement("b", MovementDirection.IN, 1.0, time, "device_A"),
            createMovement("a", MovementDirection.IN, 1.0, time, "device_A"),
            createMovement("c", MovementDirection.OUT, 1.0, time, "device_B")
        )
        val expected = resolver.resolve(movements).timeline.map { it.id }

        for (permutation in permutations(movements)) {
            assertEquals(expected, resolver.resolve(permutation).timeline.map { it.id })
        }
    }

    @Test
    fun `detects when two devices sell the last unit offline`() {
        val mIn = createMovement("in_1", MovementDirection.IN, 1.0, "2026-06-13T10:00:00Z", "dev_manager")
        val mSaleA = createMovement("out_A", MovementDirection.OUT, 1.0, "2026-06-13T10:10:00Z", "dev_A")
        val mSaleB = createMovement("out_B", MovementDirection.OUT, 1.0, "2026-06-13T10:15:00Z", "dev_B")

        val violations = resolver.detectNegativeStockViolations(listOf(mIn, mSaleB, mSaleA))

        assertEquals(1, violations.size)
        assertEquals("out_B", violations[0].movement.id)
        assertTrue(violations[0].isFirstBreach)
        assertEquals(Quantity.of(-1.0), violations[0].stockAfter)
    }

    @Test
    fun `reports every oversell, not only the one that crossed zero`() {
        // The behaviour this replaces reported one violation here and hid the
        // other three, so a manager saw a single broken sale rather than a
        // shelf four units short.
        val movements = listOf(
            createMovement("in", MovementDirection.IN, 1.0, "2026-06-13T10:00:00Z", "dev_m"),
            createMovement("s1", MovementDirection.OUT, 1.0, "2026-06-13T10:01:00Z", "dev_a"),
            createMovement("s2", MovementDirection.OUT, 1.0, "2026-06-13T10:02:00Z", "dev_b"),
            createMovement("s3", MovementDirection.OUT, 1.0, "2026-06-13T10:03:00Z", "dev_c"),
            createMovement("s4", MovementDirection.OUT, 1.0, "2026-06-13T10:04:00Z", "dev_d")
        )

        val violations = resolver.detectNegativeStockViolations(movements)

        assertEquals(listOf("s2", "s3", "s4"), violations.map { it.movement.id })
        assertTrue(violations.first().isFirstBreach)
        assertTrue(violations.drop(1).none { it.isFirstBreach })
        // The depth of the hole is what has to be reconciled.
        assertEquals(Quantity.of(-3.0), violations.last().stockAfter)
    }

    @Test
    fun `a restock ends the breach, so a later dip is a new first breach`() {
        val movements = listOf(
            createMovement("s1", MovementDirection.OUT, 1.0, "2026-06-13T10:00:00Z", "dev_a"),
            createMovement("in", MovementDirection.IN, 5.0, "2026-06-13T10:01:00Z", "dev_m"),
            createMovement("s2", MovementDirection.OUT, 9.0, "2026-06-13T10:02:00Z", "dev_a")
        )

        val violations = resolver.detectNegativeStockViolations(movements)

        assertEquals(listOf("s1", "s2"), violations.map { it.movement.id })
        assertTrue(violations[0].isFirstBreach)
        assertTrue(violations[1].isFirstBreach)
    }

    @Test
    fun `gracefully ignores expected fluctuations that stay positive`() {
        val movements = listOf(
            createMovement("1", MovementDirection.IN, 10.0, "2026-06-13T10:00:00Z", "dev_1"),
            createMovement("2", MovementDirection.OUT, 5.0, "2026-06-13T10:05:00Z", "dev_1"),
            createMovement("3", MovementDirection.OUT, 4.0, "2026-06-13T10:10:00Z", "dev_1")
        )

        val resolution = resolver.resolve(movements)
        assertTrue(resolution.violations.isEmpty())
        assertEquals(Quantity.of(1.0), resolution.finalStock)
    }

    @Test
    fun `a movement replayed by a retry is counted once`() {
        // A sync that commits and then fails before the acknowledgement is
        // recorded is ordinary on café wifi. Counting the retry would move
        // stock twice.
        val sale = createMovement("out_1", MovementDirection.OUT, 3.0, "2026-06-13T10:05:00Z", "dev_a")
        val stockIn = createMovement("in_1", MovementDirection.IN, 10.0, "2026-06-13T10:00:00Z", "dev_m")

        val resolution = resolver.resolve(listOf(stockIn, sale, sale))

        assertEquals(2, resolution.timeline.size)
        assertEquals(listOf("out_1"), resolution.discardedDuplicates.map { it.id })
        assertTrue(resolution.divergentDuplicates.isEmpty())
        assertEquals(Quantity.of(7.0), resolution.finalStock)
    }

    @Test
    fun `the same id carrying different content is surfaced, not silently dropped`() {
        val original = createMovement("out_1", MovementDirection.OUT, 3.0, "2026-06-13T10:05:00Z", "dev_a")
        val impostor = original.copy(quantity = Quantity.of(30.0))

        val resolution = resolver.resolve(listOf(original, impostor))

        assertEquals(1, resolution.timeline.size)
        assertEquals(1, resolution.divergentDuplicates.size)
        // The kept movement is the one the deterministic order reached first,
        // so every device keeps the same one.
        assertEquals(Quantity.of(-3.0), resolution.finalStock)
    }

    @Test
    fun `an empty log resolves to nothing rather than failing`() {
        val resolution = resolver.resolve(emptyList())
        assertTrue(resolution.timeline.isEmpty())
        assertTrue(resolution.violations.isEmpty())
        assertFalse(resolution.finalStock.isNegative)
        assertEquals(Quantity.ZERO, resolution.finalStock)
    }

    private fun permutations(items: List<StockMovement>): List<List<StockMovement>> {
        if (items.size <= 1) return listOf(items)
        return items.flatMap { head ->
            permutations(items - head).map { tail -> listOf(head) + tail }
        }
    }

    private fun createMovement(
        id: String,
        direction: MovementDirection,
        quantity: Double,
        timestampStr: String,
        deviceId: String
    ) = StockMovement(
        id = id,
        itemId = "item_123",
        type = if (direction == MovementDirection.IN) MovementType.RECEIVING else MovementType.SALE,
        direction = direction,
        quantity = Quantity.of(quantity),
        transactionId = null,
        deviceId = deviceId,
        timestamp = Instant.parse(timestampStr)
    )
}

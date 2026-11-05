package com.hectormtzm.liftlog.data.nutrition

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Where a food or meal came from. */
object Source {
    const val USDA = "usda"
    const val OFF = "off"          // Open Food Facts
    const val CUSTOM = "custom"
    const val CLAUDE = "claude"
    const val MANUAL = "manual"    // meal built by searching foods
    const val PHOTO = "photo"
    const val TEXT = "text"
    const val COPY = "copy"      // meal re-added from a past meal
}

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snacks");

    companion object {
        fun forHour(hour: Int): MealType = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }
        fun parse(s: String): MealType = entries.firstOrNull { it.name == s } ?: SNACK
    }
}

/** A named serving size, e.g. "1 large" = 50 g. */
data class Portion(val label: String, val grams: Double)

/**
 * A food in the searchable catalog: bundled USDA generic foods, products from Open Food Facts,
 * and the user's custom foods. Nutrients are per 100 g.
 */
@Entity(tableName = "foods", indices = [Index("barcode"), Index("lastUsedAt")])
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String = "",
    val source: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    /** "label=grams" pairs separated by '|'. */
    val portions: String = "",
    val barcode: String? = null,
    val lastUsedAt: Long? = null,
) {
    val portionList: List<Portion>
        get() = portions.split('|').mapNotNull { p ->
            val i = p.lastIndexOf('=')
            if (i <= 0) null else p.substring(i + 1).toDoubleOrNull()?.takeIf { it > 0 }?.let { Portion(p.substring(0, i), it) }
        }

    companion object {
        fun encodePortions(list: List<Portion>) =
            list.joinToString("|") { "${it.label.replace("|", "/").replace("=", "-")}=${it.grams}" }
    }
}

@Entity(tableName = "meals", indices = [Index("epochDay")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val mealType: String,
    val name: String = "",
    val source: String,
    val photoPath: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One food in a meal. Nutrients are stored per 100 g so changing the quantity or unit
 * recalculates everything: grams = quantity * gramsPerUnit.
 */
@Entity(
    tableName = "meal_items",
    foreignKeys = [ForeignKey(
        entity = MealEntity::class,
        parentColumns = ["id"],
        childColumns = ["mealId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("mealId")],
)
data class MealItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val position: Int,
    val name: String,
    val quantity: Double,
    val unit: String,
    val gramsPerUnit: Double,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val foodId: String? = null,
) {
    val grams: Double get() = quantity * gramsPerUnit
    val macros: Macros get() = Macros.per100(grams, kcal100, protein100, carbs100, fat100)
}

data class MealFull(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val items: List<MealItemEntity>,
) {
    val sortedItems: List<MealItemEntity> get() = items.sortedBy { it.position }
    val macros: Macros get() = items.fold(Macros.ZERO) { acc, i -> acc + i.macros }

    /** The meal's name, or its foods ("Egg, Bread") when it has none. */
    val title: String
        get() = meal.name.ifBlank {
            sortedItems.map { it.name.substringBefore(',').substringBefore(" (") }.distinct().joinToString(", ").ifBlank { "Meal" }
        }
}

data class Macros(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
        fun per100(grams: Double, kcal100: Double, p100: Double, c100: Double, f100: Double): Macros {
            val k = grams / 100.0
            return Macros(kcal100 * k, p100 * k, c100 * k, f100 * k)
        }
    }
}

package com.mrboora.plate.data

/**
 * A small offline list of everyday foods so the app is useful without a connection.
 * Values are approximate, per 100 g, rounded from typical published nutrition data.
 */
object FoodCatalog {
    private fun f(name: String, kcal: Int, p: Float, c: Float, fat: Float, serving: Float, label: String) =
        FoodItem(name, "", kcal.toFloat(), p, c, fat, serving, label)

    val foods: List<FoodItem> = listOf(
        f("Apple", 52, 0.3f, 14f, 0.2f, 180f, "1 medium"),
        f("Banana", 89, 1.1f, 23f, 0.3f, 120f, "1 medium"),
        f("Orange", 47, 0.9f, 12f, 0.1f, 130f, "1 medium"),
        f("Grapes", 69, 0.7f, 18f, 0.2f, 100f, "1 cup"),
        f("Strawberries", 32, 0.7f, 7.7f, 0.3f, 150f, "1 cup"),
        f("Mango", 60, 0.8f, 15f, 0.4f, 165f, "1 cup sliced"),
        f("Avocado", 160, 2f, 8.5f, 15f, 150f, "1 fruit"),
        f("Egg, boiled", 155, 13f, 1.1f, 11f, 50f, "1 large"),
        f("Egg, fried", 196, 14f, 0.8f, 15f, 46f, "1 large"),
        f("Chicken breast, cooked", 165, 31f, 0f, 3.6f, 120f, "1 piece"),
        f("Chicken curry", 150, 13f, 5f, 9f, 250f, "1 bowl"),
        f("Salmon, cooked", 206, 22f, 0f, 12f, 125f, "1 fillet"),
        f("Tuna, canned in water", 116, 26f, 0f, 1f, 85f, "1 can drained"),
        f("Beef, lean ground, cooked", 250, 26f, 0f, 15f, 100f, "1 portion"),
        f("Tofu, firm", 144, 17f, 3f, 9f, 100f, "1/4 block"),
        f("Paneer", 296, 18f, 6f, 22f, 50f, "5 cubes"),
        f("Dal (lentil curry)", 116, 7f, 15f, 3f, 200f, "1 bowl"),
        f("Chana masala", 140, 6f, 18f, 5f, 200f, "1 bowl"),
        f("Rajma (kidney bean curry)", 125, 6f, 16f, 4f, 200f, "1 bowl"),
        f("Roti / chapati", 297, 9.8f, 46f, 7.5f, 40f, "1 roti"),
        f("Naan", 290, 9f, 50f, 5.5f, 90f, "1 piece"),
        f("Basmati rice, cooked", 121, 3.5f, 25f, 0.4f, 160f, "1 cup"),
        f("Brown rice, cooked", 112, 2.6f, 24f, 0.9f, 160f, "1 cup"),
        f("Jeera rice", 160, 3f, 27f, 4.5f, 160f, "1 cup"),
        f("Biryani, chicken", 180, 9f, 22f, 6f, 300f, "1 plate"),
        f("Idli", 132, 4f, 28f, 0.4f, 40f, "1 piece"),
        f("Dosa, plain", 168, 3.9f, 29f, 3.7f, 80f, "1 dosa"),
        f("Samosa", 262, 4.6f, 30f, 14f, 60f, "1 piece"),
        f("Poha", 130, 2.5f, 23f, 3f, 200f, "1 bowl"),
        f("Pasta, cooked", 158, 5.8f, 31f, 0.9f, 140f, "1 cup"),
        f("Bread, whole wheat", 247, 13f, 41f, 3.4f, 32f, "1 slice"),
        f("Bread, white", 265, 9f, 49f, 3.2f, 28f, "1 slice"),
        f("Oats, dry", 389, 17f, 66f, 7f, 40f, "1/2 cup"),
        f("Potato, boiled", 87, 1.9f, 20f, 0.1f, 150f, "1 medium"),
        f("Sweet potato, baked", 90, 2f, 21f, 0.2f, 130f, "1 medium"),
        f("Broccoli", 34, 2.8f, 7f, 0.4f, 90f, "1 cup"),
        f("Spinach", 23, 2.9f, 3.6f, 0.4f, 30f, "1 cup raw"),
        f("Mixed salad", 20, 1.3f, 3.5f, 0.2f, 100f, "1 bowl"),
        f("Milk, 2%", 50, 3.3f, 4.8f, 2f, 250f, "1 cup"),
        f("Greek yogurt, plain 2%", 73, 9.9f, 3.9f, 2f, 170f, "1 tub"),
        f("Curd / dahi", 61, 3.5f, 4.7f, 3.3f, 150f, "1 bowl"),
        f("Cheddar cheese", 403, 25f, 1.3f, 33f, 28f, "1 slice"),
        f("Butter", 717, 0.9f, 0.1f, 81f, 10f, "1 tbsp"),
        f("Ghee", 900, 0f, 0f, 100f, 5f, "1 tsp"),
        f("Olive oil", 884, 0f, 0f, 100f, 14f, "1 tbsp"),
        f("Peanut butter", 588, 25f, 20f, 50f, 32f, "2 tbsp"),
        f("Almonds", 579, 21f, 22f, 50f, 28f, "1 handful"),
        f("Dark chocolate 70%", 598, 7.8f, 46f, 43f, 20f, "2 squares"),
        f("Pizza, cheese", 266, 11f, 33f, 10f, 107f, "1 slice"),
        f("Burger, beef", 254, 13f, 24f, 12f, 220f, "1 burger"),
        f("French fries", 312, 3.4f, 41f, 15f, 117f, "1 medium"),
        f("Coffee, black", 2, 0.3f, 0f, 0f, 240f, "1 cup"),
        f("Chai with milk & sugar", 60, 1.6f, 9f, 1.8f, 150f, "1 cup"),
        f("Orange juice", 45, 0.7f, 10f, 0.2f, 250f, "1 glass"),
        f("Cola", 42, 0f, 10.6f, 0f, 355f, "1 can"),
        f("Beer", 43, 0.5f, 3.6f, 0f, 355f, "1 can"),
        f("Protein shake (whey + water)", 120, 24f, 3f, 1.5f, 30f, "1 scoop"),
    )

    fun search(q: String): List<FoodItem> {
        val terms = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return foods.filter { food -> terms.all { food.name.lowercase().contains(it) } }
    }
}

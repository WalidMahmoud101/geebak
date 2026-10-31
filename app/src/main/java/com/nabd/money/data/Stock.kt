package com.nabd.money.data

import androidx.room.withTransaction
import com.nabd.money.agents.ArabicNumbers
import com.nabd.money.agents.MoneyParser

/** تصنيفات المخزون الجاهزة، والمفتاح اللي بيخلّي نفس الصنف يتعرف بأكتر من كتابة */
object StockCatalog {
    const val KITCHEN = "المطبخ"
    const val SPICES = "التوابل والبهارات"
    const val BATH = "الحمام"
    const val CLEANING = "النظافة والغسيل"
    const val FOOD = "الأكل والشرب"
    const val MEAT = "اللحوم والفراخ"
    const val LUXURY = "الرفاهية"
    const val OTHER = "حاجات تانية"

    val builtIn = listOf(KITCHEN, SPICES, FOOD, MEAT, CLEANING, BATH, LUXURY, OTHER)

    /** خانة كارفور: أقسام السوبر ماركت، لمين بيرتب المخزون زي ما بيلاقيه هناك */
    const val CARREFOUR = "كارفور"
    const val FRUIT_VEG = "الفواكه والخضروات"
    const val FRESH_MEAT = "اللحوم والدواجن والأسماك"
    const val DAIRY = "الألبان والبيض"
    const val BAKERY = "المخبوزات"
    const val FROZEN = "المجمدات"
    const val PANTRY = "البقالة والمعلبات"
    const val DRINKS = "المشروبات"
    const val SNACKS = "المقرمشات والحلويات"
    const val CARE = "العناية الشخصية"
    const val BABY = "الأطفال"
    const val HOUSEHOLD = "المنظفات ومستلزمات البيت"
    const val PETS = "الحيوانات الأليفة"

    val carrefour = listOf(FRUIT_VEG, FRESH_MEAT, DAIRY, BAKERY, FROZEN, PANTRY, DRINKS, SNACKS, CARE, BABY, HOUSEHOLD, PETS)

    /** أمثلة بتظهر وانت بتضيف، علشان أول مرة ماتبدأش من ورقة فاضية */
    val suggestions: Map<String, List<String>> = mapOf(
        KITCHEN to listOf("زيت", "سمنة", "خل", "أكياس زبالة", "ورق فويل"),
        SPICES to listOf("ملح", "فلفل أسود", "بهارات مشكلة", "كمون", "كزبرة ناشفة", "كركم", "بابريكا", "قرفة", "حبهان", "قرنفل", "زنجبيل", "ورق لورا", "شطة", "ثوم بودرة", "بصل بودرة", "جوزة الطيب", "بهارات كفتة", "بهارات فراخ", "مرقة"),
        FOOD to listOf("رز", "مكرونة", "سكر", "شاي", "قهوة", "لبن", "بيض", "جبنة", "عيش", "مياه"),
        MEAT to listOf("لحمة مفرومة", "فراخ", "بانيه", "كفتة", "سجق", "برجر", "كبدة", "سمك"),
        CLEANING to listOf("مسحوق غسيل", "منعم ملابس", "سائل أطباق", "كلور", "ديتول", "سلك مواعين"),
        BATH to listOf("صابون", "شامبو", "معجون أسنان", "ورق تواليت", "مناديل", "شاور جل"),
        LUXURY to listOf("شوكولاتة", "بسكويت", "مكسرات", "آيس كريم", "عصير", "شيبسي"),
        FRUIT_VEG to listOf("طماطم", "خيار", "بطاطس", "بصل", "موز", "تفاح", "ليمون"),
        FRESH_MEAT to listOf("لحمة", "فراخ", "سمك", "جمبري", "كبدة"),
        DAIRY to listOf("لبن", "بيض", "جبنة", "زبادي", "زبدة", "قشطة"),
        BAKERY to listOf("عيش", "توست", "كرواسون", "بقسماط"),
        FROZEN to listOf("بانيه", "خضار مجمد", "بطاطس مجمدة", "برجر", "آيس كريم"),
        PANTRY to listOf("رز", "مكرونة", "زيت", "سكر", "ملح", "دقيق", "عدس", "فول", "تونة", "صلصة"),
        DRINKS to listOf("مياه", "عصير", "شاي", "قهوة", "مياه غازية"),
        SNACKS to listOf("شيبسي", "شوكولاتة", "بسكويت", "مكسرات"),
        CARE to listOf("شامبو", "صابون", "معجون أسنان", "مزيل عرق", "شاور جل"),
        BABY to listOf("حفاضات", "مناديل أطفال", "لبن أطفال"),
        HOUSEHOLD to listOf("مسحوق غسيل", "سائل أطباق", "كلور", "مناديل", "أكياس زبالة", "ورق فويل"),
        PETS to listOf("أكل قطط", "أكل كلاب"),
    )

    private val carrefourHints: List<Pair<String, List<String>>> = listOf(
        FROZEN to listOf("مجمد", "frozen", "ايس كريم", "ice cream", "بانيه"),
        BABY to listOf("اطفال", "حفاض", "بيبي", "baby", "diaper"),
        PETS to listOf("قطط", "كلاب", "pet", "cat food", "dog food"),
        FRESH_MEAT to listOf("لحم", "مفروم", "فراخ", "دجاج", "صدور", "وراك", "كفته", "سجق", "برجر", "كبده", "سمك", "جمبري", "لانشون", "بسطرمه", "chicken", "beef", "meat", "fish"),
        FRUIT_VEG to listOf("طماطم", "خيار", "بطاطس", "بصل", "ثوم", "خس", "جزر", "فلفل رومي", "كوسه", "باذنجان", "موز", "تفاح", "برتقال", "ليمون", "عنب", "مانجو", "فاكهه", "خضار", "tomato", "banana", "apple"),
        DAIRY to listOf("لبن", "حليب", "بيض", "جبن", "زبادي", "زبده", "قشطه", "milk", "egg", "cheese", "yogurt", "butter"),
        BAKERY to listOf("عيش", "خبز", "توست", "كرواسون", "بقسماط", "bread", "toast"),
        DRINKS to listOf("مياه", "عصير", "شاي", "قهوه", "نسكافيه", "بيبسي", "كولا", "water", "juice", "tea", "coffee"),
        SNACKS to listOf("شيبسي", "شوكولا", "شيكولا", "بسكويت", "مكسرات", "حلويات", "كيك", "chips", "chocolate", "biscuit"),
        CARE to listOf("شامبو", "صابون", "معجون", "فرشه", "بلسم", "مزيل عرق", "شاور", "soap", "shampoo", "toothpaste"),
        HOUSEHOLD to listOf("مسحوق", "غسيل", "منعم", "كلور", "ديتول", "فلاش", "اطباق", "مواعين", "مناديل", "تواليت", "فوط", "اكياس", "فويل", "detergent", "tissue"),
    )

    private val hints: List<Pair<String, List<String>>> = listOf(
        MEAT to listOf("لحم", "لحمه", "مفروم", "فراخ", "دجاج", "صدور", "وراك", "اوراك", "بانيه", "كفته", "سجق", "برجر", "كبده", "سمك", "جمبري", "شاورما", "هوت دوج", "لانشون", "بسطرمه", "chicken", "beef", "meat", "burger"),
        CLEANING to listOf("مسحوق", "غسيل", "منعم", "كلور", "ديتول", "فلاش", "اطباق", "مواعين", "اريال", "برسيل", "تايد", "داوني", "فيري", "ariel", "persil", "tide", "downy", "fairy", "clorox", "dettol", "detergent", "softener"),
        BATH to listOf("صابون", "شامبو", "معجون", "فرشه", "تواليت", "مناديل", "شاور", "بلسم", "مزيل عرق", "حفاضات", "فوط", "soap", "shampoo", "toothpaste", "tissue", "diaper"),
        LUXURY to listOf("شوكولا", "شيكولا", "بسكويت", "مكسرات", "ايس كريم", "شيبسي", "حلويات", "جاتوه", "كيك", "نسكافيه", "chocolate", "biscuit", "chips", "nuts", "ice cream"),
        SPICES to listOf("ملح", "فلفل", "بهار", "توابل", "كمون", "كركم", "بابريكا", "قرفه", "حبهان", "قرنفل", "زنجبيل", "ورق لورا", "شطه", "جوزه الطيب", "كزبره ناشفه", "salt", "pepper", "spice", "cumin", "paprika"),
        KITCHEN to listOf("زيت", "سمن", "خل", "فويل", "اكياس", "oil", "foil"),
        FOOD to listOf("رز", "ارز", "مكرونه", "سكر", "شاي", "قهوه", "لبن", "حليب", "بيض", "جبن", "عيش", "خبز", "مياه", "عصير", "زبادي", "فول", "عدس", "دقيق", "صلصه", "تونه", "خضار", "فاكهه", "rice", "pasta", "sugar", "tea", "coffee", "milk", "egg", "cheese", "bread", "water", "juice", "yogurt"),
    )

    /** الاسم في أبسط صورة: من غير تشكيل ولا همزات ولا علامات، وبأرقام إنجليزية */
    fun key(name: String): String =
        ArabicNumbers.norm(MoneyParser.normalize(name))
            .replace(Regex("[\\u064B-\\u0652\\u0670]"), "")
            .replace('ک', 'ك').replace('ی', 'ي').replace('ھ', 'ه')
            .replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
            .replace(Regex("[^\\p{L}\\p{N}.]+"), " ")
            .trim()

    fun guessCategory(name: String): String {
        val k = key(name)
        return hints.firstOrNull { (_, words) -> words.any { k.contains(it) } }?.first ?: OTHER
    }

    fun guessCarrefour(name: String): String {
        val k = key(name)
        return carrefourHints.firstOrNull { (_, words) -> words.any { k.contains(it) } }?.first ?: PANTRY
    }

    /** الفلتر المختار: null = كل حاجة، وكارفور = كل أقسامه */
    fun inFilter(itemCategory: String, filter: String?) =
        filter == null || itemCategory == filter || (filter == CARREFOUR && itemCategory in carrefour)

    /** الصنف الجديد يروح فين وانت واقف على الفلتر ده */
    fun targetFor(name: String, filter: String?) = when (filter) {
        null -> guessCategory(name)
        CARREFOUR -> guessCarrefour(name)
        else -> filter
    }

    /** 3 أو 1.5 أو 0.258، من غير أصفار زيادة */
    fun quantityText(v: Double): String =
        if (v == Math.rint(v)) v.toLong().toString() else "%.3f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    fun aliasesOf(i: StockItem) = i.aliases.split('\n').filter { it.isNotBlank() }

    /**
     * أقرب صنف عندك لاسم مكتوب على فاتورة. لازم نفس الأرقام:
     * "زيت 1 لتر" و"زيت 2 لتر" مش نفس الصنف، والمقارنة بينهم تطلّع غالي ورخيص غلط.
     */
    fun match(name: String, items: List<StockItem>): StockItem? {
        val k = key(name)
        if (k.isEmpty()) return null
        items.firstOrNull { it.key == k || k in aliasesOf(it) }?.let { return it }
        val tokens = k.split(' ').toSet()
        val numbers = tokens.filterTo(mutableSetOf()) { t -> t.any(Char::isDigit) }
        return items
            .mapNotNull { item ->
                val other = item.key.split(' ').toSet()
                if (other.filterTo(mutableSetOf()) { t -> t.any(Char::isDigit) } != numbers) return@mapNotNull null
                val common = tokens.intersect(other).count { it.length >= 2 }
                val score = common.toDouble() / maxOf(tokens.size, other.size)
                (item to score).takeIf { common >= 1 && score >= 0.6 }
            }
            .maxByOrNull { it.second }?.first
            // "زيت" اللي في قايمة الشرا هو "زيت كريستال 1 لتر" اللي على الفاتورة. بس "فراخ" مش "صدور فراخ"
            ?: items.filter { i -> i.key.none(Char::isDigit) && (k == i.key || k.startsWith(i.key + " ")) }.maxByOrNull { it.key.length }
    }
}

/** اللي اتمسح من المخزون، علشان يرجع لو المسح كان بالغلط */
data class StockSnapshot(val items: List<StockItem>, val prices: List<StockPrice>)

/** سطر جاهز للحفظ من شاشة مراجعة الفاتورة */
data class ReceiptEntry(val name: String, val qty: Double, val price: Double, val category: String, val itemId: Long? = null)

/** الكتابة على المخزون: كلها هنا علشان تتجرب من غير واجهة */
class StockStore(private val db: NabdDatabase) {
    private val dao = db.dao()

    /** بيرجع الصنف الجديد، أو الموجود لو نفس الاسم متسجل */
    suspend fun add(name: String, category: String, out: Boolean = false, quantity: Double? = null): StockItem? = db.withTransaction {
        val clean = name.trim()
        val key = StockCatalog.key(clean)
        if (key.isEmpty()) return@withTransaction null
        val existing = dao.stockItemsOnce().firstOrNull { it.key == key }
        if (existing != null) {
            // ضفته تاني في خانة تانية: يبقى عايزه هناك، من غير ما يتكرر
            if (existing.category != category) dao.updateStockItem(existing.copy(category = category))
            if (out && !existing.isOut) setOut(existing.id, true)
            if (quantity != null) setQuantity(existing.id, quantity)
            return@withTransaction dao.stockItem(existing.id)
        }
        val now = System.currentTimeMillis()
        val i = StockItem(name = clean, key = key, category = category, isOut = out, outAt = now.takeIf { out }, createdAt = now, quantity = if (out) quantity?.let { 0.0 } else quantity)
        i.copy(id = dao.insertStockItem(i))
    }

    /** خلصت يعني الكمية صفر. ولو رجعت موجودة من غير ما نعرف بكام، الكمية تبقى مش محددة */
    suspend fun setOut(id: Long, out: Boolean) = db.withTransaction {
        dao.setStockOut(listOf(id), out, System.currentTimeMillis().takeIf { out })
        val quantity = dao.stockItem(id)?.quantity ?: return@withTransaction
        if (out) dao.setStockQuantity(id, 0.0) else if (quantity <= 0) dao.setStockQuantity(id, null)
    }

    /** الكمية صفر بتنزّل الصنف على قايمة الشرا، وأكتر من صفر بترجّعه موجود. null = مش محددة */
    suspend fun setQuantity(id: Long, quantity: Double?) = db.withTransaction {
        val item = dao.stockItem(id) ?: return@withTransaction
        val q = quantity?.coerceAtLeast(0.0)
        dao.setStockQuantity(id, q)
        when {
            q == 0.0 && !item.isOut -> dao.setStockOut(listOf(id), true, System.currentTimeMillis())
            q != null && q > 0 && item.isOut -> dao.setStockOut(listOf(id), false, null)
        }
    }

    /** اشتريت: الكمية بتزيد على اللي في البيت */
    suspend fun addQuantity(id: Long, amount: Double) = db.withTransaction {
        val item = dao.stockItem(id) ?: return@withTransaction
        setQuantity(id, (item.quantity ?: 0.0) + amount)
    }

    /** false لو الاسم الجديد بتاع صنف تاني */
    suspend fun edit(item: StockItem, name: String, category: String): Boolean = db.withTransaction {
        val clean = name.trim()
        val key = StockCatalog.key(clean)
        if (key.isEmpty() || dao.stockItemsOnce().any { it.id != item.id && it.key == key }) return@withTransaction false
        dao.updateStockItem(item.copy(name = clean, key = key, category = category))
        true
    }

    suspend fun delete(id: Long) = db.withTransaction {
        dao.deletePricesOf(id)
        dao.deleteStockItem(id)
    }

    /** بيمسح المخزون كله، أو تصنيف واحد، وبيرجع اللي اتمسح */
    suspend fun clear(category: String? = null): StockSnapshot = db.withTransaction {
        val items = dao.stockItemsOnce().filter { StockCatalog.inFilter(it.category, category) }
        val ids = items.map { it.id }.toSet()
        val prices = dao.stockPricesOnce().filter { it.itemId in ids }
        items.forEach { delete(it.id) }
        StockSnapshot(items, prices)
    }

    /** لو ضفت نفس الصنف بعد المسح، أسعاره القديمة بترجع عليه ومابيتكررش */
    suspend fun restore(s: StockSnapshot) = db.withTransaction {
        val now = dao.stockItemsOnce().associateBy { it.key }
        dao.putStockItems(s.items.filter { it.key !in now })
        val moved = s.items.mapNotNull { old -> now[old.key]?.let { old.id to it.id } }.toMap()
        dao.putStockPrices(s.prices.map { p -> moved[p.itemId]?.let { p.copy(itemId = it) } ?: p })
    }

    /** شيل أصناف من قايمة الشرا. الصنف بيفضل في المخزون بأسعاره، وبيرجع اللي اتشال علشان التراجع */
    suspend fun removeFromList(ids: List<Long>): List<StockItem> = db.withTransaction {
        val items = dao.stockItemsOnce().filter { it.id in ids && it.isOut }
        items.forEach { setOut(it.id, false) }
        items
    }

    /** بيرجّع حالة الأصناف زي ما كانت قبل ما تتشال من القايمة */
    suspend fun restoreToList(items: List<StockItem>) = db.withTransaction {
        items.forEach { old ->
            val now = dao.stockItem(old.id) ?: return@forEach
            dao.updateStockItem(now.copy(isOut = old.isOut, outAt = old.outAt, quantity = old.quantity))
        }
    }

    /** سعر جديد بإيدك. لو جبت الحاجة، بترجع موجودة في البيت */
    suspend fun addPrice(id: Long, price: Double, store: String? = null, at: Long = System.currentTimeMillis(), bought: Double? = null) = db.withTransaction {
        dao.insertStockPrice(StockPrice(itemId = id, price = price, qty = bought ?: 1.0, store = store?.trim()?.takeIf { it.isNotEmpty() }, at = at))
        setOut(id, false)
        if (bought != null) addQuantity(id, bought)
    }

    /**
     * حفظ الفاتورة بعد المراجعة: كل سطر بيتربط بصنف موجود أو يعمل صنف جديد، وسعره يتسجل،
     * واللي اشتريته يرجع موجود في البيت. لو الاسم على الفاتورة غير اسم الصنف، بيتحفظ علشان المرة الجاية.
     */
    suspend fun saveReceipt(entries: List<ReceiptEntry>, store: String?, at: Long) = db.withTransaction {
        val cleanStore = store?.trim()?.takeIf { it.isNotEmpty() }
        val items = dao.stockItemsOnce().toMutableList()
        entries.forEach { e ->
            val item = items.firstOrNull { it.id == e.itemId } ?: StockCatalog.match(e.name, items)
                ?: add(e.name, e.category)?.also { items += it }
                ?: return@forEach
            val key = StockCatalog.key(e.name)
            if (key.isNotEmpty() && key != item.key && key !in StockCatalog.aliasesOf(item)) {
                val updated = item.copy(aliases = (StockCatalog.aliasesOf(item) + key).joinToString("\n"))
                dao.updateStockItem(updated)
                items[items.indexOfFirst { it.id == item.id }] = updated
            }
            dao.insertStockPrice(StockPrice(itemId = item.id, price = e.price, qty = e.qty, store = cleanStore, at = at, fromReceipt = true))
            setOut(item.id, false)
            addQuantity(item.id, e.qty)
        }
    }
}

/** سطر قابل للتعديل في شاشة المراجعة. الأرقام نص علشان تتكتب براحتها */
data class ReviewLine(
    val uid: Int,
    val name: String,
    val qty: String,
    val price: String,
    val category: String,
    /** الصنف اللي عندك اللي السطر ده اترِبط بيه. null = صنف جديد */
    val itemId: Long? = null,
    val doubtful: Boolean = false,
) {
    val qtyValue get() = com.nabd.money.util.Money.parseInput(qty)?.takeIf { it > 0 }
    val priceValue get() = com.nabd.money.util.Money.parseInput(price)?.takeIf { it > 0 }
    val valid get() = name.isNotBlank() && qtyValue != null && priceValue != null
    val total get() = (qtyValue ?: 0.0) * (priceValue ?: 0.0)

    fun entry() = ReceiptEntry(name.trim(), qtyValue ?: 1.0, priceValue ?: 0.0, category, itemId)

    companion object {
        private fun text(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

        fun from(uid: Int, line: com.nabd.money.agents.ReceiptLine, items: List<StockItem>): ReviewLine {
            val match = StockCatalog.match(line.name, items)
            return ReviewLine(
                uid, line.name, text(line.qty), line.unitPrice?.let(::text).orEmpty(),
                match?.category ?: StockCatalog.guessCategory(line.name), match?.id, line.doubtful,
            )
        }
    }
}

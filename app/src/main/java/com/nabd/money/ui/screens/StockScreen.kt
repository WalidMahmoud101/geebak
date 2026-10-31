package com.nabd.money.ui.screens

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KebabDining
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nabd.money.agents.PriceCheck
import com.nabd.money.agents.PriceLevel
import com.nabd.money.agents.PriceVerdict
import com.nabd.money.data.CarrefourCatalog
import com.nabd.money.data.StockCatalog
import com.nabd.money.data.StockItem
import com.nabd.money.data.StockPrice
import com.nabd.money.ui.AppViewModel
import com.nabd.money.ui.ReceiptSource
import com.nabd.money.ui.components.EmptyState
import com.nabd.money.ui.components.GlassCard
import com.nabd.money.ui.components.GradientButton
import com.nabd.money.ui.components.IconBubble
import com.nabd.money.ui.components.PillButton
import com.nabd.money.ui.components.ScreenPadding
import com.nabd.money.ui.components.SectionHeader
import com.nabd.money.ui.components.SelectChip
import com.nabd.money.ui.theme.NabdColors
import com.nabd.money.util.Dates
import com.nabd.money.util.Money
import com.nabd.money.util.ReceiptOcr
import kotlinx.coroutines.launch

fun stockIcon(category: String): ImageVector = when (category) {
    StockCatalog.KITCHEN -> Icons.Rounded.Kitchen
    StockCatalog.FOOD -> Icons.Rounded.Restaurant
    StockCatalog.MEAT -> Icons.Rounded.KebabDining
    StockCatalog.CLEANING -> Icons.Rounded.CleaningServices
    StockCatalog.BATH -> Icons.Rounded.Bathtub
    StockCatalog.LUXURY -> Icons.Rounded.AutoAwesome
    StockCatalog.OTHER, StockCatalog.CARREFOUR, StockCatalog.PANTRY -> Icons.Rounded.LocalGroceryStore
    StockCatalog.FRUIT_VEG, StockCatalog.DAIRY, StockCatalog.BAKERY, StockCatalog.DRINKS -> Icons.Rounded.Restaurant
    StockCatalog.FRESH_MEAT -> Icons.Rounded.KebabDining
    StockCatalog.FROZEN -> Icons.Rounded.Kitchen
    StockCatalog.SNACKS -> Icons.Rounded.AutoAwesome
    StockCatalog.CARE, StockCatalog.BABY -> Icons.Rounded.Bathtub
    StockCatalog.HOUSEHOLD -> Icons.Rounded.CleaningServices
    else -> Icons.Rounded.Inventory2
}

fun stockColor(category: String): Color = when (category) {
    StockCatalog.KITCHEN, StockCatalog.PANTRY -> NabdColors.Gold
    StockCatalog.FOOD, StockCatalog.FRUIT_VEG, StockCatalog.DRINKS -> NabdColors.Mint
    StockCatalog.MEAT, StockCatalog.FRESH_MEAT -> NabdColors.Pink
    StockCatalog.CLEANING, StockCatalog.HOUSEHOLD, StockCatalog.FROZEN, StockCatalog.CARREFOUR -> NabdColors.Cyan
    StockCatalog.BATH, StockCatalog.CARE, StockCatalog.BABY -> NabdColors.Violet
    StockCatalog.LUXURY, StockCatalog.SNACKS, StockCatalog.DAIRY, StockCatalog.BAKERY -> NabdColors.Gold
    else -> NabdColors.TextDim
}

fun PriceLevel.color(): Color = when (this) {
    PriceLevel.EXPENSIVE -> NabdColors.Danger
    PriceLevel.CHEAP -> NabdColors.Mint
    PriceLevel.SAME -> NabdColors.TextDim
    PriceLevel.FIRST -> NabdColors.Cyan
}

/** الشارة الهادية: غالي +12% / رخيص −8% */
fun PriceVerdict.badge(): String = when (level) {
    // الرقم وعلامته معزولين، علشان الـ % مايتنقلش في النص العربي
    PriceLevel.EXPENSIVE -> "غالي \u2066+${kotlin.math.round(percent).toInt()}%\u2069"
    PriceLevel.CHEAP -> "رخيص \u2066−${kotlin.math.round(-percent).toInt()}%\u2069"
    else -> level.ar
}

/** الجملة اللي بتشرح الشارة */
fun PriceVerdict.explain(): String {
    val l = last ?: return "مفيش سعر قديم تتقارن بيه، وده هيبقى أول سعر ليها."
    val at = Dates.relative(l.at).takeIf { it != "دلوقتي" } ?: "النهاردة"
    val was = "آخر مرة ${Money.fmt(l.price)} $at" + (l.store?.let { " من $it" } ?: "")
    val low = lowest?.takeIf { it < l.price }?.let { " • أقل سعر ${Money.fmt(it)}" }.orEmpty()
    return was + low
}

@Composable
fun PriceBadge(v: PriceVerdict, modifier: Modifier = Modifier) {
    val c = v.level.color()
    Text(
        v.badge(), style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = if (NabdColors.isDark) 0.16f else 0.10f)).padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun SegmentedTwo(first: String, second: String, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NabdColors.SurfaceTop).padding(3.dp)) {
        listOf(first, second).forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) NabdColors.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) NabdColors.Text else NabdColors.TextDim, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/**
 * المخزون: حاجات البيت موجودة ولا خلصت، وقايمة الشرا من اللي خلص،
 * وأسعار كل حاجة من الفواتير علشان تعرف السعر الجديد غالي ولا رخيص.
 */
@Composable
fun StockScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val items by vm.stockItems.collectAsStateWithLifecycle()
    val prices by vm.stockPrices.collectAsStateWithLifecycle()
    val reading by vm.receiptReading.collectAsStateWithLifecycle()
    val session by vm.receipt.collectAsStateWithLifecycle()
    val message by vm.stockMessage.collectAsStateWithLifecycle()
    val undo by vm.stockUndo.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(0) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var buying by remember { mutableStateOf<StockItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    // الكاميرا ممكن تخلّي أندرويد يقفل التطبيق، فالمسار لازم يعيش بعد الرجوع
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.stockMessage.value = null }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCapture?.let(Uri::parse)
        pendingCapture = null
        if (ok && uri != null) vm.readReceipt(uri, ReceiptSource.CAMERA) else ReceiptOcr.clearCaptures(ctx)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.readReceipt(uri, ReceiptSource.GALLERY) }
    val takePhoto: () -> Unit = {
        val uri = ReceiptOcr.newCaptureUri(ctx)
        pendingCapture = uri.toString()
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pendingCapture = null
            Toast.makeText(ctx, "مفيش تطبيق كاميرا. اختار الصورة من المعرض.", Toast.LENGTH_LONG).show()
        }
    }

    StockContent(
        items = items, prices = prices, page = page, onPage = { page = it },
        category = category, onCategory = { category = it }, query = query, onQuery = { query = it },
        reading = reading,
        onBack = onBack, onAdd = { adding = true }, onCamera = takePhoto,
        onGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onManualReceipt = { vm.startEmptyReceipt() },
        onToggle = { vm.setStockOut(it, !it.isOut) },
        onOpen = { openId = it.id },
        onBought = { buying = it },
        onQuantity = { item, q -> vm.setStockQuantity(item, q) },
        onQuickAdd = { vm.addStock(it, StockCatalog.targetFor(it, category), page == 1); query = "" },
        onAddTo = { name, dept -> vm.addStock(name, dept, page == 1); query = "" },
        onClear = { confirmClear = true },
        undoCount = undo?.items?.size ?: 0,
        onUndo = { vm.undoClearStock() },
        onDismissUndo = { vm.stockUndo.value = null },
    )

    if (confirmClear) {
        val count = items.count { StockCatalog.inFilter(it.category, category) }
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = NabdColors.Surface,
            title = { Text(if (category == null) "تمسح المخزون كله؟" else "تمسح كل «$category»؟", style = MaterialTheme.typography.titleMedium) },
            text = { Text("$count حاجة وكل أسعارها هيتمسحوا. تقدر ترجّعهم بعد المسح على طول.", color = NabdColors.TextDim) },
            confirmButton = { TextButton(onClick = { vm.clearStock(category); category = null; confirmClear = false }) { Text("امسح", color = NabdColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } },
        )
    }

    if (adding) StockAddSheet(items, initialOut = page == 1, initialCategory = category, onAdd = { n, c, o, q -> vm.addStock(n, c, o, q) }) { adding = false }
    buying?.let { item ->
        BoughtSheet(item, prices[item.id].orEmpty(),
            onSave = { p, s, q ->
                if (p != null) vm.addStockPrice(item, p, s, q) else if (q != null) vm.boughtStock(item, q) else vm.setStockOut(item, false)
                buying = null
            },
            onDismiss = { buying = null })
    }
    openId?.let { id ->
        items.firstOrNull { it.id == id }?.let { item ->
            StockItemSheet(vm, item, prices[item.id].orEmpty(), items) { openId = null }
        }
    }
    session?.let { ReceiptReviewSheet(vm, it, items, prices) { vm.receipt.value = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockContent(
    items: List<StockItem>,
    prices: Map<Long, List<StockPrice>>,
    page: Int, onPage: (Int) -> Unit,
    category: String?, onCategory: (String?) -> Unit,
    query: String, onQuery: (String) -> Unit,
    reading: Boolean,
    onBack: () -> Unit, onAdd: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit, onManualReceipt: () -> Unit,
    onToggle: (StockItem) -> Unit, onOpen: (StockItem) -> Unit, onBought: (StockItem) -> Unit,
    modifier: Modifier = Modifier,
    onQuantity: (StockItem, Double) -> Unit = { _, _ -> },
    onQuickAdd: (String) -> Unit = {},
    onAddTo: (String, String) -> Unit = { _, _ -> },
    onClear: () -> Unit = {},
    undoCount: Int = 0,
    onUndo: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
) {
    val out = items.filter { it.isOut }
    val categories = (StockCatalog.builtIn + items.map { it.category } + StockCatalog.carrefour).distinct()
    val q = StockCatalog.key(query)
    // فلتر الحالة مع البحث بالصنف وشرايح النوع: 0 الكل، 1 موجود، 2 خلصت
    var status by rememberSaveable { mutableStateOf(0) }
    val shown = items.filter {
        StockCatalog.inFilter(it.category, category) && (q.isEmpty() || it.key.contains(q)) &&
            (status == 0 || it.isOut == (status == 2))
    }
    val inCarrefour = category == StockCatalog.CARREFOUR || category in StockCatalog.carrefour
    val have = items.map { it.key }.toSet()
    val catalogHits = CarrefourCatalog.search(query, category?.takeIf { inCarrefour }).filter { StockCatalog.key(it.first) !in have }.take(8)
    // اللي بتدوّر عليه مش موجود؟ يبقى ضيفه من مكانك
    val canQuickAdd = q.isNotEmpty() && items.none { it.key == q }

    LazyColumn(modifier, contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowForward, "رجوع") }
                Column(Modifier.weight(1f)) {
                    Text("المخزون", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (items.isEmpty()) "حاجات البيت وأسعارها" else if (out.isEmpty()) "${items.size} حاجة، ومفيش حاجة خلصت" else "${out.size} خلصت من ${items.size} حاجة",
                        style = MaterialTheme.typography.bodySmall, color = NabdColors.TextDim
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(NabdColors.Mint).clickable(onClick = onAdd)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, null, tint = NabdColors.OnAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ضيف", style = MaterialTheme.typography.labelLarge, color = NabdColors.OnAccent)
                }
            }
        }
        if (undoCount > 0) item {
            GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("اتمسح $undoCount حاجة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUndo) { Text("رجّعهم", color = NabdColors.Mint) }
                    IconButton(onClick = onDismissUndo) { Icon(Icons.Rounded.Close, "إخفاء", tint = NabdColors.TextFaint, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        item { SegmentedTwo("في البيت", if (out.isEmpty()) "قايمة الشرا" else "قايمة الشرا (${out.size})", page, onPage) }
        item {
            if (reading) {
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = NabdColors.Mint)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("بيقرا الفاتورة…", style = MaterialTheme.typography.titleSmall)
                            Text("على موبايلك، وممكن تاخد لحظات", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton("صوّر فاتورة", Modifier.weight(1.4f), Icons.Rounded.PhotoCamera, onCamera)
                    PillButton("من الصور", Icons.Rounded.PhotoLibrary, NabdColors.Mint, Modifier.weight(1f).height(42.dp), onClick = onGallery)
                }
            }
        }
        if (page == 0) {
            item {
                OutlinedTextField(
                    query, onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("دوّر على حاجة أو اكتب صنف جديد") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "امسح") } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canQuickAdd) onQuickAdd(query.trim()) }),
                )
            }
            if (catalogHits.isNotEmpty()) item {
                GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                    Text("من كارفور", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
                    catalogHits.forEach { (name, dept) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAddTo(name, dept) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(dept, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim, maxLines = 1)
                            }
                            Icon(Icons.Rounded.Add, "ضيف", tint = NabdColors.Mint, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            if (canQuickAdd) item {
                val target = StockCatalog.targetFor(query, category)
                GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onQuickAdd(query.trim()) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBubble(Icons.Rounded.Add, NabdColors.Mint, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ضيف «${query.trim()}»", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("في $target", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim, maxLines = 1)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("كل حاجة", category == null) { onCategory(null) }
                    SelectChip(StockCatalog.CARREFOUR, inCarrefour, stockColor(StockCatalog.CARREFOUR), stockIcon(StockCatalog.CARREFOUR)) { onCategory(StockCatalog.CARREFOUR) }
                    categories.filter { it !in StockCatalog.carrefour }.forEach { c -> SelectChip(c, category == c, stockColor(c), stockIcon(c)) { onCategory(c) } }
                }
            }
            if (inCarrefour) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StockCatalog.carrefour.forEach { c -> SelectChip(c, category == c, stockColor(c), stockIcon(c)) { onCategory(if (category == c) StockCatalog.CARREFOUR else c) } }
                }
            }
            if (items.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("كل الحالات", "موجود", "خلصت").forEachIndexed { i, label -> SelectChip(label, status == i) { status = i } }
                }
            }
            if (category != null) item { AddToCategory(category, onQuickAdd) }
            if (category in StockCatalog.carrefour) item { CarrefourPicks(category!!, have) { onAddTo(it, category) } }
            if (items.isEmpty()) {
                item { EmptyState(Icons.Rounded.Inventory2, "لسه مفيش حاجات", "ضيف اللي في البيت، أو صوّر فاتورة وهتتضاف لوحدها.") }
            } else if (shown.isEmpty() && !canQuickAdd) {
                item { Text("مفيش حاجة بالشكل ده.", style = MaterialTheme.typography.bodyMedium, color = NabdColors.TextDim, modifier = Modifier.padding(8.dp)) }
            }
            // الجروبات بنفس ترتيب الشرايح، وفي كل جروب اللي خلص الأول
            categories.forEach { c ->
                val inCat = shown.filter { it.category == c }
                if (inCat.isEmpty()) return@forEach
                if (category == null || category == StockCatalog.CARREFOUR) item(key = "h$c") { SectionHeader(c) }
                item(key = "g$c") {
                    GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                        inCat.forEachIndexed { i, it ->
                            if (i > 0) HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(horizontal = 10.dp))
                            StockRow(it, prices[it.id].orEmpty(), { onToggle(it) }, { q -> onQuantity(it, q) }) { onOpen(it) }
                        }
                    }
                }
            }
        } else {
            if (out.isEmpty()) {
                item { EmptyState(Icons.Rounded.ShoppingCart, "مفيش حاجة خلصت", "علّم أي حاجة «خلصت» من «في البيت» وهتظهر هنا وانت نازل تشتري.") }
            } else {
                val estimate = out.sumOf { prices[it.id]?.firstOrNull()?.price ?: 0.0 }
                val unknown = out.count { prices[it.id].isNullOrEmpty() }
                if (estimate > 0) item {
                    Text(
                        "متوقع حوالي ${Money.fmt(estimate)}" + (if (unknown > 0) " • و$unknown من غير سعر قديم" else ""),
                        style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim, modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                categories.forEach { c ->
                    val inCat = out.filter { it.category == c }
                    if (inCat.isEmpty()) return@forEach
                    item(key = "sh$c") { SectionHeader(c) }
                    item(key = "sg$c") {
                        GlassCard(Modifier.fillMaxWidth(), padding = 4.dp) {
                            inCat.forEachIndexed { i, it ->
                                if (i > 0) HorizontalDivider(color = NabdColors.Stroke, modifier = Modifier.padding(horizontal = 10.dp))
                                ShoppingRow(it, prices[it.id]?.firstOrNull()) { onBought(it) }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "عايز تكتب فاتورة بإيدك؟", style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onManualReceipt).padding(8.dp),
            )
        }
        if (page == 0 && items.any { StockCatalog.inFilter(it.category, category) }) item {
            PillButton(if (category == null) "امسح المخزون كله" else "امسح كل «$category»", Icons.Rounded.Delete, NabdColors.Danger, Modifier.fillMaxWidth(), onClick = onClear)
        }
    }
}

/** زرار واضح للتصنيف أو القسم المختار، لأي صنف مش موجود في التطبيق. الخانة بتفضل مفتوحة لصنف بعده */
@Composable
internal fun AddToCategory(category: String, onAdd: (String) -> Unit) {
    var writing by remember(category) { mutableStateOf(false) }
    var name by remember(category) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    fun add() {
        if (name.isNotBlank()) onAdd(name.trim())
        name = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PillButton("ضيف في «$category»", Icons.Rounded.Add, NabdColors.Mint, Modifier.fillMaxWidth()) { writing = !writing }
        if (writing) {
            OutlinedTextField(
                name, { name = it }, modifier = Modifier.fillMaxWidth().focusRequester(focus), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text("اسم الصنف في $category") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = { if (name.isNotBlank()) IconButton(onClick = { add() }) { Icon(Icons.Rounded.CheckCircle, "ضيف", tint = NabdColors.Mint) } },
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }
}

/** أصناف القسم من كارفور: لمسة تضيف، واللي عندك بيختفي من القايمة */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarrefourPicks(dept: String, have: Set<String>, onPick: (String) -> Unit) {
    var all by remember(dept) { mutableStateOf(false) }
    val left = CarrefourCatalog.items[dept].orEmpty().filter { StockCatalog.key(it) !in have }
    if (left.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("أصناف من كارفور", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (all) left else left.take(8)).forEach { SelectChip(it, false, NabdColors.Mint, Icons.Rounded.Add) { onPick(it) } }
        }
        if (left.size > 8) {
            Text(
                if (all) "أقل" else "كل الأصناف (${left.size})", style = MaterialTheme.typography.labelLarge, color = NabdColors.Mint,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = !all }.padding(4.dp),
            )
        }
    }
}

@Composable
private fun StockRow(item: StockItem, history: List<StockPrice>, onToggle: () -> Unit, onQuantity: (Double) -> Unit, onClick: () -> Unit) {
    val verdict = remember(history) { PriceCheck.latest(history) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(stockIcon(item.category), stockColor(item.category), 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val latest = history.firstOrNull()
                Text(
                    latest?.let { Money.fmt(it.price) } ?: "لسه من غير سعر",
                    style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim, maxLines = 1
                )
                if (verdict != null && verdict.level != PriceLevel.FIRST) {
                    Spacer(Modifier.width(6.dp))
                    PriceBadge(verdict)
                }
            }
            QuantityRow(item.quantity, onQuantity)
        }
        Spacer(Modifier.width(8.dp))
        val c = if (item.isOut) NabdColors.Danger else NabdColors.TextDim
        Text(
            if (item.isOut) "خلصت" else "موجود", style = MaterialTheme.typography.labelMedium, color = c,
            modifier = Modifier.clip(RoundedCornerShape(50))
                .background(if (item.isOut) c.copy(alpha = if (NabdColors.isDark) 0.16f else 0.10f) else NabdColors.SurfaceTop)
                .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** الكمية تحت السعر: بزرارين علشان تتغير من غير ما تفتح الصنف */
@Composable
private fun QuantityRow(quantity: Double?, onChange: (Double) -> Unit) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("الكمية", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
        Spacer(Modifier.width(6.dp))
        StepButton("−", "قلّل الكمية", enabled = (quantity ?: 0.0) > 0) { onChange(((quantity ?: 0.0) - 1).coerceAtLeast(0.0)) }
        Text(
            quantity?.let(StockCatalog::quantityText) ?: "—", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.widthIn(min = 34.dp).padding(horizontal = 4.dp),
        )
        StepButton("+", "زوّد الكمية", enabled = true) { onChange((quantity ?: 0.0) + 1) }
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(NabdColors.SurfaceTop)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) NabdColors.Text else NabdColors.TextFaint)
    }
}

@Composable
private fun ShoppingRow(item: StockItem, last: StockPrice?, onBought: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onBought).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.RadioButtonUnchecked, "جبتها", tint = NabdColors.TextFaint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        last?.let { Text("كانت بـ ${Money.fmt(it.price)}", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim, maxLines = 1) }
    }
}

/** خانة السعر وتحتها غالي ولا رخيص وانت بتكتب */
@Composable
fun PriceField(value: String, onChange: (String) -> Unit, history: List<StockPrice>, modifier: Modifier = Modifier) {
    val v = Money.parseInput(value)?.takeIf { it > 0 }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value, onChange, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            label = { Text("السعر النهاردة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (v != null) {
            val verdict = PriceCheck.check(v, history)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceBadge(verdict)
                Spacer(Modifier.width(8.dp))
                Text(verdict.explain(), style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** من قايمة الشرا: جبتها، وبكام؟ السعر اختياري */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoughtSheet(item: StockItem, history: List<StockPrice>, onSave: (Double?, String?, Double?) -> Unit, onDismiss: () -> Unit) {
    var price by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("1") }
    var store by remember { mutableStateOf(history.firstOrNull()?.store.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = NabdColors.Surface) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("جبت ${item.name}", style = MaterialTheme.typography.titleLarge)
            PriceField(price, { price = it }, history)
            OutlinedTextField(
                amount, { amount = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("جبت كام؟") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(store, { store = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("منين؟ (اختياري)") })
            val v = Money.parseInput(price)?.takeIf { it > 0 }
            GradientButton(if (v != null) "احفظ السعر" else "جبتها من غير سعر", Modifier.fillMaxWidth(), Icons.Rounded.CheckCircle) { onSave(v, store, Money.parseInput(amount)?.takeIf { it > 0 }) }
        }
    }
}

/** ضيف حاجة: اكتب الاسم والتصنيف بيتحدد لوحده وتقدر تغيّره، والشاشة بتفضل مفتوحة علشان تضيف كذا حاجة ورا بعض */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StockAddSheet(items: List<StockItem>, initialOut: Boolean, initialCategory: String?, onAdd: (String, String, Boolean, Double?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    val carrefourMode = initialCategory == StockCatalog.CARREFOUR
    var category by remember { mutableStateOf(initialCategory?.takeIf { !carrefourMode } ?: if (carrefourMode) StockCatalog.PANTRY else StockCatalog.FOOD) }
    // طول ما انت مااخترتش تصنيف بإيدك، بنخمّنه من الاسم
    var picked by remember { mutableStateOf(initialCategory != null && !carrefourMode) }
    var out by remember { mutableStateOf(initialOut) }
    var newCategory by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }
    var added by remember { mutableStateOf<List<String>>(emptyList()) }
    var hitType by remember { mutableStateOf<String?>(null) }
    val nameFocus = remember { FocusRequester() }
    val cats = (StockCatalog.builtIn + items.map { it.category }).distinct().filter { it !in StockCatalog.carrefour }
    val have = items.map { it.key }.toSet()
    val guessed = name.takeIf { !picked && it.isNotBlank() }?.let { StockCatalog.targetFor(it, initialCategory.takeIf { carrefourMode }) }
    val chosen = if (newCategory) custom.trim() else guessed ?: category
    fun add(n: String, into: String = chosen) {
        if (n.isBlank() || into.isEmpty()) return
        onAdd(n, into, out, Money.parseInput(quantity)?.takeIf { it >= 0 })
        added = added + n.trim()
        name = ""
        quantity = ""
        hitType = null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = NabdColors.Surface) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ضيف للمخزون", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                name, { name = it }, modifier = Modifier.fillMaxWidth().focusRequester(nameFocus), singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("ابحث أو اكتب اسم الصنف") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(name) }),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (name.isNotBlank()) IconButton(onClick = { add(name) }) { Icon(Icons.Rounded.CheckCircle, "ضيف", tint = NabdColors.Mint) } },
            )
            OutlinedTextField(
                quantity, { quantity = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text("الكمية في البيت (اختياري)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            // وانت بتكتب: اللي عندك، واللي في كارفور، وتفلتر النتايج بالنوع
            val typed = StockCatalog.key(name)
            val mine = if (typed.length >= 2) items.filter { it.key.contains(typed) }.take(4) else emptyList()
            val found = CarrefourCatalog.searchEverywhere(name).filter { StockCatalog.key(it.first) !in have && it.first !in added }
            val places = CarrefourCatalog.categoriesMatching(name, items.map { it.category })
            val hitTypes = found.map { it.second }.distinct()
            mine.forEach { i ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(i.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("عندك", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                }
            }
            if (places.isNotEmpty()) {
                Text("التصنيفات والأقسام", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // الانتقال للتصنيف: بنختاره ونفضّي البحث فتظهر أصنافه تحت
                    places.forEach { c -> SelectChip(c, false, stockColor(c), stockIcon(c)) { category = c; picked = true; newCategory = false; name = ""; hitType = null } }
                }
            }
            if (hitTypes.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("كل الأنواع", hitType == null || hitType !in hitTypes) { hitType = null }
                    hitTypes.forEach { t -> SelectChip(t, hitType == t, stockColor(t), stockIcon(t)) { hitType = t } }
                }
            }
            found.filter { hitType == null || hitType !in hitTypes || it.second == hitType }.take(10).forEach { (item, dept) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { add(item, dept) }.padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dept, style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim, maxLines = 1)
                    }
                    Icon(Icons.Rounded.Add, "ضيف ${item}", tint = NabdColors.Mint, modifier = Modifier.size(20.dp))
                }
            }
            Text("التصنيف", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEach { c -> SelectChip(c, !newCategory && chosen == c, stockColor(c), stockIcon(c)) { category = c; picked = true; newCategory = false } }
                SelectChip("تصنيف جديد", newCategory, NabdColors.Mint, Icons.Rounded.Add) { newCategory = true }
            }
            Text("أقسام كارفور", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StockCatalog.carrefour.forEach { c -> SelectChip(c, !newCategory && chosen == c, stockColor(c), stockIcon(c)) { category = c; picked = true; newCategory = false } }
            }
            if (newCategory) {
                OutlinedTextField(custom, { custom = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("اسم التصنيف") })
            }
            val ideas = (StockCatalog.suggestions[chosen].orEmpty() + CarrefourCatalog.items[chosen].orEmpty()).filter { StockCatalog.key(it) !in have && it !in added && name.isBlank() }
            if (name.isBlank()) {
                Text("أو اختار بسرعة", style = MaterialTheme.typography.labelLarge, color = NabdColors.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // حاجة مش في الاختيارات: بنثبّت القسم المختار ونوديك على خانة الاسم
                    SelectChip("حاجة جديدة", false, NabdColors.Mint, Icons.Rounded.Edit) {
                        if (!newCategory) { category = chosen; picked = true }
                        nameFocus.requestFocus()
                    }
                    ideas.forEach { s -> SelectChip(s, false, NabdColors.Mint, Icons.Rounded.Add) { add(s) } }
                }
            }
            Row(Modifier.fillMaxWidth().clickable { out = !out }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("خلصت خلاص", style = MaterialTheme.typography.titleSmall)
                    Text("تنزل على قايمة الشرا", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim)
                }
                Switch(out, { out = it })
            }
            if (added.isNotEmpty()) Text("اتضاف: ${added.joinToString("، ")}", style = MaterialTheme.typography.labelMedium, color = NabdColors.Mint)
            GradientButton("ضيف", Modifier.fillMaxWidth(), Icons.Rounded.Add) { add(name) }
        }
    }
}

/** صفحة الحاجة: سعرها دلوقتي، وتاريخ أسعارها، وتعديلها */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StockItemSheet(vm: AppViewModel, item: StockItem, history: List<StockPrice>, items: List<StockItem>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var price by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var name by remember(item.name) { mutableStateOf(item.name) }
    var category by remember(item.category) { mutableStateOf(item.category) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var quantity by remember(item.quantity) { mutableStateOf(item.quantity?.let(StockCatalog::quantityText).orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = NabdColors.Surface) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(stockIcon(item.category), stockColor(item.category), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleLarge)
                    Text(item.category, style = MaterialTheme.typography.labelMedium, color = NabdColors.TextDim)
                }
                IconButton(onClick = { editing = !editing }) { Icon(Icons.Rounded.Edit, "عدّل", tint = NabdColors.TextDim) }
            }
            if (editing) {
                OutlinedTextField(name, { name = it; error = null }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("الاسم") })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (StockCatalog.builtIn + items.map { it.category } + StockCatalog.carrefour).distinct().forEach { c -> SelectChip(c, category == c, stockColor(c), stockIcon(c)) { category = c } }
                }
                error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = NabdColors.Danger) }
                GradientButton("احفظ التعديل", Modifier.fillMaxWidth()) {
                    scope.launch {
                        if (vm.editStock(item, name, category)) editing = false else error = "فيه حاجة تانية بنفس الاسم"
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (item.isOut) "خلصت من البيت" else "موجودة في البيت", style = MaterialTheme.typography.titleSmall)
                    item.outAt?.takeIf { item.isOut }?.let { Text("خلصت ${Dates.relative(it)}", style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim) }
                }
                Switch(item.isOut, { vm.setStockOut(item, it) })
            }
            OutlinedTextField(
                quantity, { quantity = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
                label = { Text("الكمية في البيت") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            val typedQuantity = Money.parseInput(quantity)?.takeIf { it >= 0 }
            if (typedQuantity != item.quantity && (typedQuantity != null || quantity.isBlank())) {
                GradientButton("احفظ الكمية", Modifier.fillMaxWidth(), Icons.Rounded.CheckCircle) { vm.setStockQuantity(item, typedQuantity) }
            }
            HorizontalDivider(color = NabdColors.Stroke)
            PriceField(price, { price = it }, history)
            OutlinedTextField(store, { store = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("منين؟ (اختياري)") })
            val v = Money.parseInput(price)?.takeIf { it > 0 }
            if (v != null) {
                GradientButton("سجّل السعر", Modifier.fillMaxWidth(), Icons.Rounded.CheckCircle) {
                    vm.addStockPrice(item, v, store)
                    price = ""; store = ""
                }
            }
            if (history.isNotEmpty()) {
                SectionHeader("الأسعار اللي فاتت")
                history.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Money.fmt(p.price), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(Dates.date(p.at), p.store, "من فاتورة".takeIf { p.fromReceipt }).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall, color = NabdColors.TextDim
                            )
                        }
                        IconButton(onClick = { vm.deleteStockPrice(p) }) { Icon(Icons.Rounded.Close, "امسح السعر", tint = NabdColors.TextFaint, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            PillButton("احذف من المخزون", Icons.Rounded.Delete, NabdColors.Danger, Modifier.fillMaxWidth()) { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NabdColors.Surface,
            title = { Text("تحذف ${item.name}؟", style = MaterialTheme.typography.titleMedium) },
            text = { Text("الحاجة وكل أسعارها هيتمسحوا.", color = NabdColors.TextDim) },
            confirmButton = { TextButton(onClick = { vm.deleteStock(item); confirmDelete = false; onDismiss() }) { Text("احذف", color = NabdColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}

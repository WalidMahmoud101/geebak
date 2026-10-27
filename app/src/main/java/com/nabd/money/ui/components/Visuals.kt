package com.nabd.money.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.data.Account
import com.nabd.money.data.AccountType
import com.nabd.money.data.Purpose
import com.nabd.money.ui.theme.NabdColors

fun Purpose.color(): Color = when (this) {
    Purpose.HOME -> NabdColors.Mint
    Purpose.WORK -> NabdColors.Cyan
    Purpose.CAR -> NabdColors.Gold
    Purpose.OUTING -> NabdColors.Pink
    Purpose.PERSONAL -> NabdColors.Violet
    Purpose.OTHER -> NabdColors.TextDim
}

fun Purpose.icon(): ImageVector = when (this) {
    Purpose.HOME -> Icons.Rounded.Home
    Purpose.WORK -> Icons.Rounded.Work
    Purpose.CAR -> Icons.Rounded.DirectionsCar
    Purpose.OUTING -> Icons.Rounded.Celebration
    Purpose.PERSONAL -> Icons.Rounded.Person
    Purpose.OTHER -> Icons.Rounded.Category
}

fun AccountType.icon(): ImageVector = when (this) {
    AccountType.BANK -> Icons.Rounded.AccountBalance
    AccountType.WALLET -> Icons.Rounded.PhoneAndroid
    AccountType.CARD -> Icons.Rounded.CreditCard
    AccountType.CRYPTO -> Icons.Rounded.CurrencyBitcoin
    AccountType.CASH -> Icons.Rounded.Payments
}

fun AccountType.colors(): List<Color> = when (this) {
    AccountType.BANK -> listOf(Color(0xFF4F6BFF), Color(0xFF7A3DFF))
    AccountType.WALLET -> listOf(Color(0xFFFF7A45), Color(0xFFFF3D77))
    AccountType.CARD -> listOf(Color(0xFF16C79A), Color(0xFF2A7FFF))
    AccountType.CRYPTO -> listOf(Color(0xFFF5C451), Color(0xFFE0751C))
    AccountType.CASH -> listOf(Color(0xFF59C27A), Color(0xFF1F7A55))
}

fun Account.colors(): List<Color> {
    val agent = AgentRegistry.byId(agentId)
    if (agent == null || agentId == AgentRegistry.MANUAL_ID || agentId == "other_banks") return type.colors()
    val c = Color(agent.color)
    return listOf(c, type.colors().last())
}

fun agentIcon(key: String): ImageVector = when (key) {
    "bolt" -> Icons.Rounded.Bolt
    "phone" -> Icons.Rounded.PhoneAndroid
    "receipt" -> Icons.Rounded.Receipt
    "card" -> Icons.Rounded.CreditCard
    "crypto" -> Icons.Rounded.CurrencyBitcoin
    "bank" -> Icons.Rounded.AccountBalance
    "category" -> Icons.Rounded.Category
    "shield" -> Icons.Rounded.Shield
    "repeat" -> Icons.Rounded.Repeat
    "savings" -> Icons.Rounded.Savings
    "warning" -> Icons.Rounded.Warning
    "salary" -> Icons.Rounded.Paid
    else -> Icons.Rounded.Edit
}

/** لون الوكيل زي ما هو في الداكن، وأغمق شوية في الفاتح علشان يبان على الأبيض */
fun agentTint(color: Long): Color {
    val c = Color(color)
    if (NabdColors.isDark) return c
    val k = 0.72f
    return Color(c.red * k, c.green * k, c.blue * k, 1f)
}

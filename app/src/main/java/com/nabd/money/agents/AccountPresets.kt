package com.nabd.money.agents

import com.nabd.money.data.AccountType

/**
 * حسابات جاهزة تضيفها بضغطة: لو ليها وكيل (Binance، Trust Wallet، كروت Wallet…) أي إشعار منه بيتسجل عليها لوحده،
 * ولو مالهاش (Apple Pay، PayPal…) بتسجل عليها بإيدك أو بالصوت.
 */
data class AccountPreset(
    val label: String,
    val type: AccountType,
    val currency: String,
    /** وكيل الالتقاط التلقائي، أو null = يدوي */
    val agentId: String?,
    /** بيطلب آخر 4 أرقام الكارت علشان يربط الإشعارات بالكارت ده */
    val askLast4: Boolean = false,
    val note: String,
)

object AccountPresets {
    val all: List<AccountPreset> = listOf(
        AccountPreset("Binance", AccountType.CRYPTO, "USDT", "binance", note = "إشعارات Binance (إيداع وسحب) بتتسجل عليه لوحدها"),
        AccountPreset("Trust Wallet", AccountType.CRYPTO, "USDT", "trust_wallet", note = "إشعارات Trust Wallet بتتسجل عليه لوحدها"),
        AccountPreset("Visa", AccountType.CARD, "EGP", "wallet_cards", askLast4 = true, note = "دفعات الكارت من Google Pay / Samsung Pay بتتسجل لوحدها لو كتبت آخر 4 أرقام"),
        AccountPreset("Mastercard", AccountType.CARD, "EGP", "wallet_cards", askLast4 = true, note = "دفعات الكارت من Google Pay / Samsung Pay بتتسجل لوحدها لو كتبت آخر 4 أرقام"),
        AccountPreset("Meeza", AccountType.CARD, "EGP", "wallet_cards", askLast4 = true, note = "دفعات الكارت من Google Pay / Samsung Pay بتتسجل لوحدها لو كتبت آخر 4 أرقام"),
        AccountPreset("Google Pay", AccountType.CARD, "EGP", "wallet_cards", askLast4 = true, note = "دفعات Google Pay / Wallet بتتسجل لوحدها"),
        AccountPreset("Apple Pay", AccountType.CARD, "EGP", null, note = "Apple Pay مش بيبعت إشعارات لأندرويد، فسجّل عملياته بإيدك أو بالصوت"),
        AccountPreset("RedotPay", AccountType.CARD, "USD", "redotpay", note = "إشعارات RedotPay بتتسجل لوحدها"),
        AccountPreset("Kast", AccountType.CARD, "USD", "kast", note = "إشعارات Kast بتتسجل لوحدها"),
        AccountPreset("PayPal", AccountType.WALLET, "USD", null, note = "سجّل عملياته بإيدك أو بالصوت"),
        AccountPreset("Wise", AccountType.WALLET, "USD", null, note = "سجّل عملياته بإيدك أو بالصوت"),
        AccountPreset("Payoneer", AccountType.WALLET, "USD", null, note = "سجّل عملياته بإيدك أو بالصوت"),
    )
}

package com.nabd.money.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nabd.money.agents.AgentRegistry
import com.nabd.money.data.Account
import com.nabd.money.data.Direction
import com.nabd.money.data.Txn
import java.io.File

/** تصدير العمليات اللي في الفلتر لملف يفتح في Excel و Google Sheets بالعربي */
object CsvExport {
    private fun cell(v: Any?): String {
        val s = v?.toString().orEmpty()
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    fun write(ctx: Context, txns: List<Txn>, accounts: List<Account>, egp: (Txn) -> Double, label: String): File {
        val names = accounts.associate { it.id to it.name }
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val safe = label.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40)
        val file = File(dir, "geebak-$safe.csv")
        val header = listOf(
            "التاريخ", "الوقت", "النوع", "المبلغ", "العملة", "بالجنيه", "الوصف", "التصنيف", "الغرض",
            "الحساب", "المصدر", "الوكيل", "على حساب الشركة", "اتسلمت من الشركة", "في الإجمالي", "ملاحظة",
        )
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿") // علشان Excel يقرا العربي صح
            w.write(header.joinToString(",") + "\n")
            txns.sortedBy { it.timestamp }.forEach { t ->
                val d = Dates.toLocal(t.timestamp)
                w.write(
                    listOf(
                        "%04d-%02d-%02d".format(java.util.Locale.US, d.year, d.monthValue, d.dayOfMonth),
                        "%02d:%02d".format(java.util.Locale.US, d.hour, d.minute),
                        if (t.direction == Direction.OUT) "مصروف" else "دخل",
                        t.amount, t.currency, "%.2f".format(java.util.Locale.US, egp(t)),
                        t.merchant, t.category, t.purpose.ar, t.accountId?.let { names[it] } ?: "كاش",
                        t.source.ar, AgentRegistry.byId(t.agentId)?.name,
                        if (t.reimbursable) "نعم" else "", t.reimbursedAt?.let { Dates.date(it) } ?: "",
                        if (t.excluded) "لا" else "نعم", t.note,
                    ).joinToString(",") { cell(it) } + "\n"
                )
            }
        }
        return file
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "مصاريف جيبك")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "تصدير لـ Excel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

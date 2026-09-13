package com.notaspese.app

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat

object PdfExporter {
    fun create(context: Context, profile: Map<String, String>, expenses: List<Expense>): File {
        val pdf = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK; textSize = 11f }
        val bold = Paint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
        val df = DecimalFormat("#,##0.00")

        var pageNo = 1
        fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(595, 842, pageNo++).create()
            return pdf.startPage(info)
        }

        var page = newPage()
        var canvas = page.canvas
        var y = 45f

        fun text(s: String, x: Float = 40f, p: Paint = paint, gap: Float = 18f) {
            canvas.drawText(s, x, y, p); y += gap
        }

        text("NOTA SPESE", p = Paint(bold).apply { textSize = 20f }, gap = 28f)
        text("Nome e cognome: ${profile["nome"]} ${profile["cognome"]}")
        text("Azienda: ${profile["azienda"]}")
        text("Mese di riferimento: ${profile["mese"]}", gap = 26f)

        canvas.drawText("Data", 40f, y, bold)
        canvas.drawText("Descrizione", 125f, y, bold)
        canvas.drawText("Valuta", 385f, y, bold)
        canvas.drawText("Importo", 465f, y, bold)
        y += 20f

        expenses.forEach { e ->
            if (y > 760f) {
                pdf.finishPage(page)
                page = newPage(); canvas = page.canvas; y = 45f
            }
            canvas.drawText(e.date.take(12), 40f, y, paint)
            canvas.drawText(e.merchant.take(35), 125f, y, paint)
            canvas.drawText(e.currency, 390f, y, paint)
            canvas.drawText(df.format(e.amount), 465f, y, paint)
            y += 19f
        }

        y += 10f
        expenses.groupBy { it.currency }.forEach { (cur, list) ->
            text("Totale $cur: ${df.format(list.sumOf { it.amount })}", p = bold)
        }
        pdf.finishPage(page)

        expenses.filter { !it.imagePath.isNullOrBlank() }.forEach { e ->
            val f = File(e.imagePath!!)
            if (f.exists()) {
                val p = newPage()
                val c = p.canvas
                c.drawText("${e.date} - ${e.merchant} - ${e.currency} ${df.format(e.amount)}", 35f, 35f, bold)
                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp != null) {
                    val ratio = minOf(525f / bmp.width, 750f / bmp.height)
                    val w = bmp.width * ratio
                    val h = bmp.height * ratio
                    c.drawBitmap(bmp, null, RectF((595-w)/2f, 55f, (595+w)/2f, 55f+h), paint)
                    bmp.recycle()
                }
                pdf.finishPage(p)
            }
        }

        val dir = File(context.filesDir, "pdf").apply { mkdirs() }
        val out = File(dir, "NotaSpese_${(profile["mese"] ?: "mese").replace("/", "-")}.pdf")
        FileOutputStream(out).use { pdf.writeTo(it) }
        pdf.close()
        return out
    }
}

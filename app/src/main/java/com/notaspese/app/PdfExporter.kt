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
        val df = DecimalFormat("#,##0.00")

        val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 35, 35); textSize = 10f }
        val bold = Paint(black).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val title = Paint(bold).apply { textSize = 22f }
        val subtitle = Paint(bold).apply { textSize = 12f }
        val small = Paint(black).apply { textSize = 8.5f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(185, 190, 195); strokeWidth = 1f }
        val darkLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 95, 100); strokeWidth = 1.2f }
        val headerFill = Paint().apply { color = Color.rgb(235, 238, 241) }
        val totalFill = Paint().apply { color = Color.rgb(246, 247, 248) }

        var pageNo = 1
        fun newPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(595, 842, pageNo++).create()
            return pdf.startPage(info)
        }

        fun footer(canvas: Canvas, number: Int) {
            canvas.drawLine(35f, 805f, 560f, 805f, line)
            canvas.drawText("Nota spese - documento generato elettronicamente", 35f, 821f, small)
            canvas.drawText("Pagina $number", 515f, 821f, small)
        }

        fun reportHeader(canvas: Canvas, continuation: Boolean = false): Float {
            canvas.drawText(profile["azienda"].orEmpty().ifBlank { "AZIENDA" }, 35f, 48f, subtitle)
            canvas.drawText(if (continuation) "NOTA SPESE - CONTINUAZIONE" else "NOTA SPESE", 35f, 83f, title)
            canvas.drawLine(35f, 96f, 560f, 96f, darkLine)

            if (continuation) return 125f

            canvas.drawRect(35f, 116f, 560f, 190f, headerFill)
            canvas.drawText("DIPENDENTE", 50f, 137f, small)
            canvas.drawText("${profile["nome"].orEmpty()} ${profile["cognome"].orEmpty()}".trim(), 50f, 158f, bold)
            canvas.drawText("AZIENDA DI RIFERIMENTO", 315f, 137f, small)
            canvas.drawText(profile["azienda"].orEmpty(), 315f, 158f, bold)
            canvas.drawText("MESE DI RIFERIMENTO", 50f, 180f, small)
            canvas.drawText(profile["mese"].orEmpty(), 155f, 180f, bold)

            canvas.drawText("DETTAGLIO DELLE SPESE", 35f, 222f, subtitle)
            return 240f
        }

        fun tableHeader(canvas: Canvas, y: Float): Float {
            canvas.drawRect(35f, y, 560f, y + 25f, headerFill)
            canvas.drawText("Data", 45f, y + 17f, bold)
            canvas.drawText("Descrizione / esercente", 125f, y + 17f, bold)
            canvas.drawText("Valuta", 410f, y + 17f, bold)
            canvas.drawText("Importo", 493f, y + 17f, bold)
            canvas.drawLine(35f, y + 25f, 560f, y + 25f, darkLine)
            return y + 25f
        }

        var currentPageNumber = 1
        var page = newPage()
        var canvas = page.canvas
        var y = reportHeader(canvas)
        y = tableHeader(canvas, y)

        expenses.forEach { e ->
            if (y > 690f) {
                footer(canvas, currentPageNumber)
                pdf.finishPage(page)
                currentPageNumber++
                page = newPage()
                canvas = page.canvas
                y = reportHeader(canvas, continuation = true)
                y = tableHeader(canvas, y)
            }

            val rowTop = y
            val description = e.merchant.take(44)
            canvas.drawText(e.date.take(12), 45f, y + 18f, black)
            canvas.drawText(description, 125f, y + 18f, black)
            canvas.drawText(e.currency, 415f, y + 18f, black)
            canvas.drawText(df.format(e.amount), 495f, y + 18f, black)
            canvas.drawLine(35f, rowTop + 27f, 560f, rowTop + 27f, line)
            y += 28f
        }

        if (y > 615f) {
            footer(canvas, currentPageNumber)
            pdf.finishPage(page)
            currentPageNumber++
            page = newPage()
            canvas = page.canvas
            y = reportHeader(canvas, continuation = true)
        }

        y += 18f
        canvas.drawText("RIEPILOGO", 35f, y, subtitle)
        y += 14f

        val grouped = expenses.groupBy { it.currency }
        if (grouped.isEmpty()) {
            canvas.drawRect(35f, y, 560f, y + 35f, totalFill)
            canvas.drawText("Nessuna spesa registrata nel periodo selezionato", 50f, y + 22f, bold)
            y += 50f
        } else {
            grouped.forEach { (cur, list) ->
                canvas.drawRect(35f, y, 560f, y + 32f, totalFill)
                canvas.drawText("Totale $cur", 50f, y + 21f, bold)
                canvas.drawText(df.format(list.sumOf { it.amount }), 495f, y + 21f, bold)
                y += 38f
            }
        }

        y += 14f
        canvas.drawText("DICHIARAZIONE DEL DIPENDENTE", 35f, y, subtitle)
        y += 20f
        canvas.drawText("Dichiaro che le spese sopra indicate sono state sostenute per finalita professionali", 35f, y, small)
        y += 14f
        canvas.drawText("e che i relativi giustificativi allegati corrispondono alle spese riportate nel presente rendiconto.", 35f, y, small)
        y += 36f

        canvas.drawText("Luogo e data", 35f, y, small)
        canvas.drawText("Firma del dipendente", 340f, y, small)
        y += 38f
        canvas.drawLine(35f, y, 250f, y, darkLine)
        canvas.drawLine(340f, y, 560f, y, darkLine)

        footer(canvas, currentPageNumber)
        pdf.finishPage(page)

        expenses.filter { !it.imagePath.isNullOrBlank() }.forEach { e ->
            val f = File(e.imagePath!!)
            if (f.exists()) {
                currentPageNumber++
                val p = newPage()
                val c = p.canvas
                c.drawText(profile["azienda"].orEmpty().ifBlank { "AZIENDA" }, 35f, 48f, subtitle)
                c.drawText("GIUSTIFICATIVO DI SPESA", 35f, 80f, title)
                c.drawLine(35f, 94f, 560f, 94f, darkLine)
                c.drawText("${e.date}   |   ${e.merchant.take(42)}   |   ${e.currency} ${df.format(e.amount)}", 35f, 120f, bold)

                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp != null) {
                    val ratio = minOf(525f / bmp.width, 635f / bmp.height)
                    val w = bmp.width * ratio
                    val h = bmp.height * ratio
                    c.drawBitmap(bmp, null, RectF((595f - w) / 2f, 145f, (595f + w) / 2f, 145f + h), black)
                    bmp.recycle()
                }
                footer(c, currentPageNumber)
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

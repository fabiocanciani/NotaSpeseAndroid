package com.notaspese.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.notaspese.app.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var store: ExpenseStore
    private val expenses = mutableListOf<Expense>()
    private var currentPhoto: File? = null
    private var lastPdf: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) currentPhoto?.let { runOcr(it) }
    }
    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast("Permesso fotocamera necessario.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = ExpenseStore(this)

        val p = store.profile()
        b.etNome.setText(p["nome"]); b.etCognome.setText(p["cognome"]); b.etAzienda.setText(p["azienda"])
        b.etMese.setText(p["mese"].takeUnless { it.isNullOrBlank() } ?: SimpleDateFormat("yyyy-MM", Locale.ITALY).format(Date()))
        expenses.addAll(store.loadExpenses())
        refresh()

        b.btnSalvaProfilo.setOnClickListener { saveProfile(); toast("Intestazione salvata") }
        b.btnFoto.setOnClickListener { ensureCamera() }
        b.btnManuale.setOnClickListener { showExpenseDialog(null) }
        b.btnEliminaPeriodo.setOnClickListener { showDeleteRangeDialog() }
        b.btnPdf.setOnClickListener {
            saveProfile()
            lastPdf = PdfExporter.create(this, store.profile(), expensesForSelectedMonth())
            toast("PDF creato")
        }
        b.btnStampa.setOnClickListener {
            saveProfile()
            val f = PdfExporter.create(this, store.profile(), expensesForSelectedMonth())
            lastPdf = f
            printPdf(f)
        }
        b.btnCondividi.setOnClickListener {
            val f = lastPdf ?: PdfExporter.create(this, store.profile(), expensesForSelectedMonth()).also { lastPdf = it }
            sharePdf(f)
        }
    }

    private fun saveProfile() = store.saveProfile(
        b.etNome.text.toString().trim(), b.etCognome.text.toString().trim(),
        b.etAzienda.text.toString().trim(), b.etMese.text.toString().trim()
    )

    private fun ensureCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val dir = File(filesDir, "receipts").apply { mkdirs() }
        currentPhoto = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", currentPhoto!!)
        takePicture.launch(uri)
    }

    private fun runOcr(file: File) {
        val image = InputImage.fromFilePath(this, Uri.fromFile(file))
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener {
                val p = ReceiptParser.parse(it.text)
                showExpenseDialog(Expense(
                    date = p.date.ifBlank { today() }, merchant = p.merchant,
                    amount = p.amount, currency = p.currency, imagePath = file.absolutePath
                ))
            }
            .addOnFailureListener {
                toast("Lettura non riuscita: inserisci i dati manualmente.")
                showExpenseDialog(Expense(date = today(), merchant = "", amount = 0.0, currency = "CHF", imagePath = file.absolutePath))
            }
    }

    private fun showExpenseDialog(existing: Expense?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,12,48,0) }
        fun field(h: String, v: String): EditText = EditText(this).apply { hint=h; setText(v); box.addView(this) }
        val date = field("Data (GG/MM/AAAA)", existing?.date ?: today())
        val merchant = field("Esercente / descrizione", existing?.merchant ?: "")
        val amount = field("Importo", if ((existing?.amount ?: 0.0) > 0) existing!!.amount.toString() else "")
        val currency = field("Valuta (CHF/EUR)", existing?.currency ?: "CHF")

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nuova spesa" else "Controlla i dati letti")
            .setView(box)
            .setPositiveButton("Salva") { _, _ ->
                val e = existing ?: Expense(date="", merchant="", amount=0.0, currency="CHF")
                e.date = date.text.toString().trim()
                e.merchant = merchant.text.toString().trim().ifBlank { "Spesa" }
                e.amount = amount.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
                e.currency = currency.text.toString().trim().uppercase().ifBlank { "CHF" }
                if (expenses.none { it.id == e.id }) expenses.add(e)
                store.saveExpenses(expenses); refresh()
            }
            .setNegativeButton("Annulla", null).show()
    }

    private fun showDeleteRangeDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,12,48,0) }
        val from = EditText(this).apply { hint = "Dalla data (GG/MM/AAAA)"; box.addView(this) }
        val to = EditText(this).apply { hint = "Alla data (GG/MM/AAAA)"; box.addView(this) }

        AlertDialog.Builder(this)
            .setTitle("Cancella spese per periodo")
            .setMessage("Verranno eliminate tutte le spese comprese tra le due date, incluse.")
            .setView(box)
            .setPositiveButton("Continua") { _, _ ->
                val parser = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).apply { isLenient = false }
                val start = try { parser.parse(from.text.toString().trim()) } catch (_: Exception) { null }
                val end = try { parser.parse(to.text.toString().trim()) } catch (_: Exception) { null }
                if (start == null || end == null || start.after(end)) {
                    toast("Inserisci un intervallo di date valido.")
                    return@setPositiveButton
                }
                val matches = expenses.filter { e ->
                    try {
                        val d = parser.parse(e.date) ?: return@filter false
                        !d.before(start) && !d.after(end)
                    } catch (_: Exception) { false }
                }
                if (matches.isEmpty()) {
                    toast("Nessuna spesa trovata nel periodo indicato.")
                    return@setPositiveButton
                }
                AlertDialog.Builder(this)
                    .setTitle("Conferma eliminazione")
                    .setMessage("Eliminare ${matches.size} spese dal ${from.text} al ${to.text}?")
                    .setPositiveButton("Elimina") { _, _ ->
                        matches.forEach { it.imagePath?.let { path -> File(path).delete() } }
                        val ids = matches.map { it.id }.toSet()
                        expenses.removeAll { it.id in ids }
                        store.saveExpenses(expenses)
                        refresh()
                        toast("${matches.size} spese eliminate.")
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun refresh() {
        b.containerSpese.removeAllViews()
        val list = expensesForSelectedMonth()
        list.forEach { e ->
            val row = TextView(this).apply {
                text = "${e.date}   ${e.merchant}   ${e.currency} %.2f".format(e.amount)
                textSize = 16f; setPadding(6,12,6,12)
                setOnClickListener { showActions(e) }
            }
            b.containerSpese.addView(row)
        }
        val totals = list.groupBy { it.currency }.map { (c, l) -> "$c %.2f".format(l.sumOf { it.amount }) }
        b.tvTotali.text = if (totals.isEmpty()) "Nessuna spesa nel mese selezionato" else "Totali: " + totals.joinToString("  |  ")
    }

    private fun expensesForSelectedMonth(): List<Expense> {
        val ym = Regex("(20\\d{2})-(0[1-9]|1[0-2])").find(b.etMese.text.toString())?.value ?: return expenses
        val parser = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).apply { isLenient = false }
        val out = SimpleDateFormat("yyyy-MM", Locale.ITALY)
        return expenses.filter { e -> try { out.format(parser.parse(e.date)!!) == ym } catch (_: Exception) { true } }
    }

    private fun showActions(e: Expense) {
        AlertDialog.Builder(this).setTitle(e.merchant)
            .setItems(arrayOf("Modifica", "Elimina")) { _, which ->
                if (which == 0) showExpenseDialog(e) else {
                    expenses.removeAll { it.id == e.id }
                    e.imagePath?.let { File(it).delete() }
                    store.saveExpenses(expenses); refresh()
                }
            }.show()
    }

    private fun printPdf(file: File) {
        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled(); return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback?
            ) {
                if (destination == null) {
                    callback?.onWriteFailed("Destinazione di stampa non disponibile"); return
                }
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        val month = b.etMese.text.toString().trim().ifBlank { "mese" }
        printManager.print("Nota Spese $month", adapter, PrintAttributes.Builder().build())
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Condividi nota spese"))
    }

    private fun today() = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(Date())
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

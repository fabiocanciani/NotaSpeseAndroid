package com.notaspese.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
        b.btnPdf.setOnClickListener {
            saveProfile()
            lastPdf = PdfExporter.create(this, store.profile(), expensesForSelectedMonth())
            toast("PDF creato")
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

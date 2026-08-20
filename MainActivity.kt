package com.jaidurga.billing

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "jd_retailer_account"
        const val KEY_PHONE = "phone"
        const val KEY_ACCESS_CODE = "access_code"
        const val BACKUP_FILE = "billing_backup.json"
        const val PUBLIC_KEY_PEM = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAt4kOmoi9R6fxDfrrdvx1
YVFHucmouucQudlmAbE/rKZzgJDtP8pERHRzjbhhH95UBd9wYjmq2hqYDoZcZqgB
V7GV3rOwR3S+DoW09kyhRMu/HdWD7y3OQ5JkM7jrFGiijkPeo0OA7yqMc7YV4bSh
cXs54dABOD0UhQ89rpNtcjvzDiI/vpDNIs/GFKoCJr/zxl9/B13o0oZR468mlEdH
3D//9jZ8Vf/HC5u+Y6S7zjFYLR1PZ+y27jGvI9eYYgR5Kk0DpQ9I1YgEIN6AvzF/
TBK0EoUKcWwC3MryIjCJ1ezWP9YGH1bJOzNYVF7a2YJSlFMJ+atVa2AHSthDZu27
nQIDAQAB
-----END PUBLIC KEY-----
"""
    }

    private lateinit var root: LinearLayout
    private lateinit var welcomePanel: LinearLayout
    private lateinit var accessPanel: LinearLayout
    private lateinit var webView: WebView
    private var activePhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        restoreState()
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun restoreState() {
        activePhone = prefs().getString(KEY_PHONE, "") ?: ""
        val code = prefs().getString(KEY_ACCESS_CODE, "") ?: ""

        if (activePhone.isNotBlank() && verifyAccessCode(code, activePhone)) {
            showApp("JD Retailer activated")
        } else {
            showWelcome()
        }
    }

    private fun verifyAccessCode(code: String, phone: String): Boolean {
        try {
            val parts = code.trim().split(".")
            if (parts.size != 2) return false

            val payloadBytes = Base64.getUrlDecoder().decode(parts[0])
            val signatureBytes = Base64.getUrlDecoder().decode(parts[1])
            val payload = String(payloadBytes, Charsets.UTF_8)
            val json = JSONObject(payload)

            val codePhone = json.optString("phone")
            val expires = json.optLong("exp", 0L)

            if (codePhone != phone.takeLast(10)) return false
            if (expires > 0L && System.currentTimeMillis() > expires) return false

            val cleanPem = PUBLIC_KEY_PEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyBytes = Base64.getDecoder().decode(cleanPem)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(payloadBytes)
            return sig.verify(signatureBytes)
        } catch (_: Exception) {
            return false
        }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        welcomePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(44, 70, 44, 44)
        }

        val title = TextView(this).apply {
            text = "JD Retailer"
            textSize = 30f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Retail Billing • Stock • Profit/Loss • A5 PDF"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 30)
        }

        val accessCard = TextView(this).apply {
            text = "Premium Access"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val price = TextView(this).apply {
            text = "₹50 / month"
            textSize = 30f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 8)
        }

        val info = TextView(this).apply {
            text = "Tap Get Access, then enter the access code provided by JD Retailer owner."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val getAccess = Button(this).apply {
            text = "Get Access / Activate"
            setOnClickListener { showAccessEntry() }
        }

        welcomePanel.addView(title)
        welcomePanel.addView(sub)
        welcomePanel.addView(accessCard)
        welcomePanel.addView(price)
        welcomePanel.addView(info)
        welcomePanel.addView(getAccess, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        accessPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(44, 70, 44, 44)
            visibility = View.GONE
        }

        val accessTitle = TextView(this).apply {
            text = "Activate JD Retailer"
            textSize = 27f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }

        val accessSub = TextView(this).apply {
            text = "Enter your mobile number and the Access Code given by the owner."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 22)
        }

        val phoneInput = EditText(this).apply {
            hint = "10-digit mobile number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        val codeInput = EditText(this).apply {
            hint = "Paste Owner Access Code"
            minLines = 4
        }

        val activate = Button(this).apply {
            text = "Verify & Unlock App"
            setOnClickListener {
                val raw = phoneInput.text.toString().filter { it.isDigit() }
                val phone = raw.takeLast(10)
                val code = codeInput.text.toString().trim()

                if (phone.length != 10) {
                    Toast.makeText(this@MainActivity, "Enter valid mobile number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (verifyAccessCode(code, phone)) {
                    activePhone = phone
                    prefs().edit()
                        .putString(KEY_PHONE, phone)
                        .putString(KEY_ACCESS_CODE, code)
                        .apply()
                    showApp("Access approved")
                } else {
                    Toast.makeText(this@MainActivity, "Access expired or invalid. Please pay ₹50 to renew and enter a new Access Code.", Toast.LENGTH_LONG).show()
                }
            }
        }

        val back = Button(this).apply {
            text = "Back"
            setOnClickListener { showWelcome() }
        }

        accessPanel.addView(accessTitle)
        accessPanel.addView(accessSub)
        accessPanel.addView(phoneInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        accessPanel.addView(codeInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        accessPanel.addView(activate, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        accessPanel.addView(back, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        webView = WebView(this).apply {
            visibility = View.GONE
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }

        root.addView(welcomePanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(accessPanel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    private fun showWelcome() {
        welcomePanel.visibility = View.VISIBLE
        accessPanel.visibility = View.GONE
        webView.visibility = View.GONE
    }

    private fun showAccessEntry() {
        welcomePanel.visibility = View.GONE
        accessPanel.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showApp(status: String) {
        val code = prefs().getString(KEY_ACCESS_CODE, "") ?: ""
        val phone = prefs().getString(KEY_PHONE, "") ?: ""

        if (!verifyAccessCode(code, phone)) {
            showWelcome()
            return
        }

        welcomePanel.visibility = View.GONE
        accessPanel.visibility = View.GONE

        if (webView.visibility != View.VISIBLE) {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.allowFileAccess = true
            webView.addJavascriptInterface(AndroidBridge(this, webView), "Android")
            webView.loadUrl("file:///android_asset/index.html")
        }

        webView.visibility = View.VISIBLE
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
    }

    class AndroidBridge(
        private val context: Context,
        private val webView: WebView
    ) {
        private fun a5Attributes(): PrintAttributes {
            return PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setResolution(PrintAttributes.Resolution("jd_a5", "JD A5", 300, 300))
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build()
        }

        @JavascriptInterface
        fun printPage() {
            webView.post {
                val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = webView.createPrintDocumentAdapter("JD_Retailer_A5_Invoice")
                pm.print(
                    "JD Retailer A5 Invoice",
                    adapter,
                    a5Attributes()
                )
            }
        }

        @JavascriptInterface
        fun sharePdfOnWhatsApp(phone: String, invoiceNo: String) {
            webView.post {
                createPdfAndShare(phone, invoiceNo)
            }
        }

        private fun createPdfAndShare(phone: String, invoiceNo: String) {
            val cleanNo = invoiceNo.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val invoiceDir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val pdfFile = File(invoiceDir, "$cleanNo.pdf")

            val adapter = webView.createPrintDocumentAdapter("JD_Retailer_$cleanNo")
            val attrs = a5Attributes()

            adapter.onLayout(
                null,
                attrs,
                null,
                object : android.print.PrintDocumentAdapter.LayoutResultCallback() {
                    override fun onLayoutFinished(
                        info: android.print.PrintDocumentInfo?,
                        changed: Boolean
                    ) {
                        try {
                            val pfd = android.os.ParcelFileDescriptor.open(
                                pdfFile,
                                android.os.ParcelFileDescriptor.MODE_CREATE or
                                    android.os.ParcelFileDescriptor.MODE_TRUNCATE or
                                    android.os.ParcelFileDescriptor.MODE_READ_WRITE
                            )
                            adapter.onWrite(
                                arrayOf(android.print.PageRange.ALL_PAGES),
                                pfd,
                                null,
                                object : android.print.PrintDocumentAdapter.WriteResultCallback() {
                                    override fun onWriteFinished(pages: Array<out android.print.PageRange>?) {
                                        try { pfd.close() } catch (_: Exception) {}
                                        sharePdfFile(pdfFile, phone)
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "PDF could not be created.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                null
            )
        }

        private fun sharePdfFile(file: File, phone: String) {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Invoice PDF")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Try WhatsApp directly first
            share.setPackage("com.whatsapp")
            try {
                context.startActivity(share)
            } catch (e: Exception) {
                // Fallback to chooser, including WhatsApp Business
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Invoice PDF")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(fallback, "Share invoice PDF")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        @JavascriptInterface
        fun openWhatsApp(phone: String, text: String) {
            val digits = phone.filter { it.isDigit() }
            if (digits.isBlank()) return
            val normalized = if (digits.length == 10) "91$digits" else digits
            val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
            val uri = Uri.parse("https://wa.me/$normalized?text=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp could not be opened.", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun shareText(text: String) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        @JavascriptInterface
        fun saveLocalBackup(json: String) {
            try {
                File(context.filesDir, BACKUP_FILE).writeText(json, Charsets.UTF_8)
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun loadLocalBackup(): String {
            return try {
                val f = File(context.filesDir, BACKUP_FILE)
                if (f.exists()) f.readText(Charsets.UTF_8) else ""
            } catch (_: Exception) {
                ""
            }
        }
    }


    override fun onResume() {
        super.onResume()
        val phone = prefs().getString(KEY_PHONE, "") ?: ""
        val code = prefs().getString(KEY_ACCESS_CODE, "") ?: ""
        if (phone.isNotBlank() && code.isNotBlank() && !verifyAccessCode(code, phone)) {
            showWelcome()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

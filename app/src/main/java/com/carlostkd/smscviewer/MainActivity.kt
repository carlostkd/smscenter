package com.carlostkd.smscviewer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    enum class Filter { BOTH, INBOX, SENT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ---------- state ---------- */
        val smsList      = mutableStateListOf<SmsRow>()
        var dropdownOpen by mutableStateOf(false)
        var showNumDlg   by mutableStateOf(false)
        var showSearchDlg by mutableStateOf(false)
        var showExportDlg by mutableStateOf(false)
        var filenameInput by mutableStateOf("sms_export.csv")
        var currentFilter by mutableStateOf(Filter.BOTH)

        /* ---------- permission ---------- */
        val reqPerm = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> if (granted) smsList.replaceWith(fetchSms(currentFilter)) }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        ) smsList.replaceWith(fetchSms(currentFilter))
        else reqPerm.launch(Manifest.permission.READ_SMS)

        /* ---------- UI ---------- */
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {          // force dark theme
                Scaffold(topBar = {
                    TopAppBar(
                        title = { Text("SMS & SMSC Viewer") },
                        actions = {
                            IconButton(onClick = { dropdownOpen = true }) {
                                Icon(Icons.Default.MoreVert, "Menu")
                            }
                            DropdownMenu(
                                expanded = dropdownOpen,
                                onDismissRequest = { dropdownOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Show all") },
                                    onClick = {
                                        dropdownOpen = false
                                        currentFilter = Filter.BOTH
                                        smsList.replaceWith(fetchSms(Filter.BOTH))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Inbox only") },
                                    onClick = {
                                        dropdownOpen = false
                                        currentFilter = Filter.INBOX
                                        smsList.replaceWith(fetchSms(Filter.INBOX))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sent only") },
                                    onClick = {
                                        dropdownOpen = false
                                        currentFilter = Filter.SENT
                                        smsList.replaceWith(fetchSms(Filter.SENT))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select count…") },
                                    onClick = { dropdownOpen = false; showNumDlg = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Search…") },
                                    onClick = { dropdownOpen = false; showSearchDlg = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export CSV…") },
                                    onClick = { dropdownOpen = false; showExportDlg = true }
                                )
                            }
                        }
                    )
                }) { inner ->
                    if (smsList.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize().padding(inner),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) { Text("No messages / no permission.") }
                    } else {
                        SmsList(
                            rows = smsList,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                        )
                    }
                }

                /* ----- dialogs ----- */
                if (showNumDlg) {
                    var input by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showNumDlg = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val n = input.toIntOrNull()
                                if (n != null && n > 0) {
                                    smsList.replaceWith(fetchSms(currentFilter, n))
                                    showNumDlg = false
                                }
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNumDlg = false }) { Text("Cancel") }
                        },
                        title = { Text("How many recent SMS?") },
                        text = {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it.filter { ch -> ch.isDigit() } },
                                placeholder = { Text("10") }
                            )
                        }
                    )
                }

                if (showSearchDlg) {
                    var query by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showSearchDlg = false },
                        confirmButton = {
                            TextButton(onClick = {
                                smsList.replaceWith(fetchSms(currentFilter).filter {
                                    it.address.contains(query, true) || it.body.contains(query, true)
                                })
                                showSearchDlg = false
                            }) { Text("Search") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showSearchDlg = false
                                smsList.replaceWith(fetchSms(currentFilter))
                            }) { Text("Cancel") }
                        },
                        title = { Text("Search number / text") },
                        text = {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("e.g. 12345 or code") }
                            )
                        }
                    )
                }

                if (showExportDlg) {
                    AlertDialog(
                        onDismissRequest = { showExportDlg = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val ok = exportCsv(smsList, filenameInput)
                                Toast.makeText(
                                    this@MainActivity,
                                    if (ok) "Saved to Downloads/$filenameInput"
                                    else "Export failed!",
                                    Toast.LENGTH_LONG
                                ).show()
                                showExportDlg = false
                            }) { Text("Export") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExportDlg = false }) { Text("Cancel") }
                        },
                        title = { Text("Export to CSV") },
                        text = {
                            OutlinedTextField(
                                value = filenameInput,
                                onValueChange = { filenameInput = it },
                                placeholder = { Text("sms_export.csv") }
                            )
                        }
                    )
                }
            }
        }
    }

    /* ---------- helpers ---------- */
    private fun exportCsv(rows: List<SmsRow>, filename: String): Boolean = try {
        val downloads = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val file = File(downloads, filename)
        FileOutputStream(file).bufferedWriter().use { w ->
            w.write("type,id,address,smsc,date,body\n")
            rows.forEach { s ->
                w.write("sms,${s.id},\"${s.address.replace("\"","\"\"")}\","
                        + "\"${s.smsc.replace("\"","\"\"")}\","
                        + "${sdf.format(Date(s.date))},"
                        + "\"${s.body.replace("\"","\"\"")}\"")
                w.appendLine()
            }
        }; true
    } catch (e: Exception) { false }

    private fun fetchSms(filter: Filter, limit: Int = 100): List<SmsRow> {
        val rows = mutableListOf<SmsRow>()
        val proj = arrayOf("_id", "address", "service_center", "date", "body")
        fun add(uri: String) {
            contentResolver.query(Uri.parse(uri), proj, null, null, "date DESC")
                ?.use { c ->
                    val idxId=c.getColumnIndex("_id"); val idxAdr=c.getColumnIndex("address")
                    val idxSc=c.getColumnIndex("service_center"); val idxDate=c.getColumnIndex("date")
                    val idxBody=c.getColumnIndex("body"); var ct=0
                    while (c.moveToNext() && ct<limit){
                        rows += SmsRow(
                            c.getLong(idxId),
                            c.getString(idxAdr) ?: "(unknown)",
                            c.getString(idxSc) ?: "(none)",
                            c.getLong(idxDate),
                            c.getString(idxBody) ?: ""
                        ); ct++
                    }
                }
        }
        if (filter!=Filter.SENT)  add("content://sms/inbox")
        if (filter!=Filter.INBOX) add("content://sms/sent")
        return rows.sortedByDescending { it.date }.take(limit)
    }

    private fun <T> MutableList<T>.replaceWith(n: List<T>) { clear(); addAll(n) }

    data class SmsRow(val id:Long,val address:String,val smsc:String,val date:Long,val body:String)

    @Composable
    private fun SmsList(rows: List<SmsRow>, modifier: Modifier = Modifier) {
        LazyColumn(modifier.padding(8.dp)) {
            items(rows) { r ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("From: ${r.address}", fontWeight = FontWeight.Bold)
                        Text(
                            text = "SMSC: ${r.smsc}",
                            color = MaterialTheme.colorScheme.error   // red color
                        )
                        Text(sdf.format(Date(r.date)))
                        Spacer(Modifier.height(4.dp))
                        Text(r.body)
                    }
                }
            }
        }
    }
}

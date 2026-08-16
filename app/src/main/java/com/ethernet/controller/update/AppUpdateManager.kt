package com.ethernet.controller.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.ethernet.controller.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean
)

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdater"
        private const val REPO_OWNER = "gmaclol"
        private const val REPO_NAME = "Ethernet-controller"
        private const val GITHUB_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun checkForUpdates(
        isManualCheck: Boolean = false,
        onUpdateFound: ((UpdateInfo) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val updateInfo = withContext(Dispatchers.IO) {
                    fetchLatestRelease()
                }

                if (updateInfo != null && updateInfo.isUpdateAvailable) {
                    if (onUpdateFound != null) {
                        onUpdateFound(updateInfo)
                    } else if (context is Activity && !context.isFinishing) {
                        showUpdateDialog(updateInfo)
                    }
                } else if (isManualCheck) {
                    Toast.makeText(
                        context,
                        "✓ L'applicazione è già aggiornata all'ultima versione!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante il controllo aggiornamenti", e)
                if (isManualCheck) {
                    Toast.makeText(
                        context,
                        "Impossibile verificare gli aggiornamenti: ${e.localizedMessage ?: "Errore di connessione"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun fetchLatestRelease(): UpdateInfo? {
        val url = URL(GITHUB_API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        if (conn.responseCode != 200) {
            Log.w(TAG, "GitHub API returned code: ${conn.responseCode}")
            return null
        }

        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(jsonStr)

        val tagName = json.optString("tag_name", "").trim()
        val cleanVersionName = tagName.removePrefix("v").removePrefix("V")
        val body = json.optString("body", "Nessuna nota di rilascio fornita.")

        var apkDownloadUrl = ""
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
        }

        if (apkDownloadUrl.isEmpty()) {
            Log.w(TAG, "Nessun file APK trovato nella release GitHub")
            return null
        }

        val currentVersionName = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }

        val isNewer = isVersionNewer(cleanVersionName, currentVersionName)

        return UpdateInfo(
            tagName = tagName,
            versionName = cleanVersionName,
            releaseNotes = body,
            downloadUrl = apkDownloadUrl,
            isUpdateAvailable = isNewer
        )
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            return remote != current
        }
        return false
    }

    fun showUpdateDialog(updateInfo: UpdateInfo) {
        if (context !is Activity || context.isFinishing) return

        val message = "È disponibile la nuova versione ${updateInfo.tagName}!\n\n" +
                "Novità:\n${updateInfo.releaseNotes}"

        MaterialAlertDialogBuilder(context)
            .setTitle("Aggiornamento Disponibile")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("Scarica e Installa") { _, _ ->
                startDownloadAndInstall(updateInfo)
            }
            .setNegativeButton("Più tardi", null)
            .show()
    }

    private fun startDownloadAndInstall(updateInfo: UpdateInfo) {
        if (context !is Activity) return

        // Check unknown sources permission on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Permesso di Installazione")
                    .setMessage("Per installare l'aggiornamento automatico è necessario autorizzare l'installazione di app per Ethernet Controller.")
                    .setPositiveButton("Autorizza") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                    .setNegativeButton("Annulla", null)
                    .show()
                return
            }
        }

        // Show progress dialog
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null, false)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tv_download_status)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.pb_download)

        val progressDialog = MaterialAlertDialogBuilder(context)
            .setTitle("Download Aggiornamento")
            .setView(dialogView)
            .setCancelable(false)
            .create()

        progressDialog.show()

        scope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(updateInfo.downloadUrl) { progress ->
                        scope.launch(Dispatchers.Main) {
                            progressBar.isIndeterminate = progress < 0
                            if (progress >= 0) {
                                progressBar.progress = progress
                                tvStatus.text = "Download in corso: $progress%"
                            } else {
                                tvStatus.text = "Download in corso..."
                            }
                        }
                    }
                }

                progressDialog.dismiss()

                if (apkFile != null && apkFile.exists()) {
                    installApk(apkFile)
                } else {
                    Toast.makeText(context, "Errore: file APK non valido", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e(TAG, "Download fallito", e)
                Toast.makeText(context, "Download fallito: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadApk(downloadUrl: String, onProgress: (Int) -> Unit): File? {
        val url = URL(downloadUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.connect()

        if (conn.responseCode != 200) {
            return null
        }

        val totalLength = conn.contentLength
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "update.apk")

        conn.inputStream.use { input ->
            FileOutputStream(apkFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloaded = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    if (totalLength > 0) {
                        val progress = ((downloaded * 100) / totalLength).toInt()
                        onProgress(progress)
                    } else {
                        onProgress(-1)
                    }
                }
                output.flush()
            }
        }

        return apkFile
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(installIntent)
    }
}

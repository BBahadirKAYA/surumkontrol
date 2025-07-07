package com.bahadirkaya.surumkontrol

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bahadirkaya.surumkontrol.ui.theme.SurumKontrolTheme
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import com.bahadirkaya.surumkontrol.BuildConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SurumKontrolTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SorguSonucuEkrani()
                }
            }
        }
    }
}

@Composable
fun SorguSonucuEkrani() {
    val context = LocalContext.current
    var versionCodeFromServer by remember { mutableStateOf(-1) }
    var versionName by remember { mutableStateOf("") }
    var mesaj by remember { mutableStateOf("") }
    var updateAvailable by remember { mutableStateOf(false) }
    var apkUrl by remember { mutableStateOf("") }
    var webAppUrl by remember { mutableStateOf("") }
    var puantajListesi by remember { mutableStateOf(listOf<JSONObject>()) }

    LaunchedEffect(Unit) {
        val url =
            "https://script.google.com/macros/s/AKfycbxSNL58f0LSeiCPJ-5bo5Ror9vgtteTqdw_lT2BZQFCuCMxRwOG4W0L0GWJGpAb3OPKew/exec"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mesaj = "Hata: ${e.message}"
            }

            override fun onResponse(call: Call, response: Response) {
                val responseString = response.body?.string() ?: ""
                try {
                    val json = JSONObject(responseString)
                    val dataArray = json.getJSONArray("data")
                    val item = dataArray.getJSONObject(0)

                    versionCodeFromServer = item.getInt("versionCode")
                    versionName = item.getString("versionName")
                    mesaj = item.getString("mesaj")
                    apkUrl = item.getString("apkUrl")
                    webAppUrl = item.getString("webAppUrl")

                    val currentVersion = BuildConfig.VERSION_CODE
                    updateAvailable = versionCodeFromServer > currentVersion

                    if (!updateAvailable && webAppUrl.isNotBlank()) {
                        puantajVerisiniGetir(webAppUrl) { gelenListe ->
                            puantajListesi = gelenListe
                        }
                    }
                } catch (e: Exception) {
                    mesaj = "JSON ayrıştırma hatası: ${e.message}"
                }
            }
        })
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Uygulama Sürüm: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
        Text("Sunucu Sürüm: $versionName", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Mesaj: $mesaj", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(12.dp))

        if (updateAvailable) {
            Text("🔔 Yeni güncelleme mevcut!", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = {
                indirVeYukle(context, apkUrl)
            }) {
                Text("Güncellemeyi İndir")
            }
        } else {
            Text("✅ Uygulama güncel.", color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(12.dp))
            Text("📝 Puantaj Listesi:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                itemsIndexed(puantajListesi) { index, kayit ->
                    val personelId = kayit.optInt("personelID")
                    val tarih = kayit.optString("Tarih").take(10)
                    val durum = kayit.optString("DurumKodu")

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("👤 Personel ID: $personelId")
                            Text("📅 Tarih: $tarih")
                            Text("⏱️ Durum: $durum")
                        }
                    }
                }
            }
        }
    }
}

fun indirVeYukle(context: Context, apkUrl: String) {
    val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
        setTitle("Yeni güncelleme indiriliyor")
        setDescription("Lütfen bekleyin...")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "surumkontrol.apk")
        setMimeType("application/vnd.android.package-archive")
    }

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

fun puantajVerisiniGetir(url: String, onResult: (List<JSONObject>) -> Unit) {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onResult(emptyList())
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val json = response.body?.string() ?: "[]"
                val jsonArray = JSONArray(json)
                val liste = mutableListOf<JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    liste.add(jsonArray.getJSONObject(i))
                }
                onResult(liste)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    })
}

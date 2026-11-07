package com.example.poikatsu

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var textBalance: TextView
    private lateinit var textEnergy: TextView
    private lateinit var btnCollectEnergy: Button
    private lateinit var btnWatchVideoAd: Button
    private lateinit var btnHighRewardAd: Button

    private val serverBaseUrl = "http://10.0.2.2:5000"
    private val userId = "test_user"
    
    // 放置で溜まる仮想エネルギー（ゲーム要素）
    private var currentEnergy = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textBalance = findViewById(R.id.textBalance)
        textEnergy = findViewById(R.id.textEnergy)
        btnCollectEnergy = findViewById(R.id.btnCollectEnergy)
        btnWatchVideoAd = findViewById(R.id.btnWatchVideoAd)
        btnHighRewardAd = findViewById(R.id.btnHighRewardAd)

        // 起動時に残高取得
        fetchBalance()

        // 放置エネルギー回収ボタン
        btnCollectEnergy.setOnClickListener {
            currentEnergy += 10
            textEnergy.text = "溜まったエネルギー: $currentEnergy ⚡"
            Log.d("Poikatsu", "LOG: [放置要素] エネルギー回収！現在 $currentEnergy ⚡")
        }

        // 通常の動画広告（動的単価: 2.0円）
        btnWatchVideoAd.setOnClickListener {
            sendDynamicRewardRequest(adType = "reward_video", dynamicPayout = 2.0)
        }

        // 高額オファー達成（動的単価: 50.0円）
        btnHighRewardAd.setOnClickListener {
            sendDynamicRewardRequest(adType = "offer_wall", dynamicPayout = 50.0)
        }
    }

    private fun fetchBalance() {
        thread {
            try {
                val url = URL("$serverBaseUrl/api/user/$userId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val balance = json.getDouble("balance")

                    runOnUiThread {
                        textBalance.text = String.format("%.2f 円", balance)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Poikatsu", "LOG: 通信エラー - ${e.message}")
            }
        }
    }

    private fun sendDynamicRewardRequest(adType: String, dynamicPayout: Double) {
        thread {
            try {
                val url = URL("$serverBaseUrl/api/ad/dynamic-reward")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true

                val jsonInput = JSONObject().apply {
                    put("user_id", userId)
                    put("ad_type", adType)
                    put("dynamic_payout", dynamicPayout)
                }

                val os = OutputStreamWriter(conn.outputStream, "UTF-8")
                os.write(jsonInput.toString())
                os.flush()
                os.close()

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    val userReward = json.getDouble("user_reward")
                    val newBalance = json.getDouble("new_balance")

                    Log.d("Poikatsu", "LOG: [報酬確定] 還元=$userReward 円 | 新残高=$newBalance 円")

                    runOnUiThread {
                        textBalance.text = String.format("%.2f 円", newBalance)
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Poikatsu", "LOG: 通信例外 - ${e.message}")
            }
        }
    }
}

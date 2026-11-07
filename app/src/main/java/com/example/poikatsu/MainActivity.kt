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
    private lateinit var btnWatchVideoAd: Button
    private lateinit var btnHighRewardAd: Button

    // ローカルFlaskサーバーのURL (Androidエミュレータからは 10.0.2.2)
    private val serverBaseUrl = "http://10.0.2.2:5000"
    private val userId = "test_user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textBalance = findViewById(R.id.textBalance)
        btnWatchVideoAd = findViewById(R.id.btnWatchVideoAd)
        btnHighRewardAd = findViewById(R.id.btnHighRewardAd)

        // 起動時に現在の残高を取得
        fetchBalance()

        // 通常の動画広告ボタン (動的単価: 2.0円)
        btnWatchVideoAd.setOnClickListener {
            sendDynamicRewardRequest(adType = "reward_video", dynamicPayout = 2.0)
        }

        // 高額オファーウォール達成ボタン (動的単価: 50.0円)
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
                        Log.d("Poikatsu", "LOG: [UI更新] 残高読込成功 - $balance 円")
                    }
                } else {
                    Log.e("Poikatsu", "LOG: 残高取得エラー - Code: ${conn.responseCode}")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Poikatsu", "LOG: 通信例外発生 - ${e.message}")
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

                // 動的単価とユーザーIDをJSONで送信
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
                    val operatorFee = json.getDouble("operator_fee")
                    val newBalance = json.getDouble("new_balance")

                    Log.d(
                        "Poikatsu",
                        "LOG: [報酬確定] 種別=$adType | 単価=$dynamicPayout | 還元=$userReward | 手数料=$operatorFee | 新残高=$newBalance"
                    )

                    runOnUiThread {
                        textBalance.text = String.format("%.2f 円", newBalance)
                    }
                } else {
                    Log.e("Poikatsu", "LOG: 動的報酬リクエストエラー - Code: ${conn.responseCode}")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Poikatsu", "LOG: 通信例外発生 - ${e.message}")
            }
        }
    }
}
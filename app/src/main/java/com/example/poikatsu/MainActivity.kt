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
    private lateinit var textGameStatus: TextView
    private lateinit var btnGameAction: Button
    private lateinit var btnWatchVideoAd: Button
    private lateinit var btnHighRewardAd: Button

    private val serverBaseUrl = "http://10.0.2.2:5000"
    private val userId = "test_user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textBalance = findViewById(R.id.textBalance)
        textGameStatus = findViewById(R.id.textGameStatus)
        btnGameAction = findViewById(R.id.btnGameAction)
        btnWatchVideoAd = findViewById(R.id.btnWatchVideoAd)
        btnHighRewardAd = findViewById(R.id.btnHighRewardAd)

        // 起動時に残高およびゲーム情報を一括取得
        fetchUserInfo()

        // 金銭リスクゼロのゲームアクション
        btnGameAction.setOnClickListener {
            sendGameActionRequest(expGained = 25)
        }

        // 動画広告 99%還元リクエスト (動的単価: 2.0円)
        btnWatchVideoAd.setOnClickListener {
            sendDynamicRewardRequest(adType = "reward_video", dynamicPayout = 2.0)
        }

        // 高額オファー 99%還元リクエスト (動的単価: 50.0円)
        btnHighRewardAd.setOnClickListener {
            sendDynamicRewardRequest(adType = "offer_wall", dynamicPayout = 50.0)
        }
    }

    private fun fetchUserInfo() {
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
                    val level = json.getInt("game_level")
                    val exp = json.getInt("game_exp")

                    runOnUiThread {
                        textBalance.text = String.format("%.2f 円", balance)
                        textGameStatus.text = "🎮 冒険者 Level: $level (EXP: $exp/100)"
                        Log.d("Poikatsu", "LOG: [情報更新] 残高=$balance 円 | Lv=$level | EXP=$exp")
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Poikatsu", "LOG: 通信例外発生 - ${e.message}")
            }
        }
    }

    private fun sendGameActionRequest(expGained: Int) {
        thread {
            try {
                val url = URL("$serverBaseUrl/api/game/action")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true

                val jsonInput = JSONObject().apply {
                    put("user_id", userId)
                    put("exp_gained", expGained)
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
                    val level = json.getInt("game_level")
                    val exp = json.getInt("game_exp")

                    Log.d("Poikatsu", "LOG: [ゲーム更新] 新Lv=$level | 新EXP=$exp")

                    runOnUiThread {
                        textGameStatus.text = "🎮 冒険者 Level: $level (EXP: $exp/100)"
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Poikatsu", "LOG: ゲーム通信例外 - ${e.message}")
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
                Log.e("Poikatsu", "LOG: 報酬通信例外 - ${e.message}")
            }
        }
    }
}
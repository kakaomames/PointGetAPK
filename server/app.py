import os
import sqlite3
from flask import Flask, jsonify, request

app = Flask(__name__)
DB_NAME = "database.db"

# 運営手数料の割合 (固定1%)
OPERATOR_FEE_RATE = 0.01


def get_db_connection():
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    """データベースとテーブルの初期化（完全透明な監査ログ付き）"""
    conn = get_db_connection()
    cursor = conn.cursor()

    # ユーザー残高テーブル
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            balance REAL DEFAULT 0.0
        )
    """
    )

    # 動的広告報酬の監査ログテーブル
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS dynamic_reward_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT,
            ad_type TEXT,
            dynamic_payout REAL,
            user_reward REAL,
            operator_fee REAL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """
    )

    # テスト用初期ユーザーの作成 (ユーザーID: test_user)
    cursor.execute(
        "INSERT OR IGNORE INTO users (id, balance) VALUES (?, ?)",
        ("test_user", 0.0),
    )

    conn.commit()
    conn.close()
    print("LOG: [系統初期化] 99%還元ポイ活サーバーのデータベース構築完了！")


@app.route("/api/user/<user_id>", methods=["GET"])
def get_user_balance(user_id):
    """現在の残高を取得するAPI"""
    conn = get_db_connection()
    user = conn.execute(
        "SELECT * FROM users WHERE id = ?", (user_id,)
    ).fetchone()
    conn.close()

    if user is None:
        print(f"LOG: [エラー] ユーザーが見つかりません - user_id={user_id}")
        return jsonify({"error": "User not found"}), 404

    current_balance = user["balance"]
    print(f"LOG: [残高照会] user_id={user_id}, balance={current_balance}円")
    return jsonify({"user_id": user_id, "balance": current_balance}), 200


@app.route("/api/ad/dynamic-reward", methods=["POST"])
def process_dynamic_reward():
    """広告の動的単価を受け取り、1%手数料と99%還元額をリアルタイム計算して加算するAPI"""
    data = request.get_json()

    if not data or "user_id" not in data or "dynamic_payout" not in data:
        print("LOG: [エラー] 不正なJSONパラメータが送信されました")
        return jsonify({"error": "Invalid parameters"}), 400

    user_id = data["user_id"]
    ad_type = data.get(
        "ad_type", "reward_video"
    )  # 広告の種類 (例: reward_video, offer_wall)
    dynamic_payout = float(data["dynamic_payout"])  # 動的広告単価

    # 1% の手数料と 99% の還元額を計算
    operator_fee = round(dynamic_payout * OPERATOR_FEE_RATE, 4)
    user_reward = round(dynamic_payout - operator_fee, 4)

    conn = get_db_connection()
    cursor = conn.cursor()

    # ユーザーの存在確認
    user = cursor.execute(
        "SELECT * FROM users WHERE id = ?", (user_id,)
    ).fetchone()
    if user is None:
        conn.close()
        print(f"LOG: [エラー] 対象ユーザーが存在しません - user_id={user_id}")
        return jsonify({"error": "User not found"}), 404

    # 残高の更新
    new_balance = round(user["balance"] + user_reward, 4)
    cursor.execute(
        "UPDATE users SET balance = ? WHERE id = ?", (new_balance, user_id)
    )

    # 監査ログの書き込み（透明性の証明）
    cursor.execute(
        """
        INSERT INTO dynamic_reward_logs (user_id, ad_type, dynamic_payout, user_reward, operator_fee)
        VALUES (?, ?, ?, ?, ?)
    """,
        (user_id, ad_type, dynamic_payout, user_reward, operator_fee),
    )

    conn.commit()
    conn.close()

    # 値が変わったのでログにしっかり出力
    print(
        f"LOG: [報酬確定] 広告種別={ad_type} | リアルタイム単価={dynamic_payout}円 | ユーザー還元({user_reward}円) + 運営手数料({operator_fee}円) | 新残高={new_balance}円"
    )

    return (
        jsonify(
            {
                "status": "success",
                "user_id": user_id,
                "ad_type": ad_type,
                "dynamic_payout": dynamic_payout,
                "user_reward": user_reward,
                "operator_fee": operator_fee,
                "new_balance": new_balance,
            }
        ),
        200,
    )


if __name__ == "__main__":
    init_db()
    # ローカル開発・検証用サーバー起動 (Port: 5000)
    app.run(host="0.0.0.0", port=5000, debug=True)
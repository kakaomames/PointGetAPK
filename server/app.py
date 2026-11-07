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
    """データベースとテーブルの初期化（99%還元ポイ活 ＋ 無課金ゲームデータ）"""
    conn = get_db_connection()
    cursor = conn.cursor()

    # ユーザー残高・ゲームステータステーブル
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            balance REAL DEFAULT 0.0,
            game_level INTEGER DEFAULT 1,
            game_exp INTEGER DEFAULT 0
        )
    """
    )

    # 動的広告報酬の監査ログテーブル（99%還元の証明用）
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

    # 初期テストユーザーの作成
    cursor.execute(
        "INSERT OR IGNORE INTO users (id, balance, game_level, game_exp) VALUES (?, ?, ?, ?)",
        ("test_user", 0.0, 1, 0),
    )

    conn.commit()
    conn.close()
    print(
        "LOG: [系統初期化] 99%還元ポイ活 ＆ 金銭非連動ゲームデータ統合DBの構築完了！"
    )


@app.route("/api/user/<user_id>", methods=["GET"])
def get_user_info(user_id):
    """ユーザーの残高およびゲーム進行状況を取得するAPI"""
    conn = get_db_connection()
    user = conn.execute(
        "SELECT * FROM users WHERE id = ?", (user_id,)
    ).fetchone()
    conn.close()

    if user is None:
        print(f"LOG: [エラー] ユーザーが見つかりません - user_id={user_id}")
        return jsonify({"error": "User not found"}), 404

    current_balance = user["balance"]
    game_level = user["game_level"]
    game_exp = user["game_exp"]

    print(
        f"LOG: [情報照会] user_id={user_id} | balance={current_balance}円 | level={game_level} | exp={game_exp}"
    )
    return (
        jsonify(
            {
                "user_id": user_id,
                "balance": current_balance,
                "game_level": game_level,
                "game_exp": game_exp,
            }
        ),
        200,
    )


@app.route("/api/ad/dynamic-reward", methods=["POST"])
def process_dynamic_reward():
    """広告の動的単価を受け取り、1%手数料と99%還元額をリアルタイム計算して残高を更新するAPI"""
    data = request.get_json()

    if not data or "user_id" not in data or "dynamic_payout" not in data:
        print("LOG: [エラー] 不正なJSONパラメータが送信されました")
        return jsonify({"error": "Invalid parameters"}), 400

    user_id = data["user_id"]
    ad_type = data.get("ad_type", "reward_video")
    dynamic_payout = float(data["dynamic_payout"])

    # 1%の手数料と99%の還元額を計算
    operator_fee = round(dynamic_payout * OPERATOR_FEE_RATE, 4)
    user_reward = round(dynamic_payout - operator_fee, 4)

    conn = get_db_connection()
    cursor = conn.cursor()

    user = cursor.execute(
        "SELECT * FROM users WHERE id = ?", (user_id,)
    ).fetchone()
    if user is None:
        conn.close()
        print(f"LOG: [エラー] 対象ユーザーが存在しません - user_id={user_id}")
        return jsonify({"error": "User not found"}), 404

    new_balance = round(user["balance"] + user_reward, 4)
    cursor.execute(
        "UPDATE users SET balance = ? WHERE id = ?", (new_balance, user_id)
    )

    cursor.execute(
        """
        INSERT INTO dynamic_reward_logs (user_id, ad_type, dynamic_payout, user_reward, operator_fee)
        VALUES (?, ?, ?, ?, ?)
    """,
        (user_id, ad_type, dynamic_payout, user_reward, operator_fee),
    )

    conn.commit()
    conn.close()

    print(
        f"LOG: [報酬確定] 種別={ad_type} | リアルタイム単価={dynamic_payout}円 | 還元={user_reward}円 | 手数料={operator_fee}円 | 新残高={new_balance}円"
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


@app.route("/api/game/action", methods=["POST"])
def process_game_action():
    """お金・ポイントが一切絡まない純粋なゲームアクション（レベルアップ/経験値）処理API"""
    data = request.get_json()
    if not data or "user_id" not in data or "exp_gained" not in data:
        return jsonify({"error": "Invalid parameters"}), 400

    user_id = data["user_id"]
    exp_gained = int(data["exp_gained"])

    conn = get_db_connection()
    cursor = conn.cursor()

    user = cursor.execute(
        "SELECT * FROM users WHERE id = ?", (user_id,)
    ).fetchone()
    if user is None:
        conn.close()
        return jsonify({"error": "User not found"}), 404

    current_exp = user["game_exp"] + exp_gained
    current_level = user["game_level"]

    # 100EXPごとにレベルアップ（ゲーム要素のみ・金銭還元への影響ゼロ）
    if current_exp >= 100:
        current_level += current_exp // 100
        current_exp = current_exp % 100

    cursor.execute(
        "UPDATE users SET game_level = ?, game_exp = ? WHERE id = ?",
        (current_level, current_exp, user_id),
    )
    conn.commit()
    conn.close()

    print(
        f"LOG: [ゲーム進行] user_id={user_id} | 獲得EXP=+{exp_gained} | 新レベル={current_level} | 現在EXP={current_exp}"
    )

    return (
        jsonify(
            {
                "status": "success",
                "game_level": current_level,
                "game_exp": current_exp,
            }
        ),
        200,
    )


if __name__ == "__main__":
    init_db()
    app.run(host="0.0.0.0", port=5000, debug=True)
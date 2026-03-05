import pandas as pd
import numpy as np
import joblib
from collections import Counter

# === 載入模型與前處理工具 ===
rf = joblib.load("rf_model.pkl")
selector = joblib.load("feature_selector.pkl")
scaler_wifi = joblib.load("scaler_wifi.pkl")
scaler_light = joblib.load("scaler_light.pkl")
wifi_columns = joblib.load("wifi_columns.pkl")

print("模型與工具載入完成 ✅")

# === 讀取原始數據，並轉換格式 ===
raw_df = pd.read_csv('20241219_3F_02.csv')

# ========== WiFi part ==========
wifi_part = raw_df[['Timestamp', 'BSSID', 'RSSI']]
wifi_part = wifi_part.pivot_table(index='Timestamp', columns='BSSID', values='RSSI').reset_index()
wifi_part['Floor ID'] = 3

# ========== Light / Heading / Coordinate part ==========
light_part = raw_df[['Timestamp', 'Light Intensity', 'Heading', 'Coordinate']].copy()

# 👉 合併相同 Timestamp 的光訊號
light_part_agg = light_part.groupby('Timestamp', as_index=False).agg({
    'Light Intensity': 'mean',   # 光訊號取平均
    'Heading': 'mean',           # Heading 取平均
    'Coordinate': 'first'        # Coordinate 取第一個
})
light_part_agg['Floor ID'] = 3

print("✅ WiFi 與 Light DataFrame 已生成")

# === 直接指定 df, df2 ===
df = wifi_part.copy()
df2 = light_part_agg.copy()   # ✅ 使用合併後的 Light Data

# 與 train.py 一樣的處理
df = df.drop(['Timestamp', 'Floor ID'], axis=1)
df = df.fillna(df.mean())

wifi_features_new = df.reindex(columns=wifi_columns, fill_value=-100) # 只保留訓練時的 BSSID 欄位，缺的補 0
light_feature_new = df2[['Light Intensity']]
df_gndtruth_new = df2[['Coordinate']]   # 真實座標（若要計算 MDE）

X_wifi_new = wifi_features_new.values
X_light_new = light_feature_new.values

# === 前處理 ===
X_wifi_new_scaled = scaler_wifi.transform(X_wifi_new)
X_light_new_scaled = scaler_light.transform(X_light_new)

X_wifi_new_selected = selector.transform(X_wifi_new_scaled)
X_new_combined = np.hstack([X_wifi_new_selected, X_light_new_scaled])

# === 推論 ===
y_new_pred = rf.predict(X_new_combined)
print("新數據逐筆預測結果:", y_new_pred)

# === 多數決投票 (含平票處理) ===
counter = Counter(y_new_pred)
max_count = max(counter.values())
candidates = [label for label, count in counter.items() if count == max_count]

if len(candidates) > 1:
    final_pred = min(candidates)  # ⚖️ 平票時取最小的 label
    print(f"⚠️ 出現平票，候選 {candidates}，選擇 {final_pred}")
else:
    final_pred = candidates[0]

print("📌 多數決最終預測結果:", final_pred)

# === (選擇性) 計算 MDE ===
def convert_to_xy(index):
    if 1 <= index <= 11:
        return (0, index)
    elif 12 <= index <= 55:
        return (index - 11, 12)
    elif 56 <= index <= 67:
        return (44, 12 - (index - 55))
    elif 68 <= index <= 110:
        return (44 - (index - 67), 0)
    else:
        raise ValueError("Index out of range")

def mean_distance_error(true_labels, pred_labels):
    true_coords = np.array([convert_to_xy(label) for label in true_labels])
    pred_coords = np.array([convert_to_xy(label) for label in pred_labels])
    distances = np.sqrt(np.sum((true_coords - pred_coords) ** 2, axis=1))
    return np.mean(distances)

if 'Coordinate' in df2.columns:
    y_true = df_gndtruth_new.values.ravel()

    # 原始逐筆 MDE
    mde_each = mean_distance_error(y_true, y_new_pred)
    print(f"📏 逐筆預測 Mean Distance Error (MDE): {mde_each*300:.2f} cm")

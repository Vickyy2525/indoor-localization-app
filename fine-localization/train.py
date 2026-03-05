import pandas as pd
import numpy as np
import joblib
from sklearn.model_selection import train_test_split, GridSearchCV
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_selection import SelectKBest, chi2
from sklearn.preprocessing import MinMaxScaler
from sklearn.metrics import accuracy_score

# === Load datasets ===
df = pd.read_csv('wifiBSSID_3F_1219.csv')
df2 = pd.read_csv('lightBSSID_3F_1219.csv')

# === Handle missing values and select features ===
df = df.drop(['Timestamp', 'Floor ID'], axis=1)
df = df.fillna(df.mean())
light_feature = df2[['Light Intensity']]
df_gndtruth = df2[['Coordinate']]

wifi_features = df.copy()
X_wifi = wifi_features.values
X_light = light_feature.values
y = df_gndtruth.values.ravel()

# === Split train/test ===
X_train_wifi, X_test_wifi, X_train_light, X_test_light, y_train, y_test = train_test_split(
    X_wifi, X_light, y, test_size=0.3, random_state=42
)

# === Scale ===
scaler_wifi = MinMaxScaler()
X_train_wifi_scaled = scaler_wifi.fit_transform(X_train_wifi)
X_test_wifi_scaled = scaler_wifi.transform(X_test_wifi)

scaler_light = MinMaxScaler()
X_train_light_scaled = scaler_light.fit_transform(X_train_light)
X_test_light_scaled = scaler_light.transform(X_test_light)

# === Feature selection (挑選最佳 k) ===
best_score = 0
best_k = 0
best_selector = None
best_model = None

for k in range(1, 50 + 1, 5):
    selector = SelectKBest(chi2, k=k)
    X_train_wifi_selected = selector.fit_transform(X_train_wifi_scaled, y_train)
    X_test_wifi_selected = selector.transform(X_test_wifi_scaled)

    X_train_combined = np.hstack([X_train_wifi_selected, X_train_light_scaled])
    X_test_combined = np.hstack([X_test_wifi_selected, X_test_light_scaled])

    rf = RandomForestClassifier(n_estimators=100, random_state=42)
    rf.fit(X_train_combined, y_train)
    y_pred = rf.predict(X_test_combined)

    acc = accuracy_score(y_test, y_pred)
    print(f"k={k}, Accuracy={acc:.3f}")

    if acc > best_score:
        best_score = acc
        best_k = k
        best_selector = selector
        best_model = rf

print(f"最佳 k={best_k}, Accuracy={best_score:.3f}")

# === Save model & preprocessors ===
joblib.dump(best_model, "rf_model.pkl")
joblib.dump(best_selector, "feature_selector.pkl")
joblib.dump(scaler_wifi, "scaler_wifi.pkl")
joblib.dump(scaler_light, "scaler_light.pkl")

# === Save training WiFi columns ===
wifi_columns = wifi_features.columns.tolist()
joblib.dump(wifi_columns, "wifi_columns.pkl")

print("模型與前處理工具已保存！")

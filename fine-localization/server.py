import traceback
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
import pandas as pd
import joblib
import numpy as np
from collections import Counter
# server.py
from pydantic import BaseModel
from openai import OpenAI
import os
import time
from typing import List
import math


app = FastAPI()

rf = joblib.load("rf_model.pkl")
selector = joblib.load("feature_selector.pkl")
scaler_wifi = joblib.load("scaler_wifi.pkl")
scaler_light = joblib.load("scaler_light.pkl")
wifi_columns = joblib.load("wifi_columns.pkl")

# === (選擇性) 計算 MDE ===
def convert_to_xy(index):
    if 1 <= index <= 11:
        return (int(0), int(index))
    elif 12 <= index <= 55:
        return (int(index - 11), (12))
    elif 56 <= index <= 67:
        return (int(44), (12 - (index - 55)))
    elif 68 <= index <= 110:
        return (int(44 - (index - 67)), int(0))
    else:
        raise ValueError("Index out of range")


@app.post("/upload_csv")
async def upload_csv(file: UploadFile = File(...)):
    if file.content_type != "text/csv":
        raise HTTPException(status_code=400, detail="File must be a CSV")
    try:
        contents = await file.read()
        from io import StringIO
        s = str(contents, 'utf-8')
        raw_df = pd.read_csv(StringIO(s))

                
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

        wifi_features_new = df.reindex(columns=wifi_columns, fill_value=-100) # 只保留訓練時的 BSSID 欄位，缺的補 -100
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

        position = convert_to_xy(final_pred)

        return JSONResponse(content={"position": position})

    except Exception as e:
        print(traceback.format_exc())
        raise HTTPException(status_code=500, detail=str(e))


# Set your OpenAI API Key as an environment variable
## setx OPENAI_API_KEY "your OpenAI_API_Key"

client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])

# 儲存對話紀錄
conversation = []
response_times = []

# FastAPI 用資料模型
class Query(BaseModel):
    user_input: str

class QueryPoint(BaseModel):
    point: List[int]  # point 是一個整數列表
    userinput: str  # user_input 是字符串

# location_dict
location_dict = {
    (0, 0): "我在 Uniqlo 和 MLB Korea 附近",
    (3, 0): "我在 CACO 和 MLB Korea 之間",
    (6, 0): "我在 MLB Korea , CACO 和adidias&Originals附近",
    (9, 0): "我在 adidias&Originals 和 voux 附近",
    (12, 0): "我在 adidias&Originals 和 VOUX 附近",
    (15, 0): "我在 adidias&Originals 和 SKECHERS 附近",
    (18, 0): "我在 SKECHERS 和 銀谷Phiten 附近",
    (21, 0): "我在 Columbia 和 FILA 附近",
    (24, 0): "我在 Cold Stone Cafe 和 Columbia 附近",
    (27, 0): "我在 Cold Stone Cafe 和 PUMA 附近",
    (30, 0): "我在 Palladium 和 Doris 行李箱 附近",
    (33, 0): "我在 Palladium 和 THE NORTH FACE 附近",
    (36, 0): "我在 PLAY HARD 和 THE NORTH FACE 附近",
    (39, 0): "我在 PLAY HARD 和 三花棉業 附近",
    (42, 0): "我在 新馬辣經典麻辣鍋 和 三花棉業 附近",
    (0, 12): "我在 Nike 和 UNDER ARMOUR 附近",
    (3, 12): "我在 Converse 和 UNDER ARMOUR 附近",
    (6, 12): "我在 Converse、UNDER ARMOUR 和 MIZUNO 附近",
    (9, 12): "我在 UNDER ARMOUR 和 MIZUNO 附近",
    (12, 12): "我在 New Balance 附近",
    (15, 12): "我在 Dickies 和 Asics 附近",
    (18, 12): "我在 Asics 附近",
    (21, 12): "我在 Timberland 和 ELLE Active 附近",
    (24, 12): "我在 Timberland 和 LAKING 附近",
    (27, 12): "我在 QUIKSILVER & ROXY、LAKING 和 HANG TEN 附近",
    (30, 12): "我在 鬼洗 附近",
    (33, 12): "我在 EDWIN 和 CLAID 克雷德 附近",
    (36, 12): "我在 Lee 和 SST&C 男女複合店 附近",
    (39, 12): "我在 Levi's 和 PING 附近",
    (42, 12): "我在 Levi's 和 Hush Puppies 服飾 附近",
    (0, 3): "我在 Uniqlo 和 CACO 附近",
    (0, 6): "我在 NIKE 和 K-SWISS 附近",
    (0, 9): "我在 NIKE 和 le coq sportif 附近",
    (43, 0): "我在 新馬辣經典麻辣鍋 和 三花棉業 附近",
    (43, 3): "我在 漢來蔬食 和 ATUNAS 歐都納 附近",
    (43, 6): "我在 漢來蔬食 和 GIORGIO VARALLI 附近",
    (43, 9): "我在 勝博殿 和 UNIVERSITY OF CAMBRIDGE 附近",
    (43, 12): "我在 Levi's、Hush Puppies 服飾 和 JINS 附近",
}

@app.post("/ask_location")
def ask_location(query: QueryPoint):
    query_point = (query.point[0], query.point[1])

    # 計算最近位置
    closest_key = min(location_dict.keys(),
                      key=lambda k: math.sqrt((k[0] - query_point[0])**2 + (k[1] - query_point[1])**2))
    closest_label = location_dict[closest_key]

    # **四張圖片（四層樓平面圖），並明確標註樓層**
    initial_question = [
        {
            "type": "text",
            # Simple
            #"text": "這是南紡購物中心 3F 的平面圖，可用來回答大多數三樓的問題",
            "text": "這是 3F 的平面圖，這張圖特別強調廁所、ATM、電梯位置的。「橘色」方框框起來的是「廁所」，分別在靠近Uniqlo, Under Armour, Levi's, PLAY HARD的地方；「紫色」方框框起來的是「ATM」，只在QUIKSILVER & ROXY和鬼洗之間；「綠色」方框框起來的是「電梯」，分別在PLAY HARD, QUIKSILVER & ROXY, Uniqlo附近。",
        },
        {
            "type": "image_url",
            "image_url": {
                #simple
                #"url": "https://upload.wikimedia.org/wikipedia/commons/5/55/0219_3f.png",
                # detail
                "url": "https://upload.wikimedia.org/wikipedia/commons/f/f4/3F_label.png",
                "detail": "high",
            },
        },

        {
            "type": "text",
            "text": "目前GAP有一件七折，三件五折的優惠；4F漢來海港城四人同行一人免費；1樓星巴克大杯以上第二杯五折；3樓Uniqlo 兩件折100元; 全館商品滿3000送300",
        },
        {
            "type": "text",
            "text": "南紡購物中心電話:06-2366-222; 營業時間：週一至週日 11:00~22:00 (部分店櫃特殊營業時間依各店櫃公告為主); 消費滿 500元，可免費停車0.5小時；消費滿1000元，可免費停車1小時；消費滿 2,000元，可免費停車2小時，依此類推"
        },
        {
            "type": "text",
            "text": closest_label,
        }

    ]

    initial_question.append({ "type": "text", "text": query.userinput})
    conversation.clear()  # 清空舊對話
    conversation.append({"role": "system", "content": "You are an AI that analyzes architectural floor plans and answers questions about them.請以繁體中文回答，內容盡量簡短扼要"})
    conversation.append({"role": "user", "content": initial_question})

    # 呼叫 OpenAI API
    start_time = time.time()
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=conversation
    )
    end_time = time.time()

    ai_response = response.choices[0].message.content
    response_time = end_time - start_time

    return {
        "closest_label": closest_label,
        "ai_response": ai_response,
        "response_time": response_time
    }
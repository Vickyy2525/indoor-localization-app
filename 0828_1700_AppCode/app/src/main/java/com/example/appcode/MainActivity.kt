package com.example.appcode

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.datacollector.DataCollector
import com.google.android.material.chip.Chip
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import java.io.File
import com.github.chrisbanes.photoview.PhotoView
import android.graphics.RectF

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var scrollView: ScrollView
    private lateinit var dialogContainer: LinearLayout
    private lateinit var dataCollectorInner: LinearLayout
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var inputLayout: LinearLayout
    private lateinit var dataCollector: DataCollector
    private var chatContainerOriginalHeight = 0

    // new ui
    private lateinit var mapImageView: ImageView
    private lateinit var developerModeSwitch: SwitchMaterial
    private lateinit var developerLogScrollView: ScrollView
    private lateinit var suggestionChip1: Chip
    private lateinit var suggestionChip2: Chip
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private lateinit var positionMarkerView: PositionMarkerView


    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )
    private val REQUEST_CODE_PERMISSIONS = 100

    // 權限檢查
    private fun requestPermissionsIfNeeded() {
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            requestPermissions(notGranted.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                addDataCollectorMessage("所有權限已授予")
            } else {
                addDataCollectorMessage("缺少必要權限，DataCollector 功能可能無法正常運作")
            }
        }
    }

    // 定時掃描 Handler
    private val schedulerHandler = Handler(Looper.getMainLooper())

    var uploadedPosition: List<Int>? = null

    // 掃描內容
    private val scanRunnable = object : Runnable {
        override fun run() {
            dataCollector.startScanning()
            schedulerHandler.postDelayed({
                dataCollector.exportToCSV()
                val csvFile = File(dataCollector.csvPath ?: "")
                if (csvFile.exists()) {
                    val content = csvFile.readText()
                    Log.d("CSV_CONTENT", "CSV內容: $content")
                } else {
                    Log.e("CSV_CONTENT", "CSV不存在")
                }

                if (dataCollector.csvPath != null) {
                    val file = File(dataCollector.csvPath)
//                    val file = File("/storage/emulated/0/Download/20241219_3F_01.csv")
                    CsvUploader.uploadCsvFile(file) { success, message, position ->
                        if (success) {
                            uploadedPosition = position
                            Log.d("UPLOAD", "上傳成功：$message, 位置為$position")
                            // 更新地圖上的座標點
                            uploadedPosition = position
                            runOnUiThread {
                                updateMarkerPosition()
                            }
                        } else {
                            Log.e("UPLOAD", "上傳失敗：$message")
                        }
                    }
                } else {
                    Log.e("CSVPATH", "csvPath 是 null，無法建立 File")
                }
            }, 6000)
            schedulerHandler.postDelayed(this, 7000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsIfNeeded()

        positionMarkerView = findViewById(R.id.positionMarkerView)
        scrollView = findViewById(R.id.chatScrollView)
        dialogContainer = findViewById(R.id.dialogContainer)
        dataCollectorInner = findViewById(R.id.dataCollectorInner)
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        inputLayout = findViewById(R.id.inputLayout)
        mapImageView = findViewById(R.id.mapImageView)
        developerModeSwitch = findViewById(R.id.developerModeSwitch)
        developerLogScrollView = findViewById(R.id.developerLogScrollView)
        suggestionChip1 = findViewById(R.id.suggestionChip1)
        suggestionChip2 = findViewById(R.id.suggestionChip2)

        chatContainerOriginalHeight = dialogContainer.height

        // 縮放限制
        val photoView = findViewById<PhotoView>(R.id.mapImageView)
        photoView.minimumScale = 1.0f
        photoView.maximumScale = 2.0f
        photoView.scale = 1.0f
        // 初始不顯示位置在圖片上
        positionMarkerView.pixelPosition = null
        // 監聽 PhotoView 拖移或縮放，用於使用者拖移或縮放圖片時，自動更新紅點位置
        photoView.setOnMatrixChangeListener {
            updateMarkerPosition()
        }

        // 監控鍵盤顯示
        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        rootLayout.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val rect = android.graphics.Rect()
                rootLayout.getWindowVisibleDisplayFrame(rect)
                val screenHeight = rootLayout.height
                val keypadHeight = screenHeight - rect.bottom

                if (keypadHeight > screenHeight * 0.15) { // 鍵盤顯示
                    adjustChatContainerHeight(screenHeight - keypadHeight - 2)
                } else {
                    adjustChatContainerHeight(chatContainerOriginalHeight)
                }
                return true
            }
        })

        // 按下傳送按鈕
        sendButton.setOnClickListener {
            val userInput = inputEditText.text.toString()

            if (userInput.isNotEmpty()) {
                addDialogMessage("你：$userInput", true)
                val placeholder = TextView(this)
                placeholder.text = "AI：思考中..."
                placeholder.setPadding(16, 8, 16, 8)
                placeholder.background = getDrawable(R.drawable.ai_bubble)

                dialogContainer.addView(placeholder)
                scrollToBottom(dialogContainer)

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.allocation(QueryPoint(uploadedPosition, userInput))
                        placeholder.text = "AI：${response.ai_response}"
                    } catch (e: Exception) {
                        placeholder.text = "AI：出現錯誤: ${e.message}"
                        Log.d("locate fail", "定位或回答失敗")
                    }
                }

                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(inputEditText.windowToken, 0)
                inputEditText.text.clear()
            }
        }

        // Suggestion chips click to fill input
        suggestionChip1.setOnClickListener {
            inputEditText.setText(suggestionChip1.text)
            inputEditText.requestFocus()
        }

        suggestionChip2.setOnClickListener {
            inputEditText.setText(suggestionChip2.text)
            inputEditText.requestFocus()
        }

        // Developer mode toggle
        developerModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                developerLogScrollView.visibility = View.VISIBLE
                scrollView.visibility = View.GONE
            } else {
                developerLogScrollView.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
            }
        }

        // Initialize DataCollector
        dataCollector = DataCollector.getInstance(this, "floor1")
        dataCollector.setCallback(object : DataCollector.Callback {
            override fun onSuccess(message: String) {
                runOnUiThread { addDataCollectorMessage("成功：$message") }
            }

            override fun onError(error: String) {
                runOnUiThread { addDataCollectorMessage("錯誤：$error") }
            }
        })

        // Initialize gyroscope for orientation
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)

        schedulerHandler.post(scanRunnable)
    }

    private fun adjustChatContainerHeight(newHeight: Int) {
        val layoutParams = dialogContainer.layoutParams
        layoutParams.height = newHeight
        dialogContainer.layoutParams = layoutParams
    }

    private fun addDataCollectorMessage(message: String) {
        val tv = TextView(this)
        tv.setPadding(8, 8, 8, 8)
        tv.textSize = 16f
        tv.setTextColor(android.graphics.Color.BLUE)
        tv.text = message
        dataCollectorInner.addView(tv)

        if (developerLogScrollView.visibility == View.VISIBLE) {
            developerLogScrollView.post { developerLogScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun addDialogMessage(message: String, isUser: Boolean = false) {
        val tv = TextView(this)
        tv.setPadding(16, 8, 16, 8)
        tv.textSize = 16f
        tv.text = message
        if (isUser) {
            tv.background = getDrawable(R.drawable.user_bubble)
            tv.gravity = android.view.Gravity.END
        } else {
            tv.background = getDrawable(R.drawable.ai_bubble)
            tv.gravity = android.view.Gravity.START
        }
        dialogContainer.addView(tv)
        scrollToBottom(dialogContainer)
    }

    private fun scrollToBottom(container: LinearLayout) {
        val parentScrollView = container.parent as ScrollView
        parentScrollView.post { parentScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onSensorChanged(event: SensorEvent?) {

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for basic implementation
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        schedulerHandler.removeCallbacks(scanRunnable)
        sensorManager.unregisterListener(this)
    }

    fun programToPixel(x: Int, y: Int): Pair<Float, Float> {
        // 已知兩點：(0,0) -> (132,420), (43,12) -> (752,202)
        // 使用線性插值公式
        // 有微調過，並沒有直接照座標映射
        val px = 132f + (752f - 132f) * (x.toFloat() / 41f)
        val py = 420f + (202f - 420f) * (y.toFloat() / 13f)
        return Pair(px * 2.5f, py * 2.8f - 100)
    }

    private fun updateMarkerPosition() {
        val photoView = mapImageView as PhotoView
        val displayRect = photoView.displayRect ?: return

        // 取得原始圖片尺寸
        val drawable = photoView.drawable ?: return
        val imageWidth = drawable.intrinsicWidth.toFloat()
        val imageHeight = drawable.intrinsicHeight.toFloat()

        uploadedPosition?.let { (progX, progY) ->
            val (px, py) = programToPixel(progX, progY)

            // 計算像素在原始圖片中的比例
            val ratioX = px / imageWidth
            val ratioY = py / imageHeight

            // 映射到當前顯示的矩形
            val screenX = displayRect.left + ratioX * displayRect.width()
            val screenY = displayRect.top + ratioY * displayRect.height()

            // 更新 marker
            positionMarkerView.pixelPosition = Pair(screenX, screenY)
            positionMarkerView.invalidate()
        }
    }


}
# 變更紀錄

## v0.2（2026-10-17）

把單檔 HTML 版「蒙地卡羅 π 估算 Dashboard」移植成 Compose Multiplatform（Desktop + WasmJs）。

- 保留上方選單的 About，About 顯示 `Version 0.2`（`gradle.properties` 的 `app.version` 與 `AppVersion.NAME` 同步為 0.2，打包版號 `packageVersion` 也讀它）。
- 主畫面改為 π 估算儀表板，配色、文字與版面對齊 HTML：
  - 標頭：π 標誌、標題、副標題、狀態膠囊（待命／執行中（脈動點）／已暫停／已完成）。
  - 左側：正方形投點畫布（格線、四分之一圓、圓內／圓外顏色、點大小、繪製上限），點累積在 `ImageBitmap` 圖層，縮放或改點大小時重繪。
  - 畫布下方結果列 `下界 ≤ 估計值 ≤ 上界，機率 99.7%`，σ = 4·√(p̂(1−p̂)/N)，並提示真實 π 是否落在區間內（✓ 是／✗ 否）。
  - 右側：總點數上限（對數 10³–10⁷）、每幀點數（對數 10–50,000）、點大小（0.5–4）、繪製上限（5,000–200,000）滑桿，預設值與 HTML 相同。
  - 亂數種子：0 = 平台亂數，>0 = mulberry32（與 HTML 位元相同，種子 42 的序列一致），從待命狀態開始時生效。
  - 開始／暫停／重設按鈕的可用狀態與 HTML 相同；鍵盤 Space 開始／暫停、R 重設（在種子輸入框中不攔截）。
  - 六張 KPI 卡、收斂走勢圖（對數 x 軸、π 虛線、±3σ 色帶）。
  - 寬度 ≤ 1000dp 改為單欄，≤ 560dp KPI 改兩欄並隱藏副標題。
- 每幀批次以 `withFrameNanos` 執行；估計、σ、PRNG 與狀態機都在 `commonMain`（`sim/Simulation.kt`），並新增 `commonTest`。
- 桌面視窗預設 1440×900。截圖用環境變數：`MONTECARLO_DEMO=1`（種子 42 自動開始，相當於 HTML 的 `?demo=1`）、`MONTECARLO_DEMO_PAUSE_AT=5000`（到 N 後自動暫停）。WasmJs 版支援網址參數 `?demo=1`。

建置需要 JDK 25（`jvmToolchain(25)`）：

```bash
./gradlew :composeApp:desktopTest :composeApp:desktopJar :composeApp:wasmJsBrowserDistribution
./gradlew :composeApp:run
```

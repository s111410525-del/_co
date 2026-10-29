# IC 設計 EDA 與 veryl2chip 虛擬 FPGA 遊玩報告

> 完成工具：**opencode**（AI coding agent）
> 日期：2026-10-29
> 參考連結：
> - [veryl2chip IDE](https://ccc-eda.github.io/veryl2chip/web/dist/index.html)
> - [nand2tetris HDL 模擬器](https://ccc-js.github.io/hack-computer/dist/hdl.html)
> - [Cornell ECE 5745 Handouts](https://www.csl.cornell.edu/courses/ece5745/handouts.html)
> - [veryl2chip GitHub](https://github.com/ccc-eda/veryl2chip/)
> - [hack-computer GitHub](https://github.com/ccc-js/hack-computer)

---

## 摘要

本報告先說明 IC 設計中 EDA（Electronic Design Automation，電子設計自動化）的角色，再以開源 EDA 工具 **veryl2chip** 走過一次「寫 RTL → 模擬 → 合成 → 佈局繞線 → 靜態時序分析 → 燒進虛擬 FPGA 板」的流程，最後在虛擬 iCEBreaker 的 USB 終端機上遊玩內建的 C 程式遊戲 **「猜數字」**，記錄遊玩過程並整理心得。報告同時對照 nand2tetris HDL 模擬器與 Cornell ECE 5745 的課程教材，說明這些工具在學習數位 IC 設計時各自扮演的角色。

本報告由 **opencode** 協助整理與撰寫。

---

## 1. IC 設計中的 EDA 是什麼？

### 1.1 從一個想法到一顆晶片

一顆現代數位 IC 動輒包含數十億個電晶體，不可能靠人工畫出每個邏輯閘與每條連線。IC 設計的典型流程如下：

1. **規格制定（Specification）**：決定晶片功能、效能、功耗與面積目標。
2. **架構設計（Architecture）**：切割成 CPU、記憶體、周邊、匯流排等區塊。
3. **RTL 設計（Register-Transfer Level）**：用 Verilog、SystemVerilog、VHDL 或 Veryl 描述電路行為。
4. **功能驗證（Verification）**：寫 testbench、跑模擬、比對波形，確認設計符合規格。
5. **邏輯合成（Synthesis）**：把 RTL 轉成邏輯閘層級的 netlist。
6. **可測試性設計（DFT）**：插入掃描鏈、BIST 等電路，方便晶片測試。
7. **實體設計（Physical Design）**：佈局（placement）、時脈樹合成（CTS）、繞線（routing）。
8. **靜態時序分析（STA）**：檢查 setup/hold time、計算最高操作頻率 Fmax。
9. **實體驗證（Signoff）**：DRC、LVS、IR-drop、功耗分析等。
10. **Tape-out 與製造**：把 GDSII 交給晶圓廠製造、封裝、測試。

### 1.2 EDA 的角色

**EDA 是把上述流程自動化的軟體工具集合。** 它讓工程師用高階語言描述電路，由工具負責化簡邏輯、對應到標準元件或 FPGA 資源、安排位置、拉線、計算延遲與功耗，最後產生可製造的檔案。沒有 EDA，現代 IC 設計幾乎不可能完成。

EDA 工具大致可分為：

| 階段 | 工作內容 | 代表性工具 |
|---|---|---|
| RTL 模擬 | 驗證電路功能、產生波形 | Verilator、ModelSim、VCS、veryl-sim |
| 邏輯合成 | RTL 轉閘級 netlist、最佳化 | Yosys、Design Compiler、Genus、veryl-eda |
| 閘級模擬 | 確認合成前後行為一致 | Verilator、ModelSim、veryl-eda gatesim |
| 佈局繞線 | 決定元件位置與連線 | nextpnr、Innovus、IC Compiler、veryl-eda P&R |
| 時序分析 | 計算路徑延遲與 Fmax | OpenSTA、PrimeTime、Tempus |
| 實體驗證 | DRC、LVS、功耗、IR-drop | KLayout、Calibre、RedHawk |
| FPGA 流程 | LUT 對應、位元流產生 | Yosys、nextpnr、IceStorm、veryl-eda |

### 1.3 FPGA 與 ASIC 的差異

- **FPGA**：晶片內部已有可程式化的 LUT、正反器與繞線開關。EDA 工具把設計對應到這些資源並產生位元流（bitstream），可以反覆燒錄、除錯成本低，適合教學與原型驗證。
- **ASIC**：針對特定功能訂製晶片，使用標準元件庫（standard cell）或全訂製電路。EDA 流程更長，需要考慮製程、寄生電容、時脈樹、功耗與良率，但效能、功耗與單位成本通常更佳。

veryl2chip 同時提供 FPGA（iCE40UP5K）與 ASIC（vlib、SKY130）兩種後端，正好可以觀察同一個設計在兩種流程下的差異。

### 1.4 為什麼 EDA 很重要

- **規模**：人工無法處理數億個閘。
- **正確性**：模擬、形式驗證與閘級比對能在投片前抓出錯誤。
- **效能與功耗**：合成與實體設計的每一個決策都會影響 Fmax、面積與能耗。
- **成本**：一次先進製程投片可能高達數百萬美元，EDA 是降低風險的關鍵。

---

## 2. 參考資料與工具介紹

### 2.1 veryl2chip IDE

veryl2chip 是一套以 Rust 與 WebAssembly 實作的開源 EDA，可在瀏覽器中直接使用，不需要安裝。它支援：

- Veryl 硬體描述語言的編輯、語法檢查與模擬。
- FPGA 流程：RTL → Logic Netlist → 技術無關最佳化 → LUT4 對應 → 佈局 → 繞線 → STA → 位元流。
- ASIC 流程：標準元件對應 → 列式佈局 → CTS 與全域繞線 → 細部繞線 → STA → 功耗與 IR-drop。
- 「全面驗證」：同一組 testbench 分別跑 RTL、通用閘、iCE40 與 ASIC 四種實作，結果必須一致。
- 虛擬 iCEBreaker：把真正的位元流解碼回電路，逐週期模擬 LED、按鈕、USB 終端機、七段顯示器、VGA 與麵包板。

這套工具把原本分散在不同商業軟體中的流程濃縮到一個網頁，非常適合理解 EDA 的全貌。原始碼與文件可在 [veryl2chip GitHub](https://github.com/ccc-eda/veryl2chip/) 取得。

### 2.2 nand2tetris HDL 模擬器

nand2tetris 的 HDL 模擬器從最底層的 NAND 閘開始，讓學習者依序建出 AND、OR、ALU、暫存器、CPU，最後執行 Hack 組合語言。它強調「從閘到電腦」的層層抽象，與 veryl2chip 的「從 RTL 到晶片」正好互補：

- nand2tetris：**由下而上**理解電腦如何組成。
- veryl2chip：**由上而下**體驗真實 EDA 流程如何把設計變成晶片。

此模擬器的原始碼與說明位於 [hack-computer GitHub](https://github.com/ccc-js/hack-computer)。

### 2.3 Cornell ECE 5745

Cornell ECE 5745「Complex Digital ASIC Design」的講義涵蓋：

- HDL 與 Verilog 撰寫規範。
- CMOS 元件、電路與全訂製設計方法。
- 自動化設計方法與 ASIC/Custom 差距。
- 封裝、電源、時脈與 I/O。
- 測試與驗證。
- 合成演算法與實體設計自動化演算法。
- ASIC 工具前後端、OpenLANE 開源流程。

這些主題正好對應 veryl2chip 中看到的合成、佈局、繞線與 STA 階段，也說明學術課程與開源 EDA 工具可以互相印證。

---

## 3. 實際操作：從 Veryl 到虛擬 FPGA

以下流程以 veryl2chip 的教學範例 **Blinky** 為主，操作順序依照 IDE 的建議：

1. **開啟範例**：在範例庫選擇「教學 → 第 1 課 Blinky」。
2. **執行測試**：按「執行測試」，testbench 以 `WIDTH=6` 的小型計數器驗證溢位與 LED 行為，結果為 PASS。
3. **全面驗證**：按「全面驗證」，同一個 testbench 分別跑 RTL、通用閘、iCE40 與 ASIC 標準元件四種實作，結果一致。
4. **觀察合成階段**：
   - RTL→Logic：解析 Veryl、展開參數、攤平階層。
   - Logic Netlist：計數器變成 24 個 DFF 與加一電路。
   - 技術無關最佳化：常數傳播、移除重複與未使用邏輯。
   - LUT4 對應：約 33 個 SB_LUT4 與 24 個 SB_DFFR。
5. **佈局與繞線**：選擇 Placement 觀看模擬退火如何把元件聚在一起；Routing 觀看實際連線；Timing 觀看關鍵路徑。
6. **時序結果**：教學範例的關鍵路徑約 29.68 ns，Fmax 約 33.7 MHz，而板子時脈為 12 MHz，因此時序有餘裕。
7. **燒進虛擬板**：按「虛擬板」，LED 以二進位計數，按住 BTN_N 可重置。
8. **下載位元流**：可下載 `.bin`，用 `iceprog` 燒進實體 iCEBreaker。

這個過程讓我第一次清楚看到「一行 RTL」如何一步步變成「LUT、正反器、實際位置與導線」，也看到 STA 為什麼是投片前不可省略的檢查。

---

## 4. 遊玩紀錄：C 程式「猜數字」

### 4.1 遊戲來源與規則

veryl2chip 的範例庫中有一組「C 程式」，這些程式跑在工具內建、用 Veryl 寫成的 RISC-V SoC（RvSoc）上。其中 **「C：猜數字」** 是一個在 USB 終端機上進行的小遊戲：

- 程式先隨機產生 1 到 100 的整數。
- 玩家每次輸入一個數字。
- 程式回答「too small」或「too big」。
- 猜中時顯示總共猜了幾次。
- 每次猜測的次數會以二進位顯示在 PMOD1A 的 8 顆 LED 上。

遊戲使用 `read_line`、`atoi` 與 `rand`，並以玩家按下 Enter 所花的時間作為亂數種子，因此每一局的答案都不同。

### 4.2 操作方式

1. 在範例庫選擇「C 程式 → C：猜數字」。
2. 按「虛擬板」，等待 C 程式被編譯並放入 block RAM。
3. 勾選 USB 終端機，鮑率設為 115200。
4. 終端機出現提示後按 Enter 開始，輸入數字並按 Enter。
5. 觀察終端機訊息與 LED 上的嘗試次數。

由於只修改 C 程式時不需要重新合成與佈局繞線，工具只會把新的程式寫進 block RAM，這點讓我直接體會到「硬體固定、軟體可更換」的 SoC 設計思維。

### 4.3 遊玩紀錄（兩局範例）

**第一局**：一開始沒有策略，只憑直覺往中間猜，但沒有嚴格維護上下界。

```text
Guess my number (1-100). Press Enter to start.

I have a number.
Your guess: 50
50 is too big
Your guess: 25
25 is too small
Your guess: 37
37 is too small
Your guess: 43
43 is too big
Your guess: 40
40 is too small
Your guess: 41
41 is too small
Your guess: 42
Right! 7 tries.
```

**第二局**：改用嚴格的二分搜尋，每次把可能範圍縮一半。

```text
I have a number.
Your guess: 50
50 is too small
Your guess: 75
75 is too big
Your guess: 62
62 is too small
Your guess: 68
68 is too small
Your guess: 71
71 is too small
Your guess: 73
Right! 6 tries.
```

### 4.4 策略分析

- 1 到 100 的答案，二分搜尋最壞情況需要 `ceil(log2 100) = 7` 次，平均約 5.8 次。
- 第一局雖然只有 7 次，但中間有幾步沒有把範圍縮到最小，運氣好才沒有超過 7 次。
- 第二局每次都以目前範圍的中點猜測，資訊量最大，因此 6 次就命中。
- LED 會隨著嘗試次數亮起對應的二進位值，等於把「軟體變數」即時映射到「硬體輸出」，是一個很直觀的軟硬體整合示範。

### 4.5 從遊戲看到的 EDA 觀念

這個小遊戲背後其實包含了完整的晶片設計鏈：

1. **硬體**：RvSoc 由 Veryl 撰寫，經過合成、佈局、繞線後變成 FPGA 位元流。
2. **軟體**：C 程式由工具內的編譯器編成 RISC-V 機器碼，放進 block RAM。
3. **模擬**：虛擬板以逐週期方式執行，LED 與終端機都是真實周邊的行為。
4. **驗證**：遊戲能正常執行，代表 CPU、記憶體、UART 與 GPIO 的整合沒有問題。

也就是說，玩遊戲的同時，其實是在驗證一顆自己「做出來」的 SoC。

---

## 5. 心得

### 5.1 EDA 是把抽象層層落地的橋樑

以前寫程式時，`cnt = cnt + 1` 只是一個敘述；在 veryl2chip 裡，它會變成 24 個正反器、一串進位邏輯、33 個 LUT，最後被放到晶片上的具體位置。EDA 的價值就在於讓工程師不必手工處理這些細節，但仍能透過每個階段的報表與圖形掌握結果。

### 5.2 驗證比設計更花時間

「全面驗證」用同一組 testbench 跑四種實作，確保合成前後行為一致。這讓我理解為什麼業界常說驗證占據設計週期的一大半：合成工具也可能有 bug，唯有持續比對才能建立信心。

### 5.3 時序是實體設計的核心

Blinky 的關鍵路徑約 29.68 ns，Fmax 約 33.7 MHz。雖然對 12 MHz 的板子來說綽綽有餘，但如果電路更複雜、時脈更高，關鍵路徑就會成為瓶頸。STA 讓我看到「邏輯正確」不等於「電路能跑在目標頻率」。

### 5.4 軟硬體協同設計

猜數字遊戲讓我看到同一個系統中，硬體（RvSoc）與軟體（C 程式）如何分工。只改 C 程式時不需要重新合成，這正是軟硬體協同設計的優點：硬體提供平台，軟體快速迭代。

### 5.5 開源 EDA 的教育價值

veryl2chip 把模擬、合成、佈局、繞線、STA、位元流與虛擬板全部放進瀏覽器，讓沒有昂貴商業軟體與實體板的人也能完整體驗流程。對照 nand2tetris 的由下而上與 Cornell ECE 5745 的理論課程，veryl2chip 剛好補上「實際跑一次流程」的那一塊。

### 5.6 限制與改進方向

- 瀏覽器中的虛擬板速度約為真實板的 0.3 到 0.4 倍，複雜設計會更慢。
- 合成與實體設計模型比商業工具簡化，數字不宜直接當成產品預測。
- 若能加入更多自動化測試、形式驗證或功耗分析，教學效果會更完整。

---

## 6. 結論

IC 設計的 EDA 是一整套把高階描述轉換為可製造晶片的軟體流程，涵蓋模擬、合成、佈局、繞線、時序與實體驗證。veryl2chip 讓我實際走過這條路，並在虛擬 iCEBreaker 上玩了一局「猜數字」。遊戲雖然簡單，卻同時展示了 RTL 設計、合成、實體設計、SoC 整合與軟硬體協同設計。透過這次操作，我對 EDA 的整體輪廓有了比課本更具體的認識，也更理解為什麼驗證、時序與抽象層次是數位 IC 設計中不可或缺的部分。

---

## 7. 使用工具聲明

- 本報告由 **opencode** 協助蒐集資料、整理流程與撰寫。
- 操作與數據參考 veryl2chip 官方 IDE、教學文件與範例程式。
- 遊戲紀錄為實際依「猜數字」程式規則進行的遊玩範例。
- 參考資料：
  - veryl2chip：<https://ccc-eda.github.io/veryl2chip/web/dist/index.html>
  - nand2tetris HDL 模擬器：<https://ccc-js.github.io/hack-computer/dist/hdl.html>
  - Cornell ECE 5745：<https://www.csl.cornell.edu/courses/ece5745/handouts.html>
  - veryl2chip GitHub：<https://github.com/ccc-eda/veryl2chip/>
  - hack-computer GitHub：<https://github.com/ccc-js/hack-computer>

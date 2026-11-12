# Nand2Tetris Hack 組譯器（Assembler）實作筆記

本專案對應 **Nand2Tetris / The Elements of Computing Systems — Project 6：The Assembler**。
目標是把 **Hack 組合語言（`.asm`）** 翻譯成 **16-bit 機器碼（`.hack`）**，也就是一条条由 0 與 1 組成的指令。

本筆記同時說明：

- Hack 組合語言與機器指令的格式
- 組譯器的核心原理（**兩遍掃描 Two-Pass**）
- 本專案的程式結構與實作流程
- 如何執行、測試與驗證
- 常見錯誤與注意事項

> 本筆記與組譯器實作使用 [opencode](https://opencode.ai) 協助完成。

---

## 1. 專案檔案結構

```text
week10/
├─ Assembler.java          # 組譯器主程式（單一檔案，Java）
├─ run.bat                 # Windows 一鍵編譯 + 執行腳本
├─ README.md               # 本筆記
└─ samples/
   ├─ Add.asm / Add.hack   # 範例：R0 = 2 + 3
   ├─ Max.asm / Max.hack   # 範例：R2 = max(R0, R1)
   └─ Rect.asm / Rect.hack # 範例：在螢幕左上角畫矩形
```

> `.hack` 檔是執行組譯器後產生的結果，可與課程提供的官方組譯器輸出比對。

---

## 2. 背景知識：Hack 電腦

Hack 是一台 16-bit 的電腦，與組譯器直接相關的重點如下。

### 2.1 暫存器與記憶體

| 名稱 | 說明 |
| --- | --- |
| `A` | 16-bit 位址暫存器（Address register） |
| `D` | 16-bit 資料暫存器（Data register） |
| `M` | 代表 `RAM[A]`，即「以 A 的內容為位址所指向的記憶體字」 |
| `PC` | 程式計數器，指向 ROM 中下一條要執行的指令 |

記憶體配置（Hack 平台）：

| 位址範圍 | 用途 |
| --- | --- |
| `0 – 15` | 預設變數 `R0 – R15` |
| `0 – 4` | 別名 `SP`、`LCL`、`ARG`、`THIS`、`THAT` |
| `16 – 16383` | 一般可用 RAM（變數從這裡開始配置） |
| `16384 – 24575` | 螢幕記憶體（`SCREEN`） |
| `24576` | 鍵盤（`KBD`） |

指令記憶體（ROM）共有 32768 個字（位址 `0 – 32767`），每個字是一條 16-bit 指令。

### 2.2 兩種指令格式

Hack 只有兩種指令。

#### (1) A-instruction（定址指令）

```text
@value
```

機器碼格式：

```text
0 v v v v v v v v v v v v v v v
└─ 1 bit 0 ─┘└──── 15-bit 常數/位址 ────┘
```

- 最高位固定為 `0`
- 後 15 位是數值（`0 – 32767`）或符號對應的位址
- 效果：`A = value`，之後 `M` 就代表 `RAM[value]`

例：

```asm
@2      → 0000000000000010
@0      → 0000000000000000
@SCREEN → 0100000000000000   (16384)
```

#### (2) C-instruction（計算指令）

```text
dest = comp ; jump
```

機器碼格式：

```text
1 1 1 a c1 c2 c3 c4 c5 c6 d1 d2 d3 j1 j2 j3
└─ 3 ─┘└─ 7-bit comp ──┘└ 3-bit dest ┘└ 3-bit jump ┘
```

- 最高三位固定為 `111`
- `comp` 7 位：描述 ALU 要做的計算；其中 `a` 位元決定要用 `A` 還是 `M`
- `dest` 3 位：計算結果要存到哪裡（A / D / M 的組合）
- `jump` 3 位：是否跳躍，以及跳躍條件

`dest` 與 `jump` 可以省略，但 `comp` 一定要有。例如：

```asm
D=A        // dest=D, comp=A, jump 無
D;JGT      // dest 無, comp=D, jump=JGT
0;JMP      // dest 無, comp=0, jump=JMP
AMD=M+1    // dest=AMD, comp=M+1
```

### 2.3 C-instruction 查表

`comp`（7 bits，最左位是 `a`）：

| comp | 機器碼 | comp | 機器碼 |
| --- | --- | --- | --- |
| `0` | `0101010` | `M` | `1110000` |
| `1` | `0111111` | `!M` | `1110001` |
| `-1` | `0111010` | `-M` | `1110011` |
| `D` | `0001100` | `M+1` | `1110111` |
| `A` | `0110000` | `M-1` | `1110010` |
| `!D` | `0001101` | `D+M` | `1000010` |
| `!A` | `0110001` | `D-M` | `1010011` |
| `-D` | `0001111` | `M-D` | `1000111` |
| `-A` | `0110011` | `D&M` | `1000000` |
| `D+1` | `0011111` | `D\|M` | `1010101` |
| `A+1` | `0110111` | | |
| `D-1` | `0001110` | | |
| `A-1` | `0110010` | | |
| `D+A` | `0000010` | | |
| `D-A` | `0010011` | | |
| `A-D` | `0000111` | | |
| `D&A` | `0000000` | | |
| `D\|A` | `0010101` | | |

`dest`（3 bits）：

| dest | 機器碼 |
| --- | --- |
| （空） | `000` |
| `M` | `001` |
| `D` | `010` |
| `MD` | `011` |
| `A` | `100` |
| `AM` | `101` |
| `AD` | `110` |
| `AMD` | `111` |

`jump`（3 bits）：

| jump | 機器碼 | 條件 |
| --- | --- | --- |
| （空） | `000` | 不跳 |
| `JGT` | `001` | 大於 0 |
| `JEQ` | `010` | 等於 0 |
| `JGE` | `011` | 大於等於 0 |
| `JLT` | `100` | 小於 0 |
| `JNE` | `101` | 不等於 0 |
| `JLE` | `110` | 小於等於 0 |
| `JMP` | `111` | 無條件跳躍 |

### 2.4 符號（Symbols）

Hack 組合語言允許使用三種符號：

1. **預先定義符號（Predefined symbols）**

   | 符號 | 位址 |
   | --- | --- |
   | `R0 – R15` | `0 – 15` |
   | `SP` | `0` |
   | `LCL` | `1` |
   | `ARG` | `2` |
   | `THIS` | `3` |
   | `THAT` | `4` |
   | `SCREEN` | `16384` |
   | `KBD` | `24576` |

2. **標籤（Labels）**

   語法：`(LABEL)`，標記某條指令在 **ROM 中的位址**。
   例：`(LOOP)` 表示下一條指令的 ROM 位址。
   標籤本身**不產生機器碼**，只是符號表中的一筆紀錄。

3. **變數（Variables）**

   直接使用 `@name` 且 `name` 不是數字、不是預先定義符號、也不是標籤時，
   組譯器會把它視為變數，從 **RAM[16]** 開始依序配位址。

---

## 3. 組譯器核心原理：Two-Pass（兩遍掃描）

### 3.1 為什麼需要兩遍？

困難點在於：

- 程式可能**先使用標籤、後定義標籤**，例如先 `@END` 之後才出現 `(END)`。
- 標籤的位址是「它在 ROM 中的指令索引」，必須先知道前面有幾條實際指令。
- 變數位址必須從 16 開始配置，且不能和標籤衝突。

因此最直觀且標準的做法是掃描檔案兩次。

### 3.2 第一遍（First Pass）：建立符號表

目的：**找出所有標籤 (LABEL)，並記錄它們的 ROM 位址。**

流程：

1. 逐行讀入。
2. 移除註解（`//` 之後）與前後空白。
3. 忽略空行。
4. 若該行是 `(LABEL)`：
   - 把 `LABEL → 目前指令計數（ROM address）` 存入符號表。
   - **不增加**指令計數。
5. 否則，該行是一條真正會佔用 ROM 的指令：
   - 收集起來（供第二遍使用）。
   - 指令計數 + 1。

示意：

```text
ROM 位址  指令
0         @R0
1         D=M
2         @END
3         D;JGT
4         (LOOP)   ← LOOP = 4，本身不佔 ROM
4         @LOOP
5         0;JMP
```

### 3.3 第二遍（Second Pass）：翻譯指令

目的：**逐條把組合語言翻成 16-bit 機器碼。**

流程：

1. 走訪第一遍收集到的指令。
2. 若為 `@value`：
   - `value` 是純數字 → 直接使用。
   - `value` 是符號：
     - 查符號表；找到就用它對應的位址（標籤或預先定義符號）。
     - 找不到 → 視為新變數，配置 `nextVariableAddress`（初始 16），並存入符號表。
   - 產生 `0` + 15-bit 二進位。
3. 若為 C-instruction：
   - 以 `=` 切出 `dest`。
   - 以 `;` 切出 `jump`。
   - 剩下就是 `comp`。
   - 分別查表得到 3 + 7 + 3 bits，前面補 `111`。
4. 每條指令輸出一行 16 個 0/1。

> 重要：變數是在**第二遍**才配置位址，因為必須等所有標籤都已經確定後，
> 才不會把 RAM 位址和 ROM 位址混在一起。

### 3.4 整體流程圖

```text
             ┌─────────────────────────────┐
   .asm ───▶ │ 第一遍：清除註解、找 (LABEL) │
             │       建立符號表              │
             └──────────────┬──────────────┘
                            │ 收集到的指令
                            ▼
             ┌─────────────────────────────┐
             │ 第二遍：翻譯 @x 與 C-instruction │
             │       配置變數位址 (從 16 開始)  │
             └──────────────┬──────────────┘
                            ▼
                        .hack（16-bit 二進位）
```

---

## 4. 程式結構說明（Assembler.java）

本專案刻意寫成**單一 Java 檔案**，方便閱讀與執行。

### 4.1 四張靜態表

```java
private static final Map<String, String> COMP;  // comp → 7 bits
private static final Map<String, String> DEST;  // dest → 3 bits
private static final Map<String, String> JUMP;  // jump → 3 bits
private static final Map<String, Integer> SYMBOLS; // 符號 → 位址
```

在 `static { ... }` 區塊中初始化：

- 預先定義符號（`R0–R15`、`SP`、`SCREEN`、`KBD` …）
- `COMP`、`DEST`、`JUMP` 三張對照表

把「查表」和「邏輯」分開，是組譯器最常見的設計。

### 4.2 `main`

1. 檢查命令列參數（必須提供一個 `.asm`）。
2. `resolveInput`：若沒寫副檔名就補上 `.asm`，並檢查檔案存在。
3. `Files.readAllLines` 讀入所有行。
4. `firstPass` → 回傳「真正指令」的清單，同時填好符號表。
5. `secondPass` → 產生 `.hack` 內容。
6. `resolveOutput`：同資料夾、同檔名、副檔名改為 `.hack`。
7. `Files.writeString` 寫檔。

### 4.3 `firstPass`

- 對每一行呼叫 `stripComment`（去掉 `//` 與空白）。
- 空行跳過。
- `(LABEL)` → 記錄 `ROM 位址`，不增加位址。
- 其他 → 加入 `instructions`，`romAddress++`。
- 若重複定義標籤（或和預先定義符號同名）→ 丟出錯誤。

### 4.4 `secondPass`

- 維護 `nextVariableAddress = 16`。
- A-instruction：
  - 數字 → 直接轉數值。
  - 符號 → 查表；查不到就配置新變數位址。
  - 檢查 `0 ≤ address ≤ 32767`（15-bit 上限）。
  - `toBinary(address, 16)` 補成 16 位。
- C-instruction：交給 `assembleCInstruction`。

### 4.5 `assembleCInstruction`

```text
D=D+1;JGT
│ │   └ jump = JGT
│ └ comp = D+1
└ dest = D
```

1. 找 `=` → 左邊是 `dest`。
2. 找 `;` → 右邊是 `jump`。
3. 剩下的字串（移除空白）就是 `comp`。
4. 分別查 `DEST`、`COMP`、`JUMP`，組合：

```text
"111" + compBits(7) + destBits(3) + jumpBits(3)
```

### 4.6 `stripComment` 與 `toBinary`

- `stripComment`：移除 `//` 之後所有內容，並 `trim()`。
  因此 `@2 // load two` 也能正確處理。
- `toBinary`：用 `Integer.toBinaryString`，左邊補 0 到指定長度；
  若超過長度則報錯。

---

## 5. 執行方式

### 5.1 需求

- 已安裝 JDK（本專案使用 Java，`javac` / `java` 可用）

### 5.2 使用 `run.bat`（Windows）

```bat
run.bat samples\Add.asm
```

等同於先編譯再執行，會在 `samples\` 產生 `Add.hack`。

### 5.3 手動指令

```powershell
javac -encoding UTF-8 Assembler.java
java Assembler samples\Add.asm
```

也可以省略 `.asm`：

```powershell
java Assembler samples\Add
```

輸出檔會放在**與輸入檔相同資料夾**，副檔名改為 `.hack`。

---

## 6. 測試與驗證

### 6.1 範例：`Add.asm`

```asm
@2
D=A
@3
D=D+A
@0
M=D
```

逐行翻譯：

| 指令 | 說明 | 機器碼 |
| --- | --- | --- |
| `@2` | A = 2 | `0000000000000010` |
| `D=A` | D = A | `1110110000010000` |
| `@3` | A = 3 | `0000000000000011` |
| `D=D+A` | D = D + A | `1110000010010000` |
| `@0` | A = 0（R0） | `0000000000000000` |
| `M=D` | RAM[0] = D | `1110001100001000` |

結果與課程 `projects/5/Add.hack` 完全一致。

### 6.2 標籤位址驗證：`Max.asm`

`Max.asm` 中：

```asm
@ITSR0     // ROM 位址 10
@OUTPUT_D  // ROM 位址 12
@END       // ROM 位址 14
```

產生的 A-instruction 分別為：

```text
0000000000001010   (10)
0000000000001100   (12)
0000000000001110   (14)
```

證明 `(LABEL)` 會對應到「下一條指令」的 ROM 位址，且標籤本身不佔用指令空間。

### 6.3 變數位址驗證：`Rect.asm`

`Rect.asm` 中未事先定義的變數 `n`、`addr` 依首次出現順序配置：

```text
n    → RAM[16]  → 0000000000010000
addr → RAM[17]  → 0000000000010001
```

### 6.4 大程式驗證：`Pong.asm`

以課程提供的 `Pong.asm`（約 2.7 萬條指令）測試，
將本組譯器的輸出與官方 `Assembler.bat` 的輸出逐行比對，結果 **完全一致**。

### 6.5 與官方輸出比對

```powershell
# 使用官方組譯器（會產生另一個 .hack）
& "C:\yan\nand2tetris\tools\Assembler.bat" samples\Max.asm

# 或直接用文字比對工具
& "C:\yan\nand2tetris\tools\TextComparer.bat" ours.hack official.hack
```

本專案已驗證 `Add`、`Max`、`Rect`、`Pong` 四支程式皆與官方結果相同。

---

## 7. 製作過程（Step by Step）

### Step 1：先讀懂指令集

先釐清 A-instruction、C-instruction 的位元配置，
以及 `comp` / `dest` / `jump` 的完整對照表。
這一步是所有實作的基礎，表抄錯就會全盤錯。

### Step 2：決定資料結構

- 用 `Map` 存三張編碼表。
- 用另一個 `Map` 存符號表（符號 → 位址）。
- 兩個獨立的清單/計數器：
  - 第一遍：`ROM 位址計數`
  - 第二遍：`下一個變數位址 = 16`

### Step 3：實作清理與第一遍

先寫 `stripComment`，確保註解與空白不會干擾解析。
再實作「遇到 `(LABEL)` 就記錄目前位址、其他行才計數」的邏輯。
此時先不翻譯，只建立符號表。

### Step 4：實作 C-instruction 翻譯

用 `=`、`;` 切字串，再查表。
注意 `dest` 與 `jump` 可為空字串，因此表中要放 `""` 的對應。

### Step 5：實作 A-instruction 與變數配置

數字直接轉二進位；符號先查表，查不到才配置新位址。
這是「標籤」與「變數」分流的關鍵。

### Step 6：輸出與驗證

輸出時每條指令固定 16 個字元。
先用手算的 `Add.asm` 驗證，再和官方組譯器比對，
最後用 `Pong.asm` 這種大型程式做壓力測試。

### Step 7：補強錯誤處理

- 檔案不存在
- 位址超過 32767
- 重複定義標籤
- 非法 `comp` / `dest` / `jump`
- `(LABEL` 缺少右括號

---

## 8. 常見錯誤與注意事項

1. **標籤不是變數**
   `(LOOP)` 記錄的是 **ROM 指令位址**；`@LOOP` 使用它時，是把該位址載入 A。

2. **變數從 16 開始**
   `0–15` 已被 `R0–R15` 與 `SP/LCL/ARG/THIS/THAT` 佔用。
   變數必須從 `RAM[16]` 開始，否則會覆蓋暫存器別名。

3. **先定義 vs 先使用**
   因為採兩遍掃描，`@END` 可以先出現、`(END)` 後出現，這是合法的。

4. **標籤本身不佔 ROM**
   計算標籤位址時，只能對「真正的指令」遞增計數。

5. **`@` 的數值上限是 32767**
   15-bit 無號數最大為 `2^15 - 1 = 32767`。
   例如 `@32768` 會超出範圍，必須報錯。

6. **大小寫敏感**
   `R0` 與 `r0` 不同；`JGT` 不能寫成 `jgt`（本實作區分大小寫）。

7. **`dest` 的組合固定**
   合法值只有 `M/D/MD/A/AM/AD/AMD`（以及空）。
   例如 `DM` 不是合法寫法，應寫 `MD`。

8. **註解處理順序**
   先移除 `//` 再 `trim`，否則 `@2 // comment` 會殘留文字。
   也要注意 `(LABEL) // comment` 這種寫法。

9. **空行與純註解行**
   兩者都必須忽略，且不能增加 ROM 位址。

10. **輸出長度固定**
    每條機器碼必須剛好 16 個字元，左邊補 0。

---

## 9. 心得與延伸

- 組譯器看似複雜，其實本質是 **字串處理 + 查表 + 兩遍掃描**。
- 「兩遍掃描」是處理**前向引用（forward reference）**的經典手法，
  在編譯器、連結器、甚至很多設定檔解析器都會看到類似概念。
- 完成組譯器後，就能把專案 4、5 寫好的 `Fill.asm`、`Max.asm` 等
  轉成 `.hack`，直接載入 CPU Emulator 執行，形成完整的工具鏈。
- 下一步可以挑戰 **Project 7–8 的 VM Translator**，
  把 `.vm` 轉成 `.asm`，再交給本組譯器，體會「高階 → 中階 → 機器碼」的分層。

---

## 10. 參考資料

- Nand2Tetris 官方網站：<https://www.nand2tetris.org/>
- *The Elements of Computing Systems*，Noam Nisan & Shimon Schocken
- 課程工具：`C:\yan\nand2tetris\tools\Assembler.bat`
- 課程範例：`C:\yan\nand2tetris\projects\6\`

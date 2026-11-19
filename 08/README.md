# 專案 08：VM 轉譯器 Part II — 程式流程與函式呼叫

本資料夾為 Nand2Tetris（《The Elements of Computing Systems》）第 8 週的測試程式與轉譯結果。
目標是將 **VM 程式（`.vm`）轉譯成 Hack 組合語言（`.asm`）**，並新增 Part I（專案 07）尚未實作的
「程式流程（Program Flow）」與「函式呼叫（Function Calls）」兩類指令。

---

## 一、資料夾結構

```
08/
├── ProgramFlow/                  # 程式流程測試（label / goto / if-goto）
│   ├── BasicLoop/                # 計算 1+2+...+n
│   └── FibonacciSeries/          # 費氏數列（0,1,1,2,3,5...）
└── FunctionCalls/                # 函式呼叫測試（function / call / return / bootstrap）
    ├── SimpleFunction/           # 單一函式，驗證區域變數初始化與 return
    ├── NestedCall/               # 巢狀呼叫，驗證呼叫堆疊與 Memory Segments 還原
    ├── FibonacciElement/         # 遞迴 + 多檔案合併 + bootstrap（Sys.vm + Main.vm）
    └── StaticsTest/              # static 區段在同一檔案內的共用（Class1/Class2/Sys）
```

每個測試資料夾內含：

| 副檔名 | 說明 |
|--------|------|
| `.vm`  | VM 原始碼（輸入） |
| `.asm` | 轉譯後的組合語言（輸出） |
| `.tst` | CPU Emulator 的測試脚本 |
| `.cmp` | 預期輸出（Comparison File） |
| `.out` | 實際執行輸出 |
| `*VME.tst` | VM Emulator 用來觀察 VM 執行的脚本 |

---

## 二、使用的轉譯器

轉譯器原始碼位於上一層 `07/VMTranslator/`，由四個 Java 檔案組成：

| 檔案 | 職責 |
|------|------|
| `VMTranslator.java` | 程式入口：判斷輸入是「單一 `.vm` 檔」或「目錄」，並分派命令給 CodeWriter |
| `Parser.java` | 讀取 `.vm`，去除 `//` 註解與空白，拆解指令並判斷 `CommandType` |
| `CodeWriter.java` | 核心：把每一種 VM 指令轉成對應的 Hack assembly |
| `run.bat` | 批次檔：先用 `javac` 編譯，再執行 `VMTranslator` |

---

## 三、使用工具說明

整個轉譯與驗證過程使用以下工具：

| 工具 | 用途 | 本專案中的使用方式 |
|------|------|--------------------|
| **Java JDK** | 編譯並執行 VM 轉譯器 | `javac` 編譯 `Parser.java`、`CodeWriter.java`、`VMTranslator.java`；`java` 執行轉譯 |
| **run.bat** | 一鍵批次腳本 | 封裝 `javac` + `java` 兩個步驟，傳入 `.vm` 檔或目錄即可產生 `.asm` |
| **命令提示字元 / PowerShell** | 操作與切換目錄 | 執行 `cd 07\VMTranslator` 與 `run.bat ..\08\...` |
| **Nand2Tetris CPU Emulator** | 驗證轉譯結果 | 載入 `.tst` 執行 `.asm`，並將 `.out` 與 `.cmp` 比對確認正確 |
| **Nand2Tetris VM Emulator** | 觀察 VM 執行細節 | 載入 `*VME.tst`，逐步查看堆疊、記憶體區段與函式呼叫過程 |
| **文字編輯器** | 查看與編寫 `.vm` / `.asm` / `.tst` | 閱讀原始碼與轉譯結果，撰寫本說明文件 |
| **opencode** | AI 程式助理 | 協助撰寫本 README、整理轉譯流程說明與標註 |

工具之間的關係：

```
.vm 原始碼 ──[ Java VM 轉譯器 / run.bat ]──> .asm ──[ CPU Emulator + .tst ]──> .out vs .cmp
                     ▲                                        │
                     └──────── opencode 協助撰寫說明 ───────────┘
                                      │
                              VM Emulator (*VME.tst) 可觀察執行過程
```

---

## 四、執行方式

在 `07/VMTranslator` 目錄下執行（需先安裝 Java JDK）：

```bat
run.bat <檔案.vm 或 目錄>
```

實際範例：

```bat
cd 07\VMTranslator

REM 單一檔案：輸出 BasicLoop.asm
run.bat ..\08\ProgramFlow\BasicLoop\BasicLoop.vm

REM 整個目錄：輸出 FibonacciElement.asm（合併 Sys.vm + Main.vm，並加 bootstrap）
run.bat ..\08\FunctionCalls\FibonacciElement
```

規則：

- 輸入單一 `.vm` 檔 → 輸出同名 `.asm`，**不**加 bootstrap（供 SimpleFunction、NestedCall 等手動設定堆疊的測試用）。
- 輸入目錄 → 把所有 `.vm` 依檔名排序合併成 `<目錄名>.asm`，並在開頭自動加入 **bootstrap**（供 FibonacciElement、StaticsTest 使用）。
- 也可直接下 `java VMTranslator <路徑>`（前提是已编译过 `.class`）。

---

## 五、轉譯原理與過程

### 5.1 記憶體區段（Memory Segments）對照

| VM 區段 | Hack 實作 |
|---------|-----------|
| `local` | `LCL`（RAM 1）為基底 + index |
| `argument` | `ARG`（RAM 2）為基底 + index |
| `this` / `that` | `THIS`（RAM 3）/ `THAT`（RAM 4）為基底 + index |
| `constant` | 直接把 index 放入 D（`A=A`）|
| `temp` | RAM 5~12（5 + index） |
| `pointer` | RAM 3~4（3 + index，即 THIS/THAT） |
| `static` | 轉成 `<檔名>.<index>` 的全域符號 |

> `push`：先把值算進 D，再 `@SP / A=M / M=D`，最後 `SP++`。
> `pop`：先 `SP--` 取值到 D，再寫入目標位址（`local/argument/this/that` 用 R13 暫存目標位址）。

### 5.2 程式流程（Program Flow）

Hack 沒有 `label/goto`，必須轉成 `@符號` + 跳躍指令：

| VM | 轉譯後的 assembly | 說明 |
|----|-------------------|------|
| `label LOOP` | `(BasicLoop$LOOP)` | 標籤前加上「目前函式名$」避免不同函式同名標籤衝突 |
| `goto LOOP` | `@BasicLoop$LOOP` / `0;JMP` | 無條件跳躍 |
| `if-goto LOOP` | `pop 到 D` / `@BasicLoop$LOOP` / `D;JNE` | 堆疊頂不為 0 才跳躍 |

`BasicLoop.vm` 的流程即為：`sum=0` → `(LOOP)` → `sum+=n; n--` → `if n>0 goto LOOP`，最後把 sum push 回堆疊。

### 5.3 函式呼叫（Function Calls）

#### (1) `function f n`

```
(f)                 // 函式進入點
@SP  A=M  M=0       // 重複 n 次
@SP  M=M+1          // 將 n 個區域變數初始化為 0
```

Example：`function SimpleFunction.test 2` 會在堆疊上產生兩個 0，`push local 0/1` 才能讀到。

#### (2) `call f n`（呼叫端）

依序完成 13 件事：

1. 產生唯一的返回標籤 `RETURN_ADDRESS<n>`（用 `callId` 計數）。
2. push 返回位址。
3. push `LCL、ARG、THIS、THAT`（儲存呼叫端狀態）。
4. 設定 `ARG = SP - 5 - n`（第一個引數的位置）。
5. 設定 `LCL = SP`（新的區域變數基底）。
6. `goto f`（`@f` / `0;JMP`）。
7. 標記返回標籤 `(RETURN_ADDRESS<n>)`，函式 return 後從這裡繼續。

#### (3) `return`（被呼叫端）

1. `FRAME = LCL`，`RET = *(FRAME-5)`（取回返回位址，存到 R14）。
2. `*ARG = returnValue`（把堆疊頂的回傳值放到呼叫端第一個引數位置）。
3. `SP = ARG + 1`。
4. 依序還原 `THAT、THIS、ARG、LCL`（從 FRAME 往回_pop）。
5. `goto RET`（`@R14` / `A=M` / `0;JMP`），回到呼叫端。

#### (4) bootstrap（程式啟動碼）

當輸入為**整個目錄**時自動產生：

```
@256        // SP = 256
D=A
@SP
M=D
call Sys.init 0   // 呼叫進入點
```

`FibonacciElement` 與 `StaticsTest` 都依賴 bootstrap 啟動 `Sys.init`；`Sys.vm` 再 `call Main.fibonacci 1` 計算 fib(4)。

### 5.4 其他細節

- **算術/邏輯**：`add/sub/and/or` 直接對堆疊頂兩個元素運算；`neg/not` 只處理堆疊頂一個；
  `eq/gt/lt` 用 `D=M-D` 搭配 `D;JEQ/JGT/JLT` 與 `IF_TRUE<n> / IF_END<n>` 產生 -1（真）或 0（假）。
- **比較與呼叫的編號**：`comparisonId`、`callId` 持續遞增，確保每次產生的標籤獨一無二。
- **static 跨檔案**：同一個 `.vm` 檔內的 `static 0` 轉成 `Class1.0`，不同檔案各自獨立，因此 `StaticsTest` 中 Class1/Class2 的 static 不會互相覆蓋。
- **標籤作用域**：`scopedLabel()` 統一加上 `currentFunction$` 前綴，是 Part II 的關鍵設計。

---

## 六、驗證方式

1. 開啟 **CPU Emulator**，載入對應資料夾的 `.tst`（例：`SimpleFunction.tst`）。
2. 測試脚本會 `load xxx.asm`，執行若干 `ticktock`，再 `output` 比較 RAM 狀態。
3. 若產生的 `.out` 與 `.cmp` 完全一致，代表轉譯正確。
4. 若想觀察 VM 層級執行，可載入 `xxxVME.tst` 使用 VM Emulator 逐步查看堆疊與函式呼叫。

檢查結果：本資料夾所有測試（BasicLoop、FibonacciSeries、SimpleFunction、NestedCall、
FibonacciElement、StaticsTest）的 `.out` 均與 `.cmp` 相符 ✓

---

## 七、完成標註

本 README 與上述 VM → Hack assembly 轉譯流程說明，使用 **opencode** 完成。

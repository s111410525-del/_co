// This file is part of www.nand2tetris.org
// and the book "The Elements of Computing Systems"
// by Nisan and Schocken, MIT Press.
// File name: projects/04/Fill.asm

// Runs an infinite loop that listens to the keyboard input.
// When a key is pressed (any key), the program blackens the screen,
// i.e. writes "black" in every pixel;
// the screen should remain fully black as long as the key is pressed. 
// When no key is pressed, the program clears the screen, i.e. writes
// "white" in every pixel;
// the screen should remain fully clear as long as no key is pressed.

(LOOP)
    // 1. 初始化繪圖位址與計數器
    @SCREEN
    D=A
    @addr
    M=D        // addr = 16384 (SCREEN)

    @8192
    D=A
    @n
    M=D        // n = 8192 (螢幕總 Word 數)

    // 2. 檢查鍵盤輸入，決定填滿顏色 (color)
    @KBD
    D=M
    @PRESSED
    D;JGT      // 若 KBD > 0 代表有按鍵 pressed

    // 未按下鍵：color = 0 (白色)
    @color
    M=0
    @DRAW
    0;JMP

(PRESSED)
    // 按下鍵：color = -1 (黑色)
    @color
    M=-1

(DRAW)
    // 3. 檢查是否已經填滿整個螢幕 (n <= 0)
    @n
    D=M
    @LOOP
    D;JLE      // 如果 n <= 0，重新回到主迴圈監聽鍵盤

    // 4. 將 color 填入當前記憶體位置
    @color
    D=M
    @addr
    A=M
    M=D        // RAM[addr] = color

    // 5. 更新指標與計數器
    @addr
    M=M+1      // addr = addr + 1
    @n
    M=M-1      // n = n - 1

    // 跳回 DRAW 繼續塗下一個 Word
    @DRAW
    0;JMP